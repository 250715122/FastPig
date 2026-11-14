package com.gt.config;

/**
 * 全部可调配置项的集中定义：键名、默认值、取值范围、所属设置分类、是否需重建索引。
 *
 * 业务类不再各自写 private static final 常量，一律经此枚举 + AppConfig 读取，
 * 这样设置页可以按分类自动生成表单，新增一项无需手写 UI。
 */
public enum ConfigKeys {

    // ===== 云同步 =====
    CLOUD_PROVIDER("cloud.provider", "nutstore", null, null, Category.CLOUD, false, "云服务商"),
    NUTSTORE_WEBDAV_BASE("nutstore.webdav.base", "https://dav.jianguoyun.com/dav/", null, null, Category.CLOUD, false, "WebDAV 地址"),
    NUTSTORE_SYNC_PATH("nutstore.sync.path", "FastPig/notes", null, null, Category.CLOUD, false, "云端同步路径"),
    BEHAVIOR_SYNC_ON_START("behavior.sync.on.start", true, null, null, Category.CLOUD, false, "启动时同步"),
    BEHAVIOR_SYNC_ON_EXIT("behavior.sync.on.exit", true, null, null, Category.CLOUD, false, "退出时同步"),
    SYNC_AUTO_UPLOAD_INTERVAL("sync.auto.upload.interval", 30, 0, 1440, Category.CLOUD, false, "自动上传间隔（分钟，0=禁用）"),
    SYNC_REQUEST_INTERVAL_MS("sync.request.interval.ms", 1000L, 0L, 60000L, Category.CLOUD, false, "请求间隔（毫秒）"),
    SYNC_RATELIMIT_WAIT_MS("sync.ratelimit.wait.ms", 30000L, 1000L, 300000L, Category.CLOUD, false, "限流后等待（毫秒）"),
    SYNC_COOLDOWN_MS("sync.cooldown.ms", 3000L, 0L, 60000L, Category.CLOUD, false, "手动同步冷却（毫秒）"),

    // ===== 编辑器 =====
    EDITOR_FONT_NAME("editor.font.name", "Consolas", null, null, Category.EDITOR, false, "字体"),
    EDITOR_FONT_SIZE("editor.font.size", 14, 8, 48, Category.EDITOR, false, "字号"),
    EDITOR_LINE_HEIGHT("editor.line.height", 1.6, 1.0, 3.0, Category.EDITOR, false, "行高倍数"),
    EDITOR_TAB_SIZE("editor.tab.size", 4, 1, 16, Category.EDITOR, false, "Tab 宽度"),
    EDITOR_AUTOSAVE_INTERVAL("editor.autosave.interval", 10, 1, 3600, Category.EDITOR, false, "自动保存间隔（秒）"),
    PREVIEW_SECTION_ON_KB("preview.section.on.kb", 50, 10, 1024, Category.EDITOR, false, "预览分节阈值（KB）"),
    PREVIEW_SECTION_OFF_KB("preview.section.off.kb", 45, 5, 1024, Category.EDITOR, false, "预览回落阈值（KB）"),

    // ===== 界面 =====
    UI_THEME("ui.theme", "light", null, null, Category.UI, false, "主题"),
    UI_WINDOW_WIDTH("ui.window.width", 1100, 400, 7680, Category.UI, false, "窗口宽度"),
    UI_WINDOW_HEIGHT("ui.window.height", 720, 300, 4320, Category.UI, false, "窗口高度"),
    UI_START_MAXIMIZED("ui.start.maximized", false, null, null, Category.UI, false, "启动即最大化"),
    UI_TOAST_DURATION_MS("ui.toast.duration.ms", 2800, 500, 20000, Category.UI, false, "提示停留时长（毫秒）"),

    // ===== 检索 =====
    SEARCH_HUMAN_TOPK("search.human.topk", 20, 1, 200, Category.SEARCH, false, "联想结果条数"),
    SEARCH_HUMAN_PER_NOTE("search.human.per.note", 3, 1, 50, Category.SEARCH, false, "每篇笔记结果配额"),
    SEARCH_OVERFETCH("search.overfetch", 4, 1, 20, Category.SEARCH, false, "候选超取倍数"),
    SEARCH_MIN_QUERY_CHARS("search.min.query.chars", 2, 1, 10, Category.SEARCH, false, "触发检索最少字数"),
    SEARCH_DEBOUNCE_MS("search.debounce.ms", 300, 0, 5000, Category.SEARCH, false, "输入防抖（毫秒）"),

    // ===== 索引 =====
    INDEX_CONTENT_MAX_CHARS("index.content.max.chars", 300, 50, 4000, Category.INDEX, true, "块索引正文最大字数"),
    INDEX_SNIPPET_MAX_CHARS("index.snippet.max.chars", 200, 50, 2000, Category.INDEX, true, "结果摘要最大字数"),
    INDEX_SAVE_DELAY_MS("index.save.delay.ms", 2000L, 0L, 60000L, Category.INDEX, false, "保存后建索引延迟（毫秒）"),
    MODEL_FILE("model.file", "bge-small-zh-v1.5.onnx", null, null, Category.INDEX, true, "模型文件名"),
    MODEL_URL("model.url", "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model.onnx", null, null, Category.INDEX, false, "模型下载地址"),
    MODEL_VOCAB_URL("model.vocab.url", "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/vocab.txt", null, null, Category.INDEX, false, "词表下载地址"),
    // bge-small-zh-v1.5 的位置编码上限是 512，超出会让 ONNX 推理直接报错，故不允许调大
    MODEL_MAX_TOKENS("model.max.tokens", 512, 32, 512, Category.INDEX, true, "模型最大 token 数"),

    // ===== 快捷键 =====
    HOTKEY_SHOW("hotkey.show", "Alt+N", null, null, Category.HOTKEY, false, "显示/恢复窗口"),
    HOTKEY_MAX("hotkey.max", "Alt+M", null, null, Category.HOTKEY, false, "最大化窗口"),
    HOTKEY_MIN("hotkey.min", "Alt+L", null, null, Category.HOTKEY, false, "最小化窗口"),
    HOTKEY_EXIT("hotkey.exit", "Alt+Q", null, null, Category.HOTKEY, false, "退出程序"),
    HOTKEY_SYNC("hotkey.sync", "Alt+S", null, null, Category.HOTKEY, false, "上传到云端"),
    HOTKEY_PULL("hotkey.pull", "Alt+U", null, null, Category.HOTKEY, false, "从云端下载"),

    // ===== 知识库 =====
    KB_GUESS_MIN_SCORE("kb.guess.min.score", 0.55, 0.0, 1.0, Category.KB, false, "分类猜测最低相似度"),
    ;

    /** 设置页分类。ABOUT 无配置项，仅用于导航。 */
    public enum Category {
        CLOUD("云同步"),
        EDITOR("编辑器"),
        UI("界面"),
        SECURITY("安全"),
        HOTKEY("快捷键"),
        SEARCH("检索"),
        INDEX("索引"),
        KB("知识库"),
        ABOUT("关于");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public final String key;
    public final Object def;
    public final Number min;
    public final Number max;
    public final Category category;
    public final boolean needsReindex;
    public final String label;

    ConfigKeys(String key, Object def, Number min, Number max, Category category,
               boolean needsReindex, String label) {
        this.key = key;
        this.def = def;
        this.min = min;
        this.max = max;
        this.category = category;
        this.needsReindex = needsReindex;
        this.label = label;
    }

    public boolean isNumeric() {
        return def instanceof Number;
    }

    public boolean isBoolean() {
        return def instanceof Boolean;
    }

    /** 数值越界时回退默认值，避免非法配置把检索或同步打挂。 */
    public boolean inRange(double value) {
        if (min != null && value < min.doubleValue()) {
            return false;
        }
        return max == null || !(value > max.doubleValue());
    }

    public static ConfigKeys byKey(String key) {
        for (ConfigKeys k : values()) {
            if (k.key.equals(key)) {
                return k;
            }
        }
        return null;
    }
}