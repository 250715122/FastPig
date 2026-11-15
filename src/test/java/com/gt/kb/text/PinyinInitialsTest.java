package com.gt.kb.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PinyinInitialsTest {

    @Test
    public void chineseTitleYieldsInitialsAndFullPinyin() {
        assertEquals("sy|suoyin", PinyinInitials.of("索引"));
        assertEquals("mcx|manchaxun", PinyinInitials.of("慢查询"));
    }

    @Test
    public void englishTitleTakesWordInitials() {
        assertEquals("mt|mysqltuning", PinyinInitials.of("MySQL Tuning"));
        assertEquals("j|jvm", PinyinInitials.of("JVM"));
    }

    @Test
    public void mixedTitleKeepsBothScripts() {
        assertEquals("msy|mysqlsuoyin", PinyinInitials.of("MySQL索引"));
    }

    @Test
    public void digitsAreTreatedAsOneWord() {
        assertEquals("2nrz|2024nianrizhi", PinyinInitials.of("2024年日志"));
    }

    @Test
    public void punctuationAndSpacesAreWordBoundaries() {
        assertEquals("ab|ab", PinyinInitials.of("a_b"));
        assertEquals("sy|suoyin", PinyinInitials.of("  索引  "));
        assertEquals("sy|suoyin", PinyinInitials.of("索引！"));
    }

    @Test
    public void blankAndSymbolOnlyTitlesYieldEmpty() {
        assertEquals("", PinyinInitials.of(null));
        assertEquals("", PinyinInitials.of(""));
        assertEquals("", PinyinInitials.of("   "));
        assertEquals("", PinyinInitials.of("！！！——"));
    }

    @Test
    public void polyphoneStillProducesOneInitialPerChar() {
        String s = PinyinInitials.of("重庆");
        String[] parts = s.split("\\|");
        assertEquals(2, parts.length);
        assertEquals(2, parts[0].length());
        assertTrue(parts[1].endsWith("qing"), s);
    }

    @Test
    public void repeatedCallsAreStable() {
        assertEquals(PinyinInitials.of("索引"), PinyinInitials.of("索引"));
    }
}