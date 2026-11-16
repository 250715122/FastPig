package com.gt.service;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.kb.repo.BlockCatalog;
import com.gt.storage.NoteFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NoteService.save 与块目录的联动。全部落在 @TempDir，不碰真实 notes / fastpig.db。
 * 向量检索未初始化，indexNote / removeNoteIndex 都会直接返回，不会触发建索引。
 */
public class NoteServiceBlockCatalogTest {

    @TempDir
    Path tempDir;

    private NoteFileStorage storage;
    private NoteService service;
    private BlockCatalog catalog;

    @BeforeEach
    public void setUp() {
        String db = tempDir.resolve("fastpig.db").toString();
        NoteRepository repository = new NoteRepository(db);
        storage = new NoteFileStorage(tempDir.toString());
        service = new NoteService(repository, storage);
        catalog = new BlockCatalog(db);
    }

    @AfterEach
    public void tearDown() {
        service.close();
        catalog.close();
    }

    private NoteDto note(String key, String body) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = "MySQL 运维";
        n.desc = "MySQL 运维";
        n.bodyMd = body;
        n.createdAt = System.currentTimeMillis();
        n.updatedAt = n.createdAt;
        n.version = 1;
        return n;
    }

    @Test
    public void savePopulatesBlockCatalog() {
        service.save(note("mysql", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n"));

        assertEquals(Optional.of("mysql"), catalog.findNoteKey("ku_abcd2345"));
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "sy", 10).size());
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "suoyin", 10).size());
    }

    @Test
    public void secondSaveReplacesStaleBlocks() {
        NoteDto n = note("mysql", "# 索引\n\n正文一\n");
        service.save(n);
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "sy", 10).size());

        n.bodyMd = "# 慢查询\n\n正文二\n";
        service.save(n);

        assertTrue(catalog.searchByTitlePrefix("mysql", "sy", 10).isEmpty());
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "mcx", 10).size());
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "", 50).size());
    }

    @Test
    public void savingDeletedNoteClearsItsBlocks() {
        NoteDto n = note("mysql", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n");
        service.save(n);
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "", 50).size());

        n.deleted = true;
        service.save(n);

        assertTrue(catalog.searchByTitlePrefix("mysql", "", 50).isEmpty());
        assertEquals(Optional.empty(), catalog.findNoteKey("ku_abcd2345"));
    }

    @Test
    public void catalogSurvivesReopen() {
        service.save(note("mysql", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n"));
        catalog.close();

        try (BlockCatalog reopened = new BlockCatalog(tempDir.resolve("fastpig.db").toString())) {
            assertEquals(Optional.of("mysql"), reopened.findNoteKey("ku_abcd2345"));
        }
    }

    @Test
    public void deleteClearsBlockCatalog() {
        NoteDto n = note("mysql", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n");
        service.save(n);
        assertEquals(Optional.of("mysql"), catalog.findNoteKey("ku_abcd2345"));

        service.delete(n.id);

        assertEquals(Optional.empty(), catalog.findNoteKey("ku_abcd2345"));
        assertTrue(catalog.searchByTitlePrefix("mysql", "", 50).isEmpty());
    }

    @Test
    public void restoreRepopulatesBlockCatalog() {
        NoteDto n = note("mysql", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n");
        service.save(n);
        service.delete(n.id);
        assertTrue(catalog.searchByTitlePrefix("mysql", "", 50).isEmpty());

        service.restore("mysql");

        assertEquals(Optional.of("mysql"), catalog.findNoteKey("ku_abcd2345"));
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "sy", 10).size());
    }

    /** 云同步下载只改文件、不走 save，重建索引必须把块目录一起带上，否则树形目录停在旧行号。 */
    @Test
    public void rebuildIndexFromFilesRefreshesStaleCatalog() {
        NoteDto n = note("mysql", "# 索引\n\n正文一\n");
        service.save(n);
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "sy", 10).size());

        NoteDto onDisk = storage.loadFromFile(storage.findNoteFolderByKey("mysql"));
        onDisk.bodyMd = "# 慢查询\n\n正文二\n";
        storage.saveToFile(onDisk);
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "sy", 10).size(), "前提：此刻目录还是旧的");

        service.rebuildIndexFromFiles();

        assertTrue(catalog.searchByTitlePrefix("mysql", "sy", 10).isEmpty());
        assertEquals(1, catalog.searchByTitlePrefix("mysql", "mcx", 10).size());
    }
}