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
import java.util.Set;

public class ExtendedValidationWorkflow {
    public static final long DEFAULT_SAMPLE_SEED = 20260724L;
    public static final int REQUIRED_VALIDATION_SAMPLE_SIZE = 342;

    private static final List<String> SUCCESS_STATUSES = List.of("Merged", "Closed");
    private static final List<String> PRESENT_VALUES = List.of("Yes", "No");
    private static final List<String> OUTCOME_VALUES = List.of("True Positive", "True Negative", "False Positive", "False Negative");

    public static SampleResult sample(Path prDatasetPath, Path existingSamplePath, int repositoryCount, int rowsPerRepository, Path outputPath) throws IOException {
        return sample(prDatasetPath, existingSamplePath, repositoryCount, rowsPerRepository, outputPath, DEFAULT_SAMPLE_SEED);
    }

    public static SampleResult sample(Path prDatasetPath, Path existingSamplePath, int repositoryCount, int rowsPerRepository, Path outputPath, long seed) throws IOException {
        refuseOverwrite(outputPath, "Extended validation sample");
        if (repositoryCount <= 0 || rowsPerRepository <= 0) {
            throw new IllegalArgumentException("Repository count and rows per repository must be positive.");
        }
        List<Map<String, String>> population = CsvTools.readRows(prDatasetPath);
        validatePrDataset(population, prDatasetPath);
        ExistingSample existing = readExistingSample(existingSamplePath);

        Map<String, List<Map<String, String>>> byRepository = eligiblePopulationByRepository(population, existing.sampleIds());
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : byRepository.entrySet()) {
            if (!existing.repositories().contains(entry.getKey()) && !entry.getValue().isEmpty()) {
                candidates.add(entry.getKey());
            }
        }
        Collections.shuffle(candidates, new Random(seed));

        List<String> selectedRepositories = new ArrayList<>();
        List<Map<String, String>> selectedRows = new ArrayList<>();
        Map<String, Integer> rowsByRepository = new LinkedHashMap<>();
        int originalRows = existing.sampleIds().size();
        int targetCombinedRows = Math.max(originalRows + repositoryCount * rowsPerRepository, REQUIRED_VALIDATION_SAMPLE_SIZE);
        for (String repository : candidates) {
            if (selectedRepositories.size() >= repositoryCount && originalRows + selectedRows.size() >= targetCombinedRows) {
                break;
            }
            List<Map<String, String>> rows = byRepository.get(repository);
            int take = Math.min(rowsPerRepository, rows.size());
            if (take == 0) {
                continue;
            }
            selectedRepositories.add(repository);
            for (int i = 0; i < take; i++) {
                selectedRows.add(rows.get(i));
            }
            rowsByRepository.put(repository, take);
        }
        if (selectedRepositories.size() < repositoryCount) {
            throw new IllegalArgumentException("Only " + selectedRepositories.size() + " repositories have eligible unsampled PR rows; requested " + repositoryCount + ".");
        }

        writeSample(outputPath, selectedRows);
        return new SampleResult(seed, candidates.size(), selectedRepositories, rowsByRepository, selectedRows.size(), originalRows, originalRows + selectedRows.size(), REQUIRED_VALIDATION_SAMPLE_SIZE);
    }

    public static CombineResult combine(Path originalValidationPath, Path extendedValidationPath, Path combinedRowsOutputPath, Path metricsOutputPath) throws IOException {
        refuseOverwrite(combinedRowsOutputPath, "Combined detector validation rows");
        refuseOverwrite(metricsOutputPath, "Combined detector metrics");
        List<ValidationDetail> original = readValidationDetails(originalValidationPath, "Original");
        List<ValidationDetail> extended = readValidationDetails(extendedValidationPath, "Extended");

        Map<String, ValidationDetail> byId = new LinkedHashMap<>();
        List<DuplicateRow> duplicates = new ArrayList<>();
        addValidationRows(byId, duplicates, original);
        addValidationRows(byId, duplicates, extended);
        if (!duplicates.isEmpty()) {
            for (DuplicateRow duplicate : duplicates) {
                if (duplicate.conflicting()) {
                    throw new IllegalArgumentException("Conflicting duplicate Sample ID across validation files: " + duplicate.sampleId());
                }
            }
        }

        List<ValidationDetail> combined = new ArrayList<>(byId.values());
        ConfusionMatrix originalMatrix = ConfusionMatrix.from(original);
        ConfusionMatrix extendedMatrix = ConfusionMatrix.from(extended);
        ConfusionMatrix combinedMatrix = ConfusionMatrix.from(combined);
        String detectorVersion = detectorVersion();

        writeCombinedRows(combinedRowsOutputPath, combined);
        writeMetrics(metricsOutputPath, originalMatrix, extendedMatrix, combinedMatrix, duplicates, detectorVersion);
        return new CombineResult(originalMatrix, extendedMatrix, combinedMatrix, duplicates, detectorVersion);
    }

    private static Map<String, List<Map<String, String>>> eligiblePopulationByRepository(List<Map<String, String>> rows, Set<String> excludedSampleIds) {
        Map<String, List<Map<String, String>>> byRepository = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String repo = normalizeRepo(row.get("Repo"));
            String prNumber = clean(row.get("PR #"));
            String id = repo + "#" + prNumber;
            if (repo.isBlank() || prNumber.isBlank() || excludedSampleIds.contains(id) || !seen.add(id) || !isEligible(row)) {
                continue;
            }
            Map<String, String> copy = new LinkedHashMap<>(row);
            copy.put("Repo", repo);
            byRepository.computeIfAbsent(repo, ignored -> new ArrayList<>()).add(copy);
        }
        for (List<Map<String, String>> repoRows : byRepository.values()) {
            repoRows.sort((a, b) -> {
                int date = clean(b.get("Closed Date")).compareTo(clean(a.get("Closed Date")));
                if (date != 0) return date;
                return Integer.compare(parseInt(b.get("PR #")), parseInt(a.get("PR #")));
            });
        }
        return byRepository;
    }

    private static boolean isEligible(Map<String, String> row) {
        String status = clean(row.get("Status"));
        String bot = clean(row.get("Bot"));
        String disclosure = clean(row.get("AI Disclosure Present"));
        return SUCCESS_STATUSES.stream().anyMatch(value -> value.equalsIgnoreCase(status))
                && !clean(row.get("Closed Date")).isBlank()
                && !bot.equalsIgnoreCase("Yes")
                && PRESENT_VALUES.stream().anyMatch(value -> value.equalsIgnoreCase(disclosure));
    }

    private static ExistingSample readExistingSample(Path path) throws IOException {
        List<Map<String, String>> rows = CsvTools.readRows(path);
        Set<String> repositories = new LinkedHashSet<>();
        Set<String> sampleIds = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String repo = normalizeRepo(row.get("Repo"));
            String id = clean(row.get("Sample ID"));
            if (id.isBlank() && !repo.isBlank() && !clean(row.get("PR #")).isBlank()) {
                id = repo + "#" + clean(row.get("PR #"));
            }
            if (!repo.isBlank()) repositories.add(repo);
            if (!id.isBlank()) sampleIds.add(id);
        }
        return new ExistingSample(repositories, sampleIds);
    }

    private static void writeSample(Path outputPath, List<Map<String, String>> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("Sample ID,Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Status,Disclosure Text detected by script,Script AI Disclosure Present,Script Disclosure Classification,Notes");
            writer.newLine();
            for (Map<String, String> row : rows) {
                String repo = normalizeRepo(row.get("Repo"));
                String prNumber = clean(row.get("PR #"));
                writer.write(String.join(",",
                        CsvTools.csv(repo + "#" + prNumber),
                        CsvTools.csv(repo),
                        CsvTools.csv(prNumber),
                        CsvTools.csv(row.get("PR URL")),
                        CsvTools.csv(row.get("Title")),
                        CsvTools.csv(row.get("Author")),
                        CsvTools.csv(row.get("Created Date")),
                        CsvTools.csv(row.get("Closed Date")),
                        CsvTools.csv(row.get("Merged Date")),
                        CsvTools.csv(row.get("Status")),
                        CsvTools.csv(row.get("Disclosure Text")),
                        CsvTools.csv(normalizePresent(row.get("AI Disclosure Present"))),
                        CsvTools.csv(row.get("Disclosure Classification")),
                        CsvTools.csv("")
                ));
                writer.newLine();
            }
        }
    }

    private static List<ValidationDetail> readValidationDetails(Path path, String provenance) throws IOException {
        List<List<String>> records = CsvTools.readRecords(path);
        List<ValidationDetail> details = new ArrayList<>();
        List<String> header = List.of();
        for (List<String> record : records) {
            if (record.size() == 1 && "Detailed Results".equals(clean(record.get(0)))) {
                header = List.of();
                continue;
            }
            if (!record.isEmpty() && "Sample ID".equals(clean(record.get(0)))) {
                header = new ArrayList<>(record);
                continue;
            }
            if (header.isEmpty()) continue;
            Map<String, String> row = toRow(header, record);
            String id = clean(row.get("Sample ID"));
            String outcome = normalizeOutcome(row.get("Detector Outcome"));
            String script = normalizePresent(row.get("Script Disclosure Present"));
            String consensus = normalizePresent(row.get("Consensus Disclosure Present"));
            if (id.isBlank() || outcome.isBlank() || script.isBlank() || consensus.isBlank()) {
                continue;
            }
            details.add(new ValidationDetail(
                    id,
                    clean(row.get("Repo")),
                    clean(row.get("PR #")),
                    clean(row.get("PR URL")),
                    script,
                    consensus,
                    outcome,
                    clean(row.get("Script Detected Text")),
                    clean(row.get("Consensus Classification")),
                    provenance
            ));
        }
        if (details.isEmpty()) {
            throw new IllegalArgumentException("No detailed validation rows found in " + path);
        }
        return details;
    }

    private static Map<String, String> toRow(List<String> header, List<String> record) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            row.put(header.get(i), i < record.size() ? record.get(i) : "");
        }
        return row;
    }

    private static void addValidationRows(Map<String, ValidationDetail> byId, List<DuplicateRow> duplicates, List<ValidationDetail> rows) {
        for (ValidationDetail row : rows) {
            ValidationDetail previous = byId.putIfAbsent(row.sampleId(), row);
            if (previous != null) {
                duplicates.add(new DuplicateRow(row.sampleId(), previous.provenance(), row.provenance(), previous.conflictsWith(row)));
            }
        }
    }

    private static void writeCombinedRows(Path path, List<ValidationDetail> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Sample ID,Repo,PR #,PR URL,Manual Disclosure Present,Detector Disclosure Present,Detector Outcome,Detector Rule or Reason,Manual Classification,Successful Analysis Status,Provenance");
            writer.newLine();
            for (ValidationDetail row : rows) {
                writer.write(String.join(",",
                        CsvTools.csv(row.sampleId()),
                        CsvTools.csv(row.repo()),
                        CsvTools.csv(row.prNumber()),
                        CsvTools.csv(row.prUrl()),
                        CsvTools.csv(row.consensusPresent()),
                        CsvTools.csv(row.scriptPresent()),
                        CsvTools.csv(row.outcome()),
                        CsvTools.csv(row.scriptDetectedText()),
                        CsvTools.csv(row.consensusClassification()),
                        CsvTools.csv("Success"),
                        CsvTools.csv(row.provenance())
                ));
                writer.newLine();
            }
        }
    }

    private static void writeMetrics(Path path, ConfusionMatrix original, ConfusionMatrix extended, ConfusionMatrix combined, List<DuplicateRow> duplicates, String detectorVersion) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Section,Metric,Value,CI Lower,CI Upper");
            writer.newLine();
            writeMatrixMetrics(writer, "Original", original, false);
            writeMatrixMetrics(writer, "Extended", extended, false);
            writeMatrixMetrics(writer, "Combined", combined, true);
            writeMetric(writer, "Validation Sample", "Requested sample size", Integer.toString(REQUIRED_VALIDATION_SAMPLE_SIZE), "", "");
            writeMetric(writer, "Validation Sample", "Original usable sample size", Integer.toString(original.n()), "", "");
            writeMetric(writer, "Validation Sample", "Additional usable sample size", Integer.toString(extended.n()), "", "");
            writeMetric(writer, "Validation Sample", "Combined usable sample size", Integer.toString(combined.n()), "", "");
            writeMetric(writer, "Validation Sample", "Meets requested sample size", combined.n() >= REQUIRED_VALIDATION_SAMPLE_SIZE ? "Yes" : "No", "", "");
            writeMetric(writer, "Validation Sample", "Duplicate rows reported", Integer.toString(duplicates.size()), "", "");
            writeMetric(writer, "Detector", "Detector version", detectorVersion, "", "");
            for (DuplicateRow duplicate : duplicates) {
                writeMetric(writer, "Duplicates", duplicate.conflicting() ? "Conflicting duplicate Sample ID" : "Duplicate Sample ID", duplicate.sampleId(), duplicate.firstProvenance(), duplicate.secondProvenance());
            }
        }
    }

    private static void writeMatrixMetrics(BufferedWriter writer, String section, ConfusionMatrix matrix, boolean includeCi) throws IOException {
        writeMetric(writer, section, "Usable rows", Integer.toString(matrix.n()), "", "");
        writeMetric(writer, section, "True positives", Integer.toString(matrix.tp()), "", "");
        writeMetric(writer, section, "True negatives", Integer.toString(matrix.tn()), "", "");
        writeMetric(writer, section, "False positives", Integer.toString(matrix.fp()), "", "");
        writeMetric(writer, section, "False negatives", Integer.toString(matrix.fn()), "", "");
        writeProportionMetric(writer, section, "Accuracy", matrix.tp() + matrix.tn(), matrix.n(), includeCi);
        writeProportionMetric(writer, section, "Precision", matrix.tp(), matrix.tp() + matrix.fp(), includeCi);
        writeProportionMetric(writer, section, "Recall", matrix.tp(), matrix.tp() + matrix.fn(), includeCi);
        writeProportionMetric(writer, section, "Specificity", matrix.tn(), matrix.tn() + matrix.fp(), includeCi);
        writeMetric(writer, section, "F1 score", format(matrix.f1()), "", "");
        writeProportionMetric(writer, section, "False-positive rate", matrix.fp(), matrix.fp() + matrix.tn(), includeCi);
        writeProportionMetric(writer, section, "False-negative rate", matrix.fn(), matrix.fn() + matrix.tp(), includeCi);
        writeProportionMetric(writer, section, "Negative predictive value", matrix.tn(), matrix.tn() + matrix.fn(), includeCi);
    }

    private static void writeProportionMetric(BufferedWriter writer, String section, String metric, int numerator, int denominator, boolean includeCi) throws IOException {
        String value = denominator == 0 ? "N/A" : format(numerator * 1.0 / denominator);
        String lower = "";
        String upper = "";
        if (includeCi && denominator > 0) {
            Interval interval = wilson(numerator, denominator);
            lower = format(interval.lower());
            upper = format(interval.upper());
        }
        writeMetric(writer, section, metric, value, lower, upper);
    }

    private static Interval wilson(int successes, int n) {
        double z = 1.959963984540054;
        double phat = successes * 1.0 / n;
        double denominator = 1.0 + z * z / n;
        double center = (phat + z * z / (2.0 * n)) / denominator;
        double margin = z * Math.sqrt((phat * (1.0 - phat) + z * z / (4.0 * n)) / n) / denominator;
        return new Interval(Math.max(0.0, center - margin), Math.min(1.0, center + margin));
    }

    private static void writeMetric(BufferedWriter writer, String section, String metric, String value, String lower, String upper) throws IOException {
        writer.write(String.join(",", CsvTools.csv(section), CsvTools.csv(metric), CsvTools.csv(value), CsvTools.csv(lower), CsvTools.csv(upper)));
        writer.newLine();
    }

    private static void validatePrDataset(List<Map<String, String>> rows, Path path) {
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV has no data rows: " + path);
        Map<String, String> first = rows.get(0);
        for (String column : List.of("Repo", "PR #", "PR URL", "Title", "Author", "Created Date", "Closed Date", "Merged Date", "Bot", "Status", "AI Disclosure Present", "Disclosure Classification", "Disclosure Text")) {
            if (!first.containsKey(column)) {
                throw new IllegalArgumentException("Missing required column in " + path + ": " + column);
            }
        }
    }

    private static void refuseOverwrite(Path path, String label) throws IOException {
        if (Files.exists(path)) {
            throw new IOException(label + " already exists: " + path + ". Choose a new output path to avoid overwriting work.");
        }
    }

    private static String normalizeRepo(String value) {
        try {
            return RepoListImporter.normalizeRepository(value).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return clean(value).toLowerCase(Locale.ROOT);
        }
    }

    private static String normalizePresent(String value) {
        String cleaned = clean(value);
        for (String present : PRESENT_VALUES) {
            if (present.equalsIgnoreCase(cleaned)) return present;
        }
        return "";
    }

    private static String normalizeOutcome(String value) {
        String cleaned = clean(value);
        for (String outcome : OUTCOME_VALUES) {
            if (outcome.equalsIgnoreCase(cleaned)) return outcome;
        }
        return "";
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(clean(value));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String format(double value) {
        if (Double.isNaN(value)) return "N/A";
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String detectorVersion() {
        String value = System.getenv("DETECTOR_VERSION");
        if (value != null && !value.isBlank()) return value.trim();
        return "current-worktree";
    }

    private record ExistingSample(Set<String> repositories, Set<String> sampleIds) {
    }

    public record SampleResult(long seed, int candidateRepositories, List<String> selectedRepositories, Map<String, Integer> rowsByRepository, int rowsWritten, int existingRows, int combinedRows, int requiredRows) {
        public SampleResult {
            selectedRepositories = List.copyOf(selectedRepositories);
            rowsByRepository = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rowsByRepository));
        }
    }

    private record ValidationDetail(String sampleId, String repo, String prNumber, String prUrl, String scriptPresent, String consensusPresent, String outcome, String scriptDetectedText, String consensusClassification, String provenance) {
        boolean conflictsWith(ValidationDetail other) {
            return !scriptPresent.equals(other.scriptPresent)
                    || !consensusPresent.equals(other.consensusPresent)
                    || !outcome.equals(other.outcome)
                    || !prUrl.equals(other.prUrl);
        }
    }

    public record DuplicateRow(String sampleId, String firstProvenance, String secondProvenance, boolean conflicting) {
    }

    public record ConfusionMatrix(int tp, int tn, int fp, int fn) {
        static ConfusionMatrix from(List<ValidationDetail> rows) {
            int tp = 0;
            int tn = 0;
            int fp = 0;
            int fn = 0;
            for (ValidationDetail row : rows) {
                switch (row.outcome()) {
                    case "True Positive" -> tp++;
                    case "True Negative" -> tn++;
                    case "False Positive" -> fp++;
                    case "False Negative" -> fn++;
                    default -> {
                    }
                }
            }
            return new ConfusionMatrix(tp, tn, fp, fn);
        }

        int n() {
            return tp + tn + fp + fn;
        }

        double f1() {
            int precisionDenominator = tp + fp;
            int recallDenominator = tp + fn;
            if (precisionDenominator == 0 || recallDenominator == 0) return Double.NaN;
            double precision = tp * 1.0 / precisionDenominator;
            double recall = tp * 1.0 / recallDenominator;
            if (precision + recall == 0.0) return Double.NaN;
            return 2.0 * precision * recall / (precision + recall);
        }
    }

    public record CombineResult(ConfusionMatrix original, ConfusionMatrix extended, ConfusionMatrix combined, List<DuplicateRow> duplicates, String detectorVersion) {
        public CombineResult {
            duplicates = List.copyOf(duplicates);
        }
    }

    private record Interval(double lower, double upper) {
    }
}
