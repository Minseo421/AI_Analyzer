package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KappaSampleReanalysisWorkflow {
    private static final String SAMPLE_ID = "Sample ID";
    private static final String REPO = "Repo";
    private static final String PR_NUMBER = "PR #";
    private static final String DISCLOSURE_EVIDENCE = "Disclosure Text detected by script";
    private static final String SCRIPT_PRESENT = "Script AI Disclosure Present";
    private static final String SCRIPT_CLASSIFICATION = "Script Disclosure Classification";
    private static final String SCRIPT_SOURCE = "Script Disclosure Source";
    private static final String REANALYSIS_STATUS = "Reanalysis Status";
    private static final String REANALYSIS_ERROR = "Reanalysis Error";

    public static ReanalysisResult reanalyze(Path inputPath, Path outputPath) throws IOException {
        return reanalyze(inputPath, outputPath, new PrAnalyzer());
    }

    static ReanalysisResult reanalyze(Path inputPath, Path outputPath, PrAnalyzer analyzer) throws IOException {
        if (Files.exists(outputPath)) {
            throw new IOException("Reanalysis output already exists: " + outputPath + ". Choose a new output path to avoid overwriting work.");
        }
        List<String> header = CsvTools.readHeader(inputPath);
        List<Map<String, String>> rows = CsvTools.readRows(inputPath);
        validateHeader(header, inputPath);
        validateRows(rows, inputPath);

        List<String> outputHeader = outputHeader(header);
        List<Failure> failures = new ArrayList<>();
        int success = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(csvLine(outputHeader));
            writer.newLine();
            for (Map<String, String> inputRow : rows) {
                Map<String, String> outputRow = new LinkedHashMap<>(inputRow);
                String sampleId = inputRow.get(SAMPLE_ID).trim();
                try {
                    PrReportRow analyzed = analyzer.analyzeExistingPullRequest(inputRow.get(REPO).trim(), Integer.parseInt(inputRow.get(PR_NUMBER).trim()));
                    outputRow.put(DISCLOSURE_EVIDENCE, analyzed.aiDisclosure() ? analyzed.aiDisclosureEvidence() : "");
                    outputRow.put(SCRIPT_PRESENT, analyzed.aiDisclosure() ? "Yes" : "No");
                    if (outputRow.containsKey(SCRIPT_CLASSIFICATION)) {
                        outputRow.put(SCRIPT_CLASSIFICATION, analyzed.aiDisclosureClassification());
                    }
                    if (outputRow.containsKey(SCRIPT_SOURCE)) {
                        outputRow.put(SCRIPT_SOURCE, analyzed.aiDisclosureSource());
                    }
                    outputRow.put(REANALYSIS_STATUS, "Success");
                    outputRow.put(REANALYSIS_ERROR, "");
                    success++;
                } catch (Exception e) {
                    String reason = shortError(e.getMessage());
                    outputRow.put(DISCLOSURE_EVIDENCE, "");
                    outputRow.put(SCRIPT_PRESENT, "");
                    if (outputRow.containsKey(SCRIPT_CLASSIFICATION)) {
                        outputRow.put(SCRIPT_CLASSIFICATION, "");
                    }
                    if (outputRow.containsKey(SCRIPT_SOURCE)) {
                        outputRow.put(SCRIPT_SOURCE, "");
                    }
                    outputRow.put(REANALYSIS_STATUS, "Fetch Failed");
                    outputRow.put(REANALYSIS_ERROR, reason);
                    failures.add(new Failure(sampleId, reason));
                }
                writer.write(csvLine(outputHeader.stream().map(column -> outputRow.getOrDefault(column, "")).toList()));
                writer.newLine();
            }
        }
        return new ReanalysisResult(rows.size(), success, failures.size(), rows.size(), failures);
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
        requireColumn(header, SAMPLE_ID, inputPath);
        requireColumn(header, REPO, inputPath);
        requireColumn(header, PR_NUMBER, inputPath);
        requireColumn(header, "PR URL", inputPath);
        requireColumn(header, DISCLOSURE_EVIDENCE, inputPath);
        requireColumn(header, SCRIPT_PRESENT, inputPath);
    }

    private static void validateRows(List<Map<String, String>> rows, Path inputPath) {
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
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
            if (!seen.add(sampleId)) {
                throw new IllegalArgumentException("Duplicate Sample ID in " + inputPath + ": " + sampleId);
            }
        }
    }

    private static void requireColumn(List<String> header, String column, Path inputPath) {
        if (!header.contains(column)) {
            throw new IllegalArgumentException("Missing required column in " + inputPath + ": " + column);
        }
    }

    private static String requiredValue(Map<String, String> row, String column, Path inputPath) {
        String value = row.getOrDefault(column, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing " + column + " in " + inputPath);
        }
        return value;
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

    public record ReanalysisResult(int totalInputRows, int succeeded, int failed, int rowsWritten, List<Failure> failures) {
        public ReanalysisResult {
            failures = List.copyOf(failures);
        }
    }

    public record Failure(String sampleId, String reason) {
    }
}
