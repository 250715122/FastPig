package com.gt;

import javax.swing.*;
import java.awt.*;

/**
 * 编辑器配置面板
 */
public class EditorPanel extends JPanel {
    
    private final SettingsDialog parent;
    private final AppConfig config;
    
    private JComboBox<String> fontComboBox;
    private JSpinner fontSizeSpinner;
    private JSpinner autosaveIntervalSpinner;
    
    public EditorPanel(SettingsDialog parent) {
        this.parent = parent;
        this.config = parent.getConfig();
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UIColors.BG_PRIMARY);
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        // 标题
        add(createTitle("✏ 编辑器配置"));
        add(Box.createVerticalStrut(20));
        
        // 字体选择
        add(createSection("编辑器字体", "等宽字体，适合代码编辑"));
        String[] fonts = {"Consolas", "Monaco", "Courier New", "Monospaced"};
        fontComboBox = new JComboBox<>(fonts);
        fontComboBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        fontComboBox.setBackground(UIColors.BG_PRIMARY);
        fontComboBox.setForeground(UIColors.TEXT_PRIMARY);
        // 设置下拉列表渲染器的主题
        fontComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                                                         int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (!isSelected) {
                    label.setBackground(UIColors.BG_PRIMARY);
                    label.setForeground(UIColors.TEXT_PRIMARY);
                }
                return label;
            }
        });
        fontComboBox.setMaximumSize(new Dimension(400, 35));
        fontComboBox.setAlignmentX(LEFT_ALIGNMENT);
        add(fontComboBox);
        add(Box.createVerticalStrut(15));
        
        // 字体大小
        add(createSection("字体大小", "10-24 像素"));
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(14, 10, 24, 1));
        fontSizeSpinner.setMaximumSize(new Dimension(100, 35));
        fontSizeSpinner.setAlignmentX(LEFT_ALIGNMENT);
        add(fontSizeSpinner);
        add(Box.createVerticalStrut(15));
        
        // 自动保存间隔
        add(createSection("自动保存间隔", "编辑停止后自动保存的时间（秒）"));
        autosaveIntervalSpinner = new JSpinner(new SpinnerNumberModel(10, 3, 60, 1));
        autosaveIntervalSpinner.setMaximumSize(new Dimension(100, 35));
        autosaveIntervalSpinner.setAlignmentX(LEFT_ALIGNMENT);
        add(autosaveIntervalSpinner);
        
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
        fontComboBox.setSelectedItem(config.getString(AppConfig.EDITOR_FONT_NAME, "Consolas"));
        fontSizeSpinner.setValue(config.getInt(AppConfig.EDITOR_FONT_SIZE, 14));
        autosaveIntervalSpinner.setValue(config.getInt(AppConfig.EDITOR_AUTOSAVE_INTERVAL, 10));
    }
    
    public void saveConfig() {
        config.setString(AppConfig.EDITOR_FONT_NAME, (String) fontComboBox.getSelectedItem());
        config.setInt(AppConfig.EDITOR_FONT_SIZE, (Integer) fontSizeSpinner.getValue());
        config.setInt(AppConfig.EDITOR_AUTOSAVE_INTERVAL, (Integer) autosaveIntervalSpinner.getValue());
    }
    
    public boolean validateConfig() {
        return true; // 编辑器配置没有必要验证
    }
    
    public void applyConfig() {
        // TODO: 立即应用编辑器配置到主窗口
        // 需要访问 UnifiedNoteAppFrame 的 bodyArea 来更新字体
    }
}

