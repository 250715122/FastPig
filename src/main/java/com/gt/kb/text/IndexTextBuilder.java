package com.gt.kb.text;

import com.gt.AppConfig;
import com.gt.config.ConfigKeys;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.model.KnowledgeBlock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 索引文本构建：面包屑 + 标题 + 规范化正文。
 *
 * 与旧清洗规则的关键差别是<b>保留代码内容</b>——命令、参数、配置项正是知识笔记里最常被检索的东西，
 * 旧规则把整段代码删掉，等于把最有价值的召回信号扔了。
 *
 * 面包屑和标题永不截断，只有正文受 {@code index.content.max.chars} 限制。
 */
public final class IndexTextBuilder {

    /** 代码占比超过此值的块，额外把代码里的标识符前置，增强 BM25 命中。 */
    private static final double CODE_HEAVY_RATIO = 0.7;
    private static final int MAX_IDENTIFIERS = 40;

    private static final Pattern COMMENT = Pattern.compile("<!--[\\s\\S]*?(?:-->|$)");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z][A-Za-z0-9]*(?:\\s[^<>]*)?/?>");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+");
    private static final Pattern HR = Pattern.compile("^\\s*(?:[-*_]\\s*){3,}$");
    private static final Pattern LIST_MARKER = Pattern.compile("^\\s*(?:[-*+]|\\d+[.)])\\s+");
    private static final Pattern QUOTE = Pattern.compile("^\\s*>\\s?");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*([^*]+)\\*");
    private static final Pattern UNDERLINE_BOLD = Pattern.compile("__([^_]+)__");
    private static final Pattern UNDERLINE_ITALIC = Pattern.compile("_([^_]+)_");
    private static final Pattern STRIKE = Pattern.compile("~~([^~]+)~~");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");

    private static final Pattern FLAG = Pattern.compile("(?<![\\w-])--?[A-Za-z][\\w-]*");
    private static final Pattern ASSIGN_KEY = Pattern.compile("\\b([A-Za-z_][\\w.]*)\\s*=");
    private static final Pattern COMMAND = Pattern.compile("^([A-Za-z][\\w./-]*)");
    private static final Pattern PIPED_COMMAND = Pattern.compile("[|;]\\s*([A-Za-z][\\w./-]*)");

    private IndexTextBuilder() {
    }

    public static String build(KnowledgeBlock block) {
        return build(block, AppConfig.getInstance().getInt(ConfigKeys.INDEX_CONTENT_MAX_CHARS));
    }

    public static String build(KnowledgeBlock block, int maxChars) {
        String body = normalize(block.body());
        int limit = Math.max(0, maxChars);
        if (body.length() > limit) {
            body = body.substring(0, limit);
        }

        List<String> parts = new ArrayList<>();
        String identifiers = identifiers(block.body());
        if (!identifiers.isEmpty()) {
            parts.add(identifiers);
        }
        String path = block.pathText();
        if (!path.isEmpty()) {
            parts.add(path);
        }
        if (!block.title().isEmpty()) {
            parts.add(block.title());
        }
        if (!body.isEmpty()) {
            parts.add(body);
        }
        return String.join("\n", parts);
    }

    /**
     * Markdown → 纯文本：去掉标记，保留代码内容与行内代码内容。
     * 代码行原样保留（含 {@code #} 注释和缩进以外的符号），不做 Markdown 清洗，
     * 否则 {@code --force}、{@code key=value} 会被当成标记破坏掉。
     */
    public static String normalize(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String[] lines = COMMENT.matcher(body).replaceAll("").split("\r\n|\r|\n", -1);
        boolean[] inCode = DefaultChunker.markCode(lines);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (inCode[i] && DefaultChunker.isFenceLine(lines[i])) {
                continue;
            }
            String text = inCode[i] ? lines[i] : cleanLine(lines[i]);
            text = SPACES.matcher(text).replaceAll(" ").strip();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return String.join("\n", out);
    }

    private static String cleanLine(String line) {
        if (HR.matcher(line).matches()) {
            return "";
        }
        String s = IMAGE.matcher(line).replaceAll("$1");
        s = LINK.matcher(s).replaceAll("$1");
        s = HTML_TAG.matcher(s).replaceAll("");
        s = HEADING.matcher(s).replaceFirst("");
        s = LIST_MARKER.matcher(s).replaceFirst("");
        s = QUOTE.matcher(s).replaceFirst("");
        s = BOLD.matcher(s).replaceAll("$1");
        s = ITALIC.matcher(s).replaceAll("$1");
        s = UNDERLINE_BOLD.matcher(s).replaceAll("$1");
        s = UNDERLINE_ITALIC.matcher(s).replaceAll("$1");
        s = STRIKE.matcher(s).replaceAll("$1");
        return s.replace("`", "");
    }

    /** 代码占比 &gt; 70% 时抽取标识符，否则返回空串。只统计围栏代码，行内代码不参与占比。 */
    private static String identifiers(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String[] lines = body.split("\r\n|\r|\n", -1);
        boolean[] inCode = DefaultChunker.markCode(lines);
        StringBuilder code = new StringBuilder();
        int codeChars = 0;
        int totalChars = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank() || (inCode[i] && DefaultChunker.isFenceLine(lines[i]))) {
                continue;
            }
            int len = lines[i].strip().length();
            totalChars += len;
            if (inCode[i]) {
                codeChars += len;
                code.append(lines[i]).append('\n');
            }
        }
        if (totalChars == 0 || (double) codeChars / totalChars <= CODE_HEAVY_RATIO) {
            return "";
        }

        Set<String> ids = new LinkedHashSet<>();
        for (String line : code.toString().split("\n", -1)) {
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            Matcher m = COMMAND.matcher(s);
            if (m.find()) {
                ids.add(m.group(1));
            }
            m = PIPED_COMMAND.matcher(s);
            while (m.find()) {
                ids.add(m.group(1));
            }
            m = FLAG.matcher(s);
            while (m.find()) {
                ids.add(m.group());
            }
            m = ASSIGN_KEY.matcher(s);
            while (m.find()) {
                ids.add(m.group(1));
            }
            if (ids.size() >= MAX_IDENTIFIERS) {
                break;
            }
        }
        List<String> capped = new ArrayList<>(ids);
        return String.join(" ", capped.subList(0, Math.min(capped.size(), MAX_IDENTIFIERS)));
    }
}