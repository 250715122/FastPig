package com.gt;

import java.sql.*;
import java.util.*;

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
            st.execute("CREATE INDEX IF NOT EXISTS idx_snippets_key ON snippets(key)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_snippets_updated ON snippets(updated_at)");
        } catch (SQLException e) {
            throw new RuntimeException("初始化数据库失败: " + e.getMessage(), e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void save(NoteDto note) {
        String sql = "INSERT INTO snippets(id, key, title, desc, tags_json, body_md, front_matter, created_at, updated_at, version, deleted)\n" +
                "VALUES(?,?,?,?,?,?,?,?,?,?,0)\n" +
                "ON CONFLICT(id) DO UPDATE SET key=excluded.key, title=excluded.title, desc=excluded.desc, tags_json=excluded.tags_json, body_md=excluded.body_md, front_matter=excluded.front_matter, updated_at=excluded.updated_at, version=excluded.version";
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
            ps.executeUpdate();
        } catch (SQLException e) {
            // 若因 key 唯一约束失败，则按 key 进行更新（幂等覆盖）
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: snippets.key")) {
                String up = "UPDATE snippets SET title=?, desc=?, tags_json=?, body_md=?, front_matter=?, updated_at=?, version=?, deleted=0 WHERE key=?";
                try (Connection c2 = getConnection(); PreparedStatement ps2 = c2.prepareStatement(up)) {
                    ps2.setString(1, note.title);
                    ps2.setString(2, note.desc);
                    ps2.setString(3, toJson(note.tags));
                    ps2.setString(4, note.bodyMd);
                    ps2.setString(5, note.frontMatter);
                    ps2.setLong(6, note.updatedAt);
                    ps2.setInt(7, note.version);
                    ps2.setString(8, note.key);
                    ps2.executeUpdate();
                    return;
                } catch (SQLException ex) {
                    throw new RuntimeException("按key更新失败: " + ex.getMessage(), ex);
                }
            }
            throw new RuntimeException("保存失败: " + e.getMessage(), e);
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
}


