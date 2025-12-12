package com.gt;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 快捷键显示面板
 */
public class ShortcutsPanel extends JPanel {
    
    private final SettingsDialog parent;
    
    public ShortcutsPanel(SettingsDialog parent) {
        this.parent = parent;
        
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        // 标题
        JLabel titleLabel = new JLabel("⌨ 快捷键列表");
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 18));
        titleLabel.setForeground(UIColors.TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(titleLabel, BorderLayout.NORTH);
        
        // 快捷键表格
        String[] columnNames = {"功能", "快捷键", "说明"};
        Object[][] data = {
            // 全局热键
            {"显示/恢复窗口", "Alt+N", "将窗口恢复到正常大小"},
            {"最大化窗口", "Alt+M", "最大化应用窗口"},
            {"最小化窗口", "Alt+L", "最小化应用窗口"},
            {"退出程序", "Alt+Q", "同步数据并退出"},
            {"上传到云端", "Alt+S", "将本地数据库上传到云端"},
            {"从云端下载", "Alt+U", "从云端下载并覆盖本地数据库"},
            
            // 编辑功能
            {"保存", "Ctrl+S", "手动保存当前笔记"},
            {"撤销", "Ctrl+Z", "撤销上一步操作"},
            {"重做", "Ctrl+Y", "重做被撤销的操作"},
            
            // 查找替换
            {"页内搜索", "Ctrl+F", "打开/关闭搜索栏"},
            {"批量替换", "Ctrl+R", "打开查找替换面板"},
            
            // 格式化
            {"加粗", "Ctrl+B", "将选中文本加粗"},
            {"标红", "Ctrl+Shift+R", "将选中文本标红"},
            {"标题1-5", "Ctrl+1-5", "设置标题级别"},
            {"悬浮工具条", "Ctrl+E", "显示格式化工具条"},
            
            // 预览和视图
            {"切换预览", "Alt+P", "开启/关闭 Markdown 预览"},
            {"全屏预览", "Alt+F", "切换全屏预览模式"},
            {"目录切换", "Alt+T", "显示/隐藏目录面板"},
            
            // 笔记管理
            {"软删除", "Alt+D", "删除当前笔记（60秒内可撤销）"},
            {"撤销删除", "Alt+Z", "恢复最近删除的笔记"},
            
            // 多光标
            {"向下添加光标", "Alt+Shift+Down", "在下一行相同列位置添加光标"},
            {"向上添加光标", "Alt+Shift+Up", "在上一行相同列位置添加光标"},
            {"矩形选区", "Alt+鼠标拖动", "创建矩形选区"},
            {"退出多光标", "Esc", "退出多光标编辑模式"},
            
            // 设置
            {"打开设置", "Ctrl+,", "打开设置对话框"},
        };
        
        DefaultTableModel model = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable table = new JTable(model);
        table.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        table.setRowHeight(30);
        table.setShowGrid(true);
        table.setGridColor(UIColors.BORDER_LIGHT);
        table.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(UIColors.BG_SECONDARY);
        
        // 设置列宽
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(300);
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIColors.BORDER_LIGHT));
        add(scrollPane, BorderLayout.CENTER);
    }
}

