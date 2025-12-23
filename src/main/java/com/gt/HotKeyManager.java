package com.gt;

import org.apache.logging.log4j.LogManager;
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
    
    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(HotKeyManager.class);
    
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
    private static final int HOTKEY_SYNC = 5; // Alt+S 同步到云端
    private static final int HOTKEY_PULL = 6; // Alt+U 从云端下载
    // 兼容 Ctrl 系列（撤回）
    
    public HotKeyManager(JFrame frame, JTextArea textArea) {
        this.targetFrame = frame;
        this.targetTextArea = textArea;
    }
    
    /**
     * 初始化热键管理器，自动选择最佳方案
     */
    public void initialize() {
        logger.info("正在初始化全局热键管理器...");
        SystemUtils.printSystemDiagnostics();
        
        // 首先尝试JNativeHook（推荐）
        if (tryJNativeHook()) {
            activeMethod = HotKeyMethod.JNATIVEHOOK;
            logger.info("使用JNativeHook实现全局热键");
            return;
        }
        
        // 备选方案：JIntellitype
        if (tryJIntellitype()) {
            activeMethod = HotKeyMethod.JINTELLITYPE;
            logger.info("使用JIntellitype实现全局热键");
            return;
        }
        
        // 无热键支持
        activeMethod = HotKeyMethod.NONE;
        logger.warn("全局热键功能不可用");
        logger.warn("建议：1. 以管理员权限运行程序 2. 检查杀毒软件设置 3. 使用窗口菜单或系统托盘作为替代");
    }
    
    /**
     * 尝试使用JNativeHook
     */
    private boolean tryJNativeHook() {
        try {
            logger.debug("尝试初始化JNativeHook...");
            
            // 禁用JNativeHook的日志输出（减少控制台噪音）
            Logger jnhLogger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            jnhLogger.setLevel(Level.WARNING);
            jnhLogger.setUseParentHandlers(false);
            
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
            
            logger.info("JNativeHook初始化成功");
            printJNativeHookHotkeys();
            return true;
            
        } catch (NativeHookException e) {
            logger.warn("JNativeHook初始化失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("JNativeHook初始化异常: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 尝试使用JIntellitype
     */
    private boolean tryJIntellitype() {
        try {
            logger.debug("尝试初始化JIntellitype...");
            
            // 注册热键（Alt 系列）
            JIntellitype.getInstance().registerHotKey(HOTKEY_SHOW_NORMAL, JIntellitype.MOD_ALT, (int)'N');
            JIntellitype.getInstance().registerHotKey(HOTKEY_SHOW_MAX, JIntellitype.MOD_ALT, (int)'M');
            JIntellitype.getInstance().registerHotKey(HOTKEY_MINIMIZE, JIntellitype.MOD_ALT, (int)'L');
            JIntellitype.getInstance().registerHotKey(HOTKEY_EXIT, JIntellitype.MOD_ALT, (int)'Q');
            JIntellitype.getInstance().registerHotKey(HOTKEY_SYNC, JIntellitype.MOD_ALT, (int)'S');
            JIntellitype.getInstance().registerHotKey(HOTKEY_PULL, JIntellitype.MOD_ALT, (int)'U');
            // （撤回）不注册 Ctrl 系列
            
            // 添加热键监听器
            JIntellitype.getInstance().addHotKeyListener(new HotkeyListener() {
                @Override
                public void onHotKey(int identifier) {
                    handleJIntellitypeHotKey(identifier);
                }
            });
            
            logger.info("JIntellitype初始化成功");
            printJIntellitypeHotkeys();
            return true;
            
        } catch (Exception e) {
            logger.warn("JIntellitype初始化失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 处理JNativeHook按键事件
     */
    private void handleJNativeHookKeyPress(NativeKeyEvent e) {
        // 检查Alt键和Ctrl键是否按下
        boolean altPressed = (e.getModifiers() & NativeKeyEvent.ALT_MASK) != 0;
        boolean ctrlPressed = (e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0;
        
        if (altPressed) {
            logger.debug("检测到Alt组合键: Alt + {}", NativeKeyEvent.getKeyText(e.getKeyCode()));
            
            switch (e.getKeyCode()) {
                case NativeKeyEvent.VC_N:
                    logger.debug("执行Alt+N: 恢复窗口");
                    showWindowNormal();
                    break;
                case NativeKeyEvent.VC_M:
                    logger.debug("执行Alt+M: 最大化窗口");
                    showWindowMaximized();
                    break;
                case NativeKeyEvent.VC_L:
                    logger.debug("执行Alt+L: 最小化窗口");
                    minimizeWindow();
                    break;
                case NativeKeyEvent.VC_Q:
                    logger.debug("执行Alt+Q: 退出程序");
                    exitApplication();
                    break;
                case NativeKeyEvent.VC_S:
                    logger.info("检测到 Alt+S 按键，开始上传");
                    syncToCloud();
                    break;
                case NativeKeyEvent.VC_U:
                    logger.info("检测到 Alt+U 按键，开始下载");
                    pullFromCloud();
                    break;
                // Alt+P 不作为全局热键处理（预览仅在应用内快捷键生效）
                default:
                    logger.debug("未处理的Alt组合键: {}", NativeKeyEvent.getKeyText(e.getKeyCode()));
                    break;
            }
        }
        // （撤回）Ctrl 系列不再作为全局热键
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
            case HOTKEY_SYNC:
                syncToCloud();
                break;
            case HOTKEY_PULL:
                pullFromCloud();
                break;
            // 预览仅在应用内快捷键处理，这里不做全局处理
        }
    }

    /**
     * 同步数据库到云端（上传）
     */
    private void syncToCloud() {
        logger.info("[上传] 开始执行");
        
        // 获取当前活动的编辑器窗口
        UnifiedNoteAppFrame activeFrame = UnifiedNoteAppFrame.getActiveInstance();
        logger.debug("[上传] 活动窗口: {}", (activeFrame != null ? "已找到" : "未找到"));
        
        try {
            // 更新状态栏
            if (activeFrame != null) {
                activeFrame.updateStatusLeft("正在上传到云端…");
            }
            
            // 执行同步
            long startTime = System.currentTimeMillis();
            boolean ok = DbSyncService.getInstance().syncToCloud();
            long elapsed = System.currentTimeMillis() - startTime;
            
            logger.info("[上传] 耗时: {}ms, 结果: {}", elapsed, (ok ? "成功" : "失败"));
            
            // 更新结果
            if (ok) {
                if (activeFrame != null) {
                    activeFrame.updateStatusLeft("上传云端成功");
                }
            } else {
                if (activeFrame != null) {
                    activeFrame.updateStatusLeft("上传云端失败");
                }
            }
        } catch (Exception ex) {
            logger.error("[上传] 异常: {}", ex.getMessage(), ex);
            if (activeFrame != null) {
                activeFrame.updateStatusLeft("上传云端失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 从云端下载数据库（下载）
     */
    private void pullFromCloud() {
        logger.info("[下载] 开始执行");
        
        // 获取当前活动的编辑器窗口
        UnifiedNoteAppFrame activeFrame = UnifiedNoteAppFrame.getActiveInstance();
        logger.debug("[下载] 活动窗口: {}", (activeFrame != null ? "已找到" : "未找到"));
        
        try {
            // 更新状态栏
            if (activeFrame != null) {
                activeFrame.updateStatusLeft("正在从云端下载…");
            }
            
            // 执行下载
            long startTime = System.currentTimeMillis();
            boolean ok = DbSyncService.getInstance().syncFromCloud();
            long elapsed = System.currentTimeMillis() - startTime;
            
            logger.info("[下载] 耗时: {}ms, 结果: {}", elapsed, (ok ? "成功" : "失败"));
            
            // 更新结果
            if (ok) {
                if (activeFrame != null) {
                    activeFrame.updateStatusLeft("云端下载成功");
                    // 提示：需要重启应用才能看到云端数据
                    javax.swing.JOptionPane.showMessageDialog(
                        activeFrame,
                        "数据已从云端更新到本地！",
                        "下载成功",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE
                    );
                }
            } else {
                if (activeFrame != null) {
                    activeFrame.updateStatusLeft("云端下载失败");
                }
            }
        } catch (Exception ex) {
            logger.error("[下载] 异常: {}", ex.getMessage(), ex);
            if (activeFrame != null) {
                activeFrame.updateStatusLeft("云端下载失败: " + ex.getMessage());
            }
        }
    }

    // 预览切换的全局热键逻辑已移除，避免与应用内快捷键冲突
    
    /**
     * 显示窗口（正常大小）
     */
    private void showWindowNormal() {
        // 优先操作新的统一面板
        UnifiedNoteAppFrame active = UnifiedNoteAppFrame.getActiveInstance();
        if (active != null) {
            active.setVisible(true);
            active.setExtendedState(JFrame.NORMAL);
            active.toFront();
            active.requestFocus();
            active.setAlwaysOnTop(true);
            active.setAlwaysOnTop(false);
            active.focusEditor();
            logger.debug("窗口已恢复正常大小");
            return;
        }
        // 退化处理：使用旧的 targetFrame
        if (targetFrame != null) {
            targetFrame.setVisible(true);
            targetFrame.setExtendedState(JFrame.NORMAL);
            targetFrame.toFront();
            targetFrame.requestFocus();
            targetFrame.setAlwaysOnTop(true);
            targetFrame.setAlwaysOnTop(false);
            if (targetTextArea != null) {
                javax.swing.SwingUtilities.invokeLater(() -> targetTextArea.requestFocusInWindow());
            }
            logger.debug("窗口已恢复正常大小");
        }
    }
    
    /**
     * 显示窗口（最大化）
     */
    private void showWindowMaximized() {
        UnifiedNoteAppFrame active = UnifiedNoteAppFrame.getActiveInstance();
        if (active != null) {
            active.setVisible(true);
            active.setExtendedState(JFrame.MAXIMIZED_BOTH);
            active.toFront();
            active.requestFocus();
            active.setAlwaysOnTop(true);
            active.setAlwaysOnTop(false);
            active.focusEditor();
            logger.debug("窗口已最大化显示");
            return;
        }
        if (targetFrame != null) {
            targetFrame.setVisible(true);
            targetFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            targetFrame.toFront();
            targetFrame.requestFocus();
            targetFrame.setAlwaysOnTop(true);
            targetFrame.setAlwaysOnTop(false);
            if (targetTextArea != null) {
                javax.swing.SwingUtilities.invokeLater(() -> targetTextArea.requestFocusInWindow());
            }
            logger.debug("窗口已最大化显示");
        }
    }
    
    /**
     * 最小化窗口
     */
    private void minimizeWindow() {
        UnifiedNoteAppFrame active = UnifiedNoteAppFrame.getActiveInstance();
        if (active != null) {
            active.setExtendedState(JFrame.ICONIFIED);
            logger.debug("窗口已最小化");
            return;
        }
        if (targetFrame != null) {
            targetFrame.setExtendedState(JFrame.ICONIFIED);
            logger.debug("窗口已最小化");
        }
    }
    
    /**
     * 退出应用程序（带超时同步）
     */
    private void exitApplication() {
        logger.info("[退出] 正在同步数据库到云端（最多等待5秒）...");
        boolean synced = DbSyncService.getInstance().syncToCloudWithTimeout(5);
        if (synced) {
            logger.info("[退出] 同步完成，退出程序");
        } else {
            logger.info("[退出] 同步跳过或超时，直接退出程序");
        }
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
            logger.error("清理热键资源时出错: {}", e.getMessage(), e);
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
        logger.info("=== JNativeHook全局热键 ===");
        logger.info("Alt+N: 显示/恢复, Alt+M: 最大化, Alt+L: 最小化, Alt+Q: 退出");
        logger.info("Alt+S: 上传云端, Alt+U: 从云端下载");
    }
    
    /**
     * 打印JIntellitype热键说明
     */
    private void printJIntellitypeHotkeys() {
        logger.info("=== JIntellitype全局热键 ===");
        logger.info("Alt+N: 显示/恢复, Alt+M: 最大化, Alt+L: 最小化, Alt+Q: 退出");
        logger.info("Alt+S: 上传云端, Alt+U: 从云端下载");
    }
}
