package com.gt.kb.repo;

import java.nio.file.Path;

/**
 * 块在磁盘上的位置。行号从 1 起，口径与 {@code KnowledgeBlock.startLine/endLine} 一致，
 * 都是相对"去掉 front matter 后的正文"，因此可直接用于编辑器定位。
 */
public record BlockLocation(String noteKey, Path file, String blockId, int startLine, int endLine) {
}