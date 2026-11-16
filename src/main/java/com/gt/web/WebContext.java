package com.gt.web;

import com.gt.AppConfig;
import com.gt.DbSyncService;
import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.config.ConfigKeys;
import com.gt.kb.KbDirectory;
import com.gt.kb.Taxonomy;
import com.gt.kb.TaxonomyGuesser;
import com.gt.kb.id.BlockIdAssigner;
import com.gt.kb.model.BlockSummary;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.repo.BlockLocation;
import com.gt.kb.search.SearchFilter;
import com.gt.kb.search.SearchProfile;
import com.gt.service.NoteService;
import com.gt.storage.NoteFileStorage;
import com.gt.vector.EmbeddingService;
import com.gt.vector.LuceneVectorSearchService;
import com.gt.web.WebDtos.AssetOutcome;
import com.gt.web.WebDtos.BlockDetail;
import com.gt.web.WebDtos.ConfigItem;
import com.gt.web.WebDtos.ConfigRequest;
import com.gt.web.WebDtos.ConfigResponse;
import com.gt.web.WebDtos.CryptoPayload;
import com.gt.web.WebDtos.DeleteOutcome;
import com.gt.web.WebDtos.GuessResult;
import com.gt.web.WebDtos.NoteDetail;
import com.gt.web.WebDtos.NoteMeta;
import com.gt.web.WebDtos.SaveOutcome;
import com.gt.web.WebDtos.SaveRequest;
import com.gt.web.WebDtos.SearchHit;
import com.gt.web.WebDtos.SearchResponse;
import com.gt.web.WebDtos.Suggestion;
import com.gt.web.WebDtos.SyncOutcome;
import com.gt.web.WebDtos.SyncStatus;
import com.gt.web.WebDtos.TaxonomyDomain;
import com.gt.web.WebDtos.TaxonomyResponse;
import com.gt.web.WebDtos.TaxonomyTopic;
import com.gt.web.WebDtos.WebException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 网页端的业务层：所有读写都落到桌面端同一套核心上。
 *
 * 这里不新造存储、不新造切分、不新造检索，只做三件事：
 * 把核心对象翻译成接口形状、把浏览器的 E2E 密文原样交给核心落盘、把变更广播成 SSE。
 * 写操作串行化，避免两个请求同时改同一篇时版本号互相盖掉。
 */
public final class WebContext implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(WebContext.class);
    private static final long UNDO_MILLIS = 60_000L;
    private static final Set<String> EXTRA_WRITABLE_KEYS = Set.of("vector.provider");

    private final Path baseDir;
    private final NoteRepository repository;
    private final NoteFileStorage storage;
    private final NoteService service;
    private final WebIndexer indexer;
    private final WebEvents events;
    private final WebAuth auth = new WebAuth();
    private final Object writeLock = new Object();
    private final long startedAt = System.currentTimeMillis();

    private volatile String lastSyncMessage = "尚未同步";
    private volatile long lastSyncAt;

    public WebContext(Path baseDir, WebEvents events) {
        this.baseDir = baseDir;
        this.events = events;
        this.repository = new NoteRepository(baseDir.resolve("fastpig.db").toString());
        this.storage = new NoteFileStorage(baseDir.toString());
        this.service = new NoteService(repository, storage);
        this.indexer = new WebIndexer(events, service.blockRepository());
        DbSyncService.getInstance().setNoteService(service);
    }

    public void boot() {
        long t0 = System.currentTimeMillis();
        int count = service.rebuildIndexFromFiles();
        logger.info("[Web] 索引自愈完成，{} 篇，耗时 {}ms", count, System.currentTimeMillis() - t0);
        events.publish("notes", Map.of("reason", "boot", "count", count));
        indexer.start(storage.getNotesDir());
    }

    public WebAuth auth() {
        return auth;
    }

    public WebEvents events() {
        return events;
    }

    public WebIndexer indexer() {
        return indexer;
    }

    public NoteService noteService() {
        return service;
    }

    // ==================== 笔记 ====================

    public List<NoteMeta> listNotes(boolean includeDeleted) {
        Map<String, Integer> counts = service.blockRepository().blockCounts();
        List<NoteDto> source = includeDeleted
                ? repository.findAll().stream().filter(n -> n.deleted).toList()
                : repository.findAllMetadata();
        List<NoteMeta> out = new ArrayList<>(source.size());
        for (NoteDto n : source) {
            if (n.key == null || n.key.isBlank()) {
                continue;
            }
            out.add(metaOf(n, counts.getOrDefault(n.key, 0)));
        }
        out.sort(Comparator.comparing(NoteMeta::key));
        return out;
    }

    public NoteDetail getNote(String key) {
        NoteDto note = service.loadByKey(key);
        if (note == null) {
            throw new WebException(404, "笔记不存在: " + key);
        }
        return detailOf(note, blockCountOf(key));
    }

    public SaveOutcome createNote(SaveRequest req) {
        String key = requireKey(req.key);
        synchronized (writeLock) {
            if (repository.findByKey(key) != null) {
                throw new WebException(409, "快捷命令已存在: " + key);
            }
            NoteDto note = new NoteDto();
            note.id = UUID.randomUUID().toString();
            note.key = key;
            note.desc = orEmpty(req.desc);
            note.title = req.title == null || req.title.isBlank() ? note.desc : req.title;
            note.tags = req.tags == null ? new ArrayList<>() : new ArrayList<>(req.tags);
            note.aliases = req.aliases == null ? new ArrayList<>() : new ArrayList<>(req.aliases);
            note.bodyMd = orEmpty(req.bodyMd);
            long now = System.currentTimeMillis();
            note.createdAt = now;
            note.updatedAt = now;
            note.version = 1;
            applyClassification(note, req);
            if (NoteDto.DEFAULT_DOMAIN.equals(note.domain)) {
                TaxonomyGuesser.Guess guess = TaxonomyGuesser.matchKeyword(taxonomy(), note.key, note.desc);
                if (guess != null) {
                    note.domain = guess.domain();
                    note.topic = guess.topic();
                }
            }
            return persist(note, req, true);
        }
    }

    public SaveOutcome saveNote(String key, SaveRequest req) {
        synchronized (writeLock) {
            NoteDto note = service.loadByKey(key);
            if (note == null) {
                throw new WebException(404, "笔记不存在: " + key);
            }
            if (req.key != null && !req.key.isBlank() && !req.key.equals(key)) {
                throw new WebException(409, "不支持修改 key，请新建笔记后删除旧笔记");
            }
            if (req.expectedVersion != null && req.expectedVersion > 0 && note.version > req.expectedVersion) {
                throw new WebException(409, "这篇笔记已被更新到 v" + note.version
                        + "（你编辑的是 v" + req.expectedVersion + "），请重新加载后再保存");
            }
            if (note.encrypted && !Boolean.TRUE.equals(req.encrypted)) {
                // 取消私密：浏览器已解密并回传明文，清掉信封字段后按普通笔记保存
                note.encrypted = false;
                note.cipherIv = null;
                note.pwdSalt = null;
                note.pwdIv = null;
                note.pwdWrappedDek = null;
                note.masterSalt = null;
                note.masterIv = null;
                note.masterWrappedDek = null;
                note.assets = null;
                note.bodyMd = orEmpty(req.bodyMd);
            } else if (Boolean.TRUE.equals(req.encrypted)) {
                applyEnvelope(note, req);
            } else {
                if (note.encrypted) {
                    throw new WebException(423, "笔记处于锁定状态，无法保存");
                }
                note.bodyMd = orEmpty(req.bodyMd);
            }
            note.desc = req.desc == null ? note.desc : req.desc;
            note.title = req.title == null || req.title.isBlank() ? note.desc : req.title;
            if (req.tags != null) {
                note.tags = new ArrayList<>(req.tags);
            }
            if (req.aliases != null) {
                note.aliases = new ArrayList<>(req.aliases);
            }
            applyClassification(note, req);
            note.updatedAt = System.currentTimeMillis();
            return persist(note, req, false);
        }
    }

    private SaveOutcome persist(NoteDto note, SaveRequest req, boolean created) {
        boolean sealed = note.encrypted;
        BlockIdAssigner.Result ids = sealed
                ? service.saveSealed(note, req.plaintextHash)
                : service.save(note);
        if (sealed) {
            indexer.removeNoteIndex(note.key);
        } else {
            indexer.indexNote(note);
        }
        NoteDetail detail = detailOf(note, blockCountOf(note.key));
        events.publish("notes", Map.of("reason", created ? "created" : "saved", "key", note.key,
                "version", note.version, "encrypted", note.encrypted));
        return new SaveOutcome(detail, ids, created);
    }

    public DeleteOutcome deleteNote(String key) {
        synchronized (writeLock) {
            NoteDto note = repository.findByKey(key);
            if (note == null) {
                throw new WebException(404, "笔记不存在: " + key);
            }
            service.delete(note.id);
            indexer.removeNoteIndex(key);
            events.publish("notes", Map.of("reason", "deleted", "key", key));
            return new DeleteOutcome(key, System.currentTimeMillis() + UNDO_MILLIS);
        }
    }

    public NoteDetail restoreNote(String key) {
        synchronized (writeLock) {
            service.restore(key);
            NoteDto note = service.loadByKey(key);
            if (note == null) {
                throw new WebException(404, "笔记不存在: " + key);
            }
            indexer.indexNote(note);
            events.publish("notes", Map.of("reason", "restored", "key", key));
            return detailOf(note, blockCountOf(key));
        }
    }

    public List<BlockSummary> blocks(String key) {
        requireNote(key);
        return service.blockRepository().listBlockSummaries(key);
    }

    public BlockDetail block(String blockId) {
        KnowledgeBlock block = service.blockRepository().getBlock(blockId)
                .orElseThrow(() -> new WebException(404, "知识块不存在: " + blockId));
        BlockLocation loc = service.blockRepository().locate(block.parentBlockId()).orElse(null);
        return new BlockDetail(block.id(), block.noteKey(), block.title(), block.level(), block.path(),
                block.body(), block.startLine(), block.endLine(), block.meta().status(),
                loc == null ? null : baseDir.relativize(loc.file()).toString().replace('\\', '/'),
                loc == null ? 0 : loc.startLine(), loc == null ? 0 : loc.endLine());
    }

    // ==================== 目录与分类 ====================

    public KbDirectory.Snapshot tree() {
        return KbDirectory.build(repository.findAllMetadata(), taxonomy(),
                service.blockRepository().blockCounts());
    }

    public Taxonomy taxonomy() {
        return Taxonomy.loadReadOnly(storage.getNotesDir());
    }

    public TaxonomyResponse taxonomyDto() {
        Taxonomy t = taxonomy();
        List<TaxonomyDomain> domains = new ArrayList<>();
        for (Taxonomy.Domain d : t.domains()) {
            List<TaxonomyTopic> topics = new ArrayList<>();
            for (Taxonomy.Topic tp : d.topics()) {
                topics.add(new TaxonomyTopic(tp.name(), tp.aliases()));
            }
            domains.add(new TaxonomyDomain(d.name(), topics));
        }
        return new TaxonomyResponse(t.version(), domains,
                List.of("知识", "速查", "约定", "日志", "随笔"),
                List.of(NoteDto.LAYER_CURATED, NoteDto.LAYER_RAW),
                List.of(NoteDto.VISIBILITY_HUMAN, NoteDto.VISIBILITY_AI, NoteDto.VISIBILITY_PRIVATE));
    }

    public GuessResult guess(String key) {
        NoteDto note = service.loadByKey(key);
        if (note == null) {
            throw new WebException(404, "笔记不存在: " + key);
        }
        TaxonomyGuesser.Guess keyword = TaxonomyGuesser.matchKeyword(taxonomy(), note.key, note.desc);
        if (keyword != null) {
            return new GuessResult(keyword.domain(), keyword.topic(), keyword.source());
        }
        if (!EmbeddingService.getInstance().isAvailable()) {
            return new GuessResult(NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC, "unavailable");
        }
        TaxonomyGuesser guesser = new TaxonomyGuesser(taxonomy(), EmbeddingService.getInstance()::embed,
                AppConfig.getInstance().getDouble(ConfigKeys.KB_GUESS_MIN_SCORE));
        TaxonomyGuesser.Guess guess = guesser.guess(note.key, note.desc, note.bodyMd, repository.findAll());
        return new GuessResult(guess.domain(), guess.topic(), guess.source());
    }

    // ==================== 检索与联想 ====================

    public SearchResponse search(String query, String tier, Map<String, String> params) {
        long t0 = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            throw new WebException(400, "查询不能为空");
        }
        SearchProfile profile = "ai".equalsIgnoreCase(tier) ? SearchProfile.AI : SearchProfile.HUMAN;
        SearchFilter base = SearchFilter.forProfile(profile);
        SearchFilter filter = new SearchFilter(
                setParam(params.get("domain")), setParam(params.get("topic")), setParam(params.get("type")),
                base.statuses(), setParam(params.get("visibility")), setParam(params.get("layer")),
                base.excludeStatuses(), base.excludeLayers());

        List<LuceneVectorSearchService.VectorSearchResult> raw = indexer.search(query.strip(), profile, filter);
        List<SearchHit> hits = new ArrayList<>(raw.size());
        for (LuceneVectorSearchService.VectorSearchResult r : raw) {
            List<String> channels = new ArrayList<>();
            r.channels.forEach(c -> channels.add(c.name()));
            hits.add(new SearchHit(r.docId, r.noteKey, r.noteDesc, r.blockId, r.h1Title, r.sectionTitle,
                    r.path, r.startLine, r.content, r.status, r.updatedAt, channels, r.score));
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        com.gt.kb.search.HybridSearcher.weights().forEach((k, v) -> weights.put(k.name(), v));
        String message = indexer.available() ? "" : String.valueOf(indexer.status().get("message"));
        return new SearchResponse(query.strip(), profile.name().toLowerCase(Locale.ROOT), hits, hits.size(),
                System.currentTimeMillis() - t0, indexer.available(), message, weights,
                profile.topK(), profile.candidates(),
                profile.perNoteQuota() == Integer.MAX_VALUE ? 0 : profile.perNoteQuota(), profile.fullBody());
    }

    /** 与桌面首行联想同口径：先 key 前缀，无结果再按描述包含。 */
    public List<Suggestion> suggest(String prefix) {
        String q = prefix == null ? "" : prefix.strip();
        if (q.startsWith(":") || q.startsWith("：")) {
            q = q.substring(1);
        }
        if (q.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> counts = service.blockRepository().blockCounts();
        List<NoteDto> found = repository.searchByKeyPrefix(q, 20);
        String match = "key";
        if (found.isEmpty()) {
            found = repository.searchByDescContains(q, 20);
            match = "desc";
        }
        List<Suggestion> out = new ArrayList<>();
        for (NoteDto n : found) {
            if (n.key == null || n.key.isBlank() || n.deleted) {
                continue;
            }
            out.add(new Suggestion(n.key, n.desc == null ? n.title : n.desc, n.domain, n.topic,
                    n.encrypted, counts.getOrDefault(n.key, 0), match));
        }
        return out;
    }

    // ==================== 配置 ====================

    public ConfigResponse config() {
        AppConfig cfg = AppConfig.getInstance();
        List<ConfigItem> items = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        for (ConfigKeys.Category c : ConfigKeys.Category.values()) {
            if (c == ConfigKeys.Category.ABOUT) {
                continue;
            }
            categories.add(c.label);
        }
        for (ConfigKeys k : ConfigKeys.values()) {
            items.add(new ConfigItem(k.key, k.label, k.category.name(), k.category.label,
                    currentValue(cfg, k), String.valueOf(k.def), typeOf(k), k.min, k.max, k.needsReindex));
        }
        Map<String, Object> web = new LinkedHashMap<>();
        web.put("enabled", cfg.getBoolean(ConfigKeys.WEB_ENABLED));
        web.put("bind", cfg.getString(ConfigKeys.WEB_BIND));
        web.put("port", cfg.getInt(ConfigKeys.WEB_PORT));
        web.put("sessionTtl", cfg.getInt(ConfigKeys.WEB_SESSION_TTL));
        web.put("syncEnabled", cfg.getBoolean(ConfigKeys.WEB_SYNC_ENABLED));
        web.put("sessions", auth.activeSessions());
        web.put("subscribers", events.subscriberCount());
        return new ConfigResponse(items, categories, cfg.isMasterPasswordSet(),
                cfg.getString(AppConfig.MASTER_PASSWORD_SALT, ""),
                cfg.getString(AppConfig.MASTER_PASSWORD_VERIFIER, ""), web, indexer.status());
    }

    public List<String> updateConfig(ConfigRequest req) {
        AppConfig cfg = AppConfig.getInstance();
        List<String> needsReindex = new ArrayList<>();
        synchronized (writeLock) {
            if (req.changes != null) {
                for (Map.Entry<String, String> e : req.changes.entrySet()) {
                    String key = e.getKey();
                    String value = e.getValue() == null ? "" : e.getValue().strip();
                    ConfigKeys k = ConfigKeys.byKey(key);
                    if (k != null) {
                        if (k.isBoolean()) {
                            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                                throw new WebException(400, k.label + " 只能是 true/false");
                            }
                            cfg.set(k, value.toLowerCase(Locale.ROOT));
                        } else if (k.isNumeric()) {
                            double num;
                            try {
                                num = Double.parseDouble(value);
                            } catch (NumberFormatException ex) {
                                throw new WebException(400, k.label + " 必须是数字");
                            }
                            if (!k.inRange(num)) {
                                throw new WebException(400, k.label + " 超出范围 [" + k.min + ", " + k.max + "]");
                            }
                            cfg.set(k, value);
                        } else {
                            cfg.set(k, value);
                        }
                        if (k.needsReindex) {
                            needsReindex.add(k.key);
                        }
                    } else if (EXTRA_WRITABLE_KEYS.contains(key)) {
                        cfg.setString(key, value);
                        if ("vector.provider".equals(key)) {
                            needsReindex.add(key);
                        }
                    } else {
                        throw new WebException(400, "未知配置项: " + key);
                    }
                }
            }
            if (req.masterSalt != null && req.masterVerifier != null
                    && !req.masterSalt.isBlank() && !req.masterVerifier.isBlank()) {
                // 主密码校验值由浏览器派生后写入，服务端全程不见主密码
                cfg.setString(AppConfig.MASTER_PASSWORD_SALT, req.masterSalt.strip());
                cfg.setString(AppConfig.MASTER_PASSWORD_VERIFIER, req.masterVerifier.strip());
            }
            cfg.save();
        }
        events.publish("config", Map.of("needsReindex", needsReindex));
        return needsReindex;
    }

    // ==================== 同步 ====================

    public SyncStatus syncStatus() {
        AppConfig cfg = AppConfig.getInstance();
        boolean configured = DbSyncService.getInstance().isEnabled();
        boolean enabled = configured && cfg.getBoolean(ConfigKeys.WEB_SYNC_ENABLED);
        String message;
        if (!configured) {
            message = "未配置坚果云账号，同步由桌面端负责";
        } else if (!enabled) {
            message = "网页端同步已关闭（web.sync.enabled=false），避免与桌面端抢版本";
        } else {
            message = lastSyncMessage;
        }
        return new SyncStatus(enabled, configured, cfg.getString(ConfigKeys.CLOUD_PROVIDER),
                cfg.getString(ConfigKeys.NUTSTORE_SYNC_PATH), cfg.getInt(ConfigKeys.SYNC_AUTO_UPLOAD_INTERVAL),
                cfg.getBoolean(ConfigKeys.BEHAVIOR_SYNC_ON_START), cfg.getBoolean(ConfigKeys.BEHAVIOR_SYNC_ON_EXIT),
                message);
    }

    public SyncOutcome syncUpload() {
        return runSync(true);
    }

    public SyncOutcome syncPull() {
        return runSync(false);
    }

    private SyncOutcome runSync(boolean upload) {
        if (!syncStatus().enabled()) {
            throw new WebException(409, syncStatus().message());
        }
        long t0 = System.currentTimeMillis();
        events.publish("sync", Map.of("state", "running", "direction", upload ? "upload" : "pull"));
        boolean ok;
        try {
            ok = upload ? DbSyncService.getInstance().syncToCloud() : DbSyncService.getInstance().syncFromCloud();
        } catch (Exception e) {
            logger.error("[Web] 同步失败: {}", e.getMessage(), e);
            ok = false;
        }
        if (!upload) {
            service.rebuildIndexFromFiles();
        }
        long took = System.currentTimeMillis() - t0;
        lastSyncAt = System.currentTimeMillis();
        lastSyncMessage = (upload ? "上传" : "下载") + (ok ? "完成" : "失败") + "，耗时 " + took + "ms";
        events.publish("sync", Map.of("state", ok ? "done" : "failed", "direction", upload ? "upload" : "pull",
                "tookMs", took, "message", lastSyncMessage));
        events.publish("notes", Map.of("reason", "sync", "direction", upload ? "upload" : "pull"));
        return new SyncOutcome(ok, lastSyncMessage, took);
    }

    // ==================== 资源 ====================

    public AssetOutcome saveAsset(String key, String fileName, byte[] data) {
        NoteDto note = requireNote(key);
        String safe = safeAssetName(fileName);
        synchronized (writeLock) {
            Path saved = storage.saveAsset(note.id, safe, data);
            events.publish("notes", Map.of("reason", "asset", "key", key, "name", safe));
            return new AssetOutcome(safe, assetUrl(key, safe), saved.toFile().length());
        }
    }

    public byte[] readAsset(String key, String fileName) {
        requireNote(key);
        String safe = safeAssetName(fileName);
        Path folder = storage.findNoteFolderByKey(key);
        if (folder == null) {
            throw new WebException(404, "笔记目录不存在");
        }
        Path assets = folder.resolve("assets").toAbsolutePath().normalize();
        Path target = assets.resolve(safe).normalize();
        if (!target.startsWith(assets) || !Files.isRegularFile(target)) {
            throw new WebException(404, "资源不存在: " + safe);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new WebException(500, "读取资源失败: " + e.getMessage());
        }
    }

    public static String assetUrl(String key, String name) {
        return "/api/notes/" + key + "/assets/" + name;
    }

    private static String safeAssetName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new WebException(400, "缺少文件名");
        }
        String base = fileName.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String safe = base.replaceAll("[^A-Za-z0-9._\\-]", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..") || safe.length() > 120) {
            throw new WebException(400, "文件名不合法");
        }
        return safe;
    }

    // ==================== 系统 ====================

    public Map<String, Object> system() {
        Map<String, Object> out = new LinkedHashMap<>();
        KbDirectory.Snapshot snap = tree();
        out.put("app", "FastPig Web");
        out.put("baseDir", baseDir.toString());
        out.put("notesDir", storage.getNotesDir().toString());
        out.put("dbPath", repository.getDbPath());
        out.put("noteCount", snap.noteCount());
        out.put("topicCount", snap.topicCount());
        out.put("domainCount", snap.domains().size());
        out.put("blockCount", snap.blockCount());
        out.put("javaVersion", System.getProperty("java.version"));
        out.put("startedAt", startedAt);
        out.put("uptimeMs", System.currentTimeMillis() - startedAt);
        out.put("lastSyncAt", lastSyncAt);
        out.put("index", indexer.status());
        out.put("sync", syncStatus());
        out.put("masterPasswordSet", AppConfig.getInstance().isMasterPasswordSet());
        out.put("autosaveSeconds", AppConfig.getInstance().getInt(ConfigKeys.EDITOR_AUTOSAVE_INTERVAL));
        out.put("previewSectionOnKb", AppConfig.getInstance().getInt(ConfigKeys.PREVIEW_SECTION_ON_KB));
        out.put("previewSectionOffKb", AppConfig.getInstance().getInt(ConfigKeys.PREVIEW_SECTION_OFF_KB));
        return out;
    }

    public Map<String, Object> hello() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("index", indexer.status());
        out.put("sync", syncStatus());
        out.put("time", System.currentTimeMillis());
        return out;
    }

    // ==================== 内部 ====================

    private NoteDto requireNote(String key) {
        NoteDto note = repository.findByKey(key);
        if (note == null) {
            throw new WebException(404, "笔记不存在: " + key);
        }
        return note;
    }

    private int blockCountOf(String key) {
        return service.blockRepository().blockCounts().getOrDefault(key, 0);
    }

    private NoteMeta metaOf(NoteDto n, int blockCount) {
        String folder = n.folderPath == null || n.folderPath.isBlank() ? n.key
                : baseDir.relativize(Path.of(n.folderPath).toAbsolutePath()).toString().replace('\\', '/');
        return new NoteMeta(n.id, n.key, n.title, n.desc,
                NoteDto.orDefault(n.domain, NoteDto.DEFAULT_DOMAIN),
                NoteDto.orDefault(n.topic, NoteDto.DEFAULT_TOPIC),
                NoteDto.orDefault(n.noteType, NoteDto.DEFAULT_NOTE_TYPE),
                NoteDto.orDefault(n.layer, NoteDto.DEFAULT_LAYER),
                NoteDto.orDefault(n.visibility, NoteDto.DEFAULT_VISIBILITY),
                n.aliases == null ? List.of() : n.aliases,
                n.tags == null ? List.of() : n.tags,
                n.encrypted, n.encrypted, n.deleted,
                n.createdAt, n.updatedAt, n.version, blockCount, n.contentHash, folder);
    }

    private NoteDetail detailOf(NoteDto n, int blockCount) {
        NoteMeta meta = metaOf(n, blockCount);
        if (n.encrypted) {
            return new NoteDetail(meta, null, n.bodyMd, new CryptoPayload(n.cipherIv,
                    n.pwdSalt, n.pwdIv, n.pwdWrappedDek, n.masterSalt, n.masterIv, n.masterWrappedDek),
                    n.assets == null ? List.of() : n.assets);
        }
        return new NoteDetail(meta, n.bodyMd == null ? "" : n.bodyMd, null, null,
                n.assets == null ? List.of() : n.assets);
    }

    private void applyClassification(NoteDto note, SaveRequest req) {
        if (req.domain != null && !req.domain.isBlank()) {
            note.domain = req.domain.strip();
        }
        if (req.topic != null && !req.topic.isBlank()) {
            note.topic = req.topic.strip();
        }
        if (req.noteType != null) {
            note.noteType = NoteDto.normalizeNoteType(req.noteType);
        }
        if (req.layer != null) {
            note.layer = NoteDto.normalizeLayer(req.layer);
        }
        if (req.visibility != null) {
            note.visibility = NoteDto.normalizeVisibility(req.visibility);
        }
    }

    private void applyEnvelope(NoteDto note, SaveRequest req) {
        if (req.cipherIv == null || req.cipherIv.isBlank() || req.bodyMd == null || req.bodyMd.isBlank()) {
            throw new WebException(400, "私密笔记缺少密文或 IV");
        }
        if (req.pwdWrappedDek == null || req.pwdWrappedDek.isBlank()) {
            throw new WebException(400, "私密笔记缺少密码包裹的密钥");
        }
        note.encrypted = true;
        note.bodyMd = req.bodyMd;
        note.cipherIv = req.cipherIv;
        note.pwdSalt = req.pwdSalt;
        note.pwdIv = req.pwdIv;
        note.pwdWrappedDek = req.pwdWrappedDek;
        note.masterSalt = req.masterSalt;
        note.masterIv = req.masterIv;
        note.masterWrappedDek = req.masterWrappedDek;
        note.assets = req.assets;
        note.runtimeDek = null;
        note.locked = false;
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new WebException(400, "首行必须包含快捷命令");
        }
        String trimmed = key.strip();
        if (trimmed.length() > 64 || trimmed.contains(" ") || trimmed.contains("\n")) {
            throw new WebException(400, "快捷命令不能含空格或换行，且不超过 64 字符");
        }
        return trimmed;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Set<String> setParam(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(value.split(",")));
    }

    private static String currentValue(AppConfig cfg, ConfigKeys k) {
        if (k.isBoolean()) {
            return String.valueOf(cfg.getBoolean(k));
        }
        if (k.def instanceof Integer) {
            return String.valueOf(cfg.getInt(k));
        }
        if (k.def instanceof Long) {
            return String.valueOf(cfg.getLong(k));
        }
        if (k.def instanceof Double) {
            return String.valueOf(cfg.getDouble(k));
        }
        return cfg.getString(k);
    }

    private static String typeOf(ConfigKeys k) {
        if (k.isBoolean()) {
            return "boolean";
        }
        if (k.def instanceof Double) {
            return "double";
        }
        if (k.isNumeric()) {
            return "int";
        }
        return "string";
    }

    @Override
    public void close() {
        indexer.close();
        service.close();
        logger.info("[Web] 核心资源已释放");
    }
}