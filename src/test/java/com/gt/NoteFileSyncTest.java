package com.gt;

import com.gt.migration.DataMigration;
import com.gt.service.NoteService;
import com.gt.storage.NoteFileStorage;
import com.gt.sync.NoteFileSync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 笔记文件同步测试
 * 
 * 测试场景：
 * 1. 数据迁移：从数据库导出到文件
 * 2. 笔记保存：同时写入文件和索引
 * 3. 同步上传：增量上传到云端
 * 4. 同步下载：增量下载到本地
 */
public class NoteFileSyncTest {

    public static void main(String[] args) {
        System.out.println("======================================");
        System.out.println("笔记文件同步测试");
        System.out.println("======================================\n");

        try {
            // 初始化
            String basePath = System.getProperty("user.dir");
            NoteRepository repo = new NoteRepository(basePath + "/fastpig.db");
            NoteFileStorage fileStorage = NoteFileStorage.getInstance();
            NoteService noteService = NoteService.getInstance(repo);
            NoteFileSync fileSync = NoteFileSync.getInstance();
            fileSync.setNoteService(noteService);

            // 测试1：数据迁移
            System.out.println("【测试1】数据迁移");
            System.out.println("-".repeat(40));
            DataMigration migration = new DataMigration(repo, fileStorage);
            
            if (migration.needsMigration()) {
                int count = migration.migrate();
                System.out.println("迁移完成: " + count + " 个笔记");
            } else {
                System.out.println("无需迁移，数据已是最新");
            }
            
            // 验证迁移结果
            boolean valid = migration.validateMigration();
            System.out.println("验证结果: " + (valid ? "✅ 通过" : "❌ 失败"));
            System.out.println();

            // 测试2：笔记保存
            System.out.println("【测试2】笔记保存测试");
            System.out.println("-".repeat(40));
            
            // 创建测试笔记
            NoteDto testNote = new NoteDto();
            testNote.id = java.util.UUID.randomUUID().toString();
            testNote.key = "test-sync-" + System.currentTimeMillis();
            testNote.title = "同步测试笔记";
            testNote.desc = "测试文件存储和同步功能";
            testNote.tags = java.util.Arrays.asList("test", "sync");
            testNote.bodyMd = "# 测试笔记\n\n这是一个测试笔记，用于验证文件存储和同步功能。\n\n- 测试项1\n- 测试项2";
            testNote.createdAt = System.currentTimeMillis();
            testNote.updatedAt = System.currentTimeMillis();
            testNote.version = 1;

            noteService.save(testNote);
            System.out.println("已保存测试笔记: " + testNote.key);

            // 验证文件是否创建
            Path noteFolder = fileStorage.getNoteFolderPath(testNote);
            Path noteFile = noteFolder.resolve("note.md");
            if (Files.exists(noteFile)) {
                System.out.println("✅ 文件已创建: " + noteFile);
                System.out.println("   文件大小: " + Files.size(noteFile) + " bytes");
            } else {
                System.out.println("❌ 文件未创建");
            }
            System.out.println();

            // 测试3：从文件加载
            System.out.println("【测试3】从文件加载测试");
            System.out.println("-".repeat(40));
            
            NoteDto loaded = noteService.load(testNote.id);
            if (loaded != null) {
                System.out.println("✅ 加载成功");
                System.out.println("   key: " + loaded.key);
                System.out.println("   title: " + loaded.title);
                System.out.println("   version: " + loaded.version);
                System.out.println("   正文长度: " + (loaded.bodyMd != null ? loaded.bodyMd.length() : 0) + " 字符");
            } else {
                System.out.println("❌ 加载失败");
            }
            System.out.println();

            // 测试4：扫描所有笔记
            System.out.println("【测试4】扫描笔记目录");
            System.out.println("-".repeat(40));
            
            List<NoteDto> allNotes = fileStorage.scanAllNotes();
            System.out.println("发现 " + allNotes.size() + " 个笔记文件夹");
            for (NoteDto note : allNotes) {
                System.out.println("  - " + note.key + " (v" + note.version + ")");
            }
            System.out.println();

            // 测试5：同步上传（如果配置了云存储）
            System.out.println("【测试5】同步上传测试");
            System.out.println("-".repeat(40));
            
            if (fileSync.isEnabled()) {
                boolean uploadOk = fileSync.syncToCloud();
                System.out.println("上传结果: " + (uploadOk ? "✅ 成功" : "❌ 失败"));
            } else {
                System.out.println("⚠️ 云存储未配置，跳过上传测试");
                System.out.println("   请在 config.properties 中配置 nutstore.username 和 nutstore.password");
            }
            System.out.println();

            // 测试6：同步下载
            System.out.println("【测试6】同步下载测试");
            System.out.println("-".repeat(40));
            
            if (fileSync.isEnabled()) {
                boolean downloadOk = fileSync.syncFromCloud();
                System.out.println("下载结果: " + (downloadOk ? "✅ 成功" : "❌ 失败"));
            } else {
                System.out.println("⚠️ 云存储未配置，跳过下载测试");
            }
            System.out.println();

            // 清理测试数据
            System.out.println("【清理】删除测试笔记");
            System.out.println("-".repeat(40));
            noteService.delete(testNote.id);
            System.out.println("已删除测试笔记: " + testNote.key);

            System.out.println("\n======================================");
            System.out.println("测试完成！");
            System.out.println("======================================");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

