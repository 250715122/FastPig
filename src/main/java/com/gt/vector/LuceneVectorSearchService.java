package com.gt.vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于 Lucene 9.x KNN (HNSW) 的向量检索服务
 * 
 * 索引结构：
 * - id: noteKey#h1Title（唯一标识）
 * - noteKey: 笔记 key
 * - h1Title: H1 标题
 * - content: 索引内容（用于显示）
 * - vector: 512 维向量（KNN 检索用）
 */
public class LuceneVectorSearchService implements VectorSearchService {
    
    private static final Logger logger = LogManager.getLogger(LuceneVectorSearchService.class);
    
    private static final String INDEX_DIR = "vector_index";
    private static final int VECTOR_DIM = 512;
    private static final int HNSW_MAX_CONN = 16;  // HNSW M 参数
    private static final int HNSW_BEAM_WIDTH = 100;  // HNSW efConstruction
    
    // 字段名
    private static final String FIELD_ID = "id";
    private static final String FIELD_NOTE_KEY = "noteKey";
    private static final String FIELD_NOTE_DESC = "noteDesc";
    private static final String FIELD_H1_TITLE = "h1Title";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_VECTOR = "vector";
    
    private final EmbeddingService embeddingService;
    private Directory directory;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private boolean available = false;
    private String errorMessage;
    
    public LuceneVectorSearchService() {
        this.embeddingService = EmbeddingService.getInstance();
    }
    
    /**
     * 初始化服务
     */
    public synchronized void initialize() {
        if (available) {
            return;
        }
        
        try {
            // 确保 embedding 服务可用
            if (!embeddingService.isAvailable()) {
                embeddingService.initialize();
            }
            
            if (!embeddingService.isAvailable()) {
                errorMessage = "Embedding 服务不可用: " + embeddingService.getErrorMessage();
                logger.error(errorMessage);
                return;
            }
            
            // 创建索引目录
            Path indexPath = Paths.get(System.getProperty("user.dir"), INDEX_DIR);
            Files.createDirectories(indexPath);
            
            // 打开 Lucene 目录
            directory = FSDirectory.open(indexPath);
            
            // 配置 IndexWriter
            IndexWriterConfig config = new IndexWriterConfig();
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setRAMBufferSizeMB(64.0);
            
            writer = new IndexWriter(directory, config);
            
            // 刷新 reader
            refreshReader();
            
            available = true;
            logger.info("索引初始化完成，索引目录: {}", indexPath);
            logger.info("当前索引数量: {}", getIndexedCount());
            
        } catch (Exception e) {
            errorMessage = "索引初始化失败: " + e.getMessage();
            logger.error(errorMessage, e);
        }
    }
    
    /**
     * 索引单个 H1 块
     * @param noteKey 笔记 key（快捷命令）
     * @param noteDesc 笔记描述
     * @param h1Title H1 标题
     * @param content 索引内容（描述 + H1 + 正文前 300 字）
     */
    public void indexH1(String noteKey, String noteDesc, String h1Title, String content) {
        if (!available) {
            logger.error("服务不可用");
            return;
        }
        
        String docId = buildDocId(noteKey, h1Title);
        
        // 调试：打印索引内容
        logger.debug("索引内容: noteKey={}, h1Title={}, content={}", noteKey, h1Title, content);
        
        // 生成向量（在锁外执行，不阻塞搜索）
        float[] vector = embeddingService.embed(content);
        if (vector == null) {
            logger.error("向量生成失败: {}", docId);
            return;
        }
        
        lock.writeLock().lock();
        try {
            // 先删除旧文档
            writer.deleteDocuments(new Term(FIELD_ID, docId));
            
            // 创建新文档
            Document doc = new Document();
            doc.add(new StringField(FIELD_ID, docId, Field.Store.YES));
            doc.add(new StringField(FIELD_NOTE_KEY, noteKey, Field.Store.YES));
            doc.add(new StoredField(FIELD_NOTE_DESC, noteDesc != null ? noteDesc : ""));
            doc.add(new StoredField(FIELD_H1_TITLE, h1Title));
            doc.add(new StoredField(FIELD_CONTENT, truncate(content, 200))); // 存储截断后的内容用于显示
            doc.add(new KnnFloatVectorField(FIELD_VECTOR, vector, VectorSimilarityFunction.COSINE));
            
            writer.addDocument(doc);
            writer.commit();
            
            // 刷新 reader
            refreshReader();
            
            logger.debug("索引添加: {}", docId);
            
        } catch (Exception e) {
            logger.error("索引失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 批量索引 H1 块（用于全量重建）
     * 注意：embed 在锁外执行，不阻塞搜索
     */
    public void indexH1Batch(List<H1Block> blocks) {
        if (!available || blocks.isEmpty()) {
            return;
        }
        
        // 在锁外批量生成向量
        List<H1BlockWithVector> blocksWithVectors = new ArrayList<>();
        for (H1Block block : blocks) {
            float[] vector = embeddingService.embed(block.indexContent);
            if (vector != null) {
                blocksWithVectors.add(new H1BlockWithVector(
                    block.noteKey, block.noteDesc, block.h1Title, block.indexContent, vector));
            }
        }
        
        if (blocksWithVectors.isEmpty()) {
            return;
        }
        
        // 在锁内批量写入
        batchWriteWithVectors(null, blocksWithVectors);
    }
    
    /**
     * 批量写入已计算好向量的 H1 块
     * 在锁内执行：先删除指定笔记的旧索引，再批量写入，最后统一 commit + refreshReader 一次
     * 
     * @param noteKeysToRemove 需要先删除旧索引的笔记 key 集合（可为 null）
     * @param blocks 已计算好向量的 H1 块列表
     */
    public void batchWriteWithVectors(Set<String> noteKeysToRemove, List<H1BlockWithVector> blocks) {
        if (!available || blocks.isEmpty()) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            // 先删除指定笔记的旧索引
            if (noteKeysToRemove != null) {
                for (String noteKey : noteKeysToRemove) {
                    writer.deleteDocuments(new Term(FIELD_NOTE_KEY, noteKey));
                }
            }
            
            // 批量写入
            int count = 0;
            for (H1BlockWithVector block : blocks) {
                Document doc = new Document();
                doc.add(new StringField(FIELD_ID, buildDocId(block.noteKey, block.h1Title), Field.Store.YES));
                doc.add(new StringField(FIELD_NOTE_KEY, block.noteKey, Field.Store.YES));
                doc.add(new StoredField(FIELD_NOTE_DESC, block.noteDesc != null ? block.noteDesc : ""));
                doc.add(new StoredField(FIELD_H1_TITLE, block.h1Title));
                doc.add(new StoredField(FIELD_CONTENT, truncate(block.indexContent, 200)));
                doc.add(new KnnFloatVectorField(FIELD_VECTOR, block.vector, VectorSimilarityFunction.COSINE));
                
                writer.addDocument(doc);
                count++;
                
                // 每 100 条 commit 一次（减少内存压力）
                if (count % 100 == 0) {
                    writer.commit();
                    logger.debug("已批量写入 {} 条", count);
                }
            }
            
            writer.commit();
            refreshReader();
            
            logger.info("批量索引写入完成，共 {} 条", count);
            
        } catch (Exception e) {
            logger.error("批量索引写入失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 移除笔记的所有索引
     */
    public void removeNoteIndex(String noteKey) {
        if (!available) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            writer.deleteDocuments(new Term(FIELD_NOTE_KEY, noteKey));
            writer.commit();
            refreshReader();
            logger.debug("已移除笔记索引: {}", noteKey);
        } catch (Exception e) {
            logger.error("移除索引失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 查询指定笔记已索引的所有 H1 标题
     * 用于差量索引：保存时对比新旧标题集合，只对变化的块进行 embed + 写入
     * 
     * @param noteKey 笔记 key
     * @return 已索引的 H1 标题集合（包含 __NOTE_NAME__ 等特殊标记）
     */
    public Set<String> getIndexedH1Titles(String noteKey) {
        Set<String> titles = new HashSet<>();
        if (!available || searcher == null) {
            return titles;
        }
        
        lock.readLock().lock();
        try {
            TermQuery query = new TermQuery(new Term(FIELD_NOTE_KEY, noteKey));
            TopDocs topDocs = searcher.search(query, 1000); // 单个笔记最多 1000 个 H1
            
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String h1Title = doc.get(FIELD_H1_TITLE);
                if (h1Title != null) {
                    titles.add(h1Title);
                }
            }
        } catch (Exception e) {
            logger.error("查询已索引 H1 失败: {}", e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
        
        return titles;
    }
    
    /**
     * 差量更新笔记索引
     * 在写锁内执行：按 docId 删除指定 H1 块，批量写入新块，单次 commit + refreshReader
     * 
     * @param noteKey 笔记 key（用于构建 docId）
     * @param h1TitlesToRemove 需要删除的 H1 标题集合
     * @param blocksToAdd 需要新增的已计算向量的块列表
     */
    public void differentialUpdate(String noteKey, Set<String> h1TitlesToRemove, List<H1BlockWithVector> blocksToAdd) {
        if (!available) {
            return;
        }
        
        // 没有任何变更，跳过
        if ((h1TitlesToRemove == null || h1TitlesToRemove.isEmpty()) 
                && (blocksToAdd == null || blocksToAdd.isEmpty())) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            // 删除指定的 H1 块（按 docId = noteKey#h1Title）
            if (h1TitlesToRemove != null) {
                for (String h1Title : h1TitlesToRemove) {
                    String docId = buildDocId(noteKey, h1Title);
                    writer.deleteDocuments(new Term(FIELD_ID, docId));
                }
            }
            
            // 写入新块
            if (blocksToAdd != null) {
                for (H1BlockWithVector block : blocksToAdd) {
                    Document doc = new Document();
                    doc.add(new StringField(FIELD_ID, buildDocId(block.noteKey, block.h1Title), Field.Store.YES));
                    doc.add(new StringField(FIELD_NOTE_KEY, block.noteKey, Field.Store.YES));
                    doc.add(new StoredField(FIELD_NOTE_DESC, block.noteDesc != null ? block.noteDesc : ""));
                    doc.add(new StoredField(FIELD_H1_TITLE, block.h1Title));
                    doc.add(new StoredField(FIELD_CONTENT, truncate(block.indexContent, 200)));
                    doc.add(new KnnFloatVectorField(FIELD_VECTOR, block.vector, VectorSimilarityFunction.COSINE));
                    
                    writer.addDocument(doc);
                }
            }
            
            writer.commit();
            refreshReader();
            
            int removedCount = (h1TitlesToRemove != null) ? h1TitlesToRemove.size() : 0;
            int addedCount = (blocksToAdd != null) ? blocksToAdd.size() : 0;
            logger.info("差量索引更新: noteKey={}, 删除={}, 新增={}", noteKey, removedCount, addedCount);
            
        } catch (Exception e) {
            logger.error("差量索引更新失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 语义搜索
     * @param query 查询文本
     * @param topK 返回数量
     * @return 搜索结果列表（包含 noteKey、h1Title、score、keywordScore、totalScore）
     */
    public List<VectorSearchResult> search(String query, int topK) {
        List<VectorSearchResult> results = new ArrayList<>();
        
        if (!available || searcher == null) {
            return results;
        }
        
        lock.readLock().lock();
        try {
            // 调试：打印查询内容和索引数量
            logger.debug("搜索查询: {}", query);
            logger.debug("当前索引数量: {}", getIndexedCount());
            
            // 查询文本转向量
            float[] queryVector = embeddingService.embed(query);
            if (queryVector == null) {
                logger.error("查询向量生成失败");
                return results;
            }
            
            // KNN 查询
            KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, topK);
            TopDocs topDocs = searcher.search(knnQuery, topK);
            
            logger.debug("搜索结果数: {}", topDocs.scoreDocs.length);
            
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                
                VectorSearchResult result = new VectorSearchResult();
                result.noteKey = doc.get(FIELD_NOTE_KEY);
                result.noteDesc = doc.get(FIELD_NOTE_DESC);
                result.h1Title = doc.get(FIELD_H1_TITLE);
                result.content = doc.get(FIELD_CONTENT);
                result.score = scoreDoc.score;
                
                // 计算关键词匹配度（上限 0.2）
                // 使用 h1Title + noteKey + noteDesc 构建匹配内容，避免旧索引 content 为空的问题
                String matchContent = buildMatchContent(result.h1Title, result.noteKey, result.noteDesc);
                result.keywordScore = calculateKeywordScore(query, matchContent);
                result.totalScore = result.score + result.keywordScore;
                
                // 调试：打印每个结果
                logger.debug("结果: noteKey={}, h1Title={}, V={}, K={}, T={}", 
                    result.noteKey, result.h1Title, 
                    String.format("%.2f", result.score),
                    String.format("%.2f", result.keywordScore),
                    String.format("%.2f", result.totalScore));
                
                results.add(result);
            }
            
            // 按 totalScore 降序排序
            results.sort((a, b) -> Float.compare(b.totalScore, a.totalScore));
            
        } catch (Exception e) {
            logger.error("搜索失败: {}", e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
        
        return results;
    }
    
    /**
     * 构建关键词匹配内容
     * 合并 h1Title、noteKey、noteDesc 用于关键词匹配
     */
    private String buildMatchContent(String h1Title, String noteKey, String noteDesc) {
        StringBuilder sb = new StringBuilder();
        if (h1Title != null && !h1Title.isEmpty()) {
            sb.append(h1Title);
        }
        if (noteKey != null && !noteKey.isEmpty()) {
            sb.append(" ").append(noteKey);
        }
        if (noteDesc != null && !noteDesc.isEmpty()) {
            sb.append(" ").append(noteDesc);
        }
        return sb.toString();
    }
    
    /**
     * 计算关键词匹配度
     * 基于字符重叠率，上限 0.2
     * @param query 查询文本
     * @param content 索引内容
     * @return 关键词匹配分数（0~0.2）
     */
    private float calculateKeywordScore(String query, String content) {
        if (query == null || query.isEmpty() || content == null || content.isEmpty()) {
            return 0f;
        }
        
        // 转小写以忽略大小写
        String q = query.toLowerCase();
        String c = content.toLowerCase();
        
        // 计算查询中有多少字符在内容中出现
        int matchCount = 0;
        for (int i = 0; i < q.length(); i++) {
            char ch = q.charAt(i);
            if (c.indexOf(ch) >= 0) {
                matchCount++;
            }
        }
        
        // 匹配率
        float matchRate = (float) matchCount / q.length();
        
        // 额外加分：完全包含查询词
        if (c.contains(q)) {
            matchRate = 1.0f;
        }
        
        // 上限 0.2
        return Math.min(matchRate * 0.2f, 0.2f);
    }
    
    // ==================== VectorSearchService 接口实现 ====================
    
    @Override
    public void indexNote(String noteId, String content) {
        // 兼容旧接口，noteDesc 和 h1Title 都使用空字符串
        indexH1(noteId, "", "", content);
    }
    
    @Override
    public void indexNotes(List<String> noteIds, List<String> contents) {
        for (int i = 0; i < noteIds.size(); i++) {
            indexNote(noteIds.get(i), contents.get(i));
        }
    }
    
    @Override
    public void removeIndex(String noteId) {
        removeNoteIndex(noteId);
    }
    
    @Override
    public List<SearchResult> searchSimilar(String query, int topK) {
        List<SearchResult> results = new ArrayList<>();
        for (VectorSearchResult vsr : search(query, topK)) {
            SearchResult sr = new SearchResult(vsr.noteKey, vsr.score, vsr.content);
            results.add(sr);
        }
        return results;
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public String getServiceName() {
        return "Lucene KNN (HNSW)";
    }
    
    @Override
    public void clearAll() {
        if (!available) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            writer.deleteAll();
            writer.commit();
            refreshReader();
            logger.info("已清空所有索引");
        } catch (Exception e) {
            logger.error("清空索引失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public int getIndexedCount() {
        if (reader == null) {
            return 0;
        }
        return reader.numDocs();
    }
    
    /**
     * 获取错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 关闭服务
     */
    public synchronized void close() {
        lock.writeLock().lock();
        try {
            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (directory != null) {
                directory.close();
                directory = null;
            }
            available = false;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ==================== 私有方法 ====================
    
    private void refreshReader() throws IOException {
        if (reader == null) {
            reader = DirectoryReader.open(writer);
        } else {
            DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
            if (newReader != null) {
                reader.close();
                reader = newReader;
            }
        }
        searcher = new IndexSearcher(reader);
    }
    
    private String buildDocId(String noteKey, String h1Title) {
        return noteKey + "#" + h1Title;
    }
    
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
    
    // ==================== 内部类 ====================
    
    /**
     * H1 块数据
     */
    public static class H1Block {
        public String noteKey;    // 快捷命令
        public String noteDesc;   // 描述
        public String h1Title;    // H1 标题
        public String indexContent; // 索引内容
        
        public H1Block() {}
        
        public H1Block(String noteKey, String noteDesc, String h1Title, String indexContent) {
            this.noteKey = noteKey;
            this.noteDesc = noteDesc;
            this.h1Title = h1Title;
            this.indexContent = indexContent;
        }
    }
    
    /**
     * 已计算向量的 H1 块数据（用于批量写入）
     */
    public static class H1BlockWithVector {
        public String noteKey;
        public String noteDesc;
        public String h1Title;
        public String indexContent;
        public float[] vector;
        
        public H1BlockWithVector(String noteKey, String noteDesc, String h1Title, String indexContent, float[] vector) {
            this.noteKey = noteKey;
            this.noteDesc = noteDesc;
            this.h1Title = h1Title;
            this.indexContent = indexContent;
            this.vector = vector;
        }
    }
    
    /**
     * 向量搜索结果
     */
    public static class VectorSearchResult {
        public String noteKey;    // 快捷命令
        public String noteDesc;   // 描述
        public String h1Title;    // H1 标题
        public String content;    // 内容预览
        public float score;       // 向量相似度分数
        public float keywordScore; // 关键词匹配度（0~0.2）
        public float totalScore;  // 总相似度 = score + keywordScore
        
        @Override
        public String toString() {
            return String.format("[V:%.2f K:%.2f T:%.2f] %s (%s) # %s", 
                score, keywordScore, totalScore, noteKey, noteDesc, h1Title);
        }
    }
}

