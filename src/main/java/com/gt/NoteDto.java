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
}


