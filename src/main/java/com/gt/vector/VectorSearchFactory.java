package com.gt.vector;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 向量检索服务工厂
 * 根据配置创建对应的向量检索实现
 * 
 * 配置示例 (config.properties):
 * vector.provider=none | sqlite-vec | lucene
 */
public class VectorSearchFactory {

    private static VectorSearchService instance;

    /**
     * 获取向量检索服务实例（单例）
     */
    public static synchronized VectorSearchService getService() {
        if (instance == null) {
            instance = createService();
        }
        return instance;
    }

    /**
     * 重新创建服务（用于配置变更后刷新）
     */
    public static synchronized void refresh() {
        instance = null;
    }

    /**
     * 根据配置创建向量检索服务
     */
    private static VectorSearchService createService() {
        Properties config = loadConfig();
        String providerName = config.getProperty("vector.provider", "none").toLowerCase().trim();

        System.out.println("[VectorSearchFactory] 向量检索提供者: " + providerName);

        switch (providerName) {
            case "sqlite-vec":
                // TODO: 实现 sqlite-vec 提供者
                System.out.println("[VectorSearchFactory] sqlite-vec 暂未实现，使用空实现");
                return NoOpVectorSearchService.getInstance();

            case "lucene":
                // TODO: 实现 Lucene 提供者
                System.out.println("[VectorSearchFactory] Lucene 暂未实现，使用空实现");
                return NoOpVectorSearchService.getInstance();

            case "none":
            case "disabled":
            default:
                return NoOpVectorSearchService.getInstance();
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
                System.err.println("[VectorSearchFactory] 读取配置文件失败: " + e.getMessage());
            }
        }

        // 尝试从 classpath 读取
        try (InputStream input = VectorSearchFactory.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (Exception e) {
            System.err.println("[VectorSearchFactory] 读取配置文件失败: " + e.getMessage());
        }

        return props;
    }
}

