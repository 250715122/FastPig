package com.gt.vector;

import java.util.*;
import java.util.regex.*;

/**
 * 笔记 H1 解析器
 * 解析 Markdown 文件中的 H1 标题块，生成用于向量索引的内容
 */
public class NoteH1Parser {
    
    // 匹配 H1 标题的正则：行首 # 后跟空格和内容
    private static final Pattern H1_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    
    // 索引内容最大长度（正文截取）
    private static final int MAX_CONTENT_LENGTH = 300;

    // 清洗前的正文截断长度，用来把单块的清洗开销限制成常数
    private static final int PRECLEAN_LIMIT = MAX_CONTENT_LENGTH * 12;

    /**
     * Markdown 清洗规则。
     *
     * 必须预编译：String.replaceAll 每次调用都会重新编译正则，
     * 而这里有十几条规则、每个 H1 块都要走一遍，长笔记下编译开销会盖过匹配开销。
     * 刻意不加 MULTILINE，与历史行为保持一致，否则所有块的哈希都会变、触发全量重新向量化。
     */
    private static final Pattern[] CLEAN_PATTERNS = {
            Pattern.compile("```[\\s\\S]*?```"),
            Pattern.compile("`[^`]+`"),
            Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)"),
            Pattern.compile("!\\[[^\\]]*\\]\\([^)]+\\)"),
            Pattern.compile("^#{1,6}\\s+"),
            Pattern.compile("\\*\\*([^*]+)\\*\\*"),
            Pattern.compile("\\*([^*]+)\\*"),
            Pattern.compile("__([^_]+)__"),
            Pattern.compile("_([^_]+)_"),
            Pattern.compile("^[\\s]*[-*+]\\s+"),
            Pattern.compile("^[\\s]*\\d+\\.\\s+"),
            Pattern.compile("^>\\s*"),
            Pattern.compile("^[-*_]{3,}$"),
            Pattern.compile("\\s+")
    };

    private static final String[] CLEAN_REPLACEMENTS = {
            " ", " ", "$1", " ", "", "$1", "$1", "$1", "$1", "", "", "", "", " "
    };
    
    /**
     * 解析笔记内容，提取所有 H1 块
     * 
     * @param noteKey 笔记 key（快捷命令）
     * @param noteDesc 笔记描述（用于索引内容和展示）
     * @param content 笔记完整内容
     * @return H1 块列表
     */
    public static List<H1Block> parse(String noteKey, String noteDesc, String content) {
        List<H1Block> blocks = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return blocks;
        }
        
        // 先找出所有代码块的范围（避免将代码注释误识别为 H1）
        List<int[]> codeBlockRanges = findCodeBlockRanges(content);
        
        // 查找所有 H1 标题及其位置
        Matcher matcher = H1_PATTERN.matcher(content);
        List<H1Match> matches = new ArrayList<>();
        
        while (matcher.find()) {
            // 跳过代码块内的匹配（如 Python 注释）
            if (isInsideCodeBlock(matcher.start(), codeBlockRanges)) {
                continue;
            }
            
            H1Match match = new H1Match();
            match.title = matcher.group(1).trim();
            match.startPos = matcher.start();
            match.endPos = matcher.end();
            matches.add(match);
        }
        
        // 同名标题在笔记内的出现序号，用于生成互不冲突且位置稳定的 docId
        Map<String, Integer> titleCounter = new HashMap<>();
        
        // 行号增量推进。matches 按位置递增，只需在相邻两个 H1 之间数换行，
        // 每个块都调 getLineNumber 从头扫的话整体是 O(正文长度 × 块数)
        int scannedPos = 0;
        int scannedLine = 1;
        
        // 为每个 H1 提取正文内容
        for (int i = 0; i < matches.size(); i++) {
            H1Match current = matches.get(i);
            
            // 确定正文结束位置（下一个 H1 开始或文档结束）
            int bodyEnd;
            if (i + 1 < matches.size()) {
                bodyEnd = matches.get(i + 1).startPos;
            } else {
                bodyEnd = content.length();
            }
            
            // 提取正文（从 H1 行之后到下一个 H1 之前）
            String body = "";
            if (current.endPos < bodyEnd) {
                body = content.substring(current.endPos, bodyEnd).trim();
            }
            
            // 构建索引内容：H1 标题 + 正文前 N 字
            String indexContent = buildIndexContent(current.title, body);
            
            H1Block block = new H1Block();
            block.noteKey = noteKey;
            block.noteDesc = noteDesc;
            block.h1Title = current.title;
            block.indexContent = indexContent;
            while (scannedPos < current.startPos) {
                if (content.charAt(scannedPos) == '\n') scannedLine++;
                scannedPos++;
            }
            block.lineNumber = scannedLine;
            block.dupIndex = titleCounter.merge(current.title, 1, Integer::sum) - 1;
            block.blockHash = hashContent(indexContent);
            
            blocks.add(block);
        }
        
        // 如果没有 H1，将整篇文档作为一个块（但只有 indexContent 非空才添加）
        if (blocks.isEmpty()) {
            String indexContent = buildIndexContent("", content);
            // 只有 indexContent 非空才添加，避免生成无用的空索引
            if (indexContent != null && !indexContent.isEmpty()) {
                H1Block block = new H1Block();
                block.noteKey = noteKey;
                block.noteDesc = noteDesc;
                block.h1Title = ""; // 无标题
                block.indexContent = indexContent;
                block.lineNumber = 1;
                block.dupIndex = 0;
                block.blockHash = hashContent(indexContent);
                blocks.add(block);
            }
        }
        
        // 添加笔记名单独索引，支持精确搜索笔记名（如 linux、dlmhrz）
        // 使用特殊标记 __NOTE_NAME__ 避免与其他索引的 docId 冲突
        if (noteKey != null && !noteKey.isEmpty()) {
            H1Block noteNameBlock = new H1Block();
            noteNameBlock.noteKey = noteKey;
            noteNameBlock.noteDesc = noteDesc;
            noteNameBlock.h1Title = "__NOTE_NAME__";  // 特殊标记，避免 docId 冲突
            noteNameBlock.indexContent = noteKey;  // 只用笔记名做索引
            noteNameBlock.lineNumber = 1;
            noteNameBlock.dupIndex = 0;
            noteNameBlock.blockHash = hashContent(noteKey);
            blocks.add(0, noteNameBlock);  // 插入到列表开头
        }
        
        return blocks;
    }
    
    /**
     * 计算索引内容的哈希，作为块级差量比对的依据。
     * 保存时逐块比对哈希，只有内容真正变化的块才需要重新 embed。
     */
    public static String hashContent(String content) {
        String src = (content == null) ? "" : content;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(src.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(src.hashCode());
        }
    }
    
    /**
     * 在文本中查找 H1 标题的位置
     * 
     * @param content 文本内容
     * @param h1Title H1 标题
     * @return 标题所在行的字符偏移，找不到返回 -1
     */
    public static int findH1Position(String content, String h1Title) {
        if (content == null || h1Title == null) {
            return -1;
        }
        
        // 构建精确匹配模式
        String escapedTitle = Pattern.quote(h1Title.trim());
        Pattern pattern = Pattern.compile("^#\\s+" + escapedTitle + "\\s*$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return matcher.start();
        }
        
        return -1;
    }
    
    /**
     * 求出给定偏移所在 H1 区块的范围：起点是该 H1 的行首，终点是下一个 H1 的行首。
     * 与 parse() 共用同一套 H1 判定（含代码块屏蔽），保证预览分节与索引分块口径一致。
     *
     * @param content 笔记完整内容
     * @param offset  光标偏移
     * @return int[]{start, end}；内容中没有 H1 时返回整篇范围
     */
    public static int[] findSectionRange(String content, int offset) {
        if (content == null || content.isEmpty()) {
            return new int[]{0, 0};
        }
        int caret = Math.max(0, Math.min(offset, content.length()));

        List<int[]> codeBlockRanges = findCodeBlockRanges(content);
        Matcher matcher = H1_PATTERN.matcher(content);

        int start = 0;
        int end = content.length();
        while (matcher.find()) {
            if (isInsideCodeBlock(matcher.start(), codeBlockRanges)) {
                continue;
            }
            if (matcher.start() <= caret) {
                start = matcher.start();
            } else {
                end = matcher.start();
                break;
            }
        }
        return new int[]{start, end};
    }

    /**
     * 获取某偏移位置对应的行号（从 1 开始）
     */
    public static int getLineNumber(String content, int offset) {
        if (content == null || offset < 0 || offset > content.length()) {
            return 1;
        }
        
        int lineNum = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNum++;
            }
        }
        return lineNum;
    }
    
    /**
     * 构建索引内容：H1 标题 + 正文前 MAX_CONTENT_LENGTH 字。
     *
     * 正文必须参与 embedding，否则"向量检索"实际只是在匹配标题字符串，
     * 正文里写了但标题没提到的内容永远检索不到。
     * 标题放在最前面，保证它在截断后的内容里始终占有权重。
     */
    private static String buildIndexContent(String h1Title, String body) {
        StringBuilder sb = new StringBuilder();
        if (h1Title != null && !h1Title.isEmpty()) {
            sb.append(h1Title);
        }
        
        // 先截断再清洗。最终只保留 MAX_CONTENT_LENGTH 个字，
        // 把整块正文（可能几十 KB）完整跑一遍正则再丢掉 99% 纯属浪费；
        // 留出十几倍余量，足够覆盖清洗过程中被删掉的标记。
        String source = body != null && body.length() > PRECLEAN_LIMIT
                ? body.substring(0, PRECLEAN_LIMIT)
                : body;
        String cleanBody = cleanMarkdown(source);
        if (!cleanBody.isEmpty()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(cleanBody.length() > MAX_CONTENT_LENGTH
                    ? cleanBody.substring(0, MAX_CONTENT_LENGTH)
                    : cleanBody);
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 清理 Markdown 语法，保留纯文本
     */
    private static String cleanMarkdown(String text) {
        if (text == null) return "";
        
        String result = text;
        for (int i = 0; i < CLEAN_PATTERNS.length; i++) {
            result = CLEAN_PATTERNS[i].matcher(result).replaceAll(CLEAN_REPLACEMENTS[i]);
        }
        return result.trim();
    }
    
    /**
     * 查找所有代码块的范围
     * 支持 ``` 和 ~~~ 两种代码块标记
     * 
     * @param content 文本内容
     * @return 代码块范围列表，每个元素是 [start, end]
     */
    private static List<int[]> findCodeBlockRanges(String content) {
        List<int[]> ranges = new ArrayList<>();
        
        // 匹配 ``` 或 ~~~ 开头的代码块
        Pattern codeBlockPattern = Pattern.compile("^(```|~~~).*?$", Pattern.MULTILINE);
        Matcher matcher = codeBlockPattern.matcher(content);
        
        int blockStart = -1;
        String openMarker = null;
        
        while (matcher.find()) {
            String marker = matcher.group(1);
            
            if (blockStart == -1) {
                // 找到代码块开始
                blockStart = matcher.start();
                openMarker = marker;
            } else if (marker.equals(openMarker)) {
                // 找到匹配的代码块结束
                ranges.add(new int[]{blockStart, matcher.end()});
                blockStart = -1;
                openMarker = null;
            }
            // 如果 marker 不匹配，说明是嵌套的不同类型标记，忽略
        }
        
        // 如果有未闭合的代码块，延伸到文档末尾
        if (blockStart != -1) {
            ranges.add(new int[]{blockStart, content.length()});
        }
        
        return ranges;
    }
    
    /**
     * 检查某个位置是否在代码块内
     * 
     * @param position 位置偏移
     * @param codeBlockRanges 代码块范围列表
     * @return 是否在代码块内
     */
    private static boolean isInsideCodeBlock(int position, List<int[]> codeBlockRanges) {
        for (int[] range : codeBlockRanges) {
            if (position >= range[0] && position < range[1]) {
                return true;
            }
        }
        return false;
    }
    
    // ==================== 内部类 ====================
    
    /**
     * H1 匹配结果
     */
    private static class H1Match {
        String title;
        int startPos;
        int endPos;
    }
    
    /**
     * H1 块数据
     */
    public static class H1Block {
        public String noteKey;    // 快捷命令
        public String noteDesc;   // 描述
        public String h1Title;    // H1 标题
        public String indexContent; // 索引内容
        public int lineNumber;    // 行号
        public int dupIndex;      // 同名标题在笔记内的出现序号（0 起）
        public String blockHash;  // indexContent 的哈希，差量比对用
        
        @Override
        public String toString() {
            return String.format("H1Block{noteKey='%s', noteDesc='%s', h1Title='%s', dup=%d, line=%d}", 
                noteKey, noteDesc, h1Title, dupIndex, lineNumber);
        }
    }
}

