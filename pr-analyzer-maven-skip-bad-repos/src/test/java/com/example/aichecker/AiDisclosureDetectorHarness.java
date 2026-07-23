package com.example.aichecker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public class AiDisclosureDetectorHarness {
    public static void main(String[] args) throws Exception {
        AiDisclosureDetector detector = new AiDisclosureDetector();
        String testToken = "ghp_test_token_should_not_log";
        HttpRequest authenticatedRequest = GitHubClient.buildGetRequest(URI.create("https://api.github.com/repos/owner/repo/pulls/1"), "application/vnd.github+json", testToken);
        require(authenticatedRequest.headers().firstValue("Authorization").orElse("").equals("Bearer " + testToken), "Authorization header should use GITHUB_TOKEN");
        require(authenticatedRequest.headers().firstValue("Accept").orElse("").equals("application/vnd.github+json"), "Accept header should be present");
        require(authenticatedRequest.headers().firstValue("X-GitHub-Api-Version").orElse("").equals("2022-11-28"), "GitHub API version header should be present");

        HttpRequest unauthenticatedRequest = GitHubClient.buildGetRequest(URI.create("https://api.github.com/repos/owner/repo/pulls/1"), "application/vnd.github+json", null);
        require(unauthenticatedRequest.headers().firstValue("Authorization").isEmpty(), "Authorization header should be omitted without token");

        HttpRequest blankTokenRequest = GitHubClient.buildGetRequest(URI.create("https://api.github.com/repos/owner/repo/pulls/1"), "application/vnd.github+json", "   ");
        require(blankTokenRequest.headers().firstValue("Authorization").isEmpty(), "Authorization header should be omitted for blank token");

        ByteArrayOutputStream warningBytes = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(warningBytes, true, StandardCharsets.UTF_8));
            GitHubClient.resetUnauthenticatedWarningForTests();
            GitHubClient.warnUnauthenticatedOnce();
            GitHubClient.warnUnauthenticatedOnce();
        } finally {
            System.setErr(originalErr);
        }
        String warningText = warningBytes.toString(StandardCharsets.UTF_8);
        require(countOccurrences(warningText, "No GITHUB_TOKEN") == 1, "unauthenticated warning should print once");
        require(!warningText.contains(testToken), "token should not appear in warning logs");

        String unauthorized = GitHubClient.errorMessage("https://api.github.com/test", 401, "bad credentials", name -> Optional.empty(), true);
        require(unauthorized.contains("GitHub authentication failed"), "401 should include authentication guidance");
        require(!unauthorized.contains(testToken), "token should not appear in 401 message");

        String rateLimited = GitHubClient.errorMessage(
                "https://api.github.com/test",
                403,
                "{\"message\":\"API rate limit exceeded\"}",
                name -> name.equalsIgnoreCase("x-ratelimit-remaining") ? Optional.of("0") : Optional.empty(),
                false
        );
        require(rateLimited.contains("Authentication enabled: No"), "403 rate limit should report auth state");
        require(rateLimited.contains("Remaining rate limit: 0"), "403 rate limit should report remaining quota");
        require(!rateLimited.contains(testToken), "token should not appear in 403 message");

        FakeHttpClient retrySuccessHttp = new FakeHttpClient();
        retrySuccessHttp.enqueue(new IOException("/192.168.1.63:49808: GOAWAY received"));
        retrySuccessHttp.enqueue(jsonResponse(200, prJson("retry success"), Map.of()));
        List<Long> retrySuccessSleeps = new ArrayList<>();
        GitHubClient retrySuccessClient = new GitHubClient(() -> retrySuccessHttp, null, retrySuccessSleeps::add);
        PullRequestData retrySuccessPr = retrySuccessClient.getPullRequest("owner", "repo", 7);
        require("retry success".equals(retrySuccessPr.title()), "GOAWAY retry should eventually return PR");
        require(retrySuccessHttp.requests().size() == 2, "GOAWAY retry should recreate request");
        require(retrySuccessSleeps.size() == 1, "GOAWAY retry should back off once");

        FakeHttpClient exhaustedHttp = new FakeHttpClient();
        exhaustedHttp.enqueue(new java.net.SocketTimeoutException("socket timed out"));
        exhaustedHttp.enqueue(new java.net.SocketTimeoutException("socket timed out"));
        exhaustedHttp.enqueue(new java.net.SocketTimeoutException("socket timed out"));
        exhaustedHttp.enqueue(new java.net.SocketTimeoutException("socket timed out"));
        GitHubClient exhaustedClient = new GitHubClient(() -> exhaustedHttp, testToken, millis -> {});
        try {
            exhaustedClient.getPullRequest("owner", "repo", 8);
            throw new AssertionError("transient failures should exhaust after four attempts");
        } catch (IOException expected) {
            require(expected.getMessage().contains("after 4 attempts"), "exhausted retry message");
            require(!expected.getMessage().contains(testToken), "exhausted retry should not expose token");
        }
        require(exhaustedHttp.requests().size() == 4, "transient retry should try at most four times");

        FakeHttpClient retryAfterHttp = new FakeHttpClient();
        retryAfterHttp.enqueue(jsonResponse(429, "{\"message\":\"secondary rate limit\"}", Map.of("Retry-After", "2")));
        retryAfterHttp.enqueue(jsonResponse(200, prJson("retry after success"), Map.of()));
        List<Long> retryAfterSleeps = new ArrayList<>();
        GitHubClient retryAfterClient = new GitHubClient(() -> retryAfterHttp, null, retryAfterSleeps::add);
        retryAfterClient.getPullRequest("owner", "repo", 9);
        require(retryAfterSleeps.equals(List.of(2000L)), "Retry-After seconds should control backoff");

        FakeHttpClient permanentHttp = new FakeHttpClient();
        permanentHttp.enqueue(jsonResponse(404, "{\"message\":\"not found\"}", Map.of()));
        GitHubClient permanentClient = new GitHubClient(() -> permanentHttp, null, millis -> {});
        try {
            permanentClient.getPullRequest("owner", "repo", 10);
            throw new AssertionError("404 should not be retried");
        } catch (IOException expected) {
            require(expected.getMessage().contains("HTTP 404"), "404 error should be preserved");
        }
        require(permanentHttp.requests().size() == 1, "404 should be attempted once");

        List<FakeHttpClient> goAwayClients = new ArrayList<>();
        GitHubClient recreatedClient = new GitHubClient(() -> {
            FakeHttpClient fake = new FakeHttpClient();
            goAwayClients.add(fake);
            if (goAwayClients.size() == 1) {
                fake.enqueue(new IOException("GOAWAY received"));
            } else {
                fake.enqueue(jsonResponse(200, prJson("new client success"), Map.of()));
            }
            return fake;
        }, null, millis -> {});
        recreatedClient.getPullRequest("owner", "repo", 11);
        require(goAwayClients.size() == 2, "GOAWAY should recreate the underlying client before retry");

        FakeHttpClient interruptedHttp = new FakeHttpClient();
        interruptedHttp.enqueue(jsonResponse(503, "{\"message\":\"temporarily unavailable\"}", Map.of()));
        GitHubClient interruptedClient = new GitHubClient(() -> interruptedHttp, null, millis -> {
            throw new InterruptedException("stop");
        });
        try {
            interruptedClient.getPullRequest("owner", "repo", 12);
            throw new AssertionError("interrupted backoff should propagate interruption");
        } catch (InterruptedException expected) {
            require("stop".equals(expected.getMessage()), "interrupted backoff reason");
        }

        require("apache/airflow".equals(RepoListImporter.normalizeRepository("https://github.com/apache/airflow")), "normalize https GitHub URL");
        require("apache/airflow".equals(RepoListImporter.normalizeRepository("https://github.com/apache/airflow/")), "normalize trailing slash");
        require("apache/airflow".equals(RepoListImporter.normalizeRepository("http://github.com/apache/airflow")), "normalize http GitHub URL");
        require("apache/airflow".equals(RepoListImporter.normalizeRepository("github.com/apache/airflow")), "normalize schemeless GitHub URL");
        require("apache/airflow".equals(RepoListImporter.normalizeRepository("apache/airflow")), "accept owner/repo");
        require("apache/airflow".equals(RepoListImporter.normalizeRepository("https://github.com/apache/airflow.git")), "normalize git suffix");
        try {
            RepoListImporter.normalizeRepository("https://github.com/apache/airflow/blob/main/README.md");
            throw new AssertionError("policy or file URL should be rejected");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("below the repository root"), "reject file URL");
        }
        try {
            RepoListImporter.normalizeRepository("https://github.com/apache");
            throw new AssertionError("missing repository name should be rejected");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("owner/repository"), "reject missing repo");
        }

        Path repoImportCsv = Files.createTempFile("repo-import", ".csv");
        Files.writeString(repoImportCsv, repoImportCsv(), StandardCharsets.UTF_8);
        Path existingRepos = Files.createTempFile("existing-repos", ".txt");
        Files.writeString(existingRepos, "old/removed\napache/airflow\n", StandardCharsets.UTF_8);
        RepoListImporter.ImportResult importResult = RepoListImporter.importFromCsv(repoImportCsv, existingRepos, true);
        require(importResult.repositoryRowsInspected() == 9, "repo import inspected row count");
        require(importResult.nonEmptyRepositoryLinks() == 8, "repo import non-empty link count");
        require(importResult.validRepositoriesWritten() == 0, "repo import should not write valid rows when invalid links exist");
        require(importResult.duplicateRepositoriesRemoved() == 1, "repo import duplicate count");
        require(importResult.invalidRepositoryLinks() == 2, "repo import invalid count");
        require(!importResult.written(), "repo import should not write when invalid links exist");
        require(importResult.added().contains("owner/repo"), "repo import added comparison");
        require(importResult.removed().contains("old/removed"), "repo import removed comparison");
        require(importResult.unchanged() == 1, "repo import unchanged comparison");

        Path validRepoImportCsv = Files.createTempFile("valid-repo-import", ".csv");
        Files.writeString(validRepoImportCsv, validRepoImportCsv(), StandardCharsets.UTF_8);
        Path validRepoOutput = tempMissingPath("valid-repos", ".txt");
        RepoListImporter.ImportResult validImport = RepoListImporter.importFromCsv(validRepoImportCsv, validRepoOutput, false);
        require(validImport.written(), "valid repo import should write");
        List<String> importedRepos = Files.readAllLines(validRepoOutput, StandardCharsets.UTF_8);
        require(importedRepos.equals(List.of("apache/airflow", "owner/repo", "OSGeo/gdal", "processing/p5.js")), "repo import preserves order and normalization");
        try {
            RepoListImporter.importFromCsv(validRepoImportCsv, validRepoOutput, false);
            throw new AssertionError("repo import should refuse overwrite without replace");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("--replace"), "repo import overwrite refusal");
        }

        Path policyTracker = Path.of("policy-tracker.csv");
        if (Files.exists(policyTracker)) {
            Path policyImportOutput = tempMissingPath("policy-tracker-repos", ".txt");
            RepoListImporter.ImportResult policyImport = RepoListImporter.importFromCsv(policyTracker, policyImportOutput, false);
            List<String> policyRepos = Files.readAllLines(policyImportOutput, StandardCharsets.UTF_8);
            int expectedPolicyRepoCount = (int) CsvTools.readRows(policyTracker).stream()
                    .map(row -> row.getOrDefault("Repository Link", "").trim())
                    .filter(value -> !value.isBlank())
                    .map(RepoListImporter::normalizeRepository)
                    .distinct()
                    .count();
            require(policyImport.validRepositoriesWritten() == expectedPolicyRepoCount, "policy tracker repo count should match authoritative CSV links");
            require(policyRepos.size() == expectedPolicyRepoCount, "policy tracker output row count");
            require("apache/airflow".equals(policyRepos.get(0)), "policy tracker first repo");
            require("sympy/sympy".equals(policyRepos.get(policyRepos.size() - 1)), "policy tracker last repo");
        }

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

        DisclosureResult airflowTemplate = detector.detect("""
                ##### Was generative AI tooling used to co-author this PR?

                <!--
                If generative AI tooling has been used in the process of authoring this PR, please
                change below checkbox to `[X]` followed by the name of the tool, uncomment the "Generated-by".
                -->

                - [ ] Yes (please specify the tool below)

                <!--
                Generated-by: [Tool Name] following [the guidelines](https://github.com/apache/airflow/blob/main/contributing-docs/05_pull_requests.rst#gen-ai-assisted-contributions)
                -->
                """, "");
        require(!airflowTemplate.disclosed(), "Airflow-style unchecked template should not count");
        require(!airflowTemplate.evidence().contains("ai-assisted-contributions"), "comment URL anchor should not appear as evidence");

        DisclosureResult commentedAiUrl = detector.detect("""
                <!--
                [AI guidance](https://example.com/gen-ai-assisted-contributions)
                https://example.com/docs#ai-assisted-contributions
                -->
                """, "");
        require(!commentedAiUrl.disclosed(), "AI terms inside commented URLs should not count");

        String cleanedComments = AiDisclosureDetector.removeHtmlComments("""
                Visible before
                <!-- first AI comment -->
                Visible middle
                <!-- second Generated-by: ChatGPT comment -->
                Visible after
                """);
        require(cleanedComments.contains("Visible before"), "comment removal should preserve text before comments");
        require(cleanedComments.contains("Visible middle"), "comment removal should preserve text between comments");
        require(cleanedComments.contains("Visible after"), "comment removal should preserve text after comments");
        require(!cleanedComments.contains("first AI comment"), "comment removal should remove first comment");
        require(!cleanedComments.contains("Generated-by: ChatGPT"), "comment removal should remove second comment");

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
        requirePositive(detector.detect("""
                #### AI Generation Disclosure

                Used codex to write these

                #### Release Notes
                NO ENTRY
                """, ""), "AI disclosure section with Codex answer");
        requirePositive(detector.detect("""
                ## AI disclosure

                Yes, I used Claude for the tests.
                """, ""), "AI disclosure section with Claude answer");
        requirePositive(detector.detect("**AI use:** Used Copilot", ""), "bold AI use field");
        requirePositive(detector.detect("Generated documentation with ChatGPT.", ""), "generated documentation with ChatGPT");
        requirePositive(detector.detect("Cursor helped refactor this function.", ""), "Cursor helped refactor statement");
        requireNegative(detector.detect("No generative AI tools were used for this PR.", ""), "explicit no AI statement");
        requireNegative(detector.detect("I did not use AI assistance.", ""), "explicit did not use AI statement");
        requireNegative(detector.detect("AI Generation Disclosure: No AI was used", ""), "inline heading no AI statement");
        requireNegative(detector.detect("I did not use Codex or any other generative AI.", ""), "explicit no Codex statement");
        require(!detector.detect("""
                <!--
                Example: Generated documentation with ChatGPT
                -->
                """, "").disclosed(), "HTML comment example should not count");
        require(!detector.detect("""
                > Generated documentation with ChatGPT.
                """, "").disclosed(), "quoted contributor text should not count");
        require(!detector.detect("""
                ## AI Generation Disclosure


                ## Release Notes
                """, "").disclosed(), "empty AI disclosure section should not count");
        require(!detector.detect("Please disclose any AI use in this section.", "").disclosed(), "template instruction should not count");
        require(!detector.detect("This fixes an issue caused by Copilot.", "").disclosed(), "caused by Copilot is not a disclosure");
        require(!detector.detect("Update codex references in the historical manuscript notes.", "").disclosed(), "ordinary codex noun should not count");
        requireAmbiguous(detector.detect("""
                ## AI disclosure

                Codex
                """, ""), "bare Codex answer");
        requireAmbiguous(detector.detect("""
                ## AI disclosure

                N/A
                """, ""), "N/A answer");
        requireAmbiguous(detector.detect("Minor AI help.", ""), "minor AI help");
        requireAmbiguous(detector.detect("""
                ## AI disclosure

                Used Claude for tests.

                ## Notes

                I did not use Codex or any other generative AI.
                """, ""), "conflicting disclosure statements");

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

        List<RepoUrl> repoSelectionInput = List.of(
                RepoUrl.parse("alpha/one"),
                RepoUrl.parse("beta/two"),
                RepoUrl.parse("alpha/one"),
                RepoUrl.parse("gamma/three"),
                RepoUrl.parse("delta/four"),
                RepoUrl.parse("epsilon/five")
        );
        KappaWorkflow.RepositorySelection seededSelection = KappaWorkflow.selectRandomRepositories(repoSelectionInput, 2026L);
        KappaWorkflow.RepositorySelection repeatedSeedSelection = KappaWorkflow.selectRandomRepositories(repoSelectionInput, 2026L);
        KappaWorkflow.RepositorySelection differentSeedSelection = KappaWorkflow.selectRandomRepositories(repoSelectionInput, 2027L);
        require(seededSelection.availableRepositories() == 5, "kappa selection should dedupe repositories before sampling");
        require(seededSelection.selectedRepositories().size() == 3, "kappa selection should select exactly three repositories");
        require(new java.util.LinkedHashSet<>(seededSelection.selectedRepositories()).size() == 3, "kappa selection should be distinct");
        require(seededSelection.selectedRepositories().equals(repeatedSeedSelection.selectedRepositories()), "same kappa seed should produce same repositories");
        require(!seededSelection.selectedRepositories().equals(differentSeedSelection.selectedRepositories()), "different kappa seeds can produce different repositories");
        try {
            KappaWorkflow.selectRandomRepositories(List.of(RepoUrl.parse("one/repo"), RepoUrl.parse("two/repo")), 1L);
            throw new AssertionError("kappa selection should require at least three repositories");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("At least 3 valid repositories"), "kappa selection too few repos message");
        }

        Path sample = tempMissingPath("kappa-sample", ".csv");
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
            KappaWorkflow.writeSampleWithSummary(sample, List.of(row("owner/repo", 1, false, "none")));
            throw new AssertionError("kappa sample writer should refuse overwrite");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("already exists"), "kappa sample overwrite refusal");
        }

        Path fiftySample = tempMissingPath("kappa-fifty-sample", ".csv");
        List<PrReportRow> fiftyRows = new java.util.ArrayList<>();
        for (int i = 100; i > 50; i--) {
            fiftyRows.add(row("repo/full", i, false, "none"));
        }
        for (int i = 7; i > 0; i--) {
            fiftyRows.add(row("repo/short", i, false, "none"));
        }
        for (int i = 250; i > 200; i--) {
            fiftyRows.add(row("repo/third", i, false, "none"));
        }
        KappaWorkflow.writeSampleWithSummary(fiftySample, fiftyRows);
        List<Map<String, String>> fiftySampleRows = CsvTools.readRows(fiftySample);
        require(fiftySampleRows.size() == 107, "combined sample should include 50 plus short repo plus 50 rows");
        require("repo/full#100".equals(fiftySampleRows.get(0).get("Sample ID")), "latest-first ordering first repo");
        require("repo/full#51".equals(fiftySampleRows.get(49).get("Sample ID")), "first repo contributes 50 rows");
        require("repo/short#7".equals(fiftySampleRows.get(50).get("Sample ID")), "short repo appended after first group");
        require("repo/third#250".equals(fiftySampleRows.get(57).get("Sample ID")), "third repo appended after short repo");

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

        FakeGitHubClient cutoffClient = new FakeGitHubClient();
        cutoffClient.addPage("window/repo", 1, List.of(
                pr("window/repo", 10, "2026-03-23T11:59:59Z", "human", "User"),
                pr("window/repo", 11, "2026-03-23T12:00:00Z", "human", "User"),
                pr("window/repo", 12, "2026-03-24T00:00:00Z", "human", "User"),
                prWithCreated("window/repo", 13, "2026-01-01T00:00:00Z", "2026-03-25T00:00:00Z", "human", "User", "closed", true),
                pr("window/repo", 14, "2026-03-26T00:00:00Z", "bot-user", "Bot"),
                prWithCreated("window/repo", 15, "2026-07-01T00:00:00Z", "", "human", "User", "open", false)
        ));
        cutoffClient.addPage("window/repo", 2, List.of(
                pr("window/repo", 16, "2026-03-27T00:00:00Z", "human", "User"),
                pr("window/repo", 12, "2026-03-24T00:00:00Z", "human", "User"),
                pr("window/repo", 17, "2026-03-27T00:00:00Z", "human", "User")
        ));
        PrAnalyzer cutoffAnalyzer = new PrAnalyzer(cutoffClient, Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC));
        List<PrReportRow> cutoffRows = cutoffAnalyzer.analyzeLatestClosedHumanPrs(RepoUrl.parse("window/repo"), 5);
        require(cutoffRows.stream().map(PrReportRow::pullRequestNumber).toList().equals(List.of(17, 16, 13, 12, 11)), "cutoff should filter before limit and order by closed_at desc then PR number desc");
        PrAnalyzer.CollectionSummary cutoffSummary = cutoffAnalyzer.collectionSummary("window/repo");
        require(cutoffSummary.requestedCount() == 5, "cutoff requested count");
        require(cutoffSummary.fetchedPrsInspected() == 8, "cutoff should inspect replacements after ineligible rows");
        require(cutoffSummary.excludedOlderThanCutoff() == 1, "cutoff older exclusion count");
        require(cutoffSummary.excludedNotClosed() == 1, "open PR should be excluded");
        require(cutoffSummary.duplicatesSkipped() == 1, "duplicate PR should be skipped");
        require(cutoffSummary.finalCount() == 5, "cutoff final count should include replacements");
        require("2026-03-23T12:00:00Z".equals(cutoffSummary.closedAtCutoff()), "cutoff should be collection instant minus four calendar months");
        require(cutoffClient.pages().equals(List.of("window/repo#page1", "window/repo#page2", "window/repo#page3")), "cutoff should continue until GitHub has no more pages");

        FakeGitHubClient paginationClient = new FakeGitHubClient();
        paginationClient.addPage("page/repo", 1, List.of(
                pr("page/repo", 1, "2026-03-21T23:59:59Z", "human", "User"),
                pr("page/repo", 2, "2026-03-20T00:00:00Z", "bot-user", "Bot")
        ));
        paginationClient.addPage("page/repo", 2, List.of(
                pr("page/repo", 3, "2026-03-22T00:00:00Z", "human", "User"),
                pr("page/repo", 4, "2026-03-22T00:00:01Z", "human", "User")
        ));
        PrAnalyzer paginationAnalyzer = new PrAnalyzer(paginationClient, Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
        List<PrReportRow> paginationRows = paginationAnalyzer.analyzeLatestClosedHumanPrs(RepoUrl.parse("page/repo"), 3);
        require(paginationRows.stream().map(PrReportRow::pullRequestNumber).toList().equals(List.of(4, 3)), "pagination should continue until eligible requested count is exhausted and then sort locally");
        require(paginationClient.pages().equals(List.of("page/repo#page1", "page/repo#page2", "page/repo#page3")), "pagination should stop when GitHub has no more pages");
        require(paginationAnalyzer.collectionSummary("page/repo").excludedOlderThanCutoff() == 1, "pagination older exclusion count");
        require(paginationAnalyzer.botPrsExcludedByRepository().get("page/repo") == 1, "bot PRs should not count toward requested eligible count");

        FakeGitHubClient calendarClient = new FakeGitHubClient();
        calendarClient.addPage("calendar/repo", 1, List.of(
                pr("calendar/repo", 1, "2026-02-28T00:00:00Z", "human", "User")
        ));
        PrAnalyzer calendarAnalyzer = new PrAnalyzer(calendarClient, Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC));
        List<PrReportRow> calendarRows = calendarAnalyzer.analyzeLatestClosedHumanPrs(RepoUrl.parse("calendar/repo"), 1);
        require(calendarRows.stream().map(PrReportRow::pullRequestNumber).toList().equals(List.of(1)), "four calendar months should not mean fixed 120 days");

        FakeGitHubClient malformedClient = new FakeGitHubClient();
        malformedClient.addPage("bad/repo", 1, List.of(
                pr("bad/repo", 1, "", "human", "User"),
                pr("bad/repo", 2, "not-a-date", "human", "User"),
                pr("bad/repo", 3, "2026-03-22T00:00:00Z", "human", "User")
        ));
        PrAnalyzer malformedAnalyzer = new PrAnalyzer(malformedClient, Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
        List<PrReportRow> malformedRows = malformedAnalyzer.analyzeLatestClosedHumanPrs(RepoUrl.parse("bad/repo"), 3);
        require(malformedRows.stream().map(PrReportRow::pullRequestNumber).toList().equals(List.of(3)), "missing or invalid closed_at should be excluded");
        require(malformedAnalyzer.collectionSummary("bad/repo").excludedMalformedClosedAt() == 2, "malformed closed_at count");

        FakeGitHubClient multiRepoClient = new FakeGitHubClient();
        multiRepoClient.addPage("multi/one", 1, List.of(pr("multi/one", 1, "2026-03-22T00:00:00Z", "human", "User")));
        multiRepoClient.addPage("multi/two", 1, List.of(pr("multi/two", 1, "2026-03-21T23:59:59Z", "human", "User")));
        PrAnalyzer multiRepoAnalyzer = new PrAnalyzer(multiRepoClient, Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
        multiRepoAnalyzer.analyzeMultipleRepos(List.of(RepoUrl.parse("multi/one"), RepoUrl.parse("multi/two")), 1);
        require(multiRepoAnalyzer.collectionSummary("multi/one").closedAtCutoff().equals(multiRepoAnalyzer.collectionSummary("multi/two").closedAtCutoff()), "cutoff should be consistent across repositories in one run");

        FakeGitHubClient directClient = new FakeGitHubClient();
        directClient.add("direct/repo", 1, "I used ChatGPT to generate the initial implementation.");
        PrReportRow directRow = new PrAnalyzer(directClient, Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC)).analyzeExistingPullRequest("direct/repo", 1);
        require(directRow.pullRequestNumber() == 1 && directRow.aiDisclosure(), "direct specified-PR analysis should remain unaffected by cutoff");

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

        Path targetedInput = Files.createTempFile("targeted-input", ".csv");
        Files.writeString(targetedInput, targetedInputCsv(), StandardCharsets.UTF_8);
        FakeGitHubClient targetedClient = new FakeGitHubClient();
        targetedClient.add("owner/repo", 1, """
                <!-- https://example.com/docs#ai-assisted-contributions -->
                - [ ] Yes
                """);
        targetedClient.add("owner/repo", 2, "- [x] Yes - GitHub Copilot");
        targetedClient.fail("owner/repo", 3, "HTTP 401 for https://api.github.com/test. GitHub authentication failed.");
        Path targetedOutput = tempMissingPath("targeted-output", ".csv");
        ByteArrayOutputStream diagnosticsBytes = new ByteArrayOutputStream();
        SpecificPrAnalysisWorkflow.SpecificAnalysisResult targetedResult = SpecificPrAnalysisWorkflow.analyzeSpecificPrs(
                targetedInput,
                targetedOutput,
                List.of("owner/repo#1", "owner/repo#2", "owner/repo#3"),
                new PrAnalyzer(targetedClient),
                new PrintStream(diagnosticsBytes, true, StandardCharsets.UTF_8)
        );
        require(targetedResult.requested() == 3, "targeted requested count");
        require(targetedResult.succeeded() == 2, "targeted success count");
        require(targetedResult.failed() == 1, "targeted failure count");
        require(targetedResult.rowsWritten() == 4, "targeted row count");
        require(targetedClient.requests().equals(List.of("owner/repo#1", "owner/repo#2", "owner/repo#3")), "targeted fetches should include only requested PRs");
        List<Map<String, String>> targetedRows = CsvTools.readRows(targetedOutput);
        require("owner/repo#1".equals(targetedRows.get(0).get("Sample ID")), "targeted row order first");
        require("owner/repo#4".equals(targetedRows.get(3).get("Sample ID")), "targeted row order last");
        require("No".equals(targetedRows.get(0).get("Script AI Disclosure Present")), "targeted unchecked checkbox no");
        require(targetedRows.get(0).get("Disclosure Text detected by script").isBlank(), "targeted unchecked evidence blank");
        require("Yes".equals(targetedRows.get(1).get("Script AI Disclosure Present")), "targeted checked yes");
        require("Success".equals(targetedRows.get(1).get("Reanalysis Status")), "targeted retry success status");
        require("Fetch Failed".equals(targetedRows.get(2).get("Reanalysis Status")), "targeted 401 failure status");
        require("old evidence 4".equals(targetedRows.get(3).get("Disclosure Text detected by script")), "unrequested row evidence unchanged");
        require("old note 4".equals(targetedRows.get(3).get("Notes")), "unrequested row note unchanged");
        String diagnostics = diagnosticsBytes.toString(StandardCharsets.UTF_8);
        require(diagnostics.contains("Raw PR body:"), "diagnostics should show raw body");
        require(diagnostics.contains("Cleaned PR body after HTML-comment removal:"), "diagnostics should show cleaned body");
        require(diagnostics.contains("Unchecked AI checkbox lines ignored:"), "diagnostics should show ignored checkboxes");
        require(diagnostics.contains("Detected disclosure evidence:"), "diagnostics should show evidence");
        require(diagnostics.contains("Classification: Positive"), "diagnostics should show classification");
        require(!diagnostics.contains("Authorization"), "diagnostics must not print authorization header");
        require(!targetedRows.get(0).get("Disclosure Text detected by script").contains("ai-assisted-contributions"), "HTML comment content should not appear in targeted evidence");

        try {
            SpecificPrAnalysisWorkflow.analyzeSpecificPrs(targetedInput, tempMissingPath("targeted-missing", ".csv"), List.of("owner/repo#999"), new PrAnalyzer(targetedClient), System.out);
            throw new AssertionError("targeted analysis should reject missing sample IDs");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("Sample ID not found"), "targeted missing Sample ID message");
        }

        Path duplicateTargeted = Files.createTempFile("targeted-duplicate", ".csv");
        Files.writeString(duplicateTargeted, targetedInputCsv() + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,t,a,,,,Closed,old,Yes,old_class,old_source,Success,,note\n", StandardCharsets.UTF_8);
        try {
            SpecificPrAnalysisWorkflow.analyzeSpecificPrs(duplicateTargeted, tempMissingPath("targeted-duplicate-output", ".csv"), List.of("owner/repo#1"), new PrAnalyzer(targetedClient), System.out);
            throw new AssertionError("targeted analysis should reject duplicate Sample IDs");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("Duplicate Sample ID"), "targeted duplicate Sample ID message");
        }

        try {
            SpecificPrAnalysisWorkflow.analyzeSpecificPrs(targetedInput, targetedOutput, List.of("owner/repo#1"), new PrAnalyzer(targetedClient), System.out);
            throw new AssertionError("targeted analysis should refuse overwrite");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().contains("already exists"), "targeted overwrite refusal");
        }

        Path targetedConsensus = Files.createTempFile("targeted-consensus", ".csv");
        Files.writeString(targetedConsensus, targetedConsensusCsv(), StandardCharsets.UTF_8);
        Path targetedValidation = tempMissingPath("targeted-validation", ".csv");
        ConsensusWorkflow.DetectorValidationResult targetedValidationResult = ConsensusWorkflow.validateDetector(targetedOutput, targetedConsensus, targetedValidation);
        require(targetedValidationResult.totalMatchedRows() == 2, "targeted output compatible with validation for successful rows");

        Path failedStatusConsensus = Files.createTempFile("targeted-failed-status-consensus", ".csv");
        Files.writeString(failedStatusConsensus, targetedFailedStatusConsensusCsv(), StandardCharsets.UTF_8);
        try {
            ConsensusWorkflow.validateDetector(targetedOutput, failedStatusConsensus, tempMissingPath("targeted-failed-validation", ".csv"));
            throw new AssertionError("detector validation should reject matched unsuccessful reanalysis statuses");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("unsuccessful re-analysis status"), "failed reanalysis status validation");
        }

        FakeGitHubClient retryFailedClient = new FakeGitHubClient();
        retryFailedClient.add("owner/repo", 1, "No AI use");
        retryFailedClient.add("owner/repo", 2, "- [x] Yes - GitHub Copilot");
        retryFailedClient.add("owner/repo", 3, "I used ChatGPT to generate the initial implementation.");
        Path retryFailedOutput = tempMissingPath("retry-failed-output", ".csv");
        SpecificPrAnalysisWorkflow.SpecificAnalysisResult retryFailedResult = SpecificPrAnalysisWorkflow.retryFailedPrs(
                targetedInput,
                retryFailedOutput,
                new PrAnalyzer(retryFailedClient),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );
        require(retryFailedResult.requested() == 3, "retry failed should select only failed rows");
        require(retryFailedResult.succeeded() == 3, "retry failed success count");
        require(retryFailedClient.requests().equals(List.of("owner/repo#1", "owner/repo#2", "owner/repo#3")), "retry failed should not refetch successful rows");
        List<Map<String, String>> retryFailedRows = CsvTools.readRows(retryFailedOutput);
        require("Success".equals(retryFailedRows.get(0).get("Reanalysis Status")), "retry failed row 1 status");
        require("Success".equals(retryFailedRows.get(1).get("Reanalysis Status")), "retry failed row 2 status");
        require("Success".equals(retryFailedRows.get(2).get("Reanalysis Status")), "retry failed row 3 status");
        require("old evidence 4".equals(retryFailedRows.get(3).get("Disclosure Text detected by script")), "retry failed successful row unchanged");
        require("Success".equals(retryFailedRows.get(3).get("Reanalysis Status")), "retry failed original success status unchanged");

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

    private static String repoImportCsv() {
        return "Repo,Repository Link,Policy URL\n"
                + "Airflow, https://github.com/apache/airflow ,https://github.com/apache/airflow/blob/main/policy.md\n"
                + "Blank,,\n"
                + "OwnerRepo,owner/repo,\n"
                + "Duplicate,https://github.com/apache/airflow/,\n"
                + "GitSuffix,github.com/OSGeo/gdal.git,\n"
                + "Trailing,https://github.com/processing/p5.js/,\n"
                + "PolicyPage,https://github.com/apache/airflow/blob/main/README.md,\n"
                + "MissingRepo,https://github.com/apache,\n"
                + "HTTP,http://github.com/curl/curl,\n";
    }

    private static String validRepoImportCsv() {
        return "Repo,Notes,Repository Link\n"
                + "Airflow,,https://github.com/apache/airflow\n"
                + "Blank,,\n"
                + "OwnerRepo,,owner/repo\n"
                + "OSGeo,,github.com/OSGeo/gdal.git\n"
                + "Processing,,https://github.com/processing/p5.js/\n"
                + "Duplicate,,https://github.com/apache/airflow/\n";
    }

    private static String reanalysisConsensusCsv() {
        return "Sample ID,Repo,PR #,PR URL,Coder A Disclosure Present,Coder B Disclosure Present,Consensus Disclosure Present,Coder A Disclosure Classification,Coder B Disclosure Classification,Consensus Disclosure Classification,Agreement Status,Consensus Notes,Coder A Notes,Coder B Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,No,No,No,None,None,None,Agreed,unchecked template,,\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2,Yes,Yes,Yes,Positive,Positive,Positive,Agreed,checked yes,,\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3,Yes,Yes,Yes,Negative,Negative,Negative,Agreed,checked no,,\n"
                + "owner/repo#4,owner/repo,4,https://github.com/owner/repo/pull/4,Yes,Yes,Yes,Positive,Positive,Positive,Agreed,explicit,,\n";
    }

    private static String targetedInputCsv() {
        return "Sample ID,Repo,PR #,PR URL,Title,Author,Created Date,Closed Date,Merged Date,Status,Disclosure Text detected by script,Script AI Disclosure Present,Script Disclosure Classification,Script Disclosure Source,Reanalysis Status,Reanalysis Error,Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,t,a,,,,Closed,old evidence 1,Yes,possible_positive,PR body,Fetch Failed,old error 1,old note 1\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2,t,a,,,,Closed,old evidence 2,No,none,PR body,Fetch Failed,old error 2,old note 2\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3,t,a,,,,Closed,old evidence 3,No,none,PR body,Fetch Failed,old error 3,old note 3\n"
                + "owner/repo#4,owner/repo,4,https://github.com/owner/repo/pull/4,t,a,,,,Closed,old evidence 4,Yes,possible_positive,PR body,Success,,old note 4\n";
    }

    private static String targetedConsensusCsv() {
        return "Sample ID,Repo,PR #,PR URL,Coder A Disclosure Present,Coder B Disclosure Present,Consensus Disclosure Present,Coder A Disclosure Classification,Coder B Disclosure Classification,Consensus Disclosure Classification,Agreement Status,Consensus Notes,Coder A Notes,Coder B Notes\n"
                + "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,No,No,No,None,None,None,Agreed,targeted unchecked,,\n"
                + "owner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2,Yes,Yes,Yes,Positive,Positive,Positive,Agreed,targeted checked,,\n";
    }

    private static String targetedFailedStatusConsensusCsv() {
        return "Sample ID,Repo,PR #,PR URL,Coder A Disclosure Present,Coder B Disclosure Present,Consensus Disclosure Present,Coder A Disclosure Classification,Coder B Disclosure Classification,Consensus Disclosure Classification,Agreement Status,Consensus Notes,Coder A Notes,Coder B Notes\n"
                + "owner/repo#3,owner/repo,3,https://github.com/owner/repo/pull/3,No,No,No,None,None,None,Agreed,failed,,\n";
    }

    private static Path tempMissingPath(String prefix, String suffix) throws java.io.IOException {
        Path path = Files.createTempFile(prefix, suffix);
        Files.delete(path);
        return path;
    }

    private static String prJson(String title) {
        return """
                {
                  "number": 7,
                  "html_url": "https://github.com/owner/repo/pull/7",
                  "title": "%s",
                  "state": "closed",
                  "created_at": "2026-01-01T00:00:00Z",
                  "closed_at": "2026-01-02T00:00:00Z",
                  "merged_at": null,
                  "body": "",
                  "user": {"login": "contributor", "type": "User"}
                }
                """.formatted(title);
    }

    private static HttpResponse<String> jsonResponse(int statusCode, String body, Map<String, String> headers) {
        return new FakeHttpResponse(statusCode, body, headers);
    }

    private static PullRequestData pr(String repository, int number, String closedAt, String author, String userType) {
        return prWithCreated(repository, number, "2026-01-01T00:00:00Z", closedAt, author, userType, "closed", true);
    }

    private static PullRequestData prWithCreated(String repository, int number, String createdAt, String closedAt, String author, String userType, String state, boolean closed) {
        return new PullRequestData(
                repository,
                number,
                "https://github.com/" + repository + "/pull/" + number,
                "Paged PR " + number,
                createdAt,
                closedAt,
                "",
                state,
                closed,
                author,
                userType,
                false,
                ""
        );
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

    private static void requireAmbiguous(DisclosureResult result, String message) {
        require(result.disclosed(), message + " should be detected");
        require("possible_ambiguous".equals(result.classification()), message + " should be ambiguous");
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private static class FakeGitHubClient extends GitHubClient {
        private final Map<String, String> bodies = new java.util.LinkedHashMap<>();
        private final Map<String, String> failures = new java.util.LinkedHashMap<>();
        private final List<String> requests = new java.util.ArrayList<>();
        private final Map<String, List<PullRequestData>> pages = new java.util.LinkedHashMap<>();
        private final List<String> pageRequests = new java.util.ArrayList<>();

        void add(String repository, int number, String body) {
            bodies.put(repository + "#" + number, body);
        }

        void fail(String repository, int number, String reason) {
            failures.put(repository + "#" + number, reason);
        }

        void failTimes(String repository, int number, String reason, int times) {
            transientFailures.put(repository + "#" + number, new TransientFailure(reason, times));
        }

        void addPage(String repository, int page, List<PullRequestData> prs) {
            pages.put(repository + "#page" + page, List.copyOf(prs));
        }

        List<String> requests() {
            return List.copyOf(requests);
        }

        List<String> pages() {
            return List.copyOf(pageRequests);
        }

        @Override
        public PullRequestData getPullRequest(String owner, String repo, int number) throws java.io.IOException {
            String repository = owner + "/" + repo;
            String key = repository + "#" + number;
            requests.add(key);
            TransientFailure transientFailure = transientFailures.get(key);
            if (transientFailure != null && transientFailure.remaining() > 0) {
                transientFailure.decrement();
                throw new java.io.IOException(transientFailure.reason());
            }
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

        @Override
        public List<PullRequestData> getClosedPullRequestsPage(String owner, String repo, int page) {
            String key = owner + "/" + repo + "#page" + page;
            pageRequests.add(key);
            return pages.getOrDefault(key, List.of());
        }

        private final Map<String, TransientFailure> transientFailures = new java.util.LinkedHashMap<>();

        private static class TransientFailure {
            private final String reason;
            private int remaining;

            TransientFailure(String reason, int remaining) {
                this.reason = reason;
                this.remaining = remaining;
            }

            String reason() {
                return reason;
            }

            int remaining() {
                return remaining;
            }

            void decrement() {
                remaining--;
            }
        }
    }

    private static class FakeHttpClient extends HttpClient {
        private final ArrayDeque<Object> outcomes = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        void enqueue(Object outcome) {
            outcomes.add(outcome);
        }

        List<HttpRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            requests.add(request);
            if (outcomes.isEmpty()) {
                throw new IOException("missing fake HTTP outcome");
            }
            Object outcome = outcomes.removeFirst();
            if (outcome instanceof IOException io) {
                throw io;
            }
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) outcome;
            return response;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_2;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("sendAsync not used");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("sendAsync not used");
        }
    }

    private record FakeHttpResponse(int statusCode, String body, Map<String, String> headerValues) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            Map<String, List<String>> values = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> entry : headerValues.entrySet()) {
                values.put(entry.getKey(), List.of(entry.getValue()));
            }
            return HttpHeaders.of(values, (name, value) -> true);
        }

        @Override
        public URI uri() {
            return URI.create("https://api.github.com/repos/owner/repo/pulls/7");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}
