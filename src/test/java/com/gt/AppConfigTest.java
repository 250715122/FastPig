package com.gt;

import com.gt.config.ConfigKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置中心验收：默认值、非法值回退、越界回退、保存后重读。
 * 全部走 @TempDir 下的独立配置文件，不触碰真实 config.properties。
 */
public class AppConfigTest {

    @TempDir
    Path tempDir;

    private AppConfig newConfig() {
        return new AppConfig(tempDir.resolve("config.properties").toString());
    }

    @Test
    public void missingFileYieldsEveryDefault() {
        AppConfig config = newConfig();
        for (ConfigKeys k : ConfigKeys.values()) {
            if (k.isBoolean()) {
                assertEquals(k.def, config.getBoolean(k), k.key);
            } else if (k.def instanceof Long) {
                assertEquals(((Number) k.def).longValue(), config.getLong(k), k.key);
            } else if (k.def instanceof Integer) {
                assertEquals(((Number) k.def).intValue(), config.getInt(k), k.key);
            } else if (k.def instanceof Double) {
                assertEquals(((Number) k.def).doubleValue(), config.getDouble(k), 1e-9, k.key);
            } else {
                assertEquals(k.def, config.getString(k), k.key);
            }
        }
    }

    @Test
    public void nonNumericIntFallsBackToDefault() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.SEARCH_HUMAN_TOPK, "abc");
        assertEquals(20, config.getInt(ConfigKeys.SEARCH_HUMAN_TOPK));
    }

    @Test
    public void outOfRangeIntFallsBackToDefault() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.SEARCH_HUMAN_TOPK, "9999");
        assertEquals(20, config.getInt(ConfigKeys.SEARCH_HUMAN_TOPK));

        config.set(ConfigKeys.SEARCH_HUMAN_TOPK, "0");
        assertEquals(20, config.getInt(ConfigKeys.SEARCH_HUMAN_TOPK));
    }

    @Test
    public void inRangeValueIsHonoured() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.SEARCH_HUMAN_TOPK, "50");
        assertEquals(50, config.getInt(ConfigKeys.SEARCH_HUMAN_TOPK));
    }

    @Test
    public void outOfRangeLongFallsBackToDefault() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.INDEX_SAVE_DELAY_MS, "-1");
        assertEquals(2000L, config.getLong(ConfigKeys.INDEX_SAVE_DELAY_MS));
    }

    @Test
    public void nonNumericDoubleFallsBackToDefault() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.EDITOR_LINE_HEIGHT, "tall");
        assertEquals(1.6, config.getDouble(ConfigKeys.EDITOR_LINE_HEIGHT), 1e-9);
    }

    @Test
    public void outOfRangeDoubleFallsBackToDefault() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.EDITOR_LINE_HEIGHT, "99");
        assertEquals(1.6, config.getDouble(ConfigKeys.EDITOR_LINE_HEIGHT), 1e-9);
    }

    @Test
    public void nonBooleanFallsBackToDefault() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.UI_START_MAXIMIZED, "yes");
        assertFalse(config.getBoolean(ConfigKeys.UI_START_MAXIMIZED));

        config.set(ConfigKeys.BEHAVIOR_SYNC_ON_START, "1");
        assertTrue(config.getBoolean(ConfigKeys.BEHAVIOR_SYNC_ON_START));
    }

    @Test
    public void booleanParsingIsCaseInsensitive() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.UI_START_MAXIMIZED, "TRUE");
        assertTrue(config.getBoolean(ConfigKeys.UI_START_MAXIMIZED));
    }

    @Test
    public void saveThenReloadKeepsOverrides() {
        String path = tempDir.resolve("config.properties").toString();
        AppConfig config = new AppConfig(path);
        config.set(ConfigKeys.SEARCH_HUMAN_TOPK, "42");
        config.set(ConfigKeys.UI_START_MAXIMIZED, "true");
        config.save();

        AppConfig reloaded = new AppConfig(path);
        assertEquals(42, reloaded.getInt(ConfigKeys.SEARCH_HUMAN_TOPK));
        assertTrue(reloaded.getBoolean(ConfigKeys.UI_START_MAXIMIZED));
        // 未覆盖的项仍取默认值
        assertEquals(300, reloaded.getInt(ConfigKeys.SEARCH_DEBOUNCE_MS));
    }

    @Test
    public void setNullRemovesOverrideAndRestoresDefault() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.SEARCH_HUMAN_TOPK, "42");
        assertEquals(42, config.getInt(ConfigKeys.SEARCH_HUMAN_TOPK));

        config.set(ConfigKeys.SEARCH_HUMAN_TOPK, null);
        assertEquals(20, config.getInt(ConfigKeys.SEARCH_HUMAN_TOPK));
    }

    @Test
    public void modelMaxTokensCannotExceedModelLimit() {
        AppConfig config = newConfig();
        config.set(ConfigKeys.MODEL_MAX_TOKENS, "1024");
        assertEquals(512, config.getInt(ConfigKeys.MODEL_MAX_TOKENS));
    }
}