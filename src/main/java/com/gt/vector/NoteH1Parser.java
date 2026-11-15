package com.gt.vector;

import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.id.BlockIdCodec;
import com.gt.kb.model.KnowledgeBlock;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记定位器：把索引结果映射回原文位置。
 *
 * 切分口径统一由 {@link DefaultChunker} 提供，索引文档由 {@link com.gt.kb.chunk.IndexBlocks} 产出，
 * 这里只负责按块 ID / 标题文本反查偏移，以及求光标所在块的范围。
 */
public class NoteH1Parser {

    /** 任意级别的 ATX 标题行，用于按标题文本回退定位。 */
    private static final Pattern HEADING_LINE = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$", Pattern.MULTILINE);

    /**
     * 按块 ID 定位：找到原文里的 {@code <!-- ku:xxxxxxxx -->} 行，返回其所属标题行的偏移。
     * ID 行是块的锚点，标题被改写也不影响定位，因此优先于按标题文本匹配。
     * 原文格式是「标题行 + ID 行」，故标题在 ID 行的上一行；无标题块的 ID 行本身就是块首。
     *
     * @return 标题行行首的字符偏移；找不到返回 -1
     */
    public static int findBlockPosition(String content, String blockId) {
        if (content == null || blockId == null || blockId.isEmpty()) {
            return -1;
        }
        String fileForm = blockId.startsWith(BlockIdCodec.MODEL_PREFIX)
                ? BlockIdCodec.FILE_PREFIX + blockId.substring(BlockIdCodec.MODEL_PREFIX.length())
                : blockId;
        String[] lines = content.split("\r\n|\r|\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains(fileForm) && BlockIdCodec.parse(line.strip()).isPresent()) {
                boolean titled = i > 0 && DefaultChunker.isBoundaryTitle(lines[i - 1],
                        i < 2 || lines[i - 2].isBlank());
                return offsetOfLine(content, (titled ? i - 1 : i) + 1);
            }
        }
        return -1;
    }

    /**
     * 按标题文本定位（块 ID 缺失时的回退）。任意级别标题都算，其次匹配整行文本，
     * 因为编号标题、日期标题和自动摘要标题都不是 ATX 标题。
     *
     * @return 标题所在行的字符偏移，找不到返回 -1
     */
    public static int findH1Position(String content, String h1Title) {
        if (content == null || h1Title == null || h1Title.isBlank()) {
            return -1;
        }
        String title = h1Title.trim();
        Matcher matcher = HEADING_LINE.matcher(content);
        while (matcher.find()) {
            if (matcher.group(2).trim().equals(title)) {
                return matcher.start();
            }
        }
        String[] lines = content.split("\r\n|\r|\n", -1);
        int offset = 0;
        for (String line : lines) {
            if (line.strip().equals(title)) {
                return offset;
            }
            offset += line.length() + 1;
        }
        return -1;
    }

    /**
     * 求出给定偏移所在知识块的范围：起点是该块首行行首，终点是下一块首行行首。
     * 与索引共用 {@link DefaultChunker} 的切分口径，保证预览分节与索引分块一致。
     *
     * @param content 笔记完整内容
     * @param offset 光标偏移
     * @return int[]{start, end}；内容切不出块时返回整篇范围
     */
    public static int[] findSectionRange(String content, int offset) {
        if (content == null || content.isEmpty()) {
            return new int[]{0, 0};
        }
        int caret = Math.max(0, Math.min(offset, content.length()));
        List<KnowledgeBlock> blocks = new DefaultChunker()
                .chunk("", "", content, ChunkProfile.forNote(null, null));
        if (blocks.isEmpty()) {
            return new int[]{0, content.length()};
        }

        int caretLine = getLineNumber(content, caret);
        int index = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).startLine() > caretLine) {
                break;
            }
            index = i;
        }
        if (index < 0) {
            return new int[]{0, offsetOfLine(content, blocks.get(0).startLine())};
        }
        int start = offsetOfLine(content, blocks.get(index).startLine());
        int end = index + 1 < blocks.size()
                ? offsetOfLine(content, blocks.get(index + 1).startLine())
                : content.length();
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
    
    /** 行号（1 起）→ 行首字符偏移。 */
    private static int offsetOfLine(String content, int line) {
        if (line <= 1) {
            return 0;
        }
        int seen = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n' && ++seen == line) {
                return i + 1;
            }
        }
        return content.length();
    }
}

