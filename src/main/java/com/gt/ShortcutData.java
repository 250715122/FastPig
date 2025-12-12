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
     * 获取所有快捷键分类
     */
    public static List<ShortcutCategory> getCategories() {
        List<ShortcutCategory> categories = new ArrayList<>();
        
        // 📝 编辑操作
        categories.add(new ShortcutCategory("编辑操作", "📝", List.of(
            new Shortcut("Ctrl+S", "保存当前笔记"),
            new Shortcut("Ctrl+Z", "撤销上一步操作"),
            new Shortcut("Ctrl+Y", "重做被撤销的操作"),
            new Shortcut("Ctrl+F", "页内搜索"),
            new Shortcut("Ctrl+R", "批量替换")
        )));
        
        // 🎨 格式化
        categories.add(new ShortcutCategory("格式化", "🎨", List.of(
            new Shortcut("Ctrl+B", "加粗选中文本"),
            new Shortcut("Ctrl+Shift+R", "标红选中文本"),
            new Shortcut("Ctrl+1-5", "设置标题级别"),
            new Shortcut("Ctrl+E", "显示格式化工具条"),
            new Shortcut("Tab", "列表缩进/反缩进")
        )));
        
        // 👁 视图切换
        categories.add(new ShortcutCategory("视图切换", "👁", List.of(
            new Shortcut("Alt+P", "切换预览模式"),
            new Shortcut("Alt+F", "全屏预览"),
            new Shortcut("Alt+T", "显示/隐藏目录")
        )));
        
        // 📋 笔记管理
        categories.add(new ShortcutCategory("笔记管理", "📋", List.of(
            new Shortcut("Alt+D", "软删除当前笔记"),
            new Shortcut("Alt+Z", "撤销删除笔记"),
            new Shortcut("/", "斜杠快捷命令菜单")
        )));
        
        // 🖱 多光标编辑
        categories.add(new ShortcutCategory("多光标编辑", "🖱", List.of(
            new Shortcut("Alt+Shift+↓", "向下添加光标"),
            new Shortcut("Alt+Shift+↑", "向上添加光标"),
            new Shortcut("Alt+拖动", "矩形选区"),
            new Shortcut("Esc", "退出多光标模式")
        )));
        
        // ⚙ 系统功能
        categories.add(new ShortcutCategory("系统功能", "⚙", List.of(
            new Shortcut("Ctrl+,", "打开设置"),
            new Shortcut("Alt+S", "上传到云端"),
            new Shortcut("Alt+U", "从云端下载"),
            new Shortcut("Alt+Q", "退出程序")
        )));
        
        return categories;
    }
}

