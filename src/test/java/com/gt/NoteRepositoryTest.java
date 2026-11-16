package com.gt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoteRepositoryTest {

    @TempDir
    Path tempDir;

    private NoteDto newNote(String key) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = "单元测试标题";
        n.desc = "单元测试摘要";
        n.tags = Arrays.asList("test", "java");
        n.bodyMd = "# Hello\n这是测试内容";
        n.frontMatter = null;
        long now = System.currentTimeMillis();
        n.createdAt = now;
        n.updatedAt = now;
        n.version = 1;
        return n;
    }

    private NoteDto findByKey(NoteRepository repo, String key) {
        List<NoteDto> hits = repo.searchByKeyOrText(key, 10);
        assertFalse(hits.isEmpty(), "未找到笔记: " + key);
        return hits.get(0);
    }

    @Test
    public void findAllKeysKeepsReservedAllKey() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("keys.db").toString());
        repo.save(newNote("all"));
        repo.save(newNote("mysql"));

        // findAllCommandsAndDescriptions 是「用于生成 all 聚合」的查询，故意排除 all 本身
        assertFalse(repo.findAllCommandsAndDescriptions().stream().anyMatch(n -> "all".equals(n.key)));
        // 清理孤立笔记必须用不带过滤的 key 列表，否则 all 每次启动都被误判成孤立笔记删掉
        assertTrue(repo.findAllKeys().containsAll(Arrays.asList("all", "mysql")));
    }

    @Test
    public void findAllKeysSkipsDeletedNotes() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("keys-deleted.db").toString());
        repo.save(newNote("live"));
        NoteDto gone = newNote("gone");
        gone.deleted = true;
        repo.save(gone);

        assertEquals(List.of("live"), repo.findAllKeys());
    }

    @Test
    public void testSaveAndSearch() {
        String db = tempDir.resolve("test-fastpig.db").toString();
        NoteRepository repo = new NoteRepository(db);

        repo.save(newNote("unittest"));

        assertTrue(repo.searchByKeyOrText("unittest", 10).size() >= 1);
        assertTrue(repo.searchByKeyOrText("测试", 10).size() >= 1);
    }

    @Test
    public void savePersistsKnowledgeMetadata() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("meta.db").toString());
        NoteDto n = newNote("metakey");
        n.domain = "数据库";
        n.topic = "MySQL";
        n.noteType = "速查";
        n.layer = NoteDto.LAYER_RAW;
        n.visibility = NoteDto.VISIBILITY_AI;
        n.aliases = Arrays.asList("旧名", "mysql-notes");

        repo.save(n);
        NoteDto loaded = findByKey(repo, "metakey");

        assertEquals("数据库", loaded.domain);
        assertEquals("MySQL", loaded.topic);
        assertEquals("速查", loaded.noteType);
        assertEquals(NoteDto.LAYER_RAW, loaded.layer);
        assertEquals(NoteDto.VISIBILITY_AI, loaded.visibility);
        assertEquals(Arrays.asList("旧名", "mysql-notes"), loaded.aliases);
    }

    @Test
    public void saveIndexPersistsKnowledgeMetadata() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("metaidx.db").toString());
        NoteDto n = newNote("idxkey");
        n.domain = "大数据";
        n.topic = "Spark";
        n.noteType = "日志";
        n.layer = NoteDto.LAYER_RAW;
        n.aliases = List.of("spark");

        repo.saveIndex(n);
        NoteDto loaded = findByKey(repo, "idxkey");

        assertEquals("大数据", loaded.domain);
        assertEquals("Spark", loaded.topic);
        assertEquals("日志", loaded.noteType);
        assertEquals(NoteDto.LAYER_RAW, loaded.layer);
        assertEquals(NoteDto.VISIBILITY_HUMAN, loaded.visibility);
        assertEquals(List.of("spark"), loaded.aliases);
    }

    /** 重复保存同一 key 走的是按 key 更新的分支，元数据同样不能丢。 */
    @Test
    public void repeatedSaveByKeyKeepsMetadata() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("repeat.db").toString());
        NoteDto first = newNote("repeatkey");
        first.domain = "运维";
        first.topic = "Linux";
        repo.save(first);

        NoteDto second = newNote("repeatkey"); // 新 id，同 key
        second.domain = "编程语言";
        second.topic = "Java";
        second.aliases = List.of("java-note");
        repo.save(second);

        NoteDto loaded = findByKey(repo, "repeatkey");
        assertEquals("编程语言", loaded.domain);
        assertEquals("Java", loaded.topic);
        assertEquals(List.of("java-note"), loaded.aliases);
    }

    @Test
    public void encryptedNoteCannotBeAiVisible() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("enc.db").toString());
        NoteDto n = newNote("enckey");
        n.encrypted = true;
        n.visibility = NoteDto.VISIBILITY_AI;

        repo.saveIndex(n);
        NoteDto loaded = findByKey(repo, "enckey");

        assertEquals(NoteDto.VISIBILITY_PRIVATE, loaded.visibility);
    }

    /** 未设置元数据的笔记读出来必须是默认值，不能是 null。 */
    @Test
    public void unsetMetadataReadsBackAsDefaults() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("defaults.db").toString());
        repo.save(newNote("defaultkey"));

        NoteDto loaded = findByKey(repo, "defaultkey");

        assertEquals(NoteDto.DEFAULT_DOMAIN, loaded.domain);
        assertEquals(NoteDto.DEFAULT_TOPIC, loaded.topic);
        assertEquals(NoteDto.DEFAULT_NOTE_TYPE, loaded.noteType);
        assertEquals(NoteDto.LAYER_CURATED, loaded.layer);
        assertEquals(NoteDto.VISIBILITY_HUMAN, loaded.visibility);
        assertTrue(loaded.aliases.isEmpty());
    }

    @Test
    public void personalDomainCannotBeAiVisible() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("personal.db").toString());
        NoteDto n = newNote("personalkey");
        n.domain = NoteDto.DOMAIN_PERSONAL;
        n.topic = "日记";
        n.visibility = NoteDto.VISIBILITY_AI;

        repo.saveIndex(n);

        assertEquals(NoteDto.VISIBILITY_HUMAN, findByKey(repo, "personalkey").visibility);
    }

    @Test
    public void essayTypeCannotBeAiVisible() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("essay.db").toString());
        NoteDto n = newNote("essaykey");
        n.noteType = NoteDto.TYPE_ESSAY;
        n.visibility = NoteDto.VISIBILITY_AI;

        repo.saveIndex(n);

        assertEquals(NoteDto.VISIBILITY_HUMAN, findByKey(repo, "essaykey").visibility);
    }

    /** 树形目录的数据源：分类字段要齐全，但正文和标签不该被加载。 */
    @Test
    public void findAllMetadataCarriesClassificationWithoutBody() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("tree-meta.db").toString());
        NoteDto n = newNote("mysql");
        n.domain = "数据库";
        n.topic = "MySQL";
        n.noteType = "速查";
        n.layer = NoteDto.LAYER_RAW;
        n.aliases = List.of("mysql-notes");
        repo.save(n);

        List<NoteDto> meta = repo.findAllMetadata();
        assertEquals(1, meta.size());
        NoteDto loaded = meta.get(0);
        assertEquals("mysql", loaded.key);
        assertEquals("单元测试标题", loaded.title);
        assertEquals("数据库", loaded.domain);
        assertEquals("MySQL", loaded.topic);
        assertEquals("速查", loaded.noteType);
        assertEquals(NoteDto.LAYER_RAW, loaded.layer);
        assertEquals(List.of("mysql-notes"), loaded.aliases);
        assertNull(loaded.bodyMd, "目录查询不该加载正文");
        assertNull(loaded.frontMatter);
        assertNull(loaded.tags);
    }

    /** 过滤 all 是目录层的职责，查询层要如实返回，否则又会出现「数据库里有哪些笔记」口径不一致。 */
    @Test
    public void findAllMetadataKeepsAllAndSkipsDeleted() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("tree-scope.db").toString());
        repo.save(newNote("all"));
        repo.save(newNote("mysql"));
        NoteDto gone = newNote("gone");
        gone.deleted = true;
        repo.save(gone);

        assertEquals(List.of("all", "mysql"), repo.findAllMetadata().stream().map(n -> n.key).toList());
    }

    @Test
    public void findAllMetadataMarksEncryptedAsPrivateAndLocked() {
        NoteRepository repo = new NoteRepository(tempDir.resolve("tree-enc.db").toString());
        NoteDto n = newNote("secret");
        n.encrypted = true;
        n.visibility = NoteDto.VISIBILITY_AI;
        repo.saveIndex(n);

        NoteDto loaded = repo.findAllMetadata().get(0);
        assertTrue(loaded.encrypted);
        assertTrue(loaded.locked);
        assertEquals(NoteDto.VISIBILITY_PRIVATE, loaded.visibility);
        assertNull(loaded.bodyMd);
    }
}