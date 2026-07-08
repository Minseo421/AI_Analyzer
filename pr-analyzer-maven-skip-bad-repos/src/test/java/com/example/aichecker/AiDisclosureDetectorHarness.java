package com.example.aichecker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AiDisclosureDetectorHarness {
    public static void main(String[] args) throws Exception {
        AiDisclosureDetector detector = new AiDisclosureDetector();

        DisclosureResult negative = detector.detect("generative AI tooling used to co-author this PR? No", "");
        require(negative.disclosed(), "negative disclosure should count as present");
        require("possible_negative".equals(negative.classification()), "negative disclosure classification");
        require(negative.evidence().contains("No"), "negative evidence should preserve answer");

        DisclosureResult positive = detector.detect("generative AI tooling used to co-author this PR? Yes", "");
        require(positive.disclosed(), "positive disclosure should count as present");
        require("possible_positive".equals(positive.classification()), "positive disclosure classification");

        DisclosureResult chrome = detector.detect("", "GitHub Copilot Write better code with AI GitHub Copilot app Direct agents from issue to merge MCP Registry Actions Automate any workflow Codespaces Instant dev environments Issues Plan and track work Navigation Menu Skip to content");
        require(!chrome.disclosed(), "GitHub page chrome should not count as disclosure");

        DisclosureResult filename = detector.detect("Update CLAUDE.md", "");
        require(!filename.disclosed(), "AI-related filename should not count as disclosure");

        DisclosureResult ambiguous = detector.detect("This policy requires AI disclosure before merge.", "");
        require(ambiguous.disclosed(), "ambiguous AI disclosure mention should be detected for review");
        require("possible_ambiguous".equals(ambiguous.classification()), "ambiguous classification");

        Path summary = Files.createTempFile("repo-compliance-summary", ".csv");
        CsvWriter.writeRepoComplianceSummary(summary, List.of(
                row("owner/repo", 1, true, "possible_positive"),
                row("owner/repo", 2, true, "possible_negative"),
                row("owner/repo", 3, false, "none")
        ), Map.of("owner/repo", 2));
        List<String> lines = Files.readAllLines(summary, StandardCharsets.UTF_8);
        require(lines.size() == 2, "summary should contain header and one repo row");
        require(lines.get(1).equals("\"owner/repo\",\"3\",\"2\",\"2\",\"1\",\"1\",\"0\",\"1\",\"3\",\"0.6667\""), "summary counts and compliance rate");

        Path coderA = Files.createTempFile("coder-a-labels", ".csv");
        Path coderB = Files.createTempFile("coder-b-labels", ".csv");
        Files.writeString(coderA, labelsCsv("Yes", "Positive", "No", "None"), StandardCharsets.UTF_8);
        Files.writeString(coderB, labelsCsv("Yes", "Positive", "Yes", "Ambiguous"), StandardCharsets.UTF_8);
        Path kappa = Files.createTempFile("kappa-results", ".csv");
        KappaWorkflow.calculateKappa(coderA, coderB, kappa);
        String kappaText = Files.readString(kappa, StandardCharsets.UTF_8);
        require(kappaText.contains("\"Disclosure Present\",\"Matched PRs used\",\"2\""), "kappa matched rows");
        require(kappaText.contains("Disagreement Rows"), "kappa disagreement section");

        Path sample = Files.createTempFile("kappa-sample", ".csv");
        KappaWorkflow.SampleWriteResult sampleResult = KappaWorkflow.writeSampleWithSummary(sample, List.of(
                row("fedify-dev/fedify", 10, false, "none"),
                row("fedify-dev/fedify", 9, true, "possible_positive"),
                row("apache/airflow", 20, false, "none"),
                row("apache/airflow", 19, false, "none"),
                row("cloudnative-pg/cloudnative-pg", 30, true, "possible_negative"),
                row("cloudnative-pg/cloudnative-pg", 29, false, "none")
        ));
        require(sampleResult.totalRows() == 6, "combined kappa sample should include every repository row");
        require(sampleResult.rowsByRepository().get("fedify-dev/fedify") == 2, "fedify sample count");
        require(sampleResult.rowsByRepository().get("apache/airflow") == 2, "airflow sample count");
        require(sampleResult.rowsByRepository().get("cloudnative-pg/cloudnative-pg") == 2, "cloudnative sample count");
        List<Map<String, String>> sampleRows = CsvTools.readRows(sample);
        require(sampleRows.size() == 6, "combined kappa CSV row count");
        require("fedify-dev/fedify#10".equals(sampleRows.get(0).get("Sample ID")), "first sample id should be stable");
        require("apache/airflow#20".equals(sampleRows.get(2).get("Sample ID")), "second repository should be appended");
        require("cloudnative-pg/cloudnative-pg#30".equals(sampleRows.get(4).get("Sample ID")), "third repository should be appended");

        try {
            KappaWorkflow.codeSample(sample, coderA);
            throw new AssertionError("codeSample should refuse to overwrite an existing labels file");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("already exists"), "overwrite refusal message");
        }

        System.out.println("AiDisclosureDetectorHarness passed");
    }

    private static String labelsCsv(String present1, String classification1, String present2, String classification2) {
        return "Sample ID,Repo,PR #,PR URL,Disclosure Present,Disclosure Classification,Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1," + present1 + "," + classification1 + ",\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2," + present2 + "," + classification2 + ",\n";
    }

    private static PrReportRow row(String repo, int number, boolean disclosed, String classification) {
        return new PrReportRow(
                repo,
                number,
                "https://github.com/" + repo + "/pull/" + number,
                "Test PR",
                "2026-01-01T00:00:00Z",
                "2026-01-02T00:00:00Z",
                "",
                "contributor",
                "closed",
                true,
                true,
                "User",
                disclosed,
                false,
                disclosed ? "AI disclosure evidence" : "No contributor AI disclosure text detected",
                classification,
                "PR body",
                true,
                ""
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
