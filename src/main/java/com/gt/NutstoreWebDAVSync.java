package com.gt;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
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
    private static final Logger logger = LogManager.getLogger(NutstoreWebDAVSync.class);
    
    private static NutstoreWebDAVSync instance;
    
    private final boolean enabled;
    private final String webdavUrl;
    private final String username;
    private final String password;
    private final Path localDb;
    
    // 默认 WebDAV 地址
    private static final String DEFAULT_WEBDAV_BASE = "https://dav.jianguoyun.com/dav/";
    private static final String DEFAULT_SYNC_PATH = "FastPig/fastpig.db";

    private static final String B64_PREFIX = "b64:";
    
    private NutstoreWebDAVSync() {
        this.localDb = Paths.get(System.getProperty("user.dir"), "fastpig.db");
        
        // 读取配置
        Properties config = loadConfig();
        
        // 优先级：系统属性 > 配置文件 > 环境变量
        this.username = System.getProperty("nutstore.username", 
                        config.getProperty("nutstore.username", 
                        System.getenv("NUTSTORE_USERNAME")));
        
        String rawPassword = System.getProperty("nutstore.password",
                        config.getProperty("nutstore.password",
                        System.getenv("NUTSTORE_PASSWORD")));
        this.password = decodePasswordIfNeeded(rawPassword);
        
        String webdavBase = config.getProperty("nutstore.webdav.base", DEFAULT_WEBDAV_BASE);
        String syncPath = config.getProperty("nutstore.sync.path", DEFAULT_SYNC_PATH);
        this.webdavUrl = webdavBase + syncPath;
        
        this.enabled = (username != null && !username.isEmpty() 
                     && password != null && !password.isEmpty());
        
        if (enabled) {
            logger.info("[WebDAV] 坚果云同步已启用, 用户名: {}, 云端路径: {}", username, webdavUrl);
        } else {
            logger.info("[WebDAV] 坚果云同步未配置，请在 config.properties 中配置账号信息");
        }
    }

    /**
     * 兼容 UI 保存的 `b64:` 前缀 Base64 密码。
     * - `b64:...`：解码后使用
     * - 其它：按原样当明文使用（保持手工配置明文仍可用）
     */
    private String decodePasswordIfNeeded(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!value.startsWith(B64_PREFIX)) {
            return value;
        }
        String payload = value.substring(B64_PREFIX.length());
        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("[WebDAV] 解码 b64: 密码失败，按原样使用: {}", e.getMessage());
            return value;
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
                logger.debug("[配置] 从 {} 加载配置", configFile);
                return props;
            } catch (Exception e) {
                logger.error("[配置] 读取配置文件失败: {}", e.getMessage());
            }
        }
        
        // 尝试从 classpath 读取
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                logger.debug("[配置] 从 classpath 加载配置");
            } else {
                logger.debug("[配置] 未找到 config.properties 文件");
            }
        } catch (Exception e) {
            logger.error("[配置] 读取配置文件失败: {}", e.getMessage());
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
                logger.info("[WebDAV] 云端数据库不存在，跳过拉取");
                return;
            }
            
            // 如果本地文件不存在，直接下载
            if (!Files.exists(localDb)) {
                downloadFromCloud(sardine);
                logger.info("[WebDAV] 启动：本地不存在，已从云端下载");
                return;
            }
            
            // 比较修改时间
            long cloudModified = sardine.list(webdavUrl).get(0).getModified().getTime();
            long localModified = Files.getLastModifiedTime(localDb).toMillis();
            
            if (cloudModified > localModified) {
                downloadFromCloud(sardine);
                logger.info("[WebDAV] 启动：云端更新，已下载");
            } else {
                logger.debug("[WebDAV] 启动：本地已是最新，跳过拉取");
            }
            
        } catch (Exception e) {
            logger.error("[WebDAV] 启动拉取失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 上传到坚果云（Alt+S 触发）
     */
    public boolean syncToCloud() {
        logger.debug("[WebDAV] syncToCloud() 被调用, enabled={}, url={}", enabled, webdavUrl);
        
        if (!enabled) {
            logger.debug("[WebDAV] WebDAV 未启用");
            return false;
        }
        
        try {
            if (!Files.exists(localDb)) {
                logger.warn("[WebDAV] 本地数据库不存在: {}", localDb);
                return false;
            }
            
            long fileSize = Files.size(localDb);
            logger.debug("[WebDAV] 本地数据库大小: {} bytes", fileSize);
            
            Sardine sardine = SardineFactory.begin(username, password);
            
            // 确保父目录存在（捕获异常，如果目录已存在会返回错误）
            String parentUrl = webdavUrl.substring(0, webdavUrl.lastIndexOf('/') + 1);
            
            try {
                boolean parentExists = sardine.exists(parentUrl);
                if (!parentExists) {
                    sardine.createDirectory(parentUrl);
                    logger.debug("[WebDAV] 已创建云端目录: {}", parentUrl);
                }
            } catch (Exception e) {
                // 目录可能已存在或无权限检查，直接尝试上传
                logger.debug("[WebDAV] 目录检查异常，跳过: {}", e.getMessage());
            }
            
            uploadToCloud(sardine);
            logger.info("[WebDAV] 已将本地数据库同步到坚果云");
            return true;
            
        } catch (Exception e) {
            logger.error("[WebDAV] 上传失败: {}", e.getMessage(), e);
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
            logger.warn("[WebDAV] 静默上传失败: {}", ignore.getMessage());
        }
    }
    
    /**
     * 从云端强制下载数据库（Alt+U 触发）
     * 无论本地是否最新，都会覆盖本地文件
     */
    public boolean syncFromCloud() {
        logger.debug("[WebDAV] syncFromCloud() 被调用, enabled={}, url={}", enabled, webdavUrl);
        
        if (!enabled) {
            logger.debug("[WebDAV] 云端同步未启用");
            return false;
        }
        
        try {
            Sardine sardine = SardineFactory.begin(username, password);
            
            // 检查云端文件是否存在
            boolean cloudExists = sardine.exists(webdavUrl);
            
            if (!cloudExists) {
                logger.warn("[WebDAV] 云端数据库不存在，无法下载");
                return false;
            }
            
            // 强制下载，覆盖本地
            downloadFromCloud(sardine);
            logger.info("[WebDAV] 已从云端下载并覆盖本地数据库, 大小: {} bytes", Files.size(localDb));
            return true;
            
        } catch (Exception e) {
            logger.error("[WebDAV] 从云端下载失败: {}", e.getMessage(), e);
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
