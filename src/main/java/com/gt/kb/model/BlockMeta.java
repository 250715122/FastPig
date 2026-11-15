package com.gt.kb.model;

import java.util.List;

/**
 * 块级治理元数据，阶段 3 启用，阶段 1 一律 {@link #EMPTY}。
 *
 * type 为空表示继承所属笔记的 noteType；status 缺省 draft。
 */
public record BlockMeta(
        String type,
        String status,
        String supersededBy,
        String scope,
        List<String> sources,
        Long updatedAt) {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_VERIFIED = "verified";
    public static final String STATUS_DEPRECATED = "deprecated";
    public static final String STATUS_ARCHIVED = "archived";

    public static final BlockMeta EMPTY = new BlockMeta(null, null, null, null, null, null);

    public BlockMeta {
        type = type == null ? "" : type;
        status = status == null || status.isBlank() ? STATUS_DRAFT : status;
        supersededBy = supersededBy == null ? "" : supersededBy;
        scope = scope == null ? "" : scope;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}