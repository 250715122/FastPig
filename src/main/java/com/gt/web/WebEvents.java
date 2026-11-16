package com.gt.web;

import com.sun.net.httpserver.HttpExchange;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 事件总线：保存、索引进度、同步状态、配置变更都从这里推给浏览器。
 *
 * 每个订阅者占一个虚拟线程并在处理线程内阻塞写，客户端断开即退出——
 * 不把交换器交给别的线程后立刻返回，避免容器提前回收连接。
 */
public final class WebEvents {

    private static final Logger logger = LogManager.getLogger(WebEvents.class);
    private static final long HEARTBEAT_SECONDS = 20;

    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicInteger published = new AtomicInteger();

    private static final class Subscriber {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>(512);
        volatile boolean closed;
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public int publishedCount() {
        return published.get();
    }

    /** 广播一帧。队列满说明客户端已经跟不上，直接断开让它重连，不阻塞业务线程。 */
    public void publish(String type, Object payload) {
        published.incrementAndGet();
        String frame = "event: " + type + "\ndata: " + WebHttp.GSON.toJson(payload) + "\n\n";
        for (Subscriber s : subscribers) {
            if (!s.queue.offer(frame)) {
                s.closed = true;
            }
        }
    }

    public void attach(HttpExchange ex, Map<String, Object> hello) throws IOException {
        Subscriber sub = new Subscriber();
        subscribers.add(sub);
        WebHttp.streamHeaders(ex, "text/event-stream; charset=utf-8");
        OutputStream out = ex.getResponseBody();
        try {
            write(out, "event: hello\ndata: " + WebHttp.GSON.toJson(hello) + "\n\n");
            while (!sub.closed) {
                String frame = sub.queue.poll(HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                write(out, frame == null ? ": hb\n\n" : frame);
            }
        } catch (IOException e) {
            // 浏览器关页/刷新即走这里，属正常断开
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            subscribers.remove(sub);
            try {
                out.close();
            } catch (IOException ignored) {
                // 已断开
            }
            ex.close();
        }
    }

    private static void write(OutputStream out, String frame) throws IOException {
        out.write(frame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public void close() {
        for (Subscriber s : subscribers) {
            s.closed = true;
        }
        subscribers.clear();
        logger.info("[Web] 事件总线已关闭");
    }
}