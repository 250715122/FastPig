package com.gt.web;

import com.gt.AppConfig;
import com.gt.config.ConfigKeys;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * 网页端入口：无 Swing，复用桌面端全部核心。
 *
 * 启动：java -cp FastPig.jar com.gt.web.WebMain --port=8787
 * 数据目录默认取 user.dir（与桌面端同一套 notes/ + fastpig.db + config.properties），
 * 用 --base 可以指到副本上做验证，不碰生产数据。
 */
public final class WebMain {

    private static final Logger logger = LogManager.getLogger(WebMain.class);
    private static final Set<String> PUBLIC_API = Set.of("/api/auth/login", "/api/auth/setup", "/api/auth/status");

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Options opts = Options.parse(args);
        if (opts.baseDir != null) {
            System.setProperty("user.dir", opts.baseDir.toAbsolutePath().toString());
        }
        Path baseDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        AppConfig config = AppConfig.getInstance();
        if (opts.port > 0) {
            config.setInt(ConfigKeys.WEB_PORT.key, opts.port);
        }
        if (opts.bind != null) {
            config.setString(ConfigKeys.WEB_BIND.key, opts.bind);
        }
        int port = config.getInt(ConfigKeys.WEB_PORT);
        String bind = config.getString(ConfigKeys.WEB_BIND);

        WebEvents events = new WebEvents();
        WebContext ctx = new WebContext(baseDir, events);
        if (opts.password != null) {
            ctx.auth().resetPassword(opts.password.toCharArray());
        }
        ctx.boot();

        WebRoutes routes = new WebRoutes(ctx);
        WebStatic statics = new WebStatic(baseDir, opts.webDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 64);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", ex -> handle(ex, ctx, routes, statics));
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("[Web] 正在关闭…");
            server.stop(1);
            events.close();
            ctx.close();
        }, "Web-Shutdown"));

        String host = "0.0.0.0".equals(bind) || "::".equals(bind) ? "localhost" : bind;
        logger.info("[Web] 已启动 http://{}:{}  数据目录={}  前端={}", host, port, baseDir,
                statics.fromDisk() ? "web-dist/（磁盘）" : "jar 内 /web/");
        System.out.println();
        System.out.println("  FastPig Web  →  http://" + host + ":" + port);
        System.out.println("  数据目录     →  " + baseDir);
        System.out.println("  前端资源     →  " + (statics.fromDisk() ? "web-dist/（磁盘热读）" : "jar 内 /web/"));
        if (ctx.auth().setupRequired()) {
            System.out.println("  首次使用     →  在本机浏览器打开上面地址设置登录口令（仅回环地址可设置）");
        }
        System.out.println();
    }

    private static void handle(HttpExchange ex, WebContext ctx, WebRoutes routes, WebStatic statics) {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.startsWith("/api/")) {
                if (!PUBLIC_API.contains(path) && !ctx.auth().isAuthenticated(ex)) {
                    WebHttp.error(ex, 401, "未登录或会话已过期");
                    return;
                }
                if (!routes.dispatch(ex)) {
                    WebHttp.error(ex, 404, "接口不存在: " + path);
                }
            } else {
                statics.serve(ex);
            }
        } catch (Exception e) {
            routes.handleFailure(ex, e);
        }
    }

    private static final class Options {
        Path baseDir;
        Path webDir;
        int port;
        String bind;
        String password;

        static Options parse(String[] args) {
            Options o = new Options();
            for (String arg : args) {
                if (arg.startsWith("--base=")) {
                    o.baseDir = Path.of(arg.substring(7));
                } else if (arg.startsWith("--web-dir=")) {
                    o.webDir = Path.of(arg.substring(10));
                } else if (arg.startsWith("--port=")) {
                    o.port = Integer.parseInt(arg.substring(7));
                } else if (arg.startsWith("--bind=")) {
                    o.bind = arg.substring(7);
                } else if (arg.startsWith("--web-password=")) {
                    o.password = arg.substring(15);
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    System.out.println("用法: java -cp FastPig.jar com.gt.web.WebMain [--base=目录] [--port=8787] "
                            + "[--bind=0.0.0.0] [--web-password=口令]");
                    System.exit(0);
                }
            }
            return o;
        }
    }

    private WebMain() {
    }
}