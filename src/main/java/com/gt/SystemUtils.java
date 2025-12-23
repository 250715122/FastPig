package com.gt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 系统工具类 - 用于检测系统权限和环境信息
 */
public class SystemUtils {
    
    private static final Logger logger = LogManager.getLogger(SystemUtils.class);
    
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
        logger.info("=== 系统诊断信息 ===");
        logger.info("操作系统: {}, 架构: {}, 64位: {}", getOSName(), getSystemArchitecture(), is64BitSystem());
        logger.info("Java版本: {}, 管理员: {}", getJavaVersion(), isRunningAsAdmin());
        logger.info("用户: {}, 工作目录: {}", System.getProperty("user.name"), System.getProperty("user.dir"));
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
