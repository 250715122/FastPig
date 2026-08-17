package com.gt.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 私密笔记的信封加密。
 *
 * 每篇私密笔记有一把随机 DEK 用于加密正文，DEK 本身被两把 KEK 各包裹一份：
 * 一份由笔记密码派生，一份由主密码派生。两把钥匙开同一份密文，因此
 * 忘记笔记密码时可以用主密码恢复，改密码时也只需重新包裹 DEK 而不必重写整篇密文。
 *
 * 全部使用 JDK 内置算法，不引入任何第三方加密库。
 */
public final class NoteCrypto {

    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KDF_ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;

    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int SALT_BYTES = 16;
    private static final int DEK_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private NoteCrypto() {}

    /** 密码错误（GCM 认证标签校验失败）时抛出，与其它 IO/配置错误区分开 */
    public static class WrongPasswordException extends Exception {
        public WrongPasswordException(String message) {
            super(message);
        }
    }

    /**
     * 被某把密码包裹后的 DEK，连同派生所需的盐与 GCM 的 IV。
     * 这三项都不是机密，可以明文写进 front matter。
     */
    public static final class WrappedDek {
        public final String saltB64;
        public final String ivB64;
        public final String cipherB64;

        public WrappedDek(String saltB64, String ivB64, String cipherB64) {
            this.saltB64 = saltB64;
            this.ivB64 = ivB64;
            this.cipherB64 = cipherB64;
        }

        public boolean isComplete() {
            return notBlank(saltB64) && notBlank(ivB64) && notBlank(cipherB64);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isEmpty();
        }
    }

    /** 加密后的正文：密文与其 IV */
    public static final class EncryptedBody {
        public final String ivB64;
        public final String cipherB64;

        public EncryptedBody(String ivB64, String cipherB64) {
            this.ivB64 = ivB64;
            this.cipherB64 = cipherB64;
        }
    }

    // ==================== 密钥 ====================

    /** 生成一把新的随机 DEK */
    public static byte[] newDek() {
        byte[] dek = new byte[DEK_BYTES];
        RANDOM.nextBytes(dek);
        return dek;
    }

    public static byte[] randomBytes(int length) {
        byte[] b = new byte[length];
        RANDOM.nextBytes(b);
        return b;
    }

    /**
     * 由密码和盐派生 KEK。
     * 密码以 char[] 传入，派生完成后 PBEKeySpec 会被清空，避免明文密码在堆上长期驻留。
     */
    private static SecretKey deriveKek(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(keyBytes, "AES");
            } finally {
                Arrays.fill(keyBytes, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    // ==================== DEK 包裹 ====================

    /** 用密码包裹 DEK。每次调用都生成新的盐与 IV。 */
    public static WrappedDek wrapDek(byte[] dek, char[] password) throws Exception {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(GCM_IV_BYTES);
        SecretKey kek = deriveKek(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] wrapped = cipher.doFinal(dek);

        return new WrappedDek(b64(salt), b64(iv), b64(wrapped));
    }

    /**
     * 用密码解开 DEK。
     *
     * @throws WrongPasswordException 密码错误。GCM 的认证标签校验失败即可判定，
     *                               不需要额外存放任何口令校验值。
     */
    public static byte[] unwrapDek(WrappedDek wrapped, char[] password) throws Exception {
        if (wrapped == null || !wrapped.isComplete()) {
            throw new IllegalArgumentException("包裹的密钥不完整");
        }
        SecretKey kek = deriveKek(password, unb64(wrapped.saltB64));

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, kek,
                    new GCMParameterSpec(GCM_TAG_BITS, unb64(wrapped.ivB64)));
            return cipher.doFinal(unb64(wrapped.cipherB64));
        } catch (javax.crypto.AEADBadTagException e) {
            throw new WrongPasswordException("密码错误");
        }
    }

    // ==================== 正文加解密 ====================

    public static EncryptedBody encryptBody(String plaintext, byte[] dek) throws Exception {
        byte[] iv = randomBytes(GCM_IV_BYTES);
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] out = cipher.doFinal((plaintext == null ? "" : plaintext).getBytes(StandardCharsets.UTF_8));
        return new EncryptedBody(b64(iv), b64(out));
    }

    public static String decryptBody(String cipherB64, String ivB64, byte[] dek) throws Exception {
        if (cipherB64 == null || cipherB64.isEmpty()) {
            return "";
        }
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, unb64(ivB64)));
        byte[] out = cipher.doFinal(unb64(cipherB64));
        return new String(out, StandardCharsets.UTF_8);
    }

    // ==================== 主密码校验值 ====================

    /**
     * 计算主密码的校验值，用于设置界面判断输入是否正确。
     * 只存这个派生结果，不存明文密码，也不用它当密钥。
     */
    public static String deriveVerifier(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            byte[] bytes = factory.generateSecret(spec).getEncoded();
            try {
                return b64(bytes);
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    /** 定长时间比较，避免通过比较耗时泄漏信息 */
    public static boolean verifierMatches(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    // ==================== 工具 ====================

    public static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    public static void wipe(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }

    public static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] unb64(String text) {
        return Base64.getDecoder().decode(text);
    }
}
