package com.gt.cloud;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 坚果云 WebDAV 云存储实现
 * 通过 WebDAV 协议实现文件的上传、下载和管理
 */
public class NutstoreCloudProvider implements CloudStorageProvider {

    private static final Logger logger = LogManager.getLogger(NutstoreCloudProvider.class);
    private static NutstoreCloudProvider instance;

    private final boolean enabled;
    private final String username;
    private final String password;
    private final String webdavBase;
    private final String syncPath;
    private final String syncRootUrl;

    // 默认 WebDAV 地址
    private static final String DEFAULT_WEBDAV_BASE = "https://dav.jianguoyun.com/dav/";
    private static final String DEFAULT_SYNC_PATH = "FastPig/notes";

    private static final String B64_PREFIX = "b64:";
    
    // 请求限流：避免触发坚果云安全限制
    // 坚果云免费版限制较严格，需要较长间隔
    private static final long REQUEST_INTERVAL_MS = 1000; // 请求间隔 1 秒
    private static final long RETRY_WAIT_MS = 30000; // 限流后等待 30 秒
    private long lastRequestTime = 0;
    
    // 目录缓存：避免重复检查/创建目录
    private final java.util.Set<String> existingDirs = new java.util.HashSet<>();

    private NutstoreCloudProvider() {
        Properties config = loadConfig();

        // 优先级：系统属性 > 配置文件 > 环境变量
        this.username = System.getProperty("nutstore.username",
                config.getProperty("nutstore.username",
                        System.getenv("NUTSTORE_USERNAME")));

        String rawPassword = System.getProperty("nutstore.password",
                config.getProperty("nutstore.password",
                        System.getenv("NUTSTORE_PASSWORD")));
        this.password = decodePasswordIfNeeded(rawPassword);

        this.webdavBase = config.getProperty("nutstore.webdav.base", DEFAULT_WEBDAV_BASE);
        this.syncPath = config.getProperty("nutstore.sync.path", DEFAULT_SYNC_PATH);
        this.syncRootUrl = webdavBase + syncPath;

        this.enabled = (username != null && !username.isEmpty()
                && password != null && !password.isEmpty());

        if (enabled) {
            logger.info("[NutstoreProvider] 坚果云已启用");
            logger.info("[NutstoreProvider] 用户名: {}", username);
            logger.info("[NutstoreProvider] 同步路径: {}", syncRootUrl);
        } else {
            logger.warn("[NutstoreProvider] 坚果云未配置，请在 config.properties 中配置账号");
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
            logger.warn("[NutstoreProvider] 解码 b64: 密码失败，按原样使用: {}", e.getMessage());
            return value;
        }
    }

    public static synchronized NutstoreCloudProvider getInstance() {
        if (instance == null) {
            instance = new NutstoreCloudProvider();
        }
        return instance;
    }

    /**
     * 加载配置文件
     */
    private Properties loadConfig() {
        Properties props = new Properties();

        // 尝试从程序所在目录读取
        Path configFile = Paths.get(System.getProperty("user.dir"), "config.properties");
        if (Files.exists(configFile)) {
            try (InputStream input = new FileInputStream(configFile.toFile())) {
                props.load(input);
                return props;
            } catch (Exception e) {
                logger.error("[NutstoreProvider] 读取配置文件失败: {}", e.getMessage());
            }
        }

        // 尝试从 classpath 读取
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 读取配置文件失败: {}", e.getMessage());
        }

        return props;
    }

    /**
     * 获取 Sardine 客户端
     */
    private Sardine getSardine() {
        return SardineFactory.begin(username, password);
    }

    /**
     * 请求限流：确保请求之间有足够的间隔
     */
    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(REQUEST_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    /**
     * 构建完整的远程 URL
     */
    private String buildFullUrl(String remotePath) {
        if (remotePath == null || remotePath.isEmpty()) {
            return syncRootUrl;
        }
        String path = remotePath.startsWith("/") ? remotePath.substring(1) : remotePath;
        String base = syncRootUrl.endsWith("/") ? syncRootUrl : syncRootUrl + "/";
        return base + path;
    }

    @Override
    public boolean upload(String remotePath, byte[] data) {
        if (!enabled) {
            logger.warn("[NutstoreProvider] 坚果云未启用，请检查 config.properties 配置");
            return false;
        }

        String fullUrl = buildFullUrl(remotePath);
        logger.info("[NutstoreProvider] 上传文件: {}", fullUrl);

        try {
            Sardine sardine = getSardine();

            // 确保父目录存在（带缓存）
            String parentUrl = fullUrl.substring(0, fullUrl.lastIndexOf('/'));
            ensureDirectoryExistsCached(sardine, parentUrl);

            // 限流后上传文件
            throttle();
            sardine.put(fullUrl, data);
            logger.info("[NutstoreProvider] 上传成功: {} ({} bytes)", remotePath, data.length);
            return true;

        } catch (com.github.sardine.impl.SardineException e) {
            // 只在出错时打印详细信息
            logger.error("[NutstoreProvider] WebDAV 错误: HTTP {} - {}", e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() == 401) {
                logger.error("[NutstoreProvider] 提示: 请检查坚果云用户名和应用密码是否正确");
            }
            logger.error("[NutstoreProvider] 上传失败: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 上传异常: {}", e.getMessage(), e);
            logger.error("[NutstoreProvider] 上传失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public byte[] download(String remotePath) {
        if (!enabled) {
            logger.debug("[NutstoreProvider] 未启用，跳过下载");
            return null;
        }

        String fullUrl = buildFullUrl(remotePath);
        logger.info("[NutstoreProvider] 下载文件: {}", fullUrl);

        try {
            Sardine sardine = getSardine();
            throttle();

            // 直接尝试下载，不检查 exists（坚果云 HEAD 请求返回 403）
            try (InputStream in = sardine.get(fullUrl);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                byte[] data = out.toByteArray();
                logger.info("[NutstoreProvider] 下载成功: {} ({} bytes)", remotePath, data.length);
                return data;
            }

        } catch (com.github.sardine.impl.SardineException e) {
            if (e.getStatusCode() == 404) {
                logger.debug("[NutstoreProvider] 文件不存在: {}", remotePath);
            } else {
                logger.error("[NutstoreProvider] 下载失败: {}", e.getMessage());
            }
            return null;
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 下载失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean delete(String remotePath) {
        if (!enabled) {
            return false;
        }

        String fullUrl = buildFullUrl(remotePath);
        logger.info("[NutstoreProvider] 删除文件: {}", fullUrl);

        try {
            Sardine sardine = getSardine();
            throttle();
            // 直接尝试删除，不检查 exists（坚果云 HEAD 请求返回 403）
            sardine.delete(fullUrl);
            logger.info("[NutstoreProvider] 删除成功: {}", remotePath);
            return true;
        } catch (com.github.sardine.impl.SardineException e) {
            if (e.getStatusCode() == 404) {
                logger.debug("[NutstoreProvider] 文件不存在，无需删除: {}", remotePath);
                return true;
            }
            logger.error("[NutstoreProvider] 删除失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 删除失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String remotePath) {
        if (!enabled) {
            return false;
        }

        String fullUrl = buildFullUrl(remotePath);

        try {
            Sardine sardine = getSardine();
            throttle();
            // 坚果云不支持 HEAD 请求（返回 403），使用 PROPFIND 代替
            List<DavResource> resources = sardine.list(fullUrl);
            return !resources.isEmpty();
        } catch (com.github.sardine.impl.SardineException e) {
            // 404 表示不存在
            if (e.getStatusCode() == 404) {
                return false;
            }
            logger.error("[NutstoreProvider] 检查存在失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 检查存在失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<CloudFileInfo> listFiles(String remoteDir) {
        List<CloudFileInfo> result = new ArrayList<>();

        if (!enabled) {
            return result;
        }

        String fullUrl = buildFullUrl(remoteDir);
        if (!fullUrl.endsWith("/")) {
            fullUrl += "/";
        }

        logger.debug("[NutstoreProvider] 列出目录: {}", fullUrl);

        try {
            Sardine sardine = getSardine();
            throttle();
            
            // 直接尝试列出目录，不使用 exists() 检查（坚果云对 HEAD 请求返回 403）
            List<DavResource> resources = sardine.list(fullUrl);

            // 第一个元素是目录本身，跳过
            for (int i = 1; i < resources.size(); i++) {
                DavResource res = resources.get(i);
                CloudFileInfo info = new CloudFileInfo();
                info.setPath(res.getPath());
                info.setName(res.getName());
                info.setSize(res.getContentLength() != null ? res.getContentLength() : 0);
                info.setLastModified(res.getModified() != null ? res.getModified().getTime() : 0);
                info.setDirectory(res.isDirectory());
                result.add(info);
            }

            logger.info("[NutstoreProvider] 找到 {} 个文件/目录", result.size());

        } catch (com.github.sardine.impl.SardineException e) {
            // 404 表示目录不存在，这是正常情况
            if (e.getStatusCode() == 404) {
                logger.debug("[NutstoreProvider] 目录不存在: {}", remoteDir);
            } else {
                logger.error("[NutstoreProvider] 列出目录失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 列出目录失败: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public List<CloudFileInfo> listFilesRecursive(String remoteDir) {
        List<CloudFileInfo> result = new ArrayList<>();

        if (!enabled) {
            return result;
        }

        logger.debug("[NutstoreProvider] 递归列出目录: {}", remoteDir);

        try {
            // 使用手动递归方式，兼容不支持 infinity 深度的 WebDAV 服务器
            listFilesRecursiveInternal(remoteDir, result);
            logger.info("[NutstoreProvider] 递归找到 {} 个文件/目录", result.size());
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 递归列出目录失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 内部递归方法：手动递归列出所有文件和目录
     */
    private void listFilesRecursiveInternal(String dir, List<CloudFileInfo> result) throws Exception {
        List<CloudFileInfo> items = listFiles(dir);
        
        for (CloudFileInfo item : items) {
            // 构建相对路径
            String relativePath = dir.isEmpty() ? item.getName() : dir + "/" + item.getName();
            item.setPath(relativePath);
            result.add(item);
            
            // 如果是目录，递归处理
            if (item.isDirectory()) {
                listFilesRecursiveInternal(relativePath, result);
            }
        }
    }

    @Override
    public boolean createDirectory(String remotePath) {
        if (!enabled) {
            return false;
        }

        String fullUrl = buildFullUrl(remotePath);
        if (!fullUrl.endsWith("/")) {
            fullUrl += "/";
        }

        logger.debug("[NutstoreProvider] 创建目录: {}", fullUrl);

        try {
            Sardine sardine = getSardine();
            ensureDirectoryExists(sardine, fullUrl);
            return true;
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 创建目录失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 带缓存的目录创建：避免重复检查/创建已知目录
     */
    private void ensureDirectoryExistsCached(Sardine sardine, String dirUrl) throws Exception {
        if (dirUrl == null || dirUrl.isEmpty()) {
            return;
        }

        // 移除末尾斜杠以便处理
        String url = dirUrl.endsWith("/") ? dirUrl.substring(0, dirUrl.length() - 1) : dirUrl;

        // 不能超出 webdavBase
        if (url.length() <= webdavBase.length()) {
            return;
        }

        // 检查缓存
        if (existingDirs.contains(url)) {
            return;
        }

        // 递归创建父目录
        String parentUrl = url.substring(0, url.lastIndexOf('/'));
        ensureDirectoryExistsCached(sardine, parentUrl);

        // 创建当前目录
        try {
            throttle();
            sardine.createDirectory(url + "/");
            existingDirs.add(url);
            logger.debug("[NutstoreProvider] 已创建目录: {}", url);
        } catch (Exception e) {
            // 目录可能已存在（405 或 already exists），加入缓存
            if (e.getMessage().contains("405") || e.getMessage().contains("already exists") 
                || e.getMessage().contains("301")) {
                existingDirs.add(url);
            } else if (e.getMessage().contains("503")) {
                // 限流错误，等待后重试
                logger.warn("[NutstoreProvider] 触发限流，等待 {} 秒后重试...", (RETRY_WAIT_MS/1000));
                Thread.sleep(RETRY_WAIT_MS);
                throttle();
                try {
                    sardine.createDirectory(url + "/");
                    existingDirs.add(url);
                    logger.debug("[NutstoreProvider] 已创建目录: {}", url);
                } catch (Exception e2) {
                    // 重试后仍然失败，可能目录已存在
                    if (e2.getMessage().contains("405") || e2.getMessage().contains("301")) {
                        existingDirs.add(url);
                    } else {
                        logger.error("[NutstoreProvider] 创建目录失败: {}", e2.getMessage());
                    }
                }
            } else {
                throw e;
            }
        }
    }

    /**
     * 递归确保目录存在（旧方法，保留兼容）
     */
    private void ensureDirectoryExists(Sardine sardine, String dirUrl) throws Exception {
        ensureDirectoryExistsCached(sardine, dirUrl);
    }

    @Override
    public CloudFileInfo getFileInfo(String remotePath) {
        if (!enabled) {
            return null;
        }

        String fullUrl = buildFullUrl(remotePath);

        try {
            Sardine sardine = getSardine();
            throttle();
            
            // 直接列出，不检查 exists（坚果云 HEAD 请求返回 403）
            List<DavResource> resources = sardine.list(fullUrl);
            if (resources.isEmpty()) {
                return null;
            }

            DavResource res = resources.get(0);
            CloudFileInfo info = new CloudFileInfo();
            info.setPath(res.getPath());
            info.setName(res.getName());
            info.setSize(res.getContentLength() != null ? res.getContentLength() : 0);
            info.setLastModified(res.getModified() != null ? res.getModified().getTime() : 0);
            info.setDirectory(res.isDirectory());

            return info;

        } catch (com.github.sardine.impl.SardineException e) {
            if (e.getStatusCode() == 404) {
                return null; // 文件不存在
            }
            logger.error("[NutstoreProvider] 获取文件信息失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("[NutstoreProvider] 获取文件信息失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getProviderName() {
        return "nutstore";
    }

    @Override
    public String getSyncRootUrl() {
        return syncRootUrl;
    }
}

