package com.gt.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 评测集 queries.yaml 的读取。项目没有 snakeyaml 依赖，这里只解析评测集用到的 YAML 子集：
 * 顶层对象列表 + expect 嵌套对象列表，值都是单行标量。
 */
public final class EvalQueries {

    /** 一条期望命中。title 为空表示笔记级命中（该笔记任意块都算）。 */
    public record Expect(String note, String title) {
        public boolean noteLevel() {
            return title == null || title.isEmpty();
        }
    }

    public record Query(String id, String text, String kind, String profile, List<Expect> expect) {
        public Query {
            expect = List.copyOf(expect);
        }
    }

    private EvalQueries() {
    }

    public static List<Query> load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static List<Query> parse(String yaml) {
        List<Query> queries = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        String body = yaml.isEmpty() || yaml.charAt(0) != 0xFEFF ? yaml : yaml.substring(1);
        String[] lines = body.split("\r\n|\n|\r", -1);
        Builder cur = null;
        ExpectBuilder curExpect = null;
        int expectItemIndent = -1;
        boolean inExpect = false;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = raw.length() - raw.stripLeading().length();
            int lineNo = i + 1;

            if (indent == 0) {
                if (!trimmed.startsWith("- ")) {
                    throw error(lineNo, "顶层必须是 '- id: xxx' 形式的列表项");
                }
                if (cur != null) {
                    queries.add(cur.finish(lineNo, ids));
                }
                cur = new Builder();
                curExpect = null;
                inExpect = false;
                applyQueryField(cur, trimmed.substring(2).trim(), lineNo);
                continue;
            }

            if (cur == null) {
                throw error(lineNo, "缩进行出现在任何 '- id:' 之前");
            }

            if (trimmed.startsWith("- ")) {
                if (!inExpect) {
                    throw error(lineNo, "只有 expect 下才能有嵌套列表项");
                }
                curExpect = new ExpectBuilder();
                expectItemIndent = indent;
                cur.expects.add(curExpect);
                applyExpectField(curExpect, trimmed.substring(2).trim(), lineNo);
                continue;
            }

            if (curExpect != null && indent > expectItemIndent) {
                applyExpectField(curExpect, trimmed, lineNo);
            } else {
                curExpect = null;
                String key = keyOf(trimmed, lineNo);
                if (key.equals("expect")) {
                    inExpect = true;
                } else {
                    applyQueryField(cur, trimmed, lineNo);
                }
            }
        }

        if (cur != null) {
            queries.add(cur.finish(lines.length, ids));
        }
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("评测集为空");
        }
        return queries;
    }

    private static void applyQueryField(Builder b, String field, int lineNo) {
        int colon = field.indexOf(':');
        if (colon < 0) {
            throw error(lineNo, "缺少冒号: " + field);
        }
        String key = field.substring(0, colon).trim();
        String value = unquote(field.substring(colon + 1).trim());
        switch (key) {
            case "id" -> b.id = value;
            case "query" -> b.text = value;
            case "kind" -> b.kind = value;
            case "profile" -> b.profile = value;
            case "expect" -> throw error(lineNo, "expect 必须是列表，不能写成单行");
            default -> throw error(lineNo, "未知字段 '" + key + "'");
        }
    }

    private static void applyExpectField(ExpectBuilder b, String field, int lineNo) {
        int colon = field.indexOf(':');
        if (colon < 0) {
            throw error(lineNo, "缺少冒号: " + field);
        }
        String key = field.substring(0, colon).trim();
        String value = unquote(field.substring(colon + 1).trim());
        switch (key) {
            case "note" -> b.note = value;
            case "title" -> b.title = value;
            default -> throw error(lineNo, "expect 下未知字段 '" + key + "'");
        }
    }

    private static String keyOf(String field, int lineNo) {
        int colon = field.indexOf(':');
        if (colon < 0) {
            throw error(lineNo, "缺少冒号: " + field);
        }
        return field.substring(0, colon).trim();
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static IllegalArgumentException error(int lineNo, String message) {
        return new IllegalArgumentException("queries.yaml 第 " + lineNo + " 行: " + message);
    }

    private static final class Builder {
        String id;
        String text;
        String kind;
        String profile = "human";
        final List<ExpectBuilder> expects = new ArrayList<>();

        Query finish(int lineNo, Set<String> ids) {
            if (isBlank(id)) {
                throw error(lineNo, "缺少 id");
            }
            if (!ids.add(id)) {
                throw error(lineNo, "id 重复: " + id);
            }
            if (isBlank(text)) {
                throw error(lineNo, id + " 缺少 query");
            }
            if (isBlank(kind)) {
                throw error(lineNo, id + " 缺少 kind");
            }
            if (expects.isEmpty()) {
                throw error(lineNo, id + " 缺少 expect");
            }
            List<Expect> list = new ArrayList<>();
            for (ExpectBuilder e : expects) {
                if (isBlank(e.note)) {
                    throw error(lineNo, id + " 的 expect 缺少 note");
                }
                list.add(new Expect(e.note, e.title));
            }
            return new Query(id, text, kind, profile, list);
        }

        private static boolean isBlank(String s) {
            return s == null || s.isEmpty();
        }
    }

    private static final class ExpectBuilder {
        String note;
        String title;
    }
}