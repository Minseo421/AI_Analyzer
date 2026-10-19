package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CsvWriter {
    private static final String MANUAL_REVIEW_REQUIRED = "MANUAL_REVIEW_REQUIRED";
    private static final String ANALYSIS_STARTED_LABEL = "# Analysis Started UTC";
    private static final String ANALYSIS_COMPLETED_LABEL = "# Analysis Completed UTC";
    private static final String ANALYSIS_COMPLETION_PENDING = "PENDING";

    public static void write(Path path, List<PrReportRow> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeReportHeader(writer);
            writer.newLine();
            for (PrReportRow row : rows) {
                writeReportRow(writer, row);
                writer.newLine();
            }
        }
    }

    public static void writePrDataset(Path path, List<PrReportRow> rows) throws IOException {
        String timestamp = Instant.now().toString();
        writePrDataset(path, rows, timestamp, timestamp);
    }

    public static void writePrDataset(Path path, List<PrReportRow> rows, String analysisStartedUtc, String analysisCompletedUtc) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeAnalysisMetadata(writer, analysisStartedUtc, analysisCompletedUtc);
            writePrDatasetHeader(writer);
            writer.newLine();
            for (PrReportRow row : rows) {
                writePrDatasetRow(writer, row);
                writer.newLine();
            }
        }
    }

    public static int appendReportRows(Path path, List<PrReportRow> rows) throws IOException {
        validateAppendSchema(path, false);
        boolean writeHeader = !Files.exists(path) || Files.size(path) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writeReportHeader(writer);
                writer.newLine();
            }
            for (PrReportRow row : rows) {
                writeReportRow(writer, row);
                writer.newLine();
                writer.flush();
            }
        }
        return rows.size();
    }

    public static int appendPrDatasetRows(Path path, List<PrReportRow> rows) throws IOException {
        return appendPrDatasetRows(path, rows, Instant.now().toString());
    }

    public static int appendPrDatasetRows(Path path, List<PrReportRow> rows, String analysisStartedUtc) throws IOException {
        validateAppendSchema(path, true);
        boolean writeHeader = !Files.exists(path) || Files.size(path) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writeAnalysisMetadata(writer, analysisStartedUtc, ANALYSIS_COMPLETION_PENDING);
                writePrDatasetHeader(writer);
                writer.newLine();
            }
            for (PrReportRow row : rows) {
                writePrDatasetRow(writer, row);
                writer.newLine();
                writer.flush();
            }
        }
        return rows.size();
    }

    public static Set<String> completedReportIds(Path path) throws IOException {
        validateAppendSchema(path, false);
        Set<String> ids = new LinkedHashSet<>();
        if (!Files.exists(path) || Files.size(path) == 0) return ids;
        for (Map<String, String> row : CsvTools.readRows(path)) {
            addCompletedId(ids, row.get("Repository"), row.get("Pull request number"));
        }
        return ids;
    }

    public static Set<String> completedPrDatasetIds(Path path) throws IOException {
        validateAppendSchema(path, true);
        Set<String> ids = new LinkedHashSet<>();
        if (!Files.exists(path) || Files.size(path) == 0) return ids;
        for (Map<String, String> row : CsvTools.readRows(path)) {
            addCompletedId(ids, row.get("Repo"), row.get("PR #"));
        }
        return ids;
    }

    public static void writeRepoComplianceSummary(Path path, List<PrReportRow> rows, Map<String, Integer> botPrsExcluded) throws IOException {
        String timestamp = Instant.now().toString();
        writeRepoComplianceSummary(path, rows, botPrsExcluded, timestamp, timestamp);
    }

    public static void writeRepoComplianceSummary(Path path, List<PrReportRow> rows, Map<String, Integer> botPrsExcluded, String analysisStartedUtc, String analysisCompletedUtc) throws IOException {
        Map<String, List<PrReportRow>> rowsByRepo = new LinkedHashMap<>();
        for (PrReportRow row : rows) {
            rowsByRepo.computeIfAbsent(row.repository(), ignored -> new java.util.ArrayList<>()).add(row);
        }
        for (String repo : botPrsExcluded.keySet()) {
            rowsByRepo.computeIfAbsent(repo, ignored -> new java.util.ArrayList<>());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeAnalysisMetadata(writer, analysisStartedUtc, analysisCompletedUtc);
            writer.write("Repo,Eligible PRs Reviewed,Bot PRs Excluded,AI Disclosure Present Count,"
                    + "Positive Disclosure Count,Negative Disclosure Count,Ambiguous Disclosure Count,"
                    + "No Disclosure Count,Manual Review Required Count,Automated Disclosure Rate,Complete Evidence Count,Unknown Disclosure Count,Policy Acknowledgement Count");
            writer.newLine();
            for (Map.Entry<String, List<PrReportRow>> entry : rowsByRepo.entrySet()) {
                String repo = entry.getKey();
                List<PrReportRow> repoRows = entry.getValue();
                int eligible = repoRows.size();
                long complete = repoRows.stream().filter(PrReportRow::evidenceComplete).count();
                long present = repoRows.stream().filter(PrReportRow::evidenceComplete).filter(PrReportRow::aiDisclosure).count();
                long positive = countClassification(repoRows, "possible_positive");
                long negative = countClassification(repoRows, "possible_negative");
                long ambiguous = countClassification(repoRows, "possible_ambiguous");
                long noDisclosure = complete - present;
                long manualReviewRequired = eligible;
                double disclosureRate = complete == 0 ? Double.NaN : present * 1.0 / complete;

                writer.write(String.join(",",
                        csv(repo),
                        csv(Integer.toString(eligible)),
                        csv(Integer.toString(botPrsExcluded.getOrDefault(repo, 0))),
                        csv(Long.toString(present)),
                        csv(Long.toString(positive)),
                        csv(Long.toString(negative)),
                        csv(Long.toString(ambiguous)),
                        csv(Long.toString(noDisclosure)),
                        csv(Long.toString(manualReviewRequired)),
                        csv(complete == 0 ? "" : String.format(Locale.ROOT, "%.4f", disclosureRate)),
                        csv(Long.toString(complete)),
                        csv(Long.toString(eligible - complete)),
                        csv(Long.toString(countClassification(repoRows, "policy_acknowledgement")))
                ));
                writer.newLine();
            }
        }
    }

    public static void markAnalysisCompleted(Path path, String analysisCompletedUtc) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            return;
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String pendingLine = csv(ANALYSIS_COMPLETED_LABEL) + "," + csv(ANALYSIS_COMPLETION_PENDING);
        String completedLine = csv(ANALYSIS_COMPLETED_LABEL) + "," + csv(analysisCompletedUtc);
        if (content.contains(pendingLine)) {
            Files.writeString(path, content.replace(pendingLine, completedLine), StandardCharsets.UTF_8);
        }
    }

    private static long countClassification(List<PrReportRow> rows, String classification) {
        return rows.stream().filter(PrReportRow::evidenceComplete).filter(row -> classification.equals(row.aiDisclosureClassification())).count();
    }

    private static void writeReportHeader(BufferedWriter writer) throws IOException {
        writer.write(String.join(",",
                csv("Repository"),
                csv("Pull request number"),
                csv("URL"),
                csv("Author"),
                csv("State"),
                csv("Closed"),
                csv("Human author"),
                csv("GitHub user type"),
                csv("AI Disclosure"),
                csv("Merged"),
                csv("AI Disclosure evidence"),
                csv("HTML scrape success"),
                csv("HTML scrape error"), csv("Evidence Completeness"), csv("Evidence Source"),
                csv("Evidence ID"), csv("Evidence URL"), csv("Evidence Author"), csv("Evidence Created At"),
                csv("Source Coverage Rule"), csv("Review Status")
        ));
    }

    private static void writeReportRow(BufferedWriter writer, PrReportRow row) throws IOException {
        writer.write(String.join(",",
                csv(row.repository()),
                csv(Integer.toString(row.pullRequestNumber())),
                csv(row.url()),
                csv(row.author()),
                csv(row.state()),
                csv(Boolean.toString(row.closed())),
                csv(Boolean.toString(row.humanAuthor())),
                csv(row.githubUserType()),
                csv(row.disclosureStatus()),
                csv(Boolean.toString(row.merged())),
                csv(row.aiDisclosureEvidence()),
                csv(Boolean.toString(row.htmlScrapeSuccess())),
                csv(row.htmlScrapeError()), csv(row.evidenceCompleteness()), csv(row.aiDisclosureSource()),
                csv(row.evidenceId()), csv(row.evidenceUrl()), csv(row.author()), csv(row.createdAt()),
                csv(row.sourceCoverageRule()), csv(row.reviewStatus())
        ));
    }

    private static void writePrDatasetHeader(BufferedWriter writer) throws IOException {
        writer.write("Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Bot,Status,"
                + "AI Disclosure Required,AI Disclosure Present,Disclosure Classification,Disclosure Text,"
                + "Evidence Completeness,Evidence Source,Evidence ID,Evidence URL,Evidence Author,Evidence Created At,"
                + "Source Coverage Rule,HTML Scrape Success,HTML Scrape Error,Review Status,Policy Acknowledgement Only,"
                + "Policy Requirement,Policy Effective From,Policy Effective Through,Policy Source URL,Policy Coded At,"
                + "Policy Coder,Reviewed AI Use,Reviewed Disclosure Present,Reviewed Compliance,Review Evidence URL,Reviewed At,Reviewer");
    }

    private static void writePrDatasetRow(BufferedWriter writer, PrReportRow row) throws IOException {
        writer.write(String.join(",",
                csv(row.repository()),
                csv(Integer.toString(row.pullRequestNumber())),
                csv(row.url()),
                csv(row.title()),
                csv(row.author()),
                csv(row.createdAt()),
                csv(row.closedAt()),
                csv(row.mergedAt()),
                csv(yesNo(!row.humanAuthor())),
                csv(datasetStatus(row)),
                csv(MANUAL_REVIEW_REQUIRED),
                csv(row.disclosureStatus()),
                csv(disclosureClassification(row)),
                csv(disclosureText(row)),
                csv(row.evidenceCompleteness()), csv(row.aiDisclosureSource()), csv(row.evidenceId()),
                csv(row.evidenceUrl()), csv(row.author()), csv(row.createdAt()), csv(row.sourceCoverageRule()),
                csv(yesNo(row.htmlScrapeSuccess())), csv(row.htmlScrapeError()), csv(row.reviewStatus()),
                csv(yesNo("policy_acknowledgement".equals(row.aiDisclosureClassification()))),
                csv("Unresolved"), csv(""), csv(""), csv(""), csv(""), csv(""),
                csv("Unknown"), csv("Unknown"), csv("Unresolved"), csv(""), csv(""), csv("")
        ));
    }

    private static void writeAnalysisMetadata(BufferedWriter writer, String analysisStartedUtc, String analysisCompletedUtc) throws IOException {
        writer.write(csv(ANALYSIS_STARTED_LABEL) + "," + csv(analysisStartedUtc));
        writer.newLine();
        writer.write(csv(ANALYSIS_COMPLETED_LABEL) + "," + csv(analysisCompletedUtc));
        writer.newLine();
        writer.newLine();
    }

    private static void addCompletedId(Set<String> ids, String repository, String number) {
        String repo = repository == null ? "" : repository.trim();
        String pr = number == null ? "" : number.trim();
        if (!repo.isBlank() && !pr.isBlank()) {
            ids.add(repo + "#" + pr);
        }
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String datasetStatus(PrReportRow row) {
        if (row.merged()) return "Merged";
        if (row.closed()) return "Closed";
        return "Unknown";
    }

    private static String disclosureClassification(PrReportRow row) {
        if (!row.evidenceComplete()) return "Unknown";
        return row.aiDisclosureClassification();
    }

    private static String disclosureText(PrReportRow row) {
        if (!row.aiDisclosure() && !"policy_acknowledgement".equals(row.aiDisclosureClassification())) return "";
        return row.aiDisclosureEvidence();
    }

    private static void validateAppendSchema(Path path, boolean dataset) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) return;
        var buffer = new java.io.StringWriter();
        try (var writer = new BufferedWriter(buffer)) {
            if (dataset) writePrDatasetHeader(writer); else writeReportHeader(writer);
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("\"#")) continue;
                if (line.equals(buffer.toString())) return;
                break;
            }
        }
        throw new IOException("Incompatible CSV evidence schema: " + path + ". Recollect into a new file; legacy rows cannot be assumed complete.");
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
