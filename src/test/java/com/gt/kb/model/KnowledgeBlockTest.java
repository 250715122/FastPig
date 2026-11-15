package com.gt.kb.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KnowledgeBlockTest {

    private static final List<String> PATH = List.of("演示笔记", "标题");

    private static KnowledgeBlock block(String id, String body) {
        return new KnowledgeBlock(id, "demo", "标题", 1, PATH, body, 1, 3, null, null);
    }

    private static String hash(String body) {
        return KnowledgeBlock.contentHash(List.of(), "", body);
    }

    @Test
    public void hashIs16LowercaseHex() {
        String hash = hash("some body");
        assertEquals(16, hash.length());
        assertTrue(hash.matches("[0-9a-f]{16}"), hash);
    }

    @Test
    public void hashIgnoresLineEndingsAndTrailingSpaces() {
        String lf = "line one\nline two";
        assertEquals(hash(lf), hash("line one\r\nline two"));
        assertEquals(hash(lf), hash("line one\rline two"));
        assertEquals(hash(lf), hash("line one   \nline two\t"));
    }

    @Test
    public void hashIgnoresSurroundingBlankLines() {
        assertEquals(hash("body"), hash("\n\n  \nbody\n\n \n"));
    }

    @Test
    public void hashKeepsLeadingIndentation() {
        assertNotEquals(hash("if x:\n    pass"), hash("if x:\npass"));
    }

    /** 增量索引的命门：只改代码也必须换哈希，否则不会重新向量化。 */
    @Test
    public void hashChangesWhenOnlyCodeChanges() {
        assertNotEquals(hash("部署步骤\n```bash\ngit push origin main\n```"),
                hash("部署步骤\n```bash\ngit push --force origin main\n```"));
    }

    /** 标题和面包屑同样进入索引文本，改了就必须重嵌入。 */
    @Test
    public void hashCoversTitleAndBreadcrumb() {
        String body = "正文";
        assertNotEquals(KnowledgeBlock.contentHash(PATH, "旧标题", body),
                KnowledgeBlock.contentHash(PATH, "新标题", body));
        assertNotEquals(KnowledgeBlock.contentHash(List.of("笔记A", "父"), "标题", body),
                KnowledgeBlock.contentHash(List.of("笔记A", "改名后的父"), "标题", body));
    }

    @Test
    public void hashOfNullEqualsHashOfEmpty() {
        assertEquals(hash(""), hash(null));
        assertEquals(hash(""), hash("  \n\n"));
        assertEquals(hash(""), KnowledgeBlock.contentHash(null, null, null));
    }

    @Test
    public void contentHashComputedWhenAbsent() {
        KnowledgeBlock b = block("ku_7f3ka2mb", "body text");
        assertEquals(KnowledgeBlock.contentHash(PATH, "标题", "body text"), b.contentHash());
    }

    @Test
    public void explicitContentHashIsKept() {
        KnowledgeBlock b = new KnowledgeBlock("ku_7f3ka2mb", "demo", "标题", 1, List.of(),
                "body", 1, 2, "0123456789abcdef", null);
        assertEquals("0123456789abcdef", b.contentHash());
    }

    @Test
    public void pathIsDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(PATH);
        KnowledgeBlock b = new KnowledgeBlock("ku_7f3ka2mb", "demo", "标题", 1, mutable,
                "body", 1, 2, null, null);
        mutable.add("后来加的");
        assertEquals(PATH, b.path());
        assertThrows(UnsupportedOperationException.class, () -> b.path().add("x"));
    }

    @Test
    public void nullFieldsGetDefaults() {
        KnowledgeBlock b = new KnowledgeBlock(null, null, null, 1, null, null, 0, 0, null, null);
        assertEquals("", b.id());
        assertEquals("", b.noteKey());
        assertEquals("", b.title());
        assertEquals("", b.body());
        assertEquals(List.of(), b.path());
        assertSame(BlockMeta.EMPTY, b.meta());
        assertEquals(KnowledgeBlock.contentHash(List.of(), "", ""), b.contentHash());
    }

    @Test
    public void indexBlockIdResolvesToParent() {
        KnowledgeBlock parent = block("ku_7f3ka2mb", "body");
        assertFalse(parent.isIndexBlock());
        assertEquals("ku_7f3ka2mb", parent.parentBlockId());

        KnowledgeBlock part = block("ku_7f3ka2mb#p2", "body");
        assertTrue(part.isIndexBlock());
        assertEquals("ku_7f3ka2mb", part.parentBlockId());
    }

    @Test
    public void pathTextJoinsWithArrow() {
        assertEquals("演示笔记 > 标题", block("ku_7f3ka2mb", "body").pathText());
        assertEquals("", new KnowledgeBlock("ku_7f3ka2mb", "demo", "", 1, List.of(),
                "body", 1, 1, null, null).pathText());
    }
}