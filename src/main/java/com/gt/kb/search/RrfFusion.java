package com.gt.kb.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Reciprocal Rank Fusion：把多个通道的排名而不是分数加起来。
 *
 * 三个通道的分数量纲完全不同（余弦 0~1、BM25 无上界、短语命中只有相对次序），
 * 直接加权求和会被 BM25 的绝对值主导。RRF 只用名次，天然免疫量纲问题。
 *
 * 纯函数，不依赖 Lucene，可用字符串列表直接单测。
 */
public final class RrfFusion {

    public enum Channel { EXACT, BM25, VECTOR }

    /**
     * @param key    归并后的身份：融合阶段是索引文档 ID，归并阶段是知识块 ID
     * @param docKey 贡献最高分的那个索引文档 ID，供调用方回查行号与摘要
     */
    public record Fused(String key, String docKey, double score, Set<Channel> channels) {
        public Fused {
            channels = Set.copyOf(channels);
        }
    }

    private RrfFusion() {
    }

    /**
     * score(d) = Σ_通道 w_c / (k + rank_c(d))，rank 从 1 起，缺席的通道不计分。
     *
     * 每个通道的 key 列表必须已按相关性降序且无重复。权重 ≤0 视为关闭该通道。
     * 同分时按 key 字典序，保证评测结果可复现。
     */
    public static List<Fused> fuse(Map<Channel, List<String>> ranked, Map<Channel, Double> weights, int k) {
        int smooth = Math.max(1, k);
        Map<String, Double> scores = new HashMap<>();
        Map<String, EnumSet<Channel>> channels = new HashMap<>();

        for (Channel channel : Channel.values()) {
            List<String> keys = ranked.get(channel);
            double weight = weights.getOrDefault(channel, 0.0);
            if (keys == null || weight <= 0) {
                continue;
            }
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                scores.merge(key, weight / (smooth + i + 1), Double::sum);
                channels.computeIfAbsent(key, x -> EnumSet.noneOf(Channel.class)).add(channel);
            }
        }

        List<Fused> out = new ArrayList<>(scores.size());
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            out.add(new Fused(e.getKey(), e.getKey(), e.getValue(), channels.get(e.getKey())));
        }
        out.sort(Comparator.comparingDouble(Fused::score).reversed().thenComparing(Fused::key));
        return out;
    }

    /**
     * 索引块归并到所属知识块，取组内最高分，通道取并集。
     *
     * 一个知识块超长时会切出十几个索引块，不归并的话光是同一块的重复段就能占满
     * 每篇配额和整个 topK，同篇其他知识块一个都露不出来。
     *
     * 入参须已按分数降序（{@link #fuse} 的输出即满足），这样每组首个就是最高分。
     */
    public static List<Fused> mergeGroups(List<Fused> hits, Function<String, String> groupOf) {
        Map<String, Fused> best = new LinkedHashMap<>();
        Map<String, EnumSet<Channel>> union = new HashMap<>();
        for (Fused hit : hits) {
            String group = groupOf.apply(hit.key());
            best.putIfAbsent(group, hit);
            union.computeIfAbsent(group, x -> EnumSet.noneOf(Channel.class)).addAll(hit.channels());
        }
        List<Fused> out = new ArrayList<>(best.size());
        for (Map.Entry<String, Fused> e : best.entrySet()) {
            Fused winner = e.getValue();
            out.add(new Fused(e.getKey(), winner.docKey(), winner.score(), union.get(e.getKey())));
        }
        return out;
    }
}