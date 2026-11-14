package com.gt.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测指标。每条查询只有一个正确答案（expect 内多项是「或」关系），
 * 所以 Recall@k 等于「前 k 条里命中的查询占比」，MRR 取首个命中排名的倒数。
 */
public final class EvalMetrics {

    /** 一条检索结果，按排名顺序传入。 */
    public record Hit(String noteKey, String h1Title) {
    }

    /** rank 从 1 开始，0 表示 topK 内未命中。 */
    public record Outcome(String id, String kind, int rank) {
    }

    public record Summary(int total, int hitAt5, int hitAt10, double recallAt5, double recallAt10, double mrr) {
    }

    public static final String ALL = "全部";

    private EvalMetrics() {
    }

    public static int firstHitRank(List<Hit> ranked, List<EvalQueries.Expect> expects) {
        for (int i = 0; i < ranked.size(); i++) {
            Hit hit = ranked.get(i);
            for (EvalQueries.Expect e : expects) {
                if (matches(hit, e)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    private static boolean matches(Hit hit, EvalQueries.Expect e) {
        if (hit.noteKey() == null || !hit.noteKey().equals(e.note())) {
            return false;
        }
        return e.noteLevel() || e.title().equals(hit.h1Title());
    }

    public static Summary summarize(List<Outcome> outcomes) {
        int total = outcomes.size();
        if (total == 0) {
            return new Summary(0, 0, 0, 0, 0, 0);
        }
        int hit5 = 0;
        int hit10 = 0;
        double reciprocalSum = 0;
        for (Outcome o : outcomes) {
            if (o.rank() > 0) {
                reciprocalSum += 1.0 / o.rank();
                if (o.rank() <= 5) {
                    hit5++;
                }
                if (o.rank() <= 10) {
                    hit10++;
                }
            }
        }
        return new Summary(total, hit5, hit10, (double) hit5 / total, (double) hit10 / total, reciprocalSum / total);
    }

    /** 第一组是全部，其余按 kind 首次出现的顺序排列，便于跨版本对比同一张表。 */
    public static Map<String, Summary> groupByKind(List<Outcome> outcomes) {
        Map<String, List<Outcome>> groups = new LinkedHashMap<>();
        groups.put(ALL, outcomes);
        for (Outcome o : outcomes) {
            groups.computeIfAbsent(o.kind(), k -> new ArrayList<>()).add(o);
        }
        Map<String, Summary> result = new LinkedHashMap<>();
        groups.forEach((kind, list) -> result.put(kind, summarize(list)));
        return result;
    }
}