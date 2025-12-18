package com.gt;

import com.gt.vector.LuceneVectorSearchService.VectorSearchResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * 向量检索结果面板 - 显示 Top10 候选列表
 * 
 * 样式参考现有的 suggestList，支持键盘导航和主题切换
 */
public class VectorSearchPanel extends JWindow {
    
    private JList<VectorSearchResult> resultList;
    private DefaultListModel<VectorSearchResult> listModel;
    private Consumer<VectorSearchResult> onSelectCallback;
    private JLabel statusLabel;
    
    // 主题颜色
    private Color backgroundColor = new Color(45, 45, 45);
    private Color foregroundColor = new Color(220, 220, 220);
    private Color selectionColor = new Color(70, 130, 180);
    private Color borderColor = new Color(80, 80, 80);
    
    public VectorSearchPanel(Window owner) {
        super(owner);
        initComponents();
    }
    
    private void initComponents() {
        setAlwaysOnTop(true);
        
        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(backgroundColor);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        // 标题标签
        JLabel titleLabel = new JLabel("向量检索结果");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(foregroundColor);
        titleLabel.setBorder(new EmptyBorder(0, 5, 5, 5));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // 结果列表
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setCellRenderer(new ResultCellRenderer());
        resultList.setBackground(backgroundColor);
        resultList.setForeground(foregroundColor);
        resultList.setSelectionBackground(selectionColor);
        resultList.setSelectionForeground(Color.WHITE);
        resultList.setFixedCellHeight(50);
        resultList.setBorder(null);
        
        // 列表选择事件
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 || e.getClickCount() == 1) {
                    selectCurrent();
                }
            }
        });
        
        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectCurrent();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hidePanel();
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(null);
        scrollPane.setBackground(backgroundColor);
        scrollPane.getViewport().setBackground(backgroundColor);
        scrollPane.setPreferredSize(new Dimension(450, 300));
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 状态栏
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(10f));
        statusLabel.setForeground(new Color(150, 150, 150));
        statusLabel.setBorder(new EmptyBorder(5, 5, 0, 5));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(mainPanel);
        pack();
    }
    
    /**
     * 设置搜索结果
     */
    public void setResults(List<VectorSearchResult> results, String query) {
        listModel.clear();
        for (VectorSearchResult result : results) {
            listModel.addElement(result);
        }
        
        if (!results.isEmpty()) {
            resultList.setSelectedIndex(0);
            statusLabel.setText(String.format("找到 %d 条相关结果", results.size()));
        } else {
            statusLabel.setText("未找到相关结果");
        }
        
        pack();
    }
    
    /**
     * 设置选择回调
     */
    public void setOnSelectCallback(Consumer<VectorSearchResult> callback) {
        this.onSelectCallback = callback;
    }
    
    /**
     * 显示在指定位置
     */
    public void showAt(int x, int y) {
        setLocation(x, y);
        setVisible(true);
        resultList.requestFocusInWindow();
    }
    
    /**
     * 显示在组件下方
     */
    public void showBelow(Component component) {
        Point loc = component.getLocationOnScreen();
        int x = loc.x;
        int y = loc.y + component.getHeight();
        
        // 确保不超出屏幕
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        if (x + getWidth() > screenSize.width) {
            x = screenSize.width - getWidth();
        }
        if (y + getHeight() > screenSize.height) {
            y = loc.y - getHeight();
        }
        
        showAt(x, y);
    }
    
    /**
     * 隐藏面板
     */
    public void hidePanel() {
        setVisible(false);
    }
    
    /**
     * 选择上一项
     */
    public void selectPrevious() {
        int index = resultList.getSelectedIndex();
        if (index > 0) {
            resultList.setSelectedIndex(index - 1);
            resultList.ensureIndexIsVisible(index - 1);
        }
    }
    
    /**
     * 选择下一项
     */
    public void selectNext() {
        int index = resultList.getSelectedIndex();
        if (index < listModel.size() - 1) {
            resultList.setSelectedIndex(index + 1);
            resultList.ensureIndexIsVisible(index + 1);
        }
    }
    
    /**
     * 确认选择当前项
     */
    public void selectCurrent() {
        VectorSearchResult selected = resultList.getSelectedValue();
        if (selected != null && onSelectCallback != null) {
            hidePanel();
            onSelectCallback.accept(selected);
        }
    }
    
    /**
     * 应用主题
     */
    public void applyTheme(boolean isDark) {
        if (isDark) {
            backgroundColor = new Color(45, 45, 45);
            foregroundColor = new Color(220, 220, 220);
            selectionColor = new Color(70, 130, 180);
            borderColor = new Color(80, 80, 80);
        } else {
            backgroundColor = new Color(255, 255, 255);
            foregroundColor = new Color(50, 50, 50);
            selectionColor = new Color(100, 149, 237);
            borderColor = new Color(200, 200, 200);
        }
        
        // 更新组件颜色
        getContentPane().setBackground(backgroundColor);
        resultList.setBackground(backgroundColor);
        resultList.setForeground(foregroundColor);
        resultList.setSelectionBackground(selectionColor);
        
        repaint();
    }
    
    /**
     * 结果列表单元格渲染器
     */
    private class ResultCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, 
                int index, boolean isSelected, boolean cellHasFocus) {
            
            VectorSearchResult result = (VectorSearchResult) value;
            
            JPanel panel = new JPanel(new BorderLayout(5, 2));
            panel.setBorder(new EmptyBorder(5, 8, 5, 8));
            
            if (isSelected) {
                panel.setBackground(selectionColor);
            } else {
                panel.setBackground(backgroundColor);
            }
            
            // 标题行：[笔记名] H1 标题
            String title = result.h1Title;
            if (title == null || title.isEmpty()) {
                title = "(无标题)";
            }
            String displayTitle = String.format("[%s] %s", result.noteKey, title);
            
            JLabel titleLabel = new JLabel(displayTitle);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            titleLabel.setForeground(isSelected ? Color.WHITE : foregroundColor);
            panel.add(titleLabel, BorderLayout.NORTH);
            
            // 内容预览
            String preview = result.content != null ? result.content : "";
            if (preview.length() > 80) {
                preview = preview.substring(0, 80) + "...";
            }
            
            JLabel contentLabel = new JLabel(preview);
            contentLabel.setFont(contentLabel.getFont().deriveFont(11f));
            contentLabel.setForeground(isSelected ? new Color(220, 220, 220) : new Color(150, 150, 150));
            panel.add(contentLabel, BorderLayout.CENTER);
            
            // 分数
            JLabel scoreLabel = new JLabel(String.format("%.2f", result.score));
            scoreLabel.setFont(scoreLabel.getFont().deriveFont(10f));
            scoreLabel.setForeground(isSelected ? new Color(200, 200, 200) : new Color(120, 120, 120));
            panel.add(scoreLabel, BorderLayout.EAST);
            
            return panel;
        }
    }
}

