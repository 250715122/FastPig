package com.gt;

import java.util.*;

public class SeedAndTest {
    public static void main(String[] args) {
        String db = System.getProperty("user.dir") + "/fastpig.db";
        NoteRepository repo = new NoteRepository(db);

        long now = System.currentTimeMillis();
        // 清晰输出分隔
        System.out.println("=== 开始插入测试数据 ===");

        // 1) Java 片段
        repo.save(note("java-write-file", "写入文件", Arrays.asList("java","io","snippet"),
                "```java\nFile file = new File(\"A:\\\\2.txt\");\nWriter out = new FileWriter(file);\nout.write(\"888\");\nout.close();\n```", now));

        // 2) MySQL 片段
        repo.save(note("mysql-date", "MySQL 日期函数", Arrays.asList("mysql","sql","snippet"),
                "```sql\nselect UNIX_TIMESTAMP(NOW()) * 1000;\n```", now));

        // 3) 会议纪要（无 type，仅标签）
        repo.save(note(null, "数据平台周会", Arrays.asList("meeting","data"),
                "## 决策\n- 引入指标口径表\n\n## 待办\n- 补充埋点", now));

        // 4) 日记
        repo.save(note(null, "2025-09-24 日记", Arrays.asList("diary"),
                "今天完成了迅猪的编辑器与存储改造。", now));

        System.out.println("=== 插入完成 ===\n");

        // 检索验证
        runQuery(repo, "java");
        runQuery(repo, "写入");
        runQuery(repo, "meeting");
        runQuery(repo, "周会");
        runQuery(repo, "mysql");
        runQuery(repo, "日期");
    }

    private static NoteDto note(String key, String title, List<String> tags, String body, long now) {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = key;
        n.title = title;
        n.desc = title;
        n.tags = tags;
        n.bodyMd = body;
        n.frontMatter = null;
        n.createdAt = now;
        n.updatedAt = now;
        n.version = 1;
        return n;
    }

    private static void runQuery(NoteRepository repo, String q) {
        System.out.println("--- 检索: " + q + " ---");
        List<NoteDto> list = repo.searchByKeyOrText(q, 5);
        for (NoteDto n : list) {
            System.out.println("• " + (n.key!=null?n.key+" | ":"") + n.title + " [" + String.join(",", n.tags) + "]");
        }
        if (list.isEmpty()) System.out.println("(无结果)");
        System.out.println();
    }
}


