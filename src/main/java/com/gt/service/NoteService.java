package com.gt.service;

import com.gt.AppConfig;
import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.config.ConfigKeys;
import com.gt.crypto.NoteEncryptionService;
import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.id.BlockIdAssigner;
import com.gt.kb.repo.BlockCatalog;
import com.gt.kb.repo.BlockRepository;
import com.gt.kb.repo.SingleFileBlockRepository;
import com.gt.storage.NoteFileStorage;
import com.gt.vector.VectorSearchManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashSet;
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
public class NoteService implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(NoteService.class);
    private static NoteService instance;

    private final NoteRepository repository;
    private final NoteFileStorage fileStorage;
    private final SingleFileBlockRepository blockRepository;

    public NoteService(NoteRepository repository, NoteFileStorage fileStorage) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.blockRepository = new SingleFileBlockRepository(fileStorage, new BlockCatalog(repository.getDbPath()));
    }

    public static synchronized NoteService getInstance(NoteRepository repository) {
        if (instance == null) {
            instance = new NoteService(repository, NoteFileStorage.getInstance());
        }
        return instance;
    }

    /** 块仓库，供 AI 档位检索回源取完整块正文。 */
    public BlockRepository blockRepository() {
        return blockRepository;
    }

    /**
     * 释放块目录持有的数据库连接。退出前必须调用：
     * Windows 上未释放的连接会一直锁住 fastpig.db，备份/替换库文件都会失败。
     */
    public void close() {
        blockRepository.close();
    }

    /**
     * 保存笔记
     * 同时写入文件和更新索引
     *
     * @return 本次补块 ID 的结果，调用方（编辑器）据此把新增的 ID 行插回编辑区
     */
    public BlockIdAssigner.Result save(NoteDto note) {
        if (note == null || note.id == null) {
            throw new IllegalArgumentException("笔记或笔记ID不能为空");
        }

        // 自动递增版本号
        autoIncrementVersion(note);

        // 补 ID 必须在算哈希之前：哈希要对应真正落盘的正文
        BlockIdAssigner.Result ids = assignBlockIds(note);

        // 内容哈希按明文计算：密文每次保存都换新 IV，用密文算哈希会让同步永远认为有变更
        note.contentHash = computeHash(note.bodyMd);

        // 私密笔记：落盘前把正文换成密文，写完再还原成明文供编辑器继续使用
        String plaintextBody = null;
        if (note.encrypted) {
            try {
                plaintextBody = NoteEncryptionService.sealForSave(note);
            } catch (Exception e) {
                throw new RuntimeException("加密笔记失败: " + e.getMessage(), e);
            }
        }

        try {
            // 保存到文件
            fileStorage.saveToFile(note);

            // 更新文件夹路径
            Path folderPath = fileStorage.getNoteFolderPath(note);
            note.folderPath = folderPath.toString();

            // 更新索引（不存储正文，只存储元数据）
            repository.saveIndex(note);
        } finally {
            // 无论成败都要把明文放回内存，否则编辑器里会突然变成一串 Base64
            if (plaintextBody != null) {
                note.bodyMd = plaintextBody;
            }
        }

        if (note.encrypted) {
            // 向量索引里的标题和摘要是明文，私密笔记必须整体移出
            VectorSearchManager.getInstance().removeNoteIndex(note.key);
            // 清掉历史迁移可能残留在库里的明文正文
            repository.clearBodyMd(note.id);
        } else {
            // 更新向量索引（异步），块级差量：只重算内容变化的块
            VectorSearchManager.getInstance().indexNote(note);
        }

        // 块目录是派生索引，读的是刚落盘的文件：私密笔记切不出块，等同清空它的目录行。
        // 文件和主索引都已写成功，这里失败只该告警，不能把保存报成失败。
        try {
            blockRepository.refreshNote(note.key);
        } catch (RuntimeException e) {
            logger.warn("[NoteService] 块目录刷新失败: {} - {}", note.key, e.getMessage());
        }

        logger.info("[NoteService] 已保存笔记: " + note.key + " (id=" + note.id + ", version=" + note.version
                + (note.encrypted ? ", encrypted" : "") + ")");
        return ids;
    }

    /**
     * 落盘前给无 ID 的知识块补 ID，并换掉重复/被别的笔记占用的 ID。
     *
     * 私密笔记跳过：正文马上要换成密文，ID 行写进去也切不出块。
     * 补 ID 只是派生信息，失败或自检不通过都按原文保存，绝不能因此丢掉用户正文。
     */
    private BlockIdAssigner.Result assignBlockIds(NoteDto note) {
        String body = note.bodyMd;
        BlockIdAssigner.Result unchanged = new BlockIdAssigner.Result(body, 0, 0, List.of());
        if (note.encrypted || !AppConfig.getInstance().getBoolean(ConfigKeys.KB_ID_AUTO_ASSIGN)) {
            return unchanged;
        }
        try {
            BlockIdAssigner.Result result = BlockIdAssigner.assign(note.key, note.desc, body,
                    ChunkProfile.forNote(note.noteType, note.layer),
                    id -> blockRepository.isIdTakenByOtherNote(id, note.key));
            if (result.changed() && !BlockIdAssigner.preservesText(body, result.body())) {
                logger.error("[NoteService] 补块 ID 会改动正文，已放弃: {}", note.key);
                return unchanged;
            }
            note.bodyMd = result.body();
            return result;
        } catch (RuntimeException e) {
            logger.warn("[NoteService] 补块 ID 失败，按原文保存: {} - {}", note.key, e.getMessage());
            return unchanged;
        }
    }

    /**
     * 主密码变更后，把所有私密笔记的 DEK 用新主密码重新包裹。
     *
     * 分两阶段：先把每篇都用旧主密码解开并全部校验通过，再统一落盘。
     * 不这么做的话，中途某篇解不开就会留下"一半用新主密码、一半用旧主密码"的
     * 混合状态，而旧主密码此时已经被覆盖，那半篇笔记的恢复通道就永久失效了。
     *
     * @return 重新包裹的笔记数量
     * @throws IllegalStateException 有笔记无法用旧主密码解开时抛出，此时不改动任何文件
     */
    public int rewrapAllWithNewMaster(char[] oldMaster, char[] newMaster) {
        List<NoteDto> encryptedNotes = repository.findAll().stream()
                .filter(n -> n.encrypted)
                .collect(Collectors.toList());

        if (encryptedNotes.isEmpty()) {
            return 0;
        }

        // 阶段一：全部解开，任何一篇失败就整体放弃
        List<NoteDto> unlocked = new java.util.ArrayList<>();
        List<String> failed = new java.util.ArrayList<>();
        try {
            for (NoteDto stub : encryptedNotes) {
                NoteDto note = load(stub.id);
                if (note == null || !note.encrypted) {
                    continue;
                }
                try {
                    NoteEncryptionService.unlock(note, oldMaster, true);
                    unlocked.add(note);
                } catch (Exception e) {
                    logger.error("[NoteService] 主密码解不开笔记: {} - {}", note.key, e.getMessage());
                    failed.add(note.key);
                }
            }

            if (!failed.isEmpty()) {
                throw new IllegalStateException(
                        "以下笔记无法用当前主密码解开，主密码未做任何修改：" + String.join("、", failed));
            }

            // 阶段二：重新包裹并落盘。中途失败就把已写的几篇退回旧主密码，
            // 避免它们的恢复通道指向一个即将被丢弃的密码
            List<NoteDto> done = new java.util.ArrayList<>();
            try {
                for (NoteDto note : unlocked) {
                    NoteEncryptionService.rewrapWithMaster(note, newMaster);
                    save(note);
                    done.add(note);
                }
            } catch (Exception e) {
                logger.error("[NoteService] 重新包裹中途失败，开始回滚已写入的 {} 篇", done.size(), e);
                for (NoteDto note : done) {
                    try {
                        NoteEncryptionService.rewrapWithMaster(note, oldMaster);
                        save(note);
                    } catch (Exception rollbackError) {
                        logger.error("[NoteService] 回滚失败: {} - 这篇只能用笔记密码打开",
                                note.key, rollbackError);
                    }
                }
                throw new IllegalStateException(
                        "重新包裹失败，已尝试回滚到原主密码，主密码未做修改：" + e.getMessage());
            }

            logger.info("[NoteService] 已用新主密码重新包裹 {} 篇私密笔记", done.size());
            return done.size();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("重新包裹私密笔记失败: " + e.getMessage(), e);
        } finally {
            // 明文正文和 DEK 不留在内存里
            for (NoteDto note : unlocked) {
                NoteEncryptionService.lock(note);
                note.bodyMd = null;
            }
        }
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
            copyCryptoFields(fileData, indexData);
        }

        return indexData;
    }

    /**
     * 加密参数只存在于文件的 front matter，数据库索引里没有。
     * 合并时必须一并带过来，否则解锁时拿不到盐和包裹密钥。
     */
    private void copyCryptoFields(NoteDto from, NoteDto to) {
        to.encrypted = from.encrypted;
        to.cipherIv = from.cipherIv;
        to.pwdSalt = from.pwdSalt;
        to.pwdIv = from.pwdIv;
        to.pwdWrappedDek = from.pwdWrappedDek;
        to.masterSalt = from.masterSalt;
        to.masterIv = from.masterIv;
        to.masterWrappedDek = from.masterWrappedDek;
        to.assets = from.assets;
        to.locked = from.encrypted;
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
            copyCryptoFields(fileData, indexData);
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
     * 
     * 注意：只清理本地文件，不删除云端文件！
     * 云端删除只在用户显式删除笔记时触发（通过 syncDeletedNotesToCloud）
     * 这样可以避免因同步失败导致误删云端数据
     */
    public int cleanupOrphanedNotes() {
        logger.info("[NoteService] 开始清理孤立笔记...");
        
        Path notesDir = fileStorage.getNotesDir();
        if (!Files.exists(notesDir)) {
            logger.info("[NoteService] notes 目录不存在，跳过清理");
            return 0;
        }
        
        int deletedCount = 0;
        
        try {
            // 获取数据库中所有笔记的 key。必须用 findAllKeys：
            // findAllCommandsAndDescriptions 为生成 all 聚合而过滤掉 all 本身，
            // 用它会把 all 每次都判成孤立笔记删掉，再被云同步下载回旧版本。
            Set<String> dbKeys = new HashSet<>(repository.findAllKeys());
            
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
                    
                    // 跳过知识库内部目录（以 _ 开头，如 _kb 内容配置）：
                    // 它们不在数据库里，按孤立笔记处理会被直接删掉
                    if (folderName.startsWith("_")) {
                        continue;
                    }
                    
                    // 检查数据库中是否存在
                    if (!dbKeys.contains(folderName)) {
                        logger.info("[NoteService] 发现孤立笔记: {}", folderName);
                        
                        try {
                            // 只删除本地文件，不删除云端
                            // 云端删除只在用户显式删除笔记时触发
                            deleteFolder(folder);
                            deletedCount++;
                            logger.info("[NoteService] 已删除本地孤立笔记: {}", folderName);
                        } catch (Exception e) {
                            logger.error("[NoteService] 删除孤立笔记失败: {} - {}", folderName, e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("[NoteService] 清理孤立笔记失败: {}", e.getMessage(), e);
        }
        
        logger.info("[NoteService] 清理完成，删除了 {} 个本地孤立笔记", deletedCount);
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

