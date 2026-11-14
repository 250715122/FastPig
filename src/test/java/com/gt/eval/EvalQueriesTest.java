package com.gt.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalQueriesTest {

    private static final String SAMPLE = """
            # 注释行

            - id: q001
              query: 怎么看目录占用空间
              kind: 精确命令
              expect:
                - note: linux
                  title: 目录占用空间统计
            """;

    @Test
    void parsesBlockLevelExpect() {
        List<EvalQueries.Query> qs = EvalQueries.parse(SAMPLE);
        assertEquals(1, qs.size());
        EvalQueries.Query q = qs.get(0);
        assertEquals("q001", q.id());
        assertEquals("怎么看目录占用空间", q.text());
        assertEquals("精确命令", q.kind());
        assertEquals("human", q.profile());
        assertEquals(1, q.expect().size());
        assertEquals("linux", q.expect().get(0).note());
        assertEquals("目录占用空间统计", q.expect().get(0).title());
        assertFalse(q.expect().get(0).noteLevel());
    }

    @Test
    void noteLevelExpectHasNoTitle() {
        List<EvalQueries.Query> qs = EvalQueries.parse("""
                - id: q001
                  query: jvm 内存模型
                  kind: 无标题笔记
                  expect:
                    - note: jvm
                """);
        assertTrue(qs.get(0).expect().get(0).noteLevel());
    }

    @Test
    void multipleExpectsAndExplicitProfile() {
        List<EvalQueries.Query> qs = EvalQueries.parse("""
                - id: q001
                  query: spark 内存
                  kind: 概念
                  profile: ai
                  expect:
                    - note: spark
                      title: 内存调优
                    - note: sparkazbs
                """);
        EvalQueries.Query q = qs.get(0);
        assertEquals("ai", q.profile());
        assertEquals(2, q.expect().size());
        assertEquals("sparkazbs", q.expect().get(1).note());
    }

    @Test
    void multipleQueriesInOneFile() {
        List<EvalQueries.Query> qs = EvalQueries.parse("""
                - id: q001
                  query: 一
                  kind: 概念
                  expect:
                    - note: a
                - id: q002
                  query: 二
                  kind: 步骤
                  expect:
                    - note: b
                """);
        assertEquals(2, qs.size());
        assertEquals("q002", qs.get(1).id());
        assertEquals("步骤", qs.get(1).kind());
    }

    @Test
    void titleContainingColonIsKeptWhole() {
        List<EvalQueries.Query> qs = EvalQueries.parse("""
                - id: q001
                  query: nginx 配置
                  kind: 精确命令
                  expect:
                    - note: nginx
                      title: 配置说明：主文件
                """);
        assertEquals("配置说明：主文件", qs.get(0).expect().get(0).title());
    }

    @Test
    void quotedValuesAreUnquoted() {
        List<EvalQueries.Query> qs = EvalQueries.parse("""
                - id: "q001"
                  query: "带引号的查询"
                  kind: 概念
                  expect:
                    - note: "linux"
                """);
        assertEquals("q001", qs.get(0).id());
        assertEquals("带引号的查询", qs.get(0).text());
        assertEquals("linux", qs.get(0).expect().get(0).note());
    }

    @Test
    void crlfAndBomAreTolerated() {
        String yaml = ((char) 0xFEFF) + SAMPLE.replace("\n", "\r\n");
        assertEquals(1, EvalQueries.parse(yaml).size());
    }

    @Test
    void duplicateIdRejected() {
        String yaml = """
                - id: q001
                  query: 一
                  kind: 概念
                  expect:
                    - note: a
                - id: q001
                  query: 二
                  kind: 概念
                  expect:
                    - note: b
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> EvalQueries.parse(yaml));
        assertTrue(e.getMessage().contains("id 重复"), e.getMessage());
    }

    @Test
    void missingExpectRejected() {
        String yaml = """
                - id: q001
                  query: 一
                  kind: 概念
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> EvalQueries.parse(yaml));
        assertTrue(e.getMessage().contains("缺少 expect"), e.getMessage());
    }

    @Test
    void expectWithoutNoteRejected() {
        String yaml = """
                - id: q001
                  query: 一
                  kind: 概念
                  expect:
                    - title: 只有标题
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> EvalQueries.parse(yaml));
        assertTrue(e.getMessage().contains("缺少 note"), e.getMessage());
    }

    @Test
    void unknownFieldReportsLineNumber() {
        String yaml = """
                - id: q001
                  query: 一
                  kind: 概念
                  weight: 3
                  expect:
                    - note: a
                """;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> EvalQueries.parse(yaml));
        assertTrue(e.getMessage().contains("第 4 行"), e.getMessage());
        assertTrue(e.getMessage().contains("weight"), e.getMessage());
    }

    @Test
    void emptyFileRejected() {
        assertThrows(IllegalArgumentException.class, () -> EvalQueries.parse("# 只有注释\n\n"));
    }

    @Test
    void loadReadsUtf8File(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("queries.yaml");
        Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);
        assertEquals("怎么看目录占用空间", EvalQueries.load(file).get(0).text());
    }
}