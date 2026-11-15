package com.gt.kb.chunk;

import com.gt.NoteDto;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.text.IndexTextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexBlocksTest {

    private static final String NOTE = """
            # 内存调优

            先看整体占用。

            ## 堆外内存

            查看 DirectByteBuffer。
            """;

    private static NoteDto note(String key, String body) {
        NoteDto n = new NoteDto();
        n.key = key;
        n.desc = "MySQL 运维";
        n.bodyMd = body;
        return n;
    }

    @Test
    @DisplayName("首文档是笔记名文档，正文就是 key 本身")
    void noteDocComesFirst() {
        List<KnowledgeBlock> blocks = IndexBlocks.of(note("mysql", NOTE));
        KnowledgeBlock first = blocks.get(0);
        assertEquals("mysql" + IndexBlocks.NOTE_SUFFIX, first.id());
        assertTrue(IndexBlocks.isNoteDoc(first));
        assertEquals("mysql", IndexTextBuilder.build(first));
        assertEquals(1, first.startLine());
    }

    @Test
    @DisplayName("私密、已删除、无 key 的笔记不产出任何文档")
    void excludedNotesYieldNothing() {
        NoteDto encrypted = note("secret", NOTE);
        encrypted.encrypted = true;
        assertTrue(IndexBlocks.of(encrypted).isEmpty());

        NoteDto deleted = note("gone", NOTE);
        deleted.deleted = true;
        assertTrue(IndexBlocks.of(deleted).isEmpty());

        assertTrue(IndexBlocks.of(note("", NOTE)).isEmpty());
        assertTrue(IndexBlocks.of(null).isEmpty());
    }

    @Test
    @DisplayName("同名标题的文档 ID 互不重复，索引文本均非空")
    void docIdsAreUniqueAndTextNonEmpty() {
        List<KnowledgeBlock> blocks = IndexBlocks.of(note("k", "# 重复\n\n甲\n\n# 重复\n\n乙\n"));
        assertEquals(blocks.size(), blocks.stream().map(KnowledgeBlock::id).distinct().count());
        assertTrue(blocks.stream().allMatch(b -> !IndexTextBuilder.build(b).isEmpty()));
        assertEquals(2, blocks.stream().filter(b -> "重复".equals(b.title())).count());
    }

    @Test
    @DisplayName("描述为空时面包屑回退用 key")
    void descFallsBackToKey() {
        NoteDto n = note("mysql", NOTE);
        n.desc = "   ";
        assertEquals("mysql", IndexBlocks.descOf(n));
        assertTrue(IndexBlocks.of(n).get(1).pathText().startsWith("mysql"));
    }

    @Test
    @DisplayName("索引块面包屑挂到知识块，小标题留在 title 里")
    void indexBlockPathPointsAtKnowledgeBlock() {
        String longBody = "配置说明。".repeat(400);
        String body = "# 部署\n<!-- ku:aaaa2222 -->\n\n## 步骤\n\n" + longBody + "\n";
        List<KnowledgeBlock> blocks = IndexBlocks.of(note("flinkcdc", body));
        List<KnowledgeBlock> pieces = blocks.stream()
                .filter(b -> b.isIndexBlock() && !IndexBlocks.isNoteDoc(b)).toList();

        assertTrue(pieces.size() > 1, "长块应被二次切分，实际 " + pieces.size());
        for (KnowledgeBlock p : pieces) {
            assertEquals("ku_aaaa2222", p.parentBlockId());
            assertEquals("部署", IndexBlocks.blockTitleOf(p), p.id());
            assertEquals("MySQL 运维 > 部署", p.pathText(), p.id());
        }
        // 小标题不能因为面包屑改挂知识块而从索引文本里消失
        assertTrue(pieces.stream().anyMatch(p -> IndexTextBuilder.build(p).contains("步骤")));
    }

    @Test
    @DisplayName("超长知识块里的不同小节各自保留 title，作为归并身份的第二个维度")
    void distinctSectionsKeepTheirOwnTitles() {
        String longBody = "安装步骤说明。".repeat(400);
        // 两个 H1 才能让主切分级停在 H1，两个 H2 沦为超长块内部的小节
        String body = "# 安装\n\n" + longBody + "\n\n## 配置shovel\n\n开启插件。\n\n## 创建用户\n\n添加账号。\n"
                + "\n# 卸载\n\n卸载说明。\n";
        List<KnowledgeBlock> blocks = IndexBlocks.of(note("rabbitmq", body)).stream()
                .filter(b -> !IndexBlocks.isNoteDoc(b)).toList();
        List<String> titles = blocks.stream().map(KnowledgeBlock::title).distinct().toList();

        assertTrue(titles.contains("配置shovel"), "实际 title: " + titles);
        assertTrue(titles.contains("创建用户"), "实际 title: " + titles);
        // 这两个小节是超长块「安装」的二次切分段，面包屑仍统一挂在知识块上
        for (KnowledgeBlock b : blocks) {
            if ("配置shovel".equals(b.title()) || "创建用户".equals(b.title())) {
                assertTrue(b.isIndexBlock(), b.id());
                assertEquals("安装", IndexBlocks.blockTitleOf(b), b.id());
            }
        }
    }
}