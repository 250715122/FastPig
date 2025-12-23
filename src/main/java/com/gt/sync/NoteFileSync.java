package com.gt.sync;

import com.gt.NoteDto;
import com.gt.cloud.CloudFileInfo;
import com.gt.cloud.CloudStorageFactory;
import com.gt.cloud.CloudStorageProvider;
import com.gt.service.NoteService;
import com.gt.storage.NoteFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 笔记文件同步服务
 * 实现文件级增量同步
 * 
 * 同步策略：
 * - 上传：扫描本地 notes/ 目录，对比 lastSyncTime，上传变更的文件
 * - 下载：获取云端文件列表，对比本地，下载新增/更新的文件
 * - 冲突：比较 version 字段，高版本胜出，相同则比较 updatedAt
 */
public class NoteFileSync {

    private static final Logger logger = LoggerFactory.getLogger(NoteFileSync.class);
    private static NoteFileSync instance;

    private final NoteFileStorage fileStorage;
    private final CloudStorageProvider cloudProvider;
    private NoteService noteService;

    public NoteFileSync(NoteFileStorage fileStorage) {
        this.fileStorage = fileStorage;
        this.cloudProvider = CloudStorageFactory.getProvider();
    }

    public static synchronized NoteFileSync getInstance() {
        if (instance == null) {
            instance = new NoteFileSync(NoteFileStorage.getInstance());
        }
        return instance;
    }

    public void setNoteService(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * 上传同步到云端
     */
    public boolean syncToCloud() {
        if (!cloudProvider.isEnabled()) {
            logger.info("[NoteFileSync] 云存储未启用，跳过上传");
            return false;
        }

        logger.info("[NoteFileSync] 开始上传同步到云端...");
        long startTime = System.currentTimeMillis();
        boolean overallOk = true;

        try {
            Path notesDir = fileStorage.getNotesDir();
            SyncMetadata syncMeta = SyncMetadata.load(notesDir);
            normalizeSyncMeta(syncMeta);
            long lastSyncTime = syncMeta.getLastSyncTime();

            logger.info("[NoteFileSync] 上次同步时间: " + new Date(lastSyncTime));

            // 确保云端根目录存在
            boolean rootOk = cloudProvider.createDirectory("");
            if (!rootOk) {
                logger.error("[NoteFileSync] 创建云端根目录失败，终止上传");
                return false;
            }

            // 获取云端已存在的笔记列表（用于判断本地笔记是否需要上传）
            Set<String> cloudFolders = new HashSet<>();
            try {
                List<CloudFileInfo> cloudFiles = cloudProvider.listFiles("");
                for (CloudFileInfo f : cloudFiles) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) {
                        cloudFolders.add(f.getName());
                    }
                }
                logger.info("[NoteFileSync] 云端已有 {} 个笔记", cloudFolders.size());
            } catch (Exception e) {
                logger.warn("[NoteFileSync] 获取云端笔记列表失败，将上传所有本地笔记: {}", e.getMessage());
            }

            int uploadedCount = 0;
            int skippedCount = 0;
            int failedCount = 0;
            int conflictCount = 0;
            java.util.List<String> conflictList = new java.util.ArrayList<>();

            // 先统计需要上传的文件数
            java.util.List<Path> toUpload = new java.util.ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir)) {
                for (Path folderPath : stream) {
                    if (!Files.isDirectory(folderPath)) continue;
                    String folderName = folderPath.getFileName().toString();
                    if (folderName.startsWith(".")) continue;
                    
                    if (shouldUpload(folderPath, lastSyncTime, syncMeta, cloudFolders)) {
                        toUpload.add(folderPath);
                    } else {
                        skippedCount++;
                    }
                }
            }

            int total = toUpload.size();
            logger.info("[云同步上传] 需要上传: {} 个, 跳过: {} 个（未修改）", total, skippedCount);
            if (total > 0) {
                for (Path p : toUpload) {
                    logger.debug("  待上传: {}", p.getFileName().toString());
                }
            }

            // 上传文件（带进度显示和冲突检测）
            if (total > 0) {
                logger.debug("正在上传...");
            }
            
            for (int i = 0; i < toUpload.size(); i++) {
                Path folderPath = toUpload.get(i);
                String folderName = folderPath.getFileName().toString();
                System.out.print("  [" + (i + 1) + "/" + total + "] " + folderName + " ... ");
                logger.info("[NoteFileSync] 上传进度: " + (i + 1) + "/" + total);
                
                // 上传前检查云端版本，防止覆盖更新的数据
                if (checkUploadConflict(folderPath, folderName)) {
                    conflictCount++;
                    conflictList.add(folderName);
                    logger.warn("[NoteFileSync] 上传冲突（云端版本更高）: {}", folderName);
                    continue; // 跳过上传
                }
                
                if (uploadNoteFolder(folderPath)) {
                    uploadedCount++;
                    updateSyncMetadata(syncMeta, folderPath);
                    logger.debug("上传成功: {}", folderName);
                } else {
                    failedCount++;
                    overallOk = false;
                    logger.error("上传失败: {}", folderName);
                }
            }

            // 处理已删除的笔记：同步删除云端
            int deletedCount = syncDeletedNotesToCloud(notesDir, syncMeta);

            // 保存元数据：
            // - 无论整体是否成功，都保存已成功上传的条目，避免下次重复上传
            // - 只有整体成功时才更新 lastSyncTime
            if (overallOk) {
                syncMeta.setLastSyncTime(System.currentTimeMillis());
            }
            syncMeta.save(notesDir);

            // 上传 .sync_meta.json 到云端（用于跨设备版本比较）
            boolean metaOk = uploadSyncMetaToCloud(syncMeta);
            if (!metaOk) {
                overallOk = false;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("[NoteFileSync] 上传同步完成: 成功 {} 个, 失败 {} 个, 冲突 {} 个, 删除 {} 个, 跳过 {} 个, 耗时 {}ms", 
                uploadedCount, failedCount, conflictCount, deletedCount, skippedCount, elapsed);
            
            // 如果有冲突，提示用户
            if (conflictCount > 0) {
                logger.warn("检测到版本冲突的笔记（云端版本更高，已跳过上传）: {}", conflictList);
            }

            return overallOk;

        } catch (Exception e) {
            logger.error("[NoteFileSync] 上传同步失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 同步删除已标记为 deleted 的笔记到云端
     * @param notesDir 本地笔记目录
     * @param syncMeta 同步元数据
     * @return 成功删除的笔记数量
     */
    private int syncDeletedNotesToCloud(Path notesDir, SyncMetadata syncMeta) {
        int deletedCount = 0;
        java.util.List<Path> toDelete = new java.util.ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir)) {
            for (Path folderPath : stream) {
                if (!Files.isDirectory(folderPath)) continue;
                String folderName = folderPath.getFileName().toString();
                if (folderName.startsWith(".")) continue;
                
                // 检查该笔记是否被标记为已删除
                Path noteFile = folderPath.resolve("note.md");
                if (Files.exists(noteFile)) {
                    try {
                        String content = Files.readString(noteFile);
                        if (isNoteDeleted(content)) {
                            toDelete.add(folderPath);
                        }
                    } catch (IOException e) {
                        logger.warn("[NoteFileSync] 读取笔记文件失败: " + noteFile);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("[NoteFileSync] 遍历笔记目录失败: " + e.getMessage());
            return 0;
        }
        
        if (toDelete.isEmpty()) {
            return 0;
        }
        
        logger.info("[云同步删除] 需要删除: {} 个已删除的笔记", toDelete.size());
        
        for (Path folderPath : toDelete) {
            String folderName = folderPath.getFileName().toString();
            System.out.print("  删除 " + folderName + " ... ");
            
            try {
                // 1. 删除云端目录
                boolean cloudDeleted = cloudProvider.delete(folderName);
                if (cloudDeleted) {
                    logger.info("[NoteFileSync] 已删除云端笔记: " + folderName);
                }
                
                // 2. 删除本地目录（彻底删除）
                deleteLocalFolder(folderPath);
                
                // 3. 从元数据中移除
                syncMeta.getFiles().remove(folderName);
                
                deletedCount++;
                logger.debug("删除成功: {}", folderName);
                
            } catch (Exception e) {
                logger.error("[NoteFileSync] 删除笔记失败: {} - {}", folderName, e.getMessage());
            }
        }
        
        if (deletedCount > 0) {
            logger.info("[NoteFileSync] 已从云端删除 " + deletedCount + " 个笔记");
        }
        
        return deletedCount;
    }
    
    /**
     * 检查笔记内容是否标记为已删除
     */
    private boolean isNoteDeleted(String content) {
        if (content == null || !content.startsWith("---")) {
            return false;
        }
        
        int endIndex = content.indexOf("---", 3);
        if (endIndex == -1) {
            return false;
        }
        
        String frontMatter = content.substring(3, endIndex);
        for (String line : frontMatter.split("\n")) {
            line = line.trim();
            if (line.startsWith("deleted:")) {
                String value = line.substring(8).trim();
                return "true".equalsIgnoreCase(value);
            }
        }
        
        return false;
    }
    
    /**
     * 递归删除本地文件夹
     */
    private void deleteLocalFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteLocalFolder(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(folder);
    }

    /**
     * 从云端下载同步（无状态回调）
     */
    public boolean syncFromCloud() {
        return syncFromCloud(null);
    }

    /**
     * 从云端下载同步
     * 优先使用云端 .sync_meta.json 进行版本号快速比较
     * 使用 5 线程并发下载，每个任务间隔 0.3 秒
     * @param statusCallback 状态回调，用于更新 UI 状态栏
     */
    public boolean syncFromCloud(Consumer<String> statusCallback) {
        if (!cloudProvider.isEnabled()) {
            logger.info("[NoteFileSync] 云存储未启用，跳过下载");
            return false;
        }

        updateStatus(statusCallback, "正在检查云端文件...");
        logger.info("[NoteFileSync] 开始从云端下载同步...");
        long startTime = System.currentTimeMillis();

        try {
            Path notesDir = fileStorage.getNotesDir();
            SyncMetadata localMeta = SyncMetadata.load(notesDir);
            boolean metaChanged = normalizeSyncMeta(localMeta);

            // 尝试下载云端的 .sync_meta.json 进行快速版本比较
            SyncMetadata cloudMeta = downloadCloudMeta();
            boolean useVersionCompare = (cloudMeta != null && !cloudMeta.getFiles().isEmpty());

            // 获取云端文件夹列表
            List<CloudFileInfo> cloudFolders = cloudProvider.listFiles("");
            logger.info("[NoteFileSync] 云端笔记文件夹: " + cloudFolders.size() + " 个");

            // 先筛选出需要下载的文件夹
            List<DownloadTask> toDownload = new ArrayList<>();
            AtomicInteger skippedCount = new AtomicInteger(0);

            int totalFolders = (int) cloudFolders.stream().filter(f -> f.isDirectory() && !f.getName().startsWith(".")).count();
            int checkCount = 0;
            
            if (useVersionCompare) {
                logger.info("[NoteFileSync] 使用版本号快速比较模式...");
            } else {
                logger.info("[NoteFileSync] 开始检查 " + totalFolders + " 个文件夹...");
            }

            for (CloudFileInfo cloudFolder : cloudFolders) {
                if (!cloudFolder.isDirectory()) {
                    continue;
                }

                String folderName = cloudFolder.getName();
                if (folderName.startsWith(".")) {
                    continue;
                }

                checkCount++;
                Path localFolder = notesDir.resolve(folderName);

                boolean needDownload;
                if (useVersionCompare) {
                    // 快速版本比较：直接对比版本号
                    needDownload = shouldDownloadByVersion(folderName, localFolder, localMeta, cloudMeta);
                } else {
                    // 传统模式：逐个检查（慢）
                    if (checkCount % 10 == 0 || checkCount == totalFolders) {
                        logger.info("[NoteFileSync] 检查进度: " + checkCount + "/" + totalFolders);
                        updateStatus(statusCallback, "正在检查云端文件 " + checkCount + "/" + totalFolders + "...");
                    }
                    needDownload = shouldDownload(cloudFolder, localFolder, localMeta);
                }

                if (needDownload) {
                    toDownload.add(new DownloadTask(folderName, localFolder, cloudFolder));
                } else {
                    skippedCount.incrementAndGet();
                }
            }

            int totalToDownload = toDownload.size();
            logger.info("[NoteFileSync] 需要下载: " + totalToDownload + " 个, 跳过: " + skippedCount.get() + " 个");

            AtomicInteger downloadedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            AtomicInteger progressCount = new AtomicInteger(0);

            if (totalToDownload > 0) {
                updateStatus(statusCallback, "正在下载 0/" + totalToDownload + " 个文件...");
                
                // 使用 5 线程的线程池
                int threadCount = Math.min(5, totalToDownload);
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                List<Future<Boolean>> futures = new ArrayList<>();

                // 用于线程安全地更新同步元数据
                List<DownloadTask> completedTasks = Collections.synchronizedList(new ArrayList<>());

                logger.info("[NoteFileSync] 启动 " + threadCount + " 线程并发下载...");

                // 用于状态回调的 final 引用
                final Consumer<String> callback = statusCallback;
                final int total = totalToDownload;

                // 提交所有任务到线程池，每提交一个间隔 0.3 秒
                for (int i = 0; i < toDownload.size(); i++) {
                    final DownloadTask task = toDownload.get(i);

                    futures.add(executor.submit(() -> {
                        int current = progressCount.incrementAndGet();
                        logger.info("[NoteFileSync] 下载进度: " + current + "/" + total + " - " + task.folderName);
                        updateStatus(callback, "正在下载 " + current + "/" + total + " 个文件...");
                        
                        boolean success = downloadNoteFolder(task.folderName, task.localFolder);
                        if (success) {
                            downloadedCount.incrementAndGet();
                            completedTasks.add(task);
                        } else {
                            failedCount.incrementAndGet();
                        }
                        return success;
                    }));

                    // 每提交一个任务后延迟 0.3 秒，避免请求过于密集
                    if (i < toDownload.size() - 1) {
                        Thread.sleep(300);
                    }
                }

                // 等待所有任务完成
                executor.shutdown();
                boolean finished = executor.awaitTermination(60, TimeUnit.MINUTES);
                if (!finished) {
                    logger.error("[NoteFileSync] 下载超时，强制终止");
                    executor.shutdownNow();
                }

                // 批量更新同步元数据
                for (DownloadTask task : completedTasks) {
                    updateSyncMetadataFromCloud(localMeta, task.folderName, task.cloudInfo);
                }
            }

            // 重建索引
            if (downloadedCount.get() > 0 && noteService != null) {
                updateStatus(statusCallback, "正在重建索引...");
                logger.info("[NoteFileSync] 重建索引...");
                noteService.rebuildIndexFromFiles();
            }

            // 更新同步时间并保存
            localMeta.setLastSyncTime(System.currentTimeMillis());
            localMeta.save(notesDir);

            // 如果有下载，同时更新云端的 .sync_meta.json
            if (downloadedCount.get() > 0) {
                uploadSyncMetaToCloud(localMeta);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("[NoteFileSync] 下载同步完成: 成功 " + downloadedCount.get() + " 个, 失败 " + failedCount.get() + " 个, 跳过 " + skippedCount.get() + " 个, 耗时 " + elapsed + "ms");

            updateStatus(statusCallback, "云同步完成");
            return true;

        } catch (Exception e) {
            logger.error("[NoteFileSync] 下载同步失败: " + e.getMessage());
            e.printStackTrace();
            updateStatus(statusCallback, "云同步失败");
            return false;
        }
    }
    
    /**
     * 更新状态回调（线程安全）
     */
    private void updateStatus(Consumer<String> callback, String status) {
        if (callback != null) {
            callback.accept(status);
        }
    }

    /**
     * 下载任务封装类
     */
    private static class DownloadTask {
        final String folderName;
        final Path localFolder;
        final CloudFileInfo cloudInfo;

        DownloadTask(String folderName, Path localFolder, CloudFileInfo cloudInfo) {
            this.folderName = folderName;
            this.localFolder = localFolder;
            this.cloudInfo = cloudInfo;
        }
    }

    /**
     * 启动时同步（无状态回调）
     */
    public void syncOnStart() {
        syncOnStart(null);
    }

    /**
     * 启动时同步（智能决定上传还是下载）
     * 注意：首次启动时不自动上传大量文件，避免触发云端限流
     * @param statusCallback 状态回调，用于更新 UI 状态栏
     */
    public void syncOnStart(Consumer<String> statusCallback) {
        if (!cloudProvider.isEnabled()) {
            updateStatus(statusCallback, "云存储未启用");
            return;
        }

        updateStatus(statusCallback, "正在检查云端...");
        logger.info("[NoteFileSync] 启动时同步检查...");

        try {
            // 检查云端是否有数据
            List<CloudFileInfo> cloudFolders = cloudProvider.listFiles("");
            int cloudCount = (int) cloudFolders.stream().filter(f -> f.isDirectory() && !f.getName().startsWith(".")).count();

            // 检查本地是否有数据
            Path notesDir = fileStorage.getNotesDir();
            int localCount = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(notesDir)) {
                for (Path path : stream) {
                    if (Files.isDirectory(path) && !path.getFileName().toString().startsWith(".")) {
                        localCount++;
                    }
                }
            }

            logger.info("[NoteFileSync] 本地: " + localCount + " 个, 云端: " + cloudCount + " 个");

            if (cloudCount > 0) {
                // 云端有数据，下载同步
                syncFromCloud(statusCallback);
            } else if (localCount > 0 && cloudCount == 0) {
                // 本地有数据但云端为空（首次迁移场景）
                // 不自动上传，提示用户手动同步
                logger.info("[NoteFileSync] ⚠️ 检测到首次迁移场景（本地 " + localCount + " 个，云端 0 个）");
                logger.info("[NoteFileSync] ⚠️ 为避免触发云端限流，请手动按 Alt+S 上传");
                logger.info("[NoteFileSync] ⚠️ 上传过程中请耐心等待，每个文件间隔 200ms");
                updateStatus(statusCallback, "就绪（云端为空，按Alt+S上传）");
            } else {
                updateStatus(statusCallback, "就绪");
            }

        } catch (Exception e) {
            logger.error("[NoteFileSync] 启动同步失败: " + e.getMessage());
            updateStatus(statusCallback, "同步失败");
        }
    }

    /**
     * 上传前检查云端版本，防止覆盖更新的数据
     * 
     * @return true 如果检测到冲突（云端版本更高），false 如果无冲突（可以上传）
     */
    private boolean checkUploadConflict(Path folderPath, String folderName) {
        try {
            // 读取本地笔记
            NoteDto localNote = fileStorage.loadFromFile(folderPath);
            if (localNote == null) {
                return false; // 本地无法读取，无法判断冲突
            }
            
            // 尝试下载云端 note.md
            String remotePath = folderName + "/note.md";
            byte[] cloudData = cloudProvider.download(remotePath);
            
            if (cloudData == null) {
                return false; // 云端不存在，无冲突，可以上传
            }
            
            // 解析云端版本号
            String cloudContent = new String(cloudData, StandardCharsets.UTF_8);
            int cloudVersion = parseVersion(cloudContent);
            
            // 比较版本号
            if (cloudVersion > localNote.version) {
                logger.warn("[NoteFileSync] 检测到上传冲突: " + folderName + 
                    " (本地 v" + localNote.version + " < 云端 v" + cloudVersion + ")");
                return true; // 冲突！云端版本更高
            }
            
            return false; // 无冲突，可以上传
            
        } catch (Exception e) {
            logger.error("[NoteFileSync] 检查上传冲突失败: " + folderName + " - " + e.getMessage());
            return false; // 出错时允许上传（保守策略）
        }
    }

    /**
     * 判断是否需要上传
     * 
     * 比较逻辑：
     * 1. 如果云端不存在该笔记，需要上传（即使本地元数据有记录）
     * 2. 如果 SyncMetadata 中没有记录，说明是新文件，需要上传
     * 3. 如果文件修改时间或大小与记录不同，说明文件有变化，需要上传
     * 4. 额外检查 assets 目录是否有新增文件
     * 
     * @param folderPath 本地笔记文件夹路径
     * @param lastSyncTime 上次同步时间
     * @param syncMeta 同步元数据
     * @param cloudFolders 云端已存在的笔记文件夹名称集合
     */
    private boolean shouldUpload(Path folderPath, long lastSyncTime, SyncMetadata syncMeta, Set<String> cloudFolders) {
        try {
            Path noteFile = folderPath.resolve("note.md");
            if (!Files.exists(noteFile)) {
                return false;
            }

            // 检查元数据中是否有记录
            // 纯 key 目录，relativePath 直接使用目录名（key）
            String relativePath = folderPath.getFileName().toString();
            SyncMetadata.FileMetadata fm = syncMeta.getFiles().get(relativePath);

            // 检查云端是否存在该笔记
            // 如果云端不存在，即使本地元数据有记录也需要上传（可能是之前上传失败或云端被误删）
            if (!cloudFolders.isEmpty() && !cloudFolders.contains(relativePath)) {
                logger.info("[NoteFileSync] 云端不存在，需要上传: " + relativePath);
                return true;
            }

            if (fm == null) {
                // 没有记录，说明是新文件，需要上传
                logger.info("[NoteFileSync] 新笔记需要上传: " + relativePath);
                return true;
            }

            long fileModified = Files.getLastModifiedTime(noteFile).toMillis();
            long fileSize = Files.size(noteFile);

            // 元数据自愈：如果历史 size/mtime 为 0，则用当前实际值修复，避免全量上传
            if (fm.lastModified == 0 || fm.size == 0) {
                logger.warn("[NoteFileSync] 元数据自愈，强制上传: {} (旧: size={}, mtime={})", relativePath, fm.size, fm.lastModified);
                fm.lastModified = fileModified;
                fm.size = fileSize;
                syncMeta.updateFile(relativePath, fileModified, fileSize, "");
                return true; // 修复后强制本次上传，确保云端同步
            }

            // 比较文件修改时间和大小与 SyncMetadata 中记录的值
            // 只有当修改时间或大小变化时才需要上传
            if (fileModified != fm.lastModified || fileSize != fm.size) {
                logger.info("[NoteFileSync] 文件已修改: " + relativePath + 
                    " (时间: " + fm.lastModified + " -> " + fileModified + 
                    ", 大小: " + fm.size + " -> " + fileSize + ")");
                return true;
            }

            // 检查 assets 目录是否有新增或修改的文件
            Path assetsDir = folderPath.resolve("assets");
            if (Files.exists(assetsDir) && Files.isDirectory(assetsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(assetsDir)) {
                    for (Path assetFile : stream) {
                        if (Files.isRegularFile(assetFile)) {
                            long assetModified = Files.getLastModifiedTime(assetFile).toMillis();
                            // 如果资源文件修改时间晚于上次记录的同步时间，需要上传
                            if (assetModified > fm.lastModified) {
                                logger.info("[NoteFileSync] 资源文件有更新: " + assetFile.getFileName());
                                return true;
                            }
                        }
                    }
                }
            }

            return false;

        } catch (IOException e) {
            return true; // 出错时尝试上传
        }
    }

    /**
     * 基于版本号快速判断是否需要下载
     * 只比较版本号，不调用 API，速度快
     */
    private boolean shouldDownloadByVersion(String folderName, Path localFolder, 
            SyncMetadata localMeta, SyncMetadata cloudMeta) {
        
        // 本地不存在，需要下载
        if (!Files.exists(localFolder)) {
            logger.info("[NoteFileSync] 新笔记（本地不存在）: " + folderName);
            return true;
        }

        // 获取云端版本号
        SyncMetadata.FileMetadata cloudFm = cloudMeta.getFiles().get(folderName);
        if (cloudFm == null) {
            // 云端 meta 中没有记录，但云端文件夹存在
            // 如果本地不存在，需要下载
            if (!Files.exists(localFolder)) {
                logger.info("[NoteFileSync] 新笔记（meta不完整但本地不存在）: " + folderName);
                return true;
            }
            // 本地存在但 meta 不完整，跳过（保守策略）
            return false;
        }
        int cloudVersion = cloudFm.version;

        // 获取本地版本号
        SyncMetadata.FileMetadata localFm = localMeta.getFiles().get(folderName);
        int localVersion = 0;
        if (localFm != null && localFm.version > 0) {
            localVersion = localFm.version;
        } else {
            // 本地 meta 没有版本号，从文件读取
            Path noteFile = localFolder.resolve("note.md");
            if (Files.exists(noteFile)) {
                localVersion = parseVersionFromFile(noteFile);
            }
        }

        // 比较版本号
        if (cloudVersion > localVersion) {
            logger.info("[NoteFileSync] 云端版本更高: " + folderName + 
                " (本地 v" + localVersion + " < 云端 v" + cloudVersion + ")");
            return true;
        }

        return false;
    }

    /**
     * 判断是否需要下载（传统模式，需要调用 API）
     */
    private boolean shouldDownload(CloudFileInfo cloudFolder, Path localFolder, SyncMetadata syncMeta) {
        String folderName = cloudFolder.getName();
        
        // 本地不存在，需要下载
        if (!Files.exists(localFolder)) {
            logger.info("[NoteFileSync] 新笔记（本地不存在）: " + folderName);
            return true;
        }

        try {
            Path localNoteFile = localFolder.resolve("note.md");
            if (!Files.exists(localNoteFile)) {
                logger.info("[NoteFileSync] 新笔记（note.md不存在）: " + folderName);
                return true;
            }

            long localModified = Files.getLastModifiedTime(localNoteFile).toMillis();
            long localSize = Files.size(localNoteFile);

            // 获取云端 note.md 的精确信息，仅用于存在性与大小参考；mtime 不参与更新判定（坚果云 mtime=上传时间）
            CloudFileInfo noteInfo = cloudProvider.getFileInfo(folderName + "/note.md");
            if (noteInfo == null) {
                // 无法获取信息，记录本地状态并跳过，等待下次
                syncMeta.updateFileWithCloudTime(folderName, localModified, localSize, localModified);
                return false;
            }

            long cloudSize = noteInfo.getSize();

            // 检查同步元数据：是否已经同步过这个文件夹（key 为键）
            SyncMetadata.FileMetadata fm = syncMeta.getFiles().get(folderName);
            
            if (fm == null) {
                // 首次：仅当大小不同才下载；mtime 不作为判定依据
                if (cloudSize > 0 && cloudSize != localSize) {
                    logger.info("[NoteFileSync] 首次记录且大小不同，需下载: " + folderName);
                    return true;
                }
                // 记录并跳过
                syncMeta.updateFileWithCloudTime(folderName, localModified, localSize, localModified);
                return false;
            }

            // 元数据缺损自愈：历史 size/mtime 为 0 时直接修复并强制下载
            if ((fm.size == 0 && localSize > 0) || fm.cloudModified == 0) {
                logger.warn("[NoteFileSync] 元数据自愈，强制下载: {} (旧: size={}, cloudModified={})", folderName, fm.size, fm.cloudModified);
                long fixedCloud = (fm.cloudModified == 0) ? localModified : fm.cloudModified;
                fm.size = localSize;
                fm.cloudModified = fixedCloud;
                syncMeta.updateFileWithCloudTime(folderName, localModified, localSize, fixedCloud);
                return true; // 修复后强制本次下载，确保本地同步
            }

            // 云端大小与记录相同 → 云端没变，直接跳过
            if (cloudSize > 0 && cloudSize == fm.size) {
                return false;  // 不需要下载
            }

            // 云端大小变了，需要版本判定
            logger.info("[NoteFileSync] 同步版本判定: " + folderName + " (云端 size: " + cloudSize + ", 记录 size: " + fm.size + ")");
            return resolveConflictIfNeeded(localFolder, folderName);

        } catch (Exception e) {
            return true; // 出错时尝试下载
        }
    }
    
    /**
     * 检查是否需要下载（避免不必要的下载）
     */
    private boolean resolveConflictIfNeeded(Path localFolder, String cloudFolderName) {
        try {
            // 读取本地笔记
            NoteDto localNote = fileStorage.loadFromFile(localFolder);
            if (localNote == null) {
                return true; // 本地无法读取，下载云端版本
            }

            // 下载云端笔记内容来比较版本
            String remotePath = cloudFolderName + "/note.md";
            byte[] cloudData = cloudProvider.download(remotePath);
            if (cloudData == null) {
                return false; // 云端无法读取，保持本地版本
            }

            // 临时解析云端笔记
            String cloudContent = new String(cloudData, StandardCharsets.UTF_8);
            int cloudVersion = parseVersion(cloudContent);
            long cloudUpdatedAt = parseUpdatedAt(cloudContent);

            // 版本号高的胜出
            if (cloudVersion > localNote.version) {
                logger.info("[NoteFileSync] 云端版本更高: " + cloudFolderName + " (本地 v" + localNote.version + " < 云端 v" + cloudVersion + ")");
                return true; // 下载云端版本
            } else if (cloudVersion < localNote.version) {
                logger.info("[NoteFileSync] 本地版本更高: " + cloudFolderName + " (本地 v" + localNote.version + " > 云端 v" + cloudVersion + ")");
                return false; // 保持本地版本
            } else {
                // 版本相同，比较更新时间
                if (cloudUpdatedAt > localNote.updatedAt) {
                    logger.info("[NoteFileSync] 版本相同但云端更新: " + cloudFolderName);
                    return true;
                }
                // 版本相同且本地更新或相同，跳过下载
                return false;
            }

        } catch (Exception e) {
            logger.error("[NoteFileSync] 冲突检测失败: " + e.getMessage());
            return true; // 出错时下载云端版本
        }
    }

    /**
     * 从内容中解析版本号
     */
    private int parseVersion(String content) {
        int idx = content.indexOf("version:");
        if (idx < 0) return 1;
        int endIdx = content.indexOf("\n", idx);
        if (endIdx < 0) endIdx = content.length();
        String value = content.substring(idx + 8, endIdx).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * 从内容中解析更新时间
     */
    private long parseUpdatedAt(String content) {
        int idx = content.indexOf("updatedAt:");
        if (idx < 0) return 0;
        int endIdx = content.indexOf("\n", idx);
        if (endIdx < 0) endIdx = content.length();
        String value = content.substring(idx + 10, endIdx).trim();
        try {
            return java.time.Instant.parse(value).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 上传笔记文件夹
     */
    private boolean uploadNoteFolder(Path folderPath) {
        String folderName = folderPath.getFileName().toString();
        logger.info("[NoteFileSync] 上传文件夹: " + folderName);

        try {
            // 确保云端目录存在
            cloudProvider.createDirectory(folderName);

            // 上传文件夹中的所有文件
            try (Stream<Path> files = Files.walk(folderPath)) {
                List<Path> fileList = files.filter(Files::isRegularFile).collect(Collectors.toList());

                for (Path file : fileList) {
                    String relativePath = folderPath.relativize(file).toString().replace("\\", "/");
                    String remotePath = folderName + "/" + relativePath;

                    byte[] data = Files.readAllBytes(file);
                    
                    if (!cloudProvider.upload(remotePath, data)) {
                        logger.error("[NoteFileSync] 上传失败: {}", remotePath);
                        return false;
                    }
                }
            }

            return true;

        } catch (IOException e) {
            logger.error("[NoteFileSync] 上传文件夹IO异常: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("[NoteFileSync] 上传文件夹异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 下载笔记文件夹（旧方法，保留兼容）
     */
    private boolean downloadNoteFolder(String folderName, Path localFolder) {
        logger.info("[NoteFileSync] 下载文件夹: " + folderName);

        try {
            // 确保本地目录存在
            if (!Files.exists(localFolder)) {
                Files.createDirectories(localFolder);
            }

            // 获取云端文件列表
            List<CloudFileInfo> cloudFiles = cloudProvider.listFiles(folderName);

            for (CloudFileInfo cloudFile : cloudFiles) {
                if (cloudFile.isDirectory()) {
                    // 递归处理子目录（如 assets）
                    String subDirName = cloudFile.getName();
                    Path localSubDir = localFolder.resolve(subDirName);
                    if (!Files.exists(localSubDir)) {
                        Files.createDirectories(localSubDir);
                    }

                    List<CloudFileInfo> subFiles = cloudProvider.listFiles(folderName + "/" + subDirName);
                    for (CloudFileInfo subFile : subFiles) {
                        if (!subFile.isDirectory()) {
                            downloadFile(folderName + "/" + subDirName + "/" + subFile.getName(),
                                    localSubDir.resolve(subFile.getName()));
                        }
                    }
                } else {
                    // 下载文件
                    downloadFile(folderName + "/" + cloudFile.getName(),
                            localFolder.resolve(cloudFile.getName()));
                }
            }

            return true;

        } catch (IOException e) {
            logger.error("[NoteFileSync] 下载文件夹失败: " + e.getMessage());
            return false;
        }
    }


    /**
     * 下载单个文件
     */
    private void downloadFile(String remotePath, Path localPath) throws IOException {
        byte[] data = cloudProvider.download(remotePath);
        if (data != null) {
            // 确保父目录存在
            Path parent = localPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(localPath, data);
            logger.info("[NoteFileSync] 已下载: " + localPath.getFileName());
        }
    }

    /**
     * 更新同步元数据（上传后）
     */
    private void updateSyncMetadata(SyncMetadata syncMeta, Path folderPath) {
        try {
            String relativePath = fileStorage.getNotesDir().relativize(folderPath).toString().replace("\\", "/");
            Path noteFile = folderPath.resolve("note.md");

            if (Files.exists(noteFile)) {
                long modified = Files.getLastModifiedTime(noteFile).toMillis();
                long size = Files.size(noteFile);
                
                // 读取 version（从 note.md front matter）
                int version = parseVersionFromFile(noteFile);
                
                syncMeta.updateFileWithVersion(relativePath, modified, size, version);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * 从 note.md 文件读取 version
     */
    private int parseVersionFromFile(Path noteFile) {
        try {
            String content = Files.readString(noteFile, StandardCharsets.UTF_8);
            return parseVersion(content);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 更新同步元数据（下载后）
     * 必须记录本地文件的实际修改时间和大小，以便下次上传时正确比较
     */
    private void updateSyncMetadataFromCloud(SyncMetadata syncMeta, String folderName, CloudFileInfo cloudFolder) {
        try {
            Path localFolder = fileStorage.getNotesDir().resolve(folderName);
            Path noteFile = localFolder.resolve("note.md");
            
            if (Files.exists(noteFile)) {
                // 读取本地文件的实际修改时间和大小
                long localModified = Files.getLastModifiedTime(noteFile).toMillis();
                long localSize = Files.size(noteFile);
                
                // 读取 version
                int version = parseVersionFromFile(noteFile);

                // 更新元数据（包含版本号）
                SyncMetadata.FileMetadata fm = syncMeta.getFiles().computeIfAbsent(folderName, k -> new SyncMetadata.FileMetadata());
                fm.path = folderName;
                fm.lastModified = localModified;
                fm.size = localSize;
                fm.version = version;
                fm.cloudModified = localModified; // 下载后本地时间即为云端时间

                logger.info("[NoteFileSync] 已记录同步信息: " + folderName + 
                    " (大小: " + localSize + ", 版本: v" + version + ")");
            }
        } catch (IOException e) {
            logger.error("[NoteFileSync] 更新元数据失败: " + folderName + " - " + e.getMessage());
        }
    }

    /**
     * 上传 .sync_meta.json 到云端
     */
    private boolean uploadSyncMetaToCloud(SyncMetadata syncMeta) {
        try {
            String json = syncMeta.toJson();
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            if (cloudProvider.upload(".sync_meta.json", data)) {
                logger.info("[NoteFileSync] 已上传 .sync_meta.json 到云端 (" + data.length + " bytes)");
                return true;
            }
            logger.error("[NoteFileSync] 上传 .sync_meta.json 失败（upload 返回 false）");
            return false;
        } catch (Exception e) {
            logger.error("[NoteFileSync] 上传 .sync_meta.json 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从云端下载 .sync_meta.json
     * @return 云端的元数据，如果不存在或失败返回 null
     */
    private SyncMetadata downloadCloudMeta() {
        try {
            byte[] data = cloudProvider.download(".sync_meta.json");
            if (data == null || data.length == 0) {
                logger.info("[NoteFileSync] 云端没有 .sync_meta.json，将使用传统方式检查");
                return null;
            }
            String json = new String(data, StandardCharsets.UTF_8);
            SyncMetadata cloudMeta = SyncMetadata.parseFromJson(json);
            if (cloudMeta != null) {
                logger.info("[NoteFileSync] 已下载云端 .sync_meta.json (" + data.length + " bytes, " + cloudMeta.getFiles().size() + " 个文件记录)");
            }
            return cloudMeta;
        } catch (Exception e) {
            logger.error("[NoteFileSync] 下载云端 .sync_meta.json 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 兼容旧 key--uuid 的元数据：将 key--uuid 归并为 key
     */
    private boolean normalizeSyncMeta(SyncMetadata meta) {
        Map<String, SyncMetadata.FileMetadata> files = meta.getFiles();
        Map<String, SyncMetadata.FileMetadata> normalized = new HashMap<>();
        boolean changed = false;

        for (Map.Entry<String, SyncMetadata.FileMetadata> e : files.entrySet()) {
            String key = e.getKey();
            SyncMetadata.FileMetadata fm = e.getValue();

            // 纯 key 已是新格式
            if (!key.contains("--")) {
                normalized.merge(key, fm, this::mergeMeta);
                continue;
            }

            // 旧格式 key--uuid，截断为 key
            String pureKey = key.substring(0, key.indexOf("--"));
            normalized.merge(pureKey, fm, this::mergeMeta);
            changed = true;
        }

        if (changed) {
            files.clear();
            files.putAll(normalized);
            logger.info("[NoteFileSync] 已规范化同步元数据 key (去除 --uuid)，条目数: " + files.size());
        }
        return changed;
    }

    /**
     * 合并元数据时，选择较新的 cloudModified/lastModified，size 取较大
     */
    private SyncMetadata.FileMetadata mergeMeta(SyncMetadata.FileMetadata a, SyncMetadata.FileMetadata b) {
        SyncMetadata.FileMetadata r = new SyncMetadata.FileMetadata();
        r.path = a.path != null ? a.path : b.path;
        r.lastModified = Math.max(a.lastModified, b.lastModified);
        r.cloudModified = Math.max(a.cloudModified, b.cloudModified);
        r.size = Math.max(a.size, b.size);
        r.hash = (a.hash != null && !a.hash.isEmpty()) ? a.hash : b.hash;
        return r;
    }

    public boolean isEnabled() {
        return cloudProvider.isEnabled();
    }
}

