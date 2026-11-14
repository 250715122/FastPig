package com.gt.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalMetricsTest {

    private static EvalQueries.Expect block(String note, String title) {
        return new EvalQueries.Expect(note, title);
    }

    private static EvalQueries.Expect note(String note) {
        return new EvalQueries.Expect(note, null);
    }

    private static EvalMetrics.Hit hit(String note, String title) {
        return new EvalMetrics.Hit(note, title);
    }

    @Test
    void rankIsOneBased() {
        List<EvalMetrics.Hit> ranked = List.of(hit("a", "x"), hit("linux", "占用空间"));
        assertEquals(2, EvalMetrics.firstHitRank(ranked, List.of(block("linux", "占用空间"))));
    }

    @Test
    void noteLevelExpectMatchesAnyBlock() {
        List<EvalMetrics.Hit> ranked = List.of(hit("jvm", ""));
        assertEquals(1, EvalMetrics.firstHitRank(ranked, List.of(note("jvm"))));
    }

    @Test
    void noteNameBlockDoesNotSatisfyTitleExpect() {
        List<EvalMetrics.Hit> ranked = List.of(hit("linux", "__NOTE_NAME__"));
        assertEquals(0, EvalMetrics.firstHitRank(ranked, List.of(block("linux", "占用空间"))));
        assertEquals(1, EvalMetrics.firstHitRank(ranked, List.of(note("linux"))));
    }

    @Test
    void titleMustMatchExactly() {
        List<EvalMetrics.Hit> ranked = List.of(hit("linux", "占用空间"));
        assertEquals(0, EvalMetrics.firstHitRank(ranked, List.of(block("linux", "占用"))));
        assertEquals(0, EvalMetrics.firstHitRank(ranked, List.of(block("linux", "占用空间 "))));
    }

    @Test
    void wrongNoteNeverMatches() {
        List<EvalMetrics.Hit> ranked = List.of(hit("ubuntu", "占用空间"));
        assertEquals(0, EvalMetrics.firstHitRank(ranked, List.of(block("linux", "占用空间"))));
    }

    @Test
    void expectsAreAlternatives() {
        List<EvalMetrics.Hit> ranked = List.of(hit("a", "x"), hit("b", "y"));
        assertEquals(2, EvalMetrics.firstHitRank(ranked, List.of(block("c", "z"), block("b", "y"))));
    }

    @Test
    void missIsZero() {
        assertEquals(0, EvalMetrics.firstHitRank(List.of(), List.of(note("a"))));
        assertEquals(0, EvalMetrics.firstHitRank(List.of(hit("a", "x")), List.of(note("b"))));
    }

    @Test
    void summaryComputesRecallAndMrr() {
        List<EvalMetrics.Outcome> outcomes = List.of(
                new EvalMetrics.Outcome("q1", "概念", 1),
                new EvalMetrics.Outcome("q2", "概念", 6),
                new EvalMetrics.Outcome("q3", "概念", 0));
        EvalMetrics.Summary s = EvalMetrics.summarize(outcomes);
        assertEquals(3, s.total());
        assertEquals(1, s.hitAt5());
        assertEquals(2, s.hitAt10());
        assertEquals(1.0 / 3, s.recallAt5(), 1e-9);
        assertEquals(2.0 / 3, s.recallAt10(), 1e-9);
        assertEquals((1.0 + 1.0 / 6) / 3, s.mrr(), 1e-9);
    }

    @Test
    void cutoffsAreInclusive() {
        EvalMetrics.Summary s = EvalMetrics.summarize(List.of(
                new EvalMetrics.Outcome("q5", "概念", 5),
                new EvalMetrics.Outcome("q10", "概念", 10),
                new EvalMetrics.Outcome("q11", "概念", 11)));
        assertEquals(1.0 / 3, s.recallAt5(), 1e-9);
        assertEquals(2.0 / 3, s.recallAt10(), 1e-9);
    }

    @Test
    void emptySummaryIsZeroNotNaN() {
        EvalMetrics.Summary s = EvalMetrics.summarize(List.of());
        assertEquals(0, s.total());
        assertEquals(0.0, s.recallAt5(), 1e-9);
        assertEquals(0.0, s.mrr(), 1e-9);
    }

    @Test
    void groupByKindPutsAllFirstAndKeepsOrder() {
        List<EvalMetrics.Outcome> outcomes = List.of(
                new EvalMetrics.Outcome("q1", "步骤", 1),
                new EvalMetrics.Outcome("q2", "概念", 0),
                new EvalMetrics.Outcome("q3", "步骤", 3));
        Map<String, EvalMetrics.Summary> groups = EvalMetrics.groupByKind(outcomes);
        assertEquals(List.of(EvalMetrics.ALL, "步骤", "概念"), List.copyOf(groups.keySet()));
        assertEquals(3, groups.get(EvalMetrics.ALL).total());
        assertEquals(2, groups.get("步骤").total());
        assertEquals(1.0, groups.get("步骤").recallAt5(), 1e-9);
        assertEquals(0.0, groups.get("概念").mrr(), 1e-9);
        assertEquals(1, groups.get("概念").total());
    }
}