package com.gt.service;

import com.gt.AppConfig;
import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.config.ConfigKeys;
import com.gt.crypto.NoteEncryptionService;
import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.id.BlockIdAssigner;
import com.gt.kb.id.BlockIdCodec;
import com.gt.kb.id.BlockIdEditorSync;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.repo.BlockCatalog;
import com.gt.storage.NoteFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTextArea;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保存路径自动补块 ID。全部落在 @TempDir，不碰真实 notes / fastpig.db / config.properties。
 * 向量检索未初始化，indexNote / removeNoteIndex 都会直接返回。
 */
public class NoteServiceBlockIdTest {

    private static final DefaultChunker CHUNKER = new DefaultChunker();
    private static final String BODY = "# 索引\n\n正文一\n\n# 慢查询\n\n正文二\n";

    @TempDir
    Path tempDir;

    private NoteFileStorage storage;
    private NoteService service;
    private BlockCatalog catalog;
    private String autoAssignSnapshot;

    @BeforeEach
    public void setUp() {
        String db = tempDir.resolve("fastpig.db").toString();
        NoteRepository repository = new NoteRepository(db);
        storage = new NoteFileStorage(tempDir.toString());
        service = new NoteService(repository, storage);
        catalog = new BlockCatalog(db);
        autoAssignSnapshot = AppConfig.getInstance().getString(ConfigKeys.KB_ID_AUTO_ASSIGN.key);
    }

    @AfterEach
    public void tearDown() {
        AppConfig.getInstance().setString(ConfigKeys.KB_ID_AUTO_ASSIGN.key, autoAssignSnapshot);
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

    private List<KnowledgeBlock> blocks(NoteDto n) {
        return CHUNKER.chunk(n.key, n.desc, n.bodyMd, ChunkProfile.forNote(n.noteType, n.layer));
    }

    @Test
    public void saveAssignsIdsAndCatalogUsesThem() {
        NoteDto n = note("mysql", BODY);
        BlockIdAssigner.Result result = service.save(n);

        assertEquals(2, result.assigned());
        assertEquals(0, result.reassigned());
        List<KnowledgeBlock> blocks = blocks(n);
        assertEquals(2, blocks.size());
        for (KnowledgeBlock b : blocks) {
            assertTrue(BlockIdCodec.isValidId(b.id()), "块 ID 不合法: " + b.id());
            assertEquals(Optional.of("mysql"), catalog.findNoteKey(b.id()));
        }
        // NoteFileStorage 读回正文时会 trim，首尾空白不参与比对
        assertEquals(n.bodyMd.trim(), storage.loadById(n.id).bodyMd, "落盘正文与内存不一致");
    }

    @Test
    public void secondSaveKeepsIdsStable() {
        NoteDto n = note("mysql", BODY);
        service.save(n);
        String first = n.bodyMd;
        List<String> ids = blocks(n).stream().map(KnowledgeBlock::id).toList();

        BlockIdAssigner.Result again = service.save(n);

        assertFalse(again.changed());
        assertEquals(first, n.bodyMd);
        assertEquals(ids, blocks(n).stream().map(KnowledgeBlock::id).toList());
    }

    @Test
    public void editingKeepsOtherBlockIds() {
        NoteDto n = note("mysql", BODY);
        service.save(n);
        String firstId = blocks(n).get(0).id();

        n.bodyMd = n.bodyMd.replace("# 慢查询", "# 慢查询排查");
        BlockIdAssigner.Result result = service.save(n);

        assertFalse(result.changed(), "改标题不该触发重新分配");
        assertEquals(firstId, blocks(n).get(0).id());
    }

    @Test
    public void newBlockGetsIdWithoutTouchingExistingOnes() {
        NoteDto n = note("mysql", BODY);
        service.save(n);
        List<String> before = blocks(n).stream().map(KnowledgeBlock::id).toList();

        n.bodyMd = n.bodyMd + "\n# 新增块\n\n正文三\n";
        BlockIdAssigner.Result result = service.save(n);

        assertEquals(1, result.assigned());
        List<String> after = blocks(n).stream().map(KnowledgeBlock::id).toList();
        assertEquals(before, after.subList(0, before.size()));
        assertTrue(BlockIdCodec.isValidId(after.get(2)));
    }

    /** 编辑器回填后再保存必须一个 ID 都不补，否则块 ID 每次保存都在变。 */
    @Test
    public void editorRoundTripDoesNotChurnIds() throws Exception {
        JTextArea area = new JTextArea();
        area.setText("mysql MySQL 运维\n" + BODY);
        NoteDto n = note("mysql", BODY);

        BlockIdAssigner.Result result = service.save(n);
        BlockIdEditorSync.apply(area, result);
        assertEquals("mysql MySQL 运维\n" + n.bodyMd, area.getText());

        String text = area.getText();
        n.bodyMd = text.substring(text.indexOf('\n') + 1);
        assertFalse(service.save(n).changed(), "回填后的文本再保存又补了 ID");
    }

    @Test
    public void autoAssignDisabledLeavesBodyUntouched() {
        AppConfig.getInstance().set(ConfigKeys.KB_ID_AUTO_ASSIGN, "false");
        NoteDto n = note("mysql", BODY);

        BlockIdAssigner.Result result = service.save(n);

        assertFalse(result.changed());
        assertEquals(BODY, n.bodyMd);
        assertTrue(blocks(n).get(0).id().startsWith("tmp_"), "关掉开关后仍在补 ID");
    }

    @Test
    public void encryptedNoteIsSkipped() throws Exception {
        NoteDto n = note("secret", BODY);
        NoteEncryptionService.enable(n, "note-pwd".toCharArray(), "master-pwd".toCharArray());

        BlockIdAssigner.Result result = service.save(n);

        assertFalse(result.changed());
        assertEquals(BODY, n.bodyMd, "保存后没有把明文还原回 DTO");

        NoteDto loaded = service.loadByKey("secret");
        assertTrue(loaded.encrypted);
        NoteEncryptionService.unlock(loaded, "note-pwd".toCharArray(), false);
        assertEquals(BODY, loaded.bodyMd, "私密笔记被写进了 ID 行");
    }

    @Test
    public void idTakenByAnotherNoteIsReassigned() {
        service.save(note("mysql", "# 索引\n<!-- ku:abcd2345 -->\n\n正文一\n"));

        NoteDto other = note("redis", "# 索引\n<!-- ku:abcd2345 -->\n\n正文二\n");
        BlockIdAssigner.Result result = service.save(other);

        assertEquals(1, result.reassigned());
        assertFalse(other.bodyMd.contains("ku:abcd2345"));
        assertEquals(Optional.of("mysql"), catalog.findNoteKey("ku_abcd2345"));
        assertNotEquals("ku_abcd2345", blocks(other).get(0).id());
    }

    @Test
    public void mixedLineEndingsSurviveSave() {
        String body = "# 标题\r\n\r\n正文甲\n\n## 第二节\r正文乙\r\n";
        NoteDto n = note("mysql", body);

        service.save(n);

        assertTrue(BlockIdAssigner.preservesText(body, n.bodyMd), "行尾符被改动了");
    }

    @Test
    public void blankBodyIsSavedAsIs() {
        NoteDto n = note("mysql", "");
        assertFalse(service.save(n).changed());
        assertEquals("", n.bodyMd);
    }
}