package com.example.aichecker;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class EvidenceContractTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final AiDisclosureDetector detector = new AiDisclosureDetector();

    private PrReportRow analyze(String body, String html, boolean failHtml) throws Exception {
        GitHubClient client = new GitHubClient() {
            @Override public PullRequestData getPullRequest(String owner, String repo, int n) {
                return new PullRequestData("owner/repo", n, "https://github.com/owner/repo/pull/" + n,
                        "Title", "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", "",
                        "closed", true, "contributor", "User", false, body);
            }
            @Override public String fetchHtml(String url) throws IOException {
                if (failHtml) throw new IOException("fixture HTML timeout");
                return html;
            }
        };
        return new PrAnalyzer(client).analyzeExistingPullRequest("owner/repo", 1);
    }

    @Test public void legacySchemaCannotBeResumedOrCorrelated() throws Exception {
        var legacy = temp.newFile().toPath();
        String content = "Repo,PR #,Status,AI Disclosure Present\nowner/repo,1,Closed,No\n";
        Files.writeString(legacy, content);
        assertThrows(IOException.class, () -> CsvWriter.completedPrDatasetIds(legacy));
        assertThrows(IOException.class, () -> CsvWriter.appendPrDatasetRows(legacy, List.of()));
        var policy = temp.newFile().toPath();
        Files.writeString(policy, "Repo,Repository Link,Visibility Score\nowner/repo,https://github.com/owner/repo,2\n");
        assertThrows(IllegalArgumentException.class, () -> DisclosureVisibilityCorrelationWorkflow.analyze(
                legacy, policy, temp.newFile().toPath(), temp.newFile().toPath()));
        assertEquals(content, Files.readString(legacy));
    }

    @Test public void missingBodyAndFailedHtmlNeverCountAsNo() throws Exception {
        PrReportRow row = analyze(null, "", true);
        assertEquals("Unknown", row.disclosureStatus());
        var dataset = temp.newFile().toPath();
        CsvWriter.writePrDataset(dataset, List.of(row));
        Map<String, String> csv = CsvTools.readRows(dataset).get(0);
        assertEquals("Unknown", csv.get("AI Disclosure Present"));
        assertEquals("Incomplete", csv.get("Evidence Completeness"));
        assertEquals("No", csv.get("HTML Scrape Success"));
        assertEquals("fixture HTML timeout", csv.get("HTML Scrape Error"));
        assertEquals("MANUAL_REVIEW_REQUIRED", csv.get("Review Status"));
        assertEquals("Unresolved", csv.get("Policy Requirement"));
        var summary = temp.newFile().toPath();
        CsvWriter.writeRepoComplianceSummary(summary, List.of(row), Map.of());
        var counts = CsvTools.readRows(summary).get(0);
        assertEquals("0", counts.get("No Disclosure Count"));
        assertEquals("1", counts.get("Unknown Disclosure Count"));
        assertEquals("", counts.get("Automated Disclosure Rate"));
    }

    @Test public void bodyEvidenceSurvivesHtmlFailureWithAttribution() throws Exception {
        PrReportRow row = analyze("I used ChatGPT to write tests.", "", true);
        assertEquals("Yes", row.disclosureStatus());
        assertEquals("Complete", row.evidenceCompleteness());
        assertEquals("owner/repo#1:body", row.evidenceId());
        var file = temp.newFile().toPath();
        CsvWriter.writePrDataset(file, List.of(row));
        var csv = CsvTools.readRows(file).get(0);
        assertEquals(row.url(), csv.get("Evidence URL"));
        assertEquals("contributor", csv.get("Evidence Author"));
        assertEquals("2026-01-01T00:00:00Z", csv.get("Evidence Created At"));
        assertEquals("PR_BODY_ONLY_V1", csv.get("Source Coverage Rule"));
        assertEquals("No", analyze("Fixes a typo.", "", true).disclosureStatus());
    }

    @Test public void thirdPartyAndMaintainerPageTextAreNeverContributorEvidence() throws Exception {
        String page = "<div>maintainer: I used ChatGPT to write tests</div><div>third-party: AI was used</div>";
        assertEquals("No", analyze("Fixes a typo.", page, false).disclosureStatus());
        assertEquals("Unknown", analyze(null, page, false).disclosureStatus());
    }

    @Test public void quotedMaintainerAndCodeExamplesAreExcluded() {
        for (String quote : List.of("> Maintainer said: I used ChatGPT to write tests.\ncontinued quote with AI was used",
                "<blockquote>I used ChatGPT.</blockquote>", "```text\nI used ChatGPT.\n```")) {
            assertFalse(detector.detect(quote, "").disclosed());
        }
        assertTrue(detector.detect("> Maintainer said: I used ChatGPT.\n\nI used Claude to write tests.", "").disclosed());
    }

    @Test public void unfilledTemplatesAreNotNonUseStatements() {
        for (String repo : List.of("owner/repo", "qgis/QGIS", "OSGeo/gdal")) {
            assertFalse(detector.detect(repo, "- [ ] AI tools were used to prepare this pull request", "").disclosed());
        }
        assertFalse(detector.detect("## AI disclosure\n<!-- Describe tools here -->", "").disclosed());
    }

    @Test public void acknowledgementsAreSeparateFromUseAndNonUse() {
        var acknowledgement = detector.detect("searxng/searxng", "- [x] I hereby confirm that this PR conforms with the AI Policy.", "");
        assertFalse(acknowledgement.disclosed());
        assertEquals("policy_acknowledgement", acknowledgement.classification());
        assertEquals("policy_acknowledgement", detector.detect("- [x] I have read the AI policy.", "").classification());
        assertEquals("possible_positive", detector.detect("- [x] I used AI in accordance with the policy.", "").classification());
        assertEquals("possible_negative", detector.detect("I did not use AI.", "").classification());
    }

    @Test public void contradictoryAnswersRequireReview() {
        for (String body : List.of("I used ChatGPT.\nI did not use AI.",
                "- [x] AI was used\n- [x] No AI was used",
                "- [x] AI was used\n\nI did not use AI.")) {
            assertEquals("possible_ambiguous", detector.detect(body, "").classification());
        }
    }

    @Test public void correlationExcludesUnknownFromBothInputFormats() throws Exception {
        var dataset = temp.newFile().toPath();
        var summary = temp.newFile().toPath();
        var policy = temp.newFile().toPath();
        Files.writeString(policy, "Repo,Repository Link,Visibility Score\nowner/repo,https://github.com/owner/repo,2\n");
        var rows = List.of(analyze(null, "", true), analyze("Fixes a typo", "", true));
        // Separate identifiers for the two fixtures to avoid correlation deduplication.
        CsvWriter.writePrDataset(dataset, rows);
        Files.writeString(dataset, Files.readString(dataset).replaceFirst("\"owner/repo\",\"1\"", "\"owner/repo\",\"2\""));
        CsvWriter.writeRepoComplianceSummary(summary, rows, Map.of());
        for (var input : List.of(dataset, summary)) {
            var result = DisclosureVisibilityCorrelationWorkflow.analyze(input, policy, temp.newFile().toPath(), temp.newFile().toPath());
            var r = result.summaryRows().get(0);
            assertEquals(1, r.prsWithoutDisclosure());
            assertEquals(1, r.successfullyAnalysedEligiblePrs());
            assertEquals(1, r.failedOrExcludedPrs());
        }
    }
}
