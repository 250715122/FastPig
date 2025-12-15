package com.gt;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;

import java.awt.Color;
import java.awt.Window;

/**
 * Windows 标题栏/边框深色模式工具。
 *
 * <p>说明：Swing 设置 background 只能影响客户区（client area），无法改变 Windows 原生标题栏/边框。
 * 这里通过 DwmSetWindowAttribute 在 Windows 10/11 上启用沉浸式深色标题栏。</p>
 */
public final class WindowsTitleBarUtil {

    // Windows 10 1903/1909 使用 19，20H1+ 使用 20
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    // Windows 11：标题栏/边框/文字颜色（不支持时会返回失败，忽略即可）
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;
    private static final int DWMWA_COLOR_DEFAULT = 0xFFFFFFFF;

    private WindowsTitleBarUtil() {}

    /**
     * Minimal DWM binding (JNA 5.4.0 doesn't include Dwmapi wrapper in this repo).
     */
    private interface DwmApi extends StdCallLibrary {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
    }

    public static void applyForCurrentTheme(Window window) {
        ThemeManager.Theme theme = ThemeManager.getInstance().getCurrentTheme();
        apply(window, theme == ThemeManager.Theme.DARK);
    }

    public static void apply(Window window, boolean dark) {
        if (window == null || !isWindows()) {
            return;
        }
        // 需要窗口句柄：确保已经 displayable（通常 setVisible 之后）
        if (!window.isDisplayable()) {
            return;
        }
        try {
            // JNA 5.4.0 没有 WindowUtils#getHWND(Window)，使用 Native.getComponentPointer 获取 HWND
            HWND hwnd = new HWND(Native.getComponentPointer(window));

            // 1) 开关沉浸式深色标题栏
            int darkValue = dark ? 1 : 0;
            Memory darkMem = new Memory(4);
            darkMem.setInt(0, darkValue);
            int hr = DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, darkMem, 4);
            if (hr != 0) {
                // fallback for older Windows 10
                DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, darkMem, 4);
            }

            // 2) Windows 11 可选：显式设置标题栏/边框/文字颜色（不支持则失败返回，忽略）
            if (dark) {
                setColorAttribute(hwnd, DWMWA_CAPTION_COLOR, UIColors.BG_PRIMARY);
                setColorAttribute(hwnd, DWMWA_BORDER_COLOR, UIColors.BG_PRIMARY);
                setColorAttribute(hwnd, DWMWA_TEXT_COLOR, UIColors.TEXT_PRIMARY);
            } else {
                setColorAttribute(hwnd, DWMWA_CAPTION_COLOR, DWMWA_COLOR_DEFAULT);
                setColorAttribute(hwnd, DWMWA_BORDER_COLOR, DWMWA_COLOR_DEFAULT);
                setColorAttribute(hwnd, DWMWA_TEXT_COLOR, DWMWA_COLOR_DEFAULT);
            }
        } catch (Throwable ignored) {
            // 在非 Windows / 低版本 / 安全限制下可能失败，忽略不影响业务
        }
    }

    private static void setColorAttribute(HWND hwnd, int attr, Color color) {
        if (color == null) return;
        setColorAttribute(hwnd, attr, toColorRef(color));
    }

    private static void setColorAttribute(HWND hwnd, int attr, int colorRef) {
        try {
            Memory mem = new Memory(4);
            mem.setInt(0, colorRef);
            DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, attr, mem, 4);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Windows COLORREF: 0x00BBGGRR
     */
    private static int toColorRef(Color c) {
        return (c.getBlue() & 0xFF) << 16 | (c.getGreen() & 0xFF) << 8 | (c.getRed() & 0xFF);
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("windows");
    }
}


