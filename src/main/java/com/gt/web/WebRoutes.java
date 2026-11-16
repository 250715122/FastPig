package com.gt.web;

import com.gt.AppConfig;
import com.gt.config.ConfigKeys;
import com.gt.web.WebDtos.ConfigRequest;
import com.gt.web.WebDtos.LoginRequest;
import com.gt.web.WebDtos.OkResponse;
import com.gt.web.WebDtos.SaveRequest;
import com.gt.web.WebDtos.WebException;
import com.sun.net.httpserver.HttpExchange;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接口注册与鉴权。除登录/首次设置外，全部接口都要会话 cookie。
 * 私密笔记的加解密不在这些接口里：它们只搬运密文与包裹字段。
 */
public final class WebRoutes {

    private static final Logger logger = LogManager.getLogger(WebRoutes.class);

    private final WebContext ctx;
    private final WebHttp.Router router = new WebHttp.Router();

    public WebRoutes(WebContext ctx) {
        this.ctx = ctx;
        register();
    }

    public boolean dispatch(HttpExchange ex) throws Exception {
        return router.dispatch(ex);
    }

    private void register() {
        // ===== 认证 =====
        router.get("/api/auth/status", (ex, p) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("authenticated", ctx.auth().isAuthenticated(ex));
            out.put("setupRequired", ctx.auth().setupRequired());
            out.put("loopback", ctx.auth().isLoopback(ex));
            out.put("masterPasswordSet", AppConfig.getInstance().isMasterPasswordSet());
            WebHttp.json(ex, 200, out);
        });

        router.post("/api/auth/setup", (ex, p) -> {
            LoginRequest req = WebHttp.bodyAs(ex, LoginRequest.class);
            ctx.auth().setupPassword(ex, WebAuth.charsOf(req.password));
            String token = ctx.auth().login(ex, WebAuth.charsOf(req.password));
            ex.getResponseHeaders().add("Set-Cookie",
                    ctx.auth().cookieHeader(token, ttlSeconds()));
            WebHttp.json(ex, 200, new OkResponse(true, "登录口令已设置"));
        });

        router.post("/api/auth/login", (ex, p) -> {
            LoginRequest req = WebHttp.bodyAs(ex, LoginRequest.class);
            String token = ctx.auth().login(ex, WebAuth.charsOf(req.password));
            ex.getResponseHeaders().add("Set-Cookie", ctx.auth().cookieHeader(token, ttlSeconds()));
            WebHttp.json(ex, 200, new OkResponse(true, "登录成功"));
        });

        router.post("/api/auth/logout", (ex, p) -> {
            ctx.auth().logout(WebHttp.cookies(ex).get(WebAuth.COOKIE));
            ex.getResponseHeaders().add("Set-Cookie", ctx.auth().expiredCookieHeader());
            WebHttp.json(ex, 200, new OkResponse(true, "已退出"));
        });

        // ===== 笔记 =====
        router.get("/api/notes", (ex, p) -> {
            boolean deleted = "true".equalsIgnoreCase(WebHttp.param(ex, "deleted", "false"));
            WebHttp.json(ex, 200, ctx.listNotes(deleted));
        });

        router.post("/api/notes", (ex, p) -> {
            SaveRequest req = WebHttp.bodyAs(ex, SaveRequest.class);
            WebHttp.json(ex, 201, ctx.createNote(req));
        });

        router.get("/api/notes/{key}", (ex, p) -> WebHttp.json(ex, 200, ctx.getNote(p.get("key"))));

        router.put("/api/notes/{key}", (ex, p) -> {
            SaveRequest req = WebHttp.bodyAs(ex, SaveRequest.class);
            WebHttp.json(ex, 200, ctx.saveNote(p.get("key"), req));
        });

        router.delete("/api/notes/{key}", (ex, p) -> WebHttp.json(ex, 200, ctx.deleteNote(p.get("key"))));

        router.post("/api/notes/{key}/restore", (ex, p) -> WebHttp.json(ex, 200, ctx.restoreNote(p.get("key"))));

        router.get("/api/notes/{key}/blocks", (ex, p) -> WebHttp.json(ex, 200, ctx.blocks(p.get("key"))));

        router.post("/api/notes/{key}/guess", (ex, p) -> WebHttp.json(ex, 200, ctx.guess(p.get("key"))));

        router.post("/api/notes/{key}/assets", (ex, p) -> {
            String name = WebHttp.param(ex, "name", "");
            byte[] data = WebHttp.bodyBytes(ex, WebHttp.MAX_ASSET);
            if (data.length == 0) {
                throw new WebException(400, "上传内容为空");
            }
            WebHttp.json(ex, 200, ctx.saveAsset(p.get("key"), name, data));
        });

        router.get("/api/notes/{key}/assets/{name}", (ex, p) -> {
            byte[] data = ctx.readAsset(p.get("key"), p.get("name"));
            String name = p.get("name").toLowerCase(java.util.Locale.ROOT);
            String type = name.endsWith(".png") ? "image/png"
                    : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                    : name.endsWith(".gif") ? "image/gif"
                    : name.endsWith(".webp") ? "image/webp"
                    : name.endsWith(".svg") ? "image/svg+xml" : "application/octet-stream";
            WebHttp.bytes(ex, 200, type, data, "private, max-age=86400");
        });

        // ===== 块 / 目录 / 分类 =====
        router.get("/api/blocks/{blockId}", (ex, p) -> WebHttp.json(ex, 200, ctx.block(p.get("blockId"))));
        router.get("/api/tree", (ex, p) -> WebHttp.json(ex, 200, ctx.tree()));
        router.get("/api/taxonomy", (ex, p) -> WebHttp.json(ex, 200, ctx.taxonomyDto()));

        // ===== 检索 =====
        router.get("/api/search", (ex, p) -> {
            Map<String, String> q = WebHttp.query(ex);
            WebHttp.json(ex, 200, ctx.search(q.get("q"), q.getOrDefault("tier", "human"), q));
        });
        router.get("/api/suggest", (ex, p) -> WebHttp.json(ex, 200, ctx.suggest(WebHttp.param(ex, "prefix", ""))));

        // ===== 配置 / 同步 / 索引 =====
        router.get("/api/config", (ex, p) -> WebHttp.json(ex, 200, ctx.config()));
        router.put("/api/config", (ex, p) -> {
            ConfigRequest req = WebHttp.bodyAs(ex, ConfigRequest.class);
            WebHttp.json(ex, 200, Map.of("ok", true, "needsReindex", ctx.updateConfig(req)));
        });
        router.post("/api/auth/password", (ex, p) -> {
            LoginRequest req = WebHttp.bodyAs(ex, LoginRequest.class);
            ctx.auth().resetPassword(WebAuth.charsOf(req.password));
            ex.getResponseHeaders().add("Set-Cookie", ctx.auth().expiredCookieHeader());
            WebHttp.json(ex, 200, new OkResponse(true, "登录口令已重置，请重新登录"));
        });

        router.get("/api/sync/status", (ex, p) -> WebHttp.json(ex, 200, ctx.syncStatus()));
        router.post("/api/sync/upload", (ex, p) -> WebHttp.json(ex, 200, ctx.syncUpload()));
        router.post("/api/sync/pull", (ex, p) -> WebHttp.json(ex, 200, ctx.syncPull()));

        router.get("/api/index/status", (ex, p) -> WebHttp.json(ex, 200, ctx.indexer().status()));
        router.post("/api/index/rebuild", (ex, p) -> {
            ctx.indexer().rebuild(ctx.noteService().getFileStorage().getNotesDir());
            WebHttp.json(ex, 202, new OkResponse(true, "已开始重建索引"));
        });

        router.get("/api/system", (ex, p) -> WebHttp.json(ex, 200, ctx.system()));
        router.get("/api/events", (ex, p) -> ctx.events().attach(ex, ctx.hello()));
    }

    private static long ttlSeconds() {
        return AppConfig.getInstance().getInt(ConfigKeys.WEB_SESSION_TTL) * 60L;
    }

    /** 统一异常出口：业务异常带状态码，其余按类型归并，堆栈只进日志不进响应。 */
    public void handleFailure(HttpExchange ex, Exception e) {
        try {
            if (e instanceof WebException we) {
                WebHttp.error(ex, we.status, we.getMessage());
            } else if (e instanceof IllegalArgumentException) {
                WebHttp.error(ex, 400, e.getMessage());
            } else if (e instanceof IllegalStateException) {
                WebHttp.error(ex, 409, e.getMessage());
            } else if (e instanceof IOException) {
                WebHttp.error(ex, 400, e.getMessage());
            } else {
                logger.error("[Web] 接口异常 {} {}: {}", ex.getRequestMethod(), ex.getRequestURI(), e.getMessage(), e);
                WebHttp.error(ex, 500, "服务器内部错误: " + e.getClass().getSimpleName());
            }
        } catch (IOException ignored) {
            // 客户端已断开
        }
    }
}