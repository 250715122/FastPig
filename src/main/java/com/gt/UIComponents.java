package com.gt;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * UI 组件工具类
 * 提供统一样式的现代化 UI 组件
 */
public class UIComponents {
    
    // 圆角半径常量
    public static final int RADIUS_SMALL = 4;
    public static final int RADIUS_MEDIUM = 6;
    public static final int RADIUS_LARGE = 8;
    
    /**
     * 创建现代化文本框
     */
    public static JTextField createTextField(int columns) {
        JTextField textField = new JTextField(columns);
        textField.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        textField.setForeground(UIColors.TEXT_PRIMARY);
        textField.setBorder(createRoundedBorder(UIColors.BORDER_BASE, RADIUS_SMALL, 8, 12));
        
        // 添加聚焦效果
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                textField.setBorder(createRoundedBorder(UIColors.PRIMARY, RADIUS_SMALL, 8, 12));
            }
            
            @Override
            public void focusLost(FocusEvent e) {
                textField.setBorder(createRoundedBorder(UIColors.BORDER_BASE, RADIUS_SMALL, 8, 12));
            }
        });
        
        return textField;
    }
    
    /**
     * 创建主要按钮（蓝色）
     */
    public static JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        button.setForeground(UIColors.BTN_PRIMARY_TEXT);
        button.setBackground(UIColors.BTN_PRIMARY_BG);
        button.setBorder(new EmptyBorder(6, 16, 6, 16));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        // 添加悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(UIColors.BTN_PRIMARY_HOVER);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(UIColors.BTN_PRIMARY_BG);
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(UIColors.BTN_PRIMARY_ACTIVE);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                button.setBackground(UIColors.BTN_PRIMARY_HOVER);
            }
        });
        
        return button;
    }
    
    /**
     * 创建次要按钮（白色边框）
     */
    public static JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        button.setForeground(UIColors.BTN_SECONDARY_TEXT);
        button.setBackground(UIColors.BTN_SECONDARY_BG);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.BTN_SECONDARY_BORDER, 1),
            new EmptyBorder(5, 15, 5, 15)
        ));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        // 添加悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(UIColors.BTN_SECONDARY_HOVER);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(UIColors.BTN_SECONDARY_BG);
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(UIColors.BTN_SECONDARY_ACTIVE);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                button.setBackground(UIColors.BTN_SECONDARY_HOVER);
            }
        });
        
        return button;
    }
    
    /**
     * 创建紧凑按钮（用于工具栏）
     */
    public static JButton createCompactButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        button.setForeground(UIColors.BTN_SECONDARY_TEXT);
        button.setBackground(UIColors.BTN_SECONDARY_BG);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.BTN_SECONDARY_BORDER, 1),
            new EmptyBorder(4, 12, 4, 12)
        ));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        // 添加悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(UIColors.BTN_SECONDARY_HOVER);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(UIColors.BTN_SECONDARY_BG);
            }
        });
        
        return button;
    }
    
    /**
     * 创建关闭按钮（红色悬停）
     */
    public static JButton createCloseButton() {
        JButton button = new JButton("×");
        button.setFont(new Font("Arial", Font.BOLD, 18));
        button.setForeground(UIColors.TEXT_SECONDARY);
        button.setBackground(UIColors.BTN_SECONDARY_BG);
        button.setBorder(new EmptyBorder(2, 10, 2, 10));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        // 添加悬停效果 - 红色
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(Color.WHITE);
                button.setBackground(UIColors.BTN_DANGER_BG);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(UIColors.TEXT_SECONDARY);
                button.setBackground(UIColors.BTN_SECONDARY_BG);
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(UIColors.BTN_DANGER_HOVER);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                button.setBackground(UIColors.BTN_DANGER_BG);
            }
        });
        
        return button;
    }
    
    /**
     * 创建现代化复选框
     */
    public static JCheckBox createCheckBox(String text) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        checkBox.setForeground(UIColors.TEXT_PRIMARY);
        checkBox.setBackground(UIColors.BG_PANEL);
        checkBox.setFocusPainted(false);
        checkBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return checkBox;
    }
    
    /**
     * 创建标签（带徽章样式）
     */
    public static JLabel createBadgeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        label.setForeground(UIColors.TEXT_SECONDARY);
        label.setBorder(new EmptyBorder(2, 8, 2, 8));
        return label;
    }
    
    /**
     * 创建面板（带圆角和阴影）
     */
    public static JPanel createPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(UIColors.BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.BORDER_LIGHT, 1),
            new EmptyBorder(8, 8, 8, 8)
        ));
        return panel;
    }
    
    /**
     * 创建圆角边框
     */
    public static Border createRoundedBorder(Color color, int radius, int vPadding, int hPadding) {
        return BorderFactory.createCompoundBorder(
            new RoundedBorder(color, radius),
            new EmptyBorder(vPadding, hPadding, vPadding, hPadding)
        );
    }
    
    /**
     * 圆角边框实现
     */
    private static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        
        public RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.draw(new RoundRectangle2D.Double(x, y, width - 1, height - 1, radius, radius));
            g2d.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }
        
        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.top = insets.right = insets.bottom = 1;
            return insets;
        }
    }
}

