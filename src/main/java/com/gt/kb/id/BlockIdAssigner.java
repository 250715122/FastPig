package com.gt.kb.id;

import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.model.KnowledgeBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 给无 ID 的知识块补上 ID 注释行，并把重复/被占用的 ID 换掉。
 *
 * 只增删 ID 注释行，绝不改动正文一个字；重复运行结果不变（幂等）。
 * 索引块（父ID#pN）不参与——它们不写回原文。
 */
public final class BlockIdAssigner {

    /**
     * @param insertions 新增的 ID 行，按原正文行号升序。text 是待插入的原文（含行尾符），
     *                   编辑器直接插回去即可，不必自己猜行尾符；光标由 Swing 的 Position 自动顺延。
     */
    public record Result(String body, int assigned, int reassigned, List<Insertion> insertions) {
        /** line 为插入点在原正文中的行号（0 起），等于原正文行数时表示追加到文末。 */
        public record Insertion(int line, String text) {
        }

        public Result {
            insertions = List.copyOf(insertions);
        }

        public List<Integer> insertedLines() {
            return insertions.stream().map(Insertion::line).toList();
        }

        public boolean changed() {
            return assigned > 0 || reassigned > 0;
        }
    }

    private static final DefaultChunker CHUNKER = new DefaultChunker();

    private BlockIdAssigner() {
    }

    public static Result assign(String noteKey, String noteDesc, String body, ChunkProfile profile) {
        return assign(noteKey, noteDesc, body, profile, id -> false);
    }

    /** taken 判定 ID 是否已被别的块占用（全局块目录），命中则重新分配。 */
    public static Result assign(String noteKey, String noteDesc, String body, ChunkProfile profile,
                                Predicate<String> taken) {
        if (body == null || body.isBlank()) {
            return new Result(body, 0, 0, List.of());
        }
        Lines out = Lines.of(body);
        String[] lines = out.text.toArray(String[]::new);
        boolean[] inCode = DefaultChunker.markCode(lines);
        Set<String> seen = new HashSet<>();

        int reassigned = replaceConflictingIds(lines, inCode, out, seen, taken);
        List<Result.Insertion> inserted = insertMissingIds(noteKey, noteDesc, body, profile, lines, out, seen, taken);

        return new Result(out.join(), inserted.size(), reassigned, inserted);
    }

    /**
     * 补 ID 的不变量：只允许增删 ID 注释行，正文其余部分必须逐字不变（含行尾符）。
     * 迁移工具和保存路径都在落盘前用它自检，不通过就别写。
     */
    public static boolean preservesText(String before, String after) {
        return stripIds(before).equals(stripIds(after));
    }

    private static final Pattern EOL = Pattern.compile("\r\n|\r|\n");

    private static String stripIds(String text) {
        StringBuilder sb = new StringBuilder();
        Matcher m = EOL.matcher(text);
        int pos = 0;
        while (m.find()) {
            String line = text.substring(pos, m.start());
            if (BlockIdCodec.parse(line).isEmpty()) {
                sb.append(line).append(m.group());
            }
            pos = m.end();
        }
        String tail = text.substring(pos);
        if (BlockIdCodec.parse(tail).isEmpty()) {
            sb.append(tail);
        } else {
            // 追加在文末的 ID 行会给上一行补一个行尾符，撤销 ID 时一并撤销它
            int len = sb.length();
            if (len >= 2 && sb.charAt(len - 2) == '\r' && sb.charAt(len - 1) == '\n') {
                sb.setLength(len - 2);
            } else if (len > 0 && (sb.charAt(len - 1) == '\n' || sb.charAt(len - 1) == '\r')) {
                sb.setLength(len - 1);
            }
        }
        return sb.toString();
    }

    private static int replaceConflictingIds(String[] lines, boolean[] inCode, Lines out,
                                             Set<String> seen, Predicate<String> taken) {
        int reassigned = 0;
        for (int i = 0; i < lines.length; i++) {
            if (inCode[i]) {
                continue;
            }
            Optional<BlockIdCodec.IdLine> parsed = BlockIdCodec.parse(lines[i]);
            if (parsed.isEmpty()) {
                continue;
            }
            String id = parsed.get().id();
            boolean firstUse = seen.add(id);
            if (firstUse && !taken.test(id)) {
                continue;
            }
            String fresh = BlockIdGenerator.next(taken.or(seen::contains));
            out.set(i, BlockIdCodec.format(new BlockIdCodec.IdLine(fresh, parsed.get().attrs())));
            seen.add(fresh);
            reassigned++;
        }
        return reassigned;
    }

    private static List<Result.Insertion> insertMissingIds(String noteKey, String noteDesc, String body,
                                                           ChunkProfile profile, String[] lines, Lines out,
                                                           Set<String> seen, Predicate<String> taken) {
        List<Integer> insertions = new ArrayList<>();
        for (KnowledgeBlock block : CHUNKER.chunk(noteKey, noteDesc, body, profile)) {
            if (BlockIdCodec.isValidId(block.id())) {
                continue;
            }
            int idx = block.startLine() - 1;
            if (idx < 0 || idx >= lines.length) {
                continue;
            }
            boolean titled = DefaultChunker.isBoundaryTitle(lines[idx], idx == 0 || lines[idx - 1].isBlank());
            insertions.add(titled ? idx + 1 : idx);
        }
        if (insertions.isEmpty()) {
            return List.of();
        }
        // 从后往前插，前面的行号才不会被已插入的行推偏
        insertions.sort(Comparator.reverseOrder());
        List<Result.Insertion> inserted = new ArrayList<>();
        for (int at : insertions) {
            String fresh = BlockIdGenerator.next(taken.or(seen::contains));
            seen.add(fresh);
            String idLine = BlockIdCodec.format(fresh);
            String separator = out.separatorAt(at);
            // 文末追加时上一行会被补一个行尾符，等价于在正文末尾插入"行尾符 + ID 行"
            String text = out.appendsEol(at) ? out.eol + idLine : idLine + separator;
            out.insert(at, idLine, separator);
            inserted.add(new Result.Insertion(at, text));
        }
        inserted.sort(Comparator.comparingInt(Result.Insertion::line));
        return inserted;
    }

    /**
     * 行内容 + 每行原本的行尾符。
     *
     * 必须逐行记住行尾符再原样拼回：真实笔记里存在混用 CRLF/LF、甚至夹着孤立 CR 的情况，
     * 统一用一种行尾符重新拼接会悄悄改掉正文，迁移自检会因此整篇拒绝落盘。
     */
    private static final class Lines {
        final List<String> text = new ArrayList<>();
        final List<String> sep = new ArrayList<>();
        String eol = "\n";

        static Lines of(String body) {
            Lines lines = new Lines();
            lines.eol = body.contains("\r\n") ? "\r\n" : "\n";
            Matcher m = EOL.matcher(body);
            int pos = 0;
            while (m.find()) {
                lines.text.add(body.substring(pos, m.start()));
                lines.sep.add(m.group());
                pos = m.end();
            }
            lines.text.add(body.substring(pos));
            lines.sep.add("");
            return lines;
        }

        void set(int i, String line) {
            text.set(i, line);
        }

        /** 文末追加 ID 行时，上一行原本没有行尾符，必须补一个，否则 ID 会粘在正文后面。 */
        boolean appendsEol(int i) {
            return i == text.size() && i > 0 && sep.get(i - 1).isEmpty();
        }

        void insert(int i, String line, String separator) {
            if (appendsEol(i)) {
                sep.set(i - 1, eol);
            }
            text.add(i, line);
            sep.add(i, separator);
        }

        /**
         * 新插入的行沿用上一行的行尾符；插在行首时沿用下一行的。
         * 两侧都没有行尾符（整篇只有一行且不以换行结尾）时必须补一个，否则 ID 会粘在正文上。
         * 只有追加到文末时才允许为空——那时 ID 行自己就是最后一行。
         */
        String separatorAt(int i) {
            if (i > 0 && !sep.get(i - 1).isEmpty()) {
                return sep.get(i - 1);
            }
            if (i < sep.size() && !sep.get(i).isEmpty()) {
                return sep.get(i);
            }
            return i == text.size() ? "" : eol;
        }

        String join() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.size(); i++) {
                sb.append(text.get(i)).append(sep.get(i));
            }
            return sb.toString();
        }
    }
}