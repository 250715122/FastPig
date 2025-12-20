package com.gt.service;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.cloud.CloudStorageFactory;
import com.gt.cloud.CloudStorageProvider;
import com.gt.storage.NoteFileStorage;
import com.gt.vector.VectorSearchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private static final Logger logger = LoggerFactory.getLogger(NoteService.class);
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

        // 自动递增版本号
        autoIncrementVersion(note);

        // 计算内容哈希
        note.contentHash = computeHash(note.bodyMd);

        // 保存到文件
        fileStorage.saveToFile(note);

        // 更新文件夹路径
        Path folderPath = fileStorage.getNoteFolderPath(note);
        note.folderPath = folderPath.toString();

        // 更新索引（不存储正文，只存储元数据）
        repository.saveIndex(note);

        // 更新向量索引（异步），使用描述而非标题
        VectorSearchManager.getInstance().indexNote(note.key, note.desc, note.bodyMd);

        logger.info("[NoteService] 已保存笔记: " + note.key + " (id=" + note.id + ", version=" + note.version + ")");
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
            
            // 移除向量索引
            VectorSearchManager.getInstance().removeNoteIndex(note.key);
        }

        logger.info("[NoteService] 已删除笔记: " + id);
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

        logger.info("[NoteService] 已恢复笔记: " + key);
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
        logger.info("[NoteService] 开始从文件重建索引...");
        
        List<NoteDto> notes = fileStorage.scanAllNotes();
        int count = 0;

        for (NoteDto note : notes) {
            try {
                note.contentHash = computeHash(note.bodyMd);
                note.folderPath = fileStorage.getNoteFolderPath(note).toString();
                repository.saveIndex(note);
                count++;
            } catch (Exception e) {
                logger.error("[NoteService] 索引笔记失败: " + note.id + " - " + e.getMessage());
            }
        }

        logger.info("[NoteService] 索引重建完成，共 " + count + " 个笔记");
        return count;
    }

    /**
     * 自动递增版本号
     * 如果是新笔记，从 version=1 开始
     * 如果是已存在的笔记，在旧版本号基础上 +1
     */
    private void autoIncrementVersion(NoteDto note) {
        try {
            // 尝试读取旧笔记
            NoteDto oldNote = repository.findById(note.id);
            
            if (oldNote == null || oldNote.version <= 0) {
                // 新笔记或旧笔记无版本号，从 1 开始
                if (note.version <= 0) {
                    note.version = 1;
                }
                // 如果用户手动设置了版本号（>0），保留用户设置
            } else {
                // 已存在的笔记，自动递增
                note.version = oldNote.version + 1;
            }
            
            logger.debug("[NoteService] 版本号: " + note.key + " → v" + note.version);
            
        } catch (Exception e) {
            // 出错时保持原版本号或设为 1
            if (note.version <= 0) {
                note.version = 1;
            }
            logger.warn("[NoteService] 版本号递增失败，使用 v" + note.version + ": " + e.getMessage());
        }
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

    /**
     * 清理孤立笔记
     * 删除文件系统中存在但数据库中不存在的笔记文件夹
     * 同时删除云端对应的文件夹
     */
    public int cleanupOrphanedNotes() {
        logger.info("[NoteService] 开始清理孤立笔记...");
        
        Path notesDir = fileStorage.getNotesDir();
        if (!Files.exists(notesDir)) {
            logger.info("[NoteService] notes 目录不存在，跳过清理");
            return 0;
        }
        
        int deletedCount = 0;
        
        // 获取云存储提供者
        CloudStorageProvider cloudProvider = CloudStorageFactory.getProvider();
        boolean cloudEnabled = cloudProvider != null && cloudProvider.isEnabled();
        
        try {
            // 获取数据库中所有笔记的 key
            List<NoteDto> allNotes = repository.findAllCommandsAndDescriptions();
            Set<String> dbKeys = allNotes.stream()
                .map(n -> n.key)
                .filter(k -> k != null)
                .collect(Collectors.toSet());
            
            logger.info("[NoteService] 数据库中有 {} 个笔记", dbKeys.size());
            
            // 扫描 notes 目录下的所有文件夹
            try (var stream = Files.list(notesDir)) {
                List<Path> folders = stream.filter(Files::isDirectory).toList();
                
                for (Path folder : folders) {
                    String folderName = folder.getFileName().toString();
                    
                    // 跳过隐藏文件夹（以 . 开头）
                    if (folderName.startsWith(".")) {
                        continue;
                    }
                    
                    // 检查数据库中是否存在
                    if (!dbKeys.contains(folderName)) {
                        logger.info("[NoteService] 发现孤立笔记: {}", folderName);
                        
                        try {
                            // 1. 先删除云端（如果启用了云同步）
                            if (cloudEnabled) {
                                boolean cloudDeleted = cloudProvider.delete(folderName);
                                if (cloudDeleted) {
                                    logger.info("[NoteService] 已从云端删除孤立笔记: {}", folderName);
                                } else {
                                    logger.warn("[NoteService] 云端删除失败，继续删除本地: {}", folderName);
                                }
                            }
                            
                            // 2. 再删除本地
                            deleteFolder(folder);
                            deletedCount++;
                            logger.info("[NoteService] 已删除孤立笔记: {}", folderName);
                        } catch (Exception e) {
                            logger.error("[NoteService] 删除孤立笔记失败: {} - {}", folderName, e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("[NoteService] 清理孤立笔记失败: {}", e.getMessage(), e);
        }
        
        logger.info("[NoteService] 清理完成，删除了 {} 个孤立笔记", deletedCount);
        return deletedCount;
    }
    
    /**
     * 递归删除文件夹
     */
    private void deleteFolder(Path folder) throws Exception {
        Files.walk(folder)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

}

