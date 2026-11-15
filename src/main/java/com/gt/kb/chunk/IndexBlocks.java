package com.gt.kb.chunk;

import com.gt.NoteDto;
import com.gt.kb.model.BlockMeta;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.text.IndexTextBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一篇笔记进入向量索引的全部文档：笔记名文档 + {@link DefaultChunker#chunkForIndex} 切出的索引块。
 *
 * 笔记名文档不是知识块，id 形如 {@code mysql#note}，所属 block_id 为空，命中它等同命中整篇笔记。
 * 保留它是因为向量通道需要一篇以 key 本身为正文的文档：直接输入 mysql 时，
 * 正文里只有中文描述的块很难被召回，笔记级检索会掉。
 */
public final class IndexBlocks {

    public static final String NOTE_SUFFIX = "#note";

    private IndexBlocks() {
    }

    /** 私密笔记和已删除笔记返回空列表，调用方据此把旧索引整体删掉。 */
    public static List<KnowledgeBlock> of(NoteDto note) {
        List<KnowledgeBlock> out = new ArrayList<>();
        if (note == null || note.key == null || note.key.isEmpty() || note.deleted || note.encrypted) {
            return out;
        }
        out.add(new KnowledgeBlock(note.key + NOTE_SUFFIX, note.key, note.key, 0,
                List.of(), "", 1, 1, null, BlockMeta.EMPTY));

        String desc = descOf(note);
        ChunkProfile profile = ChunkProfile.forNote(note.noteType, note.layer);
        DefaultChunker chunker = new DefaultChunker();
        Map<String, KnowledgeBlock> parents = new HashMap<>();
        for (KnowledgeBlock block : chunker.chunk(note.key, desc, note.bodyMd, profile)) {
            parents.put(block.id(), block);
        }
        for (KnowledgeBlock block : chunker.chunkForIndex(note.key, desc, note.bodyMd, profile)) {
            KnowledgeBlock doc = alignToParent(block, parents);
            if (!IndexTextBuilder.build(doc).isEmpty()) {
                out.add(doc);
            }
        }
        return out;
    }

    /**
     * 索引块的面包屑改挂所属知识块，末位即知识块标题；块自身那级小标题留在 title 里。
     *
     * 二次切分会把一个小标题拆成十几段，若面包屑停在段标题上，命中后就看不出属于哪个知识块，
     * 归并和展示都会错位。小标题仍进索引文本（title 参与），召回信号不丢。
     */
    private static KnowledgeBlock alignToParent(KnowledgeBlock block, Map<String, KnowledgeBlock> parents) {
        if (!block.isIndexBlock()) {
            return block;
        }
        KnowledgeBlock parent = parents.get(block.parentBlockId());
        if (parent == null || parent.path().equals(block.path())) {
            return block;
        }
        return new KnowledgeBlock(block.id(), block.noteKey(), block.title(), block.level(),
                parent.path(), block.body(), block.startLine(), block.endLine(), null, block.meta());
    }

    public static boolean isNoteDoc(KnowledgeBlock block) {
        return block.id().endsWith(NOTE_SUFFIX);
    }

    /** 文档所属知识块的标题：索引块取面包屑末位，知识块就是自身标题。 */
    public static String blockTitleOf(KnowledgeBlock block) {
        List<String> path = block.path();
        return block.isIndexBlock() && !path.isEmpty() ? path.get(path.size() - 1) : block.title();
    }

    public static String descOf(NoteDto note) {
        return note.desc == null || note.desc.isBlank() ? note.key : note.desc;
    }
}