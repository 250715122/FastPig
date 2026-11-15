package com.gt.kb.id;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockIdCodecTest {

    private static final String ID = "ku_7f3ka2mb";

    @Test
    public void parsesPlainIdLine() {
        Optional<BlockIdCodec.IdLine> parsed = BlockIdCodec.parse("<!-- ku:7f3ka2mb -->");
        assertTrue(parsed.isPresent());
        assertEquals(ID, parsed.get().id());
        assertEquals(Map.of(), parsed.get().attrs());
    }

    @Test
    public void parsesAttributesInWrittenOrder() {
        BlockIdCodec.IdLine line = BlockIdCodec.parse(
                "<!-- ku:7f3ka2mb status=verified scope=\"Spark 3.3\" by=ku:2b8c1d0e -->").orElseThrow();
        assertEquals(ID, line.id());
        assertEquals(List.of("status", "scope", "by"), List.copyOf(line.attrs().keySet()));
        assertEquals("verified", line.attrs().get("status"));
        assertEquals("Spark 3.3", line.attrs().get("scope"));
        assertEquals("ku:2b8c1d0e", line.attrs().get("by"));
    }

    @Test
    public void toleratesMissingInnerSpacesAndTrailingWhitespace() {
        assertTrue(BlockIdCodec.parse("<!--ku:7f3ka2mb-->").isPresent());
        assertTrue(BlockIdCodec.parse("<!--   ku:7f3ka2mb   -->  \r").isPresent());
    }

    @Test
    public void rejectsNonIdLines() {
        List<String> bad = List.of(
                "",
                "# 标题",
                "正文 <!-- ku:7f3ka2mb -->",
                "<!-- ku:7f3ka2mb --> 后缀",
                "  <!-- ku:7f3ka2mb -->",
                "<!-- ku:7f3ka2m -->",
                "<!-- ku:7f3ka2mbc -->",
                "<!-- ku:7f3ka2m1 -->",
                "<!-- ku:7F3KA2MB -->",
                "<!-- KU:7f3ka2mb -->",
                "<!-- ku_7f3ka2mb -->",
                "<!-- ku:7f3ka2mb status= -->");
        for (String line : bad) {
            assertFalse(BlockIdCodec.parse(line).isPresent(), "不该识别为 ID 行: " + line);
        }
        assertFalse(BlockIdCodec.parse(null).isPresent());
    }

    @Test
    public void formatsPlainAndBack() {
        assertEquals("<!-- ku:7f3ka2mb -->", BlockIdCodec.format(ID));
        assertEquals(ID, BlockIdCodec.parse(BlockIdCodec.format(ID)).orElseThrow().id());
    }

    @Test
    public void formatRoundTripsAttributes() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("status", "verified");
        attrs.put("scope", "Spark 3.3");
        attrs.put("by", "ku:2b8c1d0e");
        String line = BlockIdCodec.format(new BlockIdCodec.IdLine(ID, attrs));

        assertEquals("<!-- ku:7f3ka2mb status=verified scope=\"Spark 3.3\" by=ku:2b8c1d0e -->", line);
        assertEquals(attrs, BlockIdCodec.parse(line).orElseThrow().attrs());
    }

    @Test
    public void formatQuotesEmptyValue() {
        String line = BlockIdCodec.format(new BlockIdCodec.IdLine(ID, Map.of("scope", "")));
        assertEquals("<!-- ku:7f3ka2mb scope=\"\" -->", line);
        assertEquals("", BlockIdCodec.parse(line).orElseThrow().attrs().get("scope"));
    }

    /** 属性值里带引号或 --> 会毁掉整行注释，必须剔除后仍能解析。 */
    @Test
    public void formatNeutralizesCommentBreakingValues() {
        String line = BlockIdCodec.format(new BlockIdCodec.IdLine(ID, Map.of("scope", "a\"b --> c")));
        assertEquals("<!-- ku:7f3ka2mb scope=\"ab -> c\" -->", line);
        assertEquals("ab -> c", BlockIdCodec.parse(line).orElseThrow().attrs().get("scope"));
    }

    @Test
    public void formatRejectsInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> BlockIdCodec.format("ku_7f3ka2m1"));
        assertThrows(IllegalArgumentException.class, () -> BlockIdCodec.format("7f3ka2mb"));
        assertThrows(IllegalArgumentException.class, () -> BlockIdCodec.format((String) null));
    }

    @Test
    public void isValidIdChecksPrefixLengthAndAlphabet() {
        assertTrue(BlockIdCodec.isValidId(ID));
        assertTrue(BlockIdCodec.isValidId("ku_aaaa2222"));
        assertFalse(BlockIdCodec.isValidId(null));
        assertFalse(BlockIdCodec.isValidId(""));
        assertFalse(BlockIdCodec.isValidId("ku:7f3ka2mb"));
        assertFalse(BlockIdCodec.isValidId("ku_7f3ka2m"));
        assertFalse(BlockIdCodec.isValidId("ku_7f3ka2mbc"));
        assertFalse(BlockIdCodec.isValidId("ku_7f3ka2m0"));
        assertFalse(BlockIdCodec.isValidId("ku_7f3ka2m9"));
        assertFalse(BlockIdCodec.isValidId("ku_7F3KA2MB"));
    }

    @Test
    public void attrsAreImmutableAndOrdered() {
        Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("status", "draft");
        mutable.put("scope", "Spark 3.3");
        BlockIdCodec.IdLine line = new BlockIdCodec.IdLine(ID, mutable);
        mutable.put("by", "ku_2b8c1d0e");

        assertEquals(List.of("status", "scope"), List.copyOf(line.attrs().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> line.attrs().put("x", "y"));
    }

    @Test
    public void lastDuplicateAttributeWins() {
        BlockIdCodec.IdLine line = BlockIdCodec.parse(
                "<!-- ku:7f3ka2mb status=draft status=verified -->").orElseThrow();
        assertEquals("verified", line.attrs().get("status"));
        assertEquals(1, line.attrs().size());
    }
}