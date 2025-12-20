package com.gt;

import com.gt.sync.NoteFileSync;
import com.gt.service.NoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 数据同步服务
 * 
 * 改造后使用文件级增量同步：
 * - 笔记存储为独立的 Markdown 文件夹
 * - 只同步 notes/ 目录，不同步 SQLite 数据库
 * - 支持增量上传/下载
 * 
 * 保留旧的数据库同步作为向后兼容（可通过配置开关）
 */
public class DbSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DbSyncService.class);
    private static volatile DbSyncService INSTANCE;

    public static DbSyncService getInstance() {
        if (INSTANCE == null) {
            synchronized (DbSyncService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DbSyncService();
                }
            }
        }
        return INSTANCE;
    }

    private final Path localDb;
    private final NoteFileSync fileSync;
    private final boolean useFileSync;  // 是否使用文件同步（新模式）
    private NoteService noteService;

    private DbSyncService() {
        this.localDb = Paths.get(System.getProperty("user.dir"), "fastpig.db");
        this.fileSync = NoteFileSync.getInstance();
        
        // 默认使用文件同步模式
        this.useFileSync = true;
        
        if (useFileSync) {
            logger.info("[DbSync] 使用文件级增量同步模式");
        } else {
            logger.info("[DbSync] 使用整库同步模式（旧模式）");
        }
    }

    public void setNoteService(NoteService noteService) {
        this.noteService = noteService;
        if (fileSync != null) {
            fileSync.setNoteService(noteService);
        }
    }

    public boolean isEnabled() { 
        return fileSync.isEnabled(); 
    }

    /**
     * 启动时同步策略（无状态回调）
     */
    public boolean syncFromCloudOnStart() {
        return syncFromCloudOnStart(null);
    }

    /**
     * 启动时同步策略：
     * - 文件同步模式：智能决定上传/下载
     * - 整库模式：从云端拉取最新数据库
     * @param statusCallback 状态回调，用于更新 UI 状态栏
     */
    public boolean syncFromCloudOnStart(Consumer<String> statusCallback) {
        logger.debug(">>> [DbSyncService] syncFromCloudOnStart() 被调用");
        
        if (useFileSync) {
            // 文件同步模式
            fileSync.syncOnStart(statusCallback);
            return true;
        }
        
        // 旧的整库同步模式（保留向后兼容）
        return legacySyncFromCloudOnStart();
    }

    /**
     * 上传到云端策略：
     * - 文件同步模式：增量上传变更的文件
     * - 整库模式：上传整个数据库
     */
    public boolean syncToCloud() {
        logger.debug(">>> [DbSyncService] syncToCloud() 被调用");
        logger.debug(">>> [DbSyncService] 使用文件同步: " + useFileSync);
        
        if (useFileSync) {
            // 文件同步模式
            return fileSync.syncToCloud();
        }
        
        // 旧的整库同步模式
        return legacySyncToCloud();
    }

    public void syncToCloudSilently() {
        try { 
            syncToCloud(); 
        } catch (Throwable e) {
            logger.error("[DbSync] 同步失败: " + e.getMessage());
        }
    }

    /**
     * 带超时的同步（退出时使用）
     * @param timeoutSeconds 超时秒数
     * @return true 如果同步成功或超时
     */
    public boolean syncToCloudWithTimeout(int timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> {
            try {
                return syncToCloud();
            } catch (Throwable e) {
                logger.error("[DbSync] 同步失败: " + e.getMessage());
                return false;
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.info("[DbSync] 同步超时（" + timeoutSeconds + "秒），跳过同步");
            future.cancel(true);
            return false;
        } catch (Exception e) {
            logger.error("[DbSync] 同步异常: " + e.getMessage());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 从云端下载策略：
     * - 文件同步模式：增量下载变更的文件
     * - 整库模式：下载整个数据库
     */
    public boolean syncFromCloud() {
        logger.debug(">>> [DbSyncService] syncFromCloud() 被调用");
        logger.debug(">>> [DbSyncService] 使用文件同步: " + useFileSync);
        
        if (useFileSync) {
            // 文件同步模式
            return fileSync.syncFromCloud();
        }
        
        // 旧的整库同步模式
        return legacySyncFromCloud();
    }

    // ===== 以下是旧的整库同步方法（保留向后兼容）=====

    /**
     * 旧模式：启动时从云端下载数据库
     */
    private boolean legacySyncFromCloudOnStart() {
        NutstoreWebDAVSync webdavSync = NutstoreWebDAVSync.getInstance();
        if (webdavSync.isEnabled()) {
            webdavSync.syncFromCloudOnStart();
        }
        return false;
    }

    /**
     * 旧模式：上传整个数据库到云端
     */
    private boolean legacySyncToCloud() {
        logger.debug(">>> [DbSyncService] 执行旧模式整库同步");
        
        // 0. 执行 WAL checkpoint 确保数据写入主文件
        if (!checkpointWAL()) {
            logger.error(">>> [DbSyncService] ⚠️ WAL checkpoint 失败，但继续上传");
        }
        
        // 使用旧的 WebDAV 同步
        NutstoreWebDAVSync webdavSync = NutstoreWebDAVSync.getInstance();
        if (webdavSync.isEnabled()) {
            return webdavSync.syncToCloud();
        }
        
        return false;
    }

    /**
     * 旧模式：从云端下载整个数据库
     */
    private boolean legacySyncFromCloud() {
        NutstoreWebDAVSync webdavSync = NutstoreWebDAVSync.getInstance();
        if (webdavSync.isEnabled()) {
            return webdavSync.syncFromCloud();
        }
        return false;
    }
    
    /**
     * 执行 SQLite WAL checkpoint
     * 将 WAL 文件中的所有更改合并到主数据库文件
     */
    private boolean checkpointWAL() {
        String jdbcUrl = "jdbc:sqlite:" + localDb.toAbsolutePath().toString();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            
            // 执行 PRAGMA wal_checkpoint(TRUNCATE)
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            logger.debug(">>> [DbSyncService] WAL checkpoint 执行成功");
            
            return true;
        } catch (Exception e) {
            logger.error(">>> [DbSyncService] ❌ WAL checkpoint 失败: " + e.getMessage());
            return false;
        }
    }
}
