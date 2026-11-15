package com.gt.storage;

import com.gt.NoteDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * 文件结构（纯 key 目录）：
 * notes/
 * ├── {key}/
 * │   ├── note.md        # 笔记正文 + front matter
 * │   └── assets/        # 图片资源
 * └── .sync_meta.json    # 同步元数据
 */
public class NoteFileStorage {

    private static final Logger logger = LogManager.getLogger(NoteFileStorage.class);
    private static NoteFileStorage instance;

    private final Path notesDir;
    private static final String NOTE_FILE_NAME = "note.md";
    private static final String ASSETS_DIR_NAME = "assets";
    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));

    // YAML front matter 解析正则
    private static final Pattern FRONT_MATTER_PATTERN = 
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    
    // 图片引用正则：匹配 ![...](assets/xxx.png) 格式
    private static final Pattern ASSET_REF_PATTERN = 
            Pattern.compile("!\\[.*?\\]\\(assets/([^)]+)\\)");

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
                logger.info("[NoteFileStorage] 创建 notes 目录: " + notesDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("无法创建 notes 目录: " + e.getMessage(), e);
        }
    }

    /**
     * 获取笔记文件夹路径（纯 key 目录）
     */
    public Path getNoteFolderPath(NoteDto note) {
        String folderName = buildFolderName(note.key);
        return notesDir.resolve(folderName);
    }

    /**
     * 构建文件夹名称: {key}
     * 对 key 做路径安全化：仅保留中英文、数字、下划线、短横线，其余替换为 '-'
     */
    private String buildFolderName(String key) {
        String safeKey = key != null ? key.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\-_]", "-") : "unnamed";
        // 避免空目录名
        if (safeKey.isEmpty()) {
            safeKey = "unnamed";
        }
        return safeKey;
    }

    /**
     * 通过 key 直接定位文件夹
     */
    public Path findNoteFolderByKey(String key) {
        String folderName = buildFolderName(key);
        Path folder = notesDir.resolve(folderName);
        return Files.exists(folder) ? folder : null;
    }

    /** 通过 key 定位 note.md；笔记不存在时返回 null。 */
    public Path findNoteFileByKey(String key) {
        Path folder = findNoteFolderByKey(key);
        return folder == null ? null : folder.resolve(NOTE_FILE_NAME);
    }

    /**
     * 正文在文件内容中的起始偏移（front matter 之后）；无 front matter 时为 0。
     * 与 {@link #parseNoteContent} 共用同一套 front matter 识别口径。
     */
    public static int bodyOffset(String content) {
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        return matcher.find() ? matcher.end() : 0;
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

            logger.info("[NoteFileStorage] 已保存笔记: " + noteFilePath);

            // 清理未引用的图片资源
            cleanupUnusedAssets(note, folderPath);

        } catch (IOException e) {
            throw new RuntimeException("保存笔记失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理未引用的图片资源
     * 扫描 assets 目录，删除不在正文中引用的图片文件
     */
    private void cleanupUnusedAssets(NoteDto note, Path folderPath) {
        Path assetsDir = folderPath.resolve(ASSETS_DIR_NAME);
        
        // 如果 assets 目录不存在，无需清理
        if (!Files.exists(assetsDir) || !Files.isDirectory(assetsDir)) {
            return;
        }
        
        try {
            // 1. 提取正文中引用的图片文件名
            Set<String> referencedFiles = new HashSet<>();
            if (note.encrypted) {
                // 加密笔记的正文是密文，正则扫不出任何引用。
                // 必须改用 front matter 里的明文清单，否则这里会把图片全删光。
                if (note.assets != null) {
                    referencedFiles.addAll(note.assets);
                } else {
                    // 清单缺失时宁可不删，也不能误删用户的图片
                    logger.info("[NoteFileStorage] 加密笔记缺少 assets 清单，跳过图片清理: " + note.key);
                    return;
                }
            } else if (note.bodyMd != null && !note.bodyMd.isEmpty()) {
                Matcher matcher = ASSET_REF_PATTERN.matcher(note.bodyMd);
                while (matcher.find()) {
                    referencedFiles.add(matcher.group(1));
                }
            }
            
            // 2. 扫描 assets 目录，删除未引用的文件
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(assetsDir)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        String fileName = file.getFileName().toString();
                        if (!referencedFiles.contains(fileName)) {
                            Files.delete(file);
                            logger.info("[NoteFileStorage] 已清理未引用图片: " + fileName);
                        }
                    }
                }
            }
            
        } catch (IOException e) {
                    logger.error("[NoteFileStorage] 清理未引用图片失败: " + e.getMessage());
        }
    }

    /**
     * 从文件夹加载笔记
     */
    public NoteDto loadFromFile(Path folderPath) {
        Path noteFilePath = folderPath.resolve(NOTE_FILE_NAME);

        if (!Files.exists(noteFilePath)) {
            logger.info("[NoteFileStorage] 笔记文件不存在: " + noteFilePath);
            return null;
        }

        try {
            String content = Files.readString(noteFilePath, StandardCharsets.UTF_8);
            return parseNoteContent(content, folderPath);
        } catch (IOException e) {
                    logger.error("[NoteFileStorage] 读取笔记失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 ID 加载笔记（纯 key 目录下，需先查索引获取 key）
     * 此方法保留以兼容调用方，但内部将依赖 key 来定位
     */
    public NoteDto loadById(String id) {
        // 兼容：直接扫描 notes 下的 note.md，匹配 front matter 的 id
        return loadByIdScan(id);
    }

    /**
     * 扫描 notes 目录，找到匹配 id 的笔记（用于兼容 id 定位）
     */
    private NoteDto loadByIdScan(String id) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && !path.getFileName().toString().startsWith(".")) {
                    Path noteFile = path.resolve(NOTE_FILE_NAME);
                    if (Files.exists(noteFile)) {
                        NoteDto dto = loadFromFile(path);
                        if (dto != null && id.equals(dto.id)) {
                            return dto;
                        }
                    }
                }
            }
        } catch (IOException e) {
                    logger.error("[NoteFileStorage] 查找笔记文件失败: " + e.getMessage());
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
                String name = path.getFileName().toString();
                // . 开头是隐藏/元数据，_ 开头是知识库内部数据（如 _kb 词表），都不是笔记
                if (Files.isDirectory(path) && !name.startsWith(".") && !name.startsWith("_")) {
                    NoteDto note = loadFromFile(path);
                    if (note != null) {
                        notes.add(note);
                    }
                }
            }
        } catch (IOException e) {
                    logger.error("[NoteFileStorage] 扫描笔记目录失败: " + e.getMessage());
        }

        logger.info("[NoteFileStorage] 扫描到 " + notes.size() + " 个笔记");
        return notes;
    }

    /**
     * 删除笔记文件夹
     */
    public boolean deleteNoteFolder(String key) {
        Path folderPath = findNoteFolderByKey(key);
        if (folderPath == null) {
            return false;
        }

        try {
            deleteDirectory(folderPath);
            logger.info("[NoteFileStorage] 已删除笔记文件夹: " + folderPath);
            return true;
        } catch (IOException e) {
                    logger.error("[NoteFileStorage] 删除笔记文件夹失败: " + e.getMessage());
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
                            logger.error("删除失败: {}", path);
                        }
                    });
        }
    }

    /**
     * 保存资源文件（图片等）
     */
    public Path saveAsset(String noteId, String fileName, byte[] data) {
        // 兼容：先扫描找到 id 对应的目录
        Path folderPath = null;
        NoteDto dto = loadById(noteId);
        if (dto != null && dto.key != null) {
            folderPath = findNoteFolderByKey(dto.key);
        }
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

            logger.info("[NoteFileStorage] 已保存资源: " + assetPath);
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
        appendStringArray(sb, "tags", note.tags);

        sb.append("version: ").append(note.version).append("\n");
        sb.append("deleted: ").append(note.deleted ? "true" : "false").append("\n");
        sb.append("createdAt: ").append(formatTimestamp(note.createdAt)).append("\n");
        sb.append("updatedAt: ").append(formatTimestamp(note.updatedAt)).append("\n");

        // 知识库分类与治理字段。旧笔记没有这些字段，读入时取默认值，
        // 这里统一写出，保证任何一次保存后元数据都是完整的。
        sb.append("domain: \"").append(escapeYaml(NoteDto.orDefault(note.domain, NoteDto.DEFAULT_DOMAIN))).append("\"\n");
        sb.append("topic: \"").append(escapeYaml(NoteDto.orDefault(note.topic, NoteDto.DEFAULT_TOPIC))).append("\"\n");
        sb.append("noteType: \"").append(escapeYaml(NoteDto.normalizeNoteType(note.noteType))).append("\"\n");
        sb.append("layer: \"").append(escapeYaml(NoteDto.normalizeLayer(note.layer))).append("\"\n");
        sb.append("visibility: \"").append(escapeYaml(NoteDto.normalizeVisibility(note.effectiveVisibility()))).append("\"\n");
        appendStringArray(sb, "aliases", note.aliases);

        // 私密笔记的加密参数。这些值不是机密，必须明文写入：
        // 云同步的 version/deleted 判定直接对 note.md 原文做字符串查找。
        if (note.encrypted) {
            sb.append("encrypted: true\n");
            appendIfPresent(sb, "cipherIv", note.cipherIv);
            appendIfPresent(sb, "pwdSalt", note.pwdSalt);
            appendIfPresent(sb, "pwdIv", note.pwdIv);
            appendIfPresent(sb, "pwdWrappedDek", note.pwdWrappedDek);
            appendIfPresent(sb, "masterSalt", note.masterSalt);
            appendIfPresent(sb, "masterIv", note.masterIv);
            appendIfPresent(sb, "masterWrappedDek", note.masterWrappedDek);

            // 图片引用清单必须明文保留，否则正文加密后 cleanupUnusedAssets
            // 扫不到任何引用，会把 assets 下的图片全部删掉
            appendStringArray(sb, "assets", note.assets);
        }

        sb.append("---\n\n");

        // 正文
        if (note.bodyMd != null) {
            sb.append(note.bodyMd);
        }

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String key, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(key).append(": ").append(value).append("\n");
        }
    }

    /** 写出 YAML 内联字符串数组，null 与空列表都写成 []。 */
    private void appendStringArray(StringBuilder sb, String key, List<String> values) {
        sb.append(key).append(": [");
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeYaml(values.get(i))).append("\"");
            }
        }
        sb.append("]\n");
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

            // 无 front-matter 时无法确定 id，生成占位
            note.id = note.id != null ? note.id : UUID.randomUUID().toString();
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

        // 知识库元数据。旧文件没有这些键，取默认值即可，不需要迁移脚本改写存量文件。
        note.domain = NoteDto.orDefault(unescapeYaml(values.get("domain")), NoteDto.DEFAULT_DOMAIN);
        note.topic = NoteDto.orDefault(unescapeYaml(values.get("topic")), NoteDto.DEFAULT_TOPIC);
        note.noteType = NoteDto.normalizeNoteType(unescapeYaml(values.get("noteType")));
        note.layer = NoteDto.normalizeLayer(unescapeYaml(values.get("layer")));
        note.visibility = NoteDto.normalizeVisibility(unescapeYaml(values.get("visibility")));
        note.aliases = parseTags(values.get("aliases"));

        // 私密笔记的加密参数。解析器对缺失字段宽容，旧版本笔记读取不受影响。
        note.encrypted = "true".equalsIgnoreCase(values.get("encrypted"));
        if (note.encrypted) {
            note.cipherIv = values.get("cipherIv");
            note.pwdSalt = values.get("pwdSalt");
            note.pwdIv = values.get("pwdIv");
            note.pwdWrappedDek = values.get("pwdWrappedDek");
            note.masterSalt = values.get("masterSalt");
            note.masterIv = values.get("masterIv");
            note.masterWrappedDek = values.get("masterWrappedDek");
            note.assets = parseTags(values.get("assets"));
            note.locked = true; // 从磁盘读出来时一律是锁定态，待解锁后才有明文
            // 手工把 visibility 改成 ai 也不能绕过加密
            note.visibility = NoteDto.VISIBILITY_PRIVATE;
        }
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
        for (String item : splitOutsideQuotes(tagsStr)) {
            String tag = item.trim();
            if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1);
            }
            if (!tag.isEmpty()) {
                tags.add(unescapeYaml(tag));
            }
        }

        return tags;
    }

    /**
     * 按逗号切分 YAML 内联数组，但引号内的逗号属于值本身。
     * 标签与别名里出现逗号是常态，直接 split(",") 会把一个值撕成两半。
     */
    private static List<String> splitOutsideQuotes(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
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
     * 重命名笔记文件夹（当前约束：key 不可变，此方法保留占位）
     */
    public void renameNoteFolder(String noteId, String oldKey, String newKey) {
        // 当前策略：key 不支持修改，故不执行重命名
        logger.info("[NoteFileStorage] 跳过重命名（当前不支持修改 key）: " + oldKey + " -> " + newKey);
    }
}

