package com.gt.kb.chunk;

import com.gt.AppConfig;
import com.gt.NoteDto;
import com.gt.config.ConfigKeys;

/**
 * 切分策略与阈值。日志类/原始层优先按日期切，其余按标题切。
 *
 * 阈值只影响索引块粒度，不会改写笔记原文——二次切分出的子块不写 ID 到文件。
 */
public record ChunkProfile(boolean dateFirst, int maxChars, int targetChars, int overlapChars, int minChars) {

    public ChunkProfile {
        maxChars = Math.max(1, maxChars);
        targetChars = Math.min(Math.max(1, targetChars), maxChars);
        // 重叠必须小于目标长度，否则段落聚合无法前进
        overlapChars = Math.max(0, Math.min(overlapChars, targetChars / 2));
        minChars = Math.max(0, minChars);
    }

    public static ChunkProfile forNote(String noteType, String layer) {
        boolean dateFirst = NoteDto.TYPE_LOG.equals(noteType) || NoteDto.LAYER_RAW.equals(layer);
        AppConfig cfg = AppConfig.getInstance();
        return new ChunkProfile(dateFirst,
                cfg.getInt(ConfigKeys.KB_CHUNK_MAX_CHARS),
                cfg.getInt(ConfigKeys.KB_CHUNK_TARGET_CHARS),
                cfg.getInt(ConfigKeys.KB_CHUNK_OVERLAP_CHARS),
                cfg.getInt(ConfigKeys.KB_CHUNK_MIN_CHARS));
    }
}