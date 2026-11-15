package com.gt.vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 全量重建的结束回调。未 initialize 时 isAvailable() 为 false，走同步早退分支，
 * 不需要 ONNX 模型或真实索引目录。
 */
public class VectorSearchManagerRebuildCallbackTest {

    @TempDir
    Path tempDir;

    @Test
    public void earlyReturnStillNotifiesCompletionExactlyOnce() {
        VectorSearchManager manager = VectorSearchManager.getInstance();
        assertFalse(manager.isAvailable(), "本用例依赖未初始化的早退分支");

        AtomicInteger done = new AtomicInteger();
        manager.rebuildAllIndex(tempDir.resolve("notes"), null, done::incrementAndGet);

        // 早退若不回调，调用方的状态标签会永久停在「正在创建向量索引」
        assertEquals(1, done.get());
    }
}