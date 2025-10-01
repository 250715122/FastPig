package com.gt;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 坚果云 WebDAV 同步服务
 * 通过坚果云的 WebDAV 接口实现数据库文件的上传和下载
 * 
 * 配置方式（按优先级）：
 * 1. config.properties 文件
 * 2. 系统属性 (-Dnutstore.username=xxx)
 * 3. 环境变量 (NUTSTORE_USERNAME)
 */
public class NutstoreWebDAVSync {
    private static NutstoreWebDAVSync instance;
    
    private final boolean enabled;
    private final String webdavUrl;
    private final String username;
    private final String password;
    private final Path localDb;
    
    // 默认 WebDAV 地址
    private static final String DEFAULT_WEBDAV_BASE = "https://dav.jianguoyun.com/dav/";
    private static final String DEFAULT_SYNC_PATH = "FastPig/fastpig.db";
    
    private NutstoreWebDAVSync() {
        this.localDb = Paths.get(System.getProperty("user.dir"), "fastpig.db");
        
        // 读取配置
        Properties config = loadConfig();
        
        // 优先级：系统属性 > 配置文件 > 环境变量
        this.username = System.getProperty("nutstore.username", 
                        config.getProperty("nutstore.username", 
                        System.getenv("NUTSTORE_USERNAME")));
        
        this.password = System.getProperty("nutstore.password", 
                        config.getProperty("nutstore.password", 
                        System.getenv("NUTSTORE_PASSWORD")));
        
        String webdavBase = config.getProperty("nutstore.webdav.base", DEFAULT_WEBDAV_BASE);
        String syncPath = config.getProperty("nutstore.sync.path", DEFAULT_SYNC_PATH);
        this.webdavUrl = webdavBase + syncPath;
        
        this.enabled = (username != null && !username.isEmpty() 
                     && password != null && !password.isEmpty());
        
        if (enabled) {
            System.out.println("[WebDAV] 坚果云同步已启用");
            System.out.println("[WebDAV] 用户名: " + username);
            System.out.println("[WebDAV] 云端路径: " + webdavUrl);
        } else {
            System.out.println("[WebDAV] 坚果云同步未配置");
            System.out.println("[WebDAV] 请在 config.properties 中配置账号信息");
        }
    }
    
    /**
     * 加载配置文件
     * 优先从程序所在目录读取，如果不存在则从 classpath 读取
     */
    private Properties loadConfig() {
        Properties props = new Properties();
        
        // 尝试从程序所在目录读取
        Path configFile = Paths.get(System.getProperty("user.dir"), "config.properties");
        if (Files.exists(configFile)) {
            try (InputStream input = new FileInputStream(configFile.toFile())) {
                props.load(input);
                System.out.println("[配置] 从 " + configFile + " 加载配置");
                return props;
            } catch (Exception e) {
                System.err.println("[配置] 读取配置文件失败: " + e.getMessage());
            }
        }
        
        // 尝试从 classpath 读取
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                System.out.println("[配置] 从 classpath 加载配置");
            } else {
                System.out.println("[配置] 未找到 config.properties 文件");
            }
        } catch (Exception e) {
            System.err.println("[配置] 读取配置文件失败: " + e.getMessage());
        }
        
        return props;
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
            
            // 确保父目录存在（捕获异常，如果目录已存在会返回错误）
            String parentUrl = webdavUrl.substring(0, webdavUrl.lastIndexOf('/') + 1);
            try {
                if (!sardine.exists(parentUrl)) {
                    sardine.createDirectory(parentUrl);
                    System.out.println("[WebDAV] 已创建云端目录: " + parentUrl);
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
    
    /**
     * 从云端强制下载数据库（Alt+U 触发）
     * 无论本地是否最新，都会覆盖本地文件
     */
    public boolean syncFromCloud() {
        if (!enabled) {
            System.out.println("[WebDAV] 云端同步未启用");
            return false;
        }
        
        try {
            Sardine sardine = SardineFactory.begin(username, password);
            
            // 检查云端文件是否存在
            if (!sardine.exists(webdavUrl)) {
                System.out.println("[WebDAV] 云端数据库不存在，无法下载");
                return false;
            }
            
            // 强制下载，覆盖本地
            downloadFromCloud(sardine);
            System.out.println("[WebDAV] 已从云端下载并覆盖本地数据库");
            return true;
            
        } catch (Exception e) {
            System.err.println("[WebDAV] 从云端下载失败: " + e.getMessage());
            e.printStackTrace();
            return false;
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
