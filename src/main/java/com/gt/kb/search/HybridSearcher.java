package com.gt.kb.search;

import com.gt.AppConfig;
import com.gt.config.ConfigKeys;
import com.gt.kb.search.RrfFusion.Channel;
import com.gt.kb.search.RrfFusion.Fused;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 三通道混合检索：精确（命令/参数/报错串）、BM25（关键词）、向量（语义近义）。
 *
 * 本类只负责编排与融合，不碰 Lucene：通道由 {@link Channels} 提供，
 * 因此融合逻辑可以脱离索引单测。持有索引的实现方负责过滤、截断与文档回查。
 */
public final class HybridSearcher {

    /** 各通道按相关性降序返回索引文档 ID，已应用 filter 并截断到 limit。 */
    public interface Channels {
        List<String> exact(String query, int limit, SearchFilter filter);

        List<String> bm25(String query, int limit, SearchFilter filter);

        List<String> vector(String query, int limit, SearchFilter filter);
    }

    private final Channels channels;

    public HybridSearcher(Channels channels) {
        this.channels = channels;
    }

    public List<Fused> search(String query, SearchProfile profile, SearchFilter filter) {
        int limit = profile.candidates();
        Map<Channel, List<String>> ranked = new EnumMap<>(Channel.class);
        ranked.put(Channel.EXACT, channels.exact(query, limit, filter));
        ranked.put(Channel.BM25, channels.bm25(query, limit, filter));
        ranked.put(Channel.VECTOR, channels.vector(query, limit, filter));
        return RrfFusion.fuse(ranked, weights(), AppConfig.getInstance().getInt(ConfigKeys.SEARCH_RRF_K));
    }

    public static Map<Channel, Double> weights() {
        AppConfig config = AppConfig.getInstance();
        Map<Channel, Double> weights = new EnumMap<>(Channel.class);
        weights.put(Channel.EXACT, config.getDouble(ConfigKeys.SEARCH_RRF_WEIGHT_EXACT));
        weights.put(Channel.BM25, config.getDouble(ConfigKeys.SEARCH_RRF_WEIGHT_BM25));
        weights.put(Channel.VECTOR, config.getDouble(ConfigKeys.SEARCH_RRF_WEIGHT_VECTOR));
        return weights;
    }
}