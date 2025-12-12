package com.gt;

import java.util.ArrayList;
import java.util.List;

/**
 * 快捷键数据类
 * 管理所有快捷键的分类和数据
 */
public class ShortcutData {
    
    /**
     * 快捷键分类
     */
    public static class ShortcutCategory {
        public final String name;
        public final String icon;
        public final List<Shortcut> shortcuts;
        
        public ShortcutCategory(String name, String icon, List<Shortcut> shortcuts) {
            this.name = name;
            this.icon = icon;
            this.shortcuts = shortcuts;
        }
    }
    
    /**
     * 单个快捷键
     */
    public static class Shortcut {
        public final String keys;
        public final String description;
        
        public Shortcut(String keys, String description) {
            this.keys = keys;
            this.description = description;
        }
    }
    
    /**
     * 获取核心快捷键分类（2x2布局，使用颜色代码）
     */
    public static List<ShortcutCategory> getCategories() {
        List<ShortcutCategory> categories = new ArrayList<>();
        
        // 编辑操作 - 蓝色（合并笔记管理）
        categories.add(new ShortcutCategory("编辑操作", "#409EFF", List.of(
            new Shortcut("Ctrl+S", "保存当前笔记"),
            new Shortcut("Ctrl+Z", "撤销操作"),
            new Shortcut("Ctrl+F", "页内搜索"),
            new Shortcut("Ctrl+R", "批量替换"),
            new Shortcut("Alt+D", "删除笔记"),
            new Shortcut("/", "快捷菜单")
        )));
        
        // 格式化 - 绿色
        categories.add(new ShortcutCategory("格式化", "#67C23A", List.of(
            new Shortcut("Ctrl+B", "加粗文本"),
            new Shortcut("Ctrl+Shift+R", "标红文本"),
            new Shortcut("Ctrl+1-5", "标题级别"),
            new Shortcut("Ctrl+E", "格式工具条"),
            new Shortcut("Tab", "列表缩进"),
            new Shortcut("Alt+Shift+↑↓", "多光标")
        )));
        
        // 视图切换 - 橙色
        categories.add(new ShortcutCategory("视图切换", "#E6A23C", List.of(
            new Shortcut("Alt+P", "切换预览"),
            new Shortcut("Alt+F", "全屏预览"),
            new Shortcut("Alt+T", "显示目录"),
            new Shortcut("Ctrl+Home", "跳到顶部"),
            new Shortcut("Ctrl+L", "选择首行"),
            new Shortcut("Esc", "关闭面板")
        )));
        
        // 系统功能 - 灰色
        categories.add(new ShortcutCategory("系统功能", "#909399", List.of(
            new Shortcut("Ctrl+,", "打开设置"),
            new Shortcut("Alt+S", "上传云端"),
            new Shortcut("Alt+U", "下载云端"),
            new Shortcut("Alt+Z", "撤销删除"),
            new Shortcut("Alt+Q", "退出程序"),
            new Shortcut("Alt+M", "最大化")
        )));
        
        return categories;
    }
}

