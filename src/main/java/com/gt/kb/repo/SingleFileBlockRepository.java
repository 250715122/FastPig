package com.gt.kb.repo;

import com.gt.AppConfig;
import com.gt.NoteDto;
import com.gt.config.ConfigKeys;
import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.chunk.Chunker;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.storage.NoteFileStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单文件块仓库：一篇笔记一个 note.md，块由 {@link DefaultChunker} 现算，不单独落盘。
 *
 * 缓存按正文原文比对而非规范化哈希：只改空白也会移动行号，
 * 用规范化哈希会让缓存里的 startLine 与文件错位，跳转就跳偏了。
 */
public class SingleFileBlockRepository implements BlockRepository, AutoCloseable {

    private static final Logger logger = LogManager.getLogger(SingleFileBlockRepository.class);

    private final NoteFileStorage storage;
    private final BlockCatalog catalog;
    private final Chunker chunker = new DefaultChunker();
    private final Map<String, Cached> cache;

    public SingleFileBlockRepository(NoteFileStorage storage, BlockCatalog catalog) {
        this.storage = storage;
        this.catalog = catalog;
        int maxNotes = AppConfig.getInstance().getInt(ConfigKeys.KB_BLOCK_CACHE_MAX_NOTES);
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, Cached>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > maxNotes;
            }
        });
    }

    @Override
    public List<KnowledgeBlock> listBlocks(String noteKey) {
        NoteDto note = load(noteKey);
        // 私密笔记的正文是密文，切出来的块既无意义又会把明文标题带进目录，一律不出块
        if (note == null || note.deleted || note.encrypted) {
            return List.of();
        }
        String body = note.bodyMd == null ? "" : note.bodyMd;
        Cached hit = cache.get(noteKey);
        if (hit != null && hit.body.equals(body)) {
            return hit.blocks;
        }
        List<KnowledgeBlock> blocks = chunker.chunk(noteKey, note.desc, body,
                ChunkProfile.forNote(note.noteType, note.layer));
        cache.put(noteKey, new Cached(body, blocks));
        return blocks;
    }

    @Override
    public Optional<KnowledgeBlock> getBlock(String blockId) {
        String id = knowledgeId(blockId);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        String noteKey = resolveNoteKey(id);
        if (noteKey == null) {
            return Optional.empty();
        }
        return listBlocks(noteKey).stream().filter(b -> b.id().equals(id)).findFirst();
    }

    @Override
    public Optional<BlockLocation> locate(String blockId) {
        return getBlock(blockId).flatMap(b -> {
            Path file = storage.findNoteFileByKey(b.noteKey());
            return file == null ? Optional.empty()
                    : Optional.of(new BlockLocation(b.noteKey(), file, b.id(), b.startLine(), b.endLine()));
        });
    }

    /** 返回文件里的原始正文：私密笔记是密文，解密由 GUI 侧负责。 */
    @Override
    public String loadNoteText(String noteKey) {
        NoteDto note = load(noteKey);
        return note == null || note.bodyMd == null ? "" : note.bodyMd;
    }

    @Override
    public void saveNoteText(String noteKey, String text) {
        Path file = storage.findNoteFileByKey(noteKey);
        if (file == null) {
            throw new IllegalArgumentException("笔记不存在: " + noteKey);
        }
        try {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            int offset = NoteFileStorage.bodyOffset(original);
            atomicWrite(file, original.substring(0, offset) + (text == null ? "" : text));
        } catch (IOException e) {
            throw new RuntimeException("保存笔记正文失败: " + noteKey + ", " + e.getMessage(), e);
        }
        refreshNote(noteKey);
    }

    /** 用文件现状刷新单篇笔记的目录行。私密笔记切不出块，等同清空。 */
    public void refreshNote(String noteKey) {
        catalog.replaceNote(noteKey, listBlocks(noteKey));
    }

    /** 让目录与文件重新对齐，返回处理过的笔记数。 */
    public int refreshCatalog() {
        int n = 0;
        for (NoteDto note : storage.scanAllNotes()) {
            if (note.key == null || note.key.isEmpty()) {
                continue;
            }
            refreshNote(note.key);
            n++;
        }
        logger.info("[BlockRepo] 块目录已重建，笔记数={}", n);
        return n;
    }

    /** 释放块目录的数据库连接。Windows 下不释放会一直锁住 db 文件；关闭后再用会自动重连。 */
    @Override
    public void close() {
        catalog.close();
    }

    private NoteDto load(String noteKey) {
        if (noteKey == null || noteKey.isEmpty()) {
            return null;
        }
        Path folder = storage.findNoteFolderByKey(noteKey);
        return folder == null ? null : storage.loadFromFile(folder);
    }

    private String resolveNoteKey(String blockId) {
        Optional<String> hit = catalog.findNoteKey(blockId);
        if (hit.isPresent()) {
            return hit.get();
        }
        // 目录非空却查不到，说明块真的没了；只有目录从未建立时才值得全量扫一遍
        if (!catalog.isEmpty()) {
            return null;
        }
        refreshCatalog();
        return catalog.findNoteKey(blockId).orElse(null);
    }

    /** 索引块 ID 归一到所属知识块 ID。 */
    private static String knowledgeId(String blockId) {
        if (blockId == null) {
            return "";
        }
        int sep = blockId.indexOf('#');
        return (sep < 0 ? blockId : blockId.substring(0, sep)).strip();
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private record Cached(String body, List<KnowledgeBlock> blocks) {
    }
}