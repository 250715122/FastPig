package com.gt;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

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
            System.out.println("[WebDAV] 坚果云同步已启用");
            System.out.println("[WebDAV] 用户名: " + username);
            System.out.println("[WebDAV] 云端路径: " + webdavUrl);
        } else {
            System.out.println("[WebDAV] 坚果云同步未配置");
            System.out.println("[WebDAV] 请在 config.properties 中配置账号信息");
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
            System.err.println("[WebDAV] 解码 b64: 密码失败，按原样使用: " + e.getMessage());
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
     * 上传到坚果云（Alt+S 触发）
     */
    public boolean syncToCloud() {
        System.out.println(">>> [WebDAV] syncToCloud() 被调用");
        System.out.println(">>> [WebDAV] 启用状态: " + enabled);
        System.out.println(">>> [WebDAV] 用户名: " + username);
        System.out.println(">>> [WebDAV] WebDAV URL: " + webdavUrl);
        System.out.println(">>> [WebDAV] 本地数据库: " + localDb);
        
        if (!enabled) {
            System.out.println(">>> [WebDAV] ❌ WebDAV 未启用");
            return false;
        }
        
        try {
            if (!Files.exists(localDb)) {
                System.out.println(">>> [WebDAV] ❌ 本地数据库不存在: " + localDb);
                return false;
            }
            
            long fileSize = Files.size(localDb);
            System.out.println(">>> [WebDAV] 本地数据库大小: " + fileSize + " bytes");
            
            System.out.println(">>> [WebDAV] 创建 Sardine 客户端...");
            Sardine sardine = SardineFactory.begin(username, password);
            System.out.println(">>> [WebDAV] Sardine 客户端创建成功");
            
            // 确保父目录存在（捕获异常，如果目录已存在会返回错误）
            String parentUrl = webdavUrl.substring(0, webdavUrl.lastIndexOf('/') + 1);
            System.out.println(">>> [WebDAV] 父目录 URL: " + parentUrl);
            
            try {
                System.out.println(">>> [WebDAV] 检查父目录是否存在...");
                boolean parentExists = sardine.exists(parentUrl);
                System.out.println(">>> [WebDAV] 父目录存在: " + parentExists);
                
                if (!parentExists) {
                    System.out.println(">>> [WebDAV] 创建父目录...");
                    sardine.createDirectory(parentUrl);
                    System.out.println(">>> [WebDAV] ✅ 已创建云端目录: " + parentUrl);
                }
            } catch (Exception e) {
                // 目录可能已存在或无权限检查，直接尝试上传
                System.out.println(">>> [WebDAV] ⚠️ 目录检查异常: " + e.getMessage());
                System.out.println(">>> [WebDAV] 跳过目录检查，直接尝试上传");
            }
            
            System.out.println(">>> [WebDAV] 开始上传文件到: " + webdavUrl);
            uploadToCloud(sardine);
            System.out.println(">>> [WebDAV] ✅ 已将本地数据库同步到坚果云");
            return true;
            
        } catch (Exception e) {
            System.err.println(">>> [WebDAV] ❌ 上传失败: " + e.getMessage());
            System.err.println(">>> [WebDAV] 异常类型: " + e.getClass().getName());
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
        System.out.println(">>> [WebDAV] syncFromCloud() 被调用");
        System.out.println(">>> [WebDAV] 启用状态: " + enabled);
        System.out.println(">>> [WebDAV] 用户名: " + username);
        System.out.println(">>> [WebDAV] WebDAV URL: " + webdavUrl);
        System.out.println(">>> [WebDAV] 本地数据库: " + localDb);
        
        if (!enabled) {
            System.out.println(">>> [WebDAV] ❌ 云端同步未启用");
            return false;
        }
        
        try {
            System.out.println(">>> [WebDAV] 创建 Sardine 客户端...");
            Sardine sardine = SardineFactory.begin(username, password);
            System.out.println(">>> [WebDAV] Sardine 客户端创建成功");
            
            // 检查云端文件是否存在
            System.out.println(">>> [WebDAV] 检查云端文件是否存在: " + webdavUrl);
            boolean cloudExists = sardine.exists(webdavUrl);
            System.out.println(">>> [WebDAV] 云端文件存在: " + cloudExists);
            
            if (!cloudExists) {
                System.out.println(">>> [WebDAV] ❌ 云端数据库不存在，无法下载");
                return false;
            }
            
            // 强制下载，覆盖本地
            System.out.println(">>> [WebDAV] 开始从云端下载文件...");
            downloadFromCloud(sardine);
            System.out.println(">>> [WebDAV] ✅ 已从云端下载并覆盖本地数据库");
            System.out.println(">>> [WebDAV] 本地文件大小: " + Files.size(localDb) + " bytes");
            return true;
            
        } catch (Exception e) {
            System.err.println(">>> [WebDAV] ❌ 从云端下载失败: " + e.getMessage());
            System.err.println(">>> [WebDAV] 异常类型: " + e.getClass().getName());
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
