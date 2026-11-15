package com.gt.vector;

import com.gt.NoteDto;
import com.gt.kb.chunk.IndexBlocks;
import com.gt.kb.model.BlockMeta;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.repo.BlockCatalog;
import com.gt.kb.repo.SingleFileBlockRepository;
import com.gt.kb.search.RrfFusion.Channel;
import com.gt.kb.search.SearchFilter;
import com.gt.kb.search.SearchProfile;
import com.gt.kb.text.IndexTextBuilder;
import com.gt.storage.NoteFileStorage;
import com.gt.vector.LuceneVectorSearchService.BlockState;
import com.gt.vector.LuceneVectorSearchService.IndexedBlock;
import com.gt.vector.LuceneVectorSearchService.VectorSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全部在 @TempDir 上跑，向量由 {@link CountingEmbedder} 确定性生成，
 * 不加载 ONNX 模型，也不碰仓库根的 notes / vector_index。
 */
class LuceneVectorSearchServiceTest {

    private static final String NOTE = """
            # 内存调优

            先看整体占用。

            ## 堆外内存

            查看 DirectByteBuffer。

            ## 元空间

            调整 MetaspaceSize。
            """;

    @TempDir
    Path tempDir;

    private CountingEmbedder embedder;
    private LuceneVectorSearchService svc;
    private Path indexDir;

    @BeforeEach
    void setUp() {
        embedder = new CountingEmbedder();
        indexDir = tempDir.resolve("vector_index");
        svc = new LuceneVectorSearchService(embedder);
        svc.initialize(indexDir);
        assertTrue(svc.isAvailable(), svc.getErrorMessage());
    }

    @AfterEach
    void tearDown() {
        svc.close();
    }

    private static NoteDto note(String key, String body) {
        NoteDto n = new NoteDto();
        n.key = key;
        n.desc = "MySQL 运维";
        n.bodyMd = body;
        return n;
    }

    // ==================== 结构版本 ====================

    @Test
    @DisplayName("新索引判为结构过期，落盘版本后重开不再过期")
    void schemaVersionPersisted() {
        assertTrue(svc.isSchemaOutdated());
        svc.markSchemaCurrent();
        assertFalse(svc.isSchemaOutdated());
        svc.close();

        LuceneVectorSearchService reopened = new LuceneVectorSearchService(embedder);
        reopened.initialize(indexDir);
        try {
            assertTrue(reopened.isAvailable(), reopened.getErrorMessage());
            assertFalse(reopened.isSchemaOutdated());
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("旧版本索引启动时判为过期，触发全量重建")
    void oldSchemaIsOutdated() throws Exception {
        Path old = tempDir.resolve("v2_index");
        Files.createDirectories(old);
        Files.writeString(old.resolve(".schema_version"), "2");

        LuceneVectorSearchService legacy = new LuceneVectorSearchService(embedder);
        legacy.initialize(old);
        try {
            assertTrue(legacy.isAvailable(), legacy.getErrorMessage());
            assertTrue(legacy.isSchemaOutdated());
        } finally {
            legacy.close();
        }
    }

    // ==================== 写入与差量 ====================

    @Test
    @DisplayName("一篇笔记落成笔记名文档 + 各索引块，doc_id 就是块 ID")
    void writesNoteDocAndBlocks() {
        NoteDto n = note("mysql", NOTE);
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        svc.indexNote(n, blocks);

        assertEquals(blocks.size(), svc.getIndexedCount());
        Map<String, BlockState> indexed = svc.getIndexedBlocks("mysql");
        assertEquals(blocks.size(), indexed.size());
        for (KnowledgeBlock b : blocks) {
            assertEquals(b.contentHash(), indexed.get(b.id()).contentHash(), b.id());
        }
    }

    @Test
    @DisplayName("内容未变化时一次 embed 都不做")
    void unchangedNoteSkipsEmbedding() {
        NoteDto n = note("mysql", NOTE);
        svc.indexNote(n, IndexBlocks.of(n));
        embedder.embedded.clear();

        svc.indexNote(n, IndexBlocks.of(n));
        assertTrue(embedder.embedded.isEmpty(), embedder.embedded.toString());
    }

    @Test
    @DisplayName("只改一个块，只重算那一个块")
    void onlyChangedBlockIsReEmbedded() {
        NoteDto n = note("mysql", NOTE);
        svc.indexNote(n, IndexBlocks.of(n));
        embedder.embedded.clear();

        n.bodyMd = NOTE.replace("调整 MetaspaceSize。", "调整 MetaspaceSize 与 ReservedCodeCacheSize。");
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        svc.indexNote(n, blocks);

        assertEquals(1, embedder.embedded.size(), embedder.embedded.toString());
        assertTrue(embedder.embedded.get(0).contains("元空间"), embedder.embedded.get(0));
        assertEquals(blocks.size(), svc.getIndexedCount());
    }

    @Test
    @DisplayName("删掉一个块，它的文档从索引消失，其余块不重算")
    void removedBlockIsDeleted() {
        // 用已迁移的笔记：原文 ID 不随行号变化，才能断言"其余块没动"
        String migrated = """
                # 内存调优
                <!-- ku:aaaa2222 -->

                先看整体占用。

                ## 堆外内存
                <!-- ku:bbbb3333 -->

                查看 DirectByteBuffer。

                ## 元空间
                <!-- ku:cccc4444 -->

                调整 MetaspaceSize。
                """;
        NoteDto n = note("mysql", migrated);
        svc.indexNote(n, IndexBlocks.of(n));
        int before = svc.getIndexedCount();
        embedder.embedded.clear();

        n.bodyMd = migrated.substring(0, migrated.indexOf("## 元空间"));
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        svc.indexNote(n, blocks);

        assertEquals(before - 1, svc.getIndexedCount());
        assertEquals(blocks.size(), svc.getIndexedCount());
        assertFalse(svc.getIndexedBlocks("mysql").containsKey("ku_cccc4444"));
        assertTrue(embedder.embedded.isEmpty(), embedder.embedded.toString());
    }

    @Test
    @DisplayName("全量重建按 doc_id 覆盖，重复写入不产生重复文档")
    void batchWriteOverwritesByDocId() {
        NoteDto n = note("mysql", NOTE);
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        List<IndexedBlock> batch = blocks.stream()
                .map(b -> new IndexedBlock(n, b, CountingEmbedder.vectorOf(IndexTextBuilder.build(b))))
                .toList();

        svc.batchWriteWithVectors(null, batch);
        svc.batchWriteWithVectors(null, batch);
        assertEquals(blocks.size(), svc.getIndexedCount());
    }

    // ==================== 笔记级分类 ====================

    @Test
    @DisplayName("块正文没变但笔记分类变了，整篇重算并刷新过滤字段")
    void noteMetaChangeForcesReIndex() {
        NoteDto n = note("mysql", NOTE);
        n.visibility = NoteDto.VISIBILITY_AI;
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        svc.indexNote(n, blocks);
        Map<String, BlockState> before = svc.getIndexedBlocks("mysql");
        embedder.embedded.clear();

        // 个人域不允许 ai 可见，effectiveVisibility 回落 human
        n.domain = NoteDto.DOMAIN_PERSONAL;
        svc.indexNote(n, IndexBlocks.of(n));

        assertEquals(blocks.size(), embedder.embedded.size(), embedder.embedded.toString());
        Map<String, BlockState> after = svc.getIndexedBlocks("mysql");
        for (KnowledgeBlock b : blocks) {
            assertEquals(before.get(b.id()).contentHash(), after.get(b.id()).contentHash(), b.id());
            assertNotEquals(before.get(b.id()).noteMeta(), after.get(b.id()).noteMeta(), b.id());
            assertTrue(after.get(b.id()).noteMeta().endsWith(NoteDto.VISIBILITY_HUMAN),
                    after.get(b.id()).noteMeta());
        }
    }

    @Test
    @DisplayName("笔记转为私密后旧索引整体删除，标题摘要不留明文")
    void encryptingNoteClearsItsDocs() {
        NoteDto n = note("mysql", NOTE);
        svc.indexNote(n, IndexBlocks.of(n));
        assertTrue(svc.getIndexedCount() > 0);

        n.encrypted = true;
        svc.indexNote(n, IndexBlocks.of(n));

        assertEquals(0, svc.getIndexedCount());
        assertTrue(svc.getIndexedBlocks("mysql").isEmpty());
    }

    // ==================== 检索结果 ====================

    @Test
    @DisplayName("命中块带回 blockId、面包屑和行号，可直接跳转")
    void searchReturnsBlockLocation() {
        NoteDto n = note("mysql", NOTE);
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        svc.indexNote(n, blocks);

        List<VectorSearchResult> results = svc.search("元空间 MetaspaceSize", 10);
        assertFalse(results.isEmpty());
        VectorSearchResult hit = results.stream()
                .filter(r -> "元空间".equals(r.h1Title)).findFirst().orElseThrow();
        KnowledgeBlock expected = blocks.stream()
                .filter(b -> "元空间".equals(b.title())).findFirst().orElseThrow();

        assertEquals("mysql", hit.noteKey);
        assertEquals("MySQL 运维", hit.noteDesc);
        assertEquals(expected.parentBlockId(), hit.blockId);
        assertEquals(expected.pathText(), hit.path);
        assertEquals(expected.startLine(), hit.startLine);
        assertTrue(hit.startLine > 1);
        assertTrue(hit.content.contains("MetaspaceSize"), hit.content);
    }

    @Test
    @DisplayName("笔记名文档的 blockId 为空，UI 据此不显示块标题")
    void noteDocHasEmptyBlockId() {
        NoteDto n = note("mysql", NOTE);
        svc.indexNote(n, IndexBlocks.of(n));

        List<VectorSearchResult> results = svc.search("mysql", 10);
        VectorSearchResult noteHit = results.stream()
                .filter(r -> r.blockId.isEmpty()).findFirst().orElseThrow();
        assertEquals("mysql", noteHit.noteKey);
        assertEquals(1, noteHit.startLine);
    }

    @Test
    @DisplayName("同一小节被切成多段时归并成一条，标题取知识块标题")
    void indexBlocksMergeIntoKnowledgeBlock() {
        String longBody = "调整 JVM 内存参数。".repeat(400);
        NoteDto n = note("flinkcdc", "# 部署\n<!-- ku:aaaa2222 -->\n\n## 步骤\n\n" + longBody
                + "\n\n# 启动\n<!-- ku:bbbb3333 -->\n\n启动任务。\n");
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        assertTrue(blocks.stream().filter(b -> b.isIndexBlock() && !IndexBlocks.isNoteDoc(b)).count() > 1);
        svc.indexNote(n, blocks);

        List<VectorSearchResult> results = svc.search("JVM 内存参数 部署 步骤", 10);
        List<VectorSearchResult> deployed = results.stream()
                .filter(r -> "ku_aaaa2222".equals(r.blockId)).toList();
        assertEquals(1, deployed.size(), "同一知识块只应出现一条: " + results);
        assertEquals("部署", deployed.get(0).h1Title);
        assertEquals("MySQL 运维 > 部署", deployed.get(0).path);
    }

    @Test
    @DisplayName("超长知识块里的不同小节各自成一条结果，低分小节不被兄弟段挤掉")
    void distinctSectionsAreNotMergedAway() {
        String longBody = "调整 JVM 内存参数。".repeat(400);
        NoteDto n = note("rabbitmq", "# 安装\n<!-- ku:aaaa2222 -->\n\n" + longBody
                + "\n\n## 配置shovel\n\n开启 rabbitmq_shovel 插件，也要调整 JVM 内存参数。\n");
        svc.indexNote(n, IndexBlocks.of(n));

        // 父块正文段和 shovel 段都含查询词：按父块归并只剩一条，按小节归并才是两条
        List<VectorSearchResult> both = svc.search("JVM 内存参数", 10).stream()
                .filter(r -> "ku_aaaa2222".equals(r.blockId)).toList();
        assertEquals(2, both.size(), "小节被父块归并掉了: " + both);
        assertNotEquals(both.get(0).sectionTitle, both.get(1).sectionTitle, both.toString());
        assertTrue(both.stream().anyMatch(r -> "配置shovel".equals(r.sectionTitle)), both.toString());

        VectorSearchResult shovel = svc.search("配置shovel", 10).stream()
                .filter(r -> "配置shovel".equals(r.sectionTitle)).findFirst()
                .orElseThrow(() -> new AssertionError("小节没出现: " + svc.search("配置shovel", 10)));
        assertEquals("ku_aaaa2222", shovel.blockId);
        assertEquals("安装", shovel.h1Title);
        assertTrue(shovel.content.contains("rabbitmq_shovel"), shovel.content);
    }

    @Test
    @DisplayName("AI 档位回源取完整块正文并带更新时间，人档位仍是摘要")
    void aiProfileReturnsFullBodyAndProvenance() {
        String longBody = "调整 JVM 内存参数。".repeat(400);
        NoteDto n = note("rabbitmq", "# 安装\n<!-- ku:aaaa2222 -->\n\n" + longBody
                + "\n\n## 配置shovel\n\n开启 rabbitmq_shovel 插件。\n");
        n.id = UUID.randomUUID().toString();
        n.updatedAt = 1_700_000_000_000L;
        n.visibility = NoteDto.VISIBILITY_AI;

        NoteFileStorage storage = new NoteFileStorage(tempDir.toString());
        storage.saveToFile(n);
        svc.indexNote(n, IndexBlocks.of(n));

        // 未注入块仓库时退回摘要，检索本身不受影响
        assertTrue(aiShovel().content.length() <= 203, "没注入仓库就不该有完整正文");

        BlockCatalog catalog = new BlockCatalog(tempDir.resolve("kb.db").toString());
        try (SingleFileBlockRepository repo = new SingleFileBlockRepository(storage, catalog)) {
            svc.setBlockRepository(repo);
            VectorSearchResult ai = aiShovel();
            assertTrue(ai.content.contains("rabbitmq_shovel"), ai.content);
            assertTrue(ai.content.length() > longBody.length(),
                    "应取到整块正文而非摘要，实际长度 " + ai.content.length());
            assertEquals(1_700_000_000_000L, ai.updatedAt);
            assertEquals("rabbitmq", ai.noteKey);
            assertEquals("安装", ai.h1Title);
            assertTrue(ai.startLine > 0, "行号 " + ai.startLine);
            assertEquals("ku_aaaa2222", ai.blockId);
        }

        VectorSearchResult human = svc.search("配置shovel", 10).stream()
                .filter(r -> "配置shovel".equals(r.sectionTitle)).findFirst().orElseThrow();
        assertTrue(human.content.length() <= 203, "人档位仍应是摘要: " + human.content.length());
    }

    private VectorSearchResult aiShovel() {
        return svc.search("配置shovel", 10, SearchProfile.AI, SearchFilter.forProfile(SearchProfile.AI))
                .stream().filter(r -> "配置shovel".equals(r.sectionTitle)).findFirst()
                .orElseThrow(() -> new AssertionError("AI 档位没命中小节"));
    }

    // ==================== 混合检索三通道 ====================

    private static final String DOCKER = """
            # 容器

            ## 创建容器

            docker run -d --name demo nginx

            提示 permission denied 时检查挂载目录权限。
            """;

    @Test
    @DisplayName("精确通道：命令串与报错串按字面命中，不依赖向量相似度")
    void exactChannelMatchesCommandLiteral() {
        NoteDto n = note("docker", DOCKER);
        svc.indexNote(n, IndexBlocks.of(n));

        List<VectorSearchResult> results = svc.search("docker run 提示 permission denied 没权限", 10);
        VectorSearchResult hit = blockHitOf(results, "docker");

        assertTrue(hit.channels.contains(Channel.EXACT), hit.toString());
        assertTrue(hit.content.contains("permission denied"), hit.content);
    }

    @Test
    @DisplayName("三个通道都命中时通道标记齐全，便于调参时看谁起了作用")
    void allChannelsAreMarked() {
        NoteDto n = note("docker", DOCKER);
        svc.indexNote(n, IndexBlocks.of(n));

        VectorSearchResult hit = blockHitOf(svc.search("docker run", 10), "docker");

        assertEquals(EnumSet.of(Channel.EXACT, Channel.BM25, Channel.VECTOR), hit.channels, hit.toString());
        assertTrue(hit.score > 0);
        assertFalse(hit.docId.isEmpty());
    }

    /** 取指定笔记的知识块命中（排除笔记名文档），标题随切分合并规则变化，不作为断言依据。 */
    private static VectorSearchResult blockHitOf(List<VectorSearchResult> results, String noteKey) {
        return results.stream()
                .filter(r -> noteKey.equals(r.noteKey) && !r.blockId.isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError(results.toString()));
    }

    @Test
    @DisplayName("向量算不出来时 BM25 仍然出结果，不再整体返回空")
    void keywordChannelsSurviveEmbeddingFailure() {
        NoteDto n = note("mysql", NOTE);
        svc.indexNote(n, IndexBlocks.of(n));
        embedder.nullFor = "元空间";

        List<VectorSearchResult> results = svc.search("元空间", 10);
        VectorSearchResult hit = results.stream()
                .filter(r -> "元空间".equals(r.h1Title)).findFirst().orElseThrow();

        // 「元空间」没有 ASCII 串，精确通道为空；只剩 BM25
        assertEquals(EnumSet.of(Channel.BM25), hit.channels, hit.toString());
    }

    @Test
    @DisplayName("人档位排除 archived：三个通道都过滤，不是只在结果里筛")
    void humanProfileExcludesArchived() {
        NoteDto n = note("mysql", NOTE);
        List<KnowledgeBlock> archived = IndexBlocks.of(n).stream()
                .map(b -> withStatus(b, BlockMeta.STATUS_ARCHIVED)).toList();
        svc.indexNote(n, archived);
        assertTrue(svc.getIndexedCount() > 0);

        assertTrue(svc.search("元空间 MetaspaceSize", 10).isEmpty(), "archived 不应出现在人档位");
        assertFalse(svc.search("元空间 MetaspaceSize", 10, SearchProfile.HUMAN, SearchFilter.none()).isEmpty(),
                "不加过滤时应当能查到，证明排除来自 filter 而非索引缺失");
    }

    @Test
    @DisplayName("已废弃块降权：同样内容同样查询，分数正好乘以降权系数")
    void deprecatedBlockIsPenalized() {
        float draft = topScoreOf(tempDir.resolve("draft"), BlockMeta.STATUS_DRAFT);
        float deprecated = topScoreOf(tempDir.resolve("deprecated"), BlockMeta.STATUS_DEPRECATED);

        assertTrue(draft > 0);
        assertEquals(draft * 0.5f, deprecated, 1e-6f, "search.deprecated.penalty 默认 0.5");
    }

    /** 在独立索引目录里建一篇单块笔记，返回指定状态下同一查询的最高分。 */
    private float topScoreOf(Path dir, String status) {
        NoteDto n = note("mysql", "# 元空间\n\n调整 MetaspaceSize。\n");
        List<KnowledgeBlock> blocks = IndexBlocks.of(n).stream()
                .map(b -> withStatus(b, status)).toList();
        LuceneVectorSearchService other = new LuceneVectorSearchService(embedder);
        other.initialize(dir);
        try {
            assertTrue(other.isAvailable(), other.getErrorMessage());
            other.indexNote(n, blocks);
            return other.search("MetaspaceSize", 10).stream()
                    .filter(r -> "元空间".equals(r.h1Title))
                    .map(r -> r.score).findFirst().orElseThrow();
        } finally {
            other.close();
        }
    }

    @Test
    @DisplayName("AI 档位只取 visibility=ai，人档位两篇都能看到")
    void aiProfileOnlyTakesOpenNotes() {
        NoteDto human = note("mysql", NOTE);
        NoteDto open = note("redis", NOTE);
        open.visibility = NoteDto.VISIBILITY_AI;
        svc.indexNote(human, IndexBlocks.of(human));
        svc.indexNote(open, IndexBlocks.of(open));

        List<VectorSearchResult> ai = svc.search("元空间", 10, SearchProfile.AI,
                SearchFilter.forProfile(SearchProfile.AI));
        assertFalse(ai.isEmpty());
        assertTrue(ai.stream().allMatch(r -> "redis".equals(r.noteKey)), ai.toString());

        List<VectorSearchResult> people = svc.search("元空间", 10);
        assertTrue(people.stream().anyMatch(r -> "mysql".equals(r.noteKey)), people.toString());
        assertTrue(people.stream().anyMatch(r -> "redis".equals(r.noteKey)), people.toString());
    }

    @Test
    @DisplayName("只命中单篇笔记时配额不足要用剩余候选回填，不能锐减到配额条数")
    void perNoteQuotaBackfillsFromSingleNote() {
        StringBuilder body = new StringBuilder("# 参数\n");
        for (int i = 1; i <= 6; i++) {
            body.append("\n## 参数").append(i)
                    .append("\n\n调整 MetaspaceSize 与 ReservedCodeCacheSize 的第").append(i).append("项配置。\n");
        }
        NoteDto n = note("mysql", body.toString());
        List<KnowledgeBlock> blocks = IndexBlocks.of(n);
        assertTrue(blocks.size() >= 6, "六个带标题的块不应被合并: " + blocks.size());
        svc.indexNote(n, blocks);

        // 每篇配额 3，但只有一篇笔记命中，topK=5 必须靠回填凑满
        assertEquals(5, svc.search("MetaspaceSize", 5).size());
    }

    private static KnowledgeBlock withStatus(KnowledgeBlock b, String status) {
        BlockMeta meta = b.meta();
        return new KnowledgeBlock(b.id(), b.noteKey(), b.title(), b.level(), b.path(), b.body(),
                b.startLine(), b.endLine(), b.contentHash(),
                new BlockMeta(meta.type(), status, meta.supersededBy(), meta.scope(),
                        meta.sources(), meta.updatedAt()));
    }

    // ==================== 向量来源 ====================

    /** 记录每次 embed 的文本，用来断言差量索引到底重算了哪些块。 */
    private static final class CountingEmbedder implements Embedder {

        final List<String> embedded = new ArrayList<>();
        /** 命中这段文本时返回 null，模拟模型不可用；索引写入不受影响。 */
        String nullFor;

        @Override
        public float[] embed(String text) {
            embedded.add(text);
            return text.equals(nullFor) ? null : vectorOf(text);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void initialize() {
        }

        @Override
        public String getErrorMessage() {
            return null;
        }

        /** 字符桶向量：字符重叠越多余弦越高，足够断言召回，且完全确定。 */
        static float[] vectorOf(String text) {
            float[] v = new float[512];
            for (int i = 0; i < text.length(); i++) {
                v[text.charAt(i) % 512] += 1f;
            }
            double sum = 0;
            for (float f : v) {
                sum += f * f;
            }
            double norm = Math.sqrt(sum);
            if (norm == 0) {
                v[0] = 1f;
                return v;
            }
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
            return v;
        }
    }
}