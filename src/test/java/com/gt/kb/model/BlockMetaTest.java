package com.gt.kb.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BlockMetaTest {

    @Test
    public void emptyMeansInheritTypeAndDraftStatus() {
        assertEquals("", BlockMeta.EMPTY.type());
        assertEquals(BlockMeta.STATUS_DRAFT, BlockMeta.EMPTY.status());
        assertEquals("", BlockMeta.EMPTY.supersededBy());
        assertEquals("", BlockMeta.EMPTY.scope());
        assertEquals(List.of(), BlockMeta.EMPTY.sources());
        assertNull(BlockMeta.EMPTY.updatedAt());
    }

    @Test
    public void blankStatusFallsBackToDraft() {
        assertEquals(BlockMeta.STATUS_DRAFT, new BlockMeta("知识", "  ", null, null, null, null).status());
        assertEquals(BlockMeta.STATUS_VERIFIED,
                new BlockMeta("知识", BlockMeta.STATUS_VERIFIED, null, null, null, null).status());
    }

    @Test
    public void sourcesAreDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(List.of("ku_7f3ka2mb"));
        BlockMeta meta = new BlockMeta(null, null, null, null, mutable, 1L);
        mutable.add("ku_2b8c1d0e");
        assertEquals(List.of("ku_7f3ka2mb"), meta.sources());
        assertThrows(UnsupportedOperationException.class, () -> meta.sources().add("x"));
    }

    @Test
    public void keepsExplicitGovernanceFields() {
        BlockMeta meta = new BlockMeta("速查", BlockMeta.STATUS_DEPRECATED, "ku_2b8c1d0e",
                "Spark 3.3", List.of("https://example.invalid/doc"), 1700000000000L);
        assertEquals("速查", meta.type());
        assertEquals("ku_2b8c1d0e", meta.supersededBy());
        assertEquals("Spark 3.3", meta.scope());
        assertEquals(1700000000000L, meta.updatedAt());
    }
}