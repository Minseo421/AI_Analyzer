package com.example.aichecker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

public class PrAnalyzer {
    private final GitHubClient gitHubClient;
    private final AiDisclosureDetector detector = new AiDisclosureDetector();
    private final ZonedDateTime runDateTime;
    private final Instant closedAtCutoff;
    private final List<String> skippedRepos = new ArrayList<>();
    private final Map<String, Integer> botPrsExcluded = new LinkedHashMap<>();
    private final Map<String, CollectionSummary> collectionSummaries = new LinkedHashMap<>();

    public PrAnalyzer() {
        this(new GitHubClient(), Clock.systemUTC());
    }

    PrAnalyzer(GitHubClient gitHubClient) {
        this(gitHubClient, Clock.systemUTC());
    }

    PrAnalyzer(GitHubClient gitHubClient, Clock clock) {
        this.gitHubClient = gitHubClient;
        this.runDateTime = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC);
        this.closedAtCutoff = runDateTime.minusMonths(4).toInstant();
    }

    public PrReportRow analyzeSingle(PullRequestUrl prUrl) throws Exception {
        PullRequestData apiData = gitHubClient.getPullRequest(prUrl.owner(), prUrl.repo(), prUrl.number());
        return analyze(apiData);
    }

    public PrReportRow analyzeExistingPullRequest(String repository, int number) throws Exception {
        RepoUrl repoUrl = RepoUrl.parse(repository);
        PullRequestData apiData = gitHubClient.getPullRequest(repoUrl.owner(), repoUrl.repo(), number);
        return analyze(apiData);
    }

    public AnalysisDetail analyzeExistingPullRequestDetail(String repository, int number) throws Exception {
        RepoUrl repoUrl = RepoUrl.parse(repository);
        PullRequestData apiData = gitHubClient.getPullRequest(repoUrl.owner(), repoUrl.repo(), number);
        HtmlData htmlData = fetchHtmlSafe(apiData.url());
        DisclosureResult disclosure = detector.detect(apiData.body(), htmlData.text());
        AiDisclosureDetector.DetectionDiagnostics diagnostics = detector.diagnosePrBody(apiData.body());
        return new AnalysisDetail(toReportRow(apiData, htmlData, disclosure), apiData, htmlData, diagnostics);
    }

    public List<PrReportRow> analyzeLatestClosedHumanPrs(RepoUrl repoUrl, int targetCount) throws Exception {
        List<PullRequestData> collected = new ArrayList<>();
        List<PrReportRow> rows = new ArrayList<>();
        botPrsExcluded.put(repoUrl.fullName(), 0);
        int page = 1;
        System.out.println();
        System.out.println("Latest closed human PRs for " + repoUrl.fullName());
        System.out.println(PrReportRow.consoleHeader());
        while (collected.size() < targetCount) {
            List<PullRequestData> prs = gitHubClient.getClosedPullRequestsPage(repoUrl.owner(), repoUrl.repo(), page);
            if (prs.isEmpty()) break;
            for (PullRequestData pr : prs) {
                if (collected.size() >= targetCount) break;
                if (!pr.closed()) continue;
                if (!BotDetector.isHuman(pr.author(), pr.userType())) {
                    botPrsExcluded.merge(repoUrl.fullName(), 1, Integer::sum);
                    continue;
                }
                collected.add(pr);
            }
            page++;
        }
        int excludedOlderThanCutoff = 0;
        int excludedMalformedClosedAt = 0;
        for (PullRequestData pr : collected) {
            ClosedAtEligibility eligibility = closedAtEligibility(pr);
            if (eligibility.eligible()) {
                PrReportRow row = analyze(pr);
                rows.add(row);
                System.out.println(row.toConsoleTableRow(rows.size()));
            } else if (eligibility.malformed()) {
                excludedMalformedClosedAt++;
                System.out.println("WARNING: Excluding " + repoUrl.fullName() + "#" + pr.number()
                        + " because closed_at is missing or invalid: " + (pr.closedAt() == null || pr.closedAt().isBlank() ? "(blank)" : pr.closedAt()));
            } else {
                excludedOlderThanCutoff++;
            }
        }
        CollectionSummary summary = new CollectionSummary(targetCount, collected.size(), excludedOlderThanCutoff, excludedMalformedClosedAt, rows.size(), runDateTime.toString(), closedAtCutoff.toString());
        collectionSummaries.put(repoUrl.fullName(), summary);
        System.out.println(PrReportRow.consoleFooter());
        printCollectionSummary(repoUrl.fullName(), summary);
        printDisclosureSummary(rows);
        return rows;
    }

    public List<PrReportRow> analyzeMultipleRepos(List<RepoUrl> repoUrls, int targetCountPerRepo) {
        skippedRepos.clear();
        botPrsExcluded.clear();
        List<PrReportRow> allRows = new ArrayList<>();
        for (RepoUrl repoUrl : repoUrls) {
            try {
                List<PrReportRow> rows = analyzeLatestClosedHumanPrs(repoUrl, targetCountPerRepo);
                allRows.addAll(rows);
            } catch (Exception e) {
                String message = repoUrl.fullName() + " -> " + shortError(e.getMessage());
                skippedRepos.add(message);
                System.out.println();
                System.out.println("Skipped repository: " + repoUrl.fullName());
                System.out.println("Reason: " + shortError(e.getMessage()));
                System.out.println("The program will continue with the next repository.");
                System.out.println();
            }
        }
        printMultiRepoSummary(allRows);
        return allRows;
    }

    public Map<String, Integer> botPrsExcludedByRepository() {
        return new LinkedHashMap<>(botPrsExcluded);
    }

    public Map<String, CollectionSummary> collectionSummariesByRepository() {
        return new LinkedHashMap<>(collectionSummaries);
    }

    public CollectionSummary collectionSummary(String repository) {
        return collectionSummaries.get(repository);
    }

    private ClosedAtEligibility closedAtEligibility(PullRequestData pr) {
        if (pr.closedAt() == null || pr.closedAt().isBlank()) {
            return new ClosedAtEligibility(false, true);
        }
        try {
            Instant closedAt = Instant.parse(pr.closedAt());
            return new ClosedAtEligibility(!closedAt.isBefore(closedAtCutoff), false);
        } catch (DateTimeParseException e) {
            return new ClosedAtEligibility(false, true);
        }
    }

    private static void printCollectionSummary(String repository, CollectionSummary summary) {
        System.out.println();
        System.out.println("Collection eligibility summary for " + repository);
        System.out.println("Requested latest closed human PRs: " + summary.requestedCount());
        System.out.println("Collected before date filtering: " + summary.collectedBeforeDateFiltering());
        System.out.println("Excluded as older than four calendar months: " + summary.excludedOlderThanCutoff());
        if (summary.excludedMalformedClosedAt() > 0) {
            System.out.println("Excluded because closed_at was missing or invalid: " + summary.excludedMalformedClosedAt());
        }
        System.out.println("Final PRs written: " + summary.finalCount());
        if (summary.finalCount() < summary.requestedCount()) {
            System.out.println("Fewer than requested were written because the four-calendar-month cutoff is applied after the requested count is collected; excluded rows are not replaced.");
        }
    }

    private void printDisclosureSummary(List<PrReportRow> rows) {
        List<String> disclosedPrs = new ArrayList<>();
        for (PrReportRow row : rows) {
            if (row.aiDisclosure()) {
                disclosedPrs.add("#" + row.pullRequestNumber());
            }
        }
        long disclosed = rows.stream().filter(PrReportRow::aiDisclosure).count();
        double percentage = rows.isEmpty() ? 0.0 : disclosed * 100.0 / rows.size();
        System.out.println();
        System.out.printf("Repository AI disclosure percentage: %.2f%% (%d/%d)%n", percentage, disclosed, rows.size());
        System.out.println("AI disclosure PRs: " + (disclosedPrs.isEmpty() ? "None" : String.join(", ", disclosedPrs)));
        System.out.println("Detailed evidence is saved in the CSV file.");
        System.out.println();
    }

    private void printMultiRepoSummary(List<PrReportRow> rows) {
        System.out.println();
        System.out.println("Combined repository summary");
        System.out.println("┌──────────────────────────────┬─────────────┬────────────┬────────────┐");
        System.out.println("│ Repository                   │ Human PRs   │ Disclosed  │ Percentage │");
        System.out.println("├──────────────────────────────┼─────────────┼────────────┼────────────┤");
        List<String> repos = new ArrayList<>();
        for (PrReportRow row : rows) {
            if (!repos.contains(row.repository())) {
                repos.add(row.repository());
            }
        }
        for (String repo : repos) {
            long total = rows.stream().filter(r -> r.repository().equals(repo)).count();
            long disclosed = rows.stream().filter(r -> r.repository().equals(repo) && r.aiDisclosure()).count();
            double percentage = total == 0 ? 0.0 : disclosed * 100.0 / total;
            System.out.printf("│ %-28s │ %11d │ %10d │ %9.2f%% │%n", fit(repo, 28), total, disclosed, percentage);
        }
        long total = rows.size();
        long disclosed = rows.stream().filter(PrReportRow::aiDisclosure).count();
        double percentage = total == 0 ? 0.0 : disclosed * 100.0 / total;
        System.out.println("├──────────────────────────────┼─────────────┼────────────┼────────────┤");
        System.out.printf("│ %-28s │ %11d │ %10d │ %9.2f%% │%n", "TOTAL", total, disclosed, percentage);
        System.out.println("└──────────────────────────────┴─────────────┴────────────┴────────────┘");
        if (!skippedRepos.isEmpty()) {
            System.out.println();
            System.out.println("Skipped repositories");
            for (String skippedRepo : skippedRepos) {
                System.out.println("- " + skippedRepo);
            }
            System.out.println("These repositories were not included in the CSV percentage calculation.");
        }
        System.out.println();
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

    private static String fit(String value, int width) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() <= width) return cleaned;
        if (width <= 1) return cleaned.substring(0, width);
        return cleaned.substring(0, width - 1) + "…";
    }

    private PrReportRow analyze(PullRequestData apiData) {
        HtmlData htmlData = fetchHtmlSafe(apiData.url());
        DisclosureResult disclosure = detector.detect(apiData.body(), htmlData.text());
        return toReportRow(apiData, htmlData, disclosure);
    }

    private PrReportRow toReportRow(PullRequestData apiData, HtmlData htmlData, DisclosureResult disclosure) {
        boolean human = BotDetector.isHuman(apiData.author(), apiData.userType());
        return new PrReportRow(
                apiData.repository(),
                apiData.number(),
                apiData.url(),
                apiData.title(),
                apiData.createdAt(),
                apiData.closedAt(),
                apiData.mergedAt(),
                apiData.author(),
                apiData.state(),
                apiData.closed(),
                human,
                apiData.userType(),
                disclosure.disclosed(),
                apiData.merged(),
                disclosure.evidence(),
                disclosure.classification(),
                disclosure.source(),
                htmlData.success(),
                htmlData.error()
        );
    }

    private HtmlData fetchHtmlSafe(String url) {
        try {
            String html = gitHubClient.fetchHtml(url);
            return new HtmlData(true, HtmlTools.toPlainText(html), "");
        } catch (Exception e) {
            return new HtmlData(false, "", e.getMessage());
        }
    }

    public record AnalysisDetail(
            PrReportRow row,
            PullRequestData apiData,
            HtmlData htmlData,
            AiDisclosureDetector.DetectionDiagnostics diagnostics
    ) {
    }

    public record CollectionSummary(
            int requestedCount,
            int collectedBeforeDateFiltering,
            int excludedOlderThanCutoff,
            int excludedMalformedClosedAt,
            int finalCount,
            String runDateTime,
            String closedAtCutoff
    ) {
    }

    private record ClosedAtEligibility(boolean eligible, boolean malformed) {
    }
}
