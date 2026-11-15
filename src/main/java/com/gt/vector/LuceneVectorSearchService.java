package com.gt.vector;

import com.gt.AppConfig;
import com.gt.NoteDto;
import com.gt.config.ConfigKeys;
import com.gt.kb.chunk.IndexBlocks;
import com.gt.kb.model.BlockMeta;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.repo.BlockRepository;
import com.gt.kb.search.HybridSearcher;
import com.gt.kb.search.RrfFusion;
import com.gt.kb.search.RrfFusion.Channel;
import com.gt.kb.search.RrfFusion.Fused;
import com.gt.kb.search.SearchFilter;
import com.gt.kb.search.SearchProfile;
import com.gt.kb.text.IndexTextBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Lucene 9.x KNN (HNSW) 的块级混合检索服务（schema v3）。
 *
 * 一篇笔记落成若干文档：笔记名文档 + 索引块（知识块超长时二次切分为 ku_xxx#p2）。
 * doc_id 直接用块 ID，不再用 noteKey#标题#序号 —— 改标题或在中间插块都不会让其余文档换 ID，
 * 差量比对因此只重算真正变化的块。
 *
 * 检索走 {@link HybridSearcher} 的三通道 RRF 融合：精确（命令/参数/报错串）、BM25、向量。
 * 命中后索引块按 block_id 归并回所属知识块。
 */
public class LuceneVectorSearchService implements VectorSearchService {

    private static final Logger logger = LogManager.getLogger(LuceneVectorSearchService.class);

    private static final String INDEX_DIR = "vector_index";

    /**
     * 索引结构版本。docId 格式变化或字段增减时必须递增，
     * 否则旧索引里的文档匹配不上新的 docId，差量比对会全部落空。
     * 版本 3：docId 改为块 ID，字段按块级索引重排，新增分类过滤字段与 BM25 文本字段。
     * 版本 4：新增笔记更新时间存储字段，供 AI 档位返回来源信息（不参与打分，指标不受影响）。
     */
    private static final int SCHEMA_VERSION = 4;
    private static final String SCHEMA_FILE = ".schema_version";

    // 字段名
    private static final String FIELD_DOC_ID = "doc_id";
    private static final String FIELD_BLOCK_ID = "block_id";
    private static final String FIELD_NOTE_KEY = "note_key";
    private static final String FIELD_NOTE_DESC = "note_desc";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_BLOCK_TITLE = "block_title";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_SNIPPET = "snippet";
    private static final String FIELD_START_LINE = "start_line";
    private static final String FIELD_END_LINE = "end_line";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_TOPIC = "topic";
    private static final String FIELD_VISIBILITY = "visibility";
    private static final String FIELD_LAYER = "layer";
    private static final String FIELD_CONTENT_HASH = "content_hash";
    private static final String FIELD_NOTE_META = "note_meta";
    private static final String FIELD_UPDATED_AT = "updated_at";

    /** 增量比对只读这三个字段，不必把摘要等无关存储字段一起反序列化。 */
    private static final Set<String> STATE_FIELDS =
            Set.of(FIELD_DOC_ID, FIELD_CONTENT_HASH, FIELD_NOTE_META);

    /**
     * 精确通道只认查询里的 ASCII 串：命令、参数、配置项、报错。中文部分交给 BM25 与向量，
     * 硬塞进 PhraseQuery 只会因为分词粒度对不上而全部落空。
     */
    private static final Pattern EXACT_RUN =
            Pattern.compile("[-A-Za-z0-9_.=/]+(?:[ \t]+[-A-Za-z0-9_.=/]+)*");

    /** 报错串特征：命中即按 search.exact.boost 加权。 */
    private static final Pattern ERROR_HINT = Pattern.compile(
            "(?i)(error|denied|exception|fail|fatal|timeout|refused|invalid|cannot|not found|no such)");

    private final Embedder embedder;
    private Directory directory;
    private Path indexPath;
    private IndexWriter writer;
    private DirectoryReader reader;
    // searcher 会在 refreshReader 中整体替换。搜索时要在读锁内取快照后再出锁做推理，
    // 因此这里必须是 volatile，保证跨线程可见。
    private volatile IndexSearcher searcher;
    // 建索引与查询分词必须同一个实例，否则 PhraseQuery 的位置对不上
    private Analyzer analyzer;
    private boolean schemaOutdated = false;
    /** AI 档位要完整块正文，索引里只有摘要，必须回源取；未注入时退回摘要。 */
    private BlockRepository blockRepository;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private boolean available = false;
    private String errorMessage;

    public LuceneVectorSearchService() {
        this(EmbeddingService.getInstance());
    }

    LuceneVectorSearchService(Embedder embedder) {
        this.embedder = embedder;
    }

    /**
     * 初始化服务，索引目录取 user.dir/vector_index
     */
    public synchronized void initialize() {
        initialize(Paths.get(System.getProperty("user.dir"), INDEX_DIR));
    }

    synchronized void initialize(Path dir) {
        if (available) {
            return;
        }

        try {
            // 确保 embedding 服务可用
            if (!embedder.isAvailable()) {
                embedder.initialize();
            }

            if (!embedder.isAvailable()) {
                errorMessage = "Embedding 服务不可用: " + embedder.getErrorMessage();
                logger.error(errorMessage);
                return;
            }

            indexPath = dir;
            Files.createDirectories(indexPath);

            schemaOutdated = !isSchemaCurrent();

            directory = FSDirectory.open(indexPath);

            analyzer = analyzerOf(AppConfig.getInstance().getString(ConfigKeys.INDEX_ANALYZER));
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setRAMBufferSizeMB(64.0);

            writer = new IndexWriter(directory, config);

            refreshReader();

            available = true;
            logger.info("索引初始化完成，索引目录: {}", indexPath);
            logger.info("当前索引数量: {}", getIndexedCount());
            if (schemaOutdated) {
                logger.info("索引结构版本低于 {}，需要一次全量重建", SCHEMA_VERSION);
            }

        } catch (Exception e) {
            errorMessage = "索引初始化失败: " + e.getMessage();
            logger.error(errorMessage, e);
        }
    }
    
    /**
     * 索引结构是否已过期，需要全量重建
     */
    public boolean isSchemaOutdated() {
        return schemaOutdated;
    }
    
    /**
     * 全量重建完成后调用，落盘当前结构版本
     */
    public void markSchemaCurrent() {
        try {
            if (indexPath != null) {
                Files.writeString(indexPath.resolve(SCHEMA_FILE), String.valueOf(SCHEMA_VERSION));
            }
            schemaOutdated = false;
        } catch (Exception e) {
            logger.error("写入索引结构版本失败: {}", e.getMessage(), e);
        }
    }
    
    private boolean isSchemaCurrent() {
        try {
            Path marker = indexPath.resolve(SCHEMA_FILE);
            if (!Files.exists(marker)) return false;
            return SCHEMA_VERSION == Integer.parseInt(Files.readString(marker).trim());
        } catch (Exception e) {
            return false;
        }
    }
    
    // ==================== 索引写入 ====================

    /**
     * 块级差量索引一篇笔记。
     *
     * 逐块比对 content_hash：只有内容真正变化的块才重新 embed，
     * 因此一篇有上百个块的长笔记改动一处，代价也只是一次 embed。
     * 笔记级分类（domain/topic/visibility/layer/desc）变了也要整篇重算，
     * 否则过滤字段会停在旧值 —— 例如笔记改成个人域后仍对 AI 可见。
     *
     * @param note   笔记元数据，提供分类过滤字段
     * @param blocks 索引文档列表，由 {@link IndexBlocks#of} 产出
     */
    @Override
    public void indexNote(NoteDto note, List<KnowledgeBlock> blocks) {
        if (!available) {
            logger.error("服务不可用");
            return;
        }
        if (note == null || note.key == null || note.key.isEmpty()) {
            return;
        }

        Map<String, BlockState> old = getIndexedBlocks(note.key);
        String meta = noteMeta(note);

        Set<String> current = new HashSet<>();
        List<KnowledgeBlock> changed = new ArrayList<>();
        for (KnowledgeBlock block : blocks) {
            current.add(block.id());
            BlockState state = old.get(block.id());
            if (state == null || !state.contentHash().equals(block.contentHash())
                    || !state.noteMeta().equals(meta)) {
                changed.add(block);
            }
        }

        Set<String> toRemove = new HashSet<>(old.keySet());
        toRemove.removeAll(current);

        if (changed.isEmpty() && toRemove.isEmpty()) {
            logger.debug("块内容无变化，跳过索引: {}", note.key);
            return;
        }

        logger.info("块级差量索引: noteKey={}, 总块数={}, 需重算={}, 删除={}",
                note.key, blocks.size(), changed.size(), toRemove.size());

        // embed 在锁外执行，不阻塞搜索
        List<IndexedBlock> toWrite = new ArrayList<>();
        for (KnowledgeBlock block : changed) {
            float[] vector = embedder.embed(IndexTextBuilder.build(block));
            if (vector != null) {
                toWrite.add(new IndexedBlock(note, block, vector));
            } else {
                logger.error("向量生成失败: {}", block.id());
            }
        }

        differentialUpdate(note.key, toRemove, toWrite);
    }
    
    /**
     * 批量写入已计算好向量的索引块（全量重建用）。
     * 在锁内执行：先删除指定笔记的旧索引，再批量写入，最后统一 commit + refreshReader 一次。
     *
     * @param noteKeysToRemove 需要先删除旧索引的笔记 key 集合（可为 null）
     * @param blocks           已计算好向量的索引块列表
     */
    public void batchWriteWithVectors(Set<String> noteKeysToRemove, List<IndexedBlock> blocks) {
        if (!available || blocks.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            // 先删除指定笔记的旧索引
            if (noteKeysToRemove != null) {
                for (String noteKey : noteKeysToRemove) {
                    writer.deleteDocuments(new Term(FIELD_NOTE_KEY, noteKey));
                }
            }

            // 批量写入
            int count = 0;
            for (IndexedBlock block : blocks) {
                writer.updateDocument(new Term(FIELD_DOC_ID, block.block().id()), buildDocument(block));
                count++;

                // 每 100 条 commit 一次（减少内存压力）
                if (count % 100 == 0) {
                    writer.commit();
                    logger.debug("已批量写入 {} 条", count);
                }
            }

            writer.commit();
            refreshReader();

            logger.info("批量索引写入完成，共 {} 条", count);

        } catch (Exception e) {
            logger.error("批量索引写入失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 移除笔记的所有索引
     */
    public void removeNoteIndex(String noteKey) {
        if (!available) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            writer.deleteDocuments(new Term(FIELD_NOTE_KEY, noteKey));
            writer.commit();
            refreshReader();
            logger.debug("已移除笔记索引: {}", noteKey);
        } catch (Exception e) {
            logger.error("移除索引失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 查询指定笔记已索引的全部文档，返回 doc_id 到比对状态的映射。
     * 保存时用它逐块比对哈希，只对内容真正变化的块重新 embed。
     * 用 Collector 收集全部命中，没有「单篇最多 N 个块」的上限。
     */
    public Map<String, BlockState> getIndexedBlocks(String noteKey) {
        Map<String, BlockState> blocks = new HashMap<>();
        if (!available) {
            return blocks;
        }
        
        lock.readLock().lock();
        try {
            // searcher 必须在读锁内取：refreshReader 会关掉旧 reader，
            // 在锁外取到的引用可能在使用时已经失效
            IndexSearcher s = searcher;
            if (s == null) {
                return blocks;
            }
            
            List<Integer> docIds = new ArrayList<>();
            s.search(new TermQuery(new Term(FIELD_NOTE_KEY, noteKey)), new SimpleCollector() {
                private int docBase;
                
                @Override
                protected void doSetNextReader(LeafReaderContext context) {
                    this.docBase = context.docBase;
                }
                
                @Override
                public void collect(int doc) {
                    docIds.add(docBase + doc);
                }
                
                @Override
                public ScoreMode scoreMode() {
                    return ScoreMode.COMPLETE_NO_SCORES;
                }
            });
            
            StoredFields storedFields = s.storedFields();
            for (int docId : docIds) {
                Document doc = storedFields.document(docId, STATE_FIELDS);
                String id = doc.get(FIELD_DOC_ID);
                if (id != null) {
                    blocks.put(id, new BlockState(
                            Objects.requireNonNullElse(doc.get(FIELD_CONTENT_HASH), ""),
                            Objects.requireNonNullElse(doc.get(FIELD_NOTE_META), "")));
                }
            }
        } catch (Exception e) {
            logger.error("查询已索引块失败: {}", e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
        
        return blocks;
    }
    
    /**
     * 差量更新笔记索引。
     * 在写锁内执行：按 doc_id 删除消失的块，按 doc_id 覆盖写入变化的块，单次 commit + refreshReader。
     *
     * @param noteKey        笔记 key（仅用于日志）
     * @param docIdsToRemove 需要删除的 doc_id 集合
     * @param blocksToWrite  需要写入的已计算向量的块列表
     */
    public void differentialUpdate(String noteKey, Set<String> docIdsToRemove, List<IndexedBlock> blocksToWrite) {
        if (!available) {
            return;
        }

        boolean noRemove = docIdsToRemove == null || docIdsToRemove.isEmpty();
        boolean noWrite = blocksToWrite == null || blocksToWrite.isEmpty();
        if (noRemove && noWrite) {
            return;
        }

        lock.writeLock().lock();
        try {
            if (!noRemove) {
                for (String docId : docIdsToRemove) {
                    writer.deleteDocuments(new Term(FIELD_DOC_ID, docId));
                }
            }

            if (!noWrite) {
                for (IndexedBlock block : blocksToWrite) {
                    writer.updateDocument(new Term(FIELD_DOC_ID, block.block().id()), buildDocument(block));
                }
            }

            writer.commit();
            refreshReader();

            logger.info("差量索引更新: noteKey={}, 删除={}, 写入={}", noteKey,
                    noRemove ? 0 : docIdsToRemove.size(), noWrite ? 0 : blocksToWrite.size());

        } catch (Exception e) {
            logger.error("差量索引更新失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 人档位混合检索。UI 与评测都走这个入口，签名保持不变。
     *
     * @return 搜索结果列表（含 blockId、path、startLine，可精确跳转到块）
     */
    public List<VectorSearchResult> search(String query, int topK) {
        return search(query, topK, SearchProfile.HUMAN, SearchFilter.forProfile(SearchProfile.HUMAN));
    }

    /**
     * 三通道混合检索：精确 / BM25 / 向量各取 candidates 条，RRF 融合后按 block_id 归并回知识块，
     * 再按档位降权与每篇配额裁剪。
     *
     * 向量算不出来时（模型未就绪）精确与 BM25 仍然工作，不再整体返回空。
     */
    public List<VectorSearchResult> search(String query, int topK, SearchProfile profile, SearchFilter filter) {
        List<VectorSearchResult> out = new ArrayList<>();
        if (!available || query == null || query.isBlank()) {
            return out;
        }

        logger.debug("搜索查询: {}", query);

        // ONNX 推理耗时可观，放在读锁内会和索引写入互相拖累。
        // 推理本身不需要 searcher，所以先在锁外算好向量，进锁后再取 searcher。
        float[] queryVector = embedder.embed(query);

        lock.readLock().lock();
        try {
            // searcher 必须在读锁内取：refreshReader 会关掉旧 reader
            IndexSearcher s = searcher;
            if (s == null) {
                return out;
            }

            SearchContext ctx = new SearchContext(s, queryVector, filter);
            List<Fused> fused = new HybridSearcher(ctx).search(query, profile, filter);
            List<Fused> merged = RrfFusion.mergeGroups(fused, ctx::groupOf);

            double penalty = AppConfig.getInstance().getDouble(ConfigKeys.SEARCH_DEPRECATED_PENALTY);
            for (Fused hit : merged) {
                VectorSearchResult r = ctx.hits.get(hit.docKey());
                if (r == null) {
                    continue;
                }
                r.channels = hit.channels();
                // 已废弃只降权不排除：人还要看到它，并知道它已废弃
                boolean deprecated = profile == SearchProfile.HUMAN
                        && BlockMeta.STATUS_DEPRECATED.equals(r.status);
                r.score = (float) (deprecated ? hit.score() * penalty : hit.score());
                out.add(r);
            }
        } catch (Exception e) {
            logger.error("搜索失败: {}", e.getMessage(), e);
            return out;
        } finally {
            lock.readLock().unlock();
        }

        out.sort((a, b) -> Float.compare(b.score, a.score));
        List<VectorSearchResult> picked = applyPerNoteQuota(out, topK, profile.perNoteQuota());
        if (profile.fullBody()) {
            fillFullBody(picked);
        }
        return picked;
    }

    /**
     * AI 档位回源取完整正文：索引里只存摘要，且超长知识块被二次切分，
     * 命中索引段时要取所属知识块的整块正文，AI 才拿得到完整上下文。
     * 未注入块仓库时保留摘要，检索本身不受影响。
     */
    private void fillFullBody(List<VectorSearchResult> results) {
        if (blockRepository == null) {
            return;
        }
        for (VectorSearchResult r : results) {
            if (r.blockId.isEmpty()) {
                r.content = blockRepository.loadNoteText(r.noteKey);
            } else {
                blockRepository.getBlock(r.blockId).ifPresent(b -> r.content = b.body());
            }
        }
    }

    /**
     * 按笔记配额裁剪结果，避免一篇有上百个 H1 的笔记占满整个结果列表。
     *
     * 先按每篇最多 quota 条挑选，若不足 topK 再用剩下的候选按分数回填 ——
     * 回填这步不能少，否则当查询只命中单篇笔记时结果会从 topK 锐减到配额条数，比不做配额还差。
     */
    private List<VectorSearchResult> applyPerNoteQuota(List<VectorSearchResult> candidates, int topK, int quota) {
        List<VectorSearchResult> picked = new ArrayList<>();
        List<VectorSearchResult> overflow = new ArrayList<>();
        Map<String, Integer> perNote = new HashMap<>();

        for (VectorSearchResult r : candidates) {
            String key = r.noteKey != null ? r.noteKey : "";
            int used = perNote.getOrDefault(key, 0);
            if (used < quota && picked.size() < topK) {
                picked.add(r);
                perNote.put(key, used + 1);
            } else {
                overflow.add(r);
            }
        }

        for (VectorSearchResult r : overflow) {
            if (picked.size() >= topK) break;
            picked.add(r);
        }

        picked.sort((a, b) -> Float.compare(b.score, a.score));
        return picked;
    }

    // ==================== 三通道查询 ====================

    /**
     * 精确通道：把查询里的 ASCII 串逐个做成 title/text 的短语匹配。
     * 命令、参数、报错串按 search.exact.boost 加权 —— 这类查询里字面命中比语义相近可靠得多。
     */
    private Query exactQuery(String query) throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        float boost = (float) AppConfig.getInstance().getDouble(ConfigKeys.SEARCH_EXACT_BOOST);
        int clauses = 0;
        Matcher matcher = EXACT_RUN.matcher(query);
        while (matcher.find()) {
            String run = matcher.group();
            Query phrase = phraseQuery(run);
            if (phrase == null) {
                continue;
            }
            builder.add(new BoostQuery(phrase, looksLikeCommand(run) ? boost : 1f),
                    BooleanClause.Occur.SHOULD);
            clauses++;
        }
        return clauses == 0 ? null : builder.setMinimumNumberShouldMatch(1).build();
    }

    /** --force、a.b.c、key=value、path/to 与报错串：字面命中权重更高。 */
    private static boolean looksLikeCommand(String run) {
        return run.startsWith("-") || run.indexOf('.') >= 0 || run.indexOf('=') >= 0
                || run.indexOf('/') >= 0 || run.indexOf('_') >= 0
                || ERROR_HINT.matcher(run).find();
    }

    /** 同一个串在 title 与 text 上各匹配一次，任一命中即可。 */
    private Query phraseQuery(String text) throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        int clauses = 0;
        for (String field : new String[]{FIELD_TITLE, FIELD_TEXT}) {
            Query q = phraseOf(field, text);
            if (q != null) {
                builder.add(q, BooleanClause.Occur.SHOULD);
                clauses++;
            }
        }
        return clauses == 0 ? null : builder.setMinimumNumberShouldMatch(1).build();
    }

    /**
     * 用建索引的同一个分词器切串，并按 PositionIncrement 还原位置 ——
     * 停用词被删掉后位置会跳号，直接按 0,1,2 排会让短语永远匹配不上。
     */
    private Query phraseOf(String field, String text) throws IOException {
        PhraseQuery.Builder phrase = new PhraseQuery.Builder();
        String only = null;
        int count = 0;
        int position = -1;
        try (TokenStream ts = analyzer.tokenStream(field, text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                position += inc.getPositionIncrement();
                phrase.add(new Term(field, term.toString()), position);
                only = term.toString();
                count++;
            }
            ts.end();
        }
        if (count == 0) {
            return null;
        }
        return count == 1 ? new TermQuery(new Term(field, only)) : phrase.build();
    }

    /**
     * BM25 通道：查询整体分词后，每个词在 title（加权）与 text 上各出一个 TermQuery。
     * 中文按字/词切分后常用字 IDF 极低，天然不会把「的」这类字当成强信号。
     */
    private Query bm25Query(String query) throws IOException {
        Set<String> terms = new LinkedHashSet<>(termsOf(query));
        if (terms.isEmpty()) {
            return null;
        }
        float titleBoost = (float) AppConfig.getInstance().getDouble(ConfigKeys.SEARCH_TITLE_BOOST);
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String term : terms) {
            builder.add(new BoostQuery(new TermQuery(new Term(FIELD_TITLE, term)), titleBoost),
                    BooleanClause.Occur.SHOULD);
            builder.add(new TermQuery(new Term(FIELD_TEXT, term)), BooleanClause.Occur.SHOULD);
        }
        return builder.setMinimumNumberShouldMatch(1).build();
    }

    private List<String> termsOf(String text) throws IOException {
        List<String> terms = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream(FIELD_TEXT, text)) {
            CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                terms.add(attr.toString());
            }
            ts.end();
        }
        return terms;
    }

    /**
     * 过滤落到 Lucene 的 FILTER / MUST_NOT，在打分前生效，不占候选名额。
     * 同一字段的多个取值是「或」，字段之间是「与」。
     */
    private static Query filterQuery(SearchFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        int positive = 0;
        positive += include(builder, FIELD_DOMAIN, filter.domains());
        positive += include(builder, FIELD_TOPIC, filter.topics());
        positive += include(builder, FIELD_TYPE, filter.types());
        positive += include(builder, FIELD_STATUS, filter.statuses());
        positive += include(builder, FIELD_VISIBILITY, filter.visibilities());
        positive += include(builder, FIELD_LAYER, filter.layers());

        int negative = 0;
        for (String value : filter.excludeStatuses()) {
            builder.add(new TermQuery(new Term(FIELD_STATUS, value)), BooleanClause.Occur.MUST_NOT);
            negative++;
        }
        for (String value : filter.excludeLayers()) {
            builder.add(new TermQuery(new Term(FIELD_LAYER, value)), BooleanClause.Occur.MUST_NOT);
            negative++;
        }
        if (positive + negative == 0) {
            return null;
        }
        // 只有 MUST_NOT 的 BooleanQuery 在 Lucene 里匹配不到任何文档，必须补一个全集
        if (positive == 0) {
            builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.FILTER);
        }
        return builder.build();
    }

    private static int include(BooleanQuery.Builder outer, String field, Set<String> values) {
        if (values.isEmpty()) {
            return 0;
        }
        BooleanQuery.Builder inner = new BooleanQuery.Builder();
        for (String value : values) {
            inner.add(new TermQuery(new Term(field, value)), BooleanClause.Occur.SHOULD);
        }
        outer.add(inner.setMinimumNumberShouldMatch(1).build(), BooleanClause.Occur.FILTER);
        return 1;
    }

    /** 分词器可切换；切换后必须全量重建索引，故 index.analyzer 标了 needsReindex。 */
    private static Analyzer analyzerOf(String name) {
        if ("smartcn".equalsIgnoreCase(name)) {
            return new SmartChineseAnalyzer();
        }
        return new StandardAnalyzer();
    }

    private static VectorSearchResult hydrate(Document doc) {
        VectorSearchResult r = new VectorSearchResult();
        r.docId = doc.get(FIELD_DOC_ID);
        r.noteKey = doc.get(FIELD_NOTE_KEY);
        r.noteDesc = doc.get(FIELD_NOTE_DESC);
        r.blockId = Objects.requireNonNullElse(doc.get(FIELD_BLOCK_ID), "");
        r.h1Title = doc.get(FIELD_BLOCK_TITLE);
        r.sectionTitle = nullToEmpty(doc.get(FIELD_TITLE));
        r.path = doc.get(FIELD_PATH);
        r.startLine = intField(doc, FIELD_START_LINE);
        r.content = doc.get(FIELD_SNIPPET);
        r.status = Objects.requireNonNullElse(doc.get(FIELD_STATUS), "");
        r.updatedAt = longField(doc, FIELD_UPDATED_AT);
        return r;
    }

    /**
     * 归并身份：同一知识块下同一小标题的多个索引段算一条；笔记名文档 block_id 为空，自成一组。
     *
     * 用段自身的 title 而非 block_title：超长知识块二次切分后各段分属不同小节，
     * block_title 已被 alignToParent 统一成父块标题，拿它归并会把整个父块塌成一条，
     * 分数低的小节永远无法单独出现。同一小节被按段落切成多段时 title 相同，仍然归并。
     */
    private static String groupKeyOf(Document doc, String docId) {
        String blockId = Objects.requireNonNullElse(doc.get(FIELD_BLOCK_ID), "");
        if (blockId.isEmpty()) {
            return docId;
        }
        return nullToEmpty(doc.get(FIELD_NOTE_KEY)) + "\n" + blockId
                + "\n" + nullToEmpty(doc.get(FIELD_TITLE));
    }
    
    /** 注入块仓库，AI 档位据此回源取完整正文。不注入则只返回索引里的摘要。 */
    public void setBlockRepository(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    // ==================== VectorSearchService 接口实现 ====================
    
    @Override
    public void removeIndex(String noteKey) {
        removeNoteIndex(noteKey);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public String getServiceName() {
        return "Lucene KNN (HNSW)";
    }
    
    @Override
    public void clearAll() {
        if (!available) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            writer.deleteAll();
            writer.commit();
            refreshReader();
            logger.info("已清空所有索引");
        } catch (Exception e) {
            logger.error("清空索引失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public int getIndexedCount() {
        if (reader == null) {
            return 0;
        }
        return reader.numDocs();
    }
    
    /**
     * 获取错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 关闭服务
     */
    public synchronized void close() {
        lock.writeLock().lock();
        try {
            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (directory != null) {
                directory.close();
                directory = null;
            }
            searcher = null;
            available = false;
        } catch (Exception e) {
            logger.error("关闭索引失败: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ==================== 私有方法 ====================
    
    private void refreshReader() throws IOException {
        if (reader == null) {
            reader = DirectoryReader.open(writer);
        } else {
            DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
            if (newReader != null) {
                reader.close();
                reader = newReader;
            }
        }
        searcher = new IndexSearcher(reader);
    }
    
    private Document buildDocument(IndexedBlock indexed) {
        KnowledgeBlock block = indexed.block();
        NoteDto note = indexed.note();
        String text = IndexTextBuilder.build(block);

        Document doc = new Document();
        doc.add(new StringField(FIELD_DOC_ID, block.id(), Field.Store.YES));
        // 笔记名文档不属于任何知识块，block_id 留空：UI 只显示笔记，跳转不定位块
        doc.add(new StringField(FIELD_BLOCK_ID,
                IndexBlocks.isNoteDoc(block) ? "" : block.parentBlockId(), Field.Store.YES));
        doc.add(new StringField(FIELD_NOTE_KEY, block.noteKey(), Field.Store.YES));
        doc.add(new StoredField(FIELD_NOTE_DESC, IndexBlocks.descOf(note)));
        doc.add(new TextField(FIELD_TITLE, block.title(), Field.Store.YES));
        doc.add(new StoredField(FIELD_BLOCK_TITLE, IndexBlocks.blockTitleOf(block)));
        doc.add(new StoredField(FIELD_PATH, block.pathText()));
        doc.add(new TextField(FIELD_TEXT, text, Field.Store.NO));
        doc.add(new StoredField(FIELD_SNIPPET,
                truncate(text, AppConfig.getInstance().getInt(ConfigKeys.INDEX_SNIPPET_MAX_CHARS))));
        doc.add(new StoredField(FIELD_START_LINE, block.startLine()));
        doc.add(new StoredField(FIELD_END_LINE, block.endLine()));
        doc.add(new StringField(FIELD_TYPE, blockType(block, note), Field.Store.YES));
        doc.add(new StringField(FIELD_STATUS, block.meta().status(), Field.Store.YES));
        doc.add(new StringField(FIELD_DOMAIN, nullToEmpty(note.domain), Field.Store.YES));
        doc.add(new StringField(FIELD_TOPIC, nullToEmpty(note.topic), Field.Store.YES));
        doc.add(new StringField(FIELD_VISIBILITY, nullToEmpty(note.effectiveVisibility()), Field.Store.YES));
        doc.add(new StringField(FIELD_LAYER, nullToEmpty(note.layer), Field.Store.YES));
        doc.add(new StoredField(FIELD_CONTENT_HASH, block.contentHash()));
        doc.add(new StoredField(FIELD_NOTE_META, noteMeta(note)));
        doc.add(new StoredField(FIELD_UPDATED_AT, note.updatedAt));
        doc.add(new KnnFloatVectorField(FIELD_VECTOR, indexed.vector(), VectorSimilarityFunction.COSINE));
        return doc;
    }

    /** 块级 type 为空表示继承笔记的 noteType。 */
    private static String blockType(KnowledgeBlock block, NoteDto note) {
        String type = block.meta().type();
        return type.isEmpty() ? nullToEmpty(note.noteType) : type;
    }

    /**
     * 笔记级分类签名。块哈希只覆盖面包屑/标题/正文，分类改了哈希不变，
     * 必须单独比对，否则 visibility 等过滤字段会停在旧值。
     */
    private static String noteMeta(NoteDto note) {
        return String.join("\n",
                IndexBlocks.descOf(note),
                nullToEmpty(note.domain),
                nullToEmpty(note.topic),
                nullToEmpty(note.noteType),
                nullToEmpty(note.layer),
                nullToEmpty(note.effectiveVisibility()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 行号、时间戳以数值型 StoredField 落盘，doc.get 取不到，必须走 numericValue。 */
    private static long longField(Document doc, String name) {
        IndexableField field = doc.getField(name);
        Number value = field == null ? null : field.numericValue();
        return value == null ? 0L : value.longValue();
    }

    private static int intField(Document doc, String name) {
        return (int) longField(doc, name);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
    
    // ==================== 内部类 ====================

    /**
     * 待写入的索引文档：块 + 所属笔记元数据 + 已算好的向量。
     * 向量在锁外算，笔记元数据提供分类过滤字段。
     */
    public record IndexedBlock(NoteDto note, KnowledgeBlock block, float[] vector) {
    }

    /** 差量比对依据：块内容哈希 + 笔记级分类签名，任一不同就要重算。 */
    public record BlockState(String contentHash, String noteMeta) {
    }

    /** 一个通道的查询构造器，允许抛 IOException 由调用方统一兜住。 */
    private interface ChannelQuery {
        Query build() throws IOException;
    }

    /**
     * 一次检索的上下文：三个通道共用一份文档缓存，谁先命中谁回查，后续通道直接复用。
     *
     * filter 在构造时绑定，通道方法收到的 filter 参数因此不再使用 ——
     * 同一次检索的三个通道必须用同一套过滤，否则融合出来的名次没有可比性。
     */
    private final class SearchContext implements HybridSearcher.Channels {

        private final IndexSearcher index;
        private final StoredFields storedFields;
        private final float[] queryVector;
        private final Query filterQuery;
        private final Map<String, VectorSearchResult> hits = new HashMap<>();
        private final Map<String, String> groups = new HashMap<>();

        SearchContext(IndexSearcher index, float[] queryVector, SearchFilter filter) throws IOException {
            this.index = index;
            this.storedFields = index.storedFields();
            this.queryVector = queryVector;
            this.filterQuery = filterQuery(filter);
        }

        String groupOf(String docId) {
            return groups.getOrDefault(docId, docId);
        }

        @Override
        public List<String> exact(String query, int limit, SearchFilter filter) {
            return run(() -> exactQuery(query), limit, false);
        }

        @Override
        public List<String> bm25(String query, int limit, SearchFilter filter) {
            return run(() -> bm25Query(query), limit, false);
        }

        @Override
        public List<String> vector(String query, int limit, SearchFilter filter) {
            // 过滤必须进 KNN 内部：先取全局 top-k 再筛，命中数会远不足 limit
            return run(() -> {
                if (queryVector == null) {
                    return null;
                }
                return filterQuery == null
                        ? new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, limit)
                        : new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, limit, filterQuery);
            }, limit, true);
        }

        /**
         * 执行一个通道，回查命中文档，返回按相关性降序且去重的 doc_id（RRF 的输入契约）。
         *
         * @param alreadyFiltered 通道内部已带过滤（KNN），不必再套一层
         */
        private List<String> run(ChannelQuery supplier, int limit, boolean alreadyFiltered) {
            try {
                Query q = supplier.build();
                if (q == null) {
                    return List.of();
                }
                if (!alreadyFiltered && filterQuery != null) {
                    q = new BooleanQuery.Builder()
                            .add(q, BooleanClause.Occur.MUST)
                            .add(filterQuery, BooleanClause.Occur.FILTER)
                            .build();
                }
                TopDocs top = index.search(q, Math.max(1, limit));
                Set<String> ids = new LinkedHashSet<>();
                for (ScoreDoc sd : top.scoreDocs) {
                    Document doc = storedFields.document(sd.doc);
                    String docId = doc.get(FIELD_DOC_ID);
                    if (docId == null) {
                        continue;
                    }
                    if (!hits.containsKey(docId)) {
                        hits.put(docId, hydrate(doc));
                        groups.put(docId, groupKeyOf(doc, docId));
                    }
                    ids.add(docId);
                }
                return new ArrayList<>(ids);
            } catch (IOException e) {
                logger.error("通道检索失败: {}", e.getMessage(), e);
                return List.of();
            }
        }
    }

    /**
     * 混合检索结果。
     * blockId 为空表示命中的是笔记名文档（整篇笔记），跳转时只打开笔记不定位块。
     */
    public static class VectorSearchResult {
        public String docId;      // 索引文档 ID：块 ID 或 key#note
        public String noteKey;    // 快捷命令
        public String noteDesc;   // 描述
        public String blockId;    // 所属知识块 ID（ku_xxx）
        public String h1Title;    // 所属知识块标题
        public String sectionTitle = ""; // 索引段自身的小标题，与 h1Title 相同则不额外展示
        public String path;       // 面包屑
        public int startLine;     // 块首行号（1 起，相对去掉 front matter 的正文）
        public String content;    // 人档位是摘要，AI 档位回源取完整正文
        public String status;     // 块状态，deprecated 要在 UI 上标出来
        public long updatedAt;    // 所属笔记更新时间，AI 档位判断知识新鲜度用
        public Set<Channel> channels = Set.of();  // 命中通道，调参时看哪个通道起了作用
        public float score;       // RRF 融合分，已废弃块再乘降权系数

        @Override
        public String toString() {
            String title = sectionTitle.isEmpty() || sectionTitle.equals(h1Title)
                    ? h1Title : h1Title + " > " + sectionTitle;
            return String.format("[%.4f %s] %s (%s) # %s @%d",
                score, channels, noteKey, noteDesc, title, startLine);
        }
    }
}

