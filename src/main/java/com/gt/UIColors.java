package com.gt;

import java.awt.Color;

/**
 * UI 颜色常量类
 * 统一管理应用的配色方案，便于后续主题切换
 */
public class UIColors {
    
    // ===== 主题色系 =====
    
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
    
    // ===== 背景色系 =====
    
    /** 主背景 - 白色 */
    public static final Color BG_PRIMARY = new Color(255, 255, 255);
    
    /** 次背景 - 浅灰 */
    public static final Color BG_SECONDARY = new Color(245, 247, 250);
    
    /** 面板背景 */
    public static final Color BG_PANEL = new Color(250, 250, 250);
    
    /** 悬停背景 */
    public static final Color BG_HOVER = new Color(240, 240, 240);
    
    /** 激活背景 */
    public static final Color BG_ACTIVE = new Color(236, 245, 255);
    
    // ===== 边框色系 =====
    
    /** 主边框色 */
    public static final Color BORDER_BASE = new Color(220, 223, 230);
    
    /** 浅边框色 */
    public static final Color BORDER_LIGHT = new Color(235, 238, 245);
    
    /** 深边框色 */
    public static final Color BORDER_DARK = new Color(200, 200, 200);
    
    // ===== 文本色系 =====
    
    /** 主文本色 */
    public static final Color TEXT_PRIMARY = new Color(48, 49, 51);
    
    /** 次文本色 */
    public static final Color TEXT_SECONDARY = new Color(96, 98, 102);
    
    /** 占位符文本色 */
    public static final Color TEXT_PLACEHOLDER = new Color(192, 196, 204);
    
    /** 禁用文本色 */
    public static final Color TEXT_DISABLED = new Color(192, 196, 204);
    
    // ===== 高亮色系 =====
    
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
    
    // ===== 特殊组件色 =====
    
    /** 行号背景 */
    public static final Color LINE_NUMBER_BG = new Color(248, 248, 248);
    
    /** 行号文本 */
    public static final Color LINE_NUMBER_TEXT = new Color(153, 153, 153);
    
    /** 行号当前行高亮 */
    public static final Color LINE_NUMBER_CURRENT = new Color(64, 158, 255);
    
    /** 滚动条轨道 */
    public static final Color SCROLLBAR_TRACK = new Color(240, 240, 240);
    
    /** 滚动条滑块 */
    public static final Color SCROLLBAR_THUMB = new Color(192, 192, 192);
    
    /** 滚动条滑块悬停 */
    public static final Color SCROLLBAR_THUMB_HOVER = new Color(160, 160, 160);
    
    // ===== 按钮色系 =====
    
    /** 主按钮背景 */
    public static final Color BTN_PRIMARY_BG = PRIMARY;
    
    /** 主按钮悬停 */
    public static final Color BTN_PRIMARY_HOVER = new Color(54, 142, 230);
    
    /** 主按钮激活 */
    public static final Color BTN_PRIMARY_ACTIVE = new Color(44, 130, 214);
    
    /** 主按钮文本 */
    public static final Color BTN_PRIMARY_TEXT = Color.WHITE;
    
    /** 次按钮背景 */
    public static final Color BTN_SECONDARY_BG = Color.WHITE;
    
    /** 次按钮悬停 */
    public static final Color BTN_SECONDARY_HOVER = new Color(245, 247, 250);
    
    /** 次按钮激活 */
    public static final Color BTN_SECONDARY_ACTIVE = new Color(236, 240, 245);
    
    /** 次按钮文本 */
    public static final Color BTN_SECONDARY_TEXT = TEXT_PRIMARY;
    
    /** 次按钮边框 */
    public static final Color BTN_SECONDARY_BORDER = BORDER_BASE;
    
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
}

