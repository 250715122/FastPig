package com.gt.storage;

import com.gt.NoteDto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记文件存储
 * 负责将笔记保存为 Markdown 文件（含 YAML front matter）
 * 
 * 文件结构：
 * notes/
 * ├── {key}--{uuid}/
 * │   ├── note.md        # 笔记正文 + front matter
 * │   └── assets/        # 图片资源
 * └── .sync_meta.json    # 同步元数据
 */
public class NoteFileStorage {

    private static NoteFileStorage instance;

    private final Path notesDir;
    private static final String NOTE_FILE_NAME = "note.md";
    private static final String ASSETS_DIR_NAME = "assets";
    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));

    // YAML front matter 解析正则
    private static final Pattern FRONT_MATTER_PATTERN = 
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);

    public NoteFileStorage(String basePath) {
        this.notesDir = Paths.get(basePath, "notes");
        ensureNotesDir();
    }

    public static synchronized NoteFileStorage getInstance() {
        if (instance == null) {
            instance = new NoteFileStorage(System.getProperty("user.dir"));
        }
        return instance;
    }

    /**
     * 确保 notes 目录存在
     */
    private void ensureNotesDir() {
        try {
            if (!Files.exists(notesDir)) {
                Files.createDirectories(notesDir);
                System.out.println("[NoteFileStorage] 创建 notes 目录: " + notesDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("无法创建 notes 目录: " + e.getMessage(), e);
        }
    }

    /**
     * 获取笔记文件夹路径
     */
    public Path getNoteFolderPath(NoteDto note) {
        String folderName = buildFolderName(note.key, note.id);
        return notesDir.resolve(folderName);
    }

    /**
     * 构建文件夹名称: {key}--{uuid}
     */
    private String buildFolderName(String key, String id) {
        // 清理 key 中的非法字符
        String safeKey = key != null ? key.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\-_]", "-") : "unnamed";
        return safeKey + "--" + id;
    }

    /**
     * 从文件夹名称解析 ID
     */
    public String parseIdFromFolderName(String folderName) {
        int idx = folderName.lastIndexOf("--");
        if (idx > 0 && idx < folderName.length() - 2) {
            return folderName.substring(idx + 2);
        }
        return null;
    }

    /**
     * 保存笔记到文件
     */
    public void saveToFile(NoteDto note) {
        if (note == null || note.id == null) {
            throw new IllegalArgumentException("笔记或笔记ID不能为空");
        }

        Path folderPath = getNoteFolderPath(note);
        Path noteFilePath = folderPath.resolve(NOTE_FILE_NAME);

        try {
            // 确保目录存在
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            // 构建文件内容
            String content = buildNoteContent(note);

            // 写入文件
            Files.writeString(noteFilePath, content, StandardCharsets.UTF_8);

            System.out.println("[NoteFileStorage] 已保存笔记: " + noteFilePath);

        } catch (IOException e) {
            throw new RuntimeException("保存笔记失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文件夹加载笔记
     */
    public NoteDto loadFromFile(Path folderPath) {
        Path noteFilePath = folderPath.resolve(NOTE_FILE_NAME);

        if (!Files.exists(noteFilePath)) {
            System.out.println("[NoteFileStorage] 笔记文件不存在: " + noteFilePath);
            return null;
        }

        try {
            String content = Files.readString(noteFilePath, StandardCharsets.UTF_8);
            return parseNoteContent(content, folderPath);
        } catch (IOException e) {
            System.err.println("[NoteFileStorage] 读取笔记失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 ID 加载笔记
     */
    public NoteDto loadById(String id) {
        Path folderPath = findNoteFolderById(id);
        if (folderPath == null) {
            return null;
        }
        return loadFromFile(folderPath);
    }

    /**
     * 通过 ID 查找笔记文件夹
     */
    public Path findNoteFolderById(String id) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    String folderName = path.getFileName().toString();
                    if (folderName.endsWith("--" + id)) {
                        return path;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[NoteFileStorage] 查找笔记文件夹失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 扫描所有笔记
     */
    public List<NoteDto> scanAllNotes() {
        List<NoteDto> notes = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && !path.getFileName().toString().startsWith(".")) {
                    NoteDto note = loadFromFile(path);
                    if (note != null) {
                        notes.add(note);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[NoteFileStorage] 扫描笔记目录失败: " + e.getMessage());
        }

        System.out.println("[NoteFileStorage] 扫描到 " + notes.size() + " 个笔记");
        return notes;
    }

    /**
     * 删除笔记文件夹
     */
    public boolean deleteNoteFolder(String id) {
        Path folderPath = findNoteFolderById(id);
        if (folderPath == null) {
            return false;
        }

        try {
            deleteDirectory(folderPath);
            System.out.println("[NoteFileStorage] 已删除笔记文件夹: " + folderPath);
            return true;
        } catch (IOException e) {
            System.err.println("[NoteFileStorage] 删除笔记文件夹失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("删除失败: " + path);
                        }
                    });
        }
    }

    /**
     * 保存资源文件（图片等）
     */
    public Path saveAsset(String noteId, String fileName, byte[] data) {
        Path folderPath = findNoteFolderById(noteId);
        if (folderPath == null) {
            throw new RuntimeException("笔记文件夹不存在: " + noteId);
        }

        Path assetsDir = folderPath.resolve(ASSETS_DIR_NAME);
        try {
            if (!Files.exists(assetsDir)) {
                Files.createDirectories(assetsDir);
            }

            Path assetPath = assetsDir.resolve(fileName);
            Files.write(assetPath, data);

            System.out.println("[NoteFileStorage] 已保存资源: " + assetPath);
            return assetPath;

        } catch (IOException e) {
            throw new RuntimeException("保存资源失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取笔记的相对路径（用于同步）
     */
    public String getRelativePath(NoteDto note) {
        Path folderPath = getNoteFolderPath(note);
        return notesDir.relativize(folderPath).toString().replace("\\", "/");
    }

    /**
     * 获取 notes 目录路径
     */
    public Path getNotesDir() {
        return notesDir;
    }

    /**
     * 构建笔记文件内容（YAML front matter + 正文）
     */
    private String buildNoteContent(NoteDto note) {
        StringBuilder sb = new StringBuilder();

        // YAML front matter
        sb.append("---\n");
        sb.append("id: \"").append(escapeYaml(note.id)).append("\"\n");
        sb.append("key: \"").append(escapeYaml(note.key)).append("\"\n");
        sb.append("title: \"").append(escapeYaml(note.title)).append("\"\n");
        sb.append("desc: \"").append(escapeYaml(note.desc)).append("\"\n");

        // tags 数组
        sb.append("tags: [");
        if (note.tags != null && !note.tags.isEmpty()) {
            for (int i = 0; i < note.tags.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeYaml(note.tags.get(i))).append("\"");
            }
        }
        sb.append("]\n");

        sb.append("version: ").append(note.version).append("\n");
        sb.append("deleted: ").append(note.deleted ? "true" : "false").append("\n");
        sb.append("createdAt: ").append(formatTimestamp(note.createdAt)).append("\n");
        sb.append("updatedAt: ").append(formatTimestamp(note.updatedAt)).append("\n");
        sb.append("---\n\n");

        // 正文
        if (note.bodyMd != null) {
            sb.append(note.bodyMd);
        }

        return sb.toString();
    }

    /**
     * 解析笔记文件内容
     */
    private NoteDto parseNoteContent(String content, Path folderPath) {
        NoteDto note = new NoteDto();

        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            String frontMatter = matcher.group(1);
            parseFrontMatter(frontMatter, note);

            // 正文是 front matter 之后的内容
            String body = content.substring(matcher.end()).trim();
            note.bodyMd = body;
            note.frontMatter = frontMatter;
        } else {
            // 没有 front matter，整个内容都是正文
            note.bodyMd = content;

            // 尝试从文件夹名称获取 ID
            String folderName = folderPath.getFileName().toString();
            note.id = parseIdFromFolderName(folderName);
        }

        // 设置文件夹路径
        note.folderPath = folderPath.toString();

        return note;
    }

    /**
     * 解析 YAML front matter
     */
    private void parseFrontMatter(String yaml, NoteDto note) {
        Map<String, String> values = new HashMap<>();

        for (String line : yaml.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.contains(":")) {
                continue;
            }

            int colonIdx = line.indexOf(':');
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            // 移除引号
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            values.put(key, value);
        }

        note.id = unescapeYaml(values.getOrDefault("id", ""));
        note.key = unescapeYaml(values.getOrDefault("key", ""));
        note.title = unescapeYaml(values.getOrDefault("title", ""));
        note.desc = unescapeYaml(values.getOrDefault("desc", ""));
        note.version = parseInt(values.get("version"), 1);
        note.deleted = "true".equalsIgnoreCase(values.get("deleted"));
        note.createdAt = parseTimestamp(values.get("createdAt"));
        note.updatedAt = parseTimestamp(values.get("updatedAt"));

        // 解析 tags 数组
        String tagsStr = values.get("tags");
        note.tags = parseTags(tagsStr);
    }

    /**
     * 解析 tags 数组
     */
    private List<String> parseTags(String tagsStr) {
        List<String> tags = new ArrayList<>();
        if (tagsStr == null || tagsStr.isEmpty()) {
            return tags;
        }

        // 移除方括号
        tagsStr = tagsStr.trim();
        if (tagsStr.startsWith("[")) {
            tagsStr = tagsStr.substring(1);
        }
        if (tagsStr.endsWith("]")) {
            tagsStr = tagsStr.substring(0, tagsStr.length() - 1);
        }

        if (tagsStr.isEmpty()) {
            return tags;
        }

        // 分割并处理每个 tag
        for (String tag : tagsStr.split(",")) {
            tag = tag.trim();
            if (tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1);
            }
            if (!tag.isEmpty()) {
                tags.add(unescapeYaml(tag));
            }
        }

        return tags;
    }

    /**
     * 格式化时间戳为 ISO 格式
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return Instant.now().toString();
        }
        return Instant.ofEpochMilli(timestamp).toString();
    }

    /**
     * 解析 ISO 时间戳
     */
    private long parseTimestamp(String str) {
        if (str == null || str.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            return Instant.parse(str).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * 解析整数
     */
    private int parseInt(String str, int defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * YAML 字符串转义
     */
    private String escapeYaml(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * YAML 字符串反转义
     */
    private String unescapeYaml(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * 重命名笔记文件夹（当 key 变化时）
     */
    public void renameNoteFolder(String noteId, String oldKey, String newKey) {
        if (oldKey != null && oldKey.equals(newKey)) {
            return; // key 没变，不需要重命名
        }

        Path oldFolderPath = findNoteFolderById(noteId);
        if (oldFolderPath == null) {
            return;
        }

        String newFolderName = buildFolderName(newKey, noteId);
        Path newFolderPath = notesDir.resolve(newFolderName);

        if (oldFolderPath.equals(newFolderPath)) {
            return;
        }

        try {
            Files.move(oldFolderPath, newFolderPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[NoteFileStorage] 重命名文件夹: " + oldFolderPath.getFileName() + " -> " + newFolderName);
        } catch (IOException e) {
            System.err.println("[NoteFileStorage] 重命名文件夹失败: " + e.getMessage());
        }
    }
}

