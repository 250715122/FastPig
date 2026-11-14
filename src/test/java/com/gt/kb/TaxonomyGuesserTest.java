package com.gt.kb;

import com.gt.NoteDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TaxonomyGuesserTest {

    private static final String YAML = """
            version: 1
            domains:
              - name: 大数据
                topics: [Spark, Hive]
                aliases:
                  Spark: [rdd, dataframe]
              - name: 数据库
                topics: [MySQL]
              - name: 待分类
                topics: [待分类]
            """;

    private final Taxonomy taxonomy = Taxonomy.parse(YAML);

    private static NoteDto note(String key, String desc, String domain, String topic) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.desc = desc;
        n.domain = domain;
        n.topic = topic;
        n.version = 1;
        return n;
    }

    @Test
    public void keyHitsTopicName() {
        TaxonomyGuesser.Guess g = TaxonomyGuesser.matchKeyword(taxonomy, "sparkdev", "开发笔记");
        assertEquals("大数据", g.domain());
        assertEquals("Spark", g.topic());
        assertEquals("keyword", g.source());
    }

    @Test
    public void keyHitsAlias() {
        assertEquals("Spark", TaxonomyGuesser.matchKeyword(taxonomy, "rddzj", "总结").topic());
    }

    @Test
    public void descHitsWhenKeyDoesNot() {
        assertEquals("MySQL", TaxonomyGuesser.matchKeyword(taxonomy, "bjsy", "MySQL 索引失效").topic());
    }

    @Test
    public void keyWinsOverDesc() {
        TaxonomyGuesser.Guess g = TaxonomyGuesser.matchKeyword(taxonomy, "sparksql", "MySQL 对比");
        assertEquals("大数据", g.domain());
    }

    @Test
    public void noKeywordMatchReturnsNull() {
        assertNull(TaxonomyGuesser.matchKeyword(taxonomy, "zzz", "无关内容"));
    }

    @Test
    public void withoutEmbedderFallsBackToUnclassified() {
        TaxonomyGuesser guesser = new TaxonomyGuesser(taxonomy, null, 0.55);
        TaxonomyGuesser.Guess g = guesser.guess("zzz", "无关内容", "正文", List.of());
        assertEquals(NoteDto.DEFAULT_DOMAIN, g.domain());
        assertEquals("none", g.source());
    }

    @Test
    public void centroidMatchWinsWhenKeywordMisses() {
        List<NoteDto> classified = List.of(
                note("aaa", "甲", "大数据", "Spark"),
                note("bbb", "乙", "大数据", "Spark"),
                note("ccc", "丙", "数据库", "MySQL"));
        // Spark 类靠近 [1,0]，MySQL 类靠近 [0,1]，查询贴近 Spark
        TaxonomyGuesser.Embedder embedder = text -> {
            if (text.startsWith("aaa") || text.startsWith("bbb") || text.startsWith("zzz")) {
                return new float[]{1f, 0.1f};
            }
            return new float[]{0.1f, 1f};
        };
        TaxonomyGuesser.Guess g = new TaxonomyGuesser(taxonomy, embedder, 0.55)
                .guess("zzz", "无关描述", "正文", classified);
        assertEquals("大数据", g.domain());
        assertEquals("Spark", g.topic());
        assertEquals("centroid", g.source());
    }

    @Test
    public void lowSimilarityStaysUnclassified() {
        List<NoteDto> classified = List.of(note("aaa", "甲", "大数据", "Spark"));
        TaxonomyGuesser.Embedder embedder = text -> text.startsWith("aaa")
                ? new float[]{1f, 0f} : new float[]{0f, 1f};
        TaxonomyGuesser.Guess g = new TaxonomyGuesser(taxonomy, embedder, 0.55)
                .guess("zzz", "无关描述", null, classified);
        assertEquals(NoteDto.DEFAULT_DOMAIN, g.domain());
    }

    @Test
    public void deletedEncryptedAndUnclassifiedNotesAreIgnored() {
        NoteDto deleted = note("aaa", "甲", "大数据", "Spark");
        deleted.deleted = true;
        NoteDto encrypted = note("bbb", "乙", "大数据", "Spark");
        encrypted.encrypted = true;
        NoteDto unclassified = note("ccc", "丙", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC);

        List<float[]> asked = new ArrayList<>();
        TaxonomyGuesser.Embedder embedder = text -> {
            asked.add(new float[]{1f, 0f});
            return new float[]{1f, 0f};
        };
        TaxonomyGuesser.Guess g = new TaxonomyGuesser(taxonomy, embedder, 0.55)
                .guess("zzz", "无关描述", null, List.of(deleted, encrypted, unclassified));

        assertEquals(NoteDto.DEFAULT_DOMAIN, g.domain());
        // 只应为查询本身推理一次，三篇都不该进中心向量
        assertEquals(1, asked.size());
    }

    @Test
    public void centroidsAreReusedUntilNotesChange() {
        List<NoteDto> classified = new ArrayList<>(List.of(note("aaa", "甲", "大数据", "Spark")));
        int[] calls = {0};
        TaxonomyGuesser.Embedder embedder = text -> {
            calls[0]++;
            return new float[]{1f, 0f};
        };
        TaxonomyGuesser guesser = new TaxonomyGuesser(taxonomy, embedder, 0.55);

        guesser.guess("zzz", "描述", null, classified);
        int afterFirst = calls[0];
        guesser.guess("yyy", "描述", null, classified);
        // 第二次只推理查询本身，中心向量命中缓存
        assertEquals(afterFirst + 1, calls[0]);

        classified.get(0).version = 2;
        guesser.guess("yyy", "描述", null, classified);
        assertEquals(afterFirst + 3, calls[0]);
    }
}