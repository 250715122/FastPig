package com.gt.kb.id;

import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.model.KnowledgeBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 迁移工具测试：全部在 @TempDir 的合成笔记上跑，绝不触碰真实 notes。 */
public class BlockIdMigrationTest {

    @TempDir
    Path tempDir;

    private static final ChunkProfile PROFILE = new ChunkProfile(false, 1500, 800, 100, 30);
    private static final DefaultChunker CHUNKER = new DefaultChunker();

    private Path notesDir() {
        return tempDir.resolve("notes");
    }

    private Path note(String key, String body) throws IOException {
        Path folder = notesDir().resolve(key);
        Files.createDirectories(folder);
        String front = """
                ---
                id: "id-%s"
                key: "%s"
                title: "%s"
                desc: "%s"
                tags: []
                version: 3
                deleted: false
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
                domain: "大数据"
                topic: "Spark"
                noteType: "知识"
                layer: "curated"
                visibility: "human"
                aliases: []
                ---

                """.formatted(key, key, key + "笔记", key + "笔记");
        Files.writeString(folder.resolve("note.md"), front + body, StandardCharsets.UTF_8);
        return folder;
    }

    private static void setFrontMatter(Path folder, String from, String to) throws IOException {
        Path file = folder.resolve("note.md");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains(from), "front matter 里没有 " + from);
        Files.writeString(file, content.replace(from, to), StandardCharsets.UTF_8);
    }

    private static Map<String, String> snapshot(Path dir) throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                out.put(dir.relativize(p).toString(), Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String read(Path folder) throws IOException {
        return Files.readString(folder.resolve("note.md"), StandardCharsets.UTF_8);
    }

    @Test
    public void dryRunChangesNoFile() throws IOException {
        note("alpha", "# 甲\n\n正文甲。\n\n# 乙\n\n正文乙。\n");
        note("beta", "无结构长文，只有一段。\n");
        Map<String, String> before = snapshot(tempDir);

        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), false);

        assertEquals(before, snapshot(tempDir), "dry-run 改动了文件");
        assertEquals(2, s.notes());
        assertEquals(2, s.changed());
        assertEquals(3, s.assigned());
        assertEquals(3, s.blocks());
        assertTrue(s.rows().stream().allMatch(r -> r.status().equals("would-change")));
    }

    @Test
    public void applyInsertsIdsAndKeepsFrontMatter() throws IOException {
        Path folder = note("alpha", "# 甲\n\n正文甲。\n\n# 乙\n\n正文乙。\n");
        String before = read(folder);

        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), true);
        String after = read(folder);

        assertEquals(1, s.changed());
        assertEquals(2, s.assigned());
        assertTrue(after.contains("version: 4"), "版本号应 +1");
        assertFalse(after.contains("updatedAt: 2024-01-01T00:00:00Z"), "updatedAt 应刷新");
        for (String field : List.of("id: \"id-alpha\"", "key: \"alpha\"", "domain: \"大数据\"",
                "topic: \"Spark\"", "noteType: \"知识\"", "layer: \"curated\"", "visibility: \"human\"",
                "createdAt: 2024-01-01T00:00:00Z", "tags: []", "aliases: []")) {
            assertTrue(after.contains(field), "front matter 字段丢失: " + field);
        }
        assertTrue(BlockIdMigration.sameTextIgnoringIds(bodyOf(before), bodyOf(after)), "正文被改动");

        List<KnowledgeBlock> blocks = CHUNKER.chunk("alpha", "alpha笔记", bodyOf(after), PROFILE);
        assertEquals(2, blocks.size());
        assertTrue(blocks.stream().allMatch(b -> BlockIdCodec.isValidId(b.id())));
    }

    @Test
    public void applyIsIdempotent() throws IOException {
        Path folder = note("alpha", "# 甲\n\n正文甲。\n\n# 乙\n\n正文乙。\n");
        BlockIdMigration.run(notesDir(), true);
        String once = read(folder);

        BlockIdMigration.Summary second = BlockIdMigration.run(notesDir(), true);
        assertEquals(0, second.changed());
        assertEquals(0, second.assigned());
        assertEquals(0, second.reassigned());
        assertEquals(once, read(folder), "第二次迁移不应再改文件（含版本号）");
    }

    @Test
    public void encryptedAndDeletedNotesAreSkipped() throws IOException {
        Path enc = note("secret", "密文正文不是 markdown\n");
        setFrontMatter(enc, "visibility: \"human\"", "visibility: \"human\"\nencrypted: true");
        Path del = note("gone", "# 甲\n\n正文。\n");
        setFrontMatter(del, "deleted: false", "deleted: true");
        String encBefore = read(enc);
        String delBefore = read(del);

        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), true);

        assertEquals(2, s.skipped());
        assertEquals(0, s.changed());
        assertEquals(encBefore, read(enc));
        assertEquals(delBefore, read(del));
        assertEquals("skip-encrypted", s.rows().get(1).status());
        assertEquals("skip-deleted", s.rows().get(0).status());
    }

    @Test
    public void noteWithoutFrontMatterIsSkipped() throws IOException {
        Path folder = notesDir().resolve("bare");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("note.md"), "# 甲\n\n正文。\n", StandardCharsets.UTF_8);
        String before = read(folder);

        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), true);
        assertEquals(1, s.skipped());
        assertEquals("skip-no-front-matter", s.rows().get(0).status());
        assertEquals(before, read(folder));
    }

    @Test
    public void sameIdInTwoNotesIsReassignedInSecond() throws IOException {
        note("alpha", "# 甲\n<!-- ku:aaaa2222 -->\n\n正文甲。\n");
        note("beta", "# 乙\n<!-- ku:aaaa2222 -->\n\n正文乙。\n");

        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), true);

        assertEquals(1, s.reassigned());
        String alpha = read(notesDir().resolve("alpha"));
        String beta = read(notesDir().resolve("beta"));
        assertTrue(alpha.contains("ku:aaaa2222"), "先处理的笔记保留原 ID");
        assertFalse(beta.contains("ku:aaaa2222"), "后处理的笔记必须换 ID");
        assertTrue(beta.contains("<!-- ku:"));
    }

    @Test
    public void duplicateIdInsideOneNoteIsReassigned() throws IOException {
        Path folder = note("alpha", "# 甲\n<!-- ku:aaaa2222 -->\n\n正文甲。\n\n# 乙\n<!-- ku:aaaa2222 -->\n\n正文乙。\n");
        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), true);
        assertEquals(1, s.reassigned());

        List<KnowledgeBlock> blocks = CHUNKER.chunk("alpha", "alpha笔记", bodyOf(read(folder)), PROFILE);
        assertEquals(2, blocks.size());
        assertNotEquals(blocks.get(0).id(), blocks.get(1).id());
    }

    @Test
    public void reportCsvListsEveryNote() throws IOException {
        note("alpha", "# 甲\n\n正文甲。\n");
        note("beta", "# 乙\n\n正文乙。\n");
        Path csv = tempDir.resolve("report").resolve("migration.csv");

        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), false);
        BlockIdMigration.writeReport(csv, s);

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertEquals("key,desc,blocks,index_blocks,assigned,reassigned,status,bytes_before,bytes_after", lines.get(0));
        assertTrue(lines.get(1).startsWith("alpha,alpha笔记,1,1,1,0,would-change,"));
        assertTrue(lines.get(2).startsWith("beta,beta笔记,1,1,1,0,would-change,"));
    }

    @Test
    public void logNoteIsChunkedByDate() throws IOException {
        Path folder = note("daily", "2024-05-01\n\n今天甲。\n\n2024-05-02\n\n今天乙。\n");
        setFrontMatter(folder, "noteType: \"知识\"", "noteType: \"日志\"");
        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), true);
        assertEquals(2, s.blocks());
        assertEquals(2, s.assigned());

        List<KnowledgeBlock> blocks = CHUNKER.chunk("daily", "daily笔记", bodyOf(read(folder)),
                new ChunkProfile(true, 1500, 800, 100, 30));
        assertEquals(List.of("2024-05-01", "2024-05-02"), blocks.stream().map(KnowledgeBlock::title).toList());
    }

    @Test
    public void internalKbFolderIsNotANote() throws IOException {
        note("alpha", "# 甲\n\n正文甲。\n");
        Path kb = notesDir().resolve("_kb");
        Files.createDirectories(kb);
        Files.writeString(kb.resolve("taxonomy.yaml"), "version: 1\n", StandardCharsets.UTF_8);

        BlockIdMigration.Summary s = BlockIdMigration.run(notesDir(), true);
        assertEquals(1, s.notes());
        assertEquals("version: 1\n", Files.readString(kb.resolve("taxonomy.yaml"), StandardCharsets.UTF_8));
    }

    @Test
    public void missingNotesDirFails() {
        assertThrows(IOException.class, () -> BlockIdMigration.run(tempDir.resolve("nope"), false));
    }

    /** 取出 front matter 之后的正文原文（不 trim，便于逐字比对），供切分器复核。 */
    private static String bodyOf(String content) {
        int end = content.indexOf("\n---\n", 4);
        return content.substring(end + 5);
    }
}