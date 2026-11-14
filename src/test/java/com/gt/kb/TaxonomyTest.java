package com.gt.kb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaxonomyTest {

    @TempDir
    Path tempDir;

    private static final String SAMPLE = """
            version: 1
            domains:
              - name: 大数据
                topics: [Spark, Hive]
                aliases:
                  Spark: [rdd, dataframe]
              - name: 待分类
                topics: [待分类]
            """;

    @Test
    public void parsesDomainsTopicsAndAliases() {
        Taxonomy t = Taxonomy.parse(SAMPLE);
        assertEquals(1, t.version());
        assertEquals(List.of("大数据", "待分类"), t.domainNames());
        assertEquals(List.of("Spark", "Hive"), t.topicNames("大数据"));
        assertEquals(List.of("rdd", "dataframe"), t.domains().get(0).topics().get(0).aliases());
        assertTrue(t.contains("大数据", "Hive"));
        assertFalse(t.contains("大数据", "MySQL"));
    }

    @Test
    public void unknownDomainHasNoTopics() {
        assertEquals(List.of(), Taxonomy.parse(SAMPLE).topicNames("不存在"));
    }

    @Test
    public void commentsAndBlankLinesAreIgnored() {
        Taxonomy t = Taxonomy.parse("# 注释\n\n" + SAMPLE + "\n# 尾注释\n");
        assertEquals(2, t.domains().size());
    }

    @Test
    public void malformedLineIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Taxonomy.parse(SAMPLE + "  这行不认识\n"));
    }

    @Test
    public void emptyTaxonomyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Taxonomy.parse("version: 1\ndomains:\n"));
    }

    @Test
    public void loadMaterializesDefaultFile() {
        Taxonomy t = Taxonomy.loadOrDefault(tempDir);
        Path file = tempDir.resolve("_kb").resolve("taxonomy.yaml");
        assertTrue(Files.exists(file));
        assertTrue(t.domainNames().contains("大数据"));
        assertTrue(t.contains("待分类", "待分类"));
    }

    @Test
    public void loadPrefersExistingFileOverDefault() throws Exception {
        Path file = tempDir.resolve("_kb").resolve("taxonomy.yaml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);

        assertEquals(List.of("大数据", "待分类"), Taxonomy.loadOrDefault(tempDir).domainNames());
    }

    @Test
    public void corruptFileFallsBackToDefault() throws Exception {
        Path file = tempDir.resolve("_kb").resolve("taxonomy.yaml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "domains: [oops\n", StandardCharsets.UTF_8);

        assertTrue(Taxonomy.loadOrDefault(tempDir).domainNames().contains("数据库"));
    }

    @Test
    public void loadReadOnlyNeverWrites() {
        assertTrue(Taxonomy.loadReadOnly(tempDir).domainNames().contains("大数据"));
        assertFalse(Files.exists(tempDir.resolve("_kb")));
    }

    @Test
    public void loadReadOnlyFallsBackWithoutOverwritingCorruptFile() throws Exception {
        Path file = tempDir.resolve("_kb").resolve("taxonomy.yaml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "domains: [oops\n", StandardCharsets.UTF_8);

        assertTrue(Taxonomy.loadReadOnly(tempDir).domainNames().contains("数据库"));
        assertEquals("domains: [oops\n", Files.readString(file, StandardCharsets.UTF_8));
    }
}