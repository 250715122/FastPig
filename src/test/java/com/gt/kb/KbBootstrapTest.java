package com.gt.kb;

import com.gt.NoteDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存量预判规则与报告生成。全部在 @TempDir 下构造假笔记，不读写真实 notes。
 */
public class KbBootstrapTest {

    @TempDir
    Path tempDir;

    private Path writeNote(String key, String frontMatterExtra, String body) throws Exception {
        Path folder = tempDir.resolve(key);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("note.md"), """
            ---
            id: "%s"
            key: "%s"
            title: "标题"
            desc: "描述,带逗号"
            tags: []
            version: 1
            deleted: false
            createdAt: 2025-01-01 00:00:00
            updatedAt: 2025-01-01 00:00:00
            %s---

            %s""".formatted(key, key, frontMatterExtra, body), StandardCharsets.UTF_8);
        return folder;
    }

    @Test
    public void threeDateLinesMakesItRawLog() {
        KbBootstrap.Prediction p = KbBootstrap.predict("""
            # 工作日志
            20250113
            今天做了 A
            20250114
            今天做了 B
            20250115
            今天做了 C
            """);
        assertEquals(NoteDto.LAYER_RAW, p.layer());
        assertEquals("日志", p.noteType());
        assertEquals(3, p.dateLines());
    }

    @Test
    public void twoDateLinesStaysCurated() {
        KbBootstrap.Prediction p = KbBootstrap.predict("20250113\n内容\n20250114\n内容\n");
        assertEquals(NoteDto.LAYER_CURATED, p.layer());
        assertEquals(NoteDto.DEFAULT_NOTE_TYPE, p.noteType());
    }

    @Test
    public void indentedDateLinesStillCount() {
        assertEquals(3, KbBootstrap.countDateLines("  20250113  \n\t20250114\n20250115\n"));
    }

    /** 行内出现的日期不是日志特征，否则任何提到日期的笔记都会被误判成 raw。 */
    @Test
    public void inlineDatesDoNotCount() {
        assertEquals(0, KbBootstrap.countDateLines("更新于 20250113，见 20250114 的记录\n"));
    }

    @Test
    public void frontMatterIsExcludedFromPrediction() {
        String content = """
            ---
            id: "x"
            key: "x"
            createdAt: 2025-01-01 00:00:00
            ---

            20250113
            20250114
            """;
        assertEquals(2, KbBootstrap.countDateLines(KbBootstrap.bodyOf(content)));
        assertEquals(NoteDto.LAYER_CURATED, KbBootstrap.predict(KbBootstrap.bodyOf(content)).layer());
    }

    @Test
    public void scanSkipsInternalAndHiddenFolders() throws Exception {
        writeNote("mysql", "", "# MySQL\n内容\n");
        writeNote("_kb", "", "version: 1\n");
        writeNote(".hidden", "", "内容\n");
        Files.createDirectories(tempDir.resolve("nodotmd"));

        List<KbBootstrap.Row> rows = KbBootstrap.scan(tempDir, Taxonomy.loadReadOnly(tempDir));

        assertEquals(1, rows.size());
        assertEquals("mysql", rows.get(0).key());
    }

    @Test
    public void scanReadsExistingClassification() throws Exception {
        writeNote("spark", "domain: \"大数据\"\ntopic: \"Spark\"\n", "# Spark\n内容\n");

        KbBootstrap.Row row = KbBootstrap.scan(tempDir, Taxonomy.loadReadOnly(tempDir)).get(0);

        assertEquals("大数据", row.currentDomain());
        assertEquals("Spark", row.currentTopic());
        assertEquals("描述,带逗号", row.desc());
        assertFalse(row.encrypted());
    }

    @Test
    public void encryptedNoteIsReportedPrivate() throws Exception {
        writeNote("secret", "encrypted: true\n", "密文\n");

        KbBootstrap.Row row = KbBootstrap.scan(tempDir, Taxonomy.loadReadOnly(tempDir)).get(0);
        Path report = tempDir.resolve("report.csv");
        KbBootstrap.writeReport(report, List.of(row));

        assertTrue(row.encrypted());
        String csv = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(csv.contains(",private\n"), csv);
    }

    /** desc 含逗号必须加引号，否则人工校对时列会错位。 */
    @Test
    public void reportQuotesFieldsContainingCommas() throws Exception {
        writeNote("comma", "", "内容\n");
        Path report = tempDir.resolve("report.csv");
        KbBootstrap.writeReport(report, KbBootstrap.scan(tempDir, Taxonomy.loadReadOnly(tempDir)));

        String csv = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"描述,带逗号\""), csv);
        assertTrue(csv.startsWith("key,desc,size_bytes,"), csv);
    }

    @Test
    public void dryRunWritesNoReport() throws Exception {
        writeNote("dry", "", "内容\n");
        Path report = tempDir.resolve("should-not-exist.csv");

        KbBootstrap.main(new String[]{
            "--dry-run", "--notes", tempDir.toString(), "--out", report.toString()
        });

        assertFalse(Files.exists(report));
    }

    @Test
    public void normalRunWritesReport() throws Exception {
        writeNote("real", "", "20250113\n20250114\n20250115\n");
        Path report = tempDir.resolve("report.csv");

        KbBootstrap.main(new String[]{
            "--notes", tempDir.toString(), "--out", report.toString()
        });

        assertTrue(Files.exists(report));
        String csv = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(csv.contains("raw,日志,3"), csv);
    }

    /** 报告必须给出词表猜测的分类，否则人工校对时无从改起。 */
    @Test
    public void reportSuggestsClassificationFromTaxonomy() throws Exception {
        writeNote("mysql", "", "# MySQL\n内容\n");
        writeNote("zzzz", "", "内容\n");

        List<KbBootstrap.Row> rows = KbBootstrap.scan(tempDir, Taxonomy.loadReadOnly(tempDir));
        KbBootstrap.Row mysql = rows.stream().filter(r -> r.key().equals("mysql")).findFirst().orElseThrow();
        KbBootstrap.Row unknown = rows.stream().filter(r -> r.key().equals("zzzz")).findFirst().orElseThrow();

        assertEquals("数据库", mysql.suggestedDomain());
        assertEquals("MySQL", mysql.suggestedTopic());
        assertEquals(NoteDto.DEFAULT_DOMAIN, unknown.suggestedDomain());

        Path report = tempDir.resolve("report.csv");
        KbBootstrap.writeReport(report, rows);
        assertTrue(Files.readString(report, StandardCharsets.UTF_8).contains(",数据库,MySQL,"),
                Files.readString(report, StandardCharsets.UTF_8));
    }

    /** --dry-run 承诺不落任何文件，包括不能顺手把默认词表写进 _kb。 */
    @Test
    public void dryRunDoesNotMaterializeTaxonomy() throws Exception {
        writeNote("dry", "", "内容\n");

        KbBootstrap.main(new String[]{
            "--dry-run", "--notes", tempDir.toString(), "--out", tempDir.resolve("r.csv").toString()
        });

        assertFalse(Files.exists(tempDir.resolve(Taxonomy.KB_DIR).resolve(Taxonomy.FILE_NAME)));
    }
}