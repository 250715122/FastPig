package com.gt;

import org.junit.Test;
import java.io.File;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

public class NoteRepositoryTest {
    @Test
    public void testSaveAndSearch() {
        String db = System.getProperty("user.dir") + "/test-fastpig.db";
        new File(db).delete();
        NoteRepository repo = new NoteRepository(db);

        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = "unittest";
        n.title = "单元测试标题";
        n.desc = "单元测试摘要";
        n.tags = Arrays.asList("test", "java");
        n.bodyMd = "# Hello\n这是测试内容";
        n.frontMatter = null;
        long now = System.currentTimeMillis();
        n.createdAt = now;
        n.updatedAt = now;
        n.version = 1;

        repo.save(n);

        assertTrue(repo.searchByKeyOrText("unittest", 10).size() >= 1);
        assertTrue(repo.searchByKeyOrText("测试", 10).size() >= 1);
    }
}


