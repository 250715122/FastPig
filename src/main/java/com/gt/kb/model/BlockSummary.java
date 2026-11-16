package com.gt.kb.model;

/**
 * 块目录里的一行元数据，不含正文。
 *
 * 树形目录展开笔记时用它列出块节点：读派生表即可，不必重新切分文件。
 * startLine/endLine 相对「去掉 front matter 后的正文」，行号从 1 起。
 */
public record BlockSummary(String blockId, String noteKey, String title, int level, int startLine, int endLine) {
}