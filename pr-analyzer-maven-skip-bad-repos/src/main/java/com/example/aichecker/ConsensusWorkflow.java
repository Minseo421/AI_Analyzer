package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConsensusWorkflow {
    private static final List<String> PRESENT_VALUES = List.of("Yes", "No");
    private static final List<String> CLASSIFICATION_VALUES = List.of("Positive", "Negative", "Ambiguous", "None");
    private static final List<String> CONSENSUS_STATUSES = List.of("Agreed", "Needs Resolution", "Resolved");

    public static ConsensusResult createConsensus(Path coderAPath, Path coderBPath, Path outputPath) throws IOException {
        refuseOverwrite(outputPath, "Consensus file");
        Map<String, Map<String, String>> aById = indexBySampleId(CsvTools.readRows(coderAPath), coderAPath);
        Map<String, Map<String, String>> bById = indexBySampleId(CsvTools.readRows(coderBPath), coderBPath);

        List<String> matchedIds = matchedIds(aById, bById);
        List<String> onlyInA = missingIds(aById.keySet(), bById.keySet());
        List<String> onlyInB = missingIds(bById.keySet(), aById.keySet());
        int fullAgreements = 0;
        int disagreements = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("Sample ID,Repo,PR #,PR URL,"
                    + "Coder A Disclosure Present,Coder B Disclosure Present,Consensus Disclosure Present,"
                    + "Coder A Disclosure Classification,Coder B Disclosure Classification,Consensus Disclosure Classification,"
                    + "Agreement Status,Consensus Notes,Coder A Notes,Coder B Notes");
            writer.newLine();

            for (String id : matchedIds) {
                Map<String, String> a = aById.get(id);
                Map<String, String> b = bById.get(id);
                validateCoderRow(a, coderAPath);
                validateCoderRow(b, coderBPath);
                validateSamePr(id, a, b);

                String aPresent = normalizeRequired(a.get("Disclosure Present"), PRESENT_VALUES, "Disclosure Present", coderAPath, id);
                String bPresent = normalizeRequired(b.get("Disclosure Present"), PRESENT_VALUES, "Disclosure Present", coderBPath, id);
                String aClassification = normalizeRequired(a.get("Disclosure Classification"), CLASSIFICATION_VALUES, "Disclosure Classification", coderAPath, id);
                String bClassification = normalizeRequired(b.get("Disclosure Classification"), CLASSIFICATION_VALUES, "Disclosure Classification", coderBPath, id);

                boolean presentAgreed = aPresent.equals(bPresent);
                boolean classificationAgreed = aClassification.equals(bClassification);
                String consensusPresent = presentAgreed ? aPresent : "";
                String consensusClassification = classificationAgreed ? aClassification : "";
                String status = presentAgreed && classificationAgreed ? "Agreed" : "Needs Resolution";
                if (status.equals("Agreed")) {
                    fullAgreements++;
                } else {
                    disagreements++;
                }

                writer.write(String.join(",",
                        CsvTools.csv(id),
                        CsvTools.csv(firstNonBlank(a.get("Repo"), b.get("Repo"))),
                        CsvTools.csv(firstNonBlank(a.get("PR #"), b.get("PR #"))),
                        CsvTools.csv(firstNonBlank(a.get("PR URL"), b.get("PR URL"))),
                        CsvTools.csv(aPresent),
                        CsvTools.csv(bPresent),
                        CsvTools.csv(consensusPresent),
                        CsvTools.csv(aClassification),
                        CsvTools.csv(bClassification),
                        CsvTools.csv(consensusClassification),
                        CsvTools.csv(status),
                        CsvTools.csv(""),
                        CsvTools.csv(a.get("Notes")),
                        CsvTools.csv(b.get("Notes"))
                ));
                writer.newLine();
            }
        }

        return new ConsensusResult(matchedIds.size(), fullAgreements, disagreements, onlyInA, onlyInB);
    }

    public static DetectorValidationResult validateDetector(Path samplePath, Path consensusPath, Path outputPath) throws IOException {
        refuseOverwrite(outputPath, "Detector validation file");
        Map<String, Map<String, String>> sampleById = indexBySampleId(CsvTools.readRows(samplePath), samplePath);
        Map<String, Map<String, String>> consensusById = indexBySampleId(CsvTools.readRows(consensusPath), consensusPath);
        List<String> matchedIds = matchedIds(sampleById, consensusById);
        if (matchedIds.isEmpty()) {
            throw new IllegalArgumentException("No matching Sample ID values found between sample and consensus files.");
        }

        List<ValidationRow> rows = new ArrayList<>();
        int tp = 0;
        int tn = 0;
        int fp = 0;
        int fn = 0;
        for (String id : matchedIds) {
            Map<String, String> sample = sampleById.get(id);
            Map<String, String> consensus = consensusById.get(id);
            String scriptPresent = normalizeRequired(sample.get("Script AI Disclosure Present"), PRESENT_VALUES, "Script AI Disclosure Present", samplePath, id);
            String consensusPresent = normalizeRequired(consensus.get("Consensus Disclosure Present"), PRESENT_VALUES, "Consensus Disclosure Present", consensusPath, id);
            String consensusClassification = normalizeRequired(consensus.get("Consensus Disclosure Classification"), CLASSIFICATION_VALUES, "Consensus Disclosure Classification", consensusPath, id);
            validateConsensusStatus(consensus, consensusPath, id);

            String outcome;
            if (scriptPresent.equals("Yes") && consensusPresent.equals("Yes")) {
                outcome = "True Positive";
                tp++;
            } else if (scriptPresent.equals("No") && consensusPresent.equals("No")) {
                outcome = "True Negative";
                tn++;
            } else if (scriptPresent.equals("Yes")) {
                outcome = "False Positive";
                fp++;
            } else {
                outcome = "False Negative";
                fn++;
            }
            rows.add(new ValidationRow(id, sample, consensus, scriptPresent, consensusPresent, consensusClassification, outcome));
        }

        DetectorValidationResult result = new DetectorValidationResult(matchedIds.size(), tp, tn, fp, fn,
                missingIds(sampleById.keySet(), consensusById.keySet()),
                missingIds(consensusById.keySet(), sampleById.keySet()));
        writeDetectorValidation(outputPath, rows, result);
        return result;
    }

    private static void writeDetectorValidation(Path outputPath, List<ValidationRow> rows, DetectorValidationResult result) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("Section,Metric,Value");
            writer.newLine();
            writeMetric(writer, "Summary", "Total matched rows", result.totalMatchedRows());
            writeMetric(writer, "Summary", "True positives", result.truePositives());
            writeMetric(writer, "Summary", "True negatives", result.trueNegatives());
            writeMetric(writer, "Summary", "False positives", result.falsePositives());
            writeMetric(writer, "Summary", "False negatives", result.falseNegatives());
            writeMetric(writer, "Summary", "Accuracy", format(result.accuracy()));
            writeMetric(writer, "Summary", "Precision", format(result.precision()));
            writeMetric(writer, "Summary", "Recall", format(result.recall()));
            writeMetric(writer, "Summary", "Specificity", format(result.specificity()));
            writeMetric(writer, "Summary", "F1 score", format(result.f1()));
            writeMetric(writer, "Summary", "False-positive rate", format(result.falsePositiveRate()));
            writeMetric(writer, "Summary", "False-negative rate", format(result.falseNegativeRate()));
            writeMissing(writer, "Only in kappa sample", result.onlyInSample());
            writeMissing(writer, "Only in consensus", result.onlyInConsensus());
            writer.newLine();
            writer.write("Detailed Results");
            writer.newLine();
            writer.write("Sample ID,Repo,PR #,PR URL,Script Disclosure Present,Consensus Disclosure Present,Detector Outcome,Script Detected Text,Consensus Classification,Consensus Notes");
            writer.newLine();
            for (ValidationRow row : rows) {
                writer.write(String.join(",",
                        CsvTools.csv(row.sampleId()),
                        CsvTools.csv(firstNonBlank(row.sample().get("Repo"), row.consensus().get("Repo"))),
                        CsvTools.csv(firstNonBlank(row.sample().get("PR #"), row.consensus().get("PR #"))),
                        CsvTools.csv(firstNonBlank(row.sample().get("PR URL"), row.consensus().get("PR URL"))),
                        CsvTools.csv(row.scriptPresent()),
                        CsvTools.csv(row.consensusPresent()),
                        CsvTools.csv(row.outcome()),
                        CsvTools.csv(row.sample().get("Disclosure Text detected by script")),
                        CsvTools.csv(row.consensusClassification()),
                        CsvTools.csv(row.consensus().get("Consensus Notes"))
                ));
                writer.newLine();
            }
        }
    }

    private static void writeMetric(BufferedWriter writer, String section, String metric, int value) throws IOException {
        writeMetric(writer, section, metric, Integer.toString(value));
    }

    private static void writeMetric(BufferedWriter writer, String section, String metric, String value) throws IOException {
        writer.write(String.join(",", CsvTools.csv(section), CsvTools.csv(metric), CsvTools.csv(value)));
        writer.newLine();
    }

    private static void writeMissing(BufferedWriter writer, String section, List<String> ids) throws IOException {
        for (String id : ids) {
            writeMetric(writer, section, "Missing matched row", id);
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

    private static List<String> matchedIds(Map<String, Map<String, String>> primary, Map<String, Map<String, String>> comparison) {
        List<String> matched = new ArrayList<>();
        for (String id : primary.keySet()) {
            if (comparison.containsKey(id)) {
                matched.add(id);
            }
        }
        return matched;
    }

    private static List<String> missingIds(Set<String> primary, Set<String> comparison) {
        List<String> missing = new ArrayList<>();
        for (String id : primary) {
            if (!comparison.contains(id)) {
                missing.add(id);
            }
        }
        return missing;
    }

    private static void validateCoderRow(Map<String, String> row, Path path) {
        requireColumn(row, "Disclosure Present", path);
        requireColumn(row, "Disclosure Classification", path);
    }

    private static void validateConsensusStatus(Map<String, String> row, Path path, String id) {
        String status = normalizeRequired(row.get("Agreement Status"), CONSENSUS_STATUSES, "Agreement Status", path, id);
        if (status.equals("Needs Resolution")) {
            throw new IllegalArgumentException("Consensus row still needs resolution in " + path + ": " + id);
        }
    }

    private static void requireColumn(Map<String, String> row, String column, Path path) {
        if (!row.containsKey(column)) {
            throw new IllegalArgumentException("Missing required column in " + path + ": " + column);
        }
    }

    private static String normalizeRequired(String value, List<String> allowed, String field, Path path, String id) {
        String normalized = normalize(value, allowed);
        if (normalized == null) {
            throw new IllegalArgumentException("Invalid or missing " + field + " in " + path + " for Sample ID " + id + ". Supported values: " + allowed);
        }
        return normalized;
    }

    private static String normalize(String value, List<String> allowed) {
        if (value == null || value.isBlank()) return null;
        for (String allowedValue : allowed) {
            if (allowedValue.equalsIgnoreCase(value.trim())) {
                return allowedValue;
            }
        }
        return null;
    }

    private static void validateSamePr(String id, Map<String, String> a, Map<String, String> b) {
        String aUrl = a.getOrDefault("PR URL", "");
        String bUrl = b.getOrDefault("PR URL", "");
        if (!aUrl.isBlank() && !bUrl.isBlank() && !aUrl.equals(bUrl)) {
            throw new IllegalArgumentException("Sample ID maps to different PR URLs across label files: " + id);
        }
    }

    private static void refuseOverwrite(Path path, String label) throws IOException {
        if (Files.exists(path)) {
            throw new IOException(label + " already exists: " + path + ". Choose a new output path to avoid overwriting work.");
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static double divide(int numerator, int denominator) {
        return denominator == 0 ? Double.NaN : numerator * 1.0 / denominator;
    }

    private static String format(double value) {
        if (Double.isNaN(value)) return "UNDEFINED";
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private record ValidationRow(
            String sampleId,
            Map<String, String> sample,
            Map<String, String> consensus,
            String scriptPresent,
            String consensusPresent,
            String consensusClassification,
            String outcome
    ) {
    }

    public record ConsensusResult(int matchedRows, int fullAgreements, int disagreements, List<String> onlyInCoderA, List<String> onlyInCoderB) {
        public ConsensusResult {
            onlyInCoderA = List.copyOf(onlyInCoderA);
            onlyInCoderB = List.copyOf(onlyInCoderB);
        }
    }

    public record DetectorValidationResult(int totalMatchedRows, int truePositives, int trueNegatives, int falsePositives, int falseNegatives, List<String> onlyInSample, List<String> onlyInConsensus) {
        public DetectorValidationResult {
            onlyInSample = List.copyOf(onlyInSample);
            onlyInConsensus = List.copyOf(onlyInConsensus);
        }

        public double accuracy() {
            return divide(truePositives + trueNegatives, totalMatchedRows);
        }

        public double precision() {
            return divide(truePositives, truePositives + falsePositives);
        }

        public double recall() {
            return divide(truePositives, truePositives + falseNegatives);
        }

        public double specificity() {
            return divide(trueNegatives, trueNegatives + falsePositives);
        }

        public double f1() {
            double precision = precision();
            double recall = recall();
            if (Double.isNaN(precision) || Double.isNaN(recall) || precision + recall == 0.0) {
                return Double.NaN;
            }
            return 2 * precision * recall / (precision + recall);
        }

        public double falsePositiveRate() {
            return divide(falsePositives, falsePositives + trueNegatives);
        }

        public double falseNegativeRate() {
            return divide(falseNegatives, falseNegatives + truePositives);
        }
    }
}
