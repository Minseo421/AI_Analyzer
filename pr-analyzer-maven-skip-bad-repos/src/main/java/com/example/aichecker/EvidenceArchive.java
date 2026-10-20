package com.example.aichecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Content-addressed response bodies plus URL/accept/retrieval metadata. Never stores auth headers. */
public final class EvidenceArchive {
    @FunctionalInterface interface Fetch { String get() throws IOException, InterruptedException; }
    private final Path root;
    private final boolean offline;
    public EvidenceArchive(Path root, boolean offline) { this.root = root; this.offline = offline; }
    String response(String url, String accept, Fetch fetch) throws IOException, InterruptedException {
        Path entry = entry(url, accept);
        if (Files.exists(entry)) return readBody(StudyFiles.read(entry));
        Path failure = failure(url, accept);
        if (Files.exists(failure) && (offline || accept.equals("text/html"))) throw new IOException(StudyFiles.read(failure).getProperty("error"));
        if (offline) throw new IOException("Offline archive missing response: " + url);
        try {
            String body = fetch.get();
            store(url, accept, body, Instant.now().toString(), "http-response", "collector");
            return body;
        } catch (IOException e) {
            Properties p = StudyFiles.values("url", url, "accept", accept, "retrieved.utc", Instant.now().toString(),
                    "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            StudyFiles.properties(root.resolve("attempts").resolve(UUID.randomUUID() + ".properties"), p);
            StudyFiles.properties(failure, p);
            throw e;
        }
    }
    void store(String url, String accept, String body, String retrieved, String kind, String owner) throws IOException {
        Instant.parse(retrieved);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String hash = StudyFiles.hash(bytes);
        Path entry = entry(url, accept);
        if (Files.exists(entry)) {
            if (!StudyFiles.read(entry).getProperty("sha256").equals(hash)) throw new IOException("Source changed; start a new dataset version: " + url);
            readBody(StudyFiles.read(entry));
            return;
        }
        Path object = root.resolve("objects").resolve(hash);
        if (Files.exists(object) && !StudyFiles.hash(object).equals(hash)) throw new IOException("Corrupt evidence object: " + hash);
        if (!Files.exists(object)) StudyFiles.atomic(object, bytes);
        StudyFiles.properties(entry, StudyFiles.values("id", StudyFiles.id(accept + "\n" + url), "url", url, "accept", accept,
                "retrieved.utc", retrieved, "sha256", hash, "kind", kind, "owner", owner));
    }
    PullRequestData archivedPullRequest(String repository, int number) throws IOException {
        String prefix = "https://api.github.com/repos/" + repository.toLowerCase(Locale.ROOT) + "/pulls";
        for (Path path : StudyFiles.files(root.resolve("responses"), ".properties")) {
            Properties p = StudyFiles.read(path);
            String url = p.getProperty("url", "").toLowerCase(Locale.ROOT);
            if (!url.equals(prefix + "/" + number) && !url.startsWith(prefix + "?")) continue;
            String body = readBody(p);
            List<String> objects = body.stripLeading().startsWith("[") ? JsonTools.splitTopLevelObjects(body) : List.of(body);
            for (String object : objects) if (JsonTools.intValue(object, "number", -1) == number) {
                return GitHubClient.parsePullRequestObject(repository.toLowerCase(Locale.ROOT), object);
            }
        }
        throw new IOException("PR not found in immutable snapshot: " + repository + "#" + number);
    }

    private String readBody(Properties p) throws IOException {
        String hash = p.getProperty("sha256", "");
        if (!hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid archive digest");
        Path object = root.resolve("objects").resolve(hash);
        byte[] bytes = Files.readAllBytes(object);
        if (!StudyFiles.hash(bytes).equals(hash)) throw new IOException("Evidence hash mismatch: " + p.getProperty("url"));
        return new String(bytes, StandardCharsets.UTF_8);
    }
    void verify() throws IOException {
        for (Path entry : StudyFiles.files(root.resolve("responses"), ".properties")) readBody(StudyFiles.read(entry));
    }
    private Path entry(String url, String accept) { return root.resolve("responses").resolve(StudyFiles.id(accept + "\n" + url) + ".properties"); }
    private Path failure(String url, String accept) { return root.resolve("failures").resolve(StudyFiles.id(accept + "\n" + url) + ".properties"); }
    static void importSupplementary(Path manifest, EvidenceArchive archive) throws IOException {
        for (var row : CsvTools.readRows(manifest)) {
            for (String key : List.of("ID", "Kind", "URL", "Retrieved UTC", "File", "SHA256", "Owner")) {
                if (row.getOrDefault(key, "").isBlank()) throw new IllegalArgumentException("Missing supplementary evidence field: " + key);
            }
            if (!List.of("policy", "comment").contains(row.get("Kind"))) throw new IllegalArgumentException("Expected policy or comment snapshot");
            Path file = manifest.toAbsolutePath().getParent().resolve(row.get("File"));
            if (!StudyFiles.hash(file).equals(row.get("SHA256"))) throw new IOException("Supplementary evidence hash mismatch: " + file);
            archive.store(row.get("URL"), "snapshot/" + row.get("Kind") + "/" + row.get("ID"), Files.readString(file), row.get("Retrieved UTC"), row.get("Kind"), row.get("Owner"));
        }
    }
    public static void compare(Path firstRun, Path secondRun, Path output) throws IOException {
        var old = index(firstRun.resolve("evidence/responses"));
        var fresh = index(secondRun.resolve("evidence/responses"));
        var ids = new TreeSet<String>(); ids.addAll(old.keySet()); ids.addAll(fresh.keySet());
        StringBuilder csv = new StringBuilder("Source ID,URL,Change,Old SHA256,New SHA256\n");
        for (String id : ids) {
            Properties a = old.get(id), b = fresh.get(id);
            String ah = a == null ? "" : a.getProperty("sha256"), bh = b == null ? "" : b.getProperty("sha256");
            csv.append(String.join(",", CsvTools.csv(id), CsvTools.csv((b == null ? a : b).getProperty("url")),
                    CsvTools.csv(a == null ? "Added" : b == null ? "Removed" : ah.equals(bh) ? "Unchanged" : "Changed"), CsvTools.csv(ah), CsvTools.csv(bh))).append('\n');
        }
        StudyFiles.text(output, csv.toString());
    }
    private static Map<String, Properties> index(Path dir) throws IOException {
        Map<String, Properties> index = new TreeMap<>();
        for (Path p : StudyFiles.files(dir, ".properties")) { Properties entry = StudyFiles.read(p); index.put(entry.getProperty("id"), entry); }
        return index;
    }
}
