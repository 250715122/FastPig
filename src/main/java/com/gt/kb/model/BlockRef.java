package com.gt.kb.model;

/** 指向某篇笔记中的某个块：noteKey 定位文件，blockId 定位块。 */
public record BlockRef(String noteKey, String blockId) {
}