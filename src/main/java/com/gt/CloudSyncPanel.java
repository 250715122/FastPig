package com.gt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 云同步配置面板
 */
public class CloudSyncPanel extends JPanel {
    
    private final SettingsDialog parent;
    private final AppConfig config;
    
    private JComboBox<String> providerComboBox;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField syncPathField;
    private JTextField webdavBaseField;
    private JCheckBox syncOnStartCheckBox;
    private JCheckBox syncOnExitCheckBox;
    private JButton testConnectionButton;
    
    public CloudSyncPanel(SettingsDialog parent) {
        this.parent = parent;
        this.config = parent.getConfig();
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        // 标题
        add(createTitle("☁ 云同步配置"));
        add(Box.createVerticalStrut(20));
        
        // 云存储提供商
        add(createSection("云存储提供商",
            "选择云存储服务提供商"));
        String[] providers = {"nutstore", "local", "none"};
        String[] providerNames = {"坚果云 WebDAV", "本地备份", "禁用云同步"};
        providerComboBox = new JComboBox<>(providerNames);
        providerComboBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        providerComboBox.setMaximumSize(new Dimension(400, 35));
        providerComboBox.setAlignmentX(LEFT_ALIGNMENT);
        add(providerComboBox);
        add(Box.createVerticalStrut(15));
        
        // 坚果云用户名
        add(createSection("坚果云用户名",
            "坚果云注册邮箱"));
        usernameField = UIComponents.createTextField(30);
        usernameField.setMaximumSize(new Dimension(400, 35));
        usernameField.setAlignmentX(LEFT_ALIGNMENT);
        add(usernameField);
        add(Box.createVerticalStrut(15));
        
        // 坚果云密码
        add(createSection("坚果云应用密码",
            "不是登录密码！需要在坚果云网页端生成"));
        passwordField = new JPasswordField(30);
        passwordField.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        passwordField.setBorder(UIComponents.createRoundedBorder(UIColors.BORDER_BASE, 4, 8, 12));
        passwordField.setMaximumSize(new Dimension(400, 35));
        passwordField.setAlignmentX(LEFT_ALIGNMENT);
        add(passwordField);
        add(Box.createVerticalStrut(15));
        
        // WebDAV 地址
        add(createSection("WebDAV 服务器地址",
            "一般不需要修改"));
        webdavBaseField = UIComponents.createTextField(30);
        webdavBaseField.setMaximumSize(new Dimension(400, 35));
        webdavBaseField.setAlignmentX(LEFT_ALIGNMENT);
        add(webdavBaseField);
        add(Box.createVerticalStrut(15));
        
        // 同步路径
        add(createSection("同步路径",
            "相对于 WebDAV 根目录的路径"));
        syncPathField = UIComponents.createTextField(30);
        syncPathField.setMaximumSize(new Dimension(400, 35));
        syncPathField.setAlignmentX(LEFT_ALIGNMENT);
        add(syncPathField);
        add(Box.createVerticalStrut(20));
        
        // 测试连接按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
        testConnectionButton = UIComponents.createSecondaryButton("测试连接");
        testConnectionButton.addActionListener(e -> testConnection());
        buttonPanel.add(testConnectionButton);
        add(buttonPanel);
        add(Box.createVerticalStrut(20));
        
        // 行为选项
        add(createSection("同步行为", ""));
        syncOnStartCheckBox = UIComponents.createCheckBox("启动时自动同步");
        syncOnStartCheckBox.setAlignmentX(LEFT_ALIGNMENT);
        add(syncOnStartCheckBox);
        add(Box.createVerticalStrut(8));
        
        syncOnExitCheckBox = UIComponents.createCheckBox("退出时自动同步");
        syncOnExitCheckBox.setAlignmentX(LEFT_ALIGNMENT);
        add(syncOnExitCheckBox);
        
        // 填充剩余空间
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
        String provider = config.getString(AppConfig.CLOUD_PROVIDER, "nutstore");
        int providerIndex = switch (provider) {
            case "local" -> 1;
            case "none" -> 2;
            default -> 0;
        };
        providerComboBox.setSelectedIndex(providerIndex);
        
        usernameField.setText(config.getString(AppConfig.NUTSTORE_USERNAME, ""));
        String password = config.getEncryptedPassword(AppConfig.NUTSTORE_PASSWORD);
        passwordField.setText(password);
        
        webdavBaseField.setText(config.getString(AppConfig.NUTSTORE_WEBDAV_BASE, 
            "https://dav.jianguoyun.com/dav/"));
        syncPathField.setText(config.getString(AppConfig.NUTSTORE_SYNC_PATH, "FastPig/notes"));
        
        syncOnStartCheckBox.setSelected(config.getBoolean(AppConfig.BEHAVIOR_SYNC_ON_START, true));
        syncOnExitCheckBox.setSelected(config.getBoolean(AppConfig.BEHAVIOR_SYNC_ON_EXIT, true));
    }
    
    public void saveConfig() {
        String provider = switch (providerComboBox.getSelectedIndex()) {
            case 1 -> "local";
            case 2 -> "none";
            default -> "nutstore";
        };
        config.setString(AppConfig.CLOUD_PROVIDER, provider);
        
        config.setString(AppConfig.NUTSTORE_USERNAME, usernameField.getText().trim());
        String password = new String(passwordField.getPassword());
        config.setEncryptedPassword(AppConfig.NUTSTORE_PASSWORD, password);
        
        config.setString(AppConfig.NUTSTORE_WEBDAV_BASE, webdavBaseField.getText().trim());
        config.setString(AppConfig.NUTSTORE_SYNC_PATH, syncPathField.getText().trim());
        
        config.setBoolean(AppConfig.BEHAVIOR_SYNC_ON_START, syncOnStartCheckBox.isSelected());
        config.setBoolean(AppConfig.BEHAVIOR_SYNC_ON_EXIT, syncOnExitCheckBox.isSelected());
    }
    
    public boolean validateConfig() {
        if (providerComboBox.getSelectedIndex() == 0) { // 坚果云
            if (usernameField.getText().trim().isEmpty()) {
                ToastNotification.showError(parent.getParentFrame(), "请输入坚果云用户名");
                usernameField.requestFocus();
                return false;
            }
            if (passwordField.getPassword().length == 0) {
                ToastNotification.showError(parent.getParentFrame(), "请输入坚果云密码");
                passwordField.requestFocus();
                return false;
            }
        }
        return true;
    }
    
    private void testConnection() {
        // 这里可以实际测试WebDAV连接
        // 暂时只做简单提示
        ToastNotification.showInfo(parent.getParentFrame(), "连接测试功能待实现");
    }
}

