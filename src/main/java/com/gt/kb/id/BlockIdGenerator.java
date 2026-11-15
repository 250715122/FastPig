package com.gt.kb.id;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * 块 ID 生成：8 位小写 base32 随机，约 1.1 万亿空间。
 *
 * ID 不复用——块被删除后它的 ID 就此消失，否则旧引用会指向新内容。
 */
public final class BlockIdGenerator {

    private static final int MAX_ATTEMPTS = 32;

    private BlockIdGenerator() {
    }

    public static String next() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char[] buf = new char[BlockIdCodec.ID_LENGTH];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = BlockIdCodec.ALPHABET.charAt(random.nextInt(BlockIdCodec.ALPHABET.length()));
        }
        return BlockIdCodec.MODEL_PREFIX + new String(buf);
    }

    /** taken 判定 ID 是否已占用（本文件已有 ID + 全局块目录），碰撞则重试。 */
    public static String next(Predicate<String> taken) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String id = next();
            if (!taken.test(id)) {
                return id;
            }
        }
        throw new IllegalStateException("连续 " + MAX_ATTEMPTS + " 次生成块 ID 都碰撞");
    }
}