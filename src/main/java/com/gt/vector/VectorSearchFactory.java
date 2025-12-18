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
 * vector.provider=none | lucene
 */
public class VectorSearchFactory {

    private static VectorSearchService instance;
    private static LuceneVectorSearchService luceneService;

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
     * 获取 Lucene 向量检索服务（用于 H1 级别索引）
     */
    public static synchronized LuceneVectorSearchService getLuceneService() {
        if (luceneService == null) {
            Properties config = loadConfig();
            String providerName = config.getProperty("vector.provider", "none").toLowerCase().trim();
            
            if ("lucene".equals(providerName)) {
                luceneService = new LuceneVectorSearchService();
                luceneService.initialize();
            }
        }
        return luceneService;
    }

    /**
     * 重新创建服务（用于配置变更后刷新）
     */
    public static synchronized void refresh() {
        if (luceneService != null) {
            luceneService.close();
            luceneService = null;
        }
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
            case "lucene":
                LuceneVectorSearchService service = new LuceneVectorSearchService();
                service.initialize();
                if (service.isAvailable()) {
                    luceneService = service;
                    return service;
                } else {
                    System.err.println("[VectorSearchFactory] Lucene 初始化失败: " + service.getErrorMessage());
                    return NoOpVectorSearchService.getInstance();
                }

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

