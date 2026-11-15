package com.gt.vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteH1ParserTest {

    private static final String NOTE = """
            # 内存调优

            先看整体占用。

            ## 堆外内存

            ```bash
            # 查看各目录占用
            du -sh /var/*
            ```

            ## 元空间

            调整 MetaspaceSize。
            """;

    @Test
    @DisplayName("按块 ID 定位到标题行")
    void findBlockPositionReturnsTitleLine() {
        String content = "前言\n\n# 堆外内存\n<!-- ku:aaaa2222 -->\n正文\n";
        int pos = NoteH1Parser.findBlockPosition(content, "ku_aaaa2222");
        assertEquals(content.indexOf("# 堆外内存"), pos);
    }

    @Test
    @DisplayName("无标题块的 ID 行本身就是块首")
    void findBlockPositionWithoutTitle() {
        String content = "<!-- ku:bbbb3333 -->\n1、启动服务\n正文\n";
        assertEquals(0, NoteH1Parser.findBlockPosition(content, "ku_bbbb3333"));
    }

    @Test
    @DisplayName("找不到块 ID 返回 -1")
    void findBlockPositionMissing() {
        assertEquals(-1, NoteH1Parser.findBlockPosition("# 标题\n正文\n", "ku_aaaa2222"));
        assertEquals(-1, NoteH1Parser.findBlockPosition(null, "ku_aaaa2222"));
    }

    @Test
    @DisplayName("按标题回退定位支持任意级别标题与整行标题")
    void findH1PositionMatchesAnyLevel() {
        String content = "# 一级\n\n## 二级\n\n1、编号标题\n";
        assertEquals(content.indexOf("## 二级"), NoteH1Parser.findH1Position(content, "二级"));
        assertEquals(content.indexOf("1、编号标题"), NoteH1Parser.findH1Position(content, "1、编号标题"));
        assertEquals(-1, NoteH1Parser.findH1Position(content, "不存在"));
    }

    @Test
    @DisplayName("预览分节与索引分块口径一致")
    void sectionRangeFollowsBlocks() {
        int caret = NOTE.indexOf("MetaspaceSize");
        int[] range = NoteH1Parser.findSectionRange(NOTE, caret);
        assertEquals(NOTE.indexOf("## 元空间"), range[0]);
        assertEquals(NOTE.length(), range[1]);

        int[] first = NoteH1Parser.findSectionRange(NOTE, NOTE.indexOf("先看整体占用"));
        assertEquals(0, first[0]);
        assertEquals(NOTE.indexOf("## 堆外内存"), first[1]);
    }

    @Test
    @DisplayName("空内容的分节范围是零长度")
    void sectionRangeOfEmptyContent() {
        assertEquals(0, NoteH1Parser.findSectionRange("", 0)[0]);
        assertEquals(0, NoteH1Parser.findSectionRange("", 0)[1]);
    }
}