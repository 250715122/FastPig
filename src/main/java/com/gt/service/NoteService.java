package com.gt.service;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.storage.NoteFileStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

/**
 * 笔记业务服务
 * 统一协调文件存储和 SQLite 索引
 * 
 * 数据流：
 * - 保存：同时写入文件和更新索引
 * - 加载：从索引查元数据，从文件读正文
 * - 搜索：查询索引返回结果
 */
public class NoteService {

    private static NoteService instance;

    private final NoteRepository repository;
    private final NoteFileStorage fileStorage;

    public NoteService(NoteRepository repository, NoteFileStorage fileStorage) {
        this.repository = repository;
        this.fileStorage = fileStorage;
    }

    public static synchronized NoteService getInstance(NoteRepository repository) {
        if (instance == null) {
            instance = new NoteService(repository, NoteFileStorage.getInstance());
        }
        return instance;
    }

    /**
     * 保存笔记
     * 同时写入文件和更新索引
     */
    public void save(NoteDto note) {
        if (note == null || note.id == null) {
            throw new IllegalArgumentException("笔记或笔记ID不能为空");
        }

        // 检查是否需要重命名文件夹（key 变化时）
        NoteDto existing = repository.findById(note.id);
        if (existing != null && existing.key != null && !existing.key.equals(note.key)) {
            fileStorage.renameNoteFolder(note.id, existing.key, note.key);
        }

        // 计算内容哈希
        note.contentHash = computeHash(note.bodyMd);

        // 保存到文件
        fileStorage.saveToFile(note);

        // 更新文件夹路径
        Path folderPath = fileStorage.getNoteFolderPath(note);
        note.folderPath = folderPath.toString();

        // 更新索引（不存储正文，只存储元数据）
        repository.saveIndex(note);

        System.out.println("[NoteService] 已保存笔记: " + note.key + " (id=" + note.id + ")");
    }

    /**
     * 加载笔记
     * 从索引查元数据，从文件读正文
     */
    public NoteDto load(String id) {
        // 先从索引获取元数据
        NoteDto indexData = repository.findById(id);
        if (indexData == null) {
            // 尝试直接从文件加载
            return fileStorage.loadById(id);
        }

        // 从文件读取正文
        NoteDto fileData = fileStorage.loadById(id);
        if (fileData != null) {
            // 合并：使用索引的元数据 + 文件的正文
            indexData.bodyMd = fileData.bodyMd;
            indexData.frontMatter = fileData.frontMatter;
        }

        return indexData;
    }

    /**
     * 通过 key 加载笔记
     */
    public NoteDto loadByKey(String key) {
        NoteDto indexData = repository.findByKey(key);
        if (indexData == null) {
            return null;
        }

        // 从文件读取正文
        NoteDto fileData = fileStorage.loadById(indexData.id);
        if (fileData != null) {
            indexData.bodyMd = fileData.bodyMd;
            indexData.frontMatter = fileData.frontMatter;
        }

        return indexData;
    }

    /**
     * 搜索笔记（按 key 或文本）
     * 返回的结果不包含正文，需要单独加载
     */
    public List<NoteDto> search(String query, int limit) {
        return repository.searchByKeyOrText(query, limit);
    }

    /**
     * 按 key 前缀搜索
     */
    public List<NoteDto> searchByKeyPrefix(String prefix, int limit) {
        return repository.searchByKeyPrefix(prefix, limit);
    }

    /**
     * 按描述搜索
     */
    public List<NoteDto> searchByDesc(String query, int limit) {
        return repository.searchByDescContains(query, limit);
    }

    /**
     * 软删除笔记
     */
    public void delete(String id) {
        // 更新索引中的删除标记
        repository.softDelete(id);

        // 更新文件中的删除标记
        NoteDto note = fileStorage.loadById(id);
        if (note != null) {
            note.deleted = true;
            note.updatedAt = System.currentTimeMillis();
            fileStorage.saveToFile(note);
        }

        System.out.println("[NoteService] 已删除笔记: " + id);
    }

    /**
     * 恢复笔记
     */
    public void restore(String key) {
        // 更新索引中的删除标记
        repository.restoreByKey(key);

        // 更新文件中的删除标记
        NoteDto indexData = repository.findByKey(key);
        if (indexData != null) {
            NoteDto note = fileStorage.loadById(indexData.id);
            if (note != null) {
                note.deleted = false;
                note.updatedAt = System.currentTimeMillis();
                fileStorage.saveToFile(note);
            }
        }

        System.out.println("[NoteService] 已恢复笔记: " + key);
    }

    /**
     * 获取所有笔记命令列表
     */
    public List<NoteDto> getAllCommands() {
        return repository.findAllCommandsAndDescriptions();
    }

    /**
     * 从文件重建索引
     * 扫描 notes 目录，更新 SQLite 索引
     */
    public int rebuildIndexFromFiles() {
        System.out.println("[NoteService] 开始从文件重建索引...");
        
        List<NoteDto> notes = fileStorage.scanAllNotes();
        int count = 0;

        for (NoteDto note : notes) {
            try {
                note.contentHash = computeHash(note.bodyMd);
                note.folderPath = fileStorage.getNoteFolderPath(note).toString();
                repository.saveIndex(note);
                count++;
            } catch (Exception e) {
                System.err.println("[NoteService] 索引笔记失败: " + note.id + " - " + e.getMessage());
            }
        }

        System.out.println("[NoteService] 索引重建完成，共 " + count + " 个笔记");
        return count;
    }

    /**
     * 计算内容哈希
     */
    private String computeHash(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取仓库实例
     */
    public NoteRepository getRepository() {
        return repository;
    }

    /**
     * 获取文件存储实例
     */
    public NoteFileStorage getFileStorage() {
        return fileStorage;
    }
}

