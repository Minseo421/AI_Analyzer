package com.example.aichecker;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RepoListImporter {
    private static final String REPOSITORY_LINK = "Repository Link";

    public static ImportResult importFromCsv(Path csvPath, Path outputPath, boolean replace) throws IOException {
        if (Files.exists(outputPath) && !replace) {
            throw new IOException("Repository list already exists: " + outputPath + ". Use --replace to overwrite it.");
        }

        List<Map<String, String>> rows = CsvTools.readRows(csvPath);
        if (!rows.isEmpty() && !rows.get(0).containsKey(REPOSITORY_LINK)) {
            throw new IllegalArgumentException("Missing required column in " + csvPath + ": " + REPOSITORY_LINK);
        }

        List<String> existing = Files.exists(outputPath) ? readExistingRepos(outputPath) : List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<String> imported = new ArrayList<>();
        List<InvalidRepositoryLink> invalid = new ArrayList<>();
        int nonEmptyLinks = 0;
        int duplicateLinks = 0;

        for (int i = 0; i < rows.size(); i++) {
            String raw = rows.get(i).getOrDefault(REPOSITORY_LINK, "").trim();
            if (raw.isBlank()) {
                continue;
            }
            nonEmptyLinks++;
            try {
                String normalized = normalizeRepository(raw);
                if (seen.add(normalized)) {
                    imported.add(normalized);
                } else {
                    duplicateLinks++;
                }
            } catch (IllegalArgumentException e) {
                invalid.add(new InvalidRepositoryLink(i + 2, raw, e.getMessage()));
            }
        }

        Comparison comparison = compare(existing, imported);
        if (!invalid.isEmpty()) {
            return new ImportResult(rows.size(), nonEmptyLinks, 0, duplicateLinks, invalid.size(), outputPath, comparison.added(), comparison.removed(), comparison.unchanged(), invalid, false);
        }

        Files.write(outputPath, imported, StandardCharsets.UTF_8);
        return new ImportResult(rows.size(), nonEmptyLinks, imported.size(), duplicateLinks, 0, outputPath, comparison.added(), comparison.removed(), comparison.unchanged(), invalid, true);
    }

    static String normalizeRepository(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("blank repository link");
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith(".git")) {
            value = value.substring(0, value.length() - 4);
        }

        String path;
        if (value.startsWith("https://github.com/") || value.startsWith("http://github.com/")) {
            URI uri = URI.create(value);
            path = uri.getPath();
        } else if (value.startsWith("github.com/")) {
            path = value.substring("github.com".length());
        } else {
            path = value;
        }

        path = path.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("repository link must not include query strings or anchors");
        }
        String lowerPath = path.toLowerCase(java.util.Locale.ROOT);
        for (String segment : List.of("/blob/", "/tree/", "/pull/", "/issues/")) {
            if (lowerPath.contains(segment)) {
                throw new IllegalArgumentException("repository link points below the repository root");
            }
        }

        String[] parts = path.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("repository link must resolve to owner/repository");
        }
        return parts[0] + "/" + parts[1];
    }

    private static List<String> readExistingRepos(Path path) throws IOException {
        List<String> repos = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String cleaned = line.trim();
            if (cleaned.isBlank() || cleaned.startsWith("#")) {
                continue;
            }
            repos.add(normalizeRepository(cleaned));
        }
        return repos;
    }

    private static Comparison compare(List<String> existing, List<String> imported) {
        Set<String> existingSet = new LinkedHashSet<>(existing);
        Set<String> importedSet = new LinkedHashSet<>(imported);
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        int unchanged = 0;
        for (String repo : imported) {
            if (existingSet.contains(repo)) {
                unchanged++;
            } else {
                added.add(repo);
            }
        }
        for (String repo : existing) {
            if (!importedSet.contains(repo)) {
                removed.add(repo);
            }
        }
        return new Comparison(added, removed, unchanged);
    }

    private record Comparison(List<String> added, List<String> removed, int unchanged) {
    }

    public record InvalidRepositoryLink(int csvRowNumber, String value, String reason) {
    }

    public record ImportResult(
            int repositoryRowsInspected,
            int nonEmptyRepositoryLinks,
            int validRepositoriesWritten,
            int duplicateRepositoriesRemoved,
            int invalidRepositoryLinks,
            Path output,
            List<String> added,
            List<String> removed,
            int unchanged,
            List<InvalidRepositoryLink> invalidLinks,
            boolean written
    ) {
        public ImportResult {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
            invalidLinks = List.copyOf(invalidLinks);
        }
    }
}
