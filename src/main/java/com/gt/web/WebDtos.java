package com.gt.web;

import com.gt.kb.id.BlockIdAssigner;

import java.util.List;
import java.util.Map;

/**
 * 网页端接口的请求与响应形状。
 *
 * 响应刻意不直接抛 NoteDto：那个类带着 runtimeDek、frontMatter 原文等内部字段，
 * 接口只暴露浏览器真正需要的部分，私密笔记的明文永远不出现在响应里。
 */
public final class WebDtos {

    private WebDtos() {
    }

    // ==================== 请求 ====================

    public static final class LoginRequest {
        public String password;
    }

    public static final class SaveRequest {
        public String key;
        public String desc;
        public String title;
        public String bodyMd;
        public String domain;
        public String topic;
        public String noteType;
        public String layer;
        public String visibility;
        public List<String> aliases;
        public List<String> tags;
        public Integer expectedVersion;

        // 私密笔记：浏览器已完成 E2E 加密，服务端只负责落盘这些字段
        public Boolean encrypted;
        public String cipherIv;
        public String pwdSalt;
        public String pwdIv;
        public String pwdWrappedDek;
        public String masterSalt;
        public String masterIv;
        public String masterWrappedDek;
        public List<String> assets;
        /** 明文的 MD5，与桌面 NoteService.computeHash 同口径，供同步判断变更 */
        public String plaintextHash;
    }

    public static final class ConfigRequest {
        public Map<String, String> changes;
        public String masterSalt;
        public String masterVerifier;
    }

    public static final class MasterVerifyRequest {
        public String verifier;
    }

    // ==================== 响应 ====================

    public record NoteMeta(
            String id, String key, String title, String desc,
            String domain, String topic, String noteType, String layer, String visibility,
            List<String> aliases, List<String> tags,
            boolean encrypted, boolean locked, boolean deleted,
            long createdAt, long updatedAt, int version, int blockCount,
            String contentHash, String folder) {
    }

    public record CryptoPayload(
            String cipherIv,
            String pwdSalt, String pwdIv, String pwdWrappedDek,
            String masterSalt, String masterIv, String masterWrappedDek) {
    }

    /** 私密笔记 bodyMd 为 null，密文在 cipherBody；解锁与加解密全在浏览器完成。 */
    public record NoteDetail(NoteMeta meta, String bodyMd, String cipherBody,
                             CryptoPayload crypto, List<String> assets) {
    }

    public record SaveOutcome(NoteDetail note, BlockIdAssigner.Result ids, boolean created) {
    }

    public record BlockDetail(String blockId, String noteKey, String title, int level,
                              List<String> path, String body, int startLine, int endLine,
                              String status, String file, int fileStartLine, int fileEndLine) {
    }

    public record Suggestion(String key, String desc, String domain, String topic,
                             boolean encrypted, int blockCount, String match) {
    }

    public record SearchHit(
            String docId, String noteKey, String noteDesc, String blockId,
            String h1Title, String sectionTitle, String path, int startLine,
            String content, String status, long updatedAt, List<String> channels, float score) {
    }

    public record SearchResponse(
            String query, String tier, List<SearchHit> hits, int total, long tookMs,
            boolean available, String message, Map<String, Double> weights,
            int topK, int candidates, int perNoteQuota, boolean fullBody) {
    }

    public record TaxonomyTopic(String name, List<String> aliases) {
    }

    public record TaxonomyDomain(String name, List<TaxonomyTopic> topics) {
    }

    public record TaxonomyResponse(int version, List<TaxonomyDomain> domains,
                                   List<String> noteTypes, List<String> layers, List<String> visibilities) {
    }

    public record ConfigItem(String key, String label, String category, String categoryLabel,
                             String value, String def, String type, Number min, Number max,
                             boolean needsReindex) {
    }

    public record ConfigResponse(List<ConfigItem> items, List<String> categories,
                                 boolean masterPasswordSet, String masterSalt, String masterVerifier,
                                 Map<String, Object> web, Map<String, Object> index) {
    }

    public record SyncStatus(boolean enabled, boolean configured, String provider, String remotePath,
                             int autoUploadMinutes, boolean syncOnStart, boolean syncOnExit,
                             String message) {
    }

    public record SyncOutcome(boolean ok, String message, long tookMs) {
    }

    public record GuessResult(String domain, String topic, String source) {
    }

    public record AssetOutcome(String name, String url, long size) {
    }

    public record DeleteOutcome(String key, long undoUntil) {
    }

    public record OkResponse(boolean ok, String message) {
    }

    /** 带 HTTP 状态码的业务异常，路由层统一转成 {"error": …}。 */
    public static final class WebException extends RuntimeException {
        public final int status;

        public WebException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}