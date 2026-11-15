package com.gt.kb.repo;

import com.gt.NoteDto;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.storage.NoteFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全部读写都落在 @TempDir 上，不碰真实 notes 目录。
 */
public class SingleFileBlockRepositoryTest {

    @TempDir
    Path tempDir;

    private NoteFileStorage storage;
    private BlockCatalog catalog;
    private SingleFileBlockRepository repo;

    @BeforeEach
    public void setUp() {
        storage = new NoteFileStorage(tempDir.toString());
        catalog = new BlockCatalog(tempDir.resolve("kb.db").toString());
        repo = new SingleFileBlockRepository(storage, catalog);
    }

    @AfterEach
    public void tearDown() {
        catalog.close();
    }

    private NoteDto note(String key, String desc, String body) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = desc;
        n.desc = desc;
        n.bodyMd = body;
        n.createdAt = System.currentTimeMillis();
        n.updatedAt = n.createdAt;
        n.version = 1;
        storage.saveToFile(n);
        return n;
    }

    private static List<String> titles(List<KnowledgeBlock> blocks) {
        return blocks.stream().map(KnowledgeBlock::title).toList();
    }

    @Test
    public void listBlocksSplitsByHeadings() {
        note("mysql", "MySQL 运维", "# 索引\n\n正文一\n\n# 慢查询\n\n正文二\n");
        List<KnowledgeBlock> blocks = repo.listBlocks("mysql");
        assertEquals(List.of("索引", "慢查询"), titles(blocks));
        assertEquals("MySQL 运维 > 索引", blocks.get(0).pathText());
        assertTrue(blocks.get(0).body().contains("正文一"));
        assertFalse(blocks.get(0).body().contains("# 索引"));
    }

    @Test
    public void listBlocksReturnsKnowledgeBlocksNotIndexBlocks() {
        String longBody = "# 索引\n\n" + "字".repeat(2500) + "\n";
        note("mysql", "MySQL 运维", longBody);
        List<KnowledgeBlock> blocks = repo.listBlocks("mysql");
        assertEquals(1, blocks.size());
        assertFalse(blocks.get(0).isIndexBlock());
    }

    @Test
    public void missingNoteYieldsNoBlocksAndEmptyText() {
        assertTrue(repo.listBlocks("nope").isEmpty());
        assertEquals("", repo.loadNoteText("nope"));
        assertTrue(repo.listBlocks(null).isEmpty());
    }

    @Test
    public void cacheIsInvalidatedWhenFileChanges() {
        NoteDto n = note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        assertEquals(List.of("索引"), titles(repo.listBlocks("mysql")));

        n.bodyMd = "# 慢查询\n\n正文二\n";
        storage.saveToFile(n);
        List<KnowledgeBlock> after = repo.listBlocks("mysql");
        assertEquals(List.of("慢查询"), titles(after));
        assertEquals(1, after.get(0).startLine());
    }

    @Test
    public void leadingBlankLinesAreTrimmedOnLoad() {
        NoteDto n = note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        assertEquals(1, repo.listBlocks("mysql").get(0).startLine());

        n.bodyMd = "\n\n# 索引\n\n正文一\n";
        storage.saveToFile(n);
        // 正文读回时会被 trim，行号口径必须与读回的正文一致，否则跳转跳偏
        assertEquals("# 索引\n\n正文一", repo.loadNoteText("mysql"));
        assertEquals(1, repo.listBlocks("mysql").get(0).startLine());
    }

    @Test
    public void getBlockFindsWrittenId() {
        note("mysql", "MySQL 运维", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n\n# 慢查询\n\n正文二\n");
        Optional<KnowledgeBlock> hit = repo.getBlock("ku_abcd2345");
        assertTrue(hit.isPresent());
        assertEquals("索引", hit.get().title());
        assertTrue(hit.get().body().contains("正文一"));
    }

    @Test
    public void getBlockAcceptsIndexBlockId() {
        note("mysql", "MySQL 运维", "# 索引\n<!-- ku:abcd2345 -->\n\n" + "字".repeat(2500) + "\n");
        Optional<KnowledgeBlock> whole = repo.getBlock("ku_abcd2345");
        Optional<KnowledgeBlock> part = repo.getBlock("ku_abcd2345#p2");
        assertTrue(whole.isPresent());
        assertEquals(whole, part);
        assertFalse(part.get().isIndexBlock());
    }

    @Test
    public void getBlockUnknownIdIsEmpty() {
        note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        repo.refreshCatalog();
        assertTrue(repo.getBlock("ku_zzzz9999").isEmpty());
        assertTrue(repo.getBlock(null).isEmpty());
        assertTrue(repo.getBlock("").isEmpty());
    }

    @Test
    public void coldCatalogIsWarmedOnFirstLookup() {
        note("mysql", "MySQL 运维", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n");
        note("redis", "Redis 运维", "# 持久化\n\n正文二\n");
        assertTrue(catalog.isEmpty());

        assertTrue(repo.getBlock("ku_abcd2345").isPresent());
        assertFalse(catalog.isEmpty());
        assertEquals(Optional.of("redis"), catalog.findNoteKey(
                repo.listBlocks("redis").get(0).id()));
    }

    @Test
    public void locateReturnsFileAndLineRange() {
        note("mysql", "MySQL 运维", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n");
        KnowledgeBlock b = repo.getBlock("ku_abcd2345").orElseThrow();
        BlockLocation loc = repo.locate("ku_abcd2345").orElseThrow();

        assertEquals("mysql", loc.noteKey());
        assertEquals(storage.findNoteFileByKey("mysql"), loc.file());
        assertTrue(Files.exists(loc.file()));
        assertEquals(b.startLine(), loc.startLine());
        assertEquals(b.endLine(), loc.endLine());
        assertTrue(repo.locate("ku_zzzz9999").isEmpty());
    }

    @Test
    public void loadNoteTextStripsFrontMatter() {
        note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        String text = repo.loadNoteText("mysql");
        assertEquals("# 索引\n\n正文一", text);
        assertFalse(text.contains("---"));
    }

    @Test
    public void saveNoteTextPreservesFrontMatterBytes() throws Exception {
        note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        Path file = storage.findNoteFileByKey("mysql");
        String original = Files.readString(file, StandardCharsets.UTF_8);
        String frontMatter = original.substring(0, NoteFileStorage.bodyOffset(original));

        repo.saveNoteText("mysql", "# 慢查询\n\n正文二\n");

        String updated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(updated.startsWith(frontMatter), "front matter 必须逐字节保留");
        assertEquals("# 慢查询\n\n正文二\n", updated.substring(frontMatter.length()));
        assertEquals(List.of("慢查询"), titles(repo.listBlocks("mysql")));
    }

    @Test
    public void roundTripKeepsFrontMatterAndStabilizesAfterFirstSave() throws Exception {
        note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        Path file = storage.findNoteFileByKey("mysql");
        String before = Files.readString(file, StandardCharsets.UTF_8);

        repo.saveNoteText("mysql", repo.loadNoteText("mysql"));
        String once = Files.readString(file, StandardCharsets.UTF_8);
        // 正文读回时 trim 掉了文末换行，与 NoteService.save 的既有口径一致
        assertEquals(before.substring(0, NoteFileStorage.bodyOffset(before)),
                once.substring(0, NoteFileStorage.bodyOffset(once)));
        assertEquals(before.stripTrailing(), once.stripTrailing());

        repo.saveNoteText("mysql", repo.loadNoteText("mysql"));
        assertEquals(once, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    public void saveNoteTextRefreshesCatalog() {
        note("mysql", "MySQL 运维", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n");
        repo.refreshCatalog();
        assertEquals(Optional.of("mysql"), catalog.findNoteKey("ku_abcd2345"));

        repo.saveNoteText("mysql", "# 慢查询\n<!-- ku:abcd2345 -->\n\n正文二\n");
        assertEquals(List.of("慢查询"), titles(repo.listBlocks("mysql")));
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "mcx", 10).size());
        assertTrue(catalog.searchByTitlePrefix("mysql", "sy", 10).isEmpty());
    }

    @Test
    public void saveNoteTextLeavesNoTempFileBehind() throws Exception {
        note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        repo.saveNoteText("mysql", "# 慢查询\n\n正文二\n");
        try (var stream = Files.list(storage.findNoteFolderByKey("mysql"))) {
            assertEquals(List.of("note.md"), stream.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    public void saveNoteTextOnUnknownNoteThrows() {
        assertThrows(IllegalArgumentException.class, () -> repo.saveNoteText("nope", "正文"));
    }

    @Test
    public void encryptedNoteYieldsNoBlocksAndNoCatalogRows() {
        NoteDto n = note("secret", "私密笔记", "# 索引\n\n明文正文\n");
        n.encrypted = true;
        n.bodyMd = "BASE64CIPHERTEXT";
        storage.saveToFile(n);

        assertTrue(repo.listBlocks("secret").isEmpty());
        repo.refreshNote("secret");
        assertTrue(catalog.searchByTitlePrefix("secret", "", 10).isEmpty());
        assertEquals("BASE64CIPHERTEXT", repo.loadNoteText("secret"));
    }

    @Test
    public void encryptedNoteRowsAreClearedOnRefresh() {
        NoteDto n = note("secret", "私密笔记", "# 索引\n<!-- ku:abcd2345 -->\n\n明文正文\n");
        repo.refreshCatalog();
        assertEquals(Optional.of("secret"), catalog.findNoteKey("ku_abcd2345"));

        n.encrypted = true;
        n.bodyMd = "BASE64CIPHERTEXT";
        storage.saveToFile(n);
        repo.refreshNote("secret");

        assertEquals(Optional.empty(), catalog.findNoteKey("ku_abcd2345"));
    }

    @Test
    public void deletedNoteYieldsNoBlocks() {
        NoteDto n = note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        n.deleted = true;
        storage.saveToFile(n);
        assertTrue(repo.listBlocks("mysql").isEmpty());
    }

    @Test
    public void refreshCatalogCoversEveryNote() {
        note("mysql", "MySQL 运维", "# 索引\n\n正文一\n");
        note("redis", "Redis 运维", "# 持久化\n\n正文二\n");
        NoteDto secret = note("secret", "私密笔记", "# 索引\n\n正文三\n");
        secret.encrypted = true;
        storage.saveToFile(secret);

        assertEquals(3, repo.refreshCatalog());
        assertEquals(2, catalog.searchByTitlePrefix(null, "", 50).size());
    }

    @Test
    public void blocksFromDifferentNotesDoNotCollide() {
        note("mysql", "MySQL 运维", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n");
        note("redis", "Redis 运维", "# 持久化\n<!-- ku:efgh5672 -->\n\n正文二\n");
        repo.refreshCatalog();

        assertEquals("mysql", repo.getBlock("ku_abcd2345").orElseThrow().noteKey());
        assertEquals("redis", repo.getBlock("ku_efgh5672").orElseThrow().noteKey());
        assertNotEquals(repo.getBlock("ku_abcd2345").orElseThrow().contentHash(),
                repo.getBlock("ku_efgh5672").orElseThrow().contentHash());
    }
}