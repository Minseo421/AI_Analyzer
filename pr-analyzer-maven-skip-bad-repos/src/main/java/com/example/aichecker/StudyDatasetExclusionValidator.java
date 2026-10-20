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
    private static ExclusionRegister defaultRegister() {
        try { return new ExclusionRegister(Path.of("study/exclusions-v1.csv")); }
        catch (IOException e) { throw new IllegalArgumentException("Cannot load versioned exclusion register", e); }
    }

    public static ValidationResult validate(List<Path> paths) throws IOException {
        return validate(paths, Path.of("study/exclusions-v1.csv"));
    }

    public static ValidationResult validate(List<Path> paths, Path registerPath) throws IOException {
        ExclusionRegister register = new ExclusionRegister(registerPath);
        List<Occurrence> occurrences = new ArrayList<>();
        for (Path path : paths) {
            if (path.toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                validateCsv(path, occurrences, register);
            } else {
                validateText(path, occurrences, register);
            }
        }
        if (!occurrences.isEmpty()) {
            throw new IllegalArgumentException("Excluded repository found in study data: " + occurrences);
        }
        return new ValidationResult(paths.size(), occurrences);
    }

    private static void validateCsv(Path path, List<Occurrence> occurrences, ExclusionRegister register) throws IOException {
        List<Map<String, String>> rows = CsvTools.readRows(path);
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (matchesExcludedRepository(entry.getValue(), register)) {
                    occurrences.add(new Occurrence(path.toString(), i + 2, entry.getKey(), entry.getValue()));
                }
            }
        }
    }

    private static void validateText(Path path, List<Occurrence> occurrences, ExclusionRegister register) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            if (matchesExcludedRepository(lines.get(i), register)) {
                occurrences.add(new Occurrence(path.toString(), i + 1, "", lines.get(i)));
            }
        }
    }

    static boolean matchesExcludedRepository(String value) {
        return matchesExcludedRepository(value, defaultRegister());
    }

    private static boolean matchesExcludedRepository(String value, ExclusionRegister register) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()) return false;
        try {
            String repository = RepoListImporter.normalizeRepository(cleaned);
            ExclusionRegister.Decision d = register.find("repository", repository);
            return d != null && !d.action().equals("Include");
        } catch (IllegalArgumentException ignored) { return false; }
    }

    public record Occurrence(String path, int rowNumber, String column, String value) {
    }

    public record ValidationResult(int filesChecked, List<Occurrence> occurrences) {
        public ValidationResult {
            occurrences = List.copyOf(occurrences);
        }
    }
}
