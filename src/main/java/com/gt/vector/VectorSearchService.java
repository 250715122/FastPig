package com.gt.vector;

import java.util.List;

/**
 * 向量检索服务接口
 * 预留接口，后续可接入 sqlite-vec、Lucene 或其他向量数据库
 * 
 * 使用示例：
 * VectorSearchService vectorService = VectorSearchFactory.create();
 * vectorService.indexNote("note-id", "笔记内容...");
 * List<SearchResult> results = vectorService.searchSimilar("查询内容", 10);
 */
public interface VectorSearchService {

    /**
     * 索引单个笔记
     * @param noteId 笔记 ID
     * @param content 笔记内容（用于生成向量）
     */
    void indexNote(String noteId, String content);

    /**
     * 批量索引笔记
     * @param noteIds 笔记 ID 列表
     * @param contents 对应的内容列表
     */
    void indexNotes(List<String> noteIds, List<String> contents);

    /**
     * 删除笔记的向量索引
     * @param noteId 笔记 ID
     */
    void removeIndex(String noteId);

    /**
     * 相似度搜索
     * @param query 查询文本
     * @param topK 返回最相似的 K 个结果
     * @return 搜索结果列表
     */
    List<SearchResult> searchSimilar(String query, int topK);

    /**
     * 检查服务是否可用
     * @return true 如果服务已初始化且可用
     */
    boolean isAvailable();

    /**
     * 获取服务名称
     * @return 服务实现名称
     */
    String getServiceName();

    /**
     * 清空所有索引
     */
    void clearAll();

    /**
     * 获取已索引的笔记数量
     * @return 索引数量
     */
    int getIndexedCount();

    /**
     * 搜索结果
     */
    class SearchResult {
        private String noteId;
        private double score;
        private String snippet;

        public SearchResult() {}

        public SearchResult(String noteId, double score) {
            this.noteId = noteId;
            this.score = score;
        }

        public SearchResult(String noteId, double score, String snippet) {
            this.noteId = noteId;
            this.score = score;
            this.snippet = snippet;
        }

        public String getNoteId() {
            return noteId;
        }

        public void setNoteId(String noteId) {
            this.noteId = noteId;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        @Override
        public String toString() {
            return "SearchResult{noteId='" + noteId + "', score=" + score + "}";
        }
    }
}

