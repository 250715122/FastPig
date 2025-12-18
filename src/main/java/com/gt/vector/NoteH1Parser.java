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
     * @param noteKey 笔记 key（文件夹名）
     * @param noteName 笔记名称（用于索引内容）
     * @param content 笔记完整内容
     * @return H1 块列表
     */
    public static List<H1Block> parse(String noteKey, String noteName, String content) {
        List<H1Block> blocks = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return blocks;
        }
        
        // 查找所有 H1 标题及其位置
        Matcher matcher = H1_PATTERN.matcher(content);
        List<H1Match> matches = new ArrayList<>();
        
        while (matcher.find()) {
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
            
            // 构建索引内容：笔记名 + H1 标题 + 正文前 N 字
            String indexContent = buildIndexContent(noteName, current.title, body);
            
            H1Block block = new H1Block();
            block.noteKey = noteKey;
            block.h1Title = current.title;
            block.indexContent = indexContent;
            block.lineNumber = getLineNumber(content, current.startPos);
            
            blocks.add(block);
        }
        
        // 如果没有 H1，将整篇文档作为一个块
        if (blocks.isEmpty()) {
            H1Block block = new H1Block();
            block.noteKey = noteKey;
            block.h1Title = ""; // 无标题
            block.indexContent = buildIndexContent(noteName, "", content);
            block.lineNumber = 1;
            blocks.add(block);
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
     * 格式：笔记名 + H1 标题 + 正文前 N 字
     */
    private static String buildIndexContent(String noteName, String h1Title, String body) {
        StringBuilder sb = new StringBuilder();
        
        // 添加笔记名
        if (noteName != null && !noteName.isEmpty()) {
            sb.append(noteName);
        }
        
        // 添加 H1 标题
        if (h1Title != null && !h1Title.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(h1Title);
        }
        
        // 添加正文（截取）
        if (body != null && !body.isEmpty()) {
            // 清理 Markdown 语法
            String cleanBody = cleanMarkdown(body);
            
            if (!cleanBody.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                
                // 截取前 N 字
                if (cleanBody.length() > MAX_CONTENT_LENGTH) {
                    sb.append(cleanBody.substring(0, MAX_CONTENT_LENGTH));
                } else {
                    sb.append(cleanBody);
                }
            }
        }
        
        return sb.toString();
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
        public String noteKey;
        public String h1Title;
        public String indexContent;
        public int lineNumber;
        
        @Override
        public String toString() {
            return String.format("H1Block{noteKey='%s', h1Title='%s', line=%d}", 
                noteKey, h1Title, lineNumber);
        }
    }
}

