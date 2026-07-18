package com.example.aichecker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PrAnalyzer {
    private final GitHubClient gitHubClient;
    private final AiDisclosureDetector detector = new AiDisclosureDetector();
    private final List<String> skippedRepos = new ArrayList<>();
    private final Map<String, Integer> botPrsExcluded = new LinkedHashMap<>();

    public PrAnalyzer() {
        this(new GitHubClient());
    }

    PrAnalyzer(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
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

    public List<PrReportRow> analyzeLatestClosedHumanPrs(RepoUrl repoUrl, int targetCount) throws Exception {
        List<PrReportRow> rows = new ArrayList<>();
        botPrsExcluded.put(repoUrl.fullName(), 0);
        int page = 1;
        System.out.println();
        System.out.println("Latest closed human PRs for " + repoUrl.fullName());
        System.out.println(PrReportRow.consoleHeader());
        while (rows.size() < targetCount && page <= 20) {
            List<PullRequestData> prs = gitHubClient.getClosedPullRequestsPage(repoUrl.owner(), repoUrl.repo(), page);
            if (prs.isEmpty()) break;
            for (PullRequestData pr : prs) {
                if (rows.size() >= targetCount) break;
                if (!pr.closed()) continue;
                if (!BotDetector.isHuman(pr.author(), pr.userType())) {
                    botPrsExcluded.merge(repoUrl.fullName(), 1, Integer::sum);
                    continue;
                }
                PrReportRow row = analyze(pr);
                rows.add(row);
                System.out.println(row.toConsoleTableRow(rows.size()));
            }
            page++;
        }
        System.out.println(PrReportRow.consoleFooter());
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
}
