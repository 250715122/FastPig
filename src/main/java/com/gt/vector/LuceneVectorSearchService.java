package com.gt.vector;

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
    
    private static final String INDEX_DIR = "vector_index";
    private static final int VECTOR_DIM = 512;
    private static final int HNSW_MAX_CONN = 16;  // HNSW M 参数
    private static final int HNSW_BEAM_WIDTH = 100;  // HNSW efConstruction
    
    // 字段名
    private static final String FIELD_ID = "id";
    private static final String FIELD_NOTE_KEY = "noteKey";
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
                System.err.println("[LuceneVectorSearch] " + errorMessage);
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
            System.out.println("[LuceneVectorSearch] 索引初始化完成，索引目录: " + indexPath);
            System.out.println("[LuceneVectorSearch] 当前索引数量: " + getIndexedCount());
            
        } catch (Exception e) {
            errorMessage = "索引初始化失败: " + e.getMessage();
            System.err.println("[LuceneVectorSearch] " + errorMessage);
            e.printStackTrace();
        }
    }
    
    /**
     * 索引单个 H1 块
     * @param noteKey 笔记 key
     * @param h1Title H1 标题
     * @param content 索引内容（文件名 + H1 + 正文前 300 字）
     */
    public void indexH1(String noteKey, String h1Title, String content) {
        if (!available) {
            System.err.println("[LuceneVectorSearch] 服务不可用");
            return;
        }
        
        lock.writeLock().lock();
        try {
            String docId = buildDocId(noteKey, h1Title);
            
            // 生成向量
            float[] vector = embeddingService.embed(content);
            if (vector == null) {
                System.err.println("[LuceneVectorSearch] 向量生成失败: " + docId);
                return;
            }
            
            // 先删除旧文档
            writer.deleteDocuments(new Term(FIELD_ID, docId));
            
            // 创建新文档
            Document doc = new Document();
            doc.add(new StringField(FIELD_ID, docId, Field.Store.YES));
            doc.add(new StringField(FIELD_NOTE_KEY, noteKey, Field.Store.YES));
            doc.add(new StoredField(FIELD_H1_TITLE, h1Title));
            doc.add(new StoredField(FIELD_CONTENT, truncate(content, 200))); // 存储截断后的内容用于显示
            doc.add(new KnnFloatVectorField(FIELD_VECTOR, vector, VectorSimilarityFunction.COSINE));
            
            writer.addDocument(doc);
            writer.commit();
            
            // 刷新 reader
            refreshReader();
            
            System.out.println("[LuceneVectorSearch] 索引添加: " + docId);
            
        } catch (Exception e) {
            System.err.println("[LuceneVectorSearch] 索引失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 批量索引 H1 块（用于全量重建）
     */
    public void indexH1Batch(List<H1Block> blocks) {
        if (!available || blocks.isEmpty()) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            int count = 0;
            for (H1Block block : blocks) {
                String docId = buildDocId(block.noteKey, block.h1Title);
                
                // 生成向量
                float[] vector = embeddingService.embed(block.indexContent);
                if (vector == null) {
                    continue;
                }
                
                // 创建文档
                Document doc = new Document();
                doc.add(new StringField(FIELD_ID, docId, Field.Store.YES));
                doc.add(new StringField(FIELD_NOTE_KEY, block.noteKey, Field.Store.YES));
                doc.add(new StoredField(FIELD_H1_TITLE, block.h1Title));
                doc.add(new StoredField(FIELD_CONTENT, truncate(block.indexContent, 200)));
                doc.add(new KnnFloatVectorField(FIELD_VECTOR, vector, VectorSimilarityFunction.COSINE));
                
                writer.addDocument(doc);
                count++;
                
                // 每 100 条 commit 一次
                if (count % 100 == 0) {
                    writer.commit();
                    System.out.println("[LuceneVectorSearch] 已索引 " + count + " 条");
                }
            }
            
            writer.commit();
            refreshReader();
            
            System.out.println("[LuceneVectorSearch] 批量索引完成，共 " + count + " 条");
            
        } catch (Exception e) {
            System.err.println("[LuceneVectorSearch] 批量索引失败: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("[LuceneVectorSearch] 已移除笔记索引: " + noteKey);
        } catch (Exception e) {
            System.err.println("[LuceneVectorSearch] 移除索引失败: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 语义搜索
     * @param query 查询文本
     * @param topK 返回数量
     * @return 搜索结果列表（包含 noteKey、h1Title、score）
     */
    public List<VectorSearchResult> search(String query, int topK) {
        List<VectorSearchResult> results = new ArrayList<>();
        
        if (!available || searcher == null) {
            return results;
        }
        
        lock.readLock().lock();
        try {
            // 查询文本转向量
            float[] queryVector = embeddingService.embed(query);
            if (queryVector == null) {
                return results;
            }
            
            // KNN 查询
            KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, topK);
            TopDocs topDocs = searcher.search(knnQuery, topK);
            
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                
                VectorSearchResult result = new VectorSearchResult();
                result.noteKey = doc.get(FIELD_NOTE_KEY);
                result.h1Title = doc.get(FIELD_H1_TITLE);
                result.content = doc.get(FIELD_CONTENT);
                result.score = scoreDoc.score;
                
                results.add(result);
            }
            
        } catch (Exception e) {
            System.err.println("[LuceneVectorSearch] 搜索失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.readLock().unlock();
        }
        
        return results;
    }
    
    // ==================== VectorSearchService 接口实现 ====================
    
    @Override
    public void indexNote(String noteId, String content) {
        // 兼容旧接口，直接使用 noteId 作为标题
        indexH1(noteId, "", content);
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
            System.out.println("[LuceneVectorSearch] 已清空所有索引");
        } catch (Exception e) {
            System.err.println("[LuceneVectorSearch] 清空索引失败: " + e.getMessage());
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
        public String noteKey;
        public String h1Title;
        public String indexContent;
        
        public H1Block() {}
        
        public H1Block(String noteKey, String h1Title, String indexContent) {
            this.noteKey = noteKey;
            this.h1Title = h1Title;
            this.indexContent = indexContent;
        }
    }
    
    /**
     * 向量搜索结果
     */
    public static class VectorSearchResult {
        public String noteKey;
        public String h1Title;
        public String content;
        public float score;
        
        @Override
        public String toString() {
            return String.format("[%.3f] %s # %s", score, noteKey, h1Title);
        }
    }
}

