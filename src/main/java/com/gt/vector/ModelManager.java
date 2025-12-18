package com.gt.vector;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;

/**
 * 模型管理器 - 处理模型下载、检查和初始化
 */
public class ModelManager {
    
    private static final String MODEL_DIR = "models";
    private static final String MODEL_FILE = "bge-small-zh-v1.5.onnx";
    private static final String VOCAB_FILE = "vocab.txt";
    
    private static boolean downloadInProgress = false;
    
    /**
     * 检查模型是否已下载
     */
    public static boolean isModelDownloaded() {
        Path modelDir = Paths.get(System.getProperty("user.dir"), MODEL_DIR);
        return Files.exists(modelDir.resolve(MODEL_FILE)) 
            && Files.exists(modelDir.resolve(VOCAB_FILE));
    }
    
    /**
     * 显示下载提示对话框
     * @param parent 父窗口
     * @return true 如果用户选择下载
     */
    public static boolean showDownloadPrompt(Component parent) {
        String message = 
            "向量检索功能需要下载 Embedding 模型。\n\n" +
            "模型名称: bge-small-zh-v1.5\n" +
            "模型大小: 约 90 MB\n" +
            "下载来源: HuggingFace\n\n" +
            "是否现在下载？";
        
        int result = JOptionPane.showConfirmDialog(
            parent,
            message,
            "下载 Embedding 模型",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        return result == JOptionPane.YES_OPTION;
    }
    
    /**
     * 下载模型（带进度对话框）
     * @param parent 父窗口
     * @param callback 完成回调
     */
    public static void downloadModelWithProgress(Component parent, Runnable callback) {
        if (downloadInProgress) {
            JOptionPane.showMessageDialog(parent, "模型正在下载中...", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        downloadInProgress = true;
        
        // 创建进度对话框
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), "下载模型", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel label = new JLabel("正在下载 Embedding 模型...");
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 25));
        
        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        
        dialog.add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        
        // 在后台线程下载
        new Thread(() -> {
            try {
                EmbeddingService service = EmbeddingService.getInstance();
                boolean success = service.downloadModel(progress -> {
                    SwingUtilities.invokeLater(() -> {
                        int percent = (int) (progress * 100);
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "%");
                    });
                });
                
                SwingUtilities.invokeLater(() -> {
                    dialog.dispose();
                    downloadInProgress = false;
                    
                    if (success) {
                        JOptionPane.showMessageDialog(parent, 
                            "模型下载完成！\n重新启动应用后，向量检索功能将可用。", 
                            "下载成功", 
                            JOptionPane.INFORMATION_MESSAGE);
                        
                        // 尝试初始化服务
                        service.initialize();
                        
                        if (callback != null) {
                            callback.run();
                        }
                    } else {
                        JOptionPane.showMessageDialog(parent, 
                            "模型下载失败，请检查网络连接后重试。\n\n" +
                            "您也可以手动下载模型文件放到 models/ 目录：\n" +
                            "- bge-small-zh-v1.5.onnx\n" +
                            "- vocab.txt", 
                            "下载失败", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    dialog.dispose();
                    downloadInProgress = false;
                    
                    JOptionPane.showMessageDialog(parent, 
                        "下载过程中发生错误: " + e.getMessage(), 
                        "下载失败", 
                        JOptionPane.ERROR_MESSAGE);
                });
                e.printStackTrace();
            }
        }).start();
        
        // 显示对话框（阻塞）
        dialog.setVisible(true);
    }
    
    /**
     * 初始化向量检索服务（如果模型已下载）
     * @return true 如果初始化成功
     */
    public static boolean initializeIfReady() {
        if (!isModelDownloaded()) {
            return false;
        }
        
        EmbeddingService embeddingService = EmbeddingService.getInstance();
        if (!embeddingService.isAvailable()) {
            embeddingService.initialize();
        }
        
        if (!embeddingService.isAvailable()) {
            return false;
        }
        
        LuceneVectorSearchService luceneService = VectorSearchFactory.getLuceneService();
        return luceneService != null && luceneService.isAvailable();
    }
    
    /**
     * 获取模型状态描述
     */
    public static String getStatusDescription() {
        if (!isModelDownloaded()) {
            return "模型未下载";
        }
        
        EmbeddingService embeddingService = EmbeddingService.getInstance();
        if (!embeddingService.isAvailable()) {
            return "模型未加载: " + embeddingService.getErrorMessage();
        }
        
        LuceneVectorSearchService luceneService = VectorSearchFactory.getLuceneService();
        if (luceneService == null || !luceneService.isAvailable()) {
            return "索引服务未初始化";
        }
        
        return "就绪 (索引: " + luceneService.getIndexedCount() + " 条)";
    }
}

