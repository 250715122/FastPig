package com.gt.vector;

import com.gt.NoteDto;
import com.gt.kb.model.KnowledgeBlock;

import java.util.List;

/**
 * 向量检索服务接口，预留 sqlite-vec 等其他实现。
 *
 * 索引单位是块：一篇笔记落成若干索引文档，doc_id 即块 ID。
 */
public interface VectorSearchService {

    /**
     * 差量索引一篇笔记：只重算内容或分类变化的块，删掉已消失的块。
     *
     * @param note   笔记元数据，提供分类过滤字段
     * @param blocks 索引文档列表，由 {@link com.gt.kb.chunk.IndexBlocks#of} 产出
     */
    void indexNote(NoteDto note, List<KnowledgeBlock> blocks);

    /**
     * 删除笔记的全部索引
     */
    void removeIndex(String noteKey);

    /**
     * 检查服务是否可用
     */
    boolean isAvailable();

    /**
     * 获取服务实现名称
     */
    String getServiceName();

    /**
     * 清空所有索引
     */
    void clearAll();

    /**
     * 已索引的文档数（块级，不是笔记数）
     */
    int getIndexedCount();
}