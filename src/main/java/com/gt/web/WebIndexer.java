package com.gt.web;

import com.gt.AppConfig;
import com.gt.NoteDto;
import com.gt.config.ConfigKeys;
import com.gt.kb.chunk.IndexBlocks;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.repo.BlockRepository;
import com.gt.kb.search.SearchFilter;
import com.gt.kb.search.SearchProfile;
import com.gt.kb.text.IndexTextBuilder;
import com.gt.storage.NoteFileStorage;
import com.gt.vector.EmbeddingService;
import com.gt.vector.LuceneVectorSearchService;
import com.gt.vector.ModelManager;
import com.gt.vector.VectorSearchFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网页端的索引与检索入口。
 *
 * 不复用 VectorSearchManager：那个类会创建 Swing 面板、往桌面窗口写启动状态。
 * 这里直接驱动同一套 EmbeddingService + LuceneVectorSearchService，
 * 切分、索引文本、差量比对、三通道融合全部走原实现，检索口径因此与桌面一致。
 */
public final class WebIndexer implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(WebIndexer.class);
    private static final int BATCH_FLUSH_SIZE = 50;

    private final WebEvents events;
    private final BlockRepository blockRepository;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Web-Indexer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);

    private volatile LuceneVectorSearchService service;
    private volatile String state = "idle";
    private volatile String message = "";
    private volatile int current;
    private volatile int total;

    public WebIndexer(WebEvents events, BlockRepository blockRepository) {
        this.events = events;
        this.blockRepository = blockRepository;
    }

    /** 后台初始化：加载模型 → 打开索引 → 必要时全量重建。不阻塞 HTTP 启动。 */
    public void start(Path notesDir) {
        worker.submit(() -> {
            try {
                initialize(notesDir);
            } catch (Exception e) {
                state = "error";
                message = e.getMessage() == null ? e.toString() : e.getMessage();
                logger.error("[Web] 索引初始化失败: {}", message, e);
                publishStatus();
            }
        });
    }

    private void initialize(Path notesDir) {
        String provider = AppConfig.getInstance().getString("vector.provider", "none");
        if (!"lucene".equalsIgnoreCase(provider)) {
            state = "disabled";
            message = "vector.provider=" + provider + "，语义检索未启用";
            publishStatus();
            return;
        }
        if (!ModelManager.isModelDownloaded()) {
            state = "model-missing";
            message = "模型未下载，语义检索不可用（精确与关键词通道仍可用）";
            publishStatus();
            return;
        }
        state = "loading";
        message = "正在加载向量模型…";
        publishStatus();

        EmbeddingService.getInstance().initialize();
        service = VectorSearchFactory.getLuceneService();
        if (service == null || !service.isAvailable()) {
            state = "error";
            message = service == null ? "Lucene 服务未创建" : service.getErrorMessage();
            publishStatus();
            return;
        }
        service.setBlockRepository(blockRepository);

        if (service.getIndexedCount() == 0 || service.isSchemaOutdated()) {
            rebuild(notesDir);
        } else {
            state = "ready";
            message = "索引就绪";
            publishStatus();
        }
    }

    /** 全量重建。与桌面同一套切分与索引文本，只是进度改走 SSE。 */
    public void rebuild(Path notesDir) {
        if (service == null || !service.isAvailable()) {
            message = "检索服务不可用，无法重建";
            publishStatus();
            return;
        }
        if (!rebuilding.compareAndSet(false, true)) {
            return;
        }
        worker.submit(() -> {
            try {
                state = "rebuilding";
                current = 0;
                total = 0;
                message = "正在重建索引…";
                publishStatus();

                service.clearAll();
                if (!Files.exists(notesDir)) {
                    return;
                }
                List<NoteDto> notes = new NoteFileStorage(notesDir.toAbsolutePath().getParent().toString())
                        .scanAllNotes();
                total = notes.size();
                List<LuceneVectorSearchService.IndexedBlock> batch = new ArrayList<>();
                int done = 0;
                for (NoteDto note : notes) {
                    for (KnowledgeBlock block : IndexBlocks.of(note)) {
                        float[] vector = EmbeddingService.getInstance().embed(IndexTextBuilder.build(block));
                        if (vector != null) {
                            batch.add(new LuceneVectorSearchService.IndexedBlock(note, block, vector));
                        }
                        if (batch.size() >= BATCH_FLUSH_SIZE) {
                            service.batchWriteWithVectors(null, batch);
                            batch = new ArrayList<>();
                        }
                    }
                    done++;
                    current = done;
                    if (done % 5 == 0 || done == total) {
                        publishStatus();
                    }
                }
                if (!batch.isEmpty()) {
                    service.batchWriteWithVectors(null, batch);
                }
                service.markSchemaCurrent();
                state = "ready";
                message = "索引就绪";
                logger.info("[Web] 全量索引完成，共 {} 条", service.getIndexedCount());
            } catch (Exception e) {
                state = "error";
                message = "重建失败: " + e.getMessage();
                logger.error("[Web] 全量索引失败: {}", e.getMessage(), e);
            } finally {
                rebuilding.set(false);
                publishStatus();
            }
        });
    }

    /** 保存后差量索引；私密与已删除笔记由 IndexBlocks.of 返回空列表自然排除。 */
    public void indexNote(NoteDto note) {
        if (service == null || note == null || note.key == null || note.key.isEmpty()) {
            return;
        }
        NoteDto snapshot = snapshotOf(note);
        worker.submit(() -> {
            try {
                if (snapshot.encrypted || snapshot.deleted) {
                    service.removeNoteIndex(snapshot.key);
                } else {
                    service.indexNote(snapshot, IndexBlocks.of(snapshot));
                }
                publishStatus();
            } catch (Exception e) {
                logger.error("[Web] 索引笔记失败 {}: {}", snapshot.key, e.getMessage());
            }
        });
    }

    public void removeNoteIndex(String noteKey) {
        if (service == null || noteKey == null || noteKey.isEmpty()) {
            return;
        }
        worker.submit(() -> {
            try {
                service.removeNoteIndex(noteKey);
                publishStatus();
            } catch (Exception e) {
                logger.error("[Web] 移除索引失败 {}: {}", noteKey, e.getMessage());
            }
        });
    }

    public List<LuceneVectorSearchService.VectorSearchResult> search(String query, SearchProfile profile,
                                                                   SearchFilter filter) {
        if (service == null || !service.isAvailable()) {
            return List.of();
        }
        return service.search(query, profile.topK(), profile, filter);
    }

    public boolean available() {
        return service != null && service.isAvailable();
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", state);
        out.put("message", message);
        out.put("available", available());
        out.put("indexedCount", service == null ? 0 : service.getIndexedCount());
        out.put("schemaOutdated", service != null && service.isSchemaOutdated());
        out.put("rebuilding", rebuilding.get());
        out.put("current", current);
        out.put("total", total);
        out.put("modelDownloaded", ModelManager.isModelDownloaded());
        out.put("provider", AppConfig.getInstance().getString("vector.provider", "none"));
        out.put("dimension", EmbeddingService.getInstance().getVectorDimension());
        out.put("weights", com.gt.kb.search.HybridSearcher.weights().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new)));
        return out;
    }

    private void publishStatus() {
        events.publish("index", status());
    }

    private static NoteDto snapshotOf(NoteDto note) {
        NoteDto copy = new NoteDto();
        copy.id = note.id;
        copy.key = note.key;
        copy.title = note.title;
        copy.desc = note.desc;
        copy.bodyMd = note.bodyMd;
        copy.domain = note.domain;
        copy.topic = note.topic;
        copy.noteType = note.noteType;
        copy.layer = note.layer;
        copy.visibility = note.visibility;
        copy.deleted = note.deleted;
        copy.encrypted = note.encrypted;
        copy.updatedAt = note.updatedAt;
        return copy;
    }

    @Override
    public void close() {
        worker.shutdownNow();
        if (service != null) {
            service.close();
        }
    }
}