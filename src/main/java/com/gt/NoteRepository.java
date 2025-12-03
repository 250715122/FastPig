package com.gt;

import java.sql.*;
import java.util.*;

/**
 * 笔记索引存储
 * 改造后主要作为索引使用，正文内容存储在文件中
 * 保留 body_md 字段用于向后兼容和数据迁移
 */
public class NoteRepository {
    private final String dbPath;

    public NoteRepository(String dbPath) {
        this.dbPath = dbPath;
        initialize();
    }

    private void initialize() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            
            // 创建主表（保留 body_md 用于向后兼容）
            st.execute("CREATE TABLE IF NOT EXISTS snippets (\n" +
                    "id TEXT PRIMARY KEY,\n" +
                    "key TEXT UNIQUE,\n" +
                    "title TEXT,\n" +
                    "desc TEXT,\n" +
                    "tags_json TEXT,\n" +
                    "body_md TEXT,\n" +
                    "front_matter TEXT,\n" +
                    "created_at INTEGER,\n" +
                    "updated_at INTEGER,\n" +
                    "version INTEGER,\n" +
                    "deleted INTEGER DEFAULT 0\n" +
                    ")");
            
            // 添加新字段（如果不存在）
            addColumnIfNotExists(conn, "folder_path", "TEXT");
            addColumnIfNotExists(conn, "content_hash", "TEXT");
            
            // 创建索引
            st.execute("CREATE INDEX IF NOT EXISTS idx_snippets_key ON snippets(key)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_snippets_updated ON snippets(updated_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_snippets_folder ON snippets(folder_path)");
            
        } catch (SQLException e) {
            throw new RuntimeException("初始化数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 如果列不存在则添加
     */
    private void addColumnIfNotExists(Connection conn, String columnName, String columnType) {
        try (Statement st = conn.createStatement()) {
            // 尝试查询该列，如果失败说明不存在
            ResultSet rs = conn.getMetaData().getColumns(null, null, "snippets", columnName);
            if (!rs.next()) {
                st.execute("ALTER TABLE snippets ADD COLUMN " + columnName + " " + columnType);
                System.out.println("[NoteRepository] 添加新列: " + columnName);
            }
        } catch (SQLException e) {
            // 忽略错误，可能列已存在
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * 保存笔记（完整保存，包含正文）
     */
    public void save(NoteDto note) {
        String sql = "INSERT INTO snippets(id, key, title, desc, tags_json, body_md, front_matter, created_at, updated_at, version, deleted, folder_path, content_hash)\n" +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)\n" +
                "ON CONFLICT(id) DO UPDATE SET key=excluded.key, title=excluded.title, desc=excluded.desc, " +
                "tags_json=excluded.tags_json, body_md=excluded.body_md, front_matter=excluded.front_matter, " +
                "updated_at=excluded.updated_at, version=excluded.version, deleted=excluded.deleted, " +
                "folder_path=excluded.folder_path, content_hash=excluded.content_hash";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, note.id);
            ps.setString(2, note.key);
            ps.setString(3, note.title);
            ps.setString(4, note.desc);
            ps.setString(5, toJson(note.tags));
            ps.setString(6, note.bodyMd);
            ps.setString(7, note.frontMatter);
            ps.setLong(8, note.createdAt);
            ps.setLong(9, note.updatedAt);
            ps.setInt(10, note.version);
            ps.setInt(11, note.deleted ? 1 : 0);
            ps.setString(12, note.folderPath);
            ps.setString(13, note.contentHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            // 若因 key 唯一约束失败，则按 key 进行更新（幂等覆盖）
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: snippets.key")) {
                String up = "UPDATE snippets SET title=?, desc=?, tags_json=?, body_md=?, front_matter=?, " +
                        "updated_at=?, version=?, deleted=?, folder_path=?, content_hash=? WHERE key=?";
                try (Connection c2 = getConnection(); PreparedStatement ps2 = c2.prepareStatement(up)) {
                    ps2.setString(1, note.title);
                    ps2.setString(2, note.desc);
                    ps2.setString(3, toJson(note.tags));
                    ps2.setString(4, note.bodyMd);
                    ps2.setString(5, note.frontMatter);
                    ps2.setLong(6, note.updatedAt);
                    ps2.setInt(7, note.version);
                    ps2.setInt(8, note.deleted ? 1 : 0);
                    ps2.setString(9, note.folderPath);
                    ps2.setString(10, note.contentHash);
                    ps2.setString(11, note.key);
                    ps2.executeUpdate();
                    return;
                } catch (SQLException ex) {
                    throw new RuntimeException("按key更新失败: " + ex.getMessage(), ex);
                }
            }
            throw new RuntimeException("保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 仅保存索引信息（不包含正文）
     * 用于新的文件存储模式
     */
    public void saveIndex(NoteDto note) {
        String sql = "INSERT INTO snippets(id, key, title, desc, tags_json, created_at, updated_at, version, deleted, folder_path, content_hash)\n" +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?)\n" +
                "ON CONFLICT(id) DO UPDATE SET key=excluded.key, title=excluded.title, desc=excluded.desc, " +
                "tags_json=excluded.tags_json, updated_at=excluded.updated_at, version=excluded.version, " +
                "deleted=excluded.deleted, folder_path=excluded.folder_path, content_hash=excluded.content_hash";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, note.id);
            ps.setString(2, note.key);
            ps.setString(3, note.title);
            ps.setString(4, note.desc);
            ps.setString(5, toJson(note.tags));
            ps.setLong(6, note.createdAt);
            ps.setLong(7, note.updatedAt);
            ps.setInt(8, note.version);
            ps.setInt(9, note.deleted ? 1 : 0);
            ps.setString(10, note.folderPath);
            ps.setString(11, note.contentHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: snippets.key")) {
                String up = "UPDATE snippets SET title=?, desc=?, tags_json=?, updated_at=?, version=?, " +
                        "deleted=?, folder_path=?, content_hash=? WHERE key=?";
                try (Connection c2 = getConnection(); PreparedStatement ps2 = c2.prepareStatement(up)) {
                    ps2.setString(1, note.title);
                    ps2.setString(2, note.desc);
                    ps2.setString(3, toJson(note.tags));
                    ps2.setLong(4, note.updatedAt);
                    ps2.setInt(5, note.version);
                    ps2.setInt(6, note.deleted ? 1 : 0);
                    ps2.setString(7, note.folderPath);
                    ps2.setString(8, note.contentHash);
                    ps2.setString(9, note.key);
                    ps2.executeUpdate();
                    return;
                } catch (SQLException ex) {
                    throw new RuntimeException("按key更新索引失败: " + ex.getMessage(), ex);
                }
            }
            throw new RuntimeException("保存索引失败: " + e.getMessage(), e);
        }
    }

    public List<NoteDto> searchByKeyOrText(String query, int limit) {
        String like = "%" + query.toLowerCase() + "%";
        String sql = "SELECT * FROM snippets WHERE deleted=0 AND (\n" +
                "LOWER(key) LIKE ? OR LOWER(desc) LIKE ? OR LOWER(title) LIKE ? OR LOWER(tags_json) LIKE ? OR LOWER(body_md) LIKE ?\n" +
                ") ORDER BY\n" +
                "CASE WHEN LOWER(key)=? THEN 0 WHEN LOWER(key) LIKE ? THEN 1 ELSE 2 END, updated_at DESC\n" +
                "LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            ps.setString(5, like);
            ps.setString(6, query.toLowerCase());
            ps.setString(7, query.toLowerCase() + "%");
            ps.setInt(8, limit);
            ResultSet rs = ps.executeQuery();
            List<NoteDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapper(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("检索失败: " + e.getMessage(), e);
        }
    }

    // 优先：按 key 前缀匹配
    public List<NoteDto> searchByKeyPrefix(String prefix, int limit) {
        String like = prefix == null ? "" : prefix.toLowerCase() + "%";
        String sql = "SELECT * FROM snippets WHERE deleted=0 AND LOWER(key) LIKE ? ORDER BY updated_at DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            List<NoteDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapper(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("检索失败(key前缀): " + e.getMessage(), e);
        }
    }

    // 备选：按 desc/title 包含匹配
    public List<NoteDto> searchByDescContains(String query, int limit) {
        String like = "%" + (query == null ? "" : query.toLowerCase()) + "%";
        String sql = "SELECT * FROM snippets WHERE deleted=0 AND (LOWER(desc) LIKE ? OR LOWER(title) LIKE ?) ORDER BY updated_at DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            List<NoteDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapper(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("检索失败(desc包含): " + e.getMessage(), e);
        }
    }

    public void softDelete(String id) {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE snippets SET deleted=1 WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除失败: " + e.getMessage(), e);
        }
    }

    public void restoreByKey(String key) {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE snippets SET deleted=0 WHERE key=?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("恢复失败: " + e.getMessage(), e);
        }
    }

    public NoteDto findByKey(String key) {
        String sql = "SELECT * FROM snippets WHERE key=? LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapper(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("按key查询失败: " + e.getMessage(), e);
        }
    }

    public NoteDto findById(String id) {
        String sql = "SELECT * FROM snippets WHERE id=? LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapper(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("按id查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有笔记（用于数据迁移）
     */
    public List<NoteDto> findAll() {
        String sql = "SELECT * FROM snippets ORDER BY updated_at DESC";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<NoteDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapper(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("查询所有笔记失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有未删除笔记的命令和描述（用于生成 all 命令）
     * 只查询必要字段，避免加载大量正文内容
     */
    public List<NoteDto> findAllCommandsAndDescriptions() {
        // 只查询 key 和 desc，不查询 body_md 等大字段
        String sql = "SELECT id, key, desc, title, folder_path FROM snippets " +
                     "WHERE deleted=0 AND key IS NOT NULL AND key != 'all' " +
                     "ORDER BY key ASC";  // 按命令名排序，方便查看
        try (Connection conn = getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<NoteDto> list = new ArrayList<>();
            while (rs.next()) {
                NoteDto n = new NoteDto();
                n.id = rs.getString("id");
                n.key = rs.getString("key");
                n.desc = rs.getString("desc");
                n.title = rs.getString("title");
                n.folderPath = rs.getString("folder_path");
                // 其他字段不需要，保持为 null
                list.add(n);
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("查询命令列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取自指定时间以来更新的笔记（用于增量同步）
     */
    public List<NoteDto> findUpdatedSince(long timestamp) {
        String sql = "SELECT * FROM snippets WHERE updated_at > ? ORDER BY updated_at DESC";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, timestamp);
            ResultSet rs = ps.executeQuery();
            List<NoteDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapper(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("查询更新笔记失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清空 body_md 字段（迁移到文件存储后）
     */
    public void clearBodyMd(String id) {
        try (Connection conn = getConnection(); 
             PreparedStatement ps = conn.prepareStatement("UPDATE snippets SET body_md=NULL WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[NoteRepository] 清空 body_md 失败: " + e.getMessage());
        }
    }

    private NoteDto mapper(ResultSet rs) throws SQLException {
        NoteDto n = new NoteDto();
        n.id = rs.getString("id");
        n.key = rs.getString("key");
        n.title = rs.getString("title");
        n.desc = rs.getString("desc");
        n.tags = fromJson(rs.getString("tags_json"));
        n.bodyMd = rs.getString("body_md");
        n.frontMatter = rs.getString("front_matter");
        n.createdAt = rs.getLong("created_at");
        n.updatedAt = rs.getLong("updated_at");
        n.version = rs.getInt("version");
        n.deleted = rs.getInt("deleted") == 1;
        
        // 新字段（可能不存在于旧数据库）
        try {
            n.folderPath = rs.getString("folder_path");
        } catch (SQLException e) {
            n.folderPath = null;
        }
        try {
            n.contentHash = rs.getString("content_hash");
        } catch (SQLException e) {
            n.contentHash = null;
        }
        
        return n;
    }

    private String toJson(List<String> tags) {
        if (tags == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(tags.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private List<String> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        String s = json.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) return new ArrayList<>();
        s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) return new ArrayList<>();
        String[] parts = s.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.startsWith("\"") && t.endsWith("\"")) {
                t = t.substring(1, t.length() - 1);
            }
            list.add(t.replace("\\\"", "\""));
        }
        return list;
    }

    public String getDbPath() {
        return dbPath;
    }
}
