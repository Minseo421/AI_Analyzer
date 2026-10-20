package com.example.aichecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class StudyConfiguration {
    public static final String DETECTOR_REVISION = "pr-body-v2";
    private final Properties p;
    StudyConfiguration(Properties p) {
        this.p = new Properties(); this.p.putAll(p);
        require("schema.version", "1"); require("window.field", "closed_at");
        require("window.boundary", "inclusive"); require("ordering", "repository-list,closed_at-desc,number-desc");
        require("bot.rule", BotDetector.REVISION); require("detector.revision", DETECTOR_REVISION);
        require("api.version", "2022-11-28"); require("api.base", "https://api.github.com");
        require("api.accept", "application/vnd.github+json"); require("api.pagination", "closed,updated-desc,all-pages");
        require("api.auth", "environment-token-optional"); require("api.page.size", "100"); require("api.max.attempts", "4");
        require("api.retry.rule", "bounded-exponential-jitter-v1"); require("source.coverage", "PR_BODY_ONLY_V1");
        require("resume.rule", "reuse-successful-responses,retry-failed-repositories");
        if (!get("window.start.utc").endsWith("Z") || !get("window.end.utc").endsWith("Z")) throw new IllegalArgumentException("Study bounds must be UTC instants ending Z");
        if (start().isAfter(end())) throw new IllegalArgumentException("Study start is after end");
        seed("seed.kappa"); seed("seed.validation");
        for (String key : List.of("sample.kappa.per.repository", "sample.validation.per.repository")) {
            if (Integer.parseInt(get(key)) <= 0) throw new IllegalArgumentException("Invalid sample size: " + key);
        }
        for (String key : List.of("api.connect.timeout.seconds", "api.request.timeout.seconds")) {
            if (Integer.parseInt(get(key)) <= 0) throw new IllegalArgumentException("Invalid timeout: " + key);
        }
    }
    static StudyConfiguration load(Path file) throws IOException { return new StudyConfiguration(StudyFiles.read(file)); }
    String get(String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing study configuration: " + key);
        return value;
    }
    String optional(String key, String fallback) { return p.getProperty(key, fallback); }
    private void require(String key, String expected) {
        if (!expected.equals(get(key))) throw new IllegalArgumentException("Unsupported " + key + ": " + get(key) + "; expected " + expected);
    }
    Instant start() { return Instant.parse(get("window.start.utc")); }
    Instant end() { return Instant.parse(get("window.end.utc")); }
    long seed(String key) { return Long.parseLong(get(key)); }
    Properties properties() { Properties copy = new Properties(); copy.putAll(p); return copy; }
    static List<RepoUrl> repositories(Path path) throws IOException {
        var repos = new LinkedHashMap<String, RepoUrl>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            RepoUrl repo = RepoUrl.parse(line.trim());
            String canonical = repo.fullName().toLowerCase(Locale.ROOT);
            repos.putIfAbsent(canonical, RepoUrl.parse(canonical));
        }
        if (repos.isEmpty()) throw new IllegalArgumentException("Empty repository list");
        return List.copyOf(repos.values());
    }
    static String runtimeHash() throws IOException {
        // Hash the full code artifact, including nested detector classes. No Git checkout needed for replay.
        try {
            Path location = Path.of(StudyConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                try (var zip = new java.util.zip.ZipFile(location.toFile())) {
                    var entries = zip.stream().filter(e -> e.getName().startsWith("com/example/aichecker/") && e.getName().endsWith(".class"))
                            .sorted(Comparator.comparing(java.util.zip.ZipEntry::getName)).toList();
                    StringBuilder hashes = new StringBuilder();
                    for (var e : entries) hashes.append(e.getName()).append(':').append(StudyFiles.hash(zip.getInputStream(e).readAllBytes())).append('\n');
                    return StudyFiles.hash(hashes.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            Path packageDir = location.resolve("com/example/aichecker");
            StringBuilder hashes = new StringBuilder();
            for (Path file : StudyFiles.files(packageDir, ".class")) hashes.append("com/example/aichecker/").append(file.getFileName()).append(':').append(StudyFiles.hash(file)).append('\n');
            return StudyFiles.hash(hashes.toString().getBytes(StandardCharsets.UTF_8));
        } catch (java.net.URISyntaxException e) { throw new IOException(e); }
    }
}
