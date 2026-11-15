package com.gt.kb.chunk;

import com.gt.kb.model.KnowledgeBlock;

import java.util.List;

/** 把一篇笔记正文切成知识块。 */
public interface Chunker {

    List<KnowledgeBlock> chunk(String noteKey, String noteDesc, String body, ChunkProfile profile);
}