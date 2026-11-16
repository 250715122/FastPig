package com.gt;

import com.gt.config.ConfigKeys;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    private static final String B64_PREFIX = "b64:";
    
    // ===== 配置项常量 =====
    // 键名与默认值统一由 com.gt.config.ConfigKeys 定义，这里保留常量名供现有调用方使用。
    // 无默认值的凭据类键（账号、密码、主密码盐）不进 ConfigKeys，避免被写入默认值。
    
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
    
    // 行为配置
    public static final String BEHAVIOR_SYNC_ON_START = "behavior.sync.on.start";
    public static final String BEHAVIOR_SYNC_ON_EXIT = "behavior.sync.on.exit";
    public static final String SYNC_AUTO_UPLOAD_INTERVAL = "sync.auto.upload.interval"; // 分钟，0=禁用

    // 私密笔记主密码。这里只存 PBKDF2 派生出的校验值与盐，绝不存明文，
    // 也不能复用 setEncryptedPassword —— 那个只是 Base64 编码，可逆。
    public static final String MASTER_PASSWORD_SALT = "security.master.salt";
    public static final String MASTER_PASSWORD_VERIFIER = "security.master.verifier";

    // 网页端登录口令。与主密码同理，只存 PBKDF2 校验值，不进设置页表单。
    public static final String WEB_AUTH_PASSWORD_HASH = "web.auth.password.hash";
    
    // ===== 默认值 =====

    private static final Properties DEFAULTS = new Properties();
    static {
        for (com.gt.config.ConfigKeys k : com.gt.config.ConfigKeys.values()) {
            DEFAULTS.setProperty(k.key, String.valueOf(k.def));
        }
    }

    private AppConfig() {
        this(System.getProperty("user.dir") + File.separator + "config.properties");
    }

    /** 指定配置文件路径，供测试与多实例场景使用，不触碰真实 config.properties。 */
    public AppConfig(String configFilePath) {
        this.configFilePath = configFilePath;
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
    
    // ===== 类型化读取（ConfigKeys）=====
    // 非法值或越界一律回退默认值并记日志，避免一个错配置把检索/同步打挂。

    private String raw(ConfigKeys k) {
        String v = properties.getProperty(k.key);
        return v == null ? String.valueOf(k.def) : v;
    }

    public int getInt(ConfigKeys k) {
        String v = raw(k).trim();
        try {
            int parsed = Integer.parseInt(v);
            if (!k.inRange(parsed)) {
                logger.warn("配置项 {} 的值 {} 超出范围 [{}, {}]，使用默认值 {}", k.key, parsed, k.min, k.max, k.def);
                return (Integer) k.def;
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 '{}' 不是有效整数，使用默认值 {}", k.key, v, k.def);
            return (Integer) k.def;
        }
    }

    public long getLong(ConfigKeys k) {
        String v = raw(k).trim();
        try {
            long parsed = Long.parseLong(v);
            if (!k.inRange(parsed)) {
                logger.warn("配置项 {} 的值 {} 超出范围 [{}, {}]，使用默认值 {}", k.key, parsed, k.min, k.max, k.def);
                return ((Number) k.def).longValue();
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 '{}' 不是有效整数，使用默认值 {}", k.key, v, k.def);
            return ((Number) k.def).longValue();
        }
    }

    public double getDouble(ConfigKeys k) {
        String v = raw(k).trim();
        try {
            double parsed = Double.parseDouble(v);
            if (!k.inRange(parsed)) {
                logger.warn("配置项 {} 的值 {} 超出范围 [{}, {}]，使用默认值 {}", k.key, parsed, k.min, k.max, k.def);
                return ((Number) k.def).doubleValue();
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 的值 '{}' 不是有效浮点数，使用默认值 {}", k.key, v, k.def);
            return ((Number) k.def).doubleValue();
        }
    }

    public boolean getBoolean(ConfigKeys k) {
        String v = raw(k).trim();
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        logger.warn("配置项 {} 的值 '{}' 不是有效布尔值，使用默认值 {}", k.key, v, k.def);
        return (Boolean) k.def;
    }

    public String getString(ConfigKeys k) {
        return raw(k);
    }

    /** 供设置面板写入；合法性由读取侧兜底，面板自身也先用 ConfigKeys.inRange 校验。 */
    public void set(ConfigKeys k, String value) {
        if (value == null) {
            properties.remove(k.key);
        } else {
            properties.setProperty(k.key, value);
        }
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
        String stored = properties.getProperty(key);
        if (stored == null || stored.isEmpty()) {
            return "";
        }

        // 新格式：b64:xxxx
        if (stored.startsWith(B64_PREFIX)) {
            String payload = stored.substring(B64_PREFIX.length());
            try {
                byte[] decoded = Base64.getDecoder().decode(payload);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.warn("解码 b64: 密码失败，返回原始值: {}", e.getMessage());
                return stored;
            }
        }

        // 兼容旧格式：无前缀 Base64（只在“明显像 Base64”时才尝试解码，避免误判明文密码）
        if (looksLikeLegacyBase64(stored)) {
            try {
                byte[] decoded = Base64.getDecoder().decode(stored);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // 忽略，按明文返回
            }
        }

        // 明文格式（推荐手工配置/历史值）
        return stored;
    }
    
    /**
     * 设置加密的密码
     */
    public void setEncryptedPassword(String key, String password) {
        if (password == null || password.isEmpty()) {
            properties.remove(key);
        } else {
            // 新格式：b64: 前缀，避免与明文混淆
            String encoded = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
            properties.setProperty(key, B64_PREFIX + encoded);
        }
    }

    // ===== 私密笔记主密码 =====

    /** 是否已设置主密码。未设置时不允许把笔记加密，否则忘记密码就无法恢复。 */
    public boolean isMasterPasswordSet() {
        String salt = properties.getProperty(MASTER_PASSWORD_SALT);
        String verifier = properties.getProperty(MASTER_PASSWORD_VERIFIER);
        return salt != null && !salt.isEmpty() && verifier != null && !verifier.isEmpty();
    }

    /**
     * 设置主密码。只保存 PBKDF2 派生值与盐，无法从中还原出密码本身；
     * 这个值仅用于校验输入是否正确，真正的密钥是在需要时现场派生的。
     */
    public void setMasterPassword(char[] password) throws Exception {
        byte[] salt = com.gt.crypto.NoteCrypto.randomBytes(16);
        String verifier = com.gt.crypto.NoteCrypto.deriveVerifier(password, salt);
        properties.setProperty(MASTER_PASSWORD_SALT, com.gt.crypto.NoteCrypto.b64(salt));
        properties.setProperty(MASTER_PASSWORD_VERIFIER, verifier);
    }

    public boolean verifyMasterPassword(char[] password) {
        if (!isMasterPasswordSet()) {
            return false;
        }
        try {
            byte[] salt = com.gt.crypto.NoteCrypto.unb64(properties.getProperty(MASTER_PASSWORD_SALT));
            String actual = com.gt.crypto.NoteCrypto.deriveVerifier(password, salt);
            return com.gt.crypto.NoteCrypto.verifierMatches(
                    properties.getProperty(MASTER_PASSWORD_VERIFIER), actual);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 旧版本曾直接把 Base64 字符串写入配置文件（无前缀）。
     * 为避免把“看起来像 Base64 的明文密码”误解码，这里做一个更严格的判断：
     * - 长度必须是 4 的倍数
     * - 只包含 Base64 字符集 + '='
     * - 且包含 '+'/'/' 或 '='（大多数明文密码不会包含这些）
     */
    private boolean looksLikeLegacyBase64(String value) {
        if (value == null) {
            return false;
        }
        int len = value.length();
        if (len < 8 || (len % 4) != 0) {
            return false;
        }
        boolean hasSpecial = false;
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                continue;
            }
            if (c == '+' || c == '/' || c == '=') {
                hasSpecial = true;
                continue;
            }
            return false;
        }
        return hasSpecial;
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
}

