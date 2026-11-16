package com.gt.kb;

import com.gt.NoteDto;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识库树形目录：domain → topic → 笔记 → 块 的派生视图。
 *
 * 不落盘、不建笔记、不进索引、不参与云同步，只是把已有元数据重新排列，
 * 任何时候都能从 notes + kb_blocks 重算。这是它取代 all 聚合笔记的根本原因：
 * all 是一篇真笔记，会被索引、被同步、被版本冲突牵扯，目录不该背这些负担。
 *
 * 纯函数，不做 I/O：笔记元数据、词表、块数都由调用方取好传进来，便于单测与后台线程重建。
 */
public final class KbDirectory {

    /** all 自身就是派生聚合，放进目录会形成自指节点。 */
    public static final String AGGREGATE_KEY = "all";

    public record NoteEntry(String key, String title, String domain, String topic,
                            boolean encrypted, int blockCount) {
    }

    public record TopicEntry(String domain, String topic, List<NoteEntry> notes) {
        public TopicEntry {
            notes = List.copyOf(notes);
        }
    }

    public record DomainEntry(String name, List<TopicEntry> topics, int noteCount) {
        public DomainEntry {
            topics = List.copyOf(topics);
        }
    }

    public record Snapshot(List<DomainEntry> domains, int noteCount, int topicCount, int blockCount) {
        public Snapshot {
            domains = List.copyOf(domains);
        }
    }

    private KbDirectory() {
    }

    /**
     * @param notes       笔记元数据，可含已删除/无 key/all，内部会过滤
     * @param taxonomy    受控词表，决定 domain 与 topic 的展示顺序
     * @param blockCounts 每篇笔记的块数，缺键按 0 计
     */
    public static Snapshot build(List<NoteDto> notes, Taxonomy taxonomy, Map<String, Integer> blockCounts) {
        Map<String, Integer> counts = blockCounts == null ? Map.of() : blockCounts;
        Map<String, Map<String, List<NoteEntry>>> grouped = new LinkedHashMap<>();
        int blockTotal = 0;
        for (NoteDto n : notes) {
            if (n == null || n.deleted || n.key == null || n.key.isBlank() || AGGREGATE_KEY.equals(n.key)) {
                continue;
            }
            String domain = NoteDto.orDefault(n.domain, NoteDto.DEFAULT_DOMAIN);
            String topic = NoteDto.orDefault(n.topic, NoteDto.DEFAULT_TOPIC);
            int count = counts.getOrDefault(n.key, 0);
            blockTotal += count;
            grouped.computeIfAbsent(domain, k -> new LinkedHashMap<>())
                    .computeIfAbsent(topic, k -> new ArrayList<>())
                    .add(new NoteEntry(n.key, titleOf(n), domain, topic, n.encrypted, count));
        }

        List<String> domainOrder = taxonomy.domainNames();
        Comparator<String> byTitle = collator();
        List<DomainEntry> domains = new ArrayList<>();
        int noteTotal = 0;
        int topicTotal = 0;
        for (String domain : sorted(grouped.keySet(), domainOrder)) {
            Map<String, List<NoteEntry>> topics = grouped.get(domain);
            List<TopicEntry> topicEntries = new ArrayList<>();
            int domainNotes = 0;
            for (String topic : sorted(topics.keySet(), taxonomy.topicNames(domain))) {
                List<NoteEntry> entries = new ArrayList<>(topics.get(topic));
                entries.sort(Comparator.comparing(NoteEntry::title, byTitle).thenComparing(NoteEntry::key));
                topicEntries.add(new TopicEntry(domain, topic, entries));
                domainNotes += entries.size();
            }
            domains.add(new DomainEntry(domain, topicEntries, domainNotes));
            noteTotal += domainNotes;
            topicTotal += topicEntries.size();
        }
        return new Snapshot(domains, noteTotal, topicTotal, blockTotal);
    }

    /** 标题缺失时退回 desc，再退回 key：目录里不能出现空白节点。 */
    private static String titleOf(NoteDto n) {
        if (n.title != null && !n.title.isBlank()) {
            return n.title.strip();
        }
        if (n.desc != null && !n.desc.isBlank()) {
            return n.desc.strip();
        }
        return n.key;
    }

    /**
     * 词表内的名字按书写顺序（技术类在前、个人在后），词表外的按名字排在已知之后，
     * 待分类始终垫底。词表外的分类不能丢——手工改过 front matter 的笔记仍要能在目录里找到。
     */
    private static List<String> sorted(Iterable<String> names, List<String> vocabularyOrder) {
        List<String> out = new ArrayList<>();
        names.forEach(out::add);
        out.sort(Comparator.comparingInt((String s) -> rank(s, vocabularyOrder))
                .thenComparing(Comparator.naturalOrder()));
        return out;
    }

    private static int rank(String name, List<String> vocabularyOrder) {
        if (NoteDto.DEFAULT_DOMAIN.equals(name) || NoteDto.DEFAULT_TOPIC.equals(name)) {
            return Integer.MAX_VALUE;
        }
        int i = vocabularyOrder.indexOf(name);
        return i >= 0 ? i : vocabularyOrder.size();
    }

    private static Comparator<String> collator() {
        Collator collator = Collator.getInstance(Locale.CHINA);
        return collator::compare;
    }
}