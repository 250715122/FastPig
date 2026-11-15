package com.gt.kb.id;

import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.model.KnowledgeBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockIdAssignerTest {

    private static final ChunkProfile DEFAULT = new ChunkProfile(false, 1500, 800, 100, 30);
    private static final ChunkProfile SMALL = new ChunkProfile(false, 200, 100, 20, 10);
    private static final DefaultChunker CHUNKER = new DefaultChunker();

    private static String fixture(String name) {
        try (InputStream in = BlockIdAssignerTest.class.getResourceAsStream("/chunk/" + name)) {
            if (in == null) {
                throw new IllegalStateException("缺少样本: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BlockIdAssigner.Result assign(String body) {
        return BlockIdAssigner.assign("demo", "演示笔记", body, DEFAULT);
    }

    private static List<String> idLines(String body) {
        List<String> out = new ArrayList<>();
        for (String line : body.split("\n", -1)) {
            if (BlockIdCodec.parse(line).isPresent()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String stripIdLines(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (BlockIdCodec.parse(line).isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @Test
    public void insertsIdRightAfterEachTitle() {
        BlockIdAssigner.Result r = assign(fixture("h1.md"));
        assertEquals(2, r.assigned());
        assertEquals(0, r.reassigned());
        assertEquals(List.of(1, 5), r.insertedLines());

        String[] lines = r.body().split("\n", -1);
        assertEquals("# 部署步骤", lines[0]);
        assertTrue(BlockIdCodec.parse(lines[1]).isPresent());
        assertEquals("# 回滚步骤", lines[5]);
        assertTrue(BlockIdCodec.parse(lines[6]).isPresent());
    }

    @Test
    public void secondRunChangesNothing() {
        String once = assign(fixture("numbered.md")).body();
        BlockIdAssigner.Result twice = BlockIdAssigner.assign("demo", "演示笔记", once, DEFAULT);
        assertFalse(twice.changed());
        assertEquals(once, twice.body());
        assertEquals(List.of(), twice.insertedLines());
    }

    @Test
    public void bodyTextIsUntouched() {
        for (String name : List.of("h1.md", "h2-only.md", "numbered.md", "dated.md", "code-heavy.md", "unclosed-fence.md")) {
            String original = fixture(name);
            assertEquals(original, stripIdLines(assign(original).body()), "正文被改动: " + name);
        }
    }

    @Test
    public void everyKnowledgeBlockEndsUpWithValidId() {
        for (String name : List.of("h1.md", "h2-only.md", "numbered.md", "dated.md", "code-heavy.md", "with-ids.md")) {
            String assigned = assign(fixture(name)).body();
            List<KnowledgeBlock> blocks = CHUNKER.chunk("demo", "演示笔记", assigned, DEFAULT);
            assertFalse(blocks.isEmpty(), name);
            for (KnowledgeBlock b : blocks) {
                assertTrue(BlockIdCodec.isValidId(b.id()), name + " 块无合法 ID: " + b.title());
            }
            assertEquals(blocks.size(), idLines(assigned).size(), name + " ID 行数与块数不符");
        }
    }

    @Test
    public void untitledPreambleGetsIdBeforeFirstLine() {
        BlockIdAssigner.Result r = assign("引言文字。\n\n# 标题\n\n正文。\n");
        assertEquals(List.of(0, 3), r.insertedLines());
        String[] lines = r.body().split("\n", -1);
        assertTrue(BlockIdCodec.parse(lines[0]).isPresent());
        assertEquals("引言文字。", lines[1]);
        assertEquals("# 标题", lines[3]);
        assertTrue(BlockIdCodec.parse(lines[4]).isPresent());
    }

    @Test
    public void duplicateIdKeepsFirstAndReassignsRest() {
        String body = "# 甲\n<!-- ku:aaaa2222 -->\n\n正文甲。\n\n# 乙\n<!-- ku:aaaa2222 -->\n\n正文乙。\n";
        BlockIdAssigner.Result r = assign(body);
        assertEquals(0, r.assigned());
        assertEquals(1, r.reassigned());

        List<KnowledgeBlock> blocks = CHUNKER.chunk("demo", "演示笔记", r.body(), DEFAULT);
        assertEquals("ku_aaaa2222", blocks.get(0).id());
        assertTrue(BlockIdCodec.isValidId(blocks.get(1).id()));
        assertNotEquals(blocks.get(0).id(), blocks.get(1).id());
    }

    @Test
    public void idTakenByAnotherNoteIsReassigned() {
        String body = "# 甲\n<!-- ku:aaaa2222 -->\n\n正文甲。\n";
        BlockIdAssigner.Result r = BlockIdAssigner.assign("demo", "演示笔记", body, DEFAULT,
                Set.of("ku_aaaa2222")::contains);
        assertEquals(1, r.reassigned());
        assertEquals(0, r.assigned());
        assertFalse(r.body().contains("ku:aaaa2222"));
        assertTrue(BlockIdCodec.isValidId(CHUNKER.chunk("demo", "演示笔记", r.body(), DEFAULT).get(0).id()));
    }

    @Test
    public void reassignedIdKeepsAttributes() {
        String body = "# 甲\n<!-- ku:aaaa2222 status=verified scope=\"Spark 3.3\" -->\n\n正文甲。\n\n"
                + "# 乙\n<!-- ku:aaaa2222 status=verified scope=\"Spark 3.3\" -->\n\n正文乙。\n";
        String out = assign(body).body();
        assertTrue(out.contains("scope=\"Spark 3.3\""));
        assertEquals(2, out.split("status=verified", -1).length - 1);
    }

    @Test
    public void idCommentInsideCodeFenceIsNotAnId() {
        String body = "# 甲\n\n```html\n<!-- ku:aaaa2222 -->\n```\n\n# 乙\n<!-- ku:aaaa2222 -->\n\n正文乙。\n";
        BlockIdAssigner.Result r = assign(body);
        assertEquals(1, r.assigned(), "代码块里的注释不是块 ID，甲块仍需补 ID");
        assertEquals(0, r.reassigned());
        assertTrue(r.body().contains("```html\n<!-- ku:aaaa2222 -->\n```"), "代码内容被改动");
    }

    @Test
    public void malformedIdCommentGetsFreshIdAndIsLeftInPlace() {
        String body = "# 甲\n<!-- ku:ZZZZ -->\n\n正文甲。\n";
        BlockIdAssigner.Result r = assign(body);
        assertEquals(1, r.assigned());
        assertTrue(r.body().contains("<!-- ku:ZZZZ -->"));
        assertTrue(BlockIdCodec.isValidId(CHUNKER.chunk("demo", "演示笔记", r.body(), DEFAULT).get(0).id()));
    }

    @Test
    public void indexBlocksDoNotGetIdsInFile() {
        String longText = fixture("plain-long.md").split("\n", -1)[2] + "\n";
        String body = String.join("\n\n", Collections.nCopies(8, longText.strip())) + "\n";
        BlockIdAssigner.Result r = BlockIdAssigner.assign("demo", "演示笔记", body, SMALL);
        assertEquals(1, r.assigned());
        assertEquals(1, idLines(r.body()).size());
        assertTrue(CHUNKER.chunkForIndex("demo", "演示笔记", r.body(), SMALL).size() > 1);
    }

    @Test
    public void crlfIsPreserved() {
        String body = fixture("h1.md").replace("\n", "\r\n");
        String out = assign(body).body();
        assertTrue(out.contains("\r\n"));
        assertFalse(out.replace("\r\n", "").contains("\n"));
        assertEquals(body, stripIdLines(out.replace("\r\n", "\n")).replace("\n", "\r\n"));
    }

    @Test
    public void blankBodyIsReturnedAsIs() {
        assertNull(assign(null).body());
        assertEquals("", assign("").body());
        assertEquals("  \n", assign("  \n").body());
        assertFalse(assign("  \n").changed());
    }

    @Test
    public void generatedIdsAreUniqueAcrossBlocks() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            body.append("# 标题").append(i).append("\n\n正文").append(i).append("。\n\n");
        }
        String out = assign(body.toString()).body();
        List<String> ids = idLines(out);
        assertEquals(60, ids.size());
        assertEquals(60, Set.copyOf(ids).size());
    }

    @Test
    public void loneCarriageReturnIsPreserved() {
        String body = "# 标题\r正文带孤立 CR\n\n## 第二节\n\n正文\n";
        String out = assign(body).body();
        assertTrue(out.contains("\r正文带孤立 CR"), "孤立 CR 被改成了别的行尾符");
        assertTrue(BlockIdMigration.sameTextIgnoringIds(body, out));
    }

    @Test
    public void mixedLineEndingsArePreserved() {
        String body = "# 标题\r\n\r\n正文甲\n\n## 第二节\r正文乙\r\n";
        assertTrue(BlockIdMigration.sameTextIgnoringIds(body, assign(body).body()));
    }

    @Test
    public void idAppendedAtEndOfBodyWithoutTrailingNewline() {
        String body = "# 只有标题没有正文";
        String out = assign(body).body();
        assertEquals(1, idLines(out).size());
        assertTrue(out.startsWith(body + "\n"), "ID 行粘在了标题后面");
        assertTrue(BlockIdMigration.sameTextIgnoringIds(body, out));
    }

    @Test
    public void idAppendedAtEndOfCrlfBodyWithoutTrailingNewline() {
        String body = "# 甲\r\n\r\n# 乙";
        String out = assign(body).body();
        int last = out.lastIndexOf("<!-- ku:");
        assertEquals("\r\n", out.substring(last - 2, last), "文末 ID 行没有独立的 CRLF 行尾");
        assertTrue(BlockIdMigration.sameTextIgnoringIds(body, out));
    }

    @Test
    public void singleLineUntitledBodyGetsIdOnItsOwnLine() {
        String body = "只有一行且不以换行结尾";
        String out = assign(body).body();
        assertEquals(1, idLines(out).size());
        assertTrue(out.endsWith("\n" + body), out);
        assertTrue(BlockIdMigration.sameTextIgnoringIds(body, out));
    }
}