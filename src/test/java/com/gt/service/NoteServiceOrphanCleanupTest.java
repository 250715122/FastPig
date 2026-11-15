package com.gt.service;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.storage.NoteFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 清理孤立笔记的保留键回归。全部落在 @TempDir，不碰真实 notes / fastpig.db。
 * 向量检索未初始化，indexNote 会直接返回。
 */
public class NoteServiceOrphanCleanupTest {

    @TempDir
    Path tempDir;

    private NoteService service;

    @AfterEach
    public void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    private NoteDto note(String key) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = key;
        n.desc = key;
        n.bodyMd = "# 标题\n\n正文\n";
        n.createdAt = System.currentTimeMillis();
        n.updatedAt = n.createdAt;
        n.version = 1;
        return n;
    }

    @Test
    public void keepsReservedAllNoteButStillRemovesRealOrphan() throws Exception {
        NoteRepository repo = new NoteRepository(tempDir.resolve("fastpig.db").toString());
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        service = new NoteService(repo, storage);
        service.save(note("all"));
        service.save(note("mysql"));

        Path notesDir = storage.getNotesDir();
        Path orphan = notesDir.resolve("orphan");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("note.md"), "---\nkey: \"orphan\"\n---\n正文\n");

        assertEquals(1, service.cleanupOrphanedNotes(), "只该删掉真正孤立的 orphan");
        assertTrue(Files.exists(notesDir.resolve("all")), "all 是保留聚合键，不该被判为孤立笔记");
        assertTrue(Files.exists(notesDir.resolve("mysql")));
        assertFalse(Files.exists(orphan));
    }

    @Test
    public void keepsInternalKnowledgeDirs() throws Exception {
        NoteRepository repo = new NoteRepository(tempDir.resolve("fastpig2.db").toString());
        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        service = new NoteService(repo, storage);
        service.save(note("mysql"));

        Path notesDir = storage.getNotesDir();
        Files.createDirectories(notesDir.resolve("_kb"));
        Files.createDirectories(notesDir.resolve(".hidden"));

        assertEquals(0, service.cleanupOrphanedNotes());
        assertTrue(Files.exists(notesDir.resolve("_kb")));
        assertTrue(Files.exists(notesDir.resolve(".hidden")));
    }
}