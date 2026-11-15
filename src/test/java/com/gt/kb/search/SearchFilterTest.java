package com.gt.kb.search;

import com.gt.NoteDto;
import com.gt.kb.model.BlockMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFilterTest {

    @Test
    @DisplayName("null 集合归一为空集，且结果不可变")
    void nullsBecomeEmptyImmutableSets() {
        SearchFilter f = new SearchFilter(null, null, null, null, null, null, null, null);
        assertTrue(f.isEmpty());
        assertEquals(Set.of(), f.domains());
        assertThrows(UnsupportedOperationException.class, () -> f.domains().add("x"));

        Set<String> mutable = new HashSet<>(Set.of("数据库"));
        SearchFilter copied = new SearchFilter(mutable, null, null, null, null, null, null, null);
        mutable.add("消息队列");
        assertEquals(Set.of("数据库"), copied.domains(), "构造后外部改动不应影响过滤条件");
    }

    @Test
    @DisplayName("人档位：排除已归档，已废弃只降权不排除")
    void humanExcludesArchivedOnly() {
        SearchFilter f = SearchFilter.forProfile(SearchProfile.HUMAN);

        assertEquals(Set.of(BlockMeta.STATUS_ARCHIVED), f.excludeStatuses());
        assertTrue(f.excludeLayers().isEmpty());
        assertTrue(f.visibilities().isEmpty(), "人档位不按可见性收窄，private 由加密笔记不入索引保证");
        assertFalse(f.isEmpty());
    }

    @Test
    @DisplayName("AI 档位：只取 visibility=ai，排除废弃/归档/原始层")
    void aiTakesOnlyExplicitlyOpenContent() {
        SearchFilter f = SearchFilter.forProfile(SearchProfile.AI);

        assertEquals(Set.of(NoteDto.VISIBILITY_AI), f.visibilities());
        assertEquals(Set.of(BlockMeta.STATUS_DEPRECATED, BlockMeta.STATUS_ARCHIVED), f.excludeStatuses());
        assertEquals(Set.of(NoteDto.LAYER_RAW), f.excludeLayers());
    }

    @Test
    @DisplayName("none() 不加任何限制，isEmpty 只看八个集合")
    void noneIsEmpty() {
        assertTrue(SearchFilter.none().isEmpty());
        assertFalse(new SearchFilter(null, null, null, null, null, null,
                null, Set.of(NoteDto.LAYER_RAW)).isEmpty());
    }
}