package com.gt;

import java.util.List;

public class NoteDto {
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
}


