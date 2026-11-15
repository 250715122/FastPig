package com.gt.kb.chunk;

import com.gt.kb.id.BlockIdCodec;
import com.gt.kb.model.BlockMeta;
import com.gt.kb.model.KnowledgeBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认切分器：多级标题 / 编号标题 / 日期条目 / 已有块 ID 行都算边界，
 * 代码围栏内的行一律不参与边界判定。
 *
 * 产出分两种：{@link #chunk} 给知识块（原文里有 ID，可引用、可治理），
 * {@link #chunkForIndex} 给索引块（知识块超长时二次切分，ID 形如 父ID#p2，不写回原文）。
 */
public final class DefaultChunker implements Chunker {

    private static final Pattern ATX = Pattern.compile("^(#{1,6})\\s+(\\S.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+){0,3})[、.\\s]\\s*(\\S.{0,40})$");
    private static final Pattern DATE_TEXT = Pattern.compile("^\\s*(20\\d{2}[-/.]?\\d{2}[-/.]?\\d{2})\\s*$");
    private static final Pattern DATE_HEADING = Pattern.compile("^#{1,6}\\s+(20\\d{2}[-/.]?\\d{2}[-/.]?\\d{2}.*)$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(`{3,}|~{3,})(.*)$");
    private static final Pattern TITLE_PREFIX = Pattern.compile("^(?:#{1,6}\\s+|[-*+]\\s+|>\\s*|\\d+[.、]\\s*)");

    private static final int AUTO_TITLE_CHARS = 30;
    /** 尚未迁移的笔记没有原文 ID，用 noteKey+行号派生临时 ID，保证索引文档不互相覆盖。 */
    private static final String TEMP_PREFIX = "tmp_";

    @Override
    public List<KnowledgeBlock> chunk(String noteKey, String noteDesc, String body, ChunkProfile profile) {
        return materialize(noteKey, knowledge(noteKey, noteDesc, body, profile));
    }

    /** 检索用的切分结果：能装下的知识块原样返回，超长的换成若干索引块。 */
    public List<KnowledgeBlock> chunkForIndex(String noteKey, String noteDesc, String body, ChunkProfile profile) {
        String[] lines = splitLines(body);
        if (lines.length == 0) {
            return List.of();
        }
        boolean[] inCode = markCode(lines);
        List<Piece> pieces = knowledgePieces(lines, inCode, desc(noteKey, noteDesc), profile);
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.body.length() <= profile.maxChars()) {
                out.add(p);
            } else {
                out.addAll(subdivide(p, noteKey, lines, inCode, profile));
            }
        }
        return materialize(noteKey, out);
    }

    // ==================== 知识块 ====================

    private List<Piece> knowledge(String noteKey, String noteDesc, String body, ChunkProfile profile) {
        String[] lines = splitLines(body);
        if (lines.length == 0) {
            return List.of();
        }
        return knowledgePieces(lines, markCode(lines), desc(noteKey, noteDesc), profile);
    }

    private List<Piece> knowledgePieces(String[] lines, boolean[] inCode, String desc, ChunkProfile profile) {
        Map<Integer, Boundary> candidates = new TreeMap<>();
        for (int i = 0; i < lines.length; i++) {
            if (inCode[i]) {
                continue;
            }
            Optional<BlockIdCodec.IdLine> id = BlockIdCodec.parse(lines[i]);
            if (id.isPresent()) {
                Boundary prev = candidates.get(i - 1);
                if (prev != null && prev.id == null) {
                    prev.id = id.get().id();
                    // 原文里已有 ID 的块必须始终成块，否则改切分级别会让 ID 凭空消失
                    prev.pinned = true;
                } else {
                    candidates.put(i, Boundary.forced(i, id.get().id()));
                }
                continue;
            }
            Boundary b = boundary(lines, inCode, i);
            if (b != null) {
                candidates.put(i, b);
            }
        }

        int primary = primaryLevel(candidates.values(), profile);
        List<Boundary> selected = new ArrayList<>();
        for (Boundary b : candidates.values()) {
            if (b.pinned || b.level <= primary) {
                selected.add(b);
            }
        }

        List<Piece> pieces = new ArrayList<>();
        List<Heading> stack = new ArrayList<>();
        int first = selected.isEmpty() ? lines.length : selected.get(0).line;
        if (hasText(lines, 0, first - 1)) {
            pieces.add(makePiece(lines, 0, 0, first - 1, KnowledgeBlock.LEVEL_FALLBACK, "", null,
                    pathOf(desc, stack), false));
        }

        for (int j = 0; j < selected.size(); j++) {
            Boundary b = selected.get(j);
            int end = (j + 1 < selected.size() ? selected.get(j + 1).line : lines.length) - 1;
            List<String> parentPath;
            if (b.forced) {
                parentPath = pathOf(desc, stack);
            } else if (b.level == KnowledgeBlock.LEVEL_DATE) {
                stack.clear();
                parentPath = List.of(desc);
            } else {
                while (!stack.isEmpty() && stack.get(stack.size() - 1).level >= b.level) {
                    stack.remove(stack.size() - 1);
                }
                parentPath = pathOf(desc, stack);
                stack.add(new Heading(b.level, b.title));
            }
            Piece p = makePiece(lines, b.line, b.line + 1, end, b.level, b.title, b.id, parentPath, !b.forced);
            if (p.titled || hasText(lines, p.bodyFrom, p.end)) {
                pieces.add(p);
            }
        }
        return mergeShort(pieces, profile.minChars());
    }

    /** 最浅且出现 ≥2 次的标题级别为主切分级；一个都不够 2 次时退而取最浅的级别。 */
    private static int primaryLevel(Iterable<Boundary> boundaries, ChunkProfile profile) {
        int[] counts = new int[7];
        for (Boundary b : boundaries) {
            if (!b.forced && b.level >= 0 && b.level < counts.length) {
                counts[b.level]++;
            }
        }
        if (profile.dateFirst() && counts[KnowledgeBlock.LEVEL_DATE] > 0) {
            return KnowledgeBlock.LEVEL_DATE;
        }
        // 日期条目只在没有重复标题结构时才当主切分级。知识笔记末尾混几行日期时，
        // 若让 level 0 参与"最浅"竞争，整套标题结构都会被挤掉，全篇塌成一个兜底块。
        for (int level = 1; level < counts.length; level++) {
            if (counts[level] >= 2) {
                return level;
            }
        }
        if (counts[KnowledgeBlock.LEVEL_DATE] > 0) {
            return KnowledgeBlock.LEVEL_DATE;
        }
        for (int level = 1; level < counts.length; level++) {
            if (counts[level] > 0) {
                return level;
            }
        }
        return -1;
    }

    private static List<Piece> mergeShort(List<Piece> pieces, int minChars) {
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces) {
            if (!out.isEmpty() && !p.titled && p.body.length() < minChars) {
                Piece prev = out.get(out.size() - 1);
                prev.end = p.end;
                prev.body = prev.body.isEmpty() ? p.body : prev.body + "\n" + p.body;
            } else {
                out.add(p);
            }
        }
        return out;
    }

    // ==================== 索引块（二次切分） ====================

    private List<Piece> subdivide(Piece parent, String noteKey, String[] lines, boolean[] inCode, ChunkProfile profile) {
        String parentId = effectiveId(parent, noteKey);
        List<Piece> out = new ArrayList<>();
        int seq = 0;
        for (Piece part : splitByHeading(parent, lines, inCode)) {
            List<Piece> pieces = part.body.length() <= profile.maxChars()
                    ? List.of(part)
                    : splitByParagraph(part, lines, inCode, profile);
            for (Piece p : pieces) {
                p.id = parentId + "#p" + (++seq);
                out.add(p);
            }
        }
        return out;
    }

    /** 按块内最浅的更深层标题再切；没有更深层标题就原样返回，交给段落聚合。 */
    private List<Piece> splitByHeading(Piece parent, String[] lines, boolean[] inCode) {
        int minLevel = parent.level >= 1 ? parent.level + 1 : 1;
        int level = Integer.MAX_VALUE;
        List<Boundary> inner = new ArrayList<>();
        for (int i = parent.start + 1; i <= parent.end; i++) {
            Boundary b = boundary(lines, inCode, i);
            if (b != null && !b.forced && b.level >= minLevel) {
                inner.add(b);
                level = Math.min(level, b.level);
            }
        }
        if (inner.isEmpty()) {
            return List.of(parent);
        }

        List<Piece> out = new ArrayList<>();
        int start = parent.start;
        int bodyFrom = parent.bodyFrom;
        int curLevel = parent.level;
        String curTitle = parent.title;
        List<String> curPath = withoutOwnTitle(parent);
        boolean curTitled = parent.titled;
        for (Boundary b : inner) {
            if (b.level != level) {
                continue;
            }
            if (hasText(lines, bodyFrom, b.line - 1)) {
                out.add(makePiece(lines, start, bodyFrom, b.line - 1, curLevel, curTitle, null, curPath, curTitled));
            }
            start = b.line;
            bodyFrom = b.line + 1;
            curLevel = b.level;
            curTitle = b.title;
            curPath = parent.path;
            curTitled = true;
        }
        if (curTitled || hasText(lines, bodyFrom, parent.end)) {
            out.add(makePiece(lines, start, bodyFrom, parent.end, curLevel, curTitle, null, curPath, curTitled));
        }
        return out.isEmpty() ? List.of(parent) : out;
    }

    /** makePiece 会自己把标题追加到面包屑末尾，故传入前先摘掉块自身那一级。 */
    private static List<String> withoutOwnTitle(Piece p) {
        List<String> path = p.path;
        if (!path.isEmpty() && path.get(path.size() - 1).equals(p.title)) {
            return path.subList(0, path.size() - 1);
        }
        return path;
    }

    private List<Piece> splitByParagraph(Piece part, String[] lines, boolean[] inCode, ChunkProfile profile) {
        List<Piece> out = new ArrayList<>();
        List<String> buf = new ArrayList<>();
        int bufChars = 0;
        int start = -1;
        int end = -1;
        String overlap = "";
        for (int[] range : paragraphRanges(lines, inCode, part.bodyFrom, part.end)) {
            String text = joinLines(lines, range[0], range[1]);
            for (String slice : text.length() > profile.maxChars() ? hardSlices(text, profile.targetChars()) : List.of(text)) {
                if (bufChars > 0 && bufChars + slice.length() > profile.targetChars()) {
                    String body = joinBuf(buf, overlap);
                    out.add(copyWithBody(part, start, end, body));
                    overlap = tail(body, profile.overlapChars());
                    buf.clear();
                    bufChars = 0;
                    start = -1;
                }
                if (start < 0) {
                    start = range[0];
                }
                end = range[1];
                buf.add(slice);
                bufChars += slice.length() + 1;
            }
        }
        if (!buf.isEmpty()) {
            out.add(copyWithBody(part, start, end, joinBuf(buf, overlap)));
        }
        return out.isEmpty() ? List.of(part) : out;
    }

    /** 空行分段，但代码围栏内的空行不算段边界。 */
    private static List<int[]> paragraphRanges(String[] lines, boolean[] inCode, int from, int to) {
        List<int[]> ranges = new ArrayList<>();
        int i = Math.max(0, from);
        while (i <= to && i < lines.length) {
            if (lines[i].isBlank() && !inCode[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i <= to && i < lines.length && (!lines[i].isBlank() || inCode[i])) {
                i++;
            }
            ranges.add(new int[]{start, i - 1});
        }
        return ranges;
    }

    private static List<String> hardSlices(String text, int size) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            out.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return out;
    }

    private static String tail(String text, int chars) {
        return chars <= 0 || text.isEmpty() ? "" : text.substring(Math.max(0, text.length() - chars));
    }

    private static String joinBuf(List<String> buf, String overlap) {
        String body = String.join("\n", buf);
        return overlap.isEmpty() ? body : overlap + "\n" + body;
    }

    // ==================== 边界识别 ====================

    private static Boundary boundary(String[] lines, boolean[] inCode, int i) {
        if (inCode[i]) {
            return null;
        }
        String line = lines[i];
        Matcher m = DATE_HEADING.matcher(line);
        if (m.matches()) {
            return new Boundary(i, KnowledgeBlock.LEVEL_DATE, m.group(1).trim(), false);
        }
        m = DATE_TEXT.matcher(line);
        if (m.matches()) {
            return new Boundary(i, KnowledgeBlock.LEVEL_DATE, m.group(1), false);
        }
        m = ATX.matcher(line);
        if (m.matches()) {
            return new Boundary(i, m.group(1).length(), m.group(2).trim(), false);
        }
        m = NUMBERED.matcher(line);
        if (m.matches() && (i == 0 || lines[i - 1].isBlank())) {
            int dots = (int) m.group(1).chars().filter(c -> c == '.').count();
            return new Boundary(i, dots + 1, line.trim(), false);
        }
        return null;
    }

    /** 该行是否是块标题行（ATX / 日期 / 编号）。补 ID 时据此决定插在标题后还是块首前。 */
    public static boolean isBoundaryTitle(String line, boolean previousBlank) {
        if (line == null) {
            return false;
        }
        return DATE_HEADING.matcher(line).matches()
                || DATE_TEXT.matcher(line).matches()
                || ATX.matcher(line).matches()
                || (previousBlank && NUMBERED.matcher(line).matches());
    }

    /** 该行是否是代码围栏的起止标记行（清洗索引文本时要整行丢掉，含语言名）。 */
    public static boolean isFenceLine(String line) {
        return line != null && FENCE.matcher(line).matches();
    }

    /** 代码围栏区间。未闭合时一直延伸到文末，避免把代码里的 # 注释当标题。 */
    public static boolean[] markCode(String[] lines) {
        boolean[] inCode = new boolean[lines.length];
        char openChar = 0;
        int openLen = 0;
        for (int i = 0; i < lines.length; i++) {
            Matcher m = FENCE.matcher(lines[i]);
            boolean fence = m.matches();
            if (openChar == 0) {
                if (fence) {
                    openChar = m.group(1).charAt(0);
                    openLen = m.group(1).length();
                    inCode[i] = true;
                }
            } else {
                inCode[i] = true;
                if (fence && m.group(1).charAt(0) == openChar
                        && m.group(1).length() >= openLen && m.group(2).isBlank()) {
                    openChar = 0;
                    openLen = 0;
                }
            }
        }
        return inCode;
    }

    // ==================== 组装 ====================

    private static Piece makePiece(String[] lines, int start, int bodyFrom, int end, int level,
                                   String title, String id, List<String> parentPath, boolean titled) {
        int last = end;
        while (last > start && lines[last].isBlank()) {
            last--;
        }
        int from = Math.max(bodyFrom, start);
        while (from <= last && lines[from].isBlank()) {
            from++;
        }
        List<String> bodyLines = new ArrayList<>();
        for (int i = from; i <= last; i++) {
            if (!BlockIdCodec.parse(lines[i]).isPresent()) {
                bodyLines.add(lines[i]);
            }
        }
        while (!bodyLines.isEmpty() && bodyLines.get(0).isBlank()) {
            bodyLines.remove(0);
        }
        while (!bodyLines.isEmpty() && bodyLines.get(bodyLines.size() - 1).isBlank()) {
            bodyLines.remove(bodyLines.size() - 1);
        }
        String body = String.join("\n", bodyLines);
        String realTitle = title == null || title.isEmpty() ? autoTitle(body) : title;
        List<String> path = new ArrayList<>(parentPath);
        if (!realTitle.isEmpty()) {
            path.add(realTitle);
        }
        Piece p = new Piece();
        p.start = start;
        p.end = Math.max(last, start);
        p.bodyFrom = bodyFrom;
        p.level = level;
        p.title = realTitle;
        p.titled = titled && !realTitle.isEmpty();
        p.id = id;
        p.path = List.copyOf(path);
        p.body = body;
        return p;
    }

    private static Piece copyWithBody(Piece src, int start, int end, String body) {
        Piece p = new Piece();
        p.start = start;
        p.end = end;
        p.bodyFrom = start;
        p.level = src.level;
        p.title = src.title;
        p.titled = src.titled;
        p.path = src.path;
        p.body = body;
        return p;
    }

    private List<KnowledgeBlock> materialize(String noteKey, List<Piece> pieces) {
        List<KnowledgeBlock> out = new ArrayList<>(pieces.size());
        for (Piece p : pieces) {
            out.add(new KnowledgeBlock(effectiveId(p, noteKey), noteKey, p.title, p.level, p.path, p.body,
                    p.start + 1, p.end + 1, null, BlockMeta.EMPTY));
        }
        return out;
    }

    private static String effectiveId(Piece p, String noteKey) {
        if (p.id != null && !p.id.isEmpty()) {
            return p.id;
        }
        return TEMP_PREFIX + noteKey + "_" + (p.start + 1);
    }

    /** 无标题块的自动摘要标题：取首行有效文本，去掉 Markdown 标记后截断。 */
    private static String autoTitle(String body) {
        for (String line : body.split("\n", -1)) {
            if (line.isBlank() || FENCE.matcher(line).matches()) {
                continue;
            }
            String text = TITLE_PREFIX.matcher(line.strip()).replaceFirst("")
                    .replace("`", "").replaceAll("\\s+", " ").strip();
            if (text.isEmpty()) {
                continue;
            }
            return text.length() <= AUTO_TITLE_CHARS ? text : text.substring(0, AUTO_TITLE_CHARS) + "…";
        }
        return "";
    }

    private static List<String> pathOf(String desc, List<Heading> stack) {
        List<String> path = new ArrayList<>();
        path.add(desc);
        for (Heading h : stack) {
            path.add(h.title);
        }
        return path;
    }

    private static String desc(String noteKey, String noteDesc) {
        return noteDesc == null || noteDesc.isBlank() ? (noteKey == null ? "" : noteKey) : noteDesc;
    }

    private static String[] splitLines(String body) {
        return body == null || body.isEmpty() ? new String[0] : body.split("\r\n|\r|\n", -1);
    }

    /** 块 ID 标记行是元数据不是正文，makePiece 会剔掉它，判断「这段有没有内容」必须同口径。 */
    private static boolean hasText(String[] lines, int from, int to) {
        for (int i = Math.max(0, from); i <= to && i < lines.length; i++) {
            if (!lines[i].isBlank() && !BlockIdCodec.parse(lines[i]).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static String joinLines(String[] lines, int from, int to) {
        return String.join("\n", Arrays.copyOfRange(lines, from, to + 1));
    }

    // ==================== 内部结构 ====================

    private record Heading(int level, String title) {
    }

    private static final class Boundary {
        final int line;
        final int level;
        final String title;
        final boolean forced;
        String id;
        boolean pinned;

        Boundary(int line, int level, String title, boolean forced) {
            this.line = line;
            this.level = level;
            this.title = title;
            this.forced = forced;
        }

        static Boundary forced(int line, String id) {
            Boundary b = new Boundary(line, KnowledgeBlock.LEVEL_FALLBACK, "", true);
            b.id = id;
            b.pinned = true;
            return b;
        }
    }

    private static final class Piece {
        int start;
        int end;
        int bodyFrom;
        int level;
        String title = "";
        boolean titled;
        String id;
        List<String> path = List.of();
        String body = "";
    }
}