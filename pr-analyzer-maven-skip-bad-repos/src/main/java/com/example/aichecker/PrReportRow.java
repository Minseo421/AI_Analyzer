package com.example.aichecker;

public record PrReportRow(
        String repository,
        int pullRequestNumber,
        String url,
        String title,
        String createdAt,
        String closedAt,
        String mergedAt,
        String author,
        String state,
        boolean closed,
        boolean humanAuthor,
        String githubUserType,
        boolean aiDisclosure,
        boolean merged,
        String aiDisclosureEvidence,
        String aiDisclosureClassification,
        String aiDisclosureSource,
        boolean htmlScrapeSuccess,
        String htmlScrapeError
) {
    public boolean evidenceComplete() {
        return "PR body".equals(aiDisclosureSource) && author != null && !author.isBlank()
                && url != null && !url.isBlank() && createdAt != null && !createdAt.isBlank();
    }

    public String disclosureStatus() {
        return evidenceComplete() ? (aiDisclosure ? "Yes" : "No") : "Unknown";
    }

    public String evidenceCompleteness() { return evidenceComplete() ? "Complete" : "Incomplete"; }
    public String evidenceId() { return evidenceComplete() ? repository + "#" + pullRequestNumber + ":body" : ""; }
    public String evidenceUrl() { return evidenceComplete() ? url : ""; }
    public String reviewStatus() { return "MANUAL_REVIEW_REQUIRED"; }
    public String sourceCoverageRule() { return "PR_BODY_ONLY_V1"; }

    public static String consoleHeader() {
        return "┌─────┬──────────────────────────────┬────────┬──────────────────────┬───────────────┬────────┐\n"
                + "│ No. │ Repository                   │ PR #   │ Author               │ AI Disclosure │ Merged │\n"
                + "├─────┼──────────────────────────────┼────────┼──────────────────────┼───────────────┼────────┤";
    }

    public static String consoleFooter() {
        return "└─────┴──────────────────────────────┴────────┴──────────────────────┴───────────────┴────────┘";
    }

    public String toConsoleTableRow(int index) {
        return String.format(
                "│ %3d │ %-28s │ #%-5d │ %-20s │ %-13s │ %-6s │",
                index,
                fit(repository, 28),
                pullRequestNumber,
                fit(author, 20),
                disclosureStatus(),
                merged ? "YES" : "NO"
        );
    }

    private static String fit(String value, int width) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() <= width) return cleaned;
        if (width <= 1) return cleaned.substring(0, width);
        return cleaned.substring(0, width - 1) + "…";
    }

    public String toPrettyString() {
        return "Repository: " + repository + "\n"
                + "Pull request number: " + pullRequestNumber + "\n"
                + "URL: " + url + "\n"
                + "Title: " + title + "\n"
                + "Created date: " + createdAt + "\n"
                + "Closed date: " + closedAt + "\n"
                + "Merged date: " + mergedAt + "\n"
                + "Author: " + author + "\n"
                + "State: " + state + "\n"
                + "Closed: " + closed + "\n"
                + "Human author: " + humanAuthor + "\n"
                + "GitHub user type: " + githubUserType + "\n"
                + "Automated body disclosure: " + disclosureStatus() + "\n"
                + "Merged: " + merged + "\n"
                + "AI Disclosure evidence: " + aiDisclosureEvidence + "\n"
                + "AI Disclosure classification: " + aiDisclosureClassification + "\n"
                + "AI Disclosure source: " + aiDisclosureSource + "\n"
                + "HTML scrape success: " + htmlScrapeSuccess + "\n"
                + "HTML scrape error: " + htmlScrapeError;
    }
}
