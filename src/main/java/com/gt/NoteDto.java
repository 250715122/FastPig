package com.gt;

import java.util.ArrayList;
import java.util.List;

public class NoteDto {
    // ===== 知识库元数据默认值 =====
    // 这些是内容 vocabulary，不是可调参数，故不进 ConfigKeys。
    public static final String DEFAULT_DOMAIN = "待分类";
    public static final String DEFAULT_TOPIC = "待分类";
    public static final String DEFAULT_NOTE_TYPE = "知识";
    public static final String DEFAULT_LAYER = "curated";
    public static final String DEFAULT_VISIBILITY = "human";

    public static final String LAYER_CURATED = "curated";
    public static final String LAYER_RAW = "raw";
    public static final String VISIBILITY_AI = "ai";
    public static final String VISIBILITY_HUMAN = "human";
    public static final String VISIBILITY_PRIVATE = "private";

    public static final String DOMAIN_PERSONAL = "个人";
    public static final String TYPE_ESSAY = "随笔";

    public String id;
    public String key;           // 可选，snippet 场景唯一
    public String title;
    public String desc;
    public List<String> tags;
    public String bodyMd;
    public String frontMatter;   // YAML 原文，可为空
    public long createdAt;
    public long updatedAt;
    public int version;
    public boolean deleted;      // 软删除标记
    public String folderPath;    // 笔记文件夹路径（新增）
    public String contentHash;   // 内容哈希，用于检测变更（新增）

    // ===== 知识库分类与治理 =====
    public String domain = DEFAULT_DOMAIN;        // 一级分类
    public String topic = DEFAULT_TOPIC;          // 二级分类
    public String noteType = DEFAULT_NOTE_TYPE;   // 知识/速查/约定/日志/随笔
    public String layer = DEFAULT_LAYER;          // curated 提炼层 / raw 原始层
    public String visibility = DEFAULT_VISIBILITY;// ai / human / private
    public List<String> aliases = new ArrayList<>(); // key 改名后保留旧名

    // ===== 私密笔记（AES-GCM 信封加密）=====
    // 以下字段全部明文存放于 front matter：它们不是机密，且云同步的版本/删除判定
    // 直接对 note.md 原文做字符串查找，front matter 必须保持明文可读。
    public boolean encrypted;            // 是否为私密笔记
    public String cipherIv;              // 正文密文的 GCM IV（Base64）
    public String pwdSalt;               // 笔记密码派生 KEK 的盐
    public String pwdIv;                 // 笔记密码包裹 DEK 的 IV
    public String pwdWrappedDek;         // 被笔记密码包裹的 DEK
    public String masterSalt;            // 主密码派生 KEK 的盐
    public String masterIv;              // 主密码包裹 DEK 的 IV
    public String masterWrappedDek;      // 被主密码包裹的 DEK
    public List<String> assets;          // 正文引用的图片清单。正文加密后无法用正则扫出
                                         // 引用关系，必须靠这份明文清单避免图片被误删。

    // 运行时状态，既不落盘也不入库
    public transient byte[] runtimeDek;  // 解锁后缓存的明文 DEK
    public transient boolean locked;     // 当前是否处于锁定态（未解锁）

    /**
     * 加密笔记一律 private。Lucene 里的 h1Title 是明文存储的，
     * 若允许 ai 可见，等于绕过加密把内容喂给模型。
     * 个人类与随笔同理不允许开放，规则放在这里而不是只放 UI，手工改 front matter 也绕不过。
     */
    public String effectiveVisibility() {
        if (encrypted) {
            return VISIBILITY_PRIVATE;
        }
        if (VISIBILITY_AI.equals(visibility) && !aiAllowed()) {
            return VISIBILITY_HUMAN;
        }
        return visibility;
    }

    public boolean aiAllowed() {
        return !DOMAIN_PERSONAL.equals(domain) && !TYPE_ESSAY.equals(noteType);
    }

    /** front matter 可被手工编辑，非法枚举值回退默认，避免脏值一路传到检索与 AI 侧。 */
    public static String normalizeVisibility(String value) {
        if (VISIBILITY_AI.equals(value) || VISIBILITY_HUMAN.equals(value) || VISIBILITY_PRIVATE.equals(value)) {
            return value;
        }
        return DEFAULT_VISIBILITY;
    }

    public static String normalizeLayer(String value) {
        return LAYER_RAW.equals(value) ? LAYER_RAW : LAYER_CURATED;
    }

    public static String normalizeNoteType(String value) {
        if (value == null) {
            return DEFAULT_NOTE_TYPE;
        }
        return switch (value) {
            case "知识", "速查", "约定", "日志", "随笔" -> value;
            default -> DEFAULT_NOTE_TYPE;
        };
    }

    /** 空值回退默认，用于旧文件/旧库缺字段的场景。 */
    public static String orDefault(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }
}


