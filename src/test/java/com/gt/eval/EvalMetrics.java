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

    /**
     * 一条检索结果，按排名顺序传入。
     * h1Title 是所属知识块标题，sectionTitle 是索引段自身的小标题（超长块二次切分才有区别）。
     */
    public record Hit(String noteKey, String h1Title, String sectionTitle) {
    }

    /** rank 从 1 开始，0 表示 topK 内未命中。 */
    public record Outcome(String id, String kind, int rank) {
    }

    public record Summary(int total, int hitAt5, int hitAt10, double recallAt5, double recallAt10, double mrr) {
    }

    public static final String ALL = "全部";

    private EvalMetrics() {
    }

    /**
     * rank 从 1 开始，0 表示 topK 内未命中。
     *
     * allowSection=false 是主口径：expect 标题只跟知识块标题比，与历史各版本可比。
     * allowSection=true 额外认块内小节标题，用来暴露「正文其实找对了、只是标题显示成父块」的情况。
     * 两个口径必须同时报，只报宽口径会把口径变更冒充成检索提升。
     */
    public static int firstHitRank(List<Hit> ranked, List<EvalQueries.Expect> expects, boolean allowSection) {
        for (int i = 0; i < ranked.size(); i++) {
            Hit hit = ranked.get(i);
            for (EvalQueries.Expect e : expects) {
                if (matches(hit, e, allowSection)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    /** 宽口径下 expect 指名知识块或其下任一小节都算命中：定位到块内小节同样满足「找到这块知识」。 */
    private static boolean matches(Hit hit, EvalQueries.Expect e, boolean allowSection) {
        if (hit.noteKey() == null || !hit.noteKey().equals(e.note())) {
            return false;
        }
        return e.noteLevel() || e.title().equals(hit.h1Title())
                || (allowSection && e.title().equals(hit.sectionTitle()));
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