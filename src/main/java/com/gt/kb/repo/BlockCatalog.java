package com.gt.kb.repo;

import com.gt.kb.model.BlockRef;
import com.gt.kb.model.BlockSummary;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.text.PinyinInitials;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 块目录：SQLite 里的一张 kb_blocks 表，只存元数据不存正文。
 *
 * 正文的唯一真相仍是 note.md，本表是派生索引，用于块 ID → 笔记的反查、
 * 拼音前缀直达和后续阶段的治理查询。任何时候都可以从文件重建。
 *
 * 全程复用一条连接并串行化访问：WAL 下每次开关连接都会触发 checkpoint + fsync，
 * 实测逐篇刷新要 120ms 以上，复用后降到 2ms 量级。SQLite 本就是单写者，串行没有损失。
 */
public class BlockCatalog implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(BlockCatalog.class);

    private final String dbPath;
    private Connection conn;

    public BlockCatalog(String dbPath) {
        this.dbPath = dbPath;
        initialize();
    }

    private synchronized void initialize() {
        try (Statement st = connection().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS kb_blocks (\n" +
                    "block_id TEXT PRIMARY KEY,\n" +
                    "note_key TEXT NOT NULL,\n" +
                    "title TEXT,\n" +
                    "title_pinyin TEXT,\n" +
                    "path TEXT,\n" +
                    "level INTEGER,\n" +
                    "start_line INTEGER,\n" +
                    "end_line INTEGER,\n" +
                    "content_hash TEXT,\n" +
                    "type TEXT,\n" +
                    "status TEXT,\n" +
                    "scope TEXT,\n" +
                    "superseded_by TEXT,\n" +
                    "updated_at INTEGER\n" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_kb_blocks_note ON kb_blocks(note_key)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_kb_blocks_pinyin ON kb_blocks(note_key, title_pinyin)");
        } catch (SQLException e) {
            throw new RuntimeException("初始化块目录失败: " + e.getMessage(), e);
        }
    }

    private Connection connection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                // NoteRepository 写同一个库，并发时靠 busy_timeout 排队而不是直接报 SQLITE_BUSY
                st.execute("PRAGMA busy_timeout=5000");
            }
        }
        return conn;
    }

    @Override
    public synchronized void close() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.warn("[BlockCatalog] 关闭连接失败: {}", e.getMessage());
            }
            conn = null;
        }
    }

    /** 用一篇笔记的最新切分结果整体替换它的目录行。传入空列表等同 {@link #deleteNote}。 */
    public synchronized void replaceNote(String noteKey, List<KnowledgeBlock> blocks) {
        if (noteKey == null || noteKey.isEmpty()) {
            throw new IllegalArgumentException("noteKey 不能为空");
        }
        String sql = "INSERT INTO kb_blocks (block_id, note_key, title, title_pinyin, path, level,"
                + " start_line, end_line, content_hash, type, status, scope, superseded_by, updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try {
            Connection c = connection();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM kb_blocks WHERE note_key = ?")) {
                    del.setString(1, noteKey);
                    del.executeUpdate();
                }
                long now = System.currentTimeMillis();
                try (PreparedStatement ins = c.prepareStatement(sql)) {
                    for (KnowledgeBlock b : blocks) {
                        ins.setString(1, b.id());
                        ins.setString(2, noteKey);
                        ins.setString(3, b.title());
                        ins.setString(4, PinyinInitials.of(b.title()));
                        ins.setString(5, pathJson(b.path()));
                        ins.setInt(6, b.level());
                        ins.setInt(7, b.startLine());
                        ins.setInt(8, b.endLine());
                        ins.setString(9, b.contentHash());
                        ins.setString(10, b.meta().type());
                        ins.setString(11, b.meta().status());
                        ins.setString(12, b.meta().scope());
                        ins.setString(13, b.meta().supersededBy());
                        ins.setLong(14, b.meta().updatedAt() == null ? now : b.meta().updatedAt());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("刷新块目录失败: " + noteKey + ", " + e.getMessage(), e);
        }
    }

    public synchronized void deleteNote(String noteKey) {
        try (PreparedStatement ps = connection().prepareStatement("DELETE FROM kb_blocks WHERE note_key = ?")) {
            ps.setString(1, noteKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除块目录失败: " + noteKey + ", " + e.getMessage(), e);
        }
    }

    public synchronized Optional<String> findNoteKey(String blockId) {
        try (PreparedStatement ps = connection().prepareStatement("SELECT note_key FROM kb_blocks WHERE block_id = ?")) {
            ps.setString(1, blockId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询块目录失败: " + blockId + ", " + e.getMessage(), e);
        }
    }

    /**
     * 拼音/英文前缀直达。首字母段与全拼段都参与匹配，所以 "sy" 和 "suoyin" 命中同一批块。
     *
     * @param noteKey 限定笔记；null 或空串表示跨全部笔记
     * @param prefix  前缀，大小写不敏感；空串返回全部
     */
    public synchronized List<BlockRef> searchByTitlePrefix(String noteKey, String prefix, int limit) {
        boolean scoped = noteKey != null && !noteKey.isEmpty();
        String p = prefix == null ? "" : prefix.strip().toLowerCase();
        StringBuilder sql = new StringBuilder("SELECT note_key, block_id FROM kb_blocks WHERE ");
        if (scoped) {
            sql.append("note_key = ? AND ");
        }
        sql.append("(title_pinyin LIKE ? ESCAPE '\\' OR title_pinyin LIKE ? ESCAPE '\\')")
                .append(" ORDER BY note_key, start_line LIMIT ?");

        List<BlockRef> out = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement(sql.toString())) {
            int i = 1;
            if (scoped) {
                ps.setString(i++, noteKey);
            }
            String head = likePrefix(p);
            ps.setString(i++, head);
            ps.setString(i++, "%" + PinyinInitials.SEPARATOR + head);
            ps.setInt(i, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BlockRef(rs.getString(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("前缀查询块目录失败: " + e.getMessage(), e);
        }
        return out;
    }

    /** 目录是否还没建立过。老库首次使用时据此触发一次全量补齐。 */
    public synchronized boolean isEmpty() {
        try (Statement st = connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM kb_blocks LIMIT 1")) {
            return !rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("查询块目录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 一篇笔记的全部块，按原文行号排序。树形目录懒加载块节点用。
     *
     * 与 {@code BlockRepository.listBlocks} 的区别：这里只读派生表，不碰文件也不重新切分，
     * 因此展开节点是毫秒级的；代价是它反映的是上次保存时的切分结果。
     */
    public synchronized List<BlockSummary> listNote(String noteKey) {
        String sql = "SELECT block_id, note_key, title, level, start_line, end_line FROM kb_blocks"
                + " WHERE note_key = ? ORDER BY start_line, block_id";
        List<BlockSummary> out = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, noteKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BlockSummary(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("列出笔记块失败: " + noteKey + ", " + e.getMessage(), e);
        }
        return out;
    }

    /** 每篇笔记的块数。一次 GROUP BY 取全量，避免目录刷新时逐篇查询。 */
    public synchronized Map<String, Integer> countByNote() {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Statement st = connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT note_key, COUNT(*) FROM kb_blocks GROUP BY note_key")) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("统计块目录失败: " + e.getMessage(), e);
        }
        return out;
    }

    /** LIKE 前缀转义：用户输入里的 % _ \ 必须当字面量，否则前缀直达会变成任意匹配。 */
    private static String likePrefix(String prefix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }

    private static String pathJson(List<String> path) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            quote(sb, path.get(i));
        }
        return sb.append(']').toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}