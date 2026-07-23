package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CsvWriter {
    private static final String MANUAL_REVIEW_REQUIRED = "MANUAL_REVIEW_REQUIRED";

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
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writePrDatasetHeader(writer);
            writer.newLine();
            for (PrReportRow row : rows) {
                writePrDatasetRow(writer, row);
                writer.newLine();
            }
        }
    }

    public static int appendReportRows(Path path, List<PrReportRow> rows) throws IOException {
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
        boolean writeHeader = !Files.exists(path) || Files.size(path) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (writeHeader) {
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
        Set<String> ids = new LinkedHashSet<>();
        if (!Files.exists(path) || Files.size(path) == 0) return ids;
        for (Map<String, String> row : CsvTools.readRows(path)) {
            addCompletedId(ids, row.get("Repository"), row.get("Pull request number"));
        }
        return ids;
    }

    public static Set<String> completedPrDatasetIds(Path path) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        if (!Files.exists(path) || Files.size(path) == 0) return ids;
        for (Map<String, String> row : CsvTools.readRows(path)) {
            addCompletedId(ids, row.get("Repo"), row.get("PR #"));
        }
        return ids;
    }

    public static void writeRepoComplianceSummary(Path path, List<PrReportRow> rows, Map<String, Integer> botPrsExcluded) throws IOException {
        Map<String, List<PrReportRow>> rowsByRepo = new LinkedHashMap<>();
        for (PrReportRow row : rows) {
            rowsByRepo.computeIfAbsent(row.repository(), ignored -> new java.util.ArrayList<>()).add(row);
        }
        for (String repo : botPrsExcluded.keySet()) {
            rowsByRepo.computeIfAbsent(repo, ignored -> new java.util.ArrayList<>());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Repo,Eligible PRs Reviewed,Bot PRs Excluded,AI Disclosure Present Count,"
                    + "Positive Disclosure Count,Negative Disclosure Count,Ambiguous Disclosure Count,"
                    + "No Disclosure Count,Manual Review Required Count,Disclosure Compliance Rate");
            writer.newLine();
            for (Map.Entry<String, List<PrReportRow>> entry : rowsByRepo.entrySet()) {
                String repo = entry.getKey();
                List<PrReportRow> repoRows = entry.getValue();
                int eligible = repoRows.size();
                long present = repoRows.stream().filter(PrReportRow::aiDisclosure).count();
                long positive = countClassification(repoRows, "possible_positive");
                long negative = countClassification(repoRows, "possible_negative");
                long ambiguous = countClassification(repoRows, "possible_ambiguous");
                long noDisclosure = eligible - present;
                long manualReviewRequired = eligible;
                double complianceRate = eligible == 0 ? 0.0 : present * 1.0 / eligible;

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
                        csv(String.format(Locale.ROOT, "%.4f", complianceRate))
                ));
                writer.newLine();
            }
        }
    }

    private static long countClassification(List<PrReportRow> rows, String classification) {
        return rows.stream().filter(row -> classification.equals(row.aiDisclosureClassification())).count();
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
                csv("HTML scrape error")
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
                csv(Boolean.toString(row.aiDisclosure())),
                csv(Boolean.toString(row.merged())),
                csv(row.aiDisclosureEvidence()),
                csv(Boolean.toString(row.htmlScrapeSuccess())),
                csv(row.htmlScrapeError())
        ));
    }

    private static void writePrDatasetHeader(BufferedWriter writer) throws IOException {
        writer.write("Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Bot,Status,"
                + "AI Disclosure Required,AI Disclosure Present,Disclosure Classification,Disclosure Text");
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
                csv(yesNo(row.aiDisclosure())),
                csv(disclosureClassification(row)),
                csv(disclosureText(row))
        ));
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
        if (!row.aiDisclosure()) return "None";
        return MANUAL_REVIEW_REQUIRED;
    }

    private static String disclosureText(PrReportRow row) {
        if (!row.aiDisclosure()) return "";
        return row.aiDisclosureEvidence();
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
