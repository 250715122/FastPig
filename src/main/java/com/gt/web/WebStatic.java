package com.gt.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * SPA 静态资源。开发期读磁盘 web-dist（改前端不用重打 jar），打包后读 jar 内 /web/。
 * 找不到文件一律回落 index.html，交给前端路由；/api 不走这里。
 */
public final class WebStatic {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("map", "application/json; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("webmanifest", "application/manifest+json"));

    private final Path diskRoot;

    public WebStatic(Path baseDir) {
        this(baseDir, null);
    }

    /** override 用于开发期把前端产物指到 web/dist，不必先拷进数据目录。 */
    public WebStatic(Path baseDir, Path override) {
        Path candidate = override != null ? override : baseDir.resolve("web-dist");
        this.diskRoot = Files.isDirectory(candidate) ? candidate.toAbsolutePath().normalize() : null;
    }

    public boolean fromDisk() {
        return diskRoot != null;
    }

    /** 处理一个非 API 请求。返回 false 表示连 index.html 都没有（前端还没构建）。 */
    public boolean serve(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        String relative = path.replaceAll("^/+", "");
        if (relative.contains("..")) {
            WebHttp.error(ex, 400, "非法路径");
            return true;
        }
        byte[] data = read(relative);
        boolean fallback = false;
        if (data == null) {
            data = read("index.html");
            fallback = true;
        }
        if (data == null) {
            WebHttp.text(ex, 503, "text/plain; charset=utf-8",
                    "网页端尚未构建：请在 web/ 下执行 npm install && npm run build，产物输出到 web-dist/。");
            return true;
        }
        String type = fallback ? TYPES.get("html") : typeOf(relative);
        String cache = fallback || relative.endsWith(".html") ? "no-store"
                : relative.startsWith("assets/") ? "public, max-age=31536000, immutable" : "public, max-age=3600";
        WebHttp.bytes(ex, 200, type, data, cache);
        return true;
    }

    private byte[] read(String relative) throws IOException {
        if (diskRoot != null) {
            Path target = diskRoot.resolve(relative).normalize();
            if (!target.startsWith(diskRoot) || !Files.isRegularFile(target)) {
                return null;
            }
            return Files.readAllBytes(target);
        }
        try (InputStream in = WebStatic.class.getResourceAsStream("/web/" + relative)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static String typeOf(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }
}