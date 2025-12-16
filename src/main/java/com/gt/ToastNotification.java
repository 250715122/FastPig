package com.gt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.WeakHashMap;

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
    
    private static final int TOAST_WIDTH = 360;
    private static final int TOAST_HEIGHT = 56;
    private static final int MARGIN = 18;
    private static final int DURATION = 2800; // 停留时长（不含入场动画）

    private static final int ENTER_MS = 180;
    private static final int EXIT_MS = 160;
    private static final int ANIM_TICK_MS = 15;
    private static final int ENTER_Y_OFFSET_START = -10; // 从上方轻微下滑进入
    private static final int EXIT_Y_OFFSET_END = -8;      // 退出时轻微上移

    // 同一 parentFrame 下只展示一个 toast，避免重叠
    private static final Map<JFrame, ToastNotification> ACTIVE_TOASTS = new WeakHashMap<>();
    
    private final JFrame parentFrame;
    private final Type type;
    private Timer hideTimer;
    private boolean isHiding = false; // 防止递归调用

    private Timer animTimer;
    private long animStartAt;
    private boolean supportsOpacity = true;
    private float currentOpacity = 1.0f;
    private int yOffset = 0;
    private boolean isShowing = false;

    private ToastPanel toastPanel;
    
    /**
     * 创建 Toast 通知
     * @param parentFrame 父窗口
     * @param message 消息内容
     * @param type 通知类型
     */
    public ToastNotification(JFrame parentFrame, String message, Type type) {
        super(parentFrame);
        this.parentFrame = parentFrame;
        this.type = type;
        
        // 透明窗口 + 自绘圆角卡片
        try {
            setBackground(new Color(0, 0, 0, 0));
            setOpacity(0.0f);
        } catch (Exception e) {
            supportsOpacity = false;
        }
        
        toastPanel = new ToastPanel(type.color);
        toastPanel.setLayout(new BorderLayout(12, 0));
        toastPanel.setOpaque(false);
        toastPanel.setBorder(new EmptyBorder(12, 14, 12, 14));
        
        // 图标标签
        JLabel iconLabel = new JLabel(type.icon);
        iconLabel.setFont(new Font("Arial", Font.BOLD, 18));
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
                beginHide();
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
        
        toastPanel.add(iconLabel, BorderLayout.WEST);
        toastPanel.add(messageLabel, BorderLayout.CENTER);
        toastPanel.add(closeLabel, BorderLayout.EAST);
        
        setContentPane(toastPanel);
        setSize(TOAST_WIDTH, TOAST_HEIGHT);
        
        // 设置位置
        updateLocation();
        
        // 点击通知可关闭
        toastPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                beginHide();
            }
        });
        
        // 自动隐藏：在入场动画结束后启动
        hideTimer = new Timer(DURATION, e -> beginHide());
        hideTimer.setRepeats(false);
    }
    
    /**
     * 显示通知
     */
    public void showToast() {
        if (isShowing) {
            return;
        }
        isShowing = true;

        synchronized (ACTIVE_TOASTS) {
            ToastNotification prev = ACTIVE_TOASTS.get(parentFrame);
            if (prev != null && prev != this) {
                prev.beginHideImmediately();
            }
            ACTIVE_TOASTS.put(parentFrame, this);
        }

        yOffset = ENTER_Y_OFFSET_START;
        setToastOpacity(0.0f);
        updateLocation();
        setVisible(true);
        startEnterAnimation();
    }
    
    /**
     * 隐藏通知
     */
    public void hide() {
        beginHide();
    }

    private void beginHide() {
        if (isHiding) {
            return;
        }
        isHiding = true;
        stopTimers();
        startExitAnimation();
    }

    private void beginHideImmediately() {
        if (isHiding) {
            return;
        }
        isHiding = true;
        stopTimers();
        cleanupAndDispose();
    }

    private void cleanupAndDispose() {
        try {
            setVisible(false);
        } finally {
            try {
                dispose();
            } finally {
                synchronized (ACTIVE_TOASTS) {
                    ToastNotification cur = ACTIVE_TOASTS.get(parentFrame);
                    if (cur == this) {
                        ACTIVE_TOASTS.remove(parentFrame);
                    }
                }
            }
        }
    }

    private void stopTimers() {
        if (hideTimer != null && hideTimer.isRunning()) {
            hideTimer.stop();
        }
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
    }

    private void startEnterAnimation() {
        animStartAt = System.currentTimeMillis();
        animTimer = new Timer(ANIM_TICK_MS, e -> {
            long now = System.currentTimeMillis();
            float t = Math.min(1.0f, (now - animStartAt) / (float) ENTER_MS);
            float eased = easeOutCubic(t);

            setToastOpacity(eased);
            yOffset = (int) (ENTER_Y_OFFSET_START + (0 - ENTER_Y_OFFSET_START) * eased);
            updateLocation();

            if (t >= 1.0f) {
                animTimer.stop();
                yOffset = 0;
                setToastOpacity(1.0f);
                updateLocation();
                hideTimer.start();
            }
        });
        animTimer.setRepeats(true);
        animTimer.start();
    }

    private void startExitAnimation() {
        animStartAt = System.currentTimeMillis();
        final float startOpacity = currentOpacity;
        final int startYOffset = yOffset;

        animTimer = new Timer(ANIM_TICK_MS, e -> {
            long now = System.currentTimeMillis();
            float t = Math.min(1.0f, (now - animStartAt) / (float) EXIT_MS);
            float eased = easeInCubic(t);

            float op = startOpacity + (0.0f - startOpacity) * eased;
            setToastOpacity(op);
            yOffset = (int) (startYOffset + (EXIT_Y_OFFSET_END - startYOffset) * eased);
            updateLocation();

            if (t >= 1.0f) {
                animTimer.stop();
                cleanupAndDispose();
            }
        });
        animTimer.setRepeats(true);
        animTimer.start();
    }

    private void setToastOpacity(float value) {
        currentOpacity = Math.max(0.0f, Math.min(1.0f, value));
        if (!supportsOpacity) {
            return;
        }
        try {
            setOpacity(currentOpacity);
        } catch (Exception e) {
            supportsOpacity = false;
        }
    }

    private float easeOutCubic(float t) {
        float p = t - 1.0f;
        return 1.0f + p * p * p;
    }

    private float easeInCubic(float t) {
        return t * t * t;
    }
    
    /**
     * 更新通知位置（父窗口顶部居中）
     */
    private void updateLocation() {
        if (parentFrame != null) {
            try {
                Point parentLocation = parentFrame.getLocationOnScreen();
                Dimension parentSize = parentFrame.getSize();

                int x = parentLocation.x + (parentSize.width - TOAST_WIDTH) / 2;
                int y = parentLocation.y + MARGIN + yOffset;

                Rectangle screen = getScreenBounds(parentLocation);
                x = Math.max(screen.x + 8, Math.min(x, screen.x + screen.width - TOAST_WIDTH - 8));
                y = Math.max(screen.y + 8, Math.min(y, screen.y + screen.height - TOAST_HEIGHT - 8));

                setLocation(x, y);
            } catch (IllegalComponentStateException ignore) {
                // 父窗口未显示时忽略
            }
        }
    }

    private Rectangle getScreenBounds(Point p) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        for (GraphicsDevice d : devices) {
            Rectangle b = d.getDefaultConfiguration().getBounds();
            if (b.contains(p)) {
                return b;
            }
        }
        return ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    }

    private static class ToastPanel extends JPanel {
        private final Color accent;
        private final int radius = 14;

        ToastPanel(Color accent) {
            this.accent = accent;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // shadow (soft)
                Color shadowBase = UIColors.SHADOW_MEDIUM;
                for (int i = 0; i < 6; i++) {
                    int pad = 1 + i;
                    int alpha = Math.max(0, shadowBase.getAlpha() - i * 6);
                    g2.setColor(new Color(0, 0, 0, alpha));
                    Shape s = new RoundRectangle2D.Float(pad, pad, w - pad * 2, h - pad * 2, radius, radius);
                    g2.draw(s);
                }

                Shape bg = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, radius, radius);
                g2.setColor(UIColors.BG_PRIMARY);
                g2.fill(bg);

                g2.setColor(UIColors.BORDER_BASE);
                g2.draw(bg);

                // accent bar (left)
                g2.setColor(accent);
                Shape bar = new RoundRectangle2D.Float(0, 0, 6, h - 1, radius, radius);
                g2.fill(bar);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
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

