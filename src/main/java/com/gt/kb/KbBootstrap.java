package com.gt.kb;

import com.gt.NoteDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 存量笔记的知识库元数据预判与报告生成。
 *
 * 只读笔记、只写 CSV 报告，绝不改写 note.md：分类结果要先经人工校对再导入，
 * 直接批量改写存量文件一旦判错就没有回退点。
 *
 * 用法：
 *   KbBootstrap                     扫描 ./notes，生成 kb_bootstrap_report.csv
 *   KbBootstrap --dry-run           只打印统计，不落任何文件
 *   KbBootstrap --notes <dir>       指定笔记目录
 *   KbBootstrap --out <file>        指定报告输出路径
 */
public class KbBootstrap {

    /** 形如 20250113 的独占一行日期，是工作日志的典型特征。 */
    private static final Pattern DATE_LINE = Pattern.compile("^\\s*20\\d{6}\\s*$", Pattern.MULTILINE);
    private static final int RAW_DATE_LINE_THRESHOLD = 3;

    public record Prediction(String layer, String noteType, int dateLines) {}

    public record Row(String key, String desc, long sizeBytes, boolean encrypted,
                      String currentDomain, String currentTopic, Prediction prediction,
                      String suggestedDomain, String suggestedTopic) {}

    public static void main(String[] args) throws IOException {
        boolean dryRun = false;
        Path notesDir = Paths.get(System.getProperty("user.dir"), "notes");
        Path out = Paths.get(System.getProperty("user.dir"), "kb_bootstrap_report.csv");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dry-run" -> dryRun = true;
                case "--notes" -> notesDir = Paths.get(requireValue(args, ++i, "--notes"));
                case "--out" -> out = Paths.get(requireValue(args, ++i, "--out"));
                default -> {
                    System.err.println("未知参数: " + args[i]);
                    System.exit(2);
                }
            }
        }

        if (!Files.isDirectory(notesDir)) {
            System.err.println("笔记目录不存在: " + notesDir);
            System.exit(1);
        }

        List<Row> rows = scan(notesDir, Taxonomy.loadReadOnly(notesDir));
        long raw = rows.stream().filter(r -> NoteDto.LAYER_RAW.equals(r.prediction().layer())).count();
        long classified = rows.stream().filter(r -> !NoteDto.DEFAULT_DOMAIN.equals(r.suggestedDomain())).count();

        System.out.println("扫描笔记: " + rows.size() + " 篇");
        System.out.println("预判 raw/日志: " + raw + " 篇，curated: " + (rows.size() - raw) + " 篇");
        System.out.println("加密笔记: " + rows.stream().filter(Row::encrypted).count() + " 篇（visibility 强制 private）");
        System.out.println("词表命中分类: " + classified + " 篇，待分类: " + (rows.size() - classified) + " 篇");

        if (dryRun) {
            System.out.println("--dry-run：未写出报告");
            return;
        }

        writeReport(out, rows);
        System.out.println("报告已写出: " + out.toAbsolutePath());
        System.out.println("请人工校对 domain/topic 后再导入，本工具不会改写任何 note.md");
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            System.err.println(flag + " 缺少参数值");
            System.exit(2);
        }
        return args[index];
    }

    public static List<Row> scan(Path notesDir, Taxonomy taxonomy) throws IOException {
        List<Path> folders;
        try (Stream<Path> stream = Files.list(notesDir)) {
            folders = stream.filter(Files::isDirectory)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return !name.startsWith(".") && !name.startsWith("_");
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        }

        List<Row> rows = new ArrayList<>();
        for (Path folder : folders) {
            Path noteFile = folder.resolve("note.md");
            if (!Files.exists(noteFile)) {
                continue;
            }
            String content = Files.readString(noteFile, StandardCharsets.UTF_8);
            String key = folder.getFileName().toString();
            String desc = extractField(content, "desc");
            TaxonomyGuesser.Guess guess = TaxonomyGuesser.matchKeyword(taxonomy, key, desc);
            rows.add(new Row(
                key,
                desc,
                Files.size(noteFile),
                extractField(content, "encrypted").equalsIgnoreCase("true"),
                NoteDto.orDefault(extractField(content, "domain"), NoteDto.DEFAULT_DOMAIN),
                NoteDto.orDefault(extractField(content, "topic"), NoteDto.DEFAULT_TOPIC),
                predict(bodyOf(content)),
                guess == null ? NoteDto.DEFAULT_DOMAIN : guess.domain(),
                guess == null ? NoteDto.DEFAULT_TOPIC : guess.topic()
            ));
        }
        return rows;
    }

    /**
     * 日期行达到阈值判为原始日志层，其余一律提炼层。
     * visibility 不在此猜测：保守留 human，由人工决定是否开放给 AI。
     */
    public static Prediction predict(String body) {
        int dateLines = countDateLines(body);
        if (dateLines >= RAW_DATE_LINE_THRESHOLD) {
            return new Prediction(NoteDto.LAYER_RAW, "日志", dateLines);
        }
        return new Prediction(NoteDto.LAYER_CURATED, NoteDto.DEFAULT_NOTE_TYPE, dateLines);
    }

    public static int countDateLines(String body) {
        if (body == null || body.isEmpty()) {
            return 0;
        }
        Matcher m = DATE_LINE.matcher(body);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /** 去掉 front matter，只留正文，避免把元数据里的数字误判成日期行。 */
    public static String bodyOf(String content) {
        if (content == null) {
            return "";
        }
        if (!content.startsWith("---")) {
            return content;
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return content;
        }
        int bodyStart = content.indexOf('\n', end + 4);
        return bodyStart < 0 ? "" : content.substring(bodyStart + 1);
    }

    /** front matter 是简单的 key: value 行，这里只做取值，不引入完整 YAML 解析。 */
    private static String extractField(String content, String field) {
        String prefix = field + ":";
        boolean inFrontMatter = false;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("---")) {
                if (inFrontMatter) {
                    break; // front matter 结束，正文里的同名行不算元数据
                }
                inFrontMatter = true;
                continue;
            }
            if (!inFrontMatter || !trimmed.startsWith(prefix)) {
                continue;
            }
            String value = trimmed.substring(prefix.length()).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return "";
    }

    public static void writeReport(Path out, List<Row> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("key,desc,size_bytes,encrypted,current_domain,current_topic,"
            + "predicted_layer,predicted_note_type,date_lines,suggested_domain,suggested_topic,visibility\n");
        for (Row r : rows) {
            Prediction p = r.prediction();
            // 加密笔记不允许开放给 AI，报告里直接标 private，避免人工校对时误选
            String visibility = r.encrypted() ? NoteDto.VISIBILITY_PRIVATE : NoteDto.DEFAULT_VISIBILITY;
            sb.append(csv(r.key())).append(',')
              .append(csv(r.desc())).append(',')
              .append(r.sizeBytes()).append(',')
              .append(r.encrypted()).append(',')
              .append(csv(r.currentDomain())).append(',')
              .append(csv(r.currentTopic())).append(',')
              .append(csv(p.layer())).append(',')
              .append(csv(p.noteType())).append(',')
              .append(p.dateLines()).append(',')
              .append(csv(r.suggestedDomain())).append(',')
              .append(csv(r.suggestedTopic())).append(',')
              .append(csv(visibility)).append('\n');
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}