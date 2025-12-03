package com.gt.migration;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.storage.NoteFileStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 数据迁移工具
 * 将现有 SQLite 数据库中的笔记导出为 Markdown 文件
 * 
 * 迁移流程：
 * 1. 从 fastpig.db 读取所有笔记（包括已删除的）
 * 2. 为每个笔记创建文件夹和 note.md 文件
 * 3. 更新 SQLite 索引（添加 folder_path，可选清空 body_md）
 * 
 * 迁移是幂等的，可以多次执行
 */
public class DataMigration {

    private final NoteRepository repository;
    private final NoteFileStorage fileStorage;
    private final boolean clearBodyAfterMigration;

    public DataMigration(NoteRepository repository, NoteFileStorage fileStorage) {
        this(repository, fileStorage, false);
    }

    public DataMigration(NoteRepository repository, NoteFileStorage fileStorage, boolean clearBodyAfterMigration) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.clearBodyAfterMigration = clearBodyAfterMigration;
    }

    /**
     * 执行数据迁移
     * @return 迁移的笔记数量
     */
    public int migrate() {
        System.out.println("[DataMigration] 开始数据迁移...");
        System.out.println("[DataMigration] 数据库路径: " + repository.getDbPath());
        System.out.println("[DataMigration] 笔记目录: " + fileStorage.getNotesDir());

        // 获取所有笔记
        List<NoteDto> allNotes = repository.findAll();
        System.out.println("[DataMigration] 发现 " + allNotes.size() + " 个笔记需要迁移");

        int migratedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (NoteDto note : allNotes) {
            try {
                if (migrateNote(note)) {
                    migratedCount++;
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                errorCount++;
                System.err.println("[DataMigration] 迁移笔记失败: " + note.key + " - " + e.getMessage());
            }
        }

        System.out.println("[DataMigration] 迁移完成:");
        System.out.println("  - 成功迁移: " + migratedCount);
        System.out.println("  - 跳过: " + skippedCount);
        System.out.println("  - 失败: " + errorCount);

        return migratedCount;
    }

    /**
     * 迁移单个笔记
     * @return true 如果执行了迁移，false 如果跳过
     */
    private boolean migrateNote(NoteDto note) {
        // 检查是否已经迁移过
        if (note.folderPath != null && !note.folderPath.isEmpty()) {
            Path existingFolder = Path.of(note.folderPath);
            if (Files.exists(existingFolder.resolve("note.md"))) {
                // 已经存在文件，跳过
                return false;
            }
        }

        // 检查是否有正文内容
        if (note.bodyMd == null || note.bodyMd.isEmpty()) {
            System.out.println("[DataMigration] 跳过空笔记: " + note.key);
            return false;
        }

        System.out.println("[DataMigration] 迁移笔记: " + note.key + " (id=" + note.id + ")");

        // 保存到文件
        fileStorage.saveToFile(note);

        // 更新索引中的 folder_path
        Path folderPath = fileStorage.getNoteFolderPath(note);
        note.folderPath = folderPath.toString();
        repository.saveIndex(note);

        // 可选：清空数据库中的 body_md
        if (clearBodyAfterMigration) {
            repository.clearBodyMd(note.id);
        }

        return true;
    }

    /**
     * 检查是否需要迁移
     * @return true 如果存在需要迁移的数据
     */
    public boolean needsMigration() {
        List<NoteDto> allNotes = repository.findAll();
        
        for (NoteDto note : allNotes) {
            // 如果有笔记没有 folderPath，说明需要迁移
            if (note.folderPath == null || note.folderPath.isEmpty()) {
                if (note.bodyMd != null && !note.bodyMd.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 自动迁移（如果需要）
     * @return 迁移的笔记数量，0 表示不需要迁移
     */
    public int migrateIfNeeded() {
        if (needsMigration()) {
            System.out.println("[DataMigration] 检测到需要迁移的数据");
            return migrate();
        } else {
            System.out.println("[DataMigration] 无需迁移");
            return 0;
        }
    }

    /**
     * 验证迁移结果
     * @return true 如果所有笔记都已成功迁移
     */
    public boolean validateMigration() {
        List<NoteDto> allNotes = repository.findAll();
        int validCount = 0;
        int invalidCount = 0;

        for (NoteDto note : allNotes) {
            if (note.bodyMd == null || note.bodyMd.isEmpty()) {
                // 空笔记，跳过验证
                continue;
            }

            if (note.folderPath == null || note.folderPath.isEmpty()) {
                invalidCount++;
                System.out.println("[DataMigration] 未迁移: " + note.key);
                continue;
            }

            Path noteFile = Path.of(note.folderPath).resolve("note.md");
            if (Files.exists(noteFile)) {
                validCount++;
            } else {
                invalidCount++;
                System.out.println("[DataMigration] 文件不存在: " + noteFile);
            }
        }

        System.out.println("[DataMigration] 验证结果: " + validCount + " 有效, " + invalidCount + " 无效");
        return invalidCount == 0;
    }

    /**
     * 回滚迁移（从文件恢复到数据库）
     * 用于紧急回退
     */
    public int rollback() {
        System.out.println("[DataMigration] 开始回滚迁移...");

        List<NoteDto> fileNotes = fileStorage.scanAllNotes();
        int rolledBack = 0;

        for (NoteDto note : fileNotes) {
            try {
                // 保存完整笔记（包含正文）到数据库
                repository.save(note);
                rolledBack++;
            } catch (Exception e) {
                System.err.println("[DataMigration] 回滚失败: " + note.key + " - " + e.getMessage());
            }
        }

        System.out.println("[DataMigration] 回滚完成: " + rolledBack + " 个笔记");
        return rolledBack;
    }
}

