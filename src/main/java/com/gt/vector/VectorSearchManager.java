package com.gt.vector;

import com.gt.AppConfig;
import com.gt.NoteDto;
import com.gt.UnifiedNoteAppFrame;
import com.gt.VectorSearchPanel;
import com.gt.config.ConfigKeys;
import com.gt.kb.chunk.IndexBlocks;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.repo.BlockRepository;
import com.gt.kb.text.IndexTextBuilder;
import com.gt.storage.NoteFileStorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * 向量检索管理器 - 协调触发逻辑、搜索和显示
 * 
 * 触发条件：
 * 1. 输入以 `:` 或 `：` 开头
 * 2. 去掉前缀后字符数 ≥ search.min.query.chars
 * 3. 停顿 ≥ search.debounce.ms（debounce）
 */
public class VectorSearchManager {
    
    private static final Logger logger = LogManager.getLogger(VectorSearchManager.class);
    
    private static VectorSearchManager instance;
    
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> debounceTask;
    
    private LuceneVectorSearchService searchService;
    private BlockRepository blockRepository; // AI 档位回源取完整块正文用
    private VectorSearchPanel searchPanel;
    private BiConsumer<String, String> onSelectCallback; // (noteKey, h1Title) -> void
    private Window parentWindow; // 保存父窗口引用，用于显示下载提示
    
    private String lastQuery = "";
    private boolean indexingInProgress = false;
    private boolean downloadPromptShown = false; // 避免重复弹出下载提示
    
    // indexNote 去抖：同一笔记多次保存合并为最后一次执行，延迟见 index.save.delay.ms
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingIndexTasks = new ConcurrentHashMap<>();
    
    private VectorSearchManager() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "VectorSearch-Debounce");
            t.setDaemon(true);
            return t;
        });
    }
    
    public static synchronized VectorSearchManager getInstance() {
        if (instance == null) {
            instance = new VectorSearchManager();
        }
        return instance;
    }
    
    /**
     * 注入块仓库，AI 档位据此回源取完整块正文。
     * initialize 可能因模型未下载而提前返回，所以先存下来，等服务建好再应用。
     */
    public void setBlockRepository(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
        if (searchService != null) {
            searchService.setBlockRepository(blockRepository);
        }
    }

    /**
     * 初始化向量检索服务
     * @param parent 父窗口（用于显示下载提示）
     */
    public void initialize(Window parent) {
        this.parentWindow = parent; // 保存引用，用于后续显示下载提示
        logger.info("initialize() 被调用, parentWindow={}", (parent != null));
        
        // 检查模型是否已下载
        if (!ModelManager.isModelDownloaded()) {
            logger.info("模型未下载，向量检索功能暂不可用（等待用户触发下载）");
            return;
        }
        
        // 初始化服务
        UnifiedNoteAppFrame.updateStartupStatus("正在加载向量模型...");
        logger.info("模型已存在，开始初始化 EmbeddingService...");
        EmbeddingService.getInstance().initialize();
        searchService = VectorSearchFactory.getLuceneService();
        
        if (searchService != null && searchService.isAvailable()) {
            searchService.setBlockRepository(blockRepository);
            // 创建搜索面板
            searchPanel = new VectorSearchPanel(parent);
            searchPanel.setOnSelectCallback(result -> {
                if (onSelectCallback != null) {
                    onSelectCallback.accept(result.noteKey, result.h1Title);
                }
            });
            
            logger.info("初始化完成，索引数量: {}", searchService.getIndexedCount());
            
            // 索引为空，或索引结构版本过旧（docId 格式与字段已变，旧文档匹配不上），
            // 都需要在后台做一次全量重建
            boolean needRebuild = searchService.getIndexedCount() == 0 || searchService.isSchemaOutdated();
            if (needRebuild) {
                logger.info("启动后台全量索引，原因: {}",
                    searchService.getIndexedCount() == 0 ? "索引为空" : "索引结构版本升级");
                Path notesDir = Paths.get(System.getProperty("user.dir"), "notes");
                rebuildAllIndex(notesDir, (current, total) -> {
                    logger.debug("索引进度: {}/{}", current, total);
                    UnifiedNoteAppFrame.updateStartupStatus("正在创建向量索引 " + current + "/" + total + "...");
                });
                UnifiedNoteAppFrame.updateStartupStatus("就绪");
            } else {
                UnifiedNoteAppFrame.updateStartupStatus("就绪");
            }
        } else {
            logger.error("向量检索服务初始化失败");
            if (searchService != null) {
                logger.error("searchService.isAvailable() = {}", searchService.isAvailable());
                logger.error("errorMessage = {}", searchService.getErrorMessage());
            }
            UnifiedNoteAppFrame.updateStartupStatus("向量服务初始化失败");
        }
    }
    
    /**
     * 重新初始化（模型下载完成后调用）
     */
    private void reinitialize() {
        logger.info("reinitialize() 被调用");
        downloadPromptShown = false;
        if (parentWindow != null) {
            initialize(parentWindow);
        }
    }
    
    /**
     * 检查是否为向量检索触发模式
     * 新逻辑：直接输入触发向量检索，:xxx 用于快捷命令打开笔记
     */
    public static boolean isVectorSearchTrigger(String input) {
        if (input == null || input.isEmpty()) return false;
        // 以 : 或 ： 开头的是快捷命令，不触发向量检索
        if (input.startsWith(":") || input.startsWith("：")) return false;
        return true;
    }
    
    /**
     * 从输入中提取查询内容
     * 新逻辑：直接输入即为查询，无需去掉前缀
     */
    public static String extractQuery(String input) {
        if (input == null) return "";
        return input;
    }
    
    /**
     * 处理输入变化（带 debounce）
     * @param input 用户输入
     * @param component 触发组件（用于定位结果面板）
     */
    public void onInputChanged(String input, Component component) {
        logger.debug("onInputChanged: input='{}', isAvailable={}", input, isAvailable());
        
        // 如果服务不可用，检查是否因为模型未下载
        if (!isAvailable()) {
            // 只在输入满足触发条件时才提示（直接输入即触发）
            if (isVectorSearchTrigger(input)) {
                String query = input.trim();
                if (query.length() >= AppConfig.getInstance().getInt(ConfigKeys.SEARCH_MIN_QUERY_CHARS)) {
                    if (!ModelManager.isModelDownloaded()) {
                        // 模型未下载，提示用户下载
                        logger.info("模型未下载，准备提示用户下载");
                        if (parentWindow != null && !downloadPromptShown) {
                            downloadPromptShown = true; // 避免重复弹出
                            SwingUtilities.invokeLater(() -> {
                                if (ModelManager.showDownloadPrompt(parentWindow)) {
                                    ModelManager.downloadModelWithProgress(parentWindow, this::reinitialize);
                                } else {
                                    downloadPromptShown = false; // 用户取消后，下次可以再弹
                                }
                            });
                        }
                    } else {
                        // 模型已下载但服务未初始化，尝试重新初始化
                        logger.info("模型已下载但服务不可用，尝试重新初始化");
                        reinitialize();
                    }
                }
            }
            return;
        }
        
        // 取消之前的 debounce 任务
        if (debounceTask != null && !debounceTask.isDone()) {
            debounceTask.cancel(false);
        }
        
        // 检查是否为向量检索触发（直接输入触发，:xxx 不触发）
        if (!isVectorSearchTrigger(input)) {
            hideResults();
            return;
        }
        
        String query = input.trim();
        
        // 检查最小字符数
        if (query.length() < AppConfig.getInstance().getInt(ConfigKeys.SEARCH_MIN_QUERY_CHARS)) {
            hideResults();
            return;
        }
        
        // 如果查询没变，不重复搜索
        if (query.equals(lastQuery) && searchPanel != null && searchPanel.isVisible()) {
            return;
        }
        
        logger.debug("触发搜索: query='{}'", query);
        
        // 设置 debounce 任务
        debounceTask = scheduler.schedule(() -> {
            performSearch(query, component);
        }, AppConfig.getInstance().getInt(ConfigKeys.SEARCH_DEBOUNCE_MS), TimeUnit.MILLISECONDS);
    }
    
    /**
     * 执行搜索
     */
    private void performSearch(String query, Component component) {
        logger.debug("performSearch: query='{}'", query);
        
        if (!isAvailable() || searchService == null) {
            logger.debug("performSearch: 服务不可用，跳过搜索");
            return;
        }
        
        lastQuery = query;
        
        // 执行搜索
        logger.debug("开始向量检索...");
        List<LuceneVectorSearchService.VectorSearchResult> results =
            searchService.search(query, AppConfig.getInstance().getInt(ConfigKeys.SEARCH_HUMAN_TOPK));
        logger.debug("检索完成，结果数: {}", results.size());
        
        // 在 EDT 中更新 UI
        SwingUtilities.invokeLater(() -> {
            if (searchPanel != null) {
                searchPanel.setResults(results, query);
                // 使用光标位置定位（如果是 JTextArea）
                if (component instanceof JTextArea) {
                    searchPanel.showBelowCaret((JTextArea) component);
                } else {
                    searchPanel.showBelow(component);
                }
                logger.debug("搜索面板已显示");
            }
        });
    }
    
    /**
     * 隐藏搜索结果
     */
    public void hideResults() {
        if (searchPanel != null) {
            searchPanel.hidePanel();
        }
        lastQuery = "";
    }
    
    /**
     * 选择上一项
     */
    public void selectPrevious() {
        if (searchPanel != null && searchPanel.isVisible()) {
            searchPanel.selectPrevious();
        }
    }
    
    /**
     * 选择下一项
     */
    public void selectNext() {
        if (searchPanel != null && searchPanel.isVisible()) {
            searchPanel.selectNext();
        }
    }
    
    /**
     * 确认选择
     */
    public void confirmSelection() {
        if (searchPanel != null && searchPanel.isVisible()) {
            searchPanel.selectCurrent();
        }
    }
    
    /**
     * 结果面板是否可见
     */
    public boolean isResultsVisible() {
        return searchPanel != null && searchPanel.isVisible();
    }
    
    /**
     * 设置选择回调
     */
    public void setOnSelectCallback(BiConsumer<String, String> callback) {
        this.onSelectCallback = callback;
    }
    
    /**
     * 应用主题
     */
    public void applyTheme(boolean isDark) {
        if (searchPanel != null) {
            searchPanel.applyTheme(isDark);
        }
    }
    
    /**
     * 服务是否可用
     */
    public boolean isAvailable() {
        return searchService != null && searchService.isAvailable();
    }
    
    /**
     * 获取索引数量
     */
    public int getIndexedCount() {
        return searchService != null ? searchService.getIndexedCount() : 0;
    }
    
    // ==================== 索引管理 ====================
    
    /**
     * 索引单个笔记（块级差量模式）
     *
     * 逐块比对 content_hash：只有内容真正变化的块才重新 embed，
     * 因此一篇有上百个块的长笔记改动一处，代价也只是一次 embed。
     *
     * 连按 Ctrl+S 采用延迟合并而非丢弃：末次改动一定会被索引到。
     */
    public void indexNote(NoteDto note) {
        if (!isAvailable() || note == null || note.key == null || note.key.isEmpty()) {
            return;
        }

        // 任务延迟执行，期间编辑器可能继续改同一个 NoteDto，
        // 先取快照：要索引的是本次保存的内容，不是执行那一刻的最新状态
        NoteDto snapshot = snapshotOf(note);
        String noteKey = snapshot.key;

        // 合并式去抖：重排定时任务，只保留最后一次的内容。
        // 原先是冷却期内直接丢弃，会导致 2 秒内的第二次保存永远不进索引。
        ScheduledFuture<?> pending = pendingIndexTasks.get(noteKey);
        if (pending != null) {
            pending.cancel(false);
        }
        ScheduledFuture<?> task = scheduler.schedule(
            () -> {
                pendingIndexTasks.remove(noteKey);
                doIndexNote(snapshot);
            },
            AppConfig.getInstance().getLong(ConfigKeys.INDEX_SAVE_DELAY_MS), TimeUnit.MILLISECONDS);
        pendingIndexTasks.put(noteKey, task);
    }

    private void doIndexNote(NoteDto note) {
        CompletableFuture.runAsync(() -> {
            try {
                searchService.indexNote(note, IndexBlocks.of(note));
            } catch (Exception e) {
                logger.error("索引笔记失败: {}", e.getMessage(), e);
            }
        });
    }

    private static NoteDto snapshotOf(NoteDto note) {
        NoteDto copy = new NoteDto();
        copy.key = note.key;
        copy.desc = note.desc;
        copy.bodyMd = note.bodyMd;
        copy.domain = note.domain;
        copy.topic = note.topic;
        copy.noteType = note.noteType;
        copy.layer = note.layer;
        copy.visibility = note.visibility;
        copy.deleted = note.deleted;
        copy.encrypted = note.encrypted;
        return copy;
    }
    
    /**
     * 移除笔记索引
     */
    public void removeNoteIndex(String noteKey) {
        if (!isAvailable()) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            searchService.removeNoteIndex(noteKey);
        });
    }
    
    /**
     * 全量重建索引
     * 优化：在锁外批量 embed，积累到一定数量后在锁内一次性写入
     * 这样 embed 推理期间不持有写锁，搜索基本不受影响
     *
     * @param notesDir 笔记目录
     * @param progressCallback 进度回调 (current, total)
     */
    public void rebuildAllIndex(Path notesDir, BiConsumer<Integer, Integer> progressCallback) {
        if (!isAvailable() || indexingInProgress) {
            return;
        }

        indexingInProgress = true;

        // 每积累 BATCH_FLUSH_SIZE 个索引块就写入一次 Lucene
        final int BATCH_FLUSH_SIZE = 50;

        CompletableFuture.runAsync(() -> {
            try {
                // 清空现有索引
                searchService.clearAll();

                if (!Files.exists(notesDir)) {
                    return;
                }

                // NoteFileStorage 按「基目录 + notes」定位，传进来的正是那个 notes 目录。
                // 用它扫描而不是自己解析 front matter：私密/已删除标记和分类字段都要走同一套口径。
                List<NoteDto> notes = new NoteFileStorage(notesDir.toAbsolutePath().getParent().toString())
                    .scanAllNotes();
                int total = notes.size();
                int current = 0;

                // 批量缓冲区：积累已 embed 的索引块
                List<LuceneVectorSearchService.IndexedBlock> batch = new ArrayList<>();

                for (NoteDto note : notes) {
                    // IndexBlocks.of 对私密/已删除笔记返回空列表，等同把它们排除在索引外
                    for (KnowledgeBlock block : IndexBlocks.of(note)) {
                        float[] vector = EmbeddingService.getInstance().embed(IndexTextBuilder.build(block));
                        if (vector != null) {
                            batch.add(new LuceneVectorSearchService.IndexedBlock(note, block, vector));
                        }

                        // 达到批次大小，刷入 Lucene
                        if (batch.size() >= BATCH_FLUSH_SIZE) {
                            searchService.batchWriteWithVectors(null, batch);
                            batch = new ArrayList<>();
                        }
                    }

                    current++;
                    if (progressCallback != null) {
                        final int c = current;
                        SwingUtilities.invokeLater(() -> progressCallback.accept(c, total));
                    }
                }

                // 刷入剩余的缓冲数据
                if (!batch.isEmpty()) {
                    searchService.batchWriteWithVectors(null, batch);
                }

                // 重建成功后才落盘结构版本，中途失败下次仍会重建
                searchService.markSchemaCurrent();
                logger.info("全量索引完成，共 {} 条", searchService.getIndexedCount());

            } catch (Exception e) {
                logger.error("全量索引失败: {}", e.getMessage(), e);
            } finally {
                indexingInProgress = false;
            }
        });
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        scheduler.shutdown();
        if (searchService != null) {
            searchService.close();
        }
    }

}

