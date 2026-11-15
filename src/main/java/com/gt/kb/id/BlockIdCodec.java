package com.gt.kb.id;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 块 ID 注释行的读写。
 *
 * 文件里写 {@code <!-- ku:7f3ka2mb -->}，模型里用 {@code ku_7f3ka2mb}，
 * 两种形态的转换只在这里发生。
 */
public final class BlockIdCodec {

    /** 小写 base32 字母表（RFC4648，去掉易混的 0/1/8/9）。 */
    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";
    public static final int ID_LENGTH = 8;
    public static final String MODEL_PREFIX = "ku_";
    public static final String FILE_PREFIX = "ku:";

    private static final Pattern ID_LINE = Pattern.compile(
            "^<!--\\s*ku:([a-z2-7]{8})((?:\\s+\\w+=(?:\"[^\"]*\"|\\S+))*)\\s*-->\\s*$");
    private static final Pattern ATTR = Pattern.compile("(\\w+)=(\"[^\"]*\"|\\S+)");
    private static final Pattern DASH_RUN = Pattern.compile("-{2,}");

    /** 一行 ID 注释：id 为模型形态（ku_ 前缀），attrs 保持书写顺序。 */
    public record IdLine(String id, Map<String, String> attrs) {
        public IdLine {
            attrs = attrs == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(attrs));
        }

        public static IdLine of(String id) {
            return new IdLine(id, Map.of());
        }
    }

    private BlockIdCodec() {
    }

    public static Optional<IdLine> parse(String line) {
        if (line == null) {
            return Optional.empty();
        }
        Matcher m = ID_LINE.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher a = ATTR.matcher(m.group(2));
        while (a.find()) {
            attrs.put(a.group(1), unquote(a.group(2)));
        }
        return Optional.of(new IdLine(MODEL_PREFIX + m.group(1), attrs));
    }

    public static String format(IdLine line) {
        StringBuilder sb = new StringBuilder("<!-- ").append(FILE_PREFIX).append(suffixOf(line.id()));
        for (Map.Entry<String, String> e : line.attrs().entrySet()) {
            sb.append(' ').append(e.getKey()).append('=').append(quote(e.getValue()));
        }
        return sb.append(" -->").toString();
    }

    public static String format(String id) {
        return format(IdLine.of(id));
    }

    /** 模型形态 ID 是否合法：ku_ + 8 位 base32。用户手改过的 ID 靠这个判定去留。 */
    public static boolean isValidId(String id) {
        if (id == null || id.length() != MODEL_PREFIX.length() + ID_LENGTH || !id.startsWith(MODEL_PREFIX)) {
            return false;
        }
        return isSuffix(id.substring(MODEL_PREFIX.length()));
    }

    private static String suffixOf(String id) {
        if (!isValidId(id)) {
            throw new IllegalArgumentException("非法块 ID: " + id);
        }
        return id.substring(MODEL_PREFIX.length());
    }

    private static boolean isSuffix(String suffix) {
        for (int i = 0; i < suffix.length(); i++) {
            if (ALPHABET.indexOf(suffix.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.charAt(0) == '"'
                ? value.substring(1, value.length() - 1)
                : value;
    }

    /** 属性值里的引号会截断引号串、连续减号会提前闭合注释，两者都会毁掉整行 ID，故剔除。 */
    private static String quote(String value) {
        String v = value == null ? "" : DASH_RUN.matcher(value.replace("\"", "")).replaceAll("-");
        boolean needQuote = v.isEmpty() || v.chars().anyMatch(Character::isWhitespace);
        return needQuote ? '"' + v + '"' : v;
    }
}