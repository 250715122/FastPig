package com.gt.vector;

import com.gt.VectorSearchPanel;
import com.gt.storage.NoteFileStorage;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
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
    
    // 配置
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int DEBOUNCE_MS = 300;
    private static final int TOP_K = 10;
    
    private static VectorSearchManager instance;
    
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> debounceTask;
    
    private LuceneVectorSearchService searchService;
    private VectorSearchPanel searchPanel;
    private BiConsumer<String, String> onSelectCallback; // (noteKey, h1Title) -> void
    
    private String lastQuery = "";
    private boolean indexingInProgress = false;
    
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
        // 检查模型是否已下载
        if (!ModelManager.isModelDownloaded()) {
            System.out.println("[VectorSearchManager] 模型未下载，向量检索功能不可用");
            return;
        }
        
        // 初始化服务
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
            
            System.out.println("[VectorSearchManager] 初始化完成，索引数量: " + searchService.getIndexedCount());
            
            // 如果索引为空，启动后台全量索引
            if (searchService.getIndexedCount() == 0) {
                System.out.println("[VectorSearchManager] 索引为空，将启动后台全量索引");
            }
        } else {
            System.err.println("[VectorSearchManager] 向量检索服务初始化失败");
        }
    }
    
    /**
     * 检查是否为向量检索触发模式
     */
    public static boolean isVectorSearchTrigger(String input) {
        return input != null && (input.startsWith(":") || input.startsWith("："));
    }
    
    /**
     * 从输入中提取查询内容（去掉前缀）
     */
    public static String extractQuery(String input) {
        if (input == null) return "";
        if (input.startsWith(":")) return input.substring(1);
        if (input.startsWith("：")) return input.substring(1);
        return input;
    }
    
    /**
     * 处理输入变化（带 debounce）
     * @param input 用户输入
     * @param component 触发组件（用于定位结果面板）
     */
    public void onInputChanged(String input, Component component) {
        if (!isAvailable()) {
            return;
        }
        
        // 取消之前的 debounce 任务
        if (debounceTask != null && !debounceTask.isDone()) {
            debounceTask.cancel(false);
        }
        
        // 检查是否为向量检索触发
        if (!isVectorSearchTrigger(input)) {
            hideResults();
            return;
        }
        
        String query = extractQuery(input).trim();
        
        // 检查最小字符数
        if (query.length() < MIN_QUERY_LENGTH) {
            hideResults();
            return;
        }
        
        // 如果查询没变，不重复搜索
        if (query.equals(lastQuery) && searchPanel != null && searchPanel.isVisible()) {
            return;
        }
        
        // 设置 debounce 任务
        debounceTask = scheduler.schedule(() -> {
            performSearch(query, component);
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 执行搜索
     */
    private void performSearch(String query, Component component) {
        if (!isAvailable() || searchService == null) {
            return;
        }
        
        lastQuery = query;
        
        // 执行搜索
        List<LuceneVectorSearchService.VectorSearchResult> results = searchService.search(query, TOP_K);
        
        // 在 EDT 中更新 UI
        SwingUtilities.invokeLater(() -> {
            if (searchPanel != null && !results.isEmpty()) {
                searchPanel.setResults(results, query);
                searchPanel.showBelow(component);
            } else if (searchPanel != null) {
                // 无结果时也显示面板（显示"未找到"提示）
                searchPanel.setResults(results, query);
                searchPanel.showBelow(component);
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
     * 索引单个笔记
     */
    public void indexNote(String noteKey, String noteName, String content) {
        if (!isAvailable()) {
            return;
        }
        
        // 在后台线程执行
        CompletableFuture.runAsync(() -> {
            try {
                // 先移除该笔记的旧索引
                searchService.removeNoteIndex(noteKey);
                
                // 解析 H1 块
                List<NoteH1Parser.H1Block> blocks = NoteH1Parser.parse(noteKey, noteName, content);
                
                // 索引每个 H1 块
                for (NoteH1Parser.H1Block block : blocks) {
                    searchService.indexH1(block.noteKey, block.h1Title, block.indexContent);
                }
                
                System.out.println("[VectorSearchManager] 笔记索引完成: " + noteKey + ", " + blocks.size() + " 个 H1");
                
            } catch (Exception e) {
                System.err.println("[VectorSearchManager] 索引笔记失败: " + e.getMessage());
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
     * @param notesDir 笔记目录
     * @param progressCallback 进度回调 (current, total)
     */
    public void rebuildAllIndex(Path notesDir, BiConsumer<Integer, Integer> progressCallback) {
        if (!isAvailable() || indexingInProgress) {
            return;
        }
        
        indexingInProgress = true;
        
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
                
                for (Path noteDir : noteDirs) {
                    String noteKey = noteDir.getFileName().toString();
                    Path noteFile = noteDir.resolve("note.md");
                    
                    if (Files.exists(noteFile)) {
                        try {
                            String content = Files.readString(noteFile);
                            String noteName = noteKey; // 可以从其他地方获取更友好的名称
                            
                            // 解析并索引
                            List<NoteH1Parser.H1Block> blocks = NoteH1Parser.parse(noteKey, noteName, content);
                            for (NoteH1Parser.H1Block block : blocks) {
                                searchService.indexH1(block.noteKey, block.h1Title, block.indexContent);
                            }
                            
                        } catch (IOException e) {
                            System.err.println("[VectorSearchManager] 读取笔记失败: " + noteFile);
                        }
                    }
                    
                    current++;
                    if (progressCallback != null) {
                        final int c = current;
                        SwingUtilities.invokeLater(() -> progressCallback.accept(c, total));
                    }
                }
                
                System.out.println("[VectorSearchManager] 全量索引完成，共 " + searchService.getIndexedCount() + " 条");
                
            } catch (Exception e) {
                System.err.println("[VectorSearchManager] 全量索引失败: " + e.getMessage());
                e.printStackTrace();
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

