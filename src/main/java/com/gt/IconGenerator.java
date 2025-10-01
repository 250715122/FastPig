package com.gt;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * FastPig 图标生成工具
 * 生成不同尺寸的图标文件供应用程序使用
 */
public class IconGenerator {

    public static void main(String[] args) throws IOException {
        // 生成不同尺寸的图标
        int[] sizes = {16, 32, 48, 64, 128, 256};
        
        System.out.println("开始生成 FastPig 图标...");
        
        for (int size : sizes) {
            BufferedImage icon = createFastPigIcon(size);
            String filename = "src/main/resources/icons/fastpig-" + size + ".png";
            ImageIO.write(icon, "PNG", new File(filename));
            System.out.println("✓ 生成: " + filename);
        }
        
        // 生成主图标（256x256，用于 jpackage）
        BufferedImage mainIcon = createFastPigIcon(256);
        ImageIO.write(mainIcon, "PNG", new File("src/main/resources/icons/FastPig.png"));
        System.out.println("✓ 生成主图标: src/main/resources/icons/FastPig.png");
        
        System.out.println("\n图标生成完成！");
        System.out.println("注意：Windows 需要 .ico 格式，请使用在线工具将 FastPig.png 转换为 FastPig.ico");
        System.out.println("推荐工具：https://convertio.co/zh/png-ico/");
    }

    /**
     * 创建 FastPig 图标
     * 设计：蓝色渐变圆形背景 + 白色"快猪"剪影/字母 F
     */
    private static BufferedImage createFastPigIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        
        // 启用抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        // 绘制渐变背景（蓝色到深蓝色）
        GradientPaint gradient = new GradientPaint(
            0, 0, new Color(41, 128, 185),           // 浅蓝色
            size, size, new Color(52, 73, 94)        // 深蓝灰色
        );
        g.setPaint(gradient);
        g.fillRoundRect(0, 0, size, size, size/5, size/5);
        
        // 添加光泽效果（顶部高光）
        GradientPaint gloss = new GradientPaint(
            0, 0, new Color(255, 255, 255, 80),
            0, size/2, new Color(255, 255, 255, 0)
        );
        g.setPaint(gloss);
        g.fillRoundRect(0, 0, size, size/2, size/5, size/5);
        
        // 绘制白色 "F" 字母（代表 FastPig）
        g.setColor(Color.WHITE);
        Font font = new Font("Arial", Font.BOLD, (int)(size * 0.65));
        g.setFont(font);
        
        String text = "F";
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(text)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        
        // 绘制字母阴影
        g.setColor(new Color(0, 0, 0, 60));
        g.drawString(text, x + size/40, y + size/40);
        
        // 绘制主字母
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
        
        // 添加小闪电图标（表示"快"）
        if (size >= 64) {
            drawLightningBolt(g, size);
        }
        
        g.dispose();
        return image;
    }

    /**
     * 绘制小闪电图标（右下角）
     */
    private static void drawLightningBolt(Graphics2D g, int size) {
        int boltSize = size / 5;
        int x = (int)(size * 0.65);
        int y = (int)(size * 0.55);
        
        // 闪电形状
        int[] xPoints = {
            x, x + boltSize/3, x + boltSize/3,
            x + boltSize, x + boltSize*2/3, x + boltSize*2/3
        };
        int[] yPoints = {
            y, y, y + boltSize/2,
            y + boltSize/2, y + boltSize, y + boltSize/2
        };
        
        // 黄色闪电
        g.setColor(new Color(241, 196, 15));
        g.fillPolygon(xPoints, yPoints, 6);
        
        // 闪电边框
        g.setColor(new Color(243, 156, 18));
        g.setStroke(new BasicStroke(Math.max(1, size/100f)));
        g.drawPolygon(xPoints, yPoints, 6);
    }
}

