package com.gt;

import com.gt.vector.LuceneVectorSearchService.VectorSearchResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
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
    
    // 跟随主窗口移动
    private JTextArea targetTextArea;
    private Window ownerWindow;
    private ComponentListener ownerListener;
    
    public VectorSearchPanel(Window owner) {
        super(owner);
        this.ownerWindow = owner;
        initComponents();
        setupOwnerListener();
    }
    
    private void initComponents() {
        // 不设置 AlwaysOnTop，让面板跟随主窗口的 z-order
        // 切换窗口时面板会正常被其他窗口遮盖
        // 禁止窗口获取焦点，保持输入框焦点
        setFocusableWindowState(false);
        
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
        resultList.setFixedCellHeight(28); // 单行显示
        resultList.setBorder(null);
        resultList.setFocusable(false); // 禁止列表获取焦点
        
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
        
        // 直接添加列表，不使用滚动条
        mainPanel.add(resultList, BorderLayout.CENTER);
        
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
     * 设置主窗口监听器，实现面板跟随主窗口移动
     */
    private void setupOwnerListener() {
        if (ownerWindow == null) return;
        
        ownerListener = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                updatePosition();
            }
            
            @Override
            public void componentResized(ComponentEvent e) {
                updatePosition();
            }
        };
        
        ownerWindow.addComponentListener(ownerListener);
    }
    
    /**
     * 更新面板位置（跟随主窗口）
     */
    private void updatePosition() {
        if (!isVisible() || targetTextArea == null) {
            return;
        }
        
        try {
            // 宽度为编辑区的一半
            int panelWidth = targetTextArea.getWidth() / 2;
            setSize(panelWidth, getHeight());
            
            Rectangle caretRect = targetTextArea.modelToView(targetTextArea.getCaretPosition());
            if (caretRect == null) return;
            
            Point screenLoc = targetTextArea.getLocationOnScreen();
            int x = screenLoc.x;
            int y = screenLoc.y + caretRect.y + caretRect.height + 5;
            
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (x + panelWidth > screenSize.width) {
                x = screenSize.width - panelWidth;
            }
            if (y + getHeight() > screenSize.height) {
                y = screenLoc.y + caretRect.y - getHeight() - 5;
            }
            if (y < 0) {
                y = 0;
            }
            
            setLocation(x, y);
        } catch (Exception e) {
            // 忽略位置更新异常
        }
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
        // 不获取焦点，让输入框保持焦点以便继续输入
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
     * 显示在文本区域的光标下方
     * @param textArea 文本区域组件
     */
    public void showBelowCaret(JTextArea textArea) {
        // 保存引用，用于跟随主窗口移动时重新计算位置
        this.targetTextArea = textArea;
        
        try {
            // 宽度为编辑区的一半
            int panelWidth = textArea.getWidth() / 2;
            // 高度根据结果数量动态计算：行高 * 数量 + 标题 + 状态栏 + 边距
            int rowHeight = 28;
            int headerHeight = 25; // 标题高度
            int statusHeight = 20; // 状态栏高度
            int padding = 20; // 边距
            int itemCount = listModel.size();
            int panelHeight = headerHeight + (rowHeight * itemCount) + statusHeight + padding;
            
            // 获取光标位置的矩形
            Rectangle caretRect = textArea.modelToView(textArea.getCaretPosition());
            if (caretRect == null) {
                // 回退到组件下方显示
                showBelow(textArea);
                return;
            }
            
            // 计算屏幕坐标 - x 与编辑区左边对齐
            Point screenLoc = textArea.getLocationOnScreen();
            int x = screenLoc.x;
            int y = screenLoc.y + caretRect.y + caretRect.height + 5; // 光标下方 5 像素
            
            // 确保不超出屏幕
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (x + panelWidth > screenSize.width) {
                x = screenSize.width - panelWidth;
            }
            if (y + panelHeight > screenSize.height) {
                // 如果下方空间不够，显示在光标上方
                y = screenLoc.y + caretRect.y - panelHeight - 5;
            }
            if (y < 0) {
                y = 0;
            }
            
            setSize(panelWidth, panelHeight);
            showAt(x, y);
        } catch (BadLocationException e) {
            // 发生异常时回退到组件下方显示
            showBelow(textArea);
        }
    }
    
    /**
     * 隐藏面板
     */
    public void hidePanel() {
        setVisible(false);
        targetTextArea = null; // 清除引用
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
     * 格式：描述 | H1标题          [笔记名] | V:0.66 K:0.15 T:0.81
     * 左侧：描述 | H1标题
     * 右侧：[笔记名] | V(向量) K(关键词) T(总分)
     */
    private class ResultCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, 
                int index, boolean isSelected, boolean cellHasFocus) {
            
            VectorSearchResult result = (VectorSearchResult) value;
            
            // 使用 JPanel + BorderLayout 实现左右布局
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(4, 8, 4, 8));
            
            // 左侧：描述 | H1标题
            StringBuilder leftText = new StringBuilder();
            if (result.noteDesc != null && !result.noteDesc.isEmpty()) {
                leftText.append(result.noteDesc);
            }
            // 处理特殊标记：__NOTE_NAME__ 显示为空（笔记名索引不显示 H1 标题）
            String displayH1 = result.h1Title;
            if ("__NOTE_NAME__".equals(displayH1)) {
                displayH1 = "";  // 笔记名索引不显示标题
            }
            if (displayH1 != null && !displayH1.isEmpty()) {
                if (leftText.length() > 0) leftText.append(" | ");
                leftText.append(displayH1);
            }
            
            JLabel leftLabel = new JLabel(leftText.toString());
            leftLabel.setFont(leftLabel.getFont().deriveFont(12f));
            
            // 右侧：[笔记名] | V:向量 K:关键词 T:总分
            String rightText = String.format("[%s] | V:%.2f K:%.2f T:%.2f", 
                result.noteKey, result.score, result.keywordScore, result.totalScore);
            JLabel rightLabel = new JLabel(rightText);
            rightLabel.setFont(rightLabel.getFont().deriveFont(11f));
            
            // 设置颜色
            if (isSelected) {
                panel.setBackground(selectionColor);
                leftLabel.setForeground(Color.WHITE);
                rightLabel.setForeground(new Color(200, 200, 200));
            } else {
                panel.setBackground(backgroundColor);
                leftLabel.setForeground(foregroundColor);
                rightLabel.setForeground(new Color(150, 150, 150));
            }
            
            panel.add(leftLabel, BorderLayout.WEST);
            panel.add(rightLabel, BorderLayout.EAST);
            
            return panel;
        }
    }
}

