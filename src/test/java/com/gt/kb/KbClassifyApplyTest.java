package com.gt.kb;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.service.NoteService;
import com.gt.storage.NoteFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分类导入工具。全部落在 @TempDir，不碰真实 notes / fastpig.db。
 * 向量检索未初始化，NoteService.save 里的 indexNote 会直接返回。
 */
public class KbClassifyApplyTest {

    @TempDir
    Path tempDir;

    private static final String HEADER = "key,desc,size_bytes,encrypted,current_domain,current_topic,"
            + "predicted_layer,predicted_note_type,date_lines,suggested_domain,suggested_topic,visibility\n";

    private static NoteDto note(String key, String domain, String topic) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = key;
        n.desc = key;
        n.bodyMd = "# 标题\n\n" + "正文内容。".repeat(10) + "\n";
        n.domain = domain;
        n.topic = topic;
        n.createdAt = System.currentTimeMillis();
        n.updatedAt = n.createdAt;
        n.version = 1;
        return n;
    }

    private static Map<String, KbClassifyApply.Suggestion> report(String... keyDomainTopic) {
        Map<String, KbClassifyApply.Suggestion> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyDomainTopic.length; i += 3) {
            m.put(keyDomainTopic[i], new KbClassifyApply.Suggestion(keyDomainTopic[i + 1], keyDomainTopic[i + 2]));
        }
        return m;
    }

    @Test
    public void planOnlyFillsUnclassifiedNotes() {
        NoteDto fill = note("mysql", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC);
        NoteDto manual = note("spark", "大数据", "Spark");
        NoteDto noHit = note("zzz", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC);
        NoteDto absent = note("absent", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC);
        NoteDto secret = note("secret", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC);
        secret.encrypted = true;
        NoteDto gone = note("gone", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC);
        gone.deleted = true;

        KbClassifyApply.Plan plan = KbClassifyApply.plan(
                List.of(fill, manual, noHit, absent, secret, gone),
                report("mysql", "数据库", "MySQL",
                        "spark", "数据库", "MySQL",
                        "zzz", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC,
                        "secret", "数据库", "MySQL",
                        "gone", "数据库", "MySQL"));

        assertEquals(List.of("mysql"), plan.changes().stream().map(KbClassifyApply.Change::key).toList());
        assertEquals("数据库", plan.changes().get(0).domain());
        assertEquals("MySQL", plan.changes().get(0).topic());
        // 人工设过的分类不能被词面猜测盖掉
        assertEquals(1, plan.alreadyClassified());
        // 建议是待分类 + 报告里没这个 key，都留给人工
        assertEquals(2, plan.noSuggestion());
        assertEquals(1, plan.encrypted());
        assertEquals(1, plan.deleted());
    }

    @Test
    public void planIsIdempotentOnceClassificationMatches() {
        NoteDto n = note("mysql", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC);
        Map<String, KbClassifyApply.Suggestion> r = report("mysql", "数据库", "MySQL");
        assertEquals(1, KbClassifyApply.plan(List.of(n), r).changes().size());

        n.domain = "数据库";
        n.topic = "MySQL";
        KbClassifyApply.Plan second = KbClassifyApply.plan(List.of(n), r);
        assertEquals(0, second.changes().size());
        assertEquals(1, second.alreadyClassified());
    }

    @Test
    public void parseCsvHandlesQuotedCommasAndEscapedQuotes() {
        List<List<String>> rows = KbClassifyApply.parseCsv(
                "key,desc\nmysql,\"a,b\"\nspark,\"say \"\"hi\"\"\"\nplain,x\n");
        assertEquals(4, rows.size());
        assertEquals(List.of("mysql", "a,b"), rows.get(1));
        assertEquals(List.of("spark", "say \"hi\""), rows.get(2));
        assertEquals(List.of("plain", "x"), rows.get(3));
    }

    @Test
    public void readReportUsesHeaderNamesNotColumnOrder() throws Exception {
        Path csv = tempDir.resolve("report.csv");
        Files.writeString(csv, "suggested_topic,key,suggested_domain\nMySQL,mysql,数据库\n",
                StandardCharsets.UTF_8);
        Map<String, KbClassifyApply.Suggestion> r = KbClassifyApply.readReport(csv);
        assertEquals(new KbClassifyApply.Suggestion("数据库", "MySQL"), r.get("mysql"));
    }

    @Test
    public void applyPersistsClassificationWithoutTouchingBody() throws Exception {
        Path base = tempDir;
        try (NoteService service = newService(base)) {
            service.save(note("mysql", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC));
        }
        NoteFileStorage storage = new NoteFileStorage(base.toString());
        // 正文基准取自磁盘：loadFromFile 会去掉末尾换行，拿内存里的 body 比会差一个 \n
        String body = storage.loadFromFile(storage.findNoteFolderByKey("mysql")).bodyMd;
        Path csv = writeReport(base, "mysql,数据库,MySQL\n");

        KbClassifyApply.Summary s = KbClassifyApply.run(base, csv, true);
        assertEquals(1, s.applied());
        assertEquals(0, s.failed());
        assertEquals(0, s.bodyChanged(), "分类只该动 front matter");
        assertEquals(0, s.idsAssigned(), "块 ID 已存在，不该再补");

        NoteDto loaded = storage.loadFromFile(storage.findNoteFolderByKey("mysql"));
        assertEquals("数据库", loaded.domain);
        assertEquals("MySQL", loaded.topic);
        assertEquals(body, loaded.bodyMd);

        NoteRepository repo = new NoteRepository(base.resolve("fastpig.db").toString());
        assertEquals("数据库", repo.findByKey("mysql").domain);
    }

    @Test
    public void secondApplyChangesNothing() throws Exception {
        Path base = tempDir;
        try (NoteService service = newService(base)) {
            service.save(note("mysql", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC));
        }
        Path csv = writeReport(base, "mysql,数据库,MySQL\n");

        assertEquals(1, KbClassifyApply.run(base, csv, true).applied());
        Path file = new NoteFileStorage(base.toString()).findNoteFileByKey("mysql");
        String afterFirst = Files.readString(file, StandardCharsets.UTF_8);

        KbClassifyApply.Summary second = KbClassifyApply.run(base, csv, true);
        assertEquals(0, second.plan().changes().size());
        assertEquals(0, second.applied());
        assertEquals(afterFirst, Files.readString(file, StandardCharsets.UTF_8), "幂等：不该再写一次文件");
    }

    @Test
    public void dryRunWritesNothing() throws Exception {
        Path base = tempDir;
        try (NoteService service = newService(base)) {
            service.save(note("mysql", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC));
        }
        Path csv = writeReport(base, "mysql,数据库,MySQL\n");
        Path file = new NoteFileStorage(base.toString()).findNoteFileByKey("mysql");
        String before = Files.readString(file, StandardCharsets.UTF_8);

        KbClassifyApply.Summary s = KbClassifyApply.run(base, csv, false);
        assertEquals(1, s.plan().changes().size());
        assertEquals(0, s.applied());
        assertEquals(before, Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(before.contains("domain: \"数据库\""));
    }

    @Test
    public void unclassifiedSuggestionIsLeftForManualReview() throws Exception {
        Path base = tempDir;
        try (NoteService service = newService(base)) {
            service.save(note("dlmhpz", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC));
        }
        Path csv = writeReport(base, "dlmhpz,待分类,待分类\n");

        KbClassifyApply.Summary s = KbClassifyApply.run(base, csv, true);
        assertEquals(0, s.applied());
        NoteFileStorage storage = new NoteFileStorage(base.toString());
        assertEquals(NoteDto.DEFAULT_DOMAIN,
                storage.loadFromFile(storage.findNoteFolderByKey("dlmhpz")).domain);
        assertTrue(s.plan().noSuggestion() >= 1);
    }

    @Test
    public void trailingBlankLineTrimIsReportedSeparatelyFromTextChange() throws Exception {
        Path base = tempDir;
        try (NoteService service = newService(base)) {
            service.save(note("mysql", NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC));
        }
        // 存储层落盘时会规范化正文首尾空白。自检必须比文件、且把「空白规范化」与「文字改动」分开，
        // 否则要么漏报真改动，要么把无害的空白差异误报成内容丢失。
        Path file = new NoteFileStorage(base.toString()).findNoteFileByKey("mysql");
        Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8) + "\n\n",
                StandardCharsets.UTF_8);
        Path csv = writeReport(base, "mysql,数据库,MySQL\n");

        KbClassifyApply.Summary s = KbClassifyApply.run(base, csv, true);
        assertEquals(1, s.applied());
        assertEquals(0, s.bodyChanged(), "空行不是文字内容");
        assertEquals(1, s.whitespaceTrimmed());
    }

    private NoteService newService(Path base) {
        return new NoteService(new NoteRepository(base.resolve("fastpig.db").toString()),
                new NoteFileStorage(base.toString()));
    }

    private Path writeReport(Path base, String dataLines) throws Exception {
        Path csv = base.resolve("report.csv");
        StringBuilder sb = new StringBuilder(HEADER);
        for (String line : dataLines.split("\n")) {
            String[] p = line.split(",");
            sb.append(p[0]).append(",desc,100,false,待分类,待分类,curated,知识,0,")
              .append(p[1]).append(',').append(p[2]).append(",human\n");
        }
        Files.writeString(csv, sb.toString(), StandardCharsets.UTF_8);
        return csv;
    }
}