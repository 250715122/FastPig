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
     * @param insertedLines 新增 ID 行的行号（0 起，升序），供编辑器按插入点修正光标
     */
    public record Result(String body, int assigned, int reassigned, List<Integer> insertedLines) {
        public Result {
            insertedLines = List.copyOf(insertedLines);
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
        List<Integer> inserted = insertMissingIds(noteKey, noteDesc, body, profile, lines, out, seen, taken);

        return new Result(out.join(), inserted.size(), reassigned, inserted);
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

    private static List<Integer> insertMissingIds(String noteKey, String noteDesc, String body, ChunkProfile profile,
                                                  String[] lines, Lines out, Set<String> seen,
                                                  Predicate<String> taken) {
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
        List<Integer> inserted = new ArrayList<>();
        for (int at : insertions) {
            String fresh = BlockIdGenerator.next(taken.or(seen::contains));
            seen.add(fresh);
            out.insert(at, BlockIdCodec.format(fresh), out.separatorAt(at));
            inserted.add(at);
        }
        inserted.sort(Comparator.naturalOrder());
        return inserted;
    }

    /**
     * 行内容 + 每行原本的行尾符。
     *
     * 必须逐行记住行尾符再原样拼回：真实笔记里存在混用 CRLF/LF、甚至夹着孤立 CR 的情况，
     * 统一用一种行尾符重新拼接会悄悄改掉正文，迁移自检会因此整篇拒绝落盘。
     */
    private static final class Lines {
        private static final Pattern EOL = Pattern.compile("\r\n|\r|\n");

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

        void insert(int i, String line, String separator) {
            if (i == text.size() && i > 0 && sep.get(i - 1).isEmpty()) {
                // 文末追加 ID 行：上一行原本没有行尾符，不补一个的话 ID 会粘在正文后面
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