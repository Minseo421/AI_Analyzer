package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpecificPrAnalysisWorkflow {
    private static final String SAMPLE_ID = "Sample ID";
    private static final String REPO = "Repo";
    private static final String PR_NUMBER = "PR #";
    private static final String PR_URL = "PR URL";
    private static final String DISCLOSURE_EVIDENCE = "Disclosure Text detected by script";
    private static final String SCRIPT_PRESENT = "Script AI Disclosure Present";
    private static final String SCRIPT_CLASSIFICATION = "Script Disclosure Classification";
    private static final String SCRIPT_SOURCE = "Script Disclosure Source";
    private static final String REANALYSIS_STATUS = "Reanalysis Status";
    private static final String REANALYSIS_ERROR = "Reanalysis Error";

    public static SpecificAnalysisResult analyzeSpecificPrs(Path inputPath, Path outputPath, List<String> requestedSampleIds) throws IOException {
        return analyzeSpecificPrs(inputPath, outputPath, requestedSampleIds, new PrAnalyzer(), System.out);
    }

    public static SpecificAnalysisResult retryFailedPrs(Path inputPath, Path outputPath) throws IOException {
        return retryFailedPrs(inputPath, outputPath, new PrAnalyzer(), System.out);
    }

    static SpecificAnalysisResult retryFailedPrs(Path inputPath, Path outputPath, PrAnalyzer analyzer, PrintStream out) throws IOException {
        List<String> header = CsvTools.readHeader(inputPath);
        List<Map<String, String>> rows = CsvTools.readRows(inputPath);
        validateHeader(header, inputPath);
        if (!header.contains(REANALYSIS_STATUS)) {
            throw new IllegalArgumentException("Missing required column in " + inputPath + ": " + REANALYSIS_STATUS);
        }
        List<String> failedSampleIds = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String status = row.getOrDefault(REANALYSIS_STATUS, "").trim();
            if (!status.isBlank() && !status.equalsIgnoreCase("Success")) {
                failedSampleIds.add(row.get(SAMPLE_ID));
            }
        }
        if (failedSampleIds.isEmpty()) {
            throw new IllegalArgumentException("No rows with unsuccessful re-analysis status found in " + inputPath);
        }
        return analyzeSpecificPrs(inputPath, outputPath, failedSampleIds, analyzer, out);
    }

    static SpecificAnalysisResult analyzeSpecificPrs(Path inputPath, Path outputPath, List<String> requestedSampleIds, PrAnalyzer analyzer, PrintStream out) throws IOException {
        if (Files.exists(outputPath)) {
            throw new IOException("Targeted analysis output already exists: " + outputPath + ". Choose a new output path to avoid overwriting work.");
        }
        if (requestedSampleIds.isEmpty()) {
            throw new IllegalArgumentException("At least one Sample ID is required.");
        }

        List<String> header = CsvTools.readHeader(inputPath);
        List<Map<String, String>> rows = CsvTools.readRows(inputPath);
        validateHeader(header, inputPath);
        Map<String, Integer> rowIndexes = indexRows(rows, inputPath);
        List<String> uniqueRequestedIds = uniqueRequestedIds(requestedSampleIds);
        for (String sampleId : uniqueRequestedIds) {
            validateSampleIdFormat(sampleId);
            if (!rowIndexes.containsKey(sampleId)) {
                throw new IllegalArgumentException("Sample ID not found in " + inputPath + ": " + sampleId);
            }
        }

        List<String> outputHeader = outputHeader(header);
        Map<String, Map<String, String>> updatedRowsById = new LinkedHashMap<>();
        List<Failure> failures = new ArrayList<>();
        int succeeded = 0;

        for (String sampleId : uniqueRequestedIds) {
            Map<String, String> inputRow = rows.get(rowIndexes.get(sampleId));
            out.println();
            out.println("Sample ID: " + sampleId);
            out.println("PR URL: " + inputRow.getOrDefault(PR_URL, ""));
            AttemptResult attempt = analyzeWithRetries(sampleId, inputRow, analyzer, out);
            Map<String, String> updatedRow = new LinkedHashMap<>(inputRow);
            if (attempt.detail() != null) {
                PrReportRow analyzed = attempt.detail().row();
                updatedRow.put(DISCLOSURE_EVIDENCE, analyzed.aiDisclosure() ? analyzed.aiDisclosureEvidence() : "");
                updatedRow.put(SCRIPT_PRESENT, analyzed.aiDisclosure() ? "Yes" : "No");
                if (updatedRow.containsKey(SCRIPT_CLASSIFICATION)) {
                    updatedRow.put(SCRIPT_CLASSIFICATION, analyzed.aiDisclosureClassification());
                }
                if (updatedRow.containsKey(SCRIPT_SOURCE)) {
                    updatedRow.put(SCRIPT_SOURCE, analyzed.aiDisclosureSource());
                }
                updatedRow.put(REANALYSIS_STATUS, "Success");
                updatedRow.put(REANALYSIS_ERROR, "");
                printDiagnostics(attempt.detail(), "Success", out);
                succeeded++;
            } else {
                updatedRow.put(DISCLOSURE_EVIDENCE, "");
                updatedRow.put(SCRIPT_PRESENT, "");
                if (updatedRow.containsKey(SCRIPT_CLASSIFICATION)) {
                    updatedRow.put(SCRIPT_CLASSIFICATION, "");
                }
                if (updatedRow.containsKey(SCRIPT_SOURCE)) {
                    updatedRow.put(SCRIPT_SOURCE, "");
                }
                updatedRow.put(REANALYSIS_STATUS, "Fetch Failed");
                updatedRow.put(REANALYSIS_ERROR, attempt.error());
                failures.add(new Failure(sampleId, attempt.error()));
                out.println("Fetch status: Fetch Failed");
                out.println("Error: " + attempt.error());
            }
            updatedRowsById.put(sampleId, updatedRow);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(csvLine(outputHeader));
            writer.newLine();
            for (Map<String, String> row : rows) {
                String sampleId = row.get(SAMPLE_ID);
                Map<String, String> outputRow = updatedRowsById.getOrDefault(sampleId, row);
                writer.write(csvLine(outputHeader.stream().map(column -> outputRow.getOrDefault(column, "")).toList()));
                writer.newLine();
            }
        }

        return new SpecificAnalysisResult(uniqueRequestedIds.size(), succeeded, failures.size(), rows.size(), failures);
    }

    private static AttemptResult analyzeWithRetries(String sampleId, Map<String, String> inputRow, PrAnalyzer analyzer, PrintStream out) {
        String repo = inputRow.get(REPO).trim();
        int number = Integer.parseInt(inputRow.get(PR_NUMBER).trim());
        try {
            return new AttemptResult(analyzer.analyzeExistingPullRequestDetail(repo, number), "");
        } catch (Exception e) {
            String reason = shortError(e.getMessage());
            out.println("Fetch failed for " + sampleId + ": " + reason);
            return new AttemptResult(null, reason);
        }
    }

    private static void printDiagnostics(PrAnalyzer.AnalysisDetail detail, String fetchStatus, PrintStream out) {
        AiDisclosureDetector.DetectionDiagnostics diagnostics = detail.diagnostics();
        DisclosureResult result = diagnostics.result();
        out.println("Fetch status: " + fetchStatus);
        printBlock(out, "Raw PR body", diagnostics.rawPrBody());
        printBlock(out, "Cleaned PR body after HTML-comment removal", diagnostics.cleanedPrBody());
        printList(out, "Checked AI checkbox responses", diagnostics.checkedCheckboxes());
        printList(out, "Unchecked AI checkbox lines ignored", diagnostics.uncheckedCheckboxes());
        printList(out, "Visible Generated-by fields", diagnostics.visibleGeneratedByFields());
        out.println("Detected disclosure evidence:");
        out.println("- " + (result.disclosed() ? result.evidence() : "None"));
        out.println();
        out.println("Final result:");
        out.println("Script AI Disclosure Present: " + (result.disclosed() ? "Yes" : "No"));
        out.println("Classification: " + displayClassification(result.classification()));
    }

    private static void printBlock(PrintStream out, String title, String value) {
        out.println();
        out.println(title + ":");
        out.println("----------------");
        out.println(value == null || value.isBlank() ? "(blank)" : value);
        out.println("----------------");
    }

    private static void printList(PrintStream out, String title, List<String> values) {
        out.println();
        out.println(title + ":");
        if (values.isEmpty()) {
            out.println("- None");
        } else {
            for (String value : values) {
                out.println("- " + value);
            }
        }
    }

    private static String displayClassification(String classification) {
        return switch (classification) {
            case "possible_positive" -> "Positive";
            case "possible_negative" -> "Negative";
            case "possible_ambiguous" -> "Ambiguous";
            default -> "None";
        };
    }

    private static List<String> outputHeader(List<String> inputHeader) {
        List<String> outputHeader = new ArrayList<>(inputHeader);
        if (!outputHeader.contains(REANALYSIS_STATUS)) {
            outputHeader.add(REANALYSIS_STATUS);
        }
        if (!outputHeader.contains(REANALYSIS_ERROR)) {
            outputHeader.add(REANALYSIS_ERROR);
        }
        return outputHeader;
    }

    private static void validateHeader(List<String> header, Path inputPath) {
        if (header.isEmpty()) {
            throw new IllegalArgumentException("Kappa sample is empty: " + inputPath);
        }
        for (String column : List.of(SAMPLE_ID, REPO, PR_NUMBER, PR_URL, DISCLOSURE_EVIDENCE, SCRIPT_PRESENT)) {
            if (!header.contains(column)) {
                throw new IllegalArgumentException("Missing required column in " + inputPath + ": " + column);
            }
        }
    }

    private static Map<String, Integer> indexRows(List<Map<String, String>> rows, Path inputPath) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String sampleId = requiredValue(row, SAMPLE_ID, inputPath);
            String repo = requiredValue(row, REPO, inputPath);
            String prNumberText = requiredValue(row, PR_NUMBER, inputPath);
            int prNumber;
            try {
                prNumber = Integer.parseInt(prNumberText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid PR # in " + inputPath + " for Sample ID " + sampleId + ": " + prNumberText);
            }
            String expectedSampleId = repo + "#" + prNumber;
            if (!sampleId.equals(expectedSampleId)) {
                throw new IllegalArgumentException("Sample ID does not match Repo and PR # in " + inputPath + ": expected " + expectedSampleId + " but found " + sampleId);
            }
            if (indexes.put(sampleId, i) != null) {
                throw new IllegalArgumentException("Duplicate Sample ID in " + inputPath + ": " + sampleId);
            }
        }
        return indexes;
    }

    private static String requiredValue(Map<String, String> row, String column, Path inputPath) {
        String value = row.getOrDefault(column, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing " + column + " in " + inputPath);
        }
        return value;
    }

    private static void validateSampleIdFormat(String sampleId) {
        int marker = sampleId.lastIndexOf('#');
        if (marker <= 0 || marker == sampleId.length() - 1) {
            throw new IllegalArgumentException("Invalid Sample ID. Expected Repo#PR: " + sampleId);
        }
        try {
            Integer.parseInt(sampleId.substring(marker + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Sample ID PR number. Expected Repo#PR: " + sampleId);
        }
    }

    private static List<String> uniqueRequestedIds(List<String> requestedSampleIds) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> unique = new ArrayList<>();
        for (String value : requestedSampleIds) {
            String sampleId = value == null ? "" : value.trim();
            if (!sampleId.isBlank() && seen.add(sampleId)) {
                unique.add(sampleId);
            }
        }
        return unique;
    }

    private static String csvLine(List<String> values) {
        return String.join(",", values.stream().map(CsvTools::csv).toList());
    }

    private static String shortError(String value) {
        if (value == null || value.isBlank()) return "Unknown error";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        int bodyIndex = cleaned.indexOf(" body=");
        if (bodyIndex >= 0) {
            cleaned = cleaned.substring(0, bodyIndex);
        }
        if (cleaned.length() > 180) {
            cleaned = cleaned.substring(0, 177) + "...";
        }
        return cleaned;
    }

    private record AttemptResult(PrAnalyzer.AnalysisDetail detail, String error) {
    }

    public record SpecificAnalysisResult(int requested, int succeeded, int failed, int rowsWritten, List<Failure> failures) {
        public SpecificAnalysisResult {
            failures = List.copyOf(failures);
        }
    }

    public record Failure(String sampleId, String reason) {
    }
}
