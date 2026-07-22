package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class KappaWorkflow {
    private static final List<String> PRESENT_VALUES = List.of("Yes", "No");
    private static final List<String> CLASSIFICATION_VALUES = List.of("Positive", "Negative", "Ambiguous", "None");

    public static int writeSample(Path path, List<PrReportRow> rows) throws IOException {
        return writeSampleWithSummary(path, rows).totalRows();
    }

    public static SampleWriteResult writeSampleWithSummary(Path path, List<PrReportRow> rows) throws IOException {
        if (Files.exists(path)) {
            throw new IOException("Kappa sample already exists: " + path + ". Choose a new output path to avoid overwriting work.");
        }
        Set<String> seen = new LinkedHashSet<>();
        Map<String, Integer> writtenByRepository = new LinkedHashMap<>();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Sample ID,Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Status,"
                    + "Disclosure Text detected by script,Script AI Disclosure Present,Notes");
            writer.newLine();
            for (PrReportRow row : rows) {
                String sampleId = sampleId(row);
                if (!seen.add(sampleId)) {
                    continue;
                }
                writer.write(String.join(",",
                        CsvTools.csv(sampleId),
                        CsvTools.csv(row.repository()),
                        CsvTools.csv(Integer.toString(row.pullRequestNumber())),
                        CsvTools.csv(row.url()),
                        CsvTools.csv(row.title()),
                        CsvTools.csv(row.author()),
                        CsvTools.csv(row.createdAt()),
                        CsvTools.csv(row.closedAt()),
                        CsvTools.csv(row.mergedAt()),
                        CsvTools.csv(status(row)),
                        CsvTools.csv(row.aiDisclosure() ? row.aiDisclosureEvidence() : ""),
                        CsvTools.csv(row.aiDisclosure() ? "Yes" : "No"),
                        CsvTools.csv("")
                ));
                writer.newLine();
                writtenByRepository.merge(row.repository(), 1, Integer::sum);
            }
        }
        int totalRows = writtenByRepository.values().stream().mapToInt(Integer::intValue).sum();
        return new SampleWriteResult(writtenByRepository, totalRows);
    }

    public static RepositorySelection selectRandomRepositories(List<RepoUrl> repoUrls, long seed) {
        List<RepoUrl> unique = uniqueRepositories(repoUrls);
        if (unique.size() < 3) {
            throw new IllegalArgumentException("At least 3 valid repositories are required to create the kappa sample.");
        }
        List<RepoUrl> shuffled = new ArrayList<>(unique);
        Collections.shuffle(shuffled, new Random(seed));
        return new RepositorySelection(unique.size(), seed, List.copyOf(shuffled.subList(0, 3)));
    }

    public static List<RepoUrl> uniqueRepositories(List<RepoUrl> repoUrls) {
        Map<String, RepoUrl> unique = new LinkedHashMap<>();
        for (RepoUrl repoUrl : repoUrls) {
            unique.putIfAbsent(repoUrl.fullName(), repoUrl);
        }
        return new ArrayList<>(unique.values());
    }

    public static void codeSample(Path samplePath, Path labelsPath) throws IOException {
        if (Files.exists(labelsPath)) {
            throw new IOException("Labels file already exists: " + labelsPath + ". Choose a new output path to avoid overwriting coder work.");
        }
        List<Map<String, String>> sampleRows = CsvTools.readRows(samplePath);
        try (BufferedWriter writer = Files.newBufferedWriter(labelsPath, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(System.in)) {
            writer.write("Sample ID,Repo,PR #,PR URL,Disclosure Present,Disclosure Classification,Notes");
            writer.newLine();
            for (Map<String, String> row : sampleRows) {
                printCodingPrompt(row);
                String present = promptChoice(scanner, "Disclosure Present", PRESENT_VALUES);
                if (present.equalsIgnoreCase("SKIP")) {
                    continue;
                }
                String classification = promptChoice(scanner, "Disclosure Classification", CLASSIFICATION_VALUES);
                if (classification.equalsIgnoreCase("SKIP")) {
                    continue;
                }
                System.out.print("Notes optional, press Enter for blank: ");
                String notes = scanner.nextLine().trim();
                writer.write(String.join(",",
                        CsvTools.csv(row.get("Sample ID")),
                        CsvTools.csv(row.get("Repo")),
                        CsvTools.csv(row.get("PR #")),
                        CsvTools.csv(row.get("PR URL")),
                        CsvTools.csv(present),
                        CsvTools.csv(classification),
                        CsvTools.csv(notes)
                ));
                writer.newLine();
                writer.flush();
            }
        }
    }

    public static void calculateKappa(Path coderAPath, Path coderBPath, Path outputPath) throws IOException {
        List<Map<String, String>> coderA = CsvTools.readRows(coderAPath);
        List<Map<String, String>> coderB = CsvTools.readRows(coderBPath);
        Map<String, Map<String, String>> aById = indexBySampleId(coderA, coderAPath);
        Map<String, Map<String, String>> bById = indexBySampleId(coderB, coderBPath);
        List<String> matchedIds = new ArrayList<>();
        for (String id : aById.keySet()) {
            if (bById.containsKey(id)) {
                String aUrl = aById.get(id).getOrDefault("PR URL", "");
                String bUrl = bById.get(id).getOrDefault("PR URL", "");
                if (!aUrl.isBlank() && !bUrl.isBlank() && !aUrl.equals(bUrl)) {
                    throw new IllegalArgumentException("Sample ID maps to different PR URLs across label files: " + id);
                }
                matchedIds.add(id);
            }
        }
        if (matchedIds.isEmpty()) {
            throw new IllegalArgumentException("No matching Sample ID values found between label files.");
        }

        MetricResult present = metric(matchedIds, aById, bById, "Disclosure Present", PRESENT_VALUES);
        MetricResult classification = metric(matchedIds, aById, bById, "Disclosure Classification", CLASSIFICATION_VALUES);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("Section,Metric,Value");
            writer.newLine();
            writeMetricSummary(writer, "Disclosure Present", present);
            writeMetricSummary(writer, "Disclosure Classification", classification);
            writer.write(String.join(",", CsvTools.csv("Kappa Interpretation"), CsvTools.csv("Note"), CsvTools.csv("Use interpretation cautiously; resolve disagreements after calculating reliability, then create a consensus gold-standard dataset.")));
            writer.newLine();
            writer.newLine();
            writeMatrix(writer, "Disclosure Present Confusion Matrix", present);
            writer.newLine();
            writeMatrix(writer, "Disclosure Classification Confusion Matrix", classification);
            writer.newLine();
            writeMissing(writer, "Only in coder A", aById.keySet(), bById.keySet());
            writeMissing(writer, "Only in coder B", bById.keySet(), aById.keySet());
            writer.newLine();
            writeDisagreements(writer, matchedIds, aById, bById);
        }
    }

    private static String sampleId(PrReportRow row) {
        return row.repository() + "#" + row.pullRequestNumber();
    }

    private static String status(PrReportRow row) {
        if (row.merged()) return "Merged";
        if (row.closed()) return "Closed";
        return "Unknown";
    }

    private static void printCodingPrompt(Map<String, String> row) {
        System.out.println();
        System.out.println("Sample ID: " + row.get("Sample ID"));
        System.out.println("Repo: " + row.get("Repo"));
        System.out.println("PR #: " + row.get("PR #"));
        System.out.println("PR URL: " + row.get("PR URL"));
        System.out.println("Title: " + row.get("Title"));
        System.out.println("Enter SKIP at either label prompt to skip this PR.");
    }

    private static String promptChoice(Scanner scanner, String label, List<String> choices) {
        while (true) {
            System.out.print(label + " " + choices + ": ");
            String value = scanner.nextLine().trim();
            if (value.equalsIgnoreCase("skip")) return "SKIP";
            for (String choice : choices) {
                if (choice.equalsIgnoreCase(value)) {
                    return choice;
                }
            }
            System.out.println("Invalid value. Use one of " + choices + " or SKIP.");
        }
    }

    private static Map<String, Map<String, String>> indexBySampleId(List<Map<String, String>> rows, Path path) {
        Map<String, Map<String, String>> byId = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String id = row.getOrDefault("Sample ID", "").trim();
            if (id.isBlank()) {
                throw new IllegalArgumentException("Missing Sample ID in " + path);
            }
            if (byId.put(id, row) != null) {
                throw new IllegalArgumentException("Duplicate Sample ID in " + path + ": " + id);
            }
        }
        return byId;
    }

    private static MetricResult metric(List<String> matchedIds, Map<String, Map<String, String>> aById, Map<String, Map<String, String>> bById, String field, List<String> labels) {
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        for (String a : labels) {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (String b : labels) {
                row.put(b, 0);
            }
            matrix.put(a, row);
        }
        int n = 0;
        int agree = 0;
        for (String id : matchedIds) {
            String a = normalize(aById.get(id).get(field), labels);
            String b = normalize(bById.get(id).get(field), labels);
            if (a == null || b == null) {
                continue;
            }
            matrix.get(a).merge(b, 1, Integer::sum);
            n++;
            if (a.equals(b)) agree++;
        }
        double observed = n == 0 ? 0.0 : agree * 1.0 / n;
        double expected = expectedAgreement(matrix, labels, n);
        double kappa = (n == 0 || expected == 1.0) ? Double.NaN : (observed - expected) / (1.0 - expected);
        return new MetricResult(labels, matrix, n, observed, expected, kappa);
    }

    private static String normalize(String value, List<String> labels) {
        if (value == null || value.isBlank()) return null;
        for (String label : labels) {
            if (label.equalsIgnoreCase(value.trim())) {
                return label;
            }
        }
        return null;
    }

    private static double expectedAgreement(Map<String, Map<String, Integer>> matrix, List<String> labels, int n) {
        if (n == 0) return 0.0;
        double expected = 0.0;
        for (String label : labels) {
            int rowTotal = 0;
            int colTotal = 0;
            for (String col : labels) rowTotal += matrix.get(label).get(col);
            for (String row : labels) colTotal += matrix.get(row).get(label);
            expected += (rowTotal * 1.0 / n) * (colTotal * 1.0 / n);
        }
        return expected;
    }

    private static void writeMetricSummary(BufferedWriter writer, String section, MetricResult result) throws IOException {
        writer.write(String.join(",", CsvTools.csv(section), CsvTools.csv("Matched PRs used"), CsvTools.csv(Integer.toString(result.n()))));
        writer.newLine();
        writer.write(String.join(",", CsvTools.csv(section), CsvTools.csv("Raw agreement"), CsvTools.csv(format(result.observed()))));
        writer.newLine();
        writer.write(String.join(",", CsvTools.csv(section), CsvTools.csv("Expected agreement"), CsvTools.csv(format(result.expected()))));
        writer.newLine();
        writer.write(String.join(",", CsvTools.csv(section), CsvTools.csv("Cohen's Kappa"), CsvTools.csv(format(result.kappa()))));
        writer.newLine();
    }

    private static void writeMatrix(BufferedWriter writer, String title, MetricResult result) throws IOException {
        writer.write(CsvTools.csv(title));
        writer.newLine();
        List<String> header = new ArrayList<>();
        header.add("Coder A \\ Coder B");
        header.addAll(result.labels());
        writer.write(csvLine(header));
        writer.newLine();
        for (String rowLabel : result.labels()) {
            List<String> values = new ArrayList<>();
            values.add(rowLabel);
            for (String colLabel : result.labels()) {
                values.add(Integer.toString(result.matrix().get(rowLabel).get(colLabel)));
            }
            writer.write(csvLine(values));
            writer.newLine();
        }
    }

    private static void writeMissing(BufferedWriter writer, String section, Set<String> primary, Set<String> comparison) throws IOException {
        for (String id : primary) {
            if (!comparison.contains(id)) {
                writer.write(String.join(",", CsvTools.csv(section), CsvTools.csv("Missing matched label"), CsvTools.csv(id)));
                writer.newLine();
            }
        }
    }

    private static void writeDisagreements(BufferedWriter writer, List<String> matchedIds, Map<String, Map<String, String>> aById, Map<String, Map<String, String>> bById) throws IOException {
        writer.write("Disagreement Rows");
        writer.newLine();
        writer.write("Sample ID,PR URL,Coder A Present,Coder B Present,Coder A Classification,Coder B Classification");
        writer.newLine();
        for (String id : matchedIds) {
            Map<String, String> a = aById.get(id);
            Map<String, String> b = bById.get(id);
            boolean presentDisagree = !a.getOrDefault("Disclosure Present", "").equalsIgnoreCase(b.getOrDefault("Disclosure Present", ""));
            boolean classificationDisagree = !a.getOrDefault("Disclosure Classification", "").equalsIgnoreCase(b.getOrDefault("Disclosure Classification", ""));
            if (presentDisagree || classificationDisagree) {
                writer.write(String.join(",",
                        CsvTools.csv(id),
                        CsvTools.csv(firstNonBlank(a.get("PR URL"), b.get("PR URL"))),
                        CsvTools.csv(a.get("Disclosure Present")),
                        CsvTools.csv(b.get("Disclosure Present")),
                        CsvTools.csv(a.get("Disclosure Classification")),
                        CsvTools.csv(b.get("Disclosure Classification"))
                ));
                writer.newLine();
            }
        }
    }

    private static String csvLine(List<String> values) {
        return String.join(",", values.stream().map(CsvTools::csv).toList());
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static String format(double value) {
        if (Double.isNaN(value)) return "UNDEFINED";
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private record MetricResult(List<String> labels, Map<String, Map<String, Integer>> matrix, int n, double observed, double expected, double kappa) {
    }

    public record SampleWriteResult(Map<String, Integer> rowsByRepository, int totalRows) {
        public SampleWriteResult {
            rowsByRepository = Collections.unmodifiableMap(new LinkedHashMap<>(rowsByRepository));
        }
    }

    public record RepositorySelection(int availableRepositories, long seed, List<RepoUrl> selectedRepositories) {
        public RepositorySelection {
            selectedRepositories = List.copyOf(selectedRepositories);
        }
    }
}
