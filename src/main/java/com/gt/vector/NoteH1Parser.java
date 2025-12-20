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
            
            // 构建索引内容：描述 + H1 标题 + 正文前 N 字
            String indexContent = buildIndexContent(noteDesc, current.title, body);
            
            H1Block block = new H1Block();
            block.noteKey = noteKey;
            block.noteDesc = noteDesc;
            block.h1Title = current.title;
            block.indexContent = indexContent;
            block.lineNumber = getLineNumber(content, current.startPos);
            
            blocks.add(block);
        }
        
        // 如果没有 H1，将整篇文档作为一个块（但只有 indexContent 非空才添加）
        if (blocks.isEmpty()) {
            String indexContent = buildIndexContent(noteDesc, "", content);
            // 只有 indexContent 非空才添加，避免生成无用的空索引
            if (indexContent != null && !indexContent.isEmpty()) {
                H1Block block = new H1Block();
                block.noteKey = noteKey;
                block.noteDesc = noteDesc;
                block.h1Title = ""; // 无标题
                block.indexContent = indexContent;
                block.lineNumber = 1;
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
            blocks.add(0, noteNameBlock);  // 插入到列表开头
        }
        
        return blocks;
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
     * 构建索引内容
     * 只用 H1 标题做索引，支持模糊搜索
     */
    private static String buildIndexContent(String noteDesc, String h1Title, String body) {
        // 只用 H1 标题做索引
        return h1Title != null ? h1Title : "";
    }
    
    /**
     * 清理 Markdown 语法，保留纯文本
     */
    private static String cleanMarkdown(String text) {
        if (text == null) return "";
        
        String result = text;
        
        // 移除代码块
        result = result.replaceAll("```[\\s\\S]*?```", " ");
        result = result.replaceAll("`[^`]+`", " ");
        
        // 移除链接，保留文本
        result = result.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        
        // 移除图片
        result = result.replaceAll("!\\[[^\\]]*\\]\\([^)]+\\)", " ");
        
        // 移除标题标记（但保留标题文本）
        result = result.replaceAll("^#{1,6}\\s+", "");
        
        // 移除粗体/斜体标记
        result = result.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        result = result.replaceAll("\\*([^*]+)\\*", "$1");
        result = result.replaceAll("__([^_]+)__", "$1");
        result = result.replaceAll("_([^_]+)_", "$1");
        
        // 移除列表标记
        result = result.replaceAll("^[\\s]*[-*+]\\s+", "");
        result = result.replaceAll("^[\\s]*\\d+\\.\\s+", "");
        
        // 移除引用标记
        result = result.replaceAll("^>\\s*", "");
        
        // 移除水平线
        result = result.replaceAll("^[-*_]{3,}$", "");
        
        // 合并多个空白字符
        result = result.replaceAll("\\s+", " ");
        
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
        
        @Override
        public String toString() {
            return String.format("H1Block{noteKey='%s', noteDesc='%s', h1Title='%s', line=%d}", 
                noteKey, noteDesc, h1Title, lineNumber);
        }
    }
}

