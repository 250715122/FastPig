package com.gt;

import javax.swing.text.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * 自定义光标高亮绘制器
 * 绘制窄的竖线样式光标，而不是整个字符的高亮块
 */
public class CaretHighlightPainter implements Highlighter.HighlightPainter {
    
    private final Color color;
    private final int width;
    
    /**
     * 创建光标高亮绘制器
     * @param color 光标颜色
     * @param width 光标宽度（像素）
     */
    public CaretHighlightPainter(Color color, int width) {
        this.color = color;
        this.width = width;
    }
    
    @Override
    public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
        try {
            // 获取光标位置的矩形
            Rectangle2D r = c.modelToView2D(p0);
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 绘制竖线
            g2d.setColor(color);
            g2d.fillRect((int)r.getX(), (int)r.getY(), width, (int)r.getHeight());
            
        } catch (BadLocationException e) {
            // 忽略
        }
    }
}

