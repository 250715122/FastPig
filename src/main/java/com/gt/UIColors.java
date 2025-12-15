package com.gt;

import java.awt.Color;

/**
 * UI 颜色常量类
 * 统一管理应用的配色方案，支持主题切换
 */
public class UIColors {
    
    private static ThemeManager themeManager = ThemeManager.getInstance();
    
    // ===== 主题色系（不随主题改变）=====
    
    /** 主色调 - 浅蓝色 */
    public static final Color PRIMARY = new Color(64, 158, 255);
    public static final Color PRIMARY_LIGHT = new Color(144, 202, 255);
    public static final Color PRIMARY_LIGHTER = new Color(236, 245, 255);
    
    /** 成功色 - 绿色 */
    public static final Color SUCCESS = new Color(103, 194, 58);
    public static final Color SUCCESS_LIGHT = new Color(225, 243, 216);
    
    /** 警告色 - 橙色 */
    public static final Color WARNING = new Color(230, 162, 60);
    public static final Color WARNING_LIGHT = new Color(253, 246, 236);
    
    /** 危险色 - 红色 */
    public static final Color DANGER = new Color(245, 108, 108);
    public static final Color DANGER_LIGHT = new Color(254, 240, 240);
    
    /** 信息色 - 灰色 */
    public static final Color INFO = new Color(144, 147, 153);
    public static final Color INFO_LIGHT = new Color(244, 244, 245);
    
    // ===== 背景色系（动态跟随主题）=====
    
    /** 主背景 */
    public static Color BG_PRIMARY = themeManager.getColor("bg.primary");
    
    /** 次背景 */
    public static Color BG_SECONDARY = themeManager.getColor("bg.secondary");
    
    /** 面板背景 */
    public static Color BG_PANEL = themeManager.getColor("bg.panel");
    
    /** 悬停背景 */
    public static Color BG_HOVER = themeManager.getColor("bg.hover");
    
    /** 激活背景 */
    public static Color BG_ACTIVE = themeManager.getColor("bg.active");
    
    // ===== 边框色系（动态跟随主题）=====
    
    /** 主边框色 */
    public static Color BORDER_BASE = themeManager.getColor("border.base");
    
    /** 浅边框色 */
    public static Color BORDER_LIGHT = themeManager.getColor("border.light");
    
    /** 深边框色 */
    public static Color BORDER_DARK = themeManager.getColor("border.dark");
    
    // ===== 文本色系（动态跟随主题）=====
    
    /** 主文本色 */
    public static Color TEXT_PRIMARY = themeManager.getColor("text.primary");
    
    /** 次文本色 */
    public static Color TEXT_SECONDARY = themeManager.getColor("text.secondary");
    
    /** 占位符文本色 */
    public static Color TEXT_PLACEHOLDER = themeManager.getColor("text.placeholder");
    
    /** 禁用文本色 */
    public static Color TEXT_DISABLED = themeManager.getColor("text.disabled");
    
    // ===== 高亮色系（不随主题改变，保持视觉一致性）=====
    
    /** 首行高亮 - 浅蓝色半透明 */
    public static final Color HIGHLIGHT_FIRST_LINE = new Color(64, 158, 255, 30);
    
    /** 搜索高亮 - 橙色半透明 */
    public static final Color HIGHLIGHT_SEARCH = new Color(255, 152, 0, 65);
    
    /** 替换高亮 - 橙色半透明（与搜索一致） */
    public static final Color HIGHLIGHT_REPLACE = new Color(255, 152, 0, 65);
    
    /** 多光标高亮 - 蓝色半透明 */
    public static final Color HIGHLIGHT_MULTI_CURSOR = new Color(33, 150, 243, 38);
    
    /** 矩形选区高亮 - 灰色半透明 */
    public static final Color HIGHLIGHT_RECT_SELECTION = new Color(150, 150, 150, 38);
    
    // ===== 特殊组件色（动态跟随主题）=====
    
    /** 行号背景 */
    public static Color LINE_NUMBER_BG = themeManager.getColor("line.number.background");
    
    /** 行号文本 */
    public static Color LINE_NUMBER_TEXT = themeManager.getColor("line.number.foreground");
    
    /** 行号当前行高亮 */
    public static final Color LINE_NUMBER_CURRENT = new Color(64, 158, 255);
    
    /** 滚动条轨道 */
    public static final Color SCROLLBAR_TRACK = new Color(240, 240, 240);
    
    /** 滚动条滑块 */
    public static final Color SCROLLBAR_THUMB = new Color(192, 192, 192);
    
    /** 滚动条滑块悬停 */
    public static final Color SCROLLBAR_THUMB_HOVER = new Color(160, 160, 160);
    
    // ===== 按钮色系（动态跟随主题）=====
    
    /** 主按钮背景 */
    public static Color BTN_PRIMARY_BG = themeManager.getColor("button.primary.background");
    
    /** 主按钮悬停 */
    public static Color BTN_PRIMARY_HOVER = themeManager.getColor("button.primary.hover");
    
    /** 主按钮激活 */
    public static final Color BTN_PRIMARY_ACTIVE = new Color(44, 130, 214);
    
    /** 主按钮文本 */
    public static Color BTN_PRIMARY_TEXT = themeManager.getColor("button.primary.text");
    
    /** 次按钮背景 */
    public static Color BTN_SECONDARY_BG = themeManager.getColor("button.secondary.background");
    
    /** 次按钮悬停 */
    public static Color BTN_SECONDARY_HOVER = themeManager.getColor("button.secondary.hover");
    
    /** 次按钮激活 */
    public static Color BTN_SECONDARY_ACTIVE = themeManager.getColor("bg.hover");
    
    /** 次按钮文本 */
    public static Color BTN_SECONDARY_TEXT = themeManager.getColor("button.secondary.text");
    
    /** 次按钮边框 */
    public static Color BTN_SECONDARY_BORDER = themeManager.getColor("button.secondary.border");
    
    // ===== 列表色系（动态跟随主题）=====
    
    /** 列表选中背景 */
    public static Color LIST_SELECTION_BG = themeManager.getColor("list.selection.background");
    
    /** 列表选中前景 */
    public static Color LIST_SELECTION_FG = themeManager.getColor("list.selection.foreground");
    
    /** 危险按钮背景 */
    public static final Color BTN_DANGER_BG = DANGER;
    
    /** 危险按钮悬停 */
    public static final Color BTN_DANGER_HOVER = new Color(220, 96, 96);
    
    // ===== 阴影色 =====
    
    /** 轻阴影 */
    public static final Color SHADOW_LIGHT = new Color(0, 0, 0, 10);
    
    /** 中阴影 */
    public static final Color SHADOW_MEDIUM = new Color(0, 0, 0, 20);
    
    /** 重阴影 */
    public static final Color SHADOW_HEAVY = new Color(0, 0, 0, 30);
    
    // ===== 工具方法 =====
    
    /**
     * 使颜色变暗指定百分比
     * @param color 原始颜色
     * @param percent 变暗百分比 (0-100)
     * @return 变暗后的颜色
     */
    public static Color darken(Color color, int percent) {
        float factor = 1 - (percent / 100f);
        int r = Math.max(0, (int)(color.getRed() * factor));
        int g = Math.max(0, (int)(color.getGreen() * factor));
        int b = Math.max(0, (int)(color.getBlue() * factor));
        return new Color(r, g, b, color.getAlpha());
    }
    
    /**
     * 使颜色变亮指定百分比
     * @param color 原始颜色
     * @param percent 变亮百分比 (0-100)
     * @return 变亮后的颜色
     */
    public static Color lighten(Color color, int percent) {
        float factor = percent / 100f;
        int r = Math.min(255, color.getRed() + (int)((255 - color.getRed()) * factor));
        int g = Math.min(255, color.getGreen() + (int)((255 - color.getGreen()) * factor));
        int b = Math.min(255, color.getBlue() + (int)((255 - color.getBlue()) * factor));
        return new Color(r, g, b, color.getAlpha());
    }
    
    /**
     * 设置颜色透明度
     * @param color 原始颜色
     * @param alpha 透明度 (0-255)
     * @return 设置透明度后的颜色
     */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
    
    /**
     * 刷新所有动态颜色
     * 在主题切换时调用
     */
    public static void refresh() {
        themeManager = ThemeManager.getInstance();
        
        // 刷新背景色
        BG_PRIMARY = themeManager.getColor("bg.primary");
        BG_SECONDARY = themeManager.getColor("bg.secondary");
        BG_PANEL = themeManager.getColor("bg.panel");
        BG_HOVER = themeManager.getColor("bg.hover");
        BG_ACTIVE = themeManager.getColor("bg.active");
        
        // 刷新边框色
        BORDER_BASE = themeManager.getColor("border.base");
        BORDER_LIGHT = themeManager.getColor("border.light");
        BORDER_DARK = themeManager.getColor("border.dark");
        
        // 刷新文本色
        TEXT_PRIMARY = themeManager.getColor("text.primary");
        TEXT_SECONDARY = themeManager.getColor("text.secondary");
        TEXT_PLACEHOLDER = themeManager.getColor("text.placeholder");
        TEXT_DISABLED = themeManager.getColor("text.disabled");
        
        // 刷新特殊组件色
        LINE_NUMBER_BG = themeManager.getColor("line.number.background");
        LINE_NUMBER_TEXT = themeManager.getColor("line.number.foreground");
        
        // 刷新按钮色
        BTN_PRIMARY_BG = themeManager.getColor("button.primary.background");
        BTN_PRIMARY_HOVER = themeManager.getColor("button.primary.hover");
        BTN_PRIMARY_TEXT = themeManager.getColor("button.primary.text");
        BTN_SECONDARY_BG = themeManager.getColor("button.secondary.background");
        BTN_SECONDARY_HOVER = themeManager.getColor("button.secondary.hover");
        BTN_SECONDARY_ACTIVE = themeManager.getColor("bg.hover");
        BTN_SECONDARY_TEXT = themeManager.getColor("button.secondary.text");
        BTN_SECONDARY_BORDER = themeManager.getColor("button.secondary.border");
        
        // 刷新列表色
        LIST_SELECTION_BG = themeManager.getColor("list.selection.background");
        LIST_SELECTION_FG = themeManager.getColor("list.selection.foreground");
    }
}

