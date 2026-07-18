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

        DisclosureResult uncheckedTemplate = detector.detect("""
                <!--
                If generative AI tooling has been used in the process of authoring this PR, please
                change below checkbox to `[X]` followed by the name of the tool, uncomment the "Generated-by".
                -->

                - [ ] Yes (please specify the tool below)
                """, "");
        require(!uncheckedTemplate.disclosed(), "unchecked affirmative template checkbox should not count");

        DisclosureResult uncheckedVisibleQuestion = detector.detect("""
                ## AI usage disclosure
                - [ ] Yes
                - [ ] No
                Generated-by:
                """, "");
        require(!uncheckedVisibleQuestion.disclosed(), "unchanged visible checkbox template should not count");

        DisclosureResult checkedYes = detector.detect("- [x] Yes - GitHub Copilot", "");
        require(checkedYes.disclosed(), "checked affirmative checkbox should count");
        require("possible_positive".equals(checkedYes.classification()), "checked affirmative classification");
        require(checkedYes.evidence().contains("GitHub Copilot"), "checked affirmative evidence");

        DisclosureResult checkedAiToolingUsed = detector.detect("* [X] AI tooling was used", "");
        require(checkedAiToolingUsed.disclosed(), "checked AI tooling checkbox should count");
        require("possible_positive".equals(checkedAiToolingUsed.classification()), "checked AI tooling classification");

        DisclosureResult checkedNo = detector.detect("- [X] No generative AI was used", "");
        require(checkedNo.disclosed(), "checked negative checkbox should count");
        require("possible_negative".equals(checkedNo.classification()), "checked negative classification");

        DisclosureResult checkboxChoice = detector.detect("""
                - [ ] Yes
                - [x] No
                """, "");
        require(checkboxChoice.disclosed(), "checked no in multiple-choice template should count");
        require("possible_negative".equals(checkboxChoice.classification()), "checked no choice classification");

        DisclosureResult contradictoryCheckboxes = detector.detect("""
                - [x] Yes
                - [x] No
                """, "");
        require(contradictoryCheckboxes.disclosed(), "contradictory checked boxes should be flagged");
        require("possible_ambiguous".equals(contradictoryCheckboxes.classification()), "contradictory checked boxes classification");

        DisclosureResult commentedGeneratedBy = detector.detect("<!-- Generated-by: ChatGPT -->", "");
        require(!commentedGeneratedBy.disclosed(), "commented Generated-by should not count");

        DisclosureResult emptyGeneratedBy = detector.detect("Generated-by:", "");
        require(!emptyGeneratedBy.disclosed(), "empty Generated-by should not count");

        DisclosureResult completedGeneratedBy = detector.detect("Generated-by: Claude", "");
        require(completedGeneratedBy.disclosed(), "completed Generated-by should count");
        require("possible_positive".equals(completedGeneratedBy.classification()), "completed Generated-by classification");

        requirePositive(detector.detect("I used ChatGPT to generate the initial implementation.", ""), "explicit ChatGPT statement");
        requirePositive(detector.detect("This PR was written with GitHub Copilot.", ""), "written with Copilot statement");
        requirePositive(detector.detect("Claude helped generate the tests.", ""), "Claude helped statement");
        requireNegative(detector.detect("No generative AI tools were used for this PR.", ""), "explicit no AI statement");
        requireNegative(detector.detect("I did not use AI assistance.", ""), "explicit did not use AI statement");

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

        Path consensusCoderA = Files.createTempFile("consensus-coder-a", ".csv");
        Path consensusCoderB = Files.createTempFile("consensus-coder-b", ".csv");
        Files.writeString(consensusCoderA, labelsCsv(
                "Yes", "Positive",
                "No", "None",
                "Yes", "Positive",
                "No", "None"
        ), StandardCharsets.UTF_8);
        Files.writeString(consensusCoderB, labelsCsv(
                "Yes", "Positive",
                "Yes", "Ambiguous",
                "Yes", "Positive",
                "No", "None"
        ), StandardCharsets.UTF_8);
        Path consensus = tempMissingPath("consensus-labels", ".csv");
        ConsensusWorkflow.ConsensusResult consensusResult = ConsensusWorkflow.createConsensus(consensusCoderA, consensusCoderB, consensus);
        require(consensusResult.matchedRows() == 4, "consensus matched rows");
        require(consensusResult.fullAgreements() == 3, "consensus full agreements");
        require(consensusResult.disagreements() == 1, "consensus disagreements");
        List<Map<String, String>> consensusRows = CsvTools.readRows(consensus);
        require("Agreed".equals(consensusRows.get(0).get("Agreement Status")), "agreed row status");
        require("Needs Resolution".equals(consensusRows.get(1).get("Agreement Status")), "disagreement row status");
        require("No".equals(consensusRows.get(1).get("Coder A Disclosure Present")), "coder A present preserved");
        require("Yes".equals(consensusRows.get(1).get("Coder B Disclosure Present")), "coder B present preserved");
        require(consensusRows.get(1).get("Consensus Disclosure Present").isBlank(), "disagreed present left blank");

        try {
            ConsensusWorkflow.createConsensus(consensusCoderA, consensusCoderB, consensus);
            throw new AssertionError("createConsensus should refuse to overwrite an existing consensus file");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("already exists"), "consensus overwrite refusal");
        }

        Path detectorSample = Files.createTempFile("detector-sample", ".csv");
        Files.writeString(detectorSample, sampleCsv(), StandardCharsets.UTF_8);
        Path resolvedConsensus = Files.createTempFile("resolved-consensus", ".csv");
        Files.writeString(resolvedConsensus, resolvedConsensusCsv(), StandardCharsets.UTF_8);
        Path detectorValidation = tempMissingPath("detector-validation", ".csv");
        ConsensusWorkflow.DetectorValidationResult validationResult = ConsensusWorkflow.validateDetector(detectorSample, resolvedConsensus, detectorValidation);
        require(validationResult.totalMatchedRows() == 4, "detector matched rows");
        require(validationResult.truePositives() == 1, "true positives");
        require(validationResult.trueNegatives() == 1, "true negatives");
        require(validationResult.falsePositives() == 1, "false positives");
        require(validationResult.falseNegatives() == 1, "false negatives");
        String validationText = Files.readString(detectorValidation, StandardCharsets.UTF_8);
        require(validationText.contains("\"Summary\",\"Accuracy\",\"0.5000\""), "detector accuracy");
        require(validationText.contains("\"Summary\",\"Precision\",\"0.5000\""), "detector precision");
        require(validationText.contains("\"owner/repo#3\",\"owner/repo\",\"3\",\"https://github.com/owner/repo/pull/3\",\"No\",\"Yes\",\"False Negative\""), "false negative detail");

        Path unresolvedConsensus = Files.createTempFile("unresolved-consensus", ".csv");
        Files.writeString(unresolvedConsensus, unresolvedConsensusCsv(), StandardCharsets.UTF_8);
        try {
            ConsensusWorkflow.validateDetector(detectorSample, unresolvedConsensus, tempMissingPath("bad-detector-validation", ".csv"));
            throw new AssertionError("validateDetector should require completed consensus values");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("Consensus Disclosure Present"), "incomplete consensus validation");
        }

        Path humanLabelsBeforeReanalysis = Files.createTempFile("human-labels-before-reanalysis", ".csv");
        Path consensusBeforeReanalysis = Files.createTempFile("consensus-before-reanalysis", ".csv");
        Files.writeString(humanLabelsBeforeReanalysis, labelsCsv("Yes", "Positive", "No", "None"), StandardCharsets.UTF_8);
        Files.writeString(consensusBeforeReanalysis, resolvedConsensusCsv(), StandardCharsets.UTF_8);
        String humanLabelsOriginal = Files.readString(humanLabelsBeforeReanalysis, StandardCharsets.UTF_8);
        String consensusOriginal = Files.readString(consensusBeforeReanalysis, StandardCharsets.UTF_8);

        Path reanalysisInput = Files.createTempFile("kappa-sample-reanalysis-input", ".csv");
        Files.writeString(reanalysisInput, reanalysisInputCsv(), StandardCharsets.UTF_8);
        FakeGitHubClient fakeClient = new FakeGitHubClient();
        fakeClient.add("owner/repo", 1, """
                <!-- If generative AI tooling has been used, select Yes. -->
                - [ ] Yes (please specify the tool below)
                """);
        fakeClient.add("owner/repo", 2, "- [x] Yes - GitHub Copilot");
        fakeClient.add("owner/repo", 3, "- [X] No generative AI was used");
        fakeClient.add("owner/repo", 4, "I used ChatGPT to generate the initial implementation.");
        fakeClient.fail("owner/repo", 5, "fixture fetch failed");
        Path reanalysisOutput = tempMissingPath("kappa-sample-reanalyzed", ".csv");
        KappaSampleReanalysisWorkflow.ReanalysisResult reanalysisResult = KappaSampleReanalysisWorkflow.reanalyze(
                reanalysisInput,
                reanalysisOutput,
                new PrAnalyzer(fakeClient)
        );
        require(reanalysisResult.totalInputRows() == 5, "reanalysis input row count");
        require(reanalysisResult.succeeded() == 4, "reanalysis success count");
        require(reanalysisResult.failed() == 1, "reanalysis failure count");
        require(reanalysisResult.rowsWritten() == 5, "reanalysis output row count");
        require(fakeClient.requests().equals(List.of("owner/repo#1", "owner/repo#2", "owner/repo#3", "owner/repo#4", "owner/repo#5")), "reanalysis should use original repo and PR numbers in order");

        List<Map<String, String>> reanalyzedRows = CsvTools.readRows(reanalysisOutput);
        require(reanalyzedRows.size() == 5, "reanalyzed CSV row count");
        require("owner/repo#1".equals(reanalyzedRows.get(0).get("Sample ID")), "reanalyzed first sample id");
        require("owner/repo#5".equals(reanalyzedRows.get(4).get("Sample ID")), "reanalyzed failed sample id preserved");
        require("No".equals(reanalyzedRows.get(0).get("Script AI Disclosure Present")), "unchecked template reanalyzed as no");
        require(reanalyzedRows.get(0).get("Disclosure Text detected by script").isBlank(), "unchecked template evidence cleared");
        require("Yes".equals(reanalyzedRows.get(1).get("Script AI Disclosure Present")), "checked yes reanalyzed as yes");
        require("possible_positive".equals(reanalyzedRows.get(1).get("Script Disclosure Classification")), "checked yes classification updated");
        require("Yes".equals(reanalyzedRows.get(2).get("Script AI Disclosure Present")), "checked no still records disclosure present");
        require("possible_negative".equals(reanalyzedRows.get(2).get("Script Disclosure Classification")), "checked no classification updated");
        require("Yes".equals(reanalyzedRows.get(3).get("Script AI Disclosure Present")), "explicit contributor disclosure reanalyzed as yes");
        require("Fetch Failed".equals(reanalyzedRows.get(4).get("Reanalysis Status")), "failed fetch status");
        require(reanalyzedRows.get(4).get("Script AI Disclosure Present").isBlank(), "failed fetch script value blank");

        require(humanLabelsOriginal.equals(Files.readString(humanLabelsBeforeReanalysis, StandardCharsets.UTF_8)), "human labels unchanged by reanalysis");
        require(consensusOriginal.equals(Files.readString(consensusBeforeReanalysis, StandardCharsets.UTF_8)), "consensus labels unchanged by reanalysis");

        Path duplicateSample = Files.createTempFile("duplicate-kappa-sample", ".csv");
        Files.writeString(duplicateSample, reanalysisInputCsv() + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,t,a,,,,Closed,old,Yes,old_class,old_source,\n", StandardCharsets.UTF_8);
        try {
            KappaSampleReanalysisWorkflow.reanalyze(duplicateSample, tempMissingPath("duplicate-output", ".csv"), new PrAnalyzer(fakeClient));
            throw new AssertionError("reanalysis should reject duplicate Sample IDs");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("Duplicate Sample ID"), "duplicate Sample ID validation");
        }

        Path mismatchedSample = Files.createTempFile("mismatched-kappa-sample", ".csv");
        Files.writeString(mismatchedSample, reanalysisInputCsv().replace("owner/repo#1,owner/repo,1", "owner/repo#99,owner/repo,1"), StandardCharsets.UTF_8);
        try {
            KappaSampleReanalysisWorkflow.reanalyze(mismatchedSample, tempMissingPath("mismatched-output", ".csv"), new PrAnalyzer(fakeClient));
            throw new AssertionError("reanalysis should reject invalid Repo#PR relationships");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("Sample ID does not match"), "Repo#PR relationship validation");
        }

        try {
            KappaSampleReanalysisWorkflow.reanalyze(reanalysisInput, reanalysisOutput, new PrAnalyzer(fakeClient));
            throw new AssertionError("reanalysis should refuse to overwrite existing output");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("already exists"), "reanalysis overwrite refusal");
        }

        Path reanalysisConsensus = Files.createTempFile("reanalysis-consensus", ".csv");
        Files.writeString(reanalysisConsensus, reanalysisConsensusCsv(), StandardCharsets.UTF_8);
        Path reanalysisValidation = tempMissingPath("reanalysis-detector-validation", ".csv");
        ConsensusWorkflow.DetectorValidationResult reanalysisValidationResult = ConsensusWorkflow.validateDetector(reanalysisOutput, reanalysisConsensus, reanalysisValidation);
        require(reanalysisValidationResult.totalMatchedRows() == 4, "reanalyzed output remains detector-validation compatible");

        System.out.println("AiDisclosureDetectorHarness passed");
    }

    private static String labelsCsv(String present1, String classification1, String present2, String classification2) {
        return "Sample ID,Repo,PR #,PR URL,Disclosure Present,Disclosure Classification,Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1," + present1 + "," + classification1 + ",\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2," + present2 + "," + classification2 + ",\n";
    }

    private static String labelsCsv(
            String present1, String classification1,
            String present2, String classification2,
            String present3, String classification3,
            String present4, String classification4
    ) {
        return "Sample ID,Repo,PR #,PR URL,Disclosure Present,Disclosure Classification,Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1," + present1 + "," + classification1 + ",note 1\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2," + present2 + "," + classification2 + ",note 2\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3," + present3 + "," + classification3 + ",note 3\n"
                + "owner/repo#4,owner/repo,4,https://github.com/owner/repo/pull/4," + present4 + "," + classification4 + ",note 4\n";
    }

    private static String sampleCsv() {
        return "Sample ID,Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Status,Disclosure Text detected by script,Script AI Disclosure Present,Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,t,a,,,,Closed,script saw yes,Yes,\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2,t,a,,,,Closed,,No,\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3,t,a,,,,Closed,,No,\n"
                + "owner/repo#4,owner/repo,4,https://github.com/owner/repo/pull/4,t,a,,,,Closed,script saw yes,Yes,\n";
    }

    private static String resolvedConsensusCsv() {
        return "Sample ID,Repo,PR #,PR URL,Coder A Disclosure Present,Coder B Disclosure Present,Consensus Disclosure Present,Coder A Disclosure Classification,Coder B Disclosure Classification,Consensus Disclosure Classification,Agreement Status,Consensus Notes,Coder A Notes,Coder B Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,Yes,Yes,Yes,Positive,Positive,Positive,Agreed,agreed yes,,\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2,No,Yes,No,None,Ambiguous,None,Resolved,resolved no,,\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3,Yes,Yes,Yes,Positive,Positive,Positive,Agreed,agreed yes,,\n"
                + "owner/repo#4,owner/repo,4,https://github.com/owner/repo/pull/4,No,No,No,None,None,None,Agreed,agreed no,,\n";
    }

    private static String unresolvedConsensusCsv() {
        return "Sample ID,Repo,PR #,PR URL,Coder A Disclosure Present,Coder B Disclosure Present,Consensus Disclosure Present,Coder A Disclosure Classification,Coder B Disclosure Classification,Consensus Disclosure Classification,Agreement Status,Consensus Notes,Coder A Notes,Coder B Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,Yes,No,,Positive,None,,Needs Resolution,,,\n";
    }

    private static String reanalysisInputCsv() {
        return "Sample ID,Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Status,Disclosure Text detected by script,Script AI Disclosure Present,Script Disclosure Classification,Script Disclosure Source,Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,old title 1,old author,,,,Closed,old false positive,Yes,possible_positive,PR body,note 1\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2,old title 2,old author,,,,Closed,,No,none,PR body,note 2\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3,old title 3,old author,,,,Closed,,No,none,PR body,note 3\n"
                + "owner/repo#4,owner/repo,4,https://github.com/owner/repo/pull/4,old title 4,old author,,,,Closed,,No,none,PR body,note 4\n"
                + "owner/repo#5,owner/repo,5,https://github.com/owner/repo/pull/5,old title 5,old author,,,,Closed,old,Yes,possible_positive,PR body,note 5\n";
    }

    private static String reanalysisConsensusCsv() {
        return "Sample ID,Repo,PR #,PR URL,Coder A Disclosure Present,Coder B Disclosure Present,Consensus Disclosure Present,Coder A Disclosure Classification,Coder B Disclosure Classification,Consensus Disclosure Classification,Agreement Status,Consensus Notes,Coder A Notes,Coder B Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,No,No,No,None,None,None,Agreed,unchecked template,,\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2,Yes,Yes,Yes,Positive,Positive,Positive,Agreed,checked yes,,\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3,Yes,Yes,Yes,Negative,Negative,Negative,Agreed,checked no,,\n"
                + "owner/repo#4,owner/repo,4,https://github.com/owner/repo/pull/4,Yes,Yes,Yes,Positive,Positive,Positive,Agreed,explicit,,\n";
    }

    private static Path tempMissingPath(String prefix, String suffix) throws java.io.IOException {
        Path path = Files.createTempFile(prefix, suffix);
        Files.delete(path);
        return path;
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

    private static void requirePositive(DisclosureResult result, String message) {
        require(result.disclosed(), message + " should disclose");
        require("possible_positive".equals(result.classification()), message + " should be positive");
    }

    private static void requireNegative(DisclosureResult result, String message) {
        require(result.disclosed(), message + " should disclose");
        require("possible_negative".equals(result.classification()), message + " should be negative");
    }

    private static class FakeGitHubClient extends GitHubClient {
        private final Map<String, String> bodies = new java.util.LinkedHashMap<>();
        private final Map<String, String> failures = new java.util.LinkedHashMap<>();
        private final List<String> requests = new java.util.ArrayList<>();

        void add(String repository, int number, String body) {
            bodies.put(repository + "#" + number, body);
        }

        void fail(String repository, int number, String reason) {
            failures.put(repository + "#" + number, reason);
        }

        List<String> requests() {
            return List.copyOf(requests);
        }

        @Override
        public PullRequestData getPullRequest(String owner, String repo, int number) throws java.io.IOException {
            String repository = owner + "/" + repo;
            String key = repository + "#" + number;
            requests.add(key);
            if (failures.containsKey(key)) {
                throw new java.io.IOException(failures.get(key));
            }
            if (!bodies.containsKey(key)) {
                throw new java.io.IOException("missing fixture " + key);
            }
            return new PullRequestData(
                    repository,
                    number,
                    "https://github.com/" + repository + "/pull/" + number,
                    "Fresh title " + number,
                    "2026-01-01T00:00:00Z",
                    "2026-01-02T00:00:00Z",
                    "",
                    "closed",
                    true,
                    "contributor",
                    "User",
                    false,
                    bodies.get(key)
            );
        }

        @Override
        public String fetchHtml(String url) {
            return "";
        }
    }
}
