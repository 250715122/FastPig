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
        // 本地备份目录
        this.cloudDir = Paths.get(System.getProperty("user.dir"), "data");
        this.cloudDb = this.cloudDir.resolve("fastpig.db");
        this.localBackupEnabled = true;
        this.webdavSync = NutstoreWebDAVSync.getInstance();
        
        System.out.println("[DbSync] 本地备份目录: " + this.cloudDir.toAbsolutePath());
    }

    public boolean isEnabled() { return localBackupEnabled || webdavSync.isEnabled(); }

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
        boolean success = true;
        
        // 1. 本地备份
        if (localBackupEnabled) {
            try {
                if (!Files.exists(localDb)) {
                    System.out.println("[DbSync] 本地数据库不存在，跳过上传");
                    return false;
                }
                ensureCloudDir();
                Files.copy(localDb, cloudDb, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[DbSync] 已保存到本地备份");
            } catch (IOException e) {
                System.err.println("[DbSync] 本地备份失败: " + e.getMessage());
                success = false;
            }
        }
        
        // 2. WebDAV 同步
        if (webdavSync.isEnabled()) {
            boolean webdavSuccess = webdavSync.syncToCloud();
            success = success && webdavSuccess;
        }
        
        return success;
    }

    public void syncToCloudSilently() {
        try { 
            syncToCloud(); 
        } catch (Throwable e) {
            System.err.println("[DbSync] 同步失败: " + e.getMessage());
        }
    }

    private void ensureCloudDir() throws IOException {
        if (!Files.exists(cloudDir)) {
            Files.createDirectories(cloudDir);
        }
    }
}