package com.gt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 数据库同步服务 - 集成本地备份和坚果云 WebDAV 同步
 *
 * 同步策略：
 * 1. 本地备份：fastpig.db → data/fastpig.db（本地副本）
 * 2. WebDAV 同步：fastpig.db → 坚果云网页端（真正的云同步）
 *
 * 配置方式：
 * - nutstore.username: 坚果云账号（邮箱）
 * - nutstore.password: 坚果云应用密码（非登录密码！）
 */
public class DbSyncService {

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
    private final Path cloudDir;
    private final Path cloudDb;
    private final boolean localBackupEnabled;
    private final NutstoreWebDAVSync webdavSync;

    private DbSyncService() {
        this.localDb = Paths.get(System.getProperty("user.dir"), "fastpig.db");
        // 本地备份目录（已禁用，只使用坚果云云端备份）
        this.cloudDir = Paths.get(System.getProperty("user.dir"), "data");
        this.cloudDb = this.cloudDir.resolve("fastpig.db");
        this.localBackupEnabled = false;  // 禁用本地备份
        this.webdavSync = NutstoreWebDAVSync.getInstance();
        
        if (localBackupEnabled) {
            System.out.println("[DbSync] 本地备份目录: " + this.cloudDir.toAbsolutePath());
        }
    }

    public boolean isEnabled() { return webdavSync.isEnabled(); }

    /**
     * 启动时同步策略：
     * 1. 优先从坚果云 WebDAV 拉取（如果配置了）
     * 2. 否则从本地 data 目录拉取
     */
    public boolean syncFromCloudOnStart() {
        // 优先尝试 WebDAV 同步
        if (webdavSync.isEnabled()) {
            webdavSync.syncFromCloudOnStart();
        }
        
        // 同时进行本地备份同步
        if (!localBackupEnabled) return false;
        try {
            if (!Files.exists(cloudDb)) {
                System.out.println("[DbSync] 本地备份不存在，跳过拉取");
                return false;
            }
            if (!Files.exists(localDb)) {
                ensureCloudDir();
                Files.copy(cloudDb, localDb, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[DbSync] 启动：已从本地备份拉取");
                return true;
            }
            long cloudTs = Files.getLastModifiedTime(cloudDb).toMillis();
            long localTs = Files.getLastModifiedTime(localDb).toMillis();
            if (cloudTs > localTs) {
                Files.copy(cloudDb, localDb, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[DbSync] 启动：本地备份较新，已覆盖");
                return true;
            }
            System.out.println("[DbSync] 启动：本地已是最新，跳过拉取");
            return false;
        } catch (IOException e) {
            System.err.println("[DbSync] 启动拉取失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 上传到云端策略：
     * 1. 先保存到本地 data 目录备份
     * 2. 再上传到坚果云 WebDAV（如果配置了）
     */
    public boolean syncToCloud() {
        System.out.println(">>> [DbSyncService] syncToCloud() 被调用");
        System.out.println(">>> [DbSyncService] 本地数据库: " + localDb);
        System.out.println(">>> [DbSyncService] 本地备份启用: " + localBackupEnabled);
        System.out.println(">>> [DbSyncService] WebDAV 启用: " + webdavSync.isEnabled());
        
        boolean success = true;
        
        // 1. 本地备份
        if (localBackupEnabled) {
            System.out.println(">>> [DbSyncService] 开始本地备份...");
            try {
                if (!Files.exists(localDb)) {
                    System.out.println(">>> [DbSyncService] ❌ 本地数据库不存在: " + localDb);
                    return false;
                }
                System.out.println(">>> [DbSyncService] 本地数据库存在，大小: " + Files.size(localDb) + " bytes");
                ensureCloudDir();
                Files.copy(localDb, cloudDb, StandardCopyOption.REPLACE_EXISTING);
                System.out.println(">>> [DbSyncService] ✅ 已保存到本地备份: " + cloudDb);
            } catch (IOException e) {
                System.err.println(">>> [DbSyncService] ❌ 本地备份失败: " + e.getMessage());
                e.printStackTrace();
                success = false;
            }
        } else {
            System.out.println(">>> [DbSyncService] 本地备份未启用，跳过");
        }
        
        // 2. WebDAV 同步
        if (webdavSync.isEnabled()) {
            System.out.println(">>> [DbSyncService] 开始 WebDAV 同步...");
            boolean webdavSuccess = webdavSync.syncToCloud();
            System.out.println(">>> [DbSyncService] WebDAV 同步结果: " + (webdavSuccess ? "成功" : "失败"));
            success = success && webdavSuccess;
        } else {
            System.out.println(">>> [DbSyncService] WebDAV 未启用，跳过");
        }
        
        System.out.println(">>> [DbSyncService] syncToCloud() 最终结果: " + (success ? "成功" : "失败"));
        return success;
    }

    public void syncToCloudSilently() {
        try { 
            syncToCloud(); 
        } catch (Throwable e) {
            System.err.println("[DbSync] 同步失败: " + e.getMessage());
        }
    }

    /**
     * 从云端下载策略（Alt+U 触发）：
     * 强制从坚果云 WebDAV 下载，覆盖本地数据库
     */
    public boolean syncFromCloud() {
        System.out.println(">>> [DbSyncService] syncFromCloud() 被调用");
        System.out.println(">>> [DbSyncService] WebDAV 启用: " + webdavSync.isEnabled());
        
        // 仅支持 WebDAV 同步
        if (webdavSync.isEnabled()) {
            System.out.println(">>> [DbSyncService] 开始从 WebDAV 下载...");
            boolean result = webdavSync.syncFromCloud();
            System.out.println(">>> [DbSyncService] WebDAV 下载结果: " + (result ? "成功" : "失败"));
            return result;
        } else {
            System.out.println(">>> [DbSyncService] ❌ 云端同步未启用，无法下载");
            return false;
        }
    }

    private void ensureCloudDir() throws IOException {
        if (!Files.exists(cloudDir)) {
            Files.createDirectories(cloudDir);
        }
    }
}