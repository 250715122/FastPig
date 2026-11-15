package com.gt.kb.chunk;

import com.gt.kb.model.KnowledgeBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切分器 golden test：样本全部是 src/test/resources/chunk 下的合成笔记，不碰真实 notes。
 */
public class DefaultChunkerTest {

    private static final String DESC = "演示笔记";
    private static final ChunkProfile DEFAULT = new ChunkProfile(false, 1500, 800, 100, 30);
    private static final ChunkProfile SMALL = new ChunkProfile(false, 200, 100, 20, 10);

    private final DefaultChunker chunker = new DefaultChunker();

    private static String fixture(String name) {
        try (InputStream in = DefaultChunkerTest.class.getResourceAsStream("/chunk/" + name)) {
            if (in == null) {
                throw new IllegalStateException("缺少样本: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<KnowledgeBlock> chunk(String name) {
        return chunker.chunk("demo", DESC, fixture(name), DEFAULT);
    }

    private List<KnowledgeBlock> chunkForIndex(String name, ChunkProfile profile) {
        return chunker.chunkForIndex("demo", DESC, fixture(name), profile);
    }

    @Test
    public void h1SplitsIntoOneBlockPerHeading() {
        List<KnowledgeBlock> blocks = chunk("h1.md");
        assertEquals(2, blocks.size());

        KnowledgeBlock first = blocks.get(0);
        assertEquals("部署步骤", first.title());
        assertEquals(1, first.level());
        assertEquals("先停服务，再替换包。", first.body());
        assertEquals(List.of(DESC, "部署步骤"), first.path());
        assertEquals(1, first.startLine());
        assertEquals(3, first.endLine());
        assertFalse(first.isIndexBlock());

        KnowledgeBlock second = blocks.get(1);
        assertEquals("回滚步骤", second.title());
        assertEquals("保留上一版本目录。", second.body());
        assertEquals(5, second.startLine());
        assertEquals(7, second.endLine());
    }

    @Test
    public void deeperHeadingsEnterBreadcrumb() {
        List<KnowledgeBlock> blocks = chunk("h2-only.md");
        assertEquals(3, blocks.size());
        assertEquals(List.of(DESC, "总览"), blocks.get(0).path());
        assertEquals(List.of(DESC, "总览", "安装"), blocks.get(1).path());
        assertEquals(List.of(DESC, "总览", "配置"), blocks.get(2).path());
        assertEquals(2, blocks.get(1).level());
        assertEquals("安装说明。", blocks.get(1).body());
    }

    @Test
    public void numberedHeadingsSplitAtShallowestRepeatedLevel() {
        List<KnowledgeBlock> blocks = chunk("numbered.md");
        assertEquals(2, blocks.size());
        assertEquals("1、环境准备", blocks.get(0).title());
        assertEquals(1, blocks.get(0).level());
        assertEquals("2、启动服务", blocks.get(1).title());
        assertEquals(5, blocks.get(1).startLine());
        assertEquals(11, blocks.get(1).endLine());
        assertTrue(blocks.get(1).body().contains("2.1、检查日志"), "更深的编号标题应留在块内");
    }

    @Test
    public void dateEntriesSplitByDay() {
        List<KnowledgeBlock> blocks = chunk("dated.md");
        assertEquals(2, blocks.size());
        assertEquals(KnowledgeBlock.LEVEL_DATE, blocks.get(0).level());
        assertEquals("2024-05-01", blocks.get(0).title());
        assertEquals(List.of(DESC, "2024-05-01"), blocks.get(0).path());
        assertEquals(1, blocks.get(0).startLine());
        assertEquals(5, blocks.get(0).endLine());
        assertTrue(blocks.get(0).body().contains("## 告警处理"), "日期块内的标题不再切分");
        assertEquals("2024-05-02", blocks.get(1).title());
        assertEquals("继续跟进。", blocks.get(1).body());
    }

    @Test
    public void logProfilePrefersDatesOverHeadings() {
        String body = "2024-05-01\n\n## 甲\n\n内容甲。\n\n## 乙\n\n内容乙。\n";
        List<KnowledgeBlock> byHeading = chunker.chunk("demo", DESC, body, DEFAULT);
        assertEquals(3, byHeading.size());

        List<KnowledgeBlock> byDate = chunker.chunk("demo", DESC, body,
                new ChunkProfile(true, 1500, 800, 100, 30));
        assertEquals(1, byDate.size());
        assertEquals("2024-05-01", byDate.get(0).title());
    }

    @Test
    public void dateLinesDoNotOutrankHeadingsInKnowledgeNote() {
        // 知识笔记末尾混了日期行：日期是 level 0，不能因此把整套 # 标题挤掉
        String body = "# 查日志\n\njournalctl -b\n\n# 杀进程\n\nps -ef | grep x\n\n"
                + "2016-08-17\n\n旧记录一。\n\n2016-08-16\n\n旧记录二。\n";
        List<KnowledgeBlock> blocks = chunker.chunk("linux", DESC, body, DEFAULT);
        assertEquals(4, blocks.size());
        assertEquals("查日志", blocks.get(0).title());
        assertEquals(1, blocks.get(0).level());
        assertEquals("杀进程", blocks.get(1).title());
        assertEquals(KnowledgeBlock.LEVEL_DATE, blocks.get(2).level());
    }

    @Test
    public void codeFenceShieldsHashComments() {
        List<KnowledgeBlock> blocks = chunk("code-heavy.md");
        assertEquals(2, blocks.size());
        assertEquals("清理磁盘", blocks.get(0).title());
        assertEquals(1, blocks.get(0).startLine());
        assertEquals(9, blocks.get(0).endLine());
        String body = blocks.get(0).body();
        assertTrue(body.startsWith("```bash"), body);
        assertTrue(body.contains("# 查看各目录占用"), "代码里的 # 注释不能被当成标题切掉");
        assertTrue(body.contains("find /var/log -name '*.log' -mtime +30 -delete"));
        assertTrue(body.endsWith("```"));
        assertEquals("校验结果", blocks.get(1).title());
    }

    @Test
    public void unclosedFenceExtendsToEndOfFile() {
        List<KnowledgeBlock> blocks = chunk("unclosed-fence.md");
        assertEquals(1, blocks.size());
        assertEquals("配置示例", blocks.get(0).title());
        assertEquals(1, blocks.get(0).startLine());
        assertEquals(6, blocks.get(0).endLine());
        assertTrue(blocks.get(0).body().contains("# 这不是标题"));
        assertTrue(blocks.get(0).body().contains("other: 1"));
    }

    @Test
    public void existingIdsAreKeptAndPinDeeperHeadings() {
        List<KnowledgeBlock> blocks = chunk("with-ids.md");
        assertEquals(2, blocks.size());
        assertEquals("ku_aaaa2222", blocks.get(0).id());
        assertEquals("参数说明。", blocks.get(0).body());
        assertEquals(1, blocks.get(0).startLine());
        assertEquals(4, blocks.get(0).endLine());

        assertEquals("ku_bbbb3333", blocks.get(1).id());
        assertEquals("堆外内存", blocks.get(1).title());
        assertEquals(List.of(DESC, "内存调优", "堆外内存"), blocks.get(1).path());
        assertEquals("细节。", blocks.get(1).body());
        assertEquals(6, blocks.get(1).startLine());
        assertEquals(9, blocks.get(1).endLine());
    }

    @Test
    public void untitledLongTextIsOneKnowledgeBlock() {
        List<KnowledgeBlock> blocks = chunk("plain-long.md");
        assertEquals(1, blocks.size());
        KnowledgeBlock b = blocks.get(0);
        assertEquals("ku_cccc4444", b.id());
        assertEquals(KnowledgeBlock.LEVEL_FALLBACK, b.level());
        assertEquals(1, b.startLine());
        assertEquals(17, b.endLine());
        assertTrue(b.title().startsWith("段落一："), b.title());
        assertTrue(b.title().endsWith("…"), b.title());
        assertTrue(b.body().contains("段落八："));
        assertFalse(b.body().contains("ku:cccc4444"), "ID 注释行不属于正文");
    }

    @Test
    public void oversizedBlockBecomesSequentialIndexBlocks() {
        List<KnowledgeBlock> parts = chunkForIndex("plain-long.md", SMALL);
        assertTrue(parts.size() > 1, "长文应切成多个索引块");
        for (int i = 0; i < parts.size(); i++) {
            KnowledgeBlock p = parts.get(i);
            assertEquals("ku_cccc4444#p" + (i + 1), p.id());
            assertTrue(p.isIndexBlock());
            assertEquals("ku_cccc4444", p.parentBlockId());
            assertTrue(p.body().length() <= SMALL.maxChars(), "索引块超长: " + p.body().length());
            assertTrue(p.startLine() >= 1 && p.endLine() <= 17);
        }
        String all = String.join("\n", parts.stream().map(KnowledgeBlock::body).toList());
        for (int i = 1; i <= 8; i++) {
            assertTrue(all.contains("段落" + "一二三四五六七八".charAt(i - 1) + "："), "段落丢失: " + i);
        }
    }

    /** 只有 ID 注释的前导段没有正文，不该产出索引块——否则它会顶着父块标题单独占一条检索结果。 */
    @Test
    public void markerOnlyPreambleProducesNoIndexBlock() {
        String body = "# 部署\n<!-- ku:aaaa2222 -->\n\n## 步骤\n\n" + "调整 JVM 内存参数。".repeat(40) + "\n";
        List<KnowledgeBlock> parts = chunker.chunkForIndex("demo", DESC, body, SMALL);
        assertFalse(parts.isEmpty());
        assertTrue(parts.stream().allMatch(p -> !p.body().isBlank()), parts.toString());
        assertEquals(List.of("步骤"), parts.stream().map(KnowledgeBlock::title).distinct().toList());
    }

    /** 阈值只影响索引块，知识块必须完全一致——否则调阈值就会改写原文里的 ID。 */
    @Test
    public void thresholdsDoNotChangeKnowledgeBlocks() {
        assertEquals(chunk("plain-long.md"), chunker.chunk("demo", DESC, fixture("plain-long.md"), SMALL));
        assertEquals(chunk("numbered.md"), chunker.chunk("demo", DESC, fixture("numbered.md"), SMALL));
    }

    @Test
    public void secondarySplitUsesDeeperHeadingAndExtendsBreadcrumb() {
        List<KnowledgeBlock> parts = chunkForIndex("numbered.md", new ChunkProfile(false, 20, 15, 3, 10));
        List<KnowledgeBlock> ofSecondBlock = parts.stream()
                .filter(p -> p.parentBlockId().equals("tmp_demo_5")).toList();
        assertTrue(ofSecondBlock.size() >= 2, "应按 2.1 再切");
        assertEquals(List.of(DESC, "2、启动服务", "2.1、检查日志"),
                ofSecondBlock.get(ofSecondBlock.size() - 1).path());
        assertTrue(ofSecondBlock.get(0).body().contains("执行启动脚本。"));
    }

    @Test
    public void blankLineInsideFenceDoesNotSplitCode() {
        String body = "# 标题\n\n" + "前置说明。".repeat(10)
                + "\n\n```bash\n# 甲\ncmd-a\n\n# 乙\ncmd-b\n```\n\n" + "后置说明。".repeat(10) + "\n";
        List<KnowledgeBlock> parts = chunker.chunkForIndex("demo", DESC, body,
                new ChunkProfile(false, 80, 70, 10, 10));
        String fence = "```bash\n# 甲\ncmd-a\n\n# 乙\ncmd-b\n```";
        assertTrue(parts.stream().anyMatch(p -> p.body().contains(fence)), "代码块被空行切断了");
    }

    @Test
    public void shortUntitledBlockMergesIntoPrevious() {
        String body = "# 标题\n\n正文内容。\n\n<!-- ku:dddd5555 -->\n\n短\n";
        List<KnowledgeBlock> blocks = chunker.chunk("demo", DESC, body, DEFAULT);
        assertEquals(1, blocks.size());
        assertEquals("正文内容。\n短", blocks.get(0).body());
        assertEquals(1, blocks.get(0).startLine());
        assertEquals(7, blocks.get(0).endLine());
    }

    @Test
    public void shortTitledBlockIsKept() {
        String body = "# 一行命令\n\n`ls`\n\n# 另一个\n\n正文。\n";
        List<KnowledgeBlock> blocks = chunker.chunk("demo", DESC, body, DEFAULT);
        assertEquals(2, blocks.size());
        assertEquals("`ls`", blocks.get(0).body());
    }

    @Test
    public void preambleBecomesUntitledBlock() {
        List<KnowledgeBlock> blocks = chunker.chunk("demo", DESC, "引言文字。\n\n# 标题\n\n正文。\n", DEFAULT);
        assertEquals(2, blocks.size());
        assertEquals(KnowledgeBlock.LEVEL_FALLBACK, blocks.get(0).level());
        assertEquals("引言文字。", blocks.get(0).title());
        assertEquals(List.of(DESC, "引言文字。"), blocks.get(0).path());
        assertEquals(1, blocks.get(0).startLine());
        assertEquals(1, blocks.get(0).endLine());
        assertEquals("标题", blocks.get(1).title());
        assertEquals(3, blocks.get(1).startLine());
    }

    @Test
    public void blocksWithoutIdsGetStableTemporaryIds() {
        List<KnowledgeBlock> blocks = chunk("h1.md");
        assertEquals("tmp_demo_1", blocks.get(0).id());
        assertEquals("tmp_demo_5", blocks.get(1).id());
        assertNotEquals(blocks.get(0).id(), blocks.get(1).id());
    }

    @Test
    public void emptyBodyYieldsNoBlocks() {
        assertEquals(List.of(), chunker.chunk("demo", DESC, null, DEFAULT));
        assertEquals(List.of(), chunker.chunk("demo", DESC, "", DEFAULT));
        assertEquals(List.of(), chunker.chunk("demo", DESC, "  \n\n", DEFAULT));
        assertEquals(List.of(), chunker.chunkForIndex("demo", DESC, "", DEFAULT));
    }

    @Test
    public void missingDescFallsBackToNoteKey() {
        List<KnowledgeBlock> blocks = chunker.chunk("demo", null, fixture("h1.md"), DEFAULT);
        assertEquals(List.of("demo", "部署步骤"), blocks.get(0).path());
    }

    @Test
    public void crlfBodyGivesSameBlocksAsLf() {
        String lf = fixture("h1.md");
        assertEquals(chunker.chunk("demo", DESC, lf, DEFAULT),
                chunker.chunk("demo", DESC, lf.replace("\n", "\r\n"), DEFAULT));
    }

    @Test
    public void fittingBlocksAreIndexedAsThemselves() {
        assertEquals(chunk("h1.md"), chunkForIndex("h1.md", DEFAULT));
    }
}