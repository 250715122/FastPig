package com.gt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * 设置对话框
 * 提供应用程序的各项配置功能
 */
public class SettingsDialog extends JDialog {
    
    private final JFrame parent;
    private final AppConfig config;
    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    
    // 左侧导航列表
    private final String[] categories = {"云同步", "编辑器", "界面", "快捷键", "关于"};
    private JList<String> categoryList;
    
    // 配置面板
    private CloudSyncPanel cloudSyncPanel;
    private EditorPanel editorPanel;
    private UIPanel uiPanel;
    private ShortcutsPanel shortcutsPanel;
    private AboutPanel aboutPanel;
    
    // 底部按钮
    private JButton saveButton;
    private JButton cancelButton;
    private JButton applyButton;
    
    public SettingsDialog(JFrame parent) {
        super(parent, "设置", true);
        this.parent = parent;
        this.config = AppConfig.getInstance();
        this.cardLayout = new CardLayout();
        this.contentPanel = new JPanel(cardLayout);
        
        initializeComponents();
        layoutComponents();
        bindEvents();
        loadConfiguration();
        
        // 应用当前主题
        applyTheme();
        
        setSize(900, 600);
        setLocationRelativeTo(parent);
        setResizable(false);
    }
    
    /**
     * 初始化组件
     */
    private void initializeComponents() {
        // 左侧分类列表
        categoryList = new JList<>(categories);
        categoryList.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setSelectedIndex(0);
        categoryList.setFixedCellHeight(50);
        categoryList.setBackground(UIColors.BG_SECONDARY);
        categoryList.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 自定义列表渲染器
        categoryList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(10, 15, 10, 15));
                label.setOpaque(true);
                
                if (isSelected) {
                    label.setBackground(UIColors.PRIMARY_LIGHTER);
                    label.setForeground(UIColors.PRIMARY);
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                } else {
                    label.setBackground(UIColors.BG_SECONDARY);
                    label.setForeground(UIColors.TEXT_PRIMARY);
                }
                
                // 添加图标
                String icon = switch (index) {
                    case 0 -> "☁";
                    case 1 -> "✏";
                    case 2 -> "🎨";
                    case 3 -> "⌨";
                    case 4 -> "ℹ";
                    default -> "";
                };
                label.setText(" " + icon + "  " + value);
                
                return label;
            }
        });
        
        // 创建各个配置面板
        cloudSyncPanel = new CloudSyncPanel(this);
        editorPanel = new EditorPanel(this);
        uiPanel = new UIPanel(this);
        shortcutsPanel = new ShortcutsPanel(this);
        aboutPanel = new AboutPanel(this);
        
        // 添加到卡片布局
        contentPanel.add(cloudSyncPanel, "云同步");
        contentPanel.add(editorPanel, "编辑器");
        contentPanel.add(uiPanel, "界面");
        contentPanel.add(shortcutsPanel, "快捷键");
        contentPanel.add(aboutPanel, "关于");
        
        // 底部按钮
        saveButton = UIComponents.createPrimaryButton("保存");
        cancelButton = UIComponents.createSecondaryButton("取消");
        applyButton = UIComponents.createSecondaryButton("应用");
    }
    
    /**
     * 布局组件
     */
    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // 左侧导航面板
        JScrollPane leftScrollPane = new JScrollPane(categoryList);
        leftScrollPane.setPreferredSize(new Dimension(180, 0));
        leftScrollPane.setBorder(null);
        leftScrollPane.setBackground(UIColors.BG_SECONDARY);
        leftScrollPane.getViewport().setBackground(UIColors.BG_SECONDARY);
        
        // 右侧内容面板
        contentPanel.setBackground(UIColors.BG_PRIMARY);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setBackground(UIColors.BG_SECONDARY);
        buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIColors.BORDER_BASE));
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        
        // 主布局
        add(leftScrollPane, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // ESC 键关闭对话框
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }
    
    /**
     * 绑定事件
     */
    private void bindEvents() {
        // 分类列表选择事件
        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = categoryList.getSelectedValue();
                cardLayout.show(contentPanel, selected);
            }
        });
        
        // 保存按钮
        saveButton.addActionListener(e -> {
            if (saveConfiguration()) {
                ToastNotification.showSuccess(parent, "配置保存成功");
                dispose();
            }
        });
        
        // 取消按钮
        cancelButton.addActionListener(e -> dispose());
        
        // 应用按钮
        applyButton.addActionListener(e -> {
            if (saveConfiguration()) {
                ToastNotification.showSuccess(parent, "配置已应用");
            }
        });
    }
    
    /**
     * 从配置文件加载配置到界面
     */
    private void loadConfiguration() {
        cloudSyncPanel.loadConfig();
        editorPanel.loadConfig();
        uiPanel.loadConfig();
    }
    
    /**
     * 保存配置
     */
    private boolean saveConfiguration() {
        try {
            // 验证配置
            if (!cloudSyncPanel.validateConfig()) {
                categoryList.setSelectedIndex(0);
                return false;
            }
            if (!editorPanel.validateConfig()) {
                categoryList.setSelectedIndex(1);
                return false;
            }
            if (!uiPanel.validateConfig()) {
                categoryList.setSelectedIndex(2);
                return false;
            }
            
            // 保存配置
            cloudSyncPanel.saveConfig();
            editorPanel.saveConfig();
            uiPanel.saveConfig();
            
            // 持久化到文件
            config.save();
            
            // 应用配置
            applyConfiguration();
            
            return true;
        } catch (Exception e) {
            ToastNotification.showError(parent, "保存配置失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 应用配置（立即生效）
     */
    private void applyConfiguration() {
        // 应用编辑器配置
        editorPanel.applyConfig();
        
        // 其他配置在下次启动时生效
        // 可以在这里添加更多立即生效的配置
    }
    
    /**
     * 获取父窗口
     */
    public UnifiedNoteAppFrame getParentFrame() {
        return (UnifiedNoteAppFrame) parent;
    }
    
    /**
     * 获取配置管理器
     */
    public AppConfig getConfig() {
        return config;
    }
    
    /**
     * 应用主题到对话框
     */
    public void applyTheme() {
        ThemeManager themeManager = ThemeManager.getInstance();
        
        // 应用对话框背景
        getContentPane().setBackground(UIColors.BG_PRIMARY);
        
        // 应用分类列表主题
        categoryList.setBackground(UIColors.BG_SECONDARY);
        categoryList.repaint();
        
        // 遍历所有组件，更新左侧面板、内容面板、底部按钮面板
        for (Component comp : getContentPane().getComponents()) {
            if (comp instanceof JScrollPane) {
                // 左侧滚动面板
                JScrollPane scrollPane = (JScrollPane) comp;
                scrollPane.setBackground(UIColors.BG_SECONDARY);
                scrollPane.getViewport().setBackground(UIColors.BG_SECONDARY);
            } else if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                
                // 判断是内容面板还是按钮面板
                if (panel == contentPanel) {
                    // 内容面板
                    contentPanel.setBackground(UIColors.BG_PRIMARY);
                } else {
                    // 按钮面板
                    panel.setBackground(UIColors.BG_SECONDARY);
                    panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIColors.BORDER_BASE));
                }
            }
        }
        
        // 应用内容面板主题
        themeManager.applyTheme(contentPanel);
        
        // 应用各个配置面板主题
        if (cloudSyncPanel != null) {
            cloudSyncPanel.setBackground(UIColors.BG_PRIMARY);
            themeManager.applyTheme(cloudSyncPanel);
        }
        if (editorPanel != null) {
            editorPanel.setBackground(UIColors.BG_PRIMARY);
            themeManager.applyTheme(editorPanel);
        }
        if (uiPanel != null) {
            uiPanel.setBackground(UIColors.BG_PRIMARY);
            themeManager.applyTheme(uiPanel);
        }
        if (shortcutsPanel != null) {
            shortcutsPanel.setBackground(UIColors.BG_PRIMARY);
            themeManager.applyTheme(shortcutsPanel);
        }
        if (aboutPanel != null) {
            aboutPanel.setBackground(UIColors.BG_PRIMARY);
            themeManager.applyTheme(aboutPanel);
        }
        
        // 重绘对话框
        repaint();
        revalidate();
    }
}

