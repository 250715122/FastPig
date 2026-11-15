package com.gt.kb.id;

import com.gt.NoteDto;
import com.gt.kb.chunk.ChunkProfile;
import com.gt.kb.chunk.DefaultChunker;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.storage.NoteFileStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 存量笔记补块 ID 的一次性迁移工具。
 *
 * 默认 dry-run：只读笔记、只出报告，不改任何一个 note.md。要落盘必须显式 --apply。
 * 落盘时保留 front matter 原文（只改 version / updatedAt），正文只增删 ID 注释行，
 * 写前自检"去掉 ID 行后正文必须与原文逐字相同"，不通过就跳过该篇。
 *
 * 用法：BlockIdMigration --notes <notes目录> [--apply] [--report <csv路径>]
 */
public final class BlockIdMigration {

    /** 与 NoteFileStorage 的 front matter 判定保持一致。 */
    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^version:[ \\t]*(\\d+)[ \\t]*$");
    private static final Pattern UPDATED_LINE = Pattern.compile("(?m)^updatedAt:[ \\t]*\\S+[ \\t]*$");

    private static final DefaultChunker CHUNKER = new DefaultChunker();

    public record Row(String key, String desc, int blocks, int indexBlocks, int assigned, int reassigned,
                      String status, long bytesBefore, long bytesAfter) {
    }

    public record Summary(List<Row> rows, int notes, int changed, int blocks, int indexBlocks,
                          int assigned, int reassigned, int skipped) {
    }

    private BlockIdMigration() {
    }

    public static Summary run(Path notesDir, boolean apply) throws IOException {
        Path abs = notesDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs)) {
            throw new IOException("notes 目录不存在: " + abs);
        }
        NoteFileStorage storage = new NoteFileStorage(abs.getParent().toString());
        List<NoteDto> notes = new ArrayList<>(storage.scanAllNotes());
        notes.sort(Comparator.comparing(n -> n.key == null ? "" : n.key));

        Set<String> usedIds = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        int changed = 0;
        int blocks = 0;
        int indexBlocks = 0;
        int assigned = 0;
        int reassigned = 0;
        int skipped = 0;

        for (NoteDto note : notes) {
            if (note.deleted || note.encrypted) {
                skipped++;
                rows.add(new Row(note.key, note.desc, 0, 0, 0, 0,
                        note.deleted ? "skip-deleted" : "skip-encrypted", 0, 0));
                continue;
            }
            Path file = Path.of(note.folderPath).resolve("note.md");
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher fm = FRONT_MATTER.matcher(content);
            if (!fm.find()) {
                skipped++;
                rows.add(new Row(note.key, note.desc, 0, 0, 0, 0, "skip-no-front-matter",
                        content.length(), content.length()));
                continue;
            }
            String front = content.substring(0, fm.end());
            String body = content.substring(fm.end());
            ChunkProfile profile = ChunkProfile.forNote(note.noteType, note.layer);

            List<KnowledgeBlock> before = CHUNKER.chunk(note.key, note.desc, body, profile);
            BlockIdAssigner.Result result = BlockIdAssigner.assign(note.key, note.desc, body, profile, usedIds::contains);
            List<KnowledgeBlock> after = CHUNKER.chunk(note.key, note.desc, result.body(), profile);
            int indexCount = CHUNKER.chunkForIndex(note.key, note.desc, result.body(), profile).size();

            blocks += after.size();
            indexBlocks += indexCount;
            assigned += result.assigned();
            reassigned += result.reassigned();
            for (KnowledgeBlock b : after) {
                if (BlockIdCodec.isValidId(b.id())) {
                    usedIds.add(b.id());
                }
            }

            String status;
            long afterBytes = content.length();
            if (!result.changed()) {
                status = "unchanged";
            } else if (!sameTextIgnoringIds(body, result.body())) {
                status = "ABORT-text-changed";
                skipped++;
            } else if (!apply) {
                status = "would-change";
                changed++;
                afterBytes = front.length() + result.body().length();
            } else {
                String newFront = bumpVersion(front);
                String out = newFront + result.body();
                atomicWrite(file, out);
                status = "changed";
                changed++;
                afterBytes = out.length();
            }
            rows.add(new Row(note.key, note.desc, before.size(), indexCount, result.assigned(),
                    result.reassigned(), status, content.length(), afterBytes));
        }
        return new Summary(List.copyOf(rows), notes.size(), changed, blocks, indexBlocks, assigned, reassigned, skipped);
    }

    /** 安全自检：补 ID 只允许增删 ID 注释行，正文其余部分必须逐字不变（含行尾符）。 */
    static boolean sameTextIgnoringIds(String before, String after) {
        return BlockIdAssigner.preservesText(before, after);
    }

    private static String bumpVersion(String front) {
        String out = front;
        Matcher v = VERSION_LINE.matcher(out);
        if (v.find()) {
            out = out.substring(0, v.start()) + "version: " + (Integer.parseInt(v.group(1)) + 1) + out.substring(v.end());
        }
        Matcher u = UPDATED_LINE.matcher(out);
        if (u.find()) {
            out = out.substring(0, u.start()) + "updatedAt: " + Instant.now() + out.substring(u.end());
        }
        return out;
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void writeReport(Path csv, Summary summary) throws IOException {
        StringBuilder sb = new StringBuilder("key,desc,blocks,index_blocks,assigned,reassigned,status,bytes_before,bytes_after\n");
        for (Row r : summary.rows()) {
            sb.append(csv(r.key())).append(',')
              .append(csv(r.desc())).append(',')
              .append(r.blocks()).append(',')
              .append(r.indexBlocks()).append(',')
              .append(r.assigned()).append(',')
              .append(r.reassigned()).append(',')
              .append(r.status()).append(',')
              .append(r.bytesBefore()).append(',')
              .append(r.bytesAfter()).append('\n');
        }
        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        Files.writeString(csv, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        String v = value == null ? "" : value;
        return v.contains(",") || v.contains("\"") || v.contains("\n")
                ? '"' + v.replace("\"", "\"\"") + '"'
                : v;
    }

    public static void main(String[] args) throws IOException {
        Path notesDir = null;
        Path report = Path.of("target", "kb-id-migration.csv");
        boolean apply = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--notes" -> notesDir = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
                case "--apply" -> apply = true;
                case "--dry-run" -> apply = false;
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }
        if (notesDir == null) {
            System.out.println("用法: BlockIdMigration --notes <notes目录> [--apply] [--report <csv路径>]");
            System.out.println("默认 dry-run，只出报告不改文件。");
            return;
        }

        Summary s = run(notesDir, apply);
        writeReport(report, s);

        System.out.println((apply ? "已落盘" : "dry-run（未改任何笔记）") + " notes=" + notesDir.toAbsolutePath());
        System.out.println("笔记 " + s.notes() + " 篇，待改/已改 " + s.changed() + " 篇，跳过 " + s.skipped() + " 篇");
        System.out.println("知识块 " + s.blocks() + " 个，索引块 " + s.indexBlocks() + " 个");
        System.out.println("新补 ID " + s.assigned() + " 个，重分配 ID " + s.reassigned() + " 个");
        System.out.println("报告: " + report.toAbsolutePath());
        for (Row r : s.rows()) {
            if (r.status().startsWith("ABORT") || r.status().startsWith("skip")) {
                System.out.println("  " + r.status() + ": " + r.key());
            }
            if (r.blocks() == 0 && r.status().equals("would-change")) {
                System.out.println("  警告: 切不出块 " + r.key());
            }
        }
    }
}