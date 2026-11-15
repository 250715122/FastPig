package com.gt.kb.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 知识块：笔记中一个可独立引用、跳转、治理的语义单元。
 *
 * id 有两种形态：知识块 {@code ku_7f3ka2mb}（写回原文的注释是 {@code <!-- ku:7f3ka2mb -->}），
 * 以及超长块二次切分出的索引块 {@code ku_7f3ka2mb#p2}（只存在于索引中，不写文件）。
 * 索引块命中后应回取所属知识块的完整正文。
 */
public record KnowledgeBlock(
        String id,
        String noteKey,
        String title,
        int level,
        List<String> path,
        String body,
        int startLine,
        int endLine,
        String contentHash,
        BlockMeta meta) {

    /** 日期条目：日志类笔记按天切分。 */
    public static final int LEVEL_DATE = 0;
    /** 无任何边界时的段落兜底切分。 */
    public static final int LEVEL_FALLBACK = -1;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public KnowledgeBlock {
        id = id == null ? "" : id;
        noteKey = noteKey == null ? "" : noteKey;
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        path = path == null ? List.of() : List.copyOf(path);
        meta = meta == null ? BlockMeta.EMPTY : meta;
        contentHash = contentHash == null || contentHash.isEmpty() ? contentHash(path, title, body) : contentHash;
    }

    public boolean isIndexBlock() {
        return id.indexOf('#') >= 0;
    }

    /** 索引块返回所属知识块 id，知识块返回自身。 */
    public String parentBlockId() {
        int sep = id.indexOf('#');
        return sep < 0 ? id : id.substring(0, sep);
    }

    public String pathText() {
        return String.join(" > ", path);
    }

    /**
     * 块哈希：SHA-1 前 16 位，只做空白规范化（统一换行、去行尾空白、去首尾空行）。
     *
     * 面包屑和标题都参与哈希，因为它们同样进入索引文本——只哈希正文的话，
     * 改标题的块会被增量索引判为未变化而跳过重新向量化。
     * 也刻意不用清洗后的索引文本做哈希：清洗会丢掉代码内容，
     * 那样"只改了代码"的块同样会被漏掉。
     */
    public static String contentHash(List<String> path, String title, String body) {
        String src = String.join(" > ", path == null ? List.of() : path)
                + "\n" + (title == null ? "" : title)
                + "\n" + normalize(body);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-1").digest(src.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 8; i++) {
            sb.append(HEX[(digest[i] >> 4) & 0xF]).append(HEX[digest[i] & 0xF]);
        }
        return sb.toString();
    }

    private static String normalize(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String[] lines = body.split("\r\n|\r|\n", -1);
        int from = 0;
        int to = lines.length;
        while (from < to && lines[from].isBlank()) {
            from++;
        }
        while (to > from && lines[to - 1].isBlank()) {
            to--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                sb.append('\n');
            }
            sb.append(lines[i].stripTrailing());
        }
        return sb.toString();
    }
}