package com.gt.kb;

import com.gt.NoteDto;
import com.gt.NoteRepository;
import com.gt.kb.id.BlockIdAssigner;
import com.gt.service.NoteService;
import com.gt.storage.NoteFileStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把分类报告导入存量笔记。{@link KbBootstrap} 只出报告、承诺不改 note.md，
 * 导入是独立一步，人工校对留在中间。
 *
 * 只填当前「待分类」的笔记，不覆盖已有分类：报告里的建议来自词面匹配，
 * 拿它盖掉人工设过的分类是倒退。要改已分类的笔记走 GUI 分类对话框。
 * 建议本身是「待分类」的也跳过，留给人工。
 *
 * 用法：
 *   KbClassifyApply --report <csv> --base <安装目录>           dry-run，只打印将改什么
 *   KbClassifyApply --report <csv> --base <安装目录> --apply    落盘
 */
public class KbClassifyApply {

    public record Suggestion(String domain, String topic) {}

    public record Change(String key, String oldDomain, String oldTopic, String domain, String topic) {}

    /** skipped* 分开计数，dry-run 时能看清「为什么这篇没被改」。 */
    public record Plan(List<Change> changes, int alreadyClassified, int noSuggestion,
                       int sameAsCurrent, int encrypted, int deleted) {
        public Plan {
            changes = List.copyOf(changes);
        }
    }

    public record Summary(int scanned, Plan plan, int applied, int failed, int bodyChanged,
                          int whitespaceTrimmed, int idsAssigned) {}

    public static void main(String[] args) throws IOException {
        Path report = null;
        Path base = Paths.get(System.getProperty("user.dir"));
        boolean apply = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--report" -> report = Paths.get(requireValue(args, ++i, "--report"));
                case "--base" -> base = Paths.get(requireValue(args, ++i, "--base"));
                case "--apply" -> apply = true;
                default -> {
                    System.err.println("未知参数: " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (report == null) {
            System.err.println("必须指定 --report <csv>（KbBootstrap 生成的报告，可人工校对后再导入）");
            System.exit(2);
        }
        if (!Files.isRegularFile(report)) {
            System.err.println("报告文件不存在: " + report);
            System.exit(1);
        }
        if (!Files.isDirectory(base.resolve("notes"))) {
            System.err.println("笔记目录不存在: " + base.resolve("notes"));
            System.exit(1);
        }

        // AppConfig / 模型 / 索引都按 user.dir 定位，指到安装目录才读得到那份 config.properties
        System.setProperty("user.dir", base.toAbsolutePath().toString());

        Summary s = run(base, report, apply);
        Plan p = s.plan();
        System.out.println("扫描笔记: " + s.scanned() + " 篇");
        System.out.println("计划改动: " + p.changes().size() + " 篇");
        System.out.println("跳过 - 已有分类: " + p.alreadyClassified()
                + "，报告无此 key: " + p.noSuggestion()
                + "，与现状相同: " + p.sameAsCurrent()
                + "，加密: " + p.encrypted()
                + "，已删除: " + p.deleted());
        for (Change c : p.changes()) {
            System.out.println("  " + c.key() + ": " + c.oldDomain() + "/" + c.oldTopic()
                    + " -> " + c.domain() + "/" + c.topic());
        }
        if (!apply) {
            System.out.println("dry-run：未改动任何文件。确认无误后加 --apply 落盘。");
            return;
        }
        System.out.println("已落盘: " + s.applied() + " 篇，失败: " + s.failed() + " 篇");
        System.out.println("正文文字被改动: " + s.bodyChanged() + " 篇（应为 0），新补/重分配块 ID: "
                + s.idsAssigned() + " 个（应为 0）");
        System.out.println("正文首尾空白被存储层规范化: " + s.whitespaceTrimmed() + " 篇（不影响文字内容）");
        if (s.failed() > 0 || s.bodyChanged() > 0) {
            System.exit(1);
        }
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            System.err.println(flag + " 缺少参数值");
            System.exit(2);
        }
        return args[index];
    }

    public static Summary run(Path base, Path reportFile, boolean apply) throws IOException {
        Map<String, Suggestion> report = readReport(reportFile);
        NoteRepository repository = new NoteRepository(base.resolve("fastpig.db").toString());
        NoteFileStorage storage = new NoteFileStorage(base.toString());
        NoteService service = new NoteService(repository, storage);
        try {
            List<NoteDto> notes = storage.scanAllNotes();
            Plan plan = plan(notes, report);
            if (!apply) {
                return new Summary(notes.size(), plan, 0, 0, 0, 0, 0);
            }

            Map<String, NoteDto> byKey = new LinkedHashMap<>();
            for (NoteDto n : notes) {
                byKey.put(n.key, n);
            }

            int applied = 0;
            int failed = 0;
            int bodyChanged = 0;
            int whitespaceTrimmed = 0;
            int idsAssigned = 0;
            for (Change c : plan.changes()) {
                NoteDto note = byKey.get(c.key());
                Path file = storage.findNoteFileByKey(c.key());
                String bodyBefore = fileBody(file);
                note.domain = c.domain();
                note.topic = c.topic();
                try {
                    BlockIdAssigner.Result ids = service.save(note);
                    idsAssigned += ids.assigned() + ids.reassigned();
                    // 必须比文件而不是内存 bodyMd：loadFromFile 已经去掉末尾换行，
                    // 拿内存比会漏掉存储层在落盘时做的首尾空白规范化。
                    String bodyAfter = fileBody(file);
                    if (!bodyBefore.strip().equals(bodyAfter.strip())) {
                        bodyChanged++;
                        System.err.println("正文文字被改动: " + c.key());
                    } else if (!bodyBefore.equals(bodyAfter)) {
                        whitespaceTrimmed++;
                    }
                    applied++;
                } catch (RuntimeException | IOException e) {
                    failed++;
                    System.err.println("落盘失败: " + c.key() + " - " + e.getMessage());
                }
            }
            return new Summary(notes.size(), plan, applied, failed, bodyChanged, whitespaceTrimmed, idsAssigned);
        } finally {
            service.close();
        }
    }

    /** 取文件里的正文区（front matter 之后），读不到时返回空串以便比对时暴露差异。 */
    private static String fileBody(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return content.substring(NoteFileStorage.bodyOffset(content));
    }

    /** 纯函数：只算「该改哪些」，不碰磁盘，便于单测。 */
    public static Plan plan(List<NoteDto> notes, Map<String, Suggestion> report) {
        List<Change> changes = new ArrayList<>();
        int alreadyClassified = 0;
        int noSuggestion = 0;
        int sameAsCurrent = 0;
        int encrypted = 0;
        int deleted = 0;

        for (NoteDto n : notes) {
            if (n.deleted) {
                deleted++;
                continue;
            }
            if (n.encrypted) {
                encrypted++;
                continue;
            }
            String domain = NoteDto.orDefault(n.domain, NoteDto.DEFAULT_DOMAIN);
            String topic = NoteDto.orDefault(n.topic, NoteDto.DEFAULT_TOPIC);
            if (!NoteDto.DEFAULT_DOMAIN.equals(domain)) {
                alreadyClassified++;
                continue;
            }
            Suggestion s = report.get(n.key);
            if (s == null) {
                noSuggestion++;
                continue;
            }
            if (NoteDto.DEFAULT_DOMAIN.equals(s.domain())) {
                noSuggestion++;
                continue;
            }
            if (s.domain().equals(domain) && s.topic().equals(topic)) {
                sameAsCurrent++;
                continue;
            }
            changes.add(new Change(n.key, domain, topic, s.domain(), s.topic()));
        }
        return new Plan(changes, alreadyClassified, noSuggestion, sameAsCurrent, encrypted, deleted);
    }

    /** 按表头取列，不依赖列顺序；desc 里可能带逗号，必须按 CSV 引号规则解析。 */
    public static Map<String, Suggestion> readReport(Path csv) throws IOException {
        List<List<String>> rows = parseCsv(Files.readString(csv, StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new IOException("报告为空: " + csv);
        }
        List<String> header = rows.get(0);
        int keyIdx = header.indexOf("key");
        int domainIdx = header.indexOf("suggested_domain");
        int topicIdx = header.indexOf("suggested_topic");
        if (keyIdx < 0 || domainIdx < 0 || topicIdx < 0) {
            throw new IOException("报告缺少 key/suggested_domain/suggested_topic 列: " + csv);
        }

        Map<String, Suggestion> out = new LinkedHashMap<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row.size() <= Math.max(keyIdx, Math.max(domainIdx, topicIdx))) {
                continue;
            }
            String key = row.get(keyIdx).trim();
            if (key.isEmpty()) {
                continue;
            }
            String domain = row.get(domainIdx).trim();
            String topic = row.get(topicIdx).trim();
            out.put(key, new Suggestion(
                    domain.isEmpty() ? NoteDto.DEFAULT_DOMAIN : domain,
                    topic.isEmpty() ? NoteDto.DEFAULT_TOPIC : topic));
        }
        return out;
    }

    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}