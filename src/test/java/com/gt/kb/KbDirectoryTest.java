package com.gt.kb;

import com.gt.NoteDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 树形目录数据源。纯函数，不碰文件与数据库。
 */
public class KbDirectoryTest {

    private static final String VOCAB = """
            version: 1
            domains:
              - name: 数据库
                topics: [MySQL, Redis]
              - name: 运维
                topics: [Linux, Docker]
              - name: 待分类
                topics: [待分类]
            """;

    private static Taxonomy vocab() {
        return Taxonomy.parse(VOCAB);
    }

    private static NoteDto note(String key, String title, String domain, String topic) {
        NoteDto n = new NoteDto();
        n.key = key;
        n.title = title;
        n.domain = domain;
        n.topic = topic;
        return n;
    }

    private static KbDirectory.Snapshot build(List<NoteDto> notes, Map<String, Integer> counts) {
        return KbDirectory.build(notes, vocab(), counts);
    }

    private static List<String> domainNames(KbDirectory.Snapshot s) {
        return s.domains().stream().map(KbDirectory.DomainEntry::name).toList();
    }

    private static List<String> topicNames(KbDirectory.Snapshot s, String domain) {
        return s.domains().stream().filter(d -> d.name().equals(domain)).findFirst().orElseThrow()
                .topics().stream().map(KbDirectory.TopicEntry::topic).toList();
    }

    private static List<String> noteKeys(KbDirectory.Snapshot s, String domain, String topic) {
        return s.domains().stream().filter(d -> d.name().equals(domain)).findFirst().orElseThrow()
                .topics().stream().filter(t -> t.topic().equals(topic)).findFirst().orElseThrow()
                .notes().stream().map(KbDirectory.NoteEntry::key).toList();
    }

    @Test
    public void groupsByDomainAndTopicInVocabularyOrder() {
        KbDirectory.Snapshot s = build(List.of(
                note("redis1", "Redis 持久化", "数据库", "Redis"),
                note("docker1", "Docker 网络", "运维", "Docker"),
                note("mysql1", "MySQL 索引", "数据库", "MySQL")), Map.of());

        assertEquals(List.of("数据库", "运维"), domainNames(s));
        assertEquals(List.of("MySQL", "Redis"), topicNames(s, "数据库"));
        assertEquals(List.of("mysql1"), noteKeys(s, "数据库", "MySQL"));
        assertEquals(3, s.noteCount());
        assertEquals(3, s.topicCount());
    }

    @Test
    public void excludesAggregateDeletedAndBlankKey() {
        NoteDto deleted = note("gone", "已删除", "数据库", "MySQL");
        deleted.deleted = true;
        NoteDto noKey = note(null, "无 key", "数据库", "MySQL");
        NoteDto blank = note("  ", "空白 key", "数据库", "MySQL");

        KbDirectory.Snapshot s = build(List.of(
                note("all", "所有命令列表", "待分类", "待分类"),
                deleted, noKey, blank,
                note("mysql1", "MySQL 索引", "数据库", "MySQL")), Map.of());

        assertEquals(1, s.noteCount());
        assertEquals(List.of("数据库"), domainNames(s));
        assertEquals(List.of("mysql1"), noteKeys(s, "数据库", "MySQL"));
    }

    @Test
    public void unclassifiedGoesLastEvenWhenVocabularyListsItEarlier() {
        String vocab = """
                version: 1
                domains:
                  - name: 待分类
                    topics: [待分类]
                  - name: 数据库
                    topics: [MySQL]
                """;
        KbDirectory.Snapshot s = KbDirectory.build(List.of(
                note("x", "未分类", "待分类", "待分类"),
                note("mysql1", "MySQL 索引", "数据库", "MySQL")), Taxonomy.parse(vocab), Map.of());

        assertEquals(List.of("数据库", "待分类"), domainNames(s));
    }

    @Test
    public void unknownDomainAndTopicAreKeptAfterKnownOnes() {
        KbDirectory.Snapshot s = build(List.of(
                note("z", "词表外主题", "数据库", "Oracle"),
                note("y", "词表外分类", "考古", "甲骨文"),
                note("mysql1", "MySQL 索引", "数据库", "MySQL")), Map.of());

        assertEquals(List.of("数据库", "考古"), domainNames(s));
        assertEquals(List.of("MySQL", "Oracle"), topicNames(s, "数据库"));
        assertEquals(3, s.noteCount());
    }

    @Test
    public void emptyTopicsAndDomainsAreNotShown() {
        KbDirectory.Snapshot s = build(List.of(note("mysql1", "MySQL 索引", "数据库", "MySQL")), Map.of());
        assertEquals(List.of("MySQL"), topicNames(s, "数据库"));
        assertEquals(List.of("数据库"), domainNames(s));
        assertEquals(1, s.topicCount());
    }

    @Test
    public void blankDomainAndTopicFallBackToUnclassified() {
        NoteDto n = note("orphan", "没有分类字段", null, "");
        KbDirectory.Snapshot s = build(List.of(n), Map.of());
        assertEquals(List.of("待分类"), domainNames(s));
        assertEquals(List.of("待分类"), topicNames(s, "待分类"));
    }

    @Test
    public void titleFallsBackToDescThenKey() {
        NoteDto noTitle = note("a", null, "数据库", "MySQL");
        noTitle.desc = "描述兜底";
        NoteDto neither = note("b", "  ", "数据库", "MySQL");
        neither.desc = "";

        KbDirectory.Snapshot s = build(List.of(noTitle, neither), Map.of());
        List<KbDirectory.NoteEntry> notes = s.domains().get(0).topics().get(0).notes();
        assertEquals("描述兜底", notes.stream().filter(e -> e.key().equals("a")).findFirst().orElseThrow().title());
        assertEquals("b", notes.stream().filter(e -> e.key().equals("b")).findFirst().orElseThrow().title());
    }

    @Test
    public void blockCountsAreAttachedAndTotaled() {
        KbDirectory.Snapshot s = build(List.of(
                note("mysql1", "MySQL 索引", "数据库", "MySQL"),
                note("redis1", "Redis 持久化", "数据库", "Redis")),
                Map.of("mysql1", 12, "redis1", 3));

        assertEquals(15, s.blockCount());
        assertEquals(12, s.domains().get(0).topics().get(0).notes().get(0).blockCount());
        assertEquals(3, s.domains().get(0).topics().get(1).notes().get(0).blockCount());
    }

    @Test
    public void missingBlockCountIsZeroNotNull() {
        KbDirectory.Snapshot s = build(List.of(note("mysql1", "MySQL 索引", "数据库", "MySQL")), null);
        assertEquals(0, s.blockCount());
        assertEquals(0, s.domains().get(0).topics().get(0).notes().get(0).blockCount());
    }

    @Test
    public void notesAreSortedByChineseTitle() {
        KbDirectory.Snapshot s = build(List.of(
                note("c", "索引", "数据库", "MySQL"),
                note("a", "备份", "数据库", "MySQL"),
                note("b", "慢查询", "数据库", "MySQL")), Map.of());
        assertEquals(List.of("备份", "慢查询", "索引"),
                s.domains().get(0).topics().get(0).notes().stream().map(KbDirectory.NoteEntry::title).toList());
    }

    @Test
    public void encryptedFlagIsPreservedForLockIcon() {
        NoteDto secret = note("pwd", "口令本", "运维", "Linux");
        secret.encrypted = true;
        KbDirectory.Snapshot s = build(List.of(secret), Map.of());
        assertTrue(s.domains().get(0).topics().get(0).notes().get(0).encrypted());
    }

    @Test
    public void emptyInputProducesEmptySnapshot() {
        KbDirectory.Snapshot s = build(List.of(), Map.of());
        assertTrue(s.domains().isEmpty());
        assertEquals(0, s.noteCount());
        assertEquals(0, s.topicCount());
        assertEquals(0, s.blockCount());
    }

    @Test
    public void domainNoteCountMatchesItsTopics() {
        KbDirectory.Snapshot s = build(List.of(
                note("m1", "MySQL 索引", "数据库", "MySQL"),
                note("m2", "MySQL 锁", "数据库", "MySQL"),
                note("r1", "Redis 持久化", "数据库", "Redis")), Map.of());
        assertEquals(3, s.domains().get(0).noteCount());
        assertEquals(3, s.noteCount());
    }
}