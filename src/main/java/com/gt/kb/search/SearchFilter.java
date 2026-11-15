package com.gt.kb.search;

import com.gt.NoteDto;
import com.gt.kb.model.BlockMeta;

import java.util.Set;

/**
 * 检索过滤条件。include 集合非空表示「只取这些值」，exclude 集合表示「这些值一律不要」。
 * 两者都落到 Lucene 的 FILTER / MUST_NOT 子句，在打分前生效，不占候选名额。
 *
 * @param excludeStatuses 排除的块状态，如 archived
 * @param excludeLayers   排除的笔记层，如 raw
 */
public record SearchFilter(
        Set<String> domains,
        Set<String> topics,
        Set<String> types,
        Set<String> statuses,
        Set<String> visibilities,
        Set<String> layers,
        Set<String> excludeStatuses,
        Set<String> excludeLayers) {

    public SearchFilter {
        domains = copy(domains);
        topics = copy(topics);
        types = copy(types);
        statuses = copy(statuses);
        visibilities = copy(visibilities);
        layers = copy(layers);
        excludeStatuses = copy(excludeStatuses);
        excludeLayers = copy(excludeLayers);
    }

    private static Set<String> copy(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    public static SearchFilter none() {
        return new SearchFilter(null, null, null, null, null, null, null, null);
    }

    /**
     * 档位默认过滤。人：排除已归档，已废弃只降权不排除（还要标"已废弃"给人看）。
     * AI：只取明确开放的内容，废弃/归档/原始层一律不给。
     */
    public static SearchFilter forProfile(SearchProfile profile) {
        if (profile == SearchProfile.AI) {
            return new SearchFilter(null, null, null, null,
                    Set.of(NoteDto.VISIBILITY_AI), null,
                    Set.of(BlockMeta.STATUS_DEPRECATED, BlockMeta.STATUS_ARCHIVED),
                    Set.of(NoteDto.LAYER_RAW));
        }
        return new SearchFilter(null, null, null, null, null, null,
                Set.of(BlockMeta.STATUS_ARCHIVED), null);
    }

    public boolean isEmpty() {
        return domains.isEmpty() && topics.isEmpty() && types.isEmpty() && statuses.isEmpty()
                && visibilities.isEmpty() && layers.isEmpty()
                && excludeStatuses.isEmpty() && excludeLayers.isEmpty();
    }
}