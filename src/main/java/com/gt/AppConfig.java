package com.gt;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 应用配置管理类
 * 统一管理所有配置项，提供类型安全的读写接口
 */
public class AppConfig {
    
    private static final Logger logger = LogManager.getLogger(AppConfig.class);
    private static AppConfig instance;
    private Properties properties;
    private final String configFilePath;
    
    // ===== 配置项常量 =====
    
    // 云同步配置
    public static final String CLOUD_PROVIDER = "cloud.provider";
    public static final String NUTSTORE_USERNAME = "nutstore.username";
    public static final String NUTSTORE_PASSWORD = "nutstore.password";
    public static final String NUTSTORE_WEBDAV_BASE = "nutstore.webdav.base";
    public static final String NUTSTORE_SYNC_PATH = "nutstore.sync.path";
    
    // 编辑器配置
    public static final String EDITOR_FONT_NAME = "editor.font.name";
    public static final String EDITOR_FONT_SIZE = "editor.font.size";
    public static final String EDITOR_LINE_HEIGHT = "editor.line.height";
    public static final String EDITOR_TAB_SIZE = "editor.tab.size";
    public static final String EDITOR_AUTOSAVE_INTERVAL = "editor.autosave.interval";
    
    // 界面配置
    public static final String UI_THEME = "ui.theme";
    public static final String UI_WINDOW_WIDTH = "ui.window.width";
    public static final String UI_WINDOW_HEIGHT = "ui.window.height";
    public static final String UI_START_MAXIMIZED = "ui.start.maximized";
    public static final String UI_WINDOW_OPACITY = "ui.window.opacity";
    
    // 行为配置
    public static final String BEHAVIOR_SYNC_ON_START = "behavior.sync.on.start";
    public static final String BEHAVIOR_SYNC_ON_EXIT = "behavior.sync.on.exit";
    
    // ===== 默认值 =====
    
    private static final Properties DEFAULTS = new Properties();
    static {
        // 云同步默认值
        DEFAULTS.setProperty(CLOUD_PROVIDER, "nutstore");
        DEFAULTS.setProperty(NUTSTORE_WEBDAV_BASE, "https://dav.jianguoyun.com/dav/");
        DEFAULTS.setProperty(NUTSTORE_SYNC_PATH, "FastPig/notes");
        
        // 编辑器默认值
        DEFAULTS.setProperty(EDITOR_FONT_NAME, "Consolas");
        DEFAULTS.setProperty(EDITOR_FONT_SIZE, "14");
        DEFAULTS.setProperty(EDITOR_LINE_HEIGHT, "1.6");
        DEFAULTS.setProperty(EDITOR_TAB_SIZE, "4");
        DEFAULTS.setProperty(EDITOR_AUTOSAVE_INTERVAL, "10");
        
        // 界面默认值
        DEFAULTS.setProperty(UI_THEME, "light");
        DEFAULTS.setProperty(UI_WINDOW_WIDTH, "1100");
        DEFAULTS.setProperty(UI_WINDOW_HEIGHT, "720");
        DEFAULTS.setProperty(UI_START_MAXIMIZED, "false");
        DEFAULTS.setProperty(UI_WINDOW_OPACITY, "95");
        
        // 行为默认值
        DEFAULTS.setProperty(BEHAVIOR_SYNC_ON_START, "true");
        DEFAULTS.setProperty(BEHAVIOR_SYNC_ON_EXIT, "true");
    }
    
    private AppConfig() {
        this.configFilePath = System.getProperty("user.dir") + File.separator + "config.properties";
        this.properties = new Properties(DEFAULTS);
        load();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }
    
    /**
     * 从配置文件加载配置
     */
    public synchronized void load() {
        Path configFile = Paths.get(configFilePath);
        
        if (Files.exists(configFile)) {
            try (InputStream input = new FileInputStream(configFile.toFile())) {
                properties.load(input);
                logger.info("配置文件加载成功: {}", configFilePath);
            } catch (IOException e) {
                logger.error("加载配置文件失败: {}", e.getMessage(), e);
            }
        } else {
            logger.warn("配置文件不存在，使用默认配置: {}", configFilePath);
            // 尝试从 classpath 加载
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (input != null) {
                    properties.load(input);
                    logger.info("从 classpath 加载配置成功");
                }
            } catch (IOException e) {
                logger.error("从 classpath 加载配置失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 保存配置到文件
     */
    public synchronized void save() {
        try (OutputStream output = new FileOutputStream(configFilePath)) {
            properties.store(output, "FastPig Configuration File");
            logger.info("配置文件保存成功: {}", configFilePath);
        } catch (IOException e) {
            logger.error("保存配置文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取字符串配置
     */
    public String getString(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * 获取字符串配置（带默认值）
     */
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * 获取整数配置
     */
    public int getInt(String key) {
        return getInt(key, 0);
    }
    
    /**
     * 获取整数配置（带默认值）
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 '{}' 不是有效的整数，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 获取浮点数配置
     */
    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }
    
    /**
     * 获取浮点数配置（带默认值）
     */
    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 '{}' 不是有效的浮点数，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 获取布尔配置
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }
    
    /**
     * 获取布尔配置（带默认值）
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * 设置字符串配置
     */
    public void setString(String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }
    
    /**
     * 设置整数配置
     */
    public void setInt(String key, int value) {
        properties.setProperty(key, String.valueOf(value));
    }
    
    /**
     * 设置浮点数配置
     */
    public void setDouble(String key, double value) {
        properties.setProperty(key, String.valueOf(value));
    }
    
    /**
     * 设置布尔配置
     */
    public void setBoolean(String key, boolean value) {
        properties.setProperty(key, String.valueOf(value));
    }
    
    /**
     * 获取加密的密码
     */
    public String getEncryptedPassword(String key) {
        String encoded = properties.getProperty(key);
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            // 简单的 Base64 解码
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded);
        } catch (Exception e) {
            logger.warn("解码密码失败，返回原始值: {}", e.getMessage());
            return encoded;
        }
    }
    
    /**
     * 设置加密的密码
     */
    public void setEncryptedPassword(String key, String password) {
        if (password == null || password.isEmpty()) {
            properties.remove(key);
        } else {
            // 简单的 Base64 编码
            String encoded = Base64.getEncoder().encodeToString(password.getBytes());
            properties.setProperty(key, encoded);
        }
    }
    
    /**
     * 检查配置项是否存在
     */
    public boolean contains(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * 删除配置项
     */
    public void remove(String key) {
        properties.remove(key);
    }
    
    /**
     * 获取所有配置项
     */
    public Properties getAllProperties() {
        return new Properties(properties);
    }
    
    /**
     * 重置为默认配置
     */
    public synchronized void resetToDefaults() {
        properties.clear();
        properties.putAll(DEFAULTS);
        logger.info("配置已重置为默认值");
    }
    
    /**
     * 获取配置文件路径
     */
    public String getConfigFilePath() {
        return configFilePath;
    }
    
    // ===== 便捷方法：窗口透明度 =====
    
    /**
     * 获取窗口透明度（0-100，100表示完全不透明）
     */
    public int getWindowOpacity() {
        return getInt(UI_WINDOW_OPACITY, 95);
    }
    
    /**
     * 设置窗口透明度（0-100，100表示完全不透明）
     */
    public void setWindowOpacity(int opacity) {
        if (opacity < 0 || opacity > 100) {
            throw new IllegalArgumentException("透明度必须在 0-100 之间");
        }
        setInt(UI_WINDOW_OPACITY, opacity);
    }
}

