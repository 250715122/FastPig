package com.gt;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 坚果云 WebDAV 同步服务
 * 通过坚果云的 WebDAV 接口实现数据库文件的上传和下载
 */
public class NutstoreWebDAVSync {
    private static NutstoreWebDAVSync instance;
    
    private final boolean enabled;
    private final String webdavUrl;
    private final String username;
    private final String password;
    private final Path localDb;
    
    // 坚果云 WebDAV 地址
    private static final String NUTSTORE_WEBDAV_BASE = "https://dav.jianguoyun.com/dav/";
    
    private NutstoreWebDAVSync() {
        this.localDb = Paths.get(System.getProperty("user.dir"), "fastpig.db");
        
        // 从系统属性或环境变量读取坚果云账号信息
        this.username = System.getProperty("nutstore.username", System.getenv("NUTSTORE_USERNAME"));
        this.password = System.getProperty("nutstore.password", System.getenv("NUTSTORE_PASSWORD"));
        
        // WebDAV 路径：FastPig/fastpig.db
        this.webdavUrl = NUTSTORE_WEBDAV_BASE + "FastPig/fastpig.db";
        
        this.enabled = (username != null && !username.isEmpty() 
                     && password != null && !password.isEmpty());
        
        if (enabled) {
            System.out.println("[WebDAV] 坚果云同步已启用");
            System.out.println("[WebDAV] 用户名: " + username);
            System.out.println("[WebDAV] 云端路径: " + webdavUrl);
        } else {
            System.out.println("[WebDAV] 坚果云同步未配置（需要设置 nutstore.username 和 nutstore.password）");
        }
    }
    
    public static NutstoreWebDAVSync getInstance() {
        if (instance == null) {
            instance = new NutstoreWebDAVSync();
        }
        return instance;
    }
    
    /**
     * 启动时从坚果云下载数据库（如果云端更新）
     */
    public void syncFromCloudOnStart() {
        if (!enabled) return;
        
        try {
            Sardine sardine = SardineFactory.begin(username, password);
            
            // 检查云端文件是否存在
            if (!sardine.exists(webdavUrl)) {
                System.out.println("[WebDAV] 云端数据库不存在，跳过拉取");
                return;
            }
            
            // 如果本地文件不存在，直接下载
            if (!Files.exists(localDb)) {
                downloadFromCloud(sardine);
                System.out.println("[WebDAV] 启动：本地不存在，已从云端下载");
                return;
            }
            
            // 比较修改时间
            long cloudModified = sardine.list(webdavUrl).get(0).getModified().getTime();
            long localModified = Files.getLastModifiedTime(localDb).toMillis();
            
            if (cloudModified > localModified) {
                downloadFromCloud(sardine);
                System.out.println("[WebDAV] 启动：云端更新，已下载");
            } else {
                System.out.println("[WebDAV] 启动：本地已是最新，跳过拉取");
            }
            
        } catch (Exception e) {
            System.err.println("[WebDAV] 启动拉取失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 上传到坚果云（Ctrl+Alt+S 或关闭时）
     */
    public boolean syncToCloud() {
        if (!enabled) return false;
        
        try {
            if (!Files.exists(localDb)) {
                System.out.println("[WebDAV] 本地数据库不存在，跳过上传");
                return false;
            }
            
            Sardine sardine = SardineFactory.begin(username, password);
            
            // 确保 FastPig 目录存在（捕获异常，如果目录已存在会返回错误）
            String parentUrl = NUTSTORE_WEBDAV_BASE + "FastPig/";
            try {
                if (!sardine.exists(parentUrl)) {
                    sardine.createDirectory(parentUrl);
                    System.out.println("[WebDAV] 已创建云端目录: FastPig/");
                }
            } catch (Exception e) {
                // 目录可能已存在或无权限检查，直接尝试上传
                System.out.println("[WebDAV] 跳过目录检查，直接上传");
            }
            
            uploadToCloud(sardine);
            System.out.println("[WebDAV] 已将本地数据库同步到坚果云");
            return true;
            
        } catch (Exception e) {
            System.err.println("[WebDAV] 上传失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 静默上传（关闭时使用，不抛异常）
     */
    public void syncToCloudSilently() {
        try {
            syncToCloud();
        } catch (Throwable ignore) {
            System.err.println("[WebDAV] 静默上传失败: " + ignore.getMessage());
        }
    }
    
    private void downloadFromCloud(Sardine sardine) throws Exception {
        try (InputStream in = sardine.get(webdavUrl);
             FileOutputStream out = new FileOutputStream(localDb.toFile())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }
    
    private void uploadToCloud(Sardine sardine) throws Exception {
        // 使用 byte[] 而不是 FileInputStream，避免 NonRepeatableRequestException
        byte[] data = Files.readAllBytes(localDb);
        sardine.put(webdavUrl, data);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}
