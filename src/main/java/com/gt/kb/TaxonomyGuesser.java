package com.gt.kb;

import com.gt.NoteDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新建笔记的分类预猜。
 *
 * 顺序：key/desc 命中词表（含别名）→ 与各 topic 已分类笔记的中心向量比相似度 → 待分类。
 * 向量策略需要 ONNX 推理，调用方应放在后台线程，不要在 EDT 上跑。
 */
public final class TaxonomyGuesser {

    /** 正文只取前 2KB：分类看开头就够，整篇推理既慢又会被后面的细节带偏。 */
    private static final int BODY_HEAD_CHARS = 2048;

    public record Guess(String domain, String topic, String source) {}

    /** 便于测试注入，生产用 EmbeddingService::embed。 */
    public interface Embedder {
        float[] embed(String text);
    }

    private final Taxonomy taxonomy;
    private final Embedder embedder;
    private final double minScore;

    private String centroidSignature;
    private Map<String, float[]> centroids = Map.of();

    public TaxonomyGuesser(Taxonomy taxonomy, Embedder embedder, double minScore) {
        this.taxonomy = taxonomy;
        this.embedder = embedder;
        this.minScore = minScore;
    }

    public static Guess unclassified() {
        return new Guess(NoteDto.DEFAULT_DOMAIN, NoteDto.DEFAULT_TOPIC, "none");
    }

    /** 词面命中：key 是拼音缩写，信号比 desc 强，故先只看 key，再看 desc。 */
    public static Guess matchKeyword(Taxonomy taxonomy, String key, String desc) {
        Guess byKey = matchText(taxonomy, key);
        if (byKey != null) {
            return byKey;
        }
        return matchText(taxonomy, desc);
    }

    private static Guess matchText(Taxonomy taxonomy, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase();
        for (Taxonomy.Domain domain : taxonomy.domains()) {
            for (Taxonomy.Topic topic : domain.topics()) {
                if (contains(lower, topic.name())) {
                    return new Guess(domain.name(), topic.name(), "keyword");
                }
                for (String alias : topic.aliases()) {
                    if (contains(lower, alias)) {
                        return new Guess(domain.name(), topic.name(), "keyword");
                    }
                }
            }
        }
        return null;
    }

    private static boolean contains(String lowerText, String term) {
        return !term.isBlank() && lowerText.contains(term.toLowerCase());
    }

    /**
     * @param classified 已分类笔记（可含未分类/已删除，内部会过滤），正文可为空
     */
    public synchronized Guess guess(String key, String desc, String body, List<NoteDto> classified) {
        Guess keyword = matchKeyword(taxonomy, key, desc);
        if (keyword != null) {
            return keyword;
        }
        if (embedder == null || classified == null || classified.isEmpty()) {
            return unclassified();
        }
        float[] query = embedder.embed(text(key, desc, body));
        if (query == null) {
            return unclassified();
        }

        String signature = signatureOf(classified);
        if (!signature.equals(centroidSignature)) {
            centroids = buildCentroids(classified);
            centroidSignature = signature;
        }

        String bestKey = null;
        double bestScore = minScore;
        for (Map.Entry<String, float[]> e : centroids.entrySet()) {
            double score = cosine(query, e.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestKey = e.getKey();
            }
        }
        if (bestKey == null) {
            return unclassified();
        }
        int sep = bestKey.indexOf('/');
        return new Guess(bestKey.substring(0, sep), bestKey.substring(sep + 1), "centroid");
    }

    private Map<String, float[]> buildCentroids(List<NoteDto> notes) {
        Map<String, List<float[]>> grouped = new LinkedHashMap<>();
        for (NoteDto n : notes) {
            if (n.deleted || n.encrypted || NoteDto.DEFAULT_DOMAIN.equals(n.domain)) {
                continue;
            }
            float[] vec = embedder.embed(text(n.key, n.desc, n.bodyMd));
            if (vec != null) {
                grouped.computeIfAbsent(n.domain + "/" + n.topic, k -> new ArrayList<>()).add(vec);
            }
        }
        Map<String, float[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<float[]>> e : grouped.entrySet()) {
            result.put(e.getKey(), mean(e.getValue()));
        }
        return result;
    }

    /** 笔记集合变化（新增/改分类/改版本）才重算中心向量，否则每次保存都要重跑全量推理。 */
    private static String signatureOf(List<NoteDto> notes) {
        StringBuilder sb = new StringBuilder();
        for (NoteDto n : notes) {
            sb.append(n.id).append(':').append(n.version).append(':')
              .append(n.domain).append('/').append(n.topic).append(';');
        }
        return sb.toString();
    }

    private static String text(String key, String desc, String body) {
        StringBuilder sb = new StringBuilder();
        if (key != null) sb.append(key).append(' ');
        if (desc != null) sb.append(desc).append(' ');
        if (body != null) sb.append(body, 0, Math.min(body.length(), BODY_HEAD_CHARS));
        return sb.toString().strip();
    }

    private static float[] mean(List<float[]> vectors) {
        float[] sum = new float[vectors.get(0).length];
        for (float[] v : vectors) {
            for (int i = 0; i < sum.length && i < v.length; i++) {
                sum[i] += v[i];
            }
        }
        for (int i = 0; i < sum.length; i++) {
            sum[i] /= vectors.size();
        }
        return sum;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}