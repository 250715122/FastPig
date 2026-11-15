package com.gt.kb.text;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 标题转拼音检索串，形如 {@code "sy|suoyin"}：前段是首字母，后段是全拼。
 * 两段都存是为了让 "sy" 和 "suoyin" 都能前缀直达同一个块。
 *
 * 英文按单词处理：首字母段取每个单词的首字母，全拼段取单词本身小写拼接，
 * 于是 "MySQL Tuning" → {@code "mt|mysqltuning"}。
 */
public final class PinyinInitials {

    /** 首字母段与全拼段的分隔符，前缀匹配时两段都要试。 */
    public static final char SEPARATOR = '|';

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();
    private static final Map<Character, String> CACHE = new ConcurrentHashMap<>();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinInitials() {
    }

    public static String of(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        StringBuilder initials = new StringBuilder();
        StringBuilder full = new StringBuilder();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            String pinyin = pinyinOf(c);
            if (pinyin != null && !pinyin.isEmpty()) {
                flushWord(word, initials, full);
                initials.append(pinyin.charAt(0));
                full.append(pinyin);
            } else if (Character.isLetterOrDigit(c) && c < 128) {
                word.append(Character.toLowerCase(c));
            } else {
                flushWord(word, initials, full);
            }
        }
        flushWord(word, initials, full);
        if (full.length() == 0) {
            return "";
        }
        return initials.append(SEPARATOR).append(full).toString();
    }

    private static void flushWord(StringBuilder word, StringBuilder initials, StringBuilder full) {
        if (word.length() > 0) {
            initials.append(word.charAt(0));
            full.append(word);
            word.setLength(0);
        }
    }

    /** 多音字取 pinyin4j 的首选读音；非汉字返回 null。 */
    private static String pinyinOf(char c) {
        if (c < 0x4E00 || c > 0x9FA5) {
            return null;
        }
        return CACHE.computeIfAbsent(c, ch -> {
            try {
                String[] readings = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
                return readings == null || readings.length == 0 || readings[0].isEmpty() ? "" : readings[0];
            } catch (Exception e) {
                return "";
            }
        });
    }
}