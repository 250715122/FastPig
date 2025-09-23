package com.gt;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 系统工具类 - 用于检测系统权限和环境信息
 */
public class SystemUtils {
    
    /**
     * 检查是否以管理员权限运行
     * @return true if running as administrator, false otherwise
     */
    public static boolean isRunningAsAdmin() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows系统检查
                Process process = Runtime.getRuntime().exec("net session");
                int exitCode = process.waitFor();
                return exitCode == 0;
            } else {
                // Unix/Linux系统检查
                String user = System.getProperty("user.name");
                return "root".equals(user);
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取系统架构信息
     * @return 系统架构字符串
     */
    public static String getSystemArchitecture() {
        return System.getProperty("os.arch");
    }
    
    /**
     * 检查是否为64位系统
     * @return true if 64-bit system, false otherwise
     */
    public static boolean is64BitSystem() {
        String arch = getSystemArchitecture();
        return arch != null && arch.contains("64");
    }
    
    /**
     * 获取操作系统名称
     * @return 操作系统名称
     */
    public static String getOSName() {
        return System.getProperty("os.name");
    }
    
    /**
     * 获取Java版本
     * @return Java版本字符串
     */
    public static String getJavaVersion() {
        return System.getProperty("java.version");
    }
    
    /**
     * 打印系统诊断信息
     */
    public static void printSystemDiagnostics() {
        System.out.println("=== 系统诊断信息 ===");
        System.out.println("操作系统: " + getOSName());
        System.out.println("系统架构: " + getSystemArchitecture());
        System.out.println("64位系统: " + (is64BitSystem() ? "是" : "否"));
        System.out.println("Java版本: " + getJavaVersion());
        System.out.println("管理员权限: " + (isRunningAsAdmin() ? "是" : "否"));
        System.out.println("当前用户: " + System.getProperty("user.name"));
        System.out.println("工作目录: " + System.getProperty("user.dir"));
        System.out.println("==================");
    }
    
    /**
     * 检查系统是否支持全局热键
     * @return true if global hotkeys are likely supported, false otherwise
     */
    public static boolean isGlobalHotkeySupported() {
        String os = getOSName().toLowerCase();
        // 目前主要支持Windows和部分Linux桌面环境
        return os.contains("win") || os.contains("linux");
    }
    
    /**
     * 获取推荐的DLL文件名（用于JIntellitype）
     * @return 推荐的DLL文件名
     */
    public static String getRecommendedDllName() {
        return is64BitSystem() ? "JIntellitype64.dll" : "JIntellitype.dll";
    }
}
