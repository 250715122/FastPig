package com.gt.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfigKeys 自身的不变量。这些约束一旦被破坏，设置页会生成非法表单，
 * 或者默认值本身就被读取侧判为越界而永远回退，属于静默失效。
 */
public class ConfigKeysTest {

    @Test
    public void keysAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ConfigKeys k : ConfigKeys.values()) {
            assertTrue(seen.add(k.key), "重复的配置键: " + k.key);
        }
    }

    @Test
    public void byKeyResolvesEveryEntry() {
        for (ConfigKeys k : ConfigKeys.values()) {
            assertEquals(k, ConfigKeys.byKey(k.key), k.key);
        }
    }

    /** 默认值必须落在自己的取值范围内，否则 getInt 会判越界并回退到同一个越界值。 */
    @Test
    public void everyDefaultIsWithinItsOwnRange() {
        for (ConfigKeys k : ConfigKeys.values()) {
            if (!k.isNumeric()) {
                continue;
            }
            double def = ((Number) k.def).doubleValue();
            assertTrue(k.inRange(def),
                k.key + " 的默认值 " + k.def + " 不在 [" + k.min + ", " + k.max + "] 内");
        }
    }

    /** 数值项必须有上下界，ConfigFormPanel 据此生成带约束的 Spinner。 */
    @Test
    public void numericEntriesDeclareBounds() {
        for (ConfigKeys k : ConfigKeys.values()) {
            if (!k.isNumeric()) {
                continue;
            }
            assertNotNull(k.min, k.key + " 缺少下界");
            assertNotNull(k.max, k.key + " 缺少上界");
            assertTrue(k.min.doubleValue() <= k.max.doubleValue(), k.key + " 上下界颠倒");
        }
    }

    @Test
    public void everyEntryHasLabelAndCategory() {
        for (ConfigKeys k : ConfigKeys.values()) {
            assertNotNull(k.category, k.key);
            assertTrue(k.label != null && !k.label.isEmpty(), k.key + " 缺少显示名");
        }
    }

    /** 模型 token 上限受 bge-small-zh 位置编码限制，放开会让 ONNX 推理报错。 */
    @Test
    public void modelMaxTokensIsCappedAtModelLimit() {
        assertEquals(512, ((Number) ConfigKeys.MODEL_MAX_TOKENS.max).intValue());
        assertTrue(ConfigKeys.MODEL_MAX_TOKENS.needsReindex);
    }
}