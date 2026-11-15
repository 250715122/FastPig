package com.gt.kb.search;

import com.gt.AppConfig;
import com.gt.config.ConfigKeys;
import com.gt.kb.search.RrfFusion.Channel;
import com.gt.kb.search.RrfFusion.Fused;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用假通道验证编排：候选数按档位取、过滤条件原样透传、融合结果按 RRF 排序。
 * 不碰 Lucene，也不碰真实索引。
 */
class HybridSearcherTest {

    private static final ConfigKeys[] TOUCHED = {
            ConfigKeys.SEARCH_HUMAN_CANDIDATES, ConfigKeys.SEARCH_AI_CANDIDATES,
            ConfigKeys.SEARCH_RRF_K, ConfigKeys.SEARCH_RRF_WEIGHT_EXACT,
            ConfigKeys.SEARCH_RRF_WEIGHT_BM25, ConfigKeys.SEARCH_RRF_WEIGHT_VECTOR};

    private final Map<String, String> snapshot = new HashMap<>();

    @BeforeEach
    void snapshotConfig() {
        AppConfig config = AppConfig.getInstance();
        for (ConfigKeys k : TOUCHED) {
            snapshot.put(k.key, config.getString(k.key));
        }
    }

    @AfterEach
    void restoreConfig() {
        AppConfig config = AppConfig.getInstance();
        snapshot.forEach((key, value) -> config.setString(key, value));
    }

    /** 记录每个通道收到的 limit 与 filter，并按预设返回名次。 */
    private static final class FakeChannels implements HybridSearcher.Channels {

        final Map<Channel, List<String>> answers = new HashMap<>();
        final Map<Channel, Integer> limits = new HashMap<>();
        final List<SearchFilter> filters = new ArrayList<>();
        final List<String> queries = new ArrayList<>();

        @Override
        public List<String> exact(String query, int limit, SearchFilter filter) {
            return answer(Channel.EXACT, query, limit, filter);
        }

        @Override
        public List<String> bm25(String query, int limit, SearchFilter filter) {
            return answer(Channel.BM25, query, limit, filter);
        }

        @Override
        public List<String> vector(String query, int limit, SearchFilter filter) {
            return answer(Channel.VECTOR, query, limit, filter);
        }

        private List<String> answer(Channel channel, String query, int limit, SearchFilter filter) {
            queries.add(query);
            limits.put(channel, limit);
            filters.add(filter);
            return answers.getOrDefault(channel, List.of());
        }
    }

    @Test
    @DisplayName("人档位每通道取 search.human.candidates 条，AI 档位取 search.ai.candidates 条")
    void candidateLimitFollowsProfile() {
        AppConfig config = AppConfig.getInstance();
        config.set(ConfigKeys.SEARCH_HUMAN_CANDIDATES, "40");
        config.set(ConfigKeys.SEARCH_AI_CANDIDATES, "100");

        FakeChannels channels = new FakeChannels();
        HybridSearcher searcher = new HybridSearcher(channels);
        SearchFilter filter = SearchFilter.forProfile(SearchProfile.HUMAN);

        searcher.search("docker run", SearchProfile.HUMAN, filter);
        assertEquals(40, channels.limits.get(Channel.EXACT));
        assertEquals(40, channels.limits.get(Channel.BM25));
        assertEquals(40, channels.limits.get(Channel.VECTOR));

        searcher.search("docker run", SearchProfile.AI, SearchFilter.forProfile(SearchProfile.AI));
        assertEquals(100, channels.limits.get(Channel.EXACT));
        assertEquals(100, channels.limits.get(Channel.BM25));
        assertEquals(100, channels.limits.get(Channel.VECTOR));

        // 每次检索三个通道各记一条，两轮共 6 条
        assertEquals(6, channels.queries.size());
        assertTrue(channels.queries.stream().allMatch("docker run"::equals), channels.queries.toString());
        assertEquals(filter, channels.filters.get(0), "过滤条件必须原样透传给每个通道");
    }

    @Test
    @DisplayName("三通道结果按 RRF 融合，多通道命中的文档排前面")
    void fusesAllThreeChannels() {
        AppConfig config = AppConfig.getInstance();
        config.set(ConfigKeys.SEARCH_RRF_K, "60");
        config.set(ConfigKeys.SEARCH_RRF_WEIGHT_EXACT, "1.5");
        config.set(ConfigKeys.SEARCH_RRF_WEIGHT_BM25, "1.0");
        config.set(ConfigKeys.SEARCH_RRF_WEIGHT_VECTOR, "1.0");

        FakeChannels channels = new FakeChannels();
        channels.answers.put(Channel.EXACT, List.of("a", "b"));
        channels.answers.put(Channel.BM25, List.of("b", "c"));
        channels.answers.put(Channel.VECTOR, List.of("b", "a"));

        List<Fused> out = new HybridSearcher(channels)
                .search("q", SearchProfile.HUMAN, SearchFilter.none());

        // b 三个通道都命中：1.5/62 + 1.0/61 + 1.0/61，高于只在两个通道出现的 a
        assertEquals(List.of("b", "a", "c"), out.stream().map(Fused::key).toList());
        assertEquals(Set.of(Channel.EXACT, Channel.BM25, Channel.VECTOR), out.get(0).channels());
        assertEquals(1.5 / 62 + 1.0 / 61 + 1.0 / 61, out.get(0).score(), 1e-9);
    }

    @Test
    @DisplayName("某个通道没有候选时其余通道照常出结果")
    void emptyChannelDoesNotBlockOthers() {
        FakeChannels channels = new FakeChannels();
        channels.answers.put(Channel.VECTOR, List.of("only-vector"));

        List<Fused> out = new HybridSearcher(channels)
                .search("q", SearchProfile.HUMAN, SearchFilter.none());

        assertEquals(List.of("only-vector"), out.stream().map(Fused::key).toList());
    }

    @Test
    @DisplayName("权重调 0 即关闭该通道，可用于评测时单独看某一通道的贡献")
    void zeroWeightDisablesChannel() {
        AppConfig config = AppConfig.getInstance();
        config.set(ConfigKeys.SEARCH_RRF_WEIGHT_EXACT, "0");
        config.set(ConfigKeys.SEARCH_RRF_WEIGHT_BM25, "0");
        config.set(ConfigKeys.SEARCH_RRF_WEIGHT_VECTOR, "1");

        FakeChannels channels = new FakeChannels();
        channels.answers.put(Channel.EXACT, List.of("a"));
        channels.answers.put(Channel.BM25, List.of("b"));
        channels.answers.put(Channel.VECTOR, List.of("c"));

        List<Fused> out = new HybridSearcher(channels)
                .search("q", SearchProfile.HUMAN, SearchFilter.none());

        assertEquals(List.of("c"), out.stream().map(Fused::key).toList());
    }

    @Test
    @DisplayName("三个通道全空时返回空列表，不抛异常")
    void allChannelsEmpty() {
        List<Fused> out = new HybridSearcher(new FakeChannels())
                .search("q", SearchProfile.HUMAN, SearchFilter.none());
        assertTrue(out.isEmpty());
    }
}