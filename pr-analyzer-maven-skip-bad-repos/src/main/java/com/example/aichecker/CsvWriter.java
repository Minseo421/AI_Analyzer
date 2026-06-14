package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CsvWriter {
    private static final String MANUAL_REVIEW_REQUIRED = "MANUAL_REVIEW_REQUIRED";

    public static void write(Path path, List<PrReportRow> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
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
            writer.newLine();
            for (PrReportRow row : rows) {
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
                writer.newLine();
            }
        }
    }

    public static void writePrDataset(Path path, List<PrReportRow> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Bot,Status,"
                    + "AI Disclosure Required,AI Disclosure Present,Disclosure Classification,Disclosure Text");
            writer.newLine();
            for (PrReportRow row : rows) {
                writer.write(String.join(",",
                        // Repo, PR #, URL, title, author, and dates come directly from the GitHub Pulls API.
                        csv(row.repository()),
                        csv(Integer.toString(row.pullRequestNumber())),
                        csv(row.url()),
                        csv(row.title()),
                        csv(row.author()),
                        csv(row.createdAt()),
                        csv(row.closedAt()),
                        csv(row.mergedAt()),
                        // Bot is derived from GitHub user type and login-name bot heuristics.
                        csv(yesNo(!row.humanAuthor())),
                        // Status is derived from GitHub state plus merged_at presence.
                        csv(datasetStatus(row)),
                        // Required disclosure depends on repository policy coding and remains a manual workbook field.
                        csv(MANUAL_REVIEW_REQUIRED),
                        // Disclosure present/text come from the existing PR body and rendered HTML detector.
                        csv(yesNo(row.aiDisclosure())),
                        // The detector can flag AI-related text, but final classification still needs researcher validation.
                        csv(disclosureClassification(row)),
                        csv(disclosureText(row))
                ));
                writer.newLine();
            }
        }
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
                    + "No Disclosure Count,Manual Review Required Count,Compliance Rate");
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
