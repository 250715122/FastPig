package com.gt;

import javax.swing.*;
import java.awt.*;

/**
 * 界面配置面板
 */
public class UIPanel extends JPanel {
    
    private final SettingsDialog parent;
    private final AppConfig config;
    
    private JSpinner windowWidthSpinner;
    private JSpinner windowHeightSpinner;
    private JCheckBox startMaximizedCheckBox;
    private JSlider opacitySlider;
    private JLabel opacityValueLabel;
    
    public UIPanel(SettingsDialog parent) {
        this.parent = parent;
        this.config = parent.getConfig();
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        add(createTitle("🎨 界面配置"));
        add(Box.createVerticalStrut(20));
        
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
        add(Box.createVerticalStrut(25));
        
        // 窗口透明度
        add(createSection("窗口透明度", "设置窗口透明度，可透视到桌面（拖动滑块实时预览）"));
        
        // 透明度滑块和值标签的容器
        JPanel opacityPanel = new JPanel();
        opacityPanel.setLayout(new BoxLayout(opacityPanel, BoxLayout.X_AXIS));
        opacityPanel.setOpaque(false);
        opacityPanel.setAlignmentX(LEFT_ALIGNMENT);
        opacityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        opacitySlider = new JSlider(50, 100, 95);
        opacitySlider.setMajorTickSpacing(10);
        opacitySlider.setMinorTickSpacing(5);
        opacitySlider.setPaintTicks(true);
        opacitySlider.setPaintLabels(true);
        opacitySlider.setOpaque(false);
        opacitySlider.setPreferredSize(new Dimension(300, 50));
        opacitySlider.setMaximumSize(new Dimension(300, 50));
        
        opacityValueLabel = new JLabel("95%");
        opacityValueLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        opacityValueLabel.setForeground(UIColors.PRIMARY);
        opacityValueLabel.setPreferredSize(new Dimension(50, 25));
        
        // 添加滑块监听器以实时更新透明度
        opacitySlider.addChangeListener(e -> {
            int value = opacitySlider.getValue();
            opacityValueLabel.setText(value + "%");
            
            // 实时预览透明度效果
            if (!opacitySlider.getValueIsAdjusting()) {
                updateWindowOpacity(value);
            }
        });
        
        opacityPanel.add(opacitySlider);
        opacityPanel.add(Box.createHorizontalStrut(15));
        opacityPanel.add(opacityValueLabel);
        opacityPanel.add(Box.createHorizontalGlue());
        
        add(opacityPanel);
        add(Box.createVerticalStrut(10));
        
        // 透明度说明
        JLabel opacityNote = new JLabel("提示：透明度过低会影响内容可读性，建议保持在 70% 以上");
        opacityNote.setFont(new Font("Microsoft YaHei UI", Font.ITALIC, 11));
        opacityNote.setForeground(UIColors.TEXT_SECONDARY);
        opacityNote.setAlignmentX(LEFT_ALIGNMENT);
        add(opacityNote);
        
        add(Box.createVerticalStrut(20));
        JLabel noteLabel = new JLabel("注意：窗口大小配置将在下次启动时生效");
        noteLabel.setFont(new Font("Microsoft YaHei UI", Font.ITALIC, 11));
        noteLabel.setForeground(UIColors.WARNING);
        noteLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(noteLabel);
        
        add(Box.createVerticalGlue());
    }
    
    /**
     * 实时更新窗口透明度
     */
    private void updateWindowOpacity(int value) {
        float opacity = value / 100.0f;
        UnifiedNoteAppFrame frame = parent.getParentFrame();
        if (frame != null) {
            frame.updateOpacity(opacity);
        }
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
        windowWidthSpinner.setValue(config.getInt(AppConfig.UI_WINDOW_WIDTH, 1100));
        windowHeightSpinner.setValue(config.getInt(AppConfig.UI_WINDOW_HEIGHT, 720));
        startMaximizedCheckBox.setSelected(config.getBoolean(AppConfig.UI_START_MAXIMIZED, false));
        
        int opacity = config.getWindowOpacity();
        opacitySlider.setValue(opacity);
        opacityValueLabel.setText(opacity + "%");
    }
    
    public void saveConfig() {
        config.setInt(AppConfig.UI_WINDOW_WIDTH, (Integer) windowWidthSpinner.getValue());
        config.setInt(AppConfig.UI_WINDOW_HEIGHT, (Integer) windowHeightSpinner.getValue());
        config.setBoolean(AppConfig.UI_START_MAXIMIZED, startMaximizedCheckBox.isSelected());
        config.setWindowOpacity(opacitySlider.getValue());
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

