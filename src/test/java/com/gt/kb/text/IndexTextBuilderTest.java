package com.gt.kb.text;

import com.gt.kb.model.BlockMeta;
import com.gt.kb.model.KnowledgeBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexTextBuilderTest {

    private static KnowledgeBlock block(String title, List<String> path, String body) {
        return new KnowledgeBlock("ku_aaaa2222", "mysql", title, 1, path, body, 1, 2, null, BlockMeta.EMPTY);
    }

    private static KnowledgeBlock block(String body) {
        return block("标题", List.of("MySQL 运维", "标题"), body);
    }

    @Test
    @DisplayName("索引文本 = 面包屑 + 标题 + 正文")
    void ordersBreadcrumbTitleBody() {
        String text = IndexTextBuilder.build(block("内容"), 300);
        assertEquals("MySQL 运维 > 标题\n标题\n内容", text);
    }

    @Test
    @DisplayName("保留代码块内容，去掉围栏标记和语言名")
    void keepsFencedCodeDropsFenceMarkers() {
        String body = "说明\n\n```bash\nsystemctl restart nginx\n```\n\n结束";
        String text = IndexTextBuilder.normalize(body);
        assertEquals("说明\nsystemctl restart nginx\n结束", text);
    }

    @Test
    @DisplayName("未闭合围栏的代码内容同样保留")
    void keepsUnclosedFenceContent() {
        assertEquals("看配置\nkey=value", IndexTextBuilder.normalize("看配置\n\n```yaml\nkey=value"));
    }

    @Test
    @DisplayName("行内代码去反引号但保留内容")
    void keepsInlineCodeContent() {
        assertEquals("执行 systemctl restart nginx 即可",
                IndexTextBuilder.normalize("执行 `systemctl restart nginx` 即可"));
    }

    @Test
    @DisplayName("代码行里的 # 注释和 --参数不被当成 Markdown 标记")
    void doesNotCleanCodeLines() {
        String body = "```bash\n# 查看占用\ndu -sh --max-depth=1 /var\n```";
        assertEquals("# 查看占用\ndu -sh --max-depth=1 /var", IndexTextBuilder.normalize(body));
    }

    @Test
    @DisplayName("去掉块 ID 注释与 HTML 注释")
    void stripsComments() {
        assertEquals("标题\n正文", IndexTextBuilder.normalize("<!-- ku:aaaa2222 -->\n# 标题\n<!-- 备注 -->\n正文"));
    }

    @Test
    @DisplayName("去掉 HTML 标签但保留标签内文字")
    void stripsHtmlTagsKeepsText() {
        assertEquals("重点 内容", IndexTextBuilder.normalize("<b>重点</b> <span class=\"x\">内容</span>"));
    }

    @Test
    @DisplayName("图片保留 alt 文本，链接保留文字")
    void keepsAltAndLinkText() {
        assertEquals("架构图 见 官方文档",
                IndexTextBuilder.normalize("![架构图](assets/a.png) 见 [官方文档](https://example.com)"));
    }

    @Test
    @DisplayName("去掉标题/列表/引用标记与强调符号")
    void stripsMarkdownMarkers() {
        String body = "## 小标题\n\n- 第一项\n- 第二项\n\n> 引用文字\n\n**加粗** 和 *斜体* 和 __下划线__ 和 ~~删除~~";
        assertEquals("小标题\n第一项\n第二项\n引用文字\n加粗 和 斜体 和 下划线 和 删除",
                IndexTextBuilder.normalize(body));
    }

    @Test
    @DisplayName("去掉分隔线与多余空白")
    void stripsRulesAndCollapsesWhitespace() {
        assertEquals("甲\n乙", IndexTextBuilder.normalize("甲\n\n---\n\n\n乙"));
        assertEquals("甲 乙", IndexTextBuilder.normalize("甲    乙"));
    }

    @Test
    @DisplayName("空正文只输出面包屑和标题")
    void emptyBodyKeepsPrefix() {
        assertEquals("MySQL 运维 > 标题\n标题", IndexTextBuilder.build(block(""), 300));
    }

    @Test
    @DisplayName("正文截断到上限，面包屑和标题永不截断")
    void truncatesBodyOnly() {
        String longTitle = "这是一个非常非常长的标题绝对不会被截断";
        List<String> path = List.of("很长的笔记描述也不会被截断", longTitle);
        String text = IndexTextBuilder.build(block(longTitle, path, "0123456789ABCDEF"), 5);
        assertEquals("很长的笔记描述也不会被截断 > " + longTitle + "\n" + longTitle + "\n01234", text);
    }

    @Test
    @DisplayName("代码占比高的块把标识符前置")
    void prependsIdentifiersForCodeHeavyBlock() {
        String body = "说明一行\n\n```bash\nsystemctl restart nginx --force\n"
                + "grep -i error app.log | awk '{print $1}'\nmax_connections=200\n```";
        String text = IndexTextBuilder.build(block(body), 300);
        String firstLine = text.substring(0, text.indexOf('\n'));
        assertTrue(firstLine.contains("systemctl"), firstLine);
        assertTrue(firstLine.contains("--force"), firstLine);
        assertTrue(firstLine.contains("max_connections"), firstLine);
        assertTrue(firstLine.contains("awk"), firstLine);
        assertTrue(text.indexOf("systemctl --force") < text.indexOf("MySQL 运维"), "标识符应在面包屑之前");
    }

    @Test
    @DisplayName("代码占比低的块不前置标识符")
    void noIdentifiersForProseBlock() {
        String body = "这是一段很长的说明文字，用来把代码占比压到七成以下，"
                + "再补充一些描述内容确保正文足够长，最后才给出一小段命令示例。\n\n"
                + "```bash\nls -l\n```";
        String text = IndexTextBuilder.build(block(body), 300);
        assertTrue(text.startsWith("MySQL 运维 > 标题"), text);
    }

    @Test
    @DisplayName("纯代码块也能前置标识符")
    void pureCodeBlockIsCodeHeavy() {
        String text = IndexTextBuilder.build(block("```bash\nredis-cli --scan\n```"), 300);
        assertTrue(text.startsWith("redis-cli --scan"), text);
    }

    @Test
    @DisplayName("无代码块时不前置标识符")
    void proseOnlyHasNoIdentifierLine() {
        String text = IndexTextBuilder.build(block("普通说明文字，没有任何代码。"), 300);
        assertEquals("MySQL 运维 > 标题\n标题\n普通说明文字，没有任何代码。", text);
    }
}