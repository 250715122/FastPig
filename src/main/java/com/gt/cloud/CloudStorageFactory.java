package com.gt.cloud;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 云存储工厂类
 * 根据配置文件创建对应的云存储提供者
 * 
 * 配置示例 (config.properties):
 * cloud.provider=nutstore | aliyun | tencent | local
 */
public class CloudStorageFactory {
    
    private static final Logger logger = LogManager.getLogger(CloudStorageFactory.class);

    private static CloudStorageProvider instance;

    /**
     * 获取云存储提供者实例（单例）
     * @return CloudStorageProvider 实例
     */
    public static synchronized CloudStorageProvider getProvider() {
        if (instance == null) {
            instance = createProvider();
        }
        return instance;
    }

    /**
     * 重新创建提供者（用于配置变更后刷新）
     */
    public static synchronized void refresh() {
        instance = null;
    }

    /**
     * 根据配置创建云存储提供者
     */
    private static CloudStorageProvider createProvider() {
        Properties config = loadConfig();
        String providerName = config.getProperty("cloud.provider", "nutstore").toLowerCase().trim();

        logger.info("云存储提供者: {}", providerName);

        switch (providerName) {
            case "nutstore":
                return NutstoreCloudProvider.getInstance();

            case "aliyun":
                // TODO: 实现阿里云 OSS 提供者
                logger.warn("阿里云 OSS 暂未实现，回退到坚果云");
                return NutstoreCloudProvider.getInstance();

            case "tencent":
                // TODO: 实现腾讯云 COS 提供者
                logger.warn("腾讯云 COS 暂未实现，回退到坚果云");
                return NutstoreCloudProvider.getInstance();

            case "local":
                // TODO: 实现本地备份提供者
                logger.warn("本地备份暂未实现，回退到坚果云");
                return NutstoreCloudProvider.getInstance();

            case "none":
            case "disabled":
                return new DisabledCloudProvider();

            default:
                logger.warn("未知提供者: {}，使用坚果云", providerName);
                return NutstoreCloudProvider.getInstance();
        }
    }

    /**
     * 加载配置文件
     */
    private static Properties loadConfig() {
        Properties props = new Properties();

        // 尝试从程序所在目录读取
        Path configFile = Paths.get(System.getProperty("user.dir"), "config.properties");
        if (Files.exists(configFile)) {
            try (InputStream input = new FileInputStream(configFile.toFile())) {
                props.load(input);
                return props;
            } catch (Exception e) {
                logger.error("读取配置文件失败: {}", e.getMessage());
            }
        }

        // 尝试从 classpath 读取
        try (InputStream input = CloudStorageFactory.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (Exception e) {
            logger.error("读取配置文件失败: {}", e.getMessage());
        }

        return props;
    }

    /**
     * 禁用的云存储提供者（空实现）
     */
    private static class DisabledCloudProvider implements CloudStorageProvider {

        @Override
        public boolean upload(String remotePath, byte[] data) {
            return false;
        }

        @Override
        public byte[] download(String remotePath) {
            return null;
        }

        @Override
        public boolean delete(String remotePath) {
            return false;
        }

        @Override
        public boolean exists(String remotePath) {
            return false;
        }

        @Override
        public java.util.List<CloudFileInfo> listFiles(String remoteDir) {
            return new java.util.ArrayList<>();
        }

        @Override
        public java.util.List<CloudFileInfo> listFilesRecursive(String remoteDir) {
            return new java.util.ArrayList<>();
        }

        @Override
        public boolean createDirectory(String remotePath) {
            return false;
        }

        @Override
        public CloudFileInfo getFileInfo(String remotePath) {
            return null;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public String getProviderName() {
            return "disabled";
        }

        @Override
        public String getSyncRootUrl() {
            return "";
        }
    }
}

