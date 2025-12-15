package com.gt;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * 主题管理器
 * 统一管理应用主题，支持浅色、深色、护眼色三种主题
 */
public class ThemeManager {
    
    private static ThemeManager instance;
    private Theme currentTheme;
    private final List<ThemeChangeListener> listeners = new ArrayList<>();
    
    /**
     * 主题枚举
     */
    public enum Theme {
        LIGHT("浅色", "light"),
        DARK("深色", "dark"),
        GREEN("护眼色", "green");
        
        private final String displayName;
        private final String configValue;
        
        Theme(String displayName, String configValue) {
            this.displayName = displayName;
            this.configValue = configValue;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getConfigValue() {
            return configValue;
        }
        
        public static Theme fromConfigValue(String value) {
            for (Theme theme : values()) {
                if (theme.configValue.equals(value)) {
                    return theme;
                }
            }
            return LIGHT; // 默认浅色主题
        }
    }
    
    /**
     * 主题变更监听器接口
     */
    public interface ThemeChangeListener {
        void onThemeChanged(Theme newTheme);
    }
    
    private ThemeManager() {
        // 从配置读取主题
        AppConfig config = AppConfig.getInstance();
        String themeValue = config.getString(AppConfig.UI_THEME, "light");
        this.currentTheme = Theme.fromConfigValue(themeValue);
    }
    
    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }
    
    /**
     * 获取当前主题
     */
    public Theme getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * 设置主题
     */
    public void setTheme(Theme theme) {
        if (this.currentTheme != theme) {
            this.currentTheme = theme;
            
            // 保存到配置
            AppConfig config = AppConfig.getInstance();
            config.setString(AppConfig.UI_THEME, theme.getConfigValue());
            config.save();
            
            // 通知所有监听器
            notifyThemeChanged();
        }
    }
    
    /**
     * 通过显示名称设置主题
     */
    public void setThemeByDisplayName(String displayName) {
        for (Theme theme : Theme.values()) {
            if (theme.getDisplayName().equals(displayName)) {
                setTheme(theme);
                return;
            }
        }
    }
    
    /**
     * 添加主题变更监听器
     */
    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 移除主题变更监听器
     */
    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 通知所有监听器主题已变更
     */
    private void notifyThemeChanged() {
        for (ThemeChangeListener listener : listeners) {
            try {
                listener.onThemeChanged(currentTheme);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取指定颜色键的颜色值
     */
    public Color getColor(String colorKey) {
        switch (currentTheme) {
            case DARK:
                return getDarkThemeColor(colorKey);
            case GREEN:
                return getGreenThemeColor(colorKey);
            case LIGHT:
            default:
                return getLightThemeColor(colorKey);
        }
    }
    
    // ===== 浅色主题配色 =====
    
    private Color getLightThemeColor(String key) {
        switch (key) {
            // 背景色
            case "bg.primary": return new Color(255, 255, 255);
            case "bg.secondary": return new Color(245, 247, 250);
            case "bg.panel": return new Color(250, 250, 250);
            case "bg.hover": return new Color(240, 240, 240);
            case "bg.active": return new Color(236, 245, 255);
            
            // 编辑器
            case "editor.background": return new Color(255, 255, 255);
            case "editor.foreground": return new Color(48, 49, 51);
            case "editor.caret": return new Color(0, 0, 0);
            case "editor.selection": return new Color(64, 158, 255, 50);
            
            // 行号
            case "line.number.background": return new Color(248, 248, 248);
            case "line.number.foreground": return new Color(153, 153, 153);
            
            // 文本色
            case "text.primary": return new Color(48, 49, 51);
            case "text.secondary": return new Color(96, 98, 102);
            case "text.placeholder": return new Color(192, 196, 204);
            case "text.disabled": return new Color(192, 196, 204);
            
            // 边框色
            case "border.base": return new Color(220, 223, 230);
            case "border.light": return new Color(235, 238, 245);
            case "border.dark": return new Color(200, 200, 200);
            
            // 按钮
            case "button.primary.background": return new Color(64, 158, 255);
            case "button.primary.hover": return new Color(54, 142, 230);
            case "button.primary.text": return Color.WHITE;
            case "button.secondary.background": return Color.WHITE;
            case "button.secondary.hover": return new Color(245, 247, 250);
            case "button.secondary.text": return new Color(48, 49, 51);
            case "button.secondary.border": return new Color(220, 223, 230);
            
            // 列表选中
            case "list.selection.background": return new Color(236, 245, 255);
            case "list.selection.foreground": return new Color(64, 158, 255);
            
            default: return new Color(255, 255, 255);
        }
    }
    
    // ===== 深色主题配色 =====
    
    private Color getDarkThemeColor(String key) {
        switch (key) {
            // 背景色
            case "bg.primary": return new Color(30, 30, 30);
            case "bg.secondary": return new Color(37, 37, 38);
            case "bg.panel": return new Color(45, 45, 48);
            case "bg.hover": return new Color(50, 50, 52);
            case "bg.active": return new Color(9, 71, 113);
            
            // 编辑器
            case "editor.background": return new Color(30, 30, 30);
            case "editor.foreground": return new Color(224, 224, 224);
            case "editor.caret": return new Color(255, 255, 255);
            case "editor.selection": return new Color(38, 79, 120);
            
            // 行号
            case "line.number.background": return new Color(37, 37, 38);
            case "line.number.foreground": return new Color(110, 118, 129);
            
            // 文本色
            case "text.primary": return new Color(224, 224, 224);
            case "text.secondary": return new Color(160, 160, 160);
            case "text.placeholder": return new Color(96, 96, 96);
            case "text.disabled": return new Color(96, 96, 96);
            
            // 边框色
            case "border.base": return new Color(60, 60, 60);
            case "border.light": return new Color(45, 45, 48);
            case "border.dark": return new Color(80, 80, 80);
            
            // 按钮
            case "button.primary.background": return new Color(14, 99, 156);
            case "button.primary.hover": return new Color(18, 116, 181);
            case "button.primary.text": return Color.WHITE;
            case "button.secondary.background": return new Color(45, 45, 48);
            case "button.secondary.hover": return new Color(55, 55, 58);
            case "button.secondary.text": return new Color(224, 224, 224);
            case "button.secondary.border": return new Color(60, 60, 60);
            
            // 列表选中
            case "list.selection.background": return new Color(60, 60, 60);
            case "list.selection.foreground": return new Color(224, 224, 224);
            
            default: return new Color(30, 30, 30);
        }
    }
    
    // ===== 护眼色主题配色 =====
    
    private Color getGreenThemeColor(String key) {
        switch (key) {
            // 背景色
            case "bg.primary": return new Color(199, 237, 204);
            case "bg.secondary": return new Color(180, 228, 185);
            case "bg.panel": return new Color(190, 232, 195);
            case "bg.hover": return new Color(170, 220, 175);
            case "bg.active": return new Color(160, 215, 165);
            
            // 编辑器
            case "editor.background": return new Color(199, 237, 204);
            case "editor.foreground": return new Color(44, 95, 45);
            case "editor.caret": return new Color(0, 0, 0);
            case "editor.selection": return new Color(120, 180, 125, 80);
            
            // 行号
            case "line.number.background": return new Color(180, 228, 185);
            case "line.number.foreground": return new Color(90, 140, 90);
            
            // 文本色
            case "text.primary": return new Color(44, 95, 45);
            case "text.secondary": return new Color(74, 124, 89);
            case "text.placeholder": return new Color(130, 180, 135);
            case "text.disabled": return new Color(130, 180, 135);
            
            // 边框色
            case "border.base": return new Color(151, 201, 158);
            case "border.light": return new Color(170, 220, 175);
            case "border.dark": return new Color(130, 180, 135);
            
            // 按钮
            case "button.primary.background": return new Color(76, 175, 80);
            case "button.primary.hover": return new Color(66, 160, 70);
            case "button.primary.text": return Color.WHITE;
            case "button.secondary.background": return new Color(190, 232, 195);
            case "button.secondary.hover": return new Color(180, 222, 185);
            case "button.secondary.text": return new Color(44, 95, 45);
            case "button.secondary.border": return new Color(151, 201, 158);
            
            // 列表选中
            case "list.selection.background": return new Color(170, 220, 175);
            case "list.selection.foreground": return new Color(44, 95, 45);
            
            default: return new Color(199, 237, 204);
        }
    }
    
    /**
     * 应用主题到组件（递归）
     */
    public void applyTheme(Component component) {
        if (component == null) {
            return;
        }
        
        // 应用到当前组件
        applyThemeToComponent(component);
        
        // 递归应用到子组件
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                applyTheme(child);
            }
        }
    }
    
    /**
     * 应用主题到单个组件
     */
    private void applyThemeToComponent(Component component) {
        // 特殊处理某些组件类型
        if (component instanceof JTextArea || component instanceof JTextPane || component instanceof JEditorPane) {
            component.setBackground(getColor("editor.background"));
            component.setForeground(getColor("editor.foreground"));
        } else if (component instanceof JPanel) {
            component.setBackground(getColor("bg.primary"));
        } else if (component instanceof JLabel) {
            component.setForeground(getColor("text.primary"));
        } else if (component instanceof JButton) {
            // 按钮样式由具体实现控制，这里不强制覆盖
        } else if (component instanceof JList) {
            component.setBackground(getColor("bg.primary"));
            component.setForeground(getColor("text.primary"));
        } else if (component instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) component;
            scrollPane.getViewport().setBackground(getColor("bg.primary"));
        }
    }
    
    /**
     * 获取所有主题的显示名称
     */
    public static String[] getThemeDisplayNames() {
        Theme[] themes = Theme.values();
        String[] names = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            names[i] = themes[i].getDisplayName();
        }
        return names;
    }
}

