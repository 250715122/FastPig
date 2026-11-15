package com.gt.kb.repo;

import com.gt.kb.model.KnowledgeBlock;

import java.util.List;
import java.util.Optional;

/**
 * 块仓库：把"笔记文件"这层存储抽象成"知识块"这层访问接口。
 *
 * 阶段 1 只有单文件实现（一篇笔记一个 note.md，块由切分器算出）；
 * 将来若改成一块一文件或数据库存储，调用方不需要跟着改。
 */
public interface BlockRepository {

    /** 该笔记的全部知识块（不含二次切分的索引块），按原文顺序。 */
    List<KnowledgeBlock> listBlocks(String noteKey);

    /** 按块 ID 取完整正文；索引块 ID（{@code ku_xxx#p2}）返回所属知识块。 */
    Optional<KnowledgeBlock> getBlock(String blockId);

    /** 按块 ID 定位到文件与行号，供编辑器跳转。 */
    Optional<BlockLocation> locate(String blockId);

    /**
     * 整篇正文（不含 front matter），供 GUI 显示。笔记不存在时返回空串。
     * 与 {@code NoteFileStorage.loadFromFile} 同口径：首尾空白已被 trim。
     */
    String loadNoteText(String noteKey);

    /**
     * 整篇正文写回，保留原 front matter 字节不变。
     *
     * 只负责落盘与块目录刷新；版本号、SQLite 索引、云同步、向量索引仍由
     * {@code NoteService.save} 统一协调，需要这些语义的调用方不要直接用本方法。
     * 正文首尾空白按 {@link #loadNoteText} 的口径规范化，与既有保存行为一致。
     */
    void saveNoteText(String noteKey, String text);
}