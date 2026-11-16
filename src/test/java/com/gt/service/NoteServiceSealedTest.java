package com.gt.service;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.kb.repo.BlockCatalog;
import com.gt.storage.NoteFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网页端端到端加密依赖的落盘契约：服务端只见密文。
 * 全部落在 @TempDir，不碰真实 notes / fastpig.db。
 */
public class NoteServiceSealedTest {

    private static final String SECRET = "SECRET_TOKEN_只在明文时代存在";
    private static final String CIPHER = "Zm9vYmFyY2lwaGVydGV4dA==|iv1234567890abcd";

    @TempDir
    Path tempDir;

    private NoteRepository repository;
    private NoteFileStorage storage;
    private NoteService service;
    private BlockCatalog catalog;

    @BeforeEach
    public void setUp() {
        String db = tempDir.resolve("fastpig.db").toString();
        repository = new NoteRepository(db);
        storage = new NoteFileStorage(tempDir.toString());
        service = new NoteService(repository, storage);
        catalog = new BlockCatalog(db);
    }

    @AfterEach
    public void tearDown() {
        service.close();
        catalog.close();
    }

    private NoteDto plainNote(String key) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = "RabbitMQ 运维";
        n.desc = "RabbitMQ 运维";
        n.bodyMd = "# 堆积\n\n" + SECRET + "\n\n# 重启\n\n正文二\n";
        n.createdAt = System.currentTimeMillis();
        n.updatedAt = n.createdAt;
        n.version = 1;
        return n;
    }

    private String rawFile(NoteDto n) throws Exception {
        return Files.readString(storage.getNoteFolderPath(n).resolve("note.md"));
    }

    @Test
    public void sealedSaveLeavesNoPlaintextAnywhere() throws Exception {
        NoteDto n = plainNote("sealed1");
        service.save(n);
        assertTrue(rawFile(n).contains(SECRET), "明文保存阶段就该能在文件里看到正文");
        assertFalse(service.blockRepository().listBlockSummaries(n.key).isEmpty(), "明文阶段块目录有行");

        n.encrypted = true;
        n.bodyMd = CIPHER;
        service.saveSealed(n, "abc123hash");

        String raw = rawFile(n);
        assertTrue(raw.contains(CIPHER), "密文正文要原样落盘");
        assertFalse(raw.contains(SECRET), "加密后文件里不能残留任何明文片段");

        assertNull(repository.findByKey(n.key).bodyMd, "索引里的正文必须清空");
        assertFalse(service.loadByKey(n.key).bodyMd.contains(SECRET));
        assertEquals(CIPHER, service.loadByKey(n.key).bodyMd, "重载回来的就是密文");
    }

    @Test
    public void sealedSaveDropsBlockCatalogRows() {
        NoteDto n = plainNote("sealed2");
        service.save(n);
        int before = service.blockRepository().listBlockSummaries(n.key).size();
        assertTrue(before > 0);

        n.encrypted = true;
        n.bodyMd = CIPHER;
        service.saveSealed(n, "hash");

        assertEquals(0, service.blockRepository().listBlockSummaries(n.key).size(),
                "密文切不出块，目录行必须一起消失，否则标题会泄露给检索");
    }

    @Test
    public void sealedSaveBumpsVersionAndStoresPlaintextHash() {
        NoteDto n = plainNote("sealed3");
        service.save(n);
        int v0 = n.version;

        n.encrypted = true;
        n.bodyMd = CIPHER;
        service.saveSealed(n, "plain-hash-1");

        assertEquals(v0 + 1, n.version, "每次成功保存都要升版本");
        assertEquals("plain-hash-1", repository.findByKey(n.key).contentHash,
                "服务端只存明文哈希，用来判断有没有改动");
    }

    @Test
    public void sealedSaveRefusesWrongShapes() throws Exception {
        NoteDto notPrivate = plainNote("sealed4");
        service.save(notPrivate);
        notPrivate.bodyMd = CIPHER;
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> service.saveSealed(notPrivate, "h"));
        assertTrue(e1.getMessage().contains("私密"), e1.getMessage());

        NoteDto privateNote = plainNote("sealed5");
        service.save(privateNote);
        privateNote.encrypted = true;
        privateNote.bodyMd = "";
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> service.saveSealed(privateNote, "h"));
        assertTrue(e2.getMessage().contains("密文正文为空"), e2.getMessage());

        NoteDto noId = plainNote("sealed6");
        noId.id = null;
        noId.encrypted = true;
        noId.bodyMd = CIPHER;
        assertThrows(IllegalArgumentException.class, () -> service.saveSealed(noId, "h"));

        assertFalse(rawFile(notPrivate).contains(CIPHER), "被拒绝的调用不能已经改了文件");
    }

    @Test
    public void sealedSaveIgnoresNullHash() {
        NoteDto n = plainNote("sealed7");
        service.save(n);
        n.encrypted = true;
        n.bodyMd = CIPHER;
        service.saveSealed(n, null);
        assertEquals("", repository.findByKey(n.key).contentHash, "没有哈希时存空串，不能存 null");
    }
}
