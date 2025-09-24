package com.gt;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.JIntellitype;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 混合热键管理器 - 支持多种热键实现方案
 */
public class HotKeyManager {
    
    public enum HotKeyMethod {
        JINTELLITYPE,    // 传统的JIntellitype
        JNATIVEHOOK,     // 现代的JNativeHook
        NONE             // 无热键支持
    }
    
    private HotKeyMethod activeMethod = HotKeyMethod.NONE;
    private JFrame targetFrame;
    private JTextArea targetTextArea;
    
    // 热键标识
    private static final int HOTKEY_SHOW_NORMAL = 1;
    private static final int HOTKEY_SHOW_MAX = 2;
    private static final int HOTKEY_MINIMIZE = 3;
    private static final int HOTKEY_EXIT = 4;
    private static final int HOTKEY_EDITOR = 5; // Alt+S 打开编辑器
    
    public HotKeyManager(JFrame frame, JTextArea textArea) {
        this.targetFrame = frame;
        this.targetTextArea = textArea;
    }
    
    /**
     * 初始化热键管理器，自动选择最佳方案
     */
    public void initialize() {
        System.out.println("正在初始化全局热键管理器...");
        SystemUtils.printSystemDiagnostics();
        
        // 首先尝试JNativeHook（推荐）
        if (tryJNativeHook()) {
            activeMethod = HotKeyMethod.JNATIVEHOOK;
            System.out.println("✓ 使用JNativeHook实现全局热键");
            return;
        }
        
        // 备选方案：JIntellitype
        if (tryJIntellitype()) {
            activeMethod = HotKeyMethod.JINTELLITYPE;
            System.out.println("✓ 使用JIntellitype实现全局热键");
            return;
        }
        
        // 无热键支持
        activeMethod = HotKeyMethod.NONE;
        System.out.println("⚠ 全局热键功能不可用");
        System.out.println("建议：");
        System.out.println("1. 以管理员权限运行程序");
        System.out.println("2. 检查杀毒软件设置");
        System.out.println("3. 使用窗口菜单或系统托盘作为替代");
    }
    
    /**
     * 尝试使用JNativeHook
     */
    private boolean tryJNativeHook() {
        try {
            System.out.println("尝试初始化JNativeHook...");
            
            // 禁用JNativeHook的日志输出（减少控制台噪音）
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.WARNING);
            logger.setUseParentHandlers(false);
            
            // 注册native hook
            GlobalScreen.registerNativeHook();
            
            // 添加键盘监听器
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    handleJNativeHookKeyPress(e);
                }
                
                @Override
                public void nativeKeyReleased(NativeKeyEvent e) {
                    // 不需要处理释放事件
                }
                
                @Override
                public void nativeKeyTyped(NativeKeyEvent e) {
                    // 不需要处理输入事件
                }
            });
            
            System.out.println("JNativeHook初始化成功！");
            printJNativeHookHotkeys();
            return true;
            
        } catch (NativeHookException e) {
            System.err.println("JNativeHook初始化失败: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("JNativeHook初始化异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 尝试使用JIntellitype
     */
    private boolean tryJIntellitype() {
        try {
            System.out.println("尝试初始化JIntellitype...");
            
            // 注册热键
            JIntellitype.getInstance().registerHotKey(HOTKEY_SHOW_NORMAL, JIntellitype.MOD_ALT, (int)'N');
            JIntellitype.getInstance().registerHotKey(HOTKEY_SHOW_MAX, JIntellitype.MOD_ALT, (int)'M');
            JIntellitype.getInstance().registerHotKey(HOTKEY_MINIMIZE, JIntellitype.MOD_ALT, (int)'L');
            JIntellitype.getInstance().registerHotKey(HOTKEY_EXIT, JIntellitype.MOD_ALT, (int)'Q');
            JIntellitype.getInstance().registerHotKey(HOTKEY_EDITOR, JIntellitype.MOD_ALT, (int)'S');
            
            // 添加热键监听器
            JIntellitype.getInstance().addHotKeyListener(new HotkeyListener() {
                @Override
                public void onHotKey(int identifier) {
                    handleJIntellitypeHotKey(identifier);
                }
            });
            
            System.out.println("JIntellitype初始化成功！");
            printJIntellitypeHotkeys();
            return true;
            
        } catch (Exception e) {
            System.err.println("JIntellitype初始化失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 处理JNativeHook按键事件
     */
    private void handleJNativeHookKeyPress(NativeKeyEvent e) {
        // 检查Alt键是否按下
        boolean altPressed = (e.getModifiers() & NativeKeyEvent.ALT_MASK) != 0;
        
        if (altPressed) {
            System.out.println("检测到Alt组合键: Alt + " + NativeKeyEvent.getKeyText(e.getKeyCode()));
            
            switch (e.getKeyCode()) {
                case NativeKeyEvent.VC_N:
                    System.out.println("执行Alt+N: 恢复窗口");
                    showWindowNormal();
                    break;
                case NativeKeyEvent.VC_M:
                    System.out.println("执行Alt+M: 最大化窗口");
                    showWindowMaximized();
                    break;
                case NativeKeyEvent.VC_L:
                    System.out.println("执行Alt+L: 最小化窗口");
                    minimizeWindow();
                    break;
                case NativeKeyEvent.VC_Q:
                    System.out.println("执行Alt+Q: 退出程序");
                    exitApplication();
                    break;
                case NativeKeyEvent.VC_S:
                    System.out.println("执行Alt+S: 打开编辑器");
                    openEditor();
                    break;
                default:
                    System.out.println("未处理的Alt组合键: " + NativeKeyEvent.getKeyText(e.getKeyCode()));
                    break;
            }
        }
    }
    
    /**
     * 处理JIntellitype热键事件
     */
    private void handleJIntellitypeHotKey(int identifier) {
        switch (identifier) {
            case HOTKEY_SHOW_NORMAL:
                showWindowNormal();
                break;
            case HOTKEY_SHOW_MAX:
                showWindowMaximized();
                break;
            case HOTKEY_MINIMIZE:
                minimizeWindow();
                break;
            case HOTKEY_EXIT:
                exitApplication();
                break;
            case HOTKEY_EDITOR:
                openEditor();
                break;
        }
    }

    private void openEditor() {
        try {
            NoteRepository repo = new NoteRepository(System.getProperty("user.dir") + "/fastpig.db");
            UnifiedNoteAppFrame frame = new UnifiedNoteAppFrame(repo);
            frame.setVisible(true);
        } catch (Exception ex) {
            System.err.println("打开编辑器失败: " + ex.getMessage());
        }
    }
    
    /**
     * 显示窗口（正常大小）
     */
    private void showWindowNormal() {
        if (targetFrame != null) {
            // 强制显示窗口
            targetFrame.setVisible(true);
            // 恢复正常状态
            targetFrame.setExtendedState(JFrame.NORMAL);
            // 置顶并获得焦点
            targetFrame.toFront();
            targetFrame.requestFocus();
            // 确保窗口在任务栏中可见
            targetFrame.setAlwaysOnTop(true);
            targetFrame.setAlwaysOnTop(false);
            
            if (targetTextArea != null) {
                // 延迟聚焦文本区域，确保窗口完全显示后再聚焦
                javax.swing.SwingUtilities.invokeLater(() -> {
                    targetTextArea.requestFocusInWindow();
                });
            }
            
            System.out.println("窗口已恢复正常大小");
        }
    }
    
    /**
     * 显示窗口（最大化）
     */
    private void showWindowMaximized() {
        if (targetFrame != null) {
            // 强制显示窗口
            targetFrame.setVisible(true);
            // 最大化窗口
            targetFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            // 置顶并获得焦点
            targetFrame.toFront();
            targetFrame.requestFocus();
            // 确保窗口在任务栏中可见
            targetFrame.setAlwaysOnTop(true);
            targetFrame.setAlwaysOnTop(false);
            
            if (targetTextArea != null) {
                // 延迟聚焦文本区域，确保窗口完全显示后再聚焦
                javax.swing.SwingUtilities.invokeLater(() -> {
                    targetTextArea.requestFocusInWindow();
                });
            }
            
            System.out.println("窗口已最大化显示");
        }
    }
    
    /**
     * 最小化窗口
     */
    private void minimizeWindow() {
        if (targetFrame != null) {
            targetFrame.setExtendedState(JFrame.ICONIFIED);
            System.out.println("窗口已最小化");
        }
    }
    
    /**
     * 退出应用程序
     */
    private void exitApplication() {
        cleanup();
        System.exit(0);
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        try {
            if (activeMethod == HotKeyMethod.JNATIVEHOOK) {
                GlobalScreen.unregisterNativeHook();
            } else if (activeMethod == HotKeyMethod.JINTELLITYPE) {
                JIntellitype.getInstance().cleanUp();
            }
        } catch (Exception e) {
            System.err.println("清理热键资源时出错: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前使用的热键方法
     */
    public HotKeyMethod getActiveMethod() {
        return activeMethod;
    }
    
    /**
     * 打印JNativeHook热键说明
     */
    private void printJNativeHookHotkeys() {
        System.out.println("=== JNativeHook全局热键 ===");
        System.out.println("Alt + N: 显示/恢复窗口");
        System.out.println("Alt + M: 最大化窗口");
        System.out.println("Alt + L: 最小化窗口");
        System.out.println("Alt + Q: 退出程序");
        System.out.println("Alt + S: 打开编辑器");
        System.out.println("========================");
    }
    
    /**
     * 打印JIntellitype热键说明
     */
    private void printJIntellitypeHotkeys() {
        System.out.println("=== JIntellitype全局热键 ===");
        System.out.println("Alt + N: 显示/恢复窗口");
        System.out.println("Alt + M: 最大化窗口");
        System.out.println("Alt + L: 最小化窗口");
        System.out.println("Alt + Q: 退出程序");
        System.out.println("Alt + S: 打开编辑器");
        System.out.println("=========================");
    }
}
