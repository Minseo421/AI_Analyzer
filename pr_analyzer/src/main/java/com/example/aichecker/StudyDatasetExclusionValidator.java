package com.example.aichecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudyDatasetExclusionValidator {
    public static final String EXCLUDED_REPOSITORY = "punkpeye/awesome-mcp-servers";

    public static ValidationResult validate(List<Path> paths) throws IOException {
        List<Occurrence> occurrences = new ArrayList<>();
        for (Path path : paths) {
            if (path.toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                validateCsv(path, occurrences);
            } else {
                validateText(path, occurrences);
            }
        }
        if (!occurrences.isEmpty()) {
            throw new IllegalArgumentException("Excluded repository found in study data: " + occurrences);
        }
        return new ValidationResult(paths.size(), occurrences);
    }

    private static void validateCsv(Path path, List<Occurrence> occurrences) throws IOException {
        List<Map<String, String>> rows = CsvTools.readRows(path);
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (matchesExcludedRepository(entry.getValue())) {
                    occurrences.add(new Occurrence(path.toString(), i + 2, entry.getKey(), entry.getValue()));
                }
            }
        }
    }

    private static void validateText(Path path, List<Occurrence> occurrences) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            if (matchesExcludedRepository(lines.get(i))) {
                occurrences.add(new Occurrence(path.toString(), i + 1, "", lines.get(i)));
            }
        }
    }

    static boolean matchesExcludedRepository(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()) return false;
        try {
            return RepoListImporter.normalizeRepository(cleaned).equalsIgnoreCase(EXCLUDED_REPOSITORY);
        } catch (IllegalArgumentException ignored) {
            return cleaned.toLowerCase(Locale.ROOT).contains("github.com/" + EXCLUDED_REPOSITORY)
                    || cleaned.equalsIgnoreCase(EXCLUDED_REPOSITORY);
        }
    }

    public record Occurrence(String path, int rowNumber, String column, String value) {
    }

    public record ValidationResult(int filesChecked, List<Occurrence> occurrences) {
        public ValidationResult {
            occurrences = List.copyOf(occurrences);
        }
    }
}
