package com.gt.sync;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 同步元数据管理
 * 记录上次同步时间和各文件的同步状态
 */
public class SyncMetadata {

    private static final String META_FILE_NAME = ".sync_meta.json";

    private long lastSyncTime;
    private Map<String, FileMetadata> files;

    public SyncMetadata() {
        this.lastSyncTime = 0;
        this.files = new HashMap<>();
    }

    /**
     * 从文件加载同步元数据
     */
    public static SyncMetadata load(Path notesDir) {
        Path metaFile = notesDir.resolve(META_FILE_NAME);
        
        if (!Files.exists(metaFile)) {
            return new SyncMetadata();
        }

        try {
            String content = Files.readString(metaFile, StandardCharsets.UTF_8);
            return parse(content);
        } catch (Exception e) {
            System.err.println("[SyncMetadata] 加载失败: " + e.getMessage());
            return new SyncMetadata();
        }
    }

    /**
     * 保存同步元数据到文件
     */
    public void save(Path notesDir) {
        Path metaFile = notesDir.resolve(META_FILE_NAME);

        try {
            String content = serialize();
            Files.writeString(metaFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[SyncMetadata] 保存失败: " + e.getMessage());
        }
    }

    /**
     * 解析 JSON 内容
     */
    private static SyncMetadata parse(String json) {
        SyncMetadata meta = new SyncMetadata();

        // 简单的 JSON 解析
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return meta;
        }

        // 提取 lastSyncTime
        int idx = json.indexOf("\"lastSyncTime\"");
        if (idx > 0) {
            int colonIdx = json.indexOf(":", idx);
            int commaIdx = json.indexOf(",", colonIdx);
            if (commaIdx < 0) commaIdx = json.indexOf("}", colonIdx);
            String value = json.substring(colonIdx + 1, commaIdx).trim();
            try {
                meta.lastSyncTime = Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // 提取 files（简化处理，只解析基本信息）
        idx = json.indexOf("\"files\"");
        if (idx > 0) {
            int startBrace = json.indexOf("{", idx);
            if (startBrace > 0) {
                int braceCount = 1;
                int endBrace = startBrace + 1;
                while (endBrace < json.length() && braceCount > 0) {
                    char c = json.charAt(endBrace);
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                    endBrace++;
                }
                
                String filesJson = json.substring(startBrace + 1, endBrace - 1);
                parseFiles(filesJson, meta.files);
            }
        }

        return meta;
    }

    /**
     * 解析文件元数据
     */
    private static void parseFiles(String json, Map<String, FileMetadata> files) {
        // 简单的解析，按 "path": {...} 格式
        int idx = 0;
        while (idx < json.length()) {
            int keyStart = json.indexOf("\"", idx);
            if (keyStart < 0) break;
            
            int keyEnd = json.indexOf("\"", keyStart + 1);
            if (keyEnd < 0) break;
            
            String path = json.substring(keyStart + 1, keyEnd);
            
            int valueStart = json.indexOf("{", keyEnd);
            if (valueStart < 0) break;
            
            int valueEnd = json.indexOf("}", valueStart);
            if (valueEnd < 0) break;
            
            String valueJson = json.substring(valueStart + 1, valueEnd);
            FileMetadata fm = parseFileMetadata(valueJson);
            fm.path = path;
            files.put(path, fm);
            
            idx = valueEnd + 1;
        }
    }

    /**
     * 解析单个文件元数据
     */
    private static FileMetadata parseFileMetadata(String json) {
        FileMetadata fm = new FileMetadata();
        
        // 解析 lastModified（注意：indexOf 返回 0 也是有效位置，所以用 >= 0）
        int idx = json.indexOf("\"lastModified\"");
        if (idx >= 0) {
            int colonIdx = json.indexOf(":", idx);
            int commaIdx = json.indexOf(",", colonIdx);
            if (commaIdx < 0) commaIdx = json.length();
            String value = json.substring(colonIdx + 1, commaIdx).trim();
            try {
                fm.lastModified = Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        // 解析 size
        idx = json.indexOf("\"size\"");
        if (idx >= 0) {
            int colonIdx = json.indexOf(":", idx);
            int commaIdx = json.indexOf(",", colonIdx);
            if (commaIdx < 0) commaIdx = json.length();
            String value = json.substring(colonIdx + 1, commaIdx).trim();
            try {
                fm.size = Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        // 解析 cloudModified
        idx = json.indexOf("\"cloudModified\"");
        if (idx >= 0) {
            int colonIdx = json.indexOf(":", idx);
            int commaIdx = json.indexOf(",", colonIdx);
            if (commaIdx < 0) commaIdx = json.length();
            String value = json.substring(colonIdx + 1, commaIdx).trim();
            try {
                fm.cloudModified = Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        // 解析 hash
        idx = json.indexOf("\"hash\"");
        if (idx >= 0) {
            int colonIdx = json.indexOf(":", idx);
            int quoteStart = json.indexOf("\"", colonIdx);
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            if (quoteStart >= 0 && quoteEnd > quoteStart) {
                fm.hash = json.substring(quoteStart + 1, quoteEnd);
            }
        }
        
        return fm;
    }

    /**
     * 序列化为 JSON
     */
    private String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"lastSyncTime\": ").append(lastSyncTime).append(",\n");
        sb.append("  \"files\": {\n");
        
        int i = 0;
        for (Map.Entry<String, FileMetadata> entry : files.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("    \"").append(escapeJson(entry.getKey())).append("\": ");
            sb.append(entry.getValue().toJson());
            i++;
        }
        
        sb.append("\n  }\n");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public Map<String, FileMetadata> getFiles() {
        return files;
    }

    public void updateFile(String path, long lastModified, long size, String hash) {
        FileMetadata fm = files.computeIfAbsent(path, k -> new FileMetadata());
        fm.path = path;
        fm.lastModified = lastModified;
        fm.size = size;
        fm.hash = hash;
    }

    /**
     * 更新文件元数据（包含云端修改时间）
     */
    public void updateFileWithCloudTime(String path, long lastModified, long size, long cloudModified) {
        FileMetadata fm = files.computeIfAbsent(path, k -> new FileMetadata());
        fm.path = path;
        fm.lastModified = lastModified;
        fm.size = size;
        fm.cloudModified = cloudModified;
    }

    /**
     * 文件元数据
     */
    public static class FileMetadata {
        public String path;
        public long lastModified;
        public long size;
        public String hash;
        public long cloudModified; // 云端文件的修改时间

        public String toJson() {
            return "{\"lastModified\": " + lastModified + 
                   ", \"size\": " + size + 
                   ", \"cloudModified\": " + cloudModified +
                   ", \"hash\": \"" + (hash != null ? hash : "") + "\"}";
        }
    }
}

