package com.gt.eval;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评测数据集解析。此前默认读仓库根的开发快照，导致基线测在过期数据上，
 * 这里锁住「优先安装目录」与「身份戳」两个行为。
 */
public class EvalRunnerTest {

    @TempDir
    Path repo;

    private Path note(Path dir, String key, String day) throws Exception {
        Path f = dir.resolve(key).resolve("note.md");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "# 标题\n\n正文。\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(f, FileTime.from(
                LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return f;
    }

    @Test
    public void prefersInstalledNotes() throws Exception {
        Files.createDirectories(repo.resolve("notes"));
        Files.createDirectories(repo.resolve("FastPig").resolve("notes"));

        assertEquals(repo.resolve("FastPig").resolve("notes"), EvalRunner.resolveNotesDir(repo));
    }

    @Test
    public void fallsBackToRootNotesWithoutInstallDir() throws Exception {
        Files.createDirectories(repo.resolve("notes"));

        assertEquals(repo.resolve("notes"), EvalRunner.resolveNotesDir(repo));
    }

    @Test
    public void emptyInstalledDirStillWins() throws Exception {
        // 安装目录存在但还没同步下笔记：仍应指向它，而不是悄悄退回旧快照
        Files.createDirectories(repo.resolve("notes"));
        Files.createDirectories(repo.resolve("FastPig").resolve("notes"));
        note(repo.resolve("notes"), "old", "2026-08-14");

        assertEquals(repo.resolve("FastPig").resolve("notes"), EvalRunner.resolveNotesDir(repo));
    }

    @Test
    public void stampCountsNotesAndReportsNewestDay() throws Exception {
        Path notes = repo.resolve("FastPig").resolve("notes");
        note(notes, "aaa", "2026-08-14");
        note(notes, "bbb", "2026-11-13");
        note(notes, "ccc", "2026-09-01");
        Files.writeString(notes.resolve(".sync_meta.json"), "{}", StandardCharsets.UTF_8);

        String stamp = EvalRunner.describeDataset(repo, notes);

        assertEquals("FastPig/notes 3 篇，最新 2026-11-13", stamp);
    }

    @Test
    public void stampOfEmptyDirReportsUnknownDay() throws Exception {
        Path notes = repo.resolve("notes");
        Files.createDirectories(notes);

        assertTrue(EvalRunner.describeDataset(repo, notes).endsWith("0 篇，最新 未知"));
    }

    @Test
    public void stampOfDatasetOnAnotherDriveFallsBackToAbsolutePath() throws Exception {
        Path notes = Files.createTempDirectory("fp-eval-stamp-");
        try {
            note(notes, "aaa", "2026-11-13");
            Path otherRoot = otherRootThan(notes);
            Assumptions.assumeTrue(otherRoot != null, "只有一个根目录，构造不出跨盘数据集");

            // 评测副本常放在系统临时目录，仓库在另一个盘；relativize 跨根会抛异常。
            // repo 只用于算相对路径，不需要真实存在。
            String stamp = EvalRunner.describeDataset(otherRoot.resolve("repo"), notes);

            assertTrue(stamp.startsWith(notes.toString().replace('\\', '/')), stamp);
            assertTrue(stamp.endsWith("1 篇，最新 2026-11-13"), stamp);
        } finally {
            try (Stream<Path> s = Files.walk(notes)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    private static Path otherRootThan(Path p) {
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            if (!Objects.equals(root, p.getRoot())) {
                return root;
            }
        }
        return null;
    }
}