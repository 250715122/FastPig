package com.gt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Toast 通知组件
 * 轻量级的消息提示框，自动显示和消失
 */
public class ToastNotification extends JWindow {
    
    /**
     * 通知类型枚举
     */
    public enum Type {
        SUCCESS(UIColors.SUCCESS, "✓"),
        INFO(UIColors.PRIMARY, "ℹ"),
        WARNING(UIColors.WARNING, "⚠"),
        ERROR(UIColors.DANGER, "✕");
        
        final Color color;
        final String icon;
        
        Type(Color color, String icon) {
            this.color = color;
            this.icon = icon;
        }
    }
    
    private static final int TOAST_WIDTH = 320;
    private static final int TOAST_HEIGHT = 60;
    private static final int MARGIN = 20;
    private static final int DURATION = 3000; // 3秒
    
    private final JFrame parentFrame;
    private Timer hideTimer;
    private boolean isHiding = false; // 防止递归调用
    
    /**
     * 创建 Toast 通知
     * @param parentFrame 父窗口
     * @param message 消息内容
     * @param type 通知类型
     */
    public ToastNotification(JFrame parentFrame, String message, Type type) {
        super(parentFrame);
        this.parentFrame = parentFrame;
        
        // 确保Toast窗口完全不透明，不受父窗口透明度影响
        try {
            setBackground(Color.WHITE); // 设置不透明背景
            setOpacity(1.0f); // 确保完全不透明
        } catch (Exception e) {
            // 忽略异常，继续创建Toast
        }
        
        // 创建内容面板
        JPanel contentPanel = new JPanel(new BorderLayout(12, 0));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setOpaque(true); // 确保面板不透明
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(type.color, 2),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        
        // 图标标签
        JLabel iconLabel = new JLabel(type.icon);
        iconLabel.setFont(new Font("Arial", Font.BOLD, 20));
        iconLabel.setForeground(type.color);
        
        // 消息标签
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        messageLabel.setForeground(UIColors.TEXT_PRIMARY);
        
        // 关闭按钮
        JLabel closeLabel = new JLabel("×");
        closeLabel.setFont(new Font("Arial", Font.BOLD, 18));
        closeLabel.setForeground(UIColors.TEXT_SECONDARY);
        closeLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                hide();
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                closeLabel.setForeground(type.color);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                closeLabel.setForeground(UIColors.TEXT_SECONDARY);
            }
        });
        
        contentPanel.add(iconLabel, BorderLayout.WEST);
        contentPanel.add(messageLabel, BorderLayout.CENTER);
        contentPanel.add(closeLabel, BorderLayout.EAST);
        
        setContentPane(contentPanel);
        setSize(TOAST_WIDTH, TOAST_HEIGHT);
        
        // 添加阴影效果（通过背景实现）
        getRootPane().setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 4, 4, 4),
            contentPanel.getBorder()
        ));
        
        // 设置位置
        updateLocation();
        
        // 点击通知可关闭
        contentPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                hide();
            }
        });
        
        // 设置自动隐藏定时器
        hideTimer = new Timer(DURATION, e -> hide());
        hideTimer.setRepeats(false);
    }
    
    /**
     * 显示通知
     */
    public void showToast() {
        setVisible(true);
        hideTimer.start();
    }
    
    /**
     * 隐藏通知
     */
    public void hide() {
        // 防止递归调用
        if (isHiding) {
            return;
        }
        
        isHiding = true;
        try {
            if (hideTimer != null && hideTimer.isRunning()) {
                hideTimer.stop();
            }
            setVisible(false);
            dispose();
        } finally {
            isHiding = false;
        }
    }
    
    /**
     * 更新通知位置（右上角）
     */
    private void updateLocation() {
        if (parentFrame != null) {
            Point parentLocation = parentFrame.getLocationOnScreen();
            Dimension parentSize = parentFrame.getSize();
            
            int x = parentLocation.x + parentSize.width - TOAST_WIDTH - MARGIN;
            int y = parentLocation.y + MARGIN;
            
            setLocation(x, y);
        }
    }
    
    // ===== 静态工具方法 =====
    
    /**
     * 显示成功通知
     */
    public static void showSuccess(JFrame parent, String message) {
        SwingUtilities.invokeLater(() -> {
            ToastNotification toast = new ToastNotification(parent, message, Type.SUCCESS);
            toast.showToast();
        });
    }
    
    /**
     * 显示信息通知
     */
    public static void showInfo(JFrame parent, String message) {
        SwingUtilities.invokeLater(() -> {
            ToastNotification toast = new ToastNotification(parent, message, Type.INFO);
            toast.showToast();
        });
    }
    
    /**
     * 显示警告通知
     */
    public static void showWarning(JFrame parent, String message) {
        SwingUtilities.invokeLater(() -> {
            ToastNotification toast = new ToastNotification(parent, message, Type.WARNING);
            toast.showToast();
        });
    }
    
    /**
     * 显示错误通知
     */
    public static void showError(JFrame parent, String message) {
        SwingUtilities.invokeLater(() -> {
            ToastNotification toast = new ToastNotification(parent, message, Type.ERROR);
            toast.showToast();
        });
    }
}

