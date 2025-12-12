package com.gt;

import javax.swing.*;
import java.awt.*;

/**
 * 关于面板
 */
public class AboutPanel extends JPanel {
    
    private final SettingsDialog parent;
    
    public AboutPanel(SettingsDialog parent) {
        this.parent = parent;
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        add(Box.createVerticalStrut(40));
        
        // 应用名称
        JLabel appNameLabel = new JLabel("FastPig 🐷⚡");
        appNameLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 28));
        appNameLabel.setForeground(UIColors.PRIMARY);
        appNameLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(appNameLabel);
        
        add(Box.createVerticalStrut(10));
        
        // 版本号
        JLabel versionLabel = new JLabel("版本 0.0.1-SNAPSHOT");
        versionLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        versionLabel.setForeground(UIColors.TEXT_SECONDARY);
        versionLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(versionLabel);
        
        add(Box.createVerticalStrut(30));
        
        // 描述
        JLabel descLabel = new JLabel("现代化的快捷命令笔记管理工具");
        descLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        descLabel.setForeground(UIColors.TEXT_PRIMARY);
        descLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(descLabel);
        
        add(Box.createVerticalStrut(10));
        
        JLabel desc2Label = new JLabel("支持 Markdown、LaTeX 公式渲染和云端同步");
        desc2Label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        desc2Label.setForeground(UIColors.TEXT_SECONDARY);
        desc2Label.setAlignmentX(CENTER_ALIGNMENT);
        add(desc2Label);
        
        add(Box.createVerticalStrut(40));
        
        // GitHub 链接
        JLabel githubLabel = new JLabel("GitHub: github.com/250715122/FastPig");
        githubLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        githubLabel.setForeground(UIColors.PRIMARY);
        githubLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(githubLabel);
        
        add(Box.createVerticalStrut(20));
        
        // 许可证
        JLabel licenseLabel = new JLabel("MIT License");
        licenseLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        licenseLabel.setForeground(UIColors.TEXT_SECONDARY);
        licenseLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(licenseLabel);
        
        add(Box.createVerticalGlue());
        
        // 底部版权
        JLabel copyrightLabel = new JLabel("© 2025 FastPig. All rights reserved.");
        copyrightLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        copyrightLabel.setForeground(UIColors.TEXT_PLACEHOLDER);
        copyrightLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(copyrightLabel);
        
        add(Box.createVerticalStrut(20));
    }
}

