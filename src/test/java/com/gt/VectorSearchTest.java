package com.gt;

import com.gt.vector.EmbeddingService;
import com.gt.vector.LuceneVectorSearchService;
import com.gt.vector.LuceneVectorSearchService.VectorSearchResult;

import java.util.List;

/**
 * 向量检索测试
 * 测试索引和搜索功能是否正常工作
 */
public class VectorSearchTest {
    
    public static void main(String[] args) {
        System.out.println("=== 向量检索测试 ===\n");
        
        // 1. 初始化 EmbeddingService
        System.out.println("1. 初始化 EmbeddingService...");
        EmbeddingService embeddingService = EmbeddingService.getInstance();
        embeddingService.initialize();
        
        if (!embeddingService.isAvailable()) {
            System.err.println("EmbeddingService 不可用: " + embeddingService.getErrorMessage());
            System.err.println("请确保 models/ 目录下有 bge-small-zh-v1.5.onnx 和 vocab.txt");
            return;
        }
        System.out.println("EmbeddingService 初始化成功\n");
        
        // 2. 初始化 LuceneVectorSearchService
        System.out.println("2. 初始化 LuceneVectorSearchService...");
        LuceneVectorSearchService searchService = new LuceneVectorSearchService();
        searchService.initialize();
        
        if (!searchService.isAvailable()) {
            System.err.println("LuceneVectorSearchService 不可用: " + searchService.getErrorMessage());
            return;
        }
        System.out.println("LuceneVectorSearchService 初始化成功");
        System.out.println("当前索引数量: " + searchService.getIndexedCount() + "\n");
        
        // 3. 清空索引
        System.out.println("3. 清空现有索引...");
        searchService.clearAll();
        System.out.println("索引已清空，当前数量: " + searchService.getIndexedCount() + "\n");
        
        // 4. 添加测试文档
        System.out.println("4. 添加测试文档...");
        
        // 测试文档1：完全匹配的情况
        String doc1Content = "linux设置系统时区";
        searchService.indexH1("Linux", "linux设置系统时区", "设置系统时区", doc1Content);
        
        // 测试文档2：另一个文档
        String doc2Content = "java多线程编程";
        searchService.indexH1("Java", "java多线程编程", "多线程编程", doc2Content);
        
        // 测试文档3：相似但不同
        String doc3Content = "linux系统管理命令";
        searchService.indexH1("Linux2", "linux系统管理命令", "系统管理", doc3Content);
        
        System.out.println("测试文档添加完成，当前索引数量: " + searchService.getIndexedCount() + "\n");
        
        // 5. 测试搜索
        System.out.println("5. 测试搜索...\n");
        
        // 测试1：完全相同的查询
        testSearch(searchService, "linux设置系统时区", "完全相同查询");
        
        // 测试2：部分匹配
        testSearch(searchService, "设置时区", "部分匹配");
        
        // 测试3：语义相似
        testSearch(searchService, "如何修改linux的时区", "语义相似");
        
        // 测试4：不相关查询
        testSearch(searchService, "java多线程", "不相关查询");
        
        // 6. 测试向量相似度
        System.out.println("\n6. 测试向量相似度...");
        testVectorSimilarity(embeddingService, "linux设置系统时区", "linux设置系统时区");
        testVectorSimilarity(embeddingService, "linux设置系统时区", "设置时区");
        testVectorSimilarity(embeddingService, "linux设置系统时区", "java多线程");
        
        // 关闭服务
        searchService.close();
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testSearch(LuceneVectorSearchService service, String query, String testName) {
        System.out.println("--- " + testName + " ---");
        System.out.println("查询: " + query);
        
        List<VectorSearchResult> results = service.search(query, 5);
        
        if (results.isEmpty()) {
            System.out.println("结果: 无匹配");
        } else {
            System.out.println("结果:");
            for (int i = 0; i < results.size(); i++) {
                VectorSearchResult r = results.get(i);
                System.out.printf("  %d. [%.4f] %s - %s - %s%n", 
                    i + 1, r.score, r.noteKey, r.h1Title, r.content);
            }
        }
        System.out.println();
    }
    
    private static void testVectorSimilarity(EmbeddingService service, String text1, String text2) {
        float[] v1 = service.embed(text1);
        float[] v2 = service.embed(text2);
        
        if (v1 == null || v2 == null) {
            System.out.println("向量生成失败");
            return;
        }
        
        // 计算余弦相似度
        double similarity = cosineSimilarity(v1, v2);
        System.out.printf("相似度: \"%s\" vs \"%s\" = %.4f%n", text1, text2, similarity);
    }
    
    private static double cosineSimilarity(float[] v1, float[] v2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}

