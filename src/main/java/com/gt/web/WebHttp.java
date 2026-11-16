package com.gt.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 底座：JDK 自带 HttpServer 之上的路由与读写助手。
 *
 * 刻意不引入 Jetty/Javalin：桌面端用 maven-shade 打成一个 jar 再 jpackage，
 * 多一套 servlet 容器就多一份服务文件合并与打包风险，而这里只有单用户二十来个接口。
 */
public final class WebHttp {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** 正文/配置请求体上限 */
    public static final int MAX_BODY = 8 * 1024 * 1024;
    /** 图片上传上限 */
    public static final int MAX_ASSET = 20 * 1024 * 1024;

    private WebHttp() {
    }

    // ==================== 路由 ====================

    public interface Handler {
        void handle(HttpExchange ex, Map<String, String> params) throws Exception;
    }

    public static final class Router {
        private record Route(String method, String[] parts, Handler handler) {
        }

        private final List<Route> routes = new ArrayList<>();

        public Router add(String method, String path, Handler handler) {
            routes.add(new Route(method, split(path), handler));
            return this;
        }

        public Router get(String path, Handler h) {
            return add("GET", path, h);
        }

        public Router post(String path, Handler h) {
            return add("POST", path, h);
        }

        public Router put(String path, Handler h) {
            return add("PUT", path, h);
        }

        public Router delete(String path, Handler h) {
            return add("DELETE", path, h);
        }

        /** 命中返回 true；未命中由调用方决定是 404 还是交给静态资源。 */
        public boolean dispatch(HttpExchange ex) throws Exception {
            String method = ex.getRequestMethod();
            String[] parts = split(ex.getRequestURI().getPath());
            boolean pathExists = false;
            for (Route r : routes) {
                Map<String, String> params = match(r.parts(), parts);
                if (params == null) {
                    continue;
                }
                pathExists = true;
                if (!r.method().equals(method)) {
                    continue;
                }
                r.handler().handle(ex, params);
                return true;
            }
            if (pathExists) {
                error(ex, 405, "方法不允许");
                return true;
            }
            return false;
        }

        private static Map<String, String> match(String[] pattern, String[] actual) {
            if (pattern.length != actual.length) {
                return null;
            }
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < pattern.length; i++) {
                String p = pattern[i];
                if (p.startsWith("{") && p.endsWith("}")) {
                    params.put(p.substring(1, p.length() - 1), decode(actual[i]));
                } else if (!p.equals(actual[i])) {
                    return null;
                }
            }
            return params;
        }

        private static String[] split(String path) {
            String trimmed = path == null ? "" : path.replaceAll("^/+", "").replaceAll("/+$", "");
            if (trimmed.isEmpty()) {
                return new String[0];
            }
            return trimmed.split("/");
        }
    }

    // ==================== 读 ====================

    public static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(decode(k), decode(v));
        }
        return out;
    }

    public static String param(HttpExchange ex, String name, String fallback) {
        String v = query(ex).get(name);
        return v == null || v.isEmpty() ? fallback : v;
    }

    public static Map<String, String> cookies(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return out;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
            }
        }
        return out;
    }

    public static byte[] bodyBytes(HttpExchange ex, int limit) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = in.readNBytes(limit + 1);
            if (buf.length > limit) {
                throw new IOException("请求体超过上限 " + limit + " 字节");
            }
            return buf;
        }
    }

    public static String body(HttpExchange ex) throws IOException {
        return new String(bodyBytes(ex, MAX_BODY), StandardCharsets.UTF_8);
    }

    public static <T> T bodyAs(HttpExchange ex, Class<T> type) throws IOException {
        String text = body(ex);
        if (text.isBlank()) {
            throw new IOException("请求体为空");
        }
        try {
            T parsed = GSON.fromJson(text, type);
            if (parsed == null) {
                throw new IOException("请求体不是合法 JSON");
            }
            return parsed;
        } catch (JsonSyntaxException e) {
            throw new IOException("请求体不是合法 JSON: " + e.getMessage());
        }
    }

    public static String clientIp(HttpExchange ex) {
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    public static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    // ==================== 写 ====================

    public static void json(HttpExchange ex, int status, Object payload) throws IOException {
        bytes(ex, status, "application/json; charset=utf-8",
                GSON.toJson(payload).getBytes(StandardCharsets.UTF_8), "no-store");
    }

    public static void error(HttpExchange ex, int status, String message) throws IOException {
        json(ex, status, Map.of("error", message == null ? "请求失败" : message));
    }

    public static void text(HttpExchange ex, int status, String contentType, String body) throws IOException {
        bytes(ex, status, contentType, body.getBytes(StandardCharsets.UTF_8), "no-store");
    }

    public static void bytes(HttpExchange ex, int status, String contentType, byte[] data, String cache)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        if (cache != null) {
            ex.getResponseHeaders().set("Cache-Control", cache);
        }
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
        if (data.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(data);
            }
        } else {
            ex.close();
        }
    }

    public static void empty(HttpExchange ex, int status) throws IOException {
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    /** 流式响应头（SSE 用）：长度 0 表示分块传输，连接保持打开。 */
    public static void streamHeaders(HttpExchange ex, String contentType) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.getResponseHeaders().set("X-Accel-Buffering", "no");
        ex.sendResponseHeaders(200, 0);
    }
}