package com.gt.eval;

import com.gt.NoteDto;
import com.gt.config.ConfigKeys;
import com.gt.AppConfig;
import com.gt.kb.chunk.IndexBlocks;
import com.gt.kb.model.KnowledgeBlock;
import com.gt.kb.text.IndexTextBuilder;
import com.gt.storage.NoteFileStorage;
import com.gt.vector.EmbeddingService;
import com.gt.vector.LuceneVectorSearchService;
import com.gt.vector.LuceneVectorSearchService.IndexedBlock;
import com.gt.vector.LuceneVectorSearchService.VectorSearchResult;
import com.gt.vector.VectorSearchFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 检索评测运行器。不是 JUnit 测试，mvn test 不会触发它，必须显式运行：
 *
 * <pre>
 * mvn -B test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" \
 *      -Dstdout.encoding=UTF-8 com.gt.eval.EvalRunner --label v0 --change 基线
 * </pre>
 *
 * 全程在真实笔记的副本上跑：把 notes 和 models 复制进工作目录，再把 user.dir 指过去，
 * 索引、配置、模型都落在工作目录里，绝不读写仓库根的 notes / vector_index / config.properties。
 *
 * 默认数据集是安装目录 {@code FastPig/notes}（应用实际在用、并同步到云端的那份），
 * 不是仓库根的开发快照 {@code notes}；用 {@code --notes} 可显式覆盖。
 * 每次运行都会把「数据集路径 + 篇数 + 最新修改日」写进明细和记录，跨数据集的数字不可直接对比。
 */
public final class EvalRunner {

    private static final int BATCH_FLUSH_SIZE = 50;

    private EvalRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Options o = Options.parse(args, repo);
        o.dataset = describeDataset(repo, o.notes);
        System.out.println("数据集: " + o.dataset);

        prepareWorkDir(o, repo);
        // 必须在 AppConfig / EmbeddingService / VectorSearchFactory 初始化之前改，
        // 它们都用 user.dir 定位 config.properties、models、vector_index
        System.setProperty("user.dir", o.work.toString());

        List<EvalQueries.Query> queries = EvalQueries.load(o.queries);
        System.out.println("评测集: " + queries.size() + " 条  " + o.queries);

        NoteFileStorage storage = new NoteFileStorage(o.work.toString());
        List<NoteDto> notes = storage.scanAllNotes();
        Map<String, Set<String>> titles = titlesByNote(notes);
        validateExpects(queries, titles);

        EmbeddingService.getInstance().initialize();
        if (!EmbeddingService.getInstance().isAvailable()) {
            throw new IllegalStateException("Embedding 服务不可用: " + EmbeddingService.getInstance().getErrorMessage());
        }
        LuceneVectorSearchService svc = VectorSearchFactory.getLuceneService();
        if (svc == null || !svc.isAvailable()) {
            throw new IllegalStateException("Lucene 检索服务不可用（工作目录 config.properties 需含 vector.provider=lucene）");
        }

        boolean rebuild = o.rebuild || svc.getIndexedCount() == 0 || svc.isSchemaOutdated();
        if (rebuild) {
            buildIndex(svc, notes);
        } else {
            System.out.println("复用已有索引: " + svc.getIndexedCount() + " 条（--rebuild 可强制重建）");
        }

        List<EvalMetrics.Outcome> outcomes = new ArrayList<>();
        List<EvalMetrics.Outcome> looseOutcomes = new ArrayList<>();
        List<String> detail = new ArrayList<>();
        long searchNanos = 0;
        for (EvalQueries.Query q : queries) {
            long t0 = System.nanoTime();
            List<VectorSearchResult> results = svc.search(q.text(), o.topK);
            searchNanos += System.nanoTime() - t0;

            List<EvalMetrics.Hit> hits = new ArrayList<>();
            for (VectorSearchResult r : results) {
                hits.add(new EvalMetrics.Hit(r.noteKey, r.h1Title, r.sectionTitle));
            }
            int rank = EvalMetrics.firstHitRank(hits, q.expect(), false);
            int looseRank = EvalMetrics.firstHitRank(hits, q.expect(), true);
            outcomes.add(new EvalMetrics.Outcome(q.id(), q.kind(), rank));
            looseOutcomes.add(new EvalMetrics.Outcome(q.id(), q.kind(), looseRank));
            detail.add(detailOf(q, rank, looseRank, results));
        }

        Map<String, EvalMetrics.Summary> groups = EvalMetrics.groupByKind(outcomes);
        EvalMetrics.Summary looseAll = EvalMetrics.summarize(looseOutcomes);
        printSummary(groups, looseAll, o);
        writeDetail(o, queries.size(), groups, looseAll, detail, searchNanos, svc.getIndexedCount(), rebuild);
        appendRecord(o, groups, looseAll);

        svc.close();
        EmbeddingService.getInstance().close();
    }

    // ==================== 工作目录 ====================

    /**
     * 默认数据集取安装目录 {@code FastPig/notes}——那才是应用实际读写、并同步到云端的一份；
     * 仓库根 {@code notes} 只是开发快照，可能落后数月。没有安装目录时才退回根 notes。
     */
    static Path resolveNotesDir(Path repo) {
        Path installed = repo.resolve("FastPig").resolve("notes");
        return Files.isDirectory(installed) ? installed : repo.resolve("notes");
    }

    /**
     * 数据集身份戳：相对路径 + 篇数 + 最新修改日。
     * 写进明细和记录，避免再出现「拿旧快照的数字当基线、跨数据集对比」而无人察觉。
     */
    static String describeDataset(Path repo, Path dir) throws IOException {
        int count = 0;
        long newest = 0;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(x -> x.getFileName().toString().equals("note.md")).toList()) {
                count++;
                newest = Math.max(newest, Files.getLastModifiedTime(p).toMillis());
            }
        }
        String day = newest == 0 ? "未知"
                : LocalDate.ofInstant(Instant.ofEpochMilli(newest), ZoneId.systemDefault()).toString();
        // 数据集可能在别的盘（临时目录里的副本），relativize 跨根会抛异常，这时只能报绝对路径
        Path from = repo.toAbsolutePath().normalize();
        Path to = dir.toAbsolutePath().normalize();
        String rel = (Objects.equals(from.getRoot(), to.getRoot()) ? from.relativize(to) : to)
                .toString().replace('\\', '/');
        return rel + " " + count + " 篇，最新 " + day;
    }

    private static void prepareWorkDir(Options o, Path repo) throws IOException {
        if (o.work.equals(repo)) {
            throw new IllegalArgumentException("工作目录不能是仓库根目录，否则会覆盖真实 notes");
        }
        if (o.work.resolve("notes").equals(o.notes)) {
            throw new IllegalArgumentException("工作目录里的 notes 不能就是源 notes");
        }
        Files.createDirectories(o.work);

        copyTree(o.notes, o.work.resolve("notes"));
        copyModelIfNeeded(repo.resolve("models"), o.work.resolve("models"));

        // 只写 provider 与显式覆盖项，其余全部走 ConfigKeys 默认值，这样默认值调整能反映到评测里
        StringBuilder config = new StringBuilder("vector.provider=lucene\n");
        o.overrides.forEach((k, v) -> config.append(k).append('=').append(v).append('\n'));
        Files.writeString(o.work.resolve("config.properties"), config.toString(), StandardCharsets.UTF_8);
        System.out.println("工作目录: " + o.work);
        if (!o.overrides.isEmpty()) {
            System.out.println("配置覆盖: " + o.overrides);
        }
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        if (!Files.isDirectory(src)) {
            throw new IOException("源目录不存在: " + src);
        }
        List<Path> paths;
        try (Stream<Path> s = Files.walk(src)) {
            paths = s.sorted(Comparator.naturalOrder()).toList();
        }
        for (Path p : paths) {
            Path target = dst.resolve(src.relativize(p).toString());
            if (Files.isDirectory(p)) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void copyModelIfNeeded(Path src, Path dst) throws IOException {
        if (!Files.isDirectory(src)) {
            throw new IOException("模型目录不存在: " + src + "（先在应用里下载模型）");
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(src)) {
            files = s.filter(Files::isRegularFile).toList();
        }
        for (Path f : files) {
            Path target = dst.resolve(f.getFileName().toString());
            if (Files.exists(target) && Files.size(target) == Files.size(f)) {
                continue;
            }
            Files.createDirectories(dst);
            Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("复制模型: " + f.getFileName());
        }
    }

    // ==================== 索引 ====================

    private static Map<String, Set<String>> titlesByNote(List<NoteDto> notes) {
        Map<String, Set<String>> map = new HashMap<>();
        for (NoteDto n : notes) {
            List<KnowledgeBlock> blocks = IndexBlocks.of(n);
            if (blocks.isEmpty()) {
                continue;
            }
            Set<String> set = map.computeIfAbsent(n.key, k -> new HashSet<>());
            for (KnowledgeBlock b : blocks) {
                // 校验口径必须和命中判定一致：知识块标题与其下小节标题都算合法 expect
                set.add(IndexBlocks.blockTitleOf(b));
                set.add(b.title());
            }
        }
        return map;
    }

    /** expect 写错笔记名或标题会静默变成「未命中」，把基线数据污染掉，所以先硬校验。 */
    private static void validateExpects(List<EvalQueries.Query> queries, Map<String, Set<String>> titles) {
        List<String> problems = new ArrayList<>();
        for (EvalQueries.Query q : queries) {
            for (EvalQueries.Expect e : q.expect()) {
                Set<String> set = titles.get(e.note());
                if (set == null) {
                    problems.add(q.id() + ": 笔记不存在或未进索引 " + e.note());
                } else if (!e.noteLevel() && !set.contains(e.title())) {
                    problems.add(q.id() + ": " + e.note() + " 下没有标题 " + e.title());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("评测集 expect 校验失败:\n  " + String.join("\n  ", problems));
        }
        System.out.println("expect 校验通过，可索引笔记 " + titles.size() + " 篇");
    }

    /** 与 VectorSearchManager.rebuildAllIndex 同一套切分与写入路径，只是同步执行、无 Swing 依赖。 */
    private static void buildIndex(LuceneVectorSearchService svc, List<NoteDto> notes) {
        long t0 = System.currentTimeMillis();
        svc.clearAll();
        List<IndexedBlock> batch = new ArrayList<>();
        int done = 0;
        int skipped = 0;
        for (NoteDto n : notes) {
            List<KnowledgeBlock> blocks = IndexBlocks.of(n);
            if (blocks.isEmpty()) {
                skipped++;
                continue;
            }
            for (KnowledgeBlock b : blocks) {
                float[] vector = EmbeddingService.getInstance().embed(IndexTextBuilder.build(b));
                if (vector != null) {
                    batch.add(new IndexedBlock(n, b, vector));
                }
                if (batch.size() >= BATCH_FLUSH_SIZE) {
                    svc.batchWriteWithVectors(null, batch);
                    batch = new ArrayList<>();
                }
            }
            done++;
            if (done % 20 == 0) {
                System.out.println("  建索引 " + done + "/" + notes.size());
            }
        }
        if (!batch.isEmpty()) {
            svc.batchWriteWithVectors(null, batch);
        }
        svc.markSchemaCurrent();
        System.out.println("索引重建完成: " + svc.getIndexedCount() + " 条，跳过私密/已删除 " + skipped
                + " 篇，耗时 " + (System.currentTimeMillis() - t0) / 1000 + "s");
    }

    // ==================== 输出 ====================

    private static void printSummary(Map<String, EvalMetrics.Summary> groups, EvalMetrics.Summary looseAll, Options o) {
        System.out.println();
        System.out.println("== " + o.label + "  topK=" + o.topK + " ==");
        System.out.printf("%-12s %5s %8s %8s %8s%n", "分组", "条数", "R@5", "R@10", "MRR");
        groups.forEach((kind, s) -> System.out.printf("%-12s %5d %8.3f %8.3f %8.3f%n",
                kind, s.total(), s.recallAt5(), s.recallAt10(), s.mrr()));
        System.out.println("主口径只认知识块标题，与历史各版本可比。");
        System.out.printf("含小节口径（全部）: R@5=%.3f R@10=%.3f MRR=%.3f%n",
                looseAll.recallAt5(), looseAll.recallAt10(), looseAll.mrr());
        System.out.println();
    }

    private static String detailOf(EvalQueries.Query q, int rank, int looseRank, List<VectorSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(q.id()).append("  [").append(q.kind()).append("]  ")
                .append(rank > 0 ? "命中于第 " + rank + " 位" : "未命中");
        if (looseRank != rank) {
            sb.append("（含小节口径：").append(looseRank > 0 ? "第 " + looseRank + " 位" : "仍未命中").append("）");
        }
        sb.append('\n');
        sb.append("- 查询: ").append(q.text()).append('\n');
        sb.append("- 期望: ");
        List<String> parts = new ArrayList<>();
        for (EvalQueries.Expect e : q.expect()) {
            parts.add(e.noteLevel() ? e.note() : e.note() + " / " + e.title());
        }
        sb.append(String.join(" 或 ", parts)).append('\n');
        sb.append("- 实际（全部 ").append(results.size()).append(" 条）:\n");
        for (int i = 0; i < results.size(); i++) {
            VectorSearchResult r = results.get(i);
            String title = r.h1Title == null || r.h1Title.isEmpty() ? "(无标题)" : r.h1Title;
            if (!r.sectionTitle.isEmpty() && !r.sectionTitle.equals(r.h1Title)) {
                title += " > " + r.sectionTitle;
            }
            sb.append("  ").append(i + 1).append(". ").append(r.noteKey).append(" / ").append(title)
                    .append(String.format("  [%.4f %s]", r.score, r.channels)).append('\n');
        }
        if (results.isEmpty()) {
            sb.append("  (无结果)\n");
        }
        return sb.toString();
    }

    private static void writeDetail(Options o, int queryCount, Map<String, EvalMetrics.Summary> groups,
                                    EvalMetrics.Summary looseAll, List<String> detail, long searchNanos,
                                    int indexedCount, boolean rebuilt)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 评测明细 ").append(o.label).append("  ").append(LocalDate.now()).append("\n\n");
        sb.append("- 改动: ").append(o.change).append('\n');
        if (!o.overrides.isEmpty()) {
            sb.append("- 配置覆盖: ").append(o.overrides).append('\n');
        }
        sb.append("- 数据集: ").append(o.dataset).append('\n');
        sb.append("- 查询数: ").append(queryCount).append("，topK=").append(o.topK).append('\n');
        sb.append("- 索引条数: ").append(indexedCount).append(rebuilt ? "（本次重建）" : "（复用）").append('\n');
        sb.append("- 检索总耗时: ").append(searchNanos / 1_000_000).append("ms，平均 ")
                .append(String.format("%.1f", searchNanos / 1_000_000.0 / Math.max(1, queryCount))).append("ms/条\n");
        sb.append("- 生效配置: ");
        List<String> cfg = new ArrayList<>();
        for (ConfigKeys k : new ConfigKeys[]{ConfigKeys.SEARCH_HUMAN_TOPK, ConfigKeys.SEARCH_HUMAN_PER_NOTE,
                ConfigKeys.SEARCH_HUMAN_CANDIDATES, ConfigKeys.SEARCH_RRF_K,
                ConfigKeys.SEARCH_RRF_WEIGHT_EXACT, ConfigKeys.SEARCH_RRF_WEIGHT_BM25,
                ConfigKeys.SEARCH_RRF_WEIGHT_VECTOR, ConfigKeys.SEARCH_EXACT_BOOST,
                ConfigKeys.SEARCH_TITLE_BOOST, ConfigKeys.INDEX_ANALYZER,
                ConfigKeys.INDEX_CONTENT_MAX_CHARS, ConfigKeys.MODEL_MAX_TOKENS}) {
            cfg.add(k.key + "=" + AppConfig.getInstance().getString(k.key));
        }
        sb.append(String.join(", ", cfg)).append("\n\n");
        sb.append("| 分组 | 条数 | R@5 | R@10 | MRR |\n|---|---|---|---|---|\n");
        groups.forEach((kind, s) -> sb.append(String.format("| %s | %d | %.3f | %.3f | %.3f |\n",
                kind, s.total(), s.recallAt5(), s.recallAt10(), s.mrr())));
        sb.append(String.format("%n主口径只认知识块标题。含小节口径（全部）: R@5=%.3f R@10=%.3f MRR=%.3f%n%n",
                looseAll.recallAt5(), looseAll.recallAt10(), looseAll.mrr()));
        for (String d : detail) {
            sb.append(d).append('\n');
        }
        Files.createDirectories(o.detail.getParent());
        Files.writeString(o.detail, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("明细: " + o.detail);
    }

    private static void appendRecord(Options o, Map<String, EvalMetrics.Summary> groups,
                                     EvalMetrics.Summary looseAll) throws IOException {
        EvalMetrics.Summary all = groups.get(EvalMetrics.ALL);
        String date = LocalDate.now().toString();
        String row = String.format("| %s | %s | %s | %.3f | %.3f | %.3f | %d 条，topK=%d；%s；含小节口径 %.3f/%.3f/%.3f |",
                o.label, date, o.change, all.recallAt5(), all.recallAt10(), all.mrr(), all.total(), o.topK, o.dataset,
                looseAll.recallAt5(), looseAll.recallAt10(), looseAll.mrr());

        List<String> lines = Files.exists(o.record)
                ? new ArrayList<>(Files.readAllLines(o.record, StandardCharsets.UTF_8))
                : new ArrayList<>(List.of("# 检索评测记录", "",
                        "每次改切分参数、分词器、模型、融合权重后跑一次 EvalRunner，指标无提升不合入。", "",
                        "| 版本 | 日期 | 改动 | R@5 | R@10 | MRR | 备注 |",
                        "|---|---|---|---|---|---|---|"));

        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| 版本 |")) {
                header = i;
                break;
            }
        }
        if (header < 0) {
            lines.addAll(List.of("", "| 版本 | 日期 | 改动 | R@5 | R@10 | MRR | 备注 |",
                    "|---|---|---|---|---|---|---|"));
            header = lines.size() - 2;
        }
        int insert = header + 1;
        while (insert < lines.size() && lines.get(insert).startsWith("|")) {
            insert++;
        }
        lines.add(insert, row);

        StringBuilder section = new StringBuilder();
        section.append("\n## ").append(o.label).append("（").append(date).append("）\n\n");
        section.append("改动：").append(o.change).append("\n\n");
        section.append("数据集：").append(o.dataset).append("\n\n");
        section.append("| 分组 | 条数 | R@5 | R@10 | MRR |\n|---|---|---|---|---|\n");
        groups.forEach((kind, s) -> section.append(String.format("| %s | %d | %.3f | %.3f | %.3f |\n",
                kind, s.total(), s.recallAt5(), s.recallAt10(), s.mrr())));
        section.append(String.format("%n主口径只认知识块标题，与历史各版本可比；含小节口径（全部）R@5=%.3f R@10=%.3f MRR=%.3f。%n",
                looseAll.recallAt5(), looseAll.recallAt10(), looseAll.mrr()));
        String detailLink = o.record.getParent().relativize(o.detail).toString().replace('\\', '/');
        section.append("\n明细见 `").append(detailLink).append("`。\n");
        lines.add(section.toString());

        Files.createDirectories(o.record.getParent());
        Files.writeString(o.record, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        System.out.println("记录: " + o.record);
    }

    // ==================== 参数 ====================

    private static final class Options {
        Path notes;
        Path work;
        Path queries;
        Path record;
        Path detail;
        String label = "v0";
        String change = "基线";
        String dataset = "";
        int topK = 10;
        boolean rebuild = false;
        /** 写进工作目录 config.properties 的覆盖项，用于分词器选型与融合权重调参。 */
        final Map<String, String> overrides = new LinkedHashMap<>();

        static Options parse(String[] args, Path repo) {
            Options o = new Options();
            Map<String, String> kv = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (!a.startsWith("--")) {
                    throw new IllegalArgumentException("未知参数: " + a);
                }
                String key = a.substring(2);
                if (key.equals("rebuild")) {
                    o.rebuild = true;
                } else if (i + 1 < args.length) {
                    String value = args[++i];
                    if (key.equals("config")) {
                        int eq = value.indexOf('=');
                        if (eq <= 0) {
                            throw new IllegalArgumentException("--config 需要 key=value: " + value);
                        }
                        o.overrides.put(value.substring(0, eq), value.substring(eq + 1));
                    } else {
                        kv.put(key, value);
                    }
                } else {
                    throw new IllegalArgumentException("参数缺少取值: " + a);
                }
            }
            o.notes = Paths.get(kv.getOrDefault("notes", resolveNotesDir(repo).toString())).toAbsolutePath();
            o.work = Paths.get(kv.getOrDefault("work", repo.resolve("target/eval/work").toString())).toAbsolutePath();
            o.queries = Paths.get(kv.getOrDefault("queries",
                    repo.resolve("docs/实施计划/eval/queries.yaml").toString())).toAbsolutePath();
            o.record = Paths.get(kv.getOrDefault("record",
                    repo.resolve("docs/实施计划/评测记录.md").toString())).toAbsolutePath();
            o.label = kv.getOrDefault("label", o.label);
            o.change = kv.getOrDefault("change", o.change);
            o.topK = Integer.parseInt(kv.getOrDefault("topk", String.valueOf(o.topK)));
            o.detail = Paths.get(kv.getOrDefault("detail",
                    repo.resolve("target/eval/" + o.label + "-detail.md").toString())).toAbsolutePath();
            if (o.topK < 10) {
                throw new IllegalArgumentException("--topk 至少 10，否则 R@10 无意义");
            }
            return o;
        }
    }
}