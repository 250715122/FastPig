package com.gt.vector;

import com.gt.NoteDto;
import com.gt.kb.model.KnowledgeBlock;

import java.util.List;

/**
 * 空实现的向量检索服务
 * 当向量检索功能未启用时使用
 */
public class NoOpVectorSearchService implements VectorSearchService {

    private static NoOpVectorSearchService instance;

    public static synchronized NoOpVectorSearchService getInstance() {
        if (instance == null) {
            instance = new NoOpVectorSearchService();
        }
        return instance;
    }

    private NoOpVectorSearchService() {
        System.out.println("[VectorSearch] 向量检索未启用，使用空实现");
    }

    @Override
    public void indexNote(NoteDto note, List<KnowledgeBlock> blocks) {
        // 空实现
    }

    @Override
    public void removeIndex(String noteKey) {
        // 空实现
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getServiceName() {
        return "noop";
    }

    @Override
    public void clearAll() {
        // 空实现
    }

    @Override
    public int getIndexedCount() {
        return 0;
    }
}