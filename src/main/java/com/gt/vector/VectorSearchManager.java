package com.gt.vector;

import com.gt.UnifiedNoteAppFrame;
import com.gt.VectorSearchPanel;
import com.gt.storage.NoteFileStorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.function.BiConsumer;

/**
 * 向量检索管理器 - 协调触发逻辑、搜索和显示
 * 
 * 触发条件：
 * 1. 输入以 `:` 或 `：` 开头
 * 2. 去掉前缀后字符数 ≥2
 * 3. 停顿 ≥300ms（debounce）
 */
public class VectorSearchManager {
    
    private static final Logger logger = LogManager.getLogger(VectorSearchManager.class);
    
    // 配置
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int DEBOUNCE_MS = 300;
    private static final int TOP_K = 20;
    
    private static VectorSearchManager instance;
    
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> debounceTask;
    
    private LuceneVectorSearchService searchService;
    private VectorSearchPanel searchPanel;
    private BiConsumer<String, String> onSelectCallback; // (noteKey, h1Title) -> void
    private Window parentWindow; // 保存父窗口引用，用于显示下载提示
    
    private String lastQuery = "";
    private boolean indexingInProgress = false;
    private boolean downloadPromptShown = false; // 避免重复弹出下载提示
    
    // indexNote 防抖：同一笔记 2 秒内不重复索引
    private final ConcurrentHashMap<String, Long> lastIndexedTime = new ConcurrentHashMap<>();
    private static final long INDEX_COOLDOWN_MS = 2000;
    
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
            // 创建搜索面板
            searchPanel = new VectorSearchPanel(parent);
            searchPanel.setOnSelectCallback(result -> {
                if (onSelectCallback != null) {
                    onSelectCallback.accept(result.noteKey, result.h1Title);
                }
            });
            
            logger.info("初始化完成，索引数量: {}", searchService.getIndexedCount());
            
            // 如果索引为空，启动后台全量索引
            if (searchService.getIndexedCount() == 0) {
                logger.info("索引为空，启动后台全量索引...");
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
                if (query.length() >= MIN_QUERY_LENGTH) {
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
        if (query.length() < MIN_QUERY_LENGTH) {
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
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
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
        List<LuceneVectorSearchService.VectorSearchResult> results = searchService.search(query, TOP_K);
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
     * 索引单个笔记（差量模式）
     * 
     * 优化逻辑：
     * 1. 解析新的 H1 标题集合
     * 2. 查询 Lucene 中已索引的旧 H1 标题集合
     * 3. 如果完全相同 → 跳过（最常见场景：编辑正文不改标题）
     * 4. 如果有差异 → 仅 embed 新增的块，删除被移除的块，单次 commit
     * 
     * @param noteKey 快捷命令
     * @param noteDesc 描述
     * @param content 笔记内容
     */
    public void indexNote(String noteKey, String noteDesc, String content) {
        if (!isAvailable()) {
            return;
        }
        
        // 防抖：同一笔记 2 秒内不重复索引（应对 Ctrl+S 连按）
        long now = System.currentTimeMillis();
        Long lastTime = lastIndexedTime.get(noteKey);
        if (lastTime != null && now - lastTime < INDEX_COOLDOWN_MS) {
            logger.debug("索引冷却期内，跳过: {}", noteKey);
            return;
        }
        lastIndexedTime.put(noteKey, now);
        
        // 在后台线程执行
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 解析新 H1 块
                List<NoteH1Parser.H1Block> newBlocks = NoteH1Parser.parse(noteKey, noteDesc, content);
                Set<String> newTitles = newBlocks.stream()
                    .map(b -> b.h1Title)
                    .collect(Collectors.toSet());
                
                // 2. 查询已索引的旧 H1 标题
                Set<String> oldTitles = searchService.getIndexedH1Titles(noteKey);
                
                // 3. 如果完全相同，跳过索引（最常见：编辑正文不改标题）
                if (newTitles.equals(oldTitles)) {
                    logger.debug("H1 标题无变化，跳过索引: {}", noteKey);
                    return;
                }
                
                // 4. 计算差异
                Set<String> toAdd = new HashSet<>(newTitles);
                toAdd.removeAll(oldTitles);    // 新增的标题
                
                Set<String> toRemove = new HashSet<>(oldTitles);
                toRemove.removeAll(newTitles); // 被删除的标题
                
                logger.info("差量索引: noteKey={}, 新增={}, 删除={}, 不变={}", 
                    noteKey, toAdd.size(), toRemove.size(), newTitles.size() - toAdd.size());
                
                // 5. 仅 embed 需要新增的块（在锁外执行，不阻塞搜索）
                List<LuceneVectorSearchService.H1BlockWithVector> blocksToWrite = new ArrayList<>();
                for (NoteH1Parser.H1Block block : newBlocks) {
                    if (toAdd.contains(block.h1Title)) {
                        float[] vector = EmbeddingService.getInstance().embed(block.indexContent);
                        if (vector != null) {
                            blocksToWrite.add(new LuceneVectorSearchService.H1BlockWithVector(
                                block.noteKey, block.noteDesc, block.h1Title, block.indexContent, vector));
                        }
                    }
                }
                
                // 6. 差量写入 Lucene（删除旧块 + 写入新块 + 单次 commit + refreshReader）
                searchService.differentialUpdate(noteKey, toRemove, blocksToWrite);
                
                logger.debug("笔记差量索引完成: {}, 新增 {} 个 H1, 删除 {} 个 H1", 
                    noteKey, blocksToWrite.size(), toRemove.size());
                
            } catch (Exception e) {
                logger.error("索引笔记失败: {}", e.getMessage(), e);
            }
        });
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
        
        // 每积累 BATCH_FLUSH_SIZE 个 H1 块就写入一次 Lucene
        final int BATCH_FLUSH_SIZE = 50;
        
        CompletableFuture.runAsync(() -> {
            try {
                // 清空现有索引
                searchService.clearAll();
                
                // 扫描所有笔记
                if (!Files.exists(notesDir)) {
                    return;
                }
                
                List<Path> noteDirs;
                try (var stream = Files.list(notesDir)) {
                    noteDirs = stream.filter(Files::isDirectory).toList();
                }
                
                int total = noteDirs.size();
                int current = 0;
                
                // 批量缓冲区：积累已 embed 的 H1 块
                List<LuceneVectorSearchService.H1BlockWithVector> batch = new ArrayList<>();
                
                for (Path noteDir : noteDirs) {
                    String noteKey = noteDir.getFileName().toString();
                    Path noteFile = noteDir.resolve("note.md");
                    
                    if (Files.exists(noteFile)) {
                        try {
                            String content = Files.readString(noteFile);
                            
                            // 检查是否已删除，跳过已删除的笔记
                            if (isNoteDeleted(content)) {
                                current++;
                                if (progressCallback != null) {
                                    final int c = current;
                                    SwingUtilities.invokeLater(() -> progressCallback.accept(c, total));
                                }
                                continue;
                            }
                            
                            // 从 YAML Front Matter 中提取描述
                            String noteDesc = extractDescFromFrontMatter(content);
                            if (noteDesc == null || noteDesc.isEmpty()) {
                                noteDesc = noteKey;
                            }
                            
                            // 解析 H1 块
                            List<NoteH1Parser.H1Block> blocks = NoteH1Parser.parse(noteKey, noteDesc, content);
                            
                            // 在锁外逐个 embed，加入批量缓冲区
                            for (NoteH1Parser.H1Block block : blocks) {
                                float[] vector = EmbeddingService.getInstance().embed(block.indexContent);
                                if (vector != null) {
                                    batch.add(new LuceneVectorSearchService.H1BlockWithVector(
                                        block.noteKey, block.noteDesc, block.h1Title, block.indexContent, vector));
                                }
                                
                                // 达到批次大小，刷入 Lucene
                                if (batch.size() >= BATCH_FLUSH_SIZE) {
                                    searchService.batchWriteWithVectors(null, batch);
                                    batch = new ArrayList<>();
                                }
                            }
                            
                        } catch (IOException e) {
                            logger.error("读取笔记失败: {}", noteFile, e);
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
    
    /**
     * 从 YAML Front Matter 中提取描述
     * @param content 笔记内容
     * @return 描述，未找到返回 null
     */
    private String extractDescFromFrontMatter(String content) {
        if (content == null || !content.startsWith("---")) {
            return null;
        }
        
        int endIndex = content.indexOf("---", 3);
        if (endIndex == -1) {
            return null;
        }
        
        String frontMatter = content.substring(3, endIndex);
        
        // 简单解析 desc 字段
        for (String line : frontMatter.split("\n")) {
            line = line.trim();
            if (line.startsWith("desc:")) {
                String desc = line.substring(5).trim();
                // 去掉可能的引号
                if (desc.startsWith("\"") && desc.endsWith("\"")) {
                    desc = desc.substring(1, desc.length() - 1);
                } else if (desc.startsWith("'") && desc.endsWith("'")) {
                    desc = desc.substring(1, desc.length() - 1);
                }
                return desc;
            }
        }
        
        return null;
    }
    
    /**
     * 检查笔记是否被标记为已删除
     * @param content 笔记内容
     * @return 是否已删除
     */
    private boolean isNoteDeleted(String content) {
        if (content == null || !content.startsWith("---")) {
            return false;
        }
        
        int endIndex = content.indexOf("---", 3);
        if (endIndex == -1) {
            return false;
        }
        
        String frontMatter = content.substring(3, endIndex);
        
        for (String line : frontMatter.split("\n")) {
            line = line.trim();
            if (line.startsWith("deleted:")) {
                String value = line.substring(8).trim();
                return "true".equalsIgnoreCase(value);
            }
        }
        
        return false;
    }
}

