package com.gt.kb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 受控分类词表。文件位于 notes/_kb/taxonomy.yaml，随笔记一起云同步。
 *
 * 只解析本项目用到的 YAML 子集（version / domains / name / topics / aliases），
 * 不引入 YAML 依赖；无法识别的行直接报错，避免词表写错后静默丢分类。
 */
public final class Taxonomy {

    private static final Logger logger = LogManager.getLogger(Taxonomy.class);

    public static final String KB_DIR = "_kb";
    public static final String FILE_NAME = "taxonomy.yaml";
    private static final String DEFAULT_RESOURCE = "/kb/taxonomy.default.yaml";

    public record Topic(String name, List<String> aliases) {
        public Topic {
            aliases = List.copyOf(aliases);
        }
    }

    public record Domain(String name, List<Topic> topics) {
        public Domain {
            topics = List.copyOf(topics);
        }
    }

    private final int version;
    private final List<Domain> domains;

    private Taxonomy(int version, List<Domain> domains) {
        this.version = version;
        this.domains = List.copyOf(domains);
    }

    public int version() {
        return version;
    }

    public List<Domain> domains() {
        return domains;
    }

    public List<String> domainNames() {
        return domains.stream().map(Domain::name).toList();
    }

    public List<String> topicNames(String domain) {
        for (Domain d : domains) {
            if (d.name().equals(domain)) {
                return d.topics().stream().map(Topic::name).toList();
            }
        }
        return List.of();
    }

    public boolean contains(String domain, String topic) {
        return topicNames(domain).contains(topic);
    }

    /**
     * 读取 notes/_kb/taxonomy.yaml；不存在时用内置模板落地一份。
     * 词表损坏不能让应用起不来，因此解析失败时退回内置模板并告警。
     */
    public static Taxonomy loadOrDefault(Path notesDir) {
        Path file = notesDir.resolve(KB_DIR).resolve(FILE_NAME);
        if (!Files.exists(file)) {
            try {
                materializeDefault(file);
            } catch (IOException e) {
                logger.warn("[Taxonomy] 词表落地失败，使用内置默认: {} - {}", file, e.getMessage());
            }
        }
        return loadReadOnly(notesDir);
    }

    /**
     * 只读加载：文件缺失或损坏都退回内置词表，绝不落盘。
     * KbBootstrap 这类承诺「不改任何文件」的工具必须用它，而不是 loadOrDefault。
     */
    public static Taxonomy loadReadOnly(Path notesDir) {
        Path file = notesDir.resolve(KB_DIR).resolve(FILE_NAME);
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.warn("[Taxonomy] 词表不可用，使用内置默认: {} - {}", file, e.getMessage());
            return parse(defaultText());
        }
    }

    private static void materializeDefault(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
        try {
            Files.writeString(tmp, defaultText(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String defaultText() {
        try (InputStream in = Taxonomy.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("缺少内置词表资源: " + DEFAULT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取内置词表失败", e);
        }
    }

    public static Taxonomy parse(String text) {
        int version = 1;
        List<Domain> domains = new ArrayList<>();
        String name = null;
        List<String> topics = new ArrayList<>();
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        boolean inAliases = false;

        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("- name:")) {
                if (name != null) {
                    domains.add(buildDomain(name, topics, aliases));
                }
                name = unquote(value(line.substring("- name:".length())));
                topics = new ArrayList<>();
                aliases = new LinkedHashMap<>();
                inAliases = false;
            } else if (line.startsWith("topics:")) {
                topics = parseList(value(line.substring("topics:".length())));
                inAliases = false;
            } else if (line.equals("aliases:")) {
                inAliases = true;
            } else if (line.startsWith("version:")) {
                version = Integer.parseInt(value(line.substring("version:".length())));
            } else if (line.equals("domains:")) {
                inAliases = false;
            } else if (inAliases && line.contains(":")) {
                int colon = line.indexOf(':');
                aliases.put(unquote(line.substring(0, colon).strip()),
                        parseList(value(line.substring(colon + 1))));
            } else {
                throw new IllegalArgumentException("无法识别的词表行: " + raw);
            }
        }
        if (name != null) {
            domains.add(buildDomain(name, topics, aliases));
        }
        if (domains.isEmpty()) {
            throw new IllegalArgumentException("词表没有任何 domain");
        }
        return new Taxonomy(version, domains);
    }

    private static Domain buildDomain(String name, List<String> topics, Map<String, List<String>> aliases) {
        if (name.isEmpty() || topics.isEmpty()) {
            throw new IllegalArgumentException("domain 缺少 name 或 topics: " + name);
        }
        List<Topic> list = new ArrayList<>();
        for (String t : topics) {
            list.add(new Topic(t, aliases.getOrDefault(t, List.of())));
        }
        return new Domain(name, list);
    }

    private static String value(String s) {
        return unquote(s.strip());
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static List<String> parseList(String s) {
        String body = s.strip();
        if (body.startsWith("[") && body.endsWith("]")) {
            body = body.substring(1, body.length() - 1);
        }
        List<String> out = new ArrayList<>();
        for (String item : body.split(",")) {
            String v = unquote(item.strip());
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }
}