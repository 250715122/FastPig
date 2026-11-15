package com.gt.kb.search;

import com.gt.kb.search.RrfFusion.Channel;
import com.gt.kb.search.RrfFusion.Fused;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRF 是纯函数，用字符串列表就能把名次、权重、平滑常数、归并这几条规则全部钉住。
 */
class RrfFusionTest {

    private static final double EPS = 1e-9;

    private static Map<Channel, Double> weights(double exact, double bm25, double vector) {
        Map<Channel, Double> w = new EnumMap<>(Channel.class);
        w.put(Channel.EXACT, exact);
        w.put(Channel.BM25, bm25);
        w.put(Channel.VECTOR, vector);
        return w;
    }

    private static Map<Channel, List<String>> ranked(Channel channel, String... keys) {
        Map<Channel, List<String>> r = new EnumMap<>(Channel.class);
        r.put(channel, List.of(keys));
        return r;
    }

    @Test
    @DisplayName("单通道：分数就是 w/(k+rank)，名次越靠前分越高")
    void singleChannelScoresByRank() {
        List<Fused> out = RrfFusion.fuse(ranked(Channel.BM25, "a", "b", "c"),
                weights(0, 1, 0), 60);

        assertEquals(List.of("a", "b", "c"), out.stream().map(Fused::key).toList());
        assertEquals(1.0 / 61, out.get(0).score(), EPS);
        assertEquals(1.0 / 62, out.get(1).score(), EPS);
        assertEquals(1.0 / 63, out.get(2).score(), EPS);
        assertEquals(Set.of(Channel.BM25), out.get(0).channels());
    }

    @Test
    @DisplayName("多通道命中同一文档：分数相加，通道取并集")
    void scoresAddAcrossChannels() {
        Map<Channel, List<String>> r = new EnumMap<>(Channel.class);
        r.put(Channel.EXACT, List.of("a", "b"));
        r.put(Channel.VECTOR, List.of("b", "a"));

        List<Fused> out = RrfFusion.fuse(r, weights(1.5, 0, 1.0), 60);

        // a: 1.5/61 + 1.0/62；b: 1.5/62 + 1.0/61 —— a 在权重更高的通道里排第一，应当胜出
        assertEquals("a", out.get(0).key());
        assertEquals(1.5 / 61 + 1.0 / 62, out.get(0).score(), EPS);
        assertEquals(Set.of(Channel.EXACT, Channel.VECTOR), out.get(0).channels());
        assertEquals(1.5 / 62 + 1.0 / 61, out.get(1).score(), EPS);
    }

    @Test
    @DisplayName("只在一个通道出现的文档照样参与融合，缺席的通道不计分")
    void absentChannelContributesNothing() {
        Map<Channel, List<String>> r = new EnumMap<>(Channel.class);
        r.put(Channel.EXACT, List.of("only-exact"));
        r.put(Channel.BM25, List.of("only-bm25"));

        List<Fused> out = RrfFusion.fuse(r, weights(1.5, 1.0, 0), 60);

        assertEquals(2, out.size());
        assertEquals("only-exact", out.get(0).key());
        assertEquals(Set.of(Channel.EXACT), out.get(0).channels());
        assertEquals(1.5 / 61, out.get(0).score(), EPS);
        assertEquals(1.0 / 61, out.get(1).score(), EPS);
    }

    @Test
    @DisplayName("权重 ≤0 视为关闭该通道，它的候选完全不进结果")
    void nonPositiveWeightDisablesChannel() {
        Map<Channel, List<String>> r = new EnumMap<>(Channel.class);
        r.put(Channel.EXACT, List.of("a"));
        r.put(Channel.VECTOR, List.of("b"));

        List<Fused> out = RrfFusion.fuse(r, weights(0, 0, -1), 60);
        assertTrue(out.isEmpty(), out.toString());

        List<Fused> onlyExact = RrfFusion.fuse(r, weights(1.5, 0, 0), 60);
        assertEquals(List.of("a"), onlyExact.stream().map(Fused::key).toList());
    }

    @Test
    @DisplayName("同分按 key 字典序，保证评测可复现")
    void tiesBreakByKey() {
        // 三个通道各命中一个不同文档且名次相同 → 分数完全相等
        Map<Channel, List<String>> r = new EnumMap<>(Channel.class);
        r.put(Channel.EXACT, List.of("z"));
        r.put(Channel.BM25, List.of("a"));
        r.put(Channel.VECTOR, List.of("m"));

        List<Fused> tied = RrfFusion.fuse(r, weights(1, 1, 1), 60);

        assertEquals(3, tied.size());
        assertEquals(tied.get(0).score(), tied.get(1).score(), EPS);
        assertEquals(List.of("a", "m", "z"), tied.stream().map(Fused::key).toList());
    }

    @Test
    @DisplayName("k 越大头部与尾部的分差越小，k 至少为 1")
    void smoothConstantControlsHeadAdvantage() {
        double smallK = RrfFusion.fuse(ranked(Channel.BM25, "a", "b"), weights(0, 1, 0), 1)
                .get(0).score();
        double largeK = RrfFusion.fuse(ranked(Channel.BM25, "a", "b"), weights(0, 1, 0), 1000)
                .get(0).score();
        assertTrue(smallK > largeK);

        // k=0 会被抬到 1，不能出现除零；rank 1 的分数是 1/(1+1)
        List<Fused> zero = RrfFusion.fuse(ranked(Channel.BM25, "a"), weights(0, 1, 0), 0);
        assertEquals(0.5, zero.get(0).score(), EPS);
    }

    @Test
    @DisplayName("空输入与 null 通道列表都不报错")
    void emptyInputIsSafe() {
        assertTrue(RrfFusion.fuse(new EnumMap<>(Channel.class), weights(1, 1, 1), 60).isEmpty());
        assertTrue(RrfFusion.fuse(ranked(Channel.BM25), weights(1, 1, 1), 60).isEmpty());
    }

    @Test
    @DisplayName("归并取组内最高分，保留获胜文档 ID，通道取并集，顺序不变")
    void mergeGroupsKeepsBestAndUnionsChannels() {
        List<Fused> hits = List.of(
                new Fused("ku_a#p2", "ku_a#p2", 0.9, Set.of(Channel.EXACT)),
                new Fused("ku_b#p1", "ku_b#p1", 0.8, Set.of(Channel.BM25)),
                new Fused("ku_a#p1", "ku_a#p1", 0.7, Set.of(Channel.VECTOR)),
                new Fused("ku_b#p2", "ku_b#p2", 0.6, Set.of(Channel.EXACT, Channel.VECTOR)));

        List<Fused> merged = RrfFusion.mergeGroups(hits, key -> key.substring(0, key.indexOf('#')));

        assertEquals(List.of("ku_a", "ku_b"), merged.stream().map(Fused::key).toList());
        assertEquals("ku_a#p2", merged.get(0).docKey());
        assertEquals(0.9, merged.get(0).score(), EPS);
        assertEquals(Set.of(Channel.EXACT, Channel.VECTOR), merged.get(0).channels());
        assertEquals("ku_b#p1", merged.get(1).docKey());
        assertEquals(Set.of(Channel.BM25, Channel.EXACT, Channel.VECTOR), merged.get(1).channels());
    }

    @Test
    @DisplayName("归并后同一知识块只出现一次，索引块再多也不占满 topK")
    void mergeGroupsDeduplicates() {
        List<Fused> hits = List.of(
                new Fused("ku_a#p1", "ku_a#p1", 0.9, Set.of(Channel.BM25)),
                new Fused("ku_a#p2", "ku_a#p2", 0.8, Set.of(Channel.BM25)),
                new Fused("ku_a#p3", "ku_a#p3", 0.7, Set.of(Channel.BM25)));

        List<Fused> merged = RrfFusion.mergeGroups(hits, key -> "ku_a");
        assertEquals(1, merged.size());
        assertEquals("ku_a#p1", merged.get(0).docKey());
    }
}