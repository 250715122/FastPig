package com.gt;

import javax.swing.*;
import java.awt.*;

/**
 * 界面配置面板
 */
public class UIPanel extends JPanel {
    
    private final SettingsDialog parent;
    private final AppConfig config;
    
    private JComboBox<String> themeComboBox;
    private JSpinner windowWidthSpinner;
    private JSpinner windowHeightSpinner;
    private JCheckBox startMaximizedCheckBox;
    
    public UIPanel(SettingsDialog parent) {
        this.parent = parent;
        this.config = parent.getConfig();
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UIColors.BG_PRIMARY);
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        add(createTitle("🎨 界面配置"));
        add(Box.createVerticalStrut(20));
        
        // 主题颜色
        add(createSection("主题颜色", "选择应用界面主题"));
        themeComboBox = new JComboBox<>(ThemeManager.getThemeDisplayNames());
        themeComboBox.setMaximumSize(new Dimension(200, 35));
        themeComboBox.setAlignmentX(LEFT_ALIGNMENT);
        themeComboBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        add(themeComboBox);
        add(Box.createVerticalStrut(15));
        
        // 窗口宽度
        add(createSection("窗口宽度", "默认窗口宽度（像素）"));
        windowWidthSpinner = new JSpinner(new SpinnerNumberModel(1100, 800, 2560, 10));
        windowWidthSpinner.setMaximumSize(new Dimension(150, 35));
        windowWidthSpinner.setAlignmentX(LEFT_ALIGNMENT);
        add(windowWidthSpinner);
        add(Box.createVerticalStrut(15));
        
        // 窗口高度
        add(createSection("窗口高度", "默认窗口高度（像素）"));
        windowHeightSpinner = new JSpinner(new SpinnerNumberModel(720, 600, 1440, 10));
        windowHeightSpinner.setMaximumSize(new Dimension(150, 35));
        windowHeightSpinner.setAlignmentX(LEFT_ALIGNMENT);
        add(windowHeightSpinner);
        add(Box.createVerticalStrut(20));
        
        // 启动时最大化
        startMaximizedCheckBox = UIComponents.createCheckBox("启动时最大化窗口");
        startMaximizedCheckBox.setAlignmentX(LEFT_ALIGNMENT);
        add(startMaximizedCheckBox);
        
        add(Box.createVerticalStrut(20));
        JLabel noteLabel = new JLabel("注意：窗口大小配置将在下次启动时生效");
        noteLabel.setFont(new Font("Microsoft YaHei UI", Font.ITALIC, 11));
        noteLabel.setForeground(UIColors.WARNING);
        noteLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(noteLabel);
        
        add(Box.createVerticalGlue());
    }
    
    private JLabel createTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 18));
        label.setForeground(UIColors.TEXT_PRIMARY);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
    
    private JPanel createSection(String title, String description) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        titleLabel.setForeground(UIColors.TEXT_PRIMARY);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(titleLabel);
        
        if (!description.isEmpty()) {
            JLabel descLabel = new JLabel(description);
            descLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
            descLabel.setForeground(UIColors.TEXT_SECONDARY);
            descLabel.setAlignmentX(LEFT_ALIGNMENT);
            panel.add(Box.createVerticalStrut(2));
            panel.add(descLabel);
        }
        
        return panel;
    }
    
    public void loadConfig() {
        // 加载主题配置
        ThemeManager themeManager = ThemeManager.getInstance();
        String currentThemeName = themeManager.getCurrentTheme().getDisplayName();
        themeComboBox.setSelectedItem(currentThemeName);
        
        windowWidthSpinner.setValue(config.getInt(AppConfig.UI_WINDOW_WIDTH, 1100));
        windowHeightSpinner.setValue(config.getInt(AppConfig.UI_WINDOW_HEIGHT, 720));
        startMaximizedCheckBox.setSelected(config.getBoolean(AppConfig.UI_START_MAXIMIZED, false));
    }
    
    public void saveConfig() {
        // 保存并应用主题
        String selectedTheme = (String) themeComboBox.getSelectedItem();
        ThemeManager themeManager = ThemeManager.getInstance();
        themeManager.setThemeByDisplayName(selectedTheme);
        
        // 刷新 UIColors
        UIColors.refresh();
        
        // 通知主窗口更新
        UnifiedNoteAppFrame activeFrame = UnifiedNoteAppFrame.getActiveInstance();
        if (activeFrame != null) {
            activeFrame.applyTheme();
        }
        
        // 应用主题到设置对话框自身
        parent.applyTheme();
        
        config.setInt(AppConfig.UI_WINDOW_WIDTH, (Integer) windowWidthSpinner.getValue());
        config.setInt(AppConfig.UI_WINDOW_HEIGHT, (Integer) windowHeightSpinner.getValue());
        config.setBoolean(AppConfig.UI_START_MAXIMIZED, startMaximizedCheckBox.isSelected());
    }
    
    public boolean validateConfig() {
        int width = (Integer) windowWidthSpinner.getValue();
        int height = (Integer) windowHeightSpinner.getValue();
        if (width < 800 || height < 600) {
            ToastNotification.showError(parent.getParentFrame(), "窗口大小不能小于 800x600");
            return false;
        }
        return true;
    }
}

