package com.gt.storage;

import com.gt.NoteDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * front matter 读写兼容性：旧文件可读、字段往返一致、tags 不丢失。
 */
public class FrontMatterCompatTest {

    @TempDir
    Path tempDir;

    private NoteDto newNote(String key) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = "标题";
        n.desc = "描述";
        n.bodyMd = "# 块一\n正文";
        long now = System.currentTimeMillis();
        n.createdAt = now;
        n.updatedAt = now;
        n.version = 1;
        return n;
    }

    @Test
    public void tagsSurviveSaveLoadRoundTrip() {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        NoteDto n = newNote("tagtest");
        n.tags = Arrays.asList("a", "b");

        storage.saveToFile(n);
        NoteDto loaded = storage.loadFromFile(storage.getNoteFolderPath(n));

        assertNotNull(loaded);
        assertEquals(Arrays.asList("a", "b"), loaded.tags);
    }

    @Test
    public void repeatedSaveDoesNotDropTags() {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        NoteDto n = newNote("tagtest2");
        n.tags = Arrays.asList("keep");
        storage.saveToFile(n);

        NoteDto loaded = storage.loadFromFile(storage.getNoteFolderPath(n));
        loaded.bodyMd = "# 块一\n改过的正文";
        loaded.version++;
        storage.saveToFile(loaded);

        NoteDto again = storage.loadFromFile(storage.getNoteFolderPath(loaded));
        assertEquals(Arrays.asList("keep"), again.tags);
    }

    @Test
    public void knowledgeMetadataSurvivesRoundTrip() {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        NoteDto n = newNote("metatest");
        n.domain = "大数据";
        n.topic = "Spark";
        n.noteType = "速查";
        n.layer = NoteDto.LAYER_RAW;
        n.visibility = NoteDto.VISIBILITY_AI;
        n.aliases = Arrays.asList("旧名", "spark-core");

        storage.saveToFile(n);
        NoteDto loaded = storage.loadFromFile(storage.getNoteFolderPath(n));

        assertEquals("大数据", loaded.domain);
        assertEquals("Spark", loaded.topic);
        assertEquals("速查", loaded.noteType);
        assertEquals(NoteDto.LAYER_RAW, loaded.layer);
        assertEquals(NoteDto.VISIBILITY_AI, loaded.visibility);
        assertEquals(Arrays.asList("旧名", "spark-core"), loaded.aliases);
    }

    /** 旧文件没有这些键，读入必须落到默认值而不是 null，否则下游分类与检索会 NPE。 */
    @Test
    public void legacyFileWithoutMetadataGetsDefaults() throws Exception {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        Path folder = tempDir.resolve("notes").resolve("legacy");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("note.md"), """
            ---
            id: "legacy-id"
            key: "legacy"
            title: "旧笔记"
            desc: "没有元数据字段"
            tags: ["old"]
            version: 1
            deleted: false
            createdAt: 2025-01-01 00:00:00
            updatedAt: 2025-01-01 00:00:00
            ---

            # 块一
            正文
            """, StandardCharsets.UTF_8);

        NoteDto loaded = storage.loadFromFile(folder);

        assertNotNull(loaded);
        assertEquals(NoteDto.DEFAULT_DOMAIN, loaded.domain);
        assertEquals(NoteDto.DEFAULT_TOPIC, loaded.topic);
        assertEquals(NoteDto.DEFAULT_NOTE_TYPE, loaded.noteType);
        assertEquals(NoteDto.LAYER_CURATED, loaded.layer);
        assertEquals(NoteDto.VISIBILITY_HUMAN, loaded.visibility);
        assertTrue(loaded.aliases.isEmpty());
        assertEquals(List.of("old"), loaded.tags);
    }

    /** 保存一次旧笔记后，新字段应被补全写回，不需要单独的迁移脚本。 */
    @Test
    public void savingLegacyNoteBackfillsMetadata() throws Exception {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        Path folder = tempDir.resolve("notes").resolve("backfill");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("note.md"), """
            ---
            id: "backfill-id"
            key: "backfill"
            title: "旧笔记"
            desc: "描述"
            tags: []
            version: 1
            deleted: false
            createdAt: 2025-01-01 00:00:00
            updatedAt: 2025-01-01 00:00:00
            ---

            正文
            """, StandardCharsets.UTF_8);

        NoteDto loaded = storage.loadFromFile(folder);
        loaded.domain = "数据库";
        loaded.topic = "MySQL";
        storage.saveToFile(loaded);

        String written = Files.readString(folder.resolve("note.md"), StandardCharsets.UTF_8);
        assertTrue(written.contains("domain: \"数据库\""), written);
        assertTrue(written.contains("topic: \"MySQL\""), written);
        assertTrue(written.contains("visibility: \"human\""), written);

        NoteDto again = storage.loadFromFile(folder);
        assertEquals("数据库", again.domain);
        assertEquals("MySQL", again.topic);
    }

    @Test
    public void encryptedNoteIsAlwaysPrivate() {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        NoteDto n = newNote("secret");
        n.encrypted = true;
        n.visibility = NoteDto.VISIBILITY_AI; // 即便调用方设成 ai 也必须被压回 private

        storage.saveToFile(n);
        NoteDto loaded = storage.loadFromFile(storage.getNoteFolderPath(n));

        assertEquals(NoteDto.VISIBILITY_PRIVATE, loaded.visibility);
        assertEquals(NoteDto.VISIBILITY_PRIVATE, n.effectiveVisibility());
    }

    /** front matter 可手工编辑，非法枚举值不能一路传到检索与 AI 侧。 */
    @Test
    public void invalidEnumValuesFallBackToDefaults() throws Exception {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        Path folder = tempDir.resolve("notes").resolve("badenum");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("note.md"), """
            ---
            id: "bad-id"
            key: "badenum"
            title: "非法值"
            desc: "描述"
            tags: []
            version: 1
            deleted: false
            createdAt: 2025-01-01 00:00:00
            updatedAt: 2025-01-01 00:00:00
            noteType: "不存在的类型"
            layer: "unknown"
            visibility: "public"
            ---

            正文
            """, StandardCharsets.UTF_8);

        NoteDto loaded = storage.loadFromFile(folder);

        assertEquals(NoteDto.DEFAULT_NOTE_TYPE, loaded.noteType);
        assertEquals(NoteDto.LAYER_CURATED, loaded.layer);
        assertEquals(NoteDto.VISIBILITY_HUMAN, loaded.visibility);
    }

    @Test
    public void aliasesWithSpecialCharactersSurviveRoundTrip() {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        NoteDto n = newNote("aliasspecial");
        n.aliases = Arrays.asList("带,逗号", "带\"引号\"", "带\\反斜杠");

        storage.saveToFile(n);
        NoteDto loaded = storage.loadFromFile(storage.getNoteFolderPath(n));

        assertEquals(n.aliases, loaded.aliases);
    }

    @Test
    public void personalDomainCannotBeAiVisible() {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        NoteDto n = newNote("personal");
        n.domain = NoteDto.DOMAIN_PERSONAL;
        n.topic = "日记";
        n.visibility = NoteDto.VISIBILITY_AI;

        storage.saveToFile(n);
        NoteDto loaded = storage.loadFromFile(storage.getNoteFolderPath(n));

        assertEquals(NoteDto.VISIBILITY_HUMAN, loaded.visibility);
    }

    @Test
    public void essayTypeCannotBeAiVisible() {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        NoteDto n = newNote("essay");
        n.noteType = NoteDto.TYPE_ESSAY;
        n.visibility = NoteDto.VISIBILITY_AI;

        storage.saveToFile(n);
        NoteDto loaded = storage.loadFromFile(storage.getNoteFolderPath(n));

        assertEquals(NoteDto.VISIBILITY_HUMAN, loaded.visibility);
    }

    /** _kb 是词表等内部数据，扫描笔记时必须跳过，否则会被当成一篇坏笔记。 */
    @Test
    public void scanAllNotesSkipsKbInternalDir() throws Exception {
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        storage.saveToFile(newNote("scanme"));
        Path kb = tempDir.resolve("notes").resolve("_kb");
        Files.createDirectories(kb);
        Files.writeString(kb.resolve("taxonomy.yaml"), "version: 1\n", StandardCharsets.UTF_8);

        assertEquals(1, storage.scanAllNotes().size());
    }
}