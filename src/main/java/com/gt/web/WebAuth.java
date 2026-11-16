package com.gt.web;

import com.gt.AppConfig;
import com.gt.config.ConfigKeys;
import com.gt.crypto.NoteCrypto;
import com.sun.net.httpserver.HttpExchange;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单用户登录：口令只存 PBKDF2 校验值，会话走 HttpOnly + SameSite=Strict cookie。
 *
 * 首次设置口令只接受回环地址，避免局域网里别人抢先把口令设成他自己的。
 * 这个口令与坚果云口令、笔记主密码相互独立。
 */
public final class WebAuth {

    private static final Logger logger = LogManager.getLogger(WebAuth.class);
    public static final String COOKIE = "fp_session";

    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 5 * 60_000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private record Session(String token, long expiresAt, String ip) {
    }

    private record Failure(int count, long firstAt, long lockedUntil) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();

    public boolean setupRequired() {
        return configuredHash() == null;
    }

    public boolean isLoopback(HttpExchange ex) {
        return ex.getRemoteAddress().getAddress().isLoopbackAddress();
    }

    /** 首次设置口令。仅回环地址且尚未设置时可用。 */
    public synchronized void setupPassword(HttpExchange ex, char[] password) throws Exception {
        if (!setupRequired()) {
            throw new IllegalStateException("登录口令已设置，如需修改请在设置页重置");
        }
        if (!isLoopback(ex)) {
            throw new IllegalStateException("首次设置登录口令只能在本机浏览器完成");
        }
        if (password == null || password.length < 6) {
            throw new IllegalArgumentException("登录口令至少 6 位");
        }
        storeHash(password);
        logger.info("[Web] 已设置网页端登录口令");
    }

    public synchronized void resetPassword(char[] password) throws Exception {
        if (password == null || password.length < 6) {
            throw new IllegalArgumentException("登录口令至少 6 位");
        }
        storeHash(password);
        sessions.clear();
        logger.info("[Web] 已重置网页端登录口令，全部会话失效");
    }

    private void storeHash(char[] password) throws Exception {
        byte[] salt = NoteCrypto.randomBytes(SALT_BYTES);
        byte[] hash = derive(password, salt);
        String value = "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
        AppConfig.getInstance().setString(AppConfig.WEB_AUTH_PASSWORD_HASH, value);
        AppConfig.getInstance().save();
    }

    /** 登录成功返回会话 token；失败抛异常，连续失败触发限速。 */
    public synchronized String login(HttpExchange ex, char[] password) throws Exception {
        String ip = WebHttp.clientIp(ex);
        Failure f = failures.get(ip);
        long now = System.currentTimeMillis();
        if (f != null && f.lockedUntil() > now) {
            throw new IllegalStateException("失败次数过多，请 " + ((f.lockedUntil() - now) / 1000 + 1) + " 秒后再试");
        }
        String stored = configuredHash();
        if (stored == null) {
            throw new IllegalStateException("尚未设置登录口令");
        }
        if (!matches(stored, password)) {
            int count = (f == null || now - f.firstAt() > LOCK_MILLIS) ? 1 : f.count() + 1;
            long firstAt = (f == null || now - f.firstAt() > LOCK_MILLIS) ? now : f.firstAt();
            long lockedUntil = count >= MAX_FAILURES ? now + LOCK_MILLIS : 0;
            failures.put(ip, new Failure(count, firstAt, lockedUntil));
            logger.warn("[Web] 登录失败 ip={} 第 {} 次", ip, count);
            throw new IllegalArgumentException(lockedUntil > 0
                    ? "失败次数过多，已锁定 5 分钟"
                    : "口令错误（剩余 " + (MAX_FAILURES - count) + " 次尝试）");
        }
        failures.remove(ip);
        String token = newToken();
        long ttl = AppConfig.getInstance().getInt(ConfigKeys.WEB_SESSION_TTL) * 60_000L;
        sessions.put(token, new Session(token, now + ttl, ip));
        return token;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public boolean isAuthenticated(HttpExchange ex) {
        String token = WebHttp.cookies(ex).get(COOKIE);
        if (token == null || token.isEmpty()) {
            return false;
        }
        Session s = sessions.get(token);
        if (s == null) {
            return false;
        }
        if (s.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public String cookieHeader(String token, long maxAgeSeconds) {
        return COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAgeSeconds;
    }

    public String expiredCookieHeader() {
        return COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0";
    }

    public int activeSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        return sessions.size();
    }

    // ==================== 内部 ====================

    private static String configuredHash() {
        String value = AppConfig.getInstance().getString(AppConfig.WEB_AUTH_PASSWORD_HASH);
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean matches(String stored, char[] password) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            logger.error("[Web] 口令校验值解析失败: {}", e.getMessage());
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt) throws Exception {
        return derive(password, salt, ITERATIONS);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 请求体里的口令字段，用完即清。 */
    public static char[] charsOf(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    public static byte[] utf8(char[] chars) {
        return new String(chars).getBytes(StandardCharsets.UTF_8);
    }
}