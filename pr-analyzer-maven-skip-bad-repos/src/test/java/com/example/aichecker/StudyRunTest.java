package com.example.aichecker;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

public class StudyRunTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path config, repos, register, root;
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String START = "2026-05-22T09:58:43.766425Z", END = "2026-09-22T09:58:43.766425Z";
    @Before public void setup() throws Exception {
        config = temp.newFile("input.properties").toPath();
        var p = StudyFiles.read(Path.of("study/study-v1.properties")); p.remove("evidence.manifest");
        StudyFiles.properties(config, p);
        repos = temp.newFile("repos.txt").toPath(); Files.writeString(repos, "owner/repo\n");
        register = temp.newFile("exclusions.csv").toPath(); Files.copy(Path.of("study/exclusions-v1.csv"), register, StandardCopyOption.REPLACE_EXISTING);
        root = temp.getRoot().toPath().resolve("run");
    }
    private StudyRun run() throws IOException { return StudyRun.initialize(config, repos, register, root, true); }
    private GitHubClient client(StudyRun run) { return new GitHubClient(run.configuration(), run.archive()); }
    static String pageUrl(String repo, int page) { return "https://api.github.com/repos/" + repo + "/pulls?state=closed&sort=updated&direction=desc&per_page=100&page=" + page; }
    private void pages(StudyRun run, String repo, String... prs) throws IOException {
        run.archive().store(pageUrl(repo, 1), ACCEPT, "[" + String.join(",", prs) + "]", END, "http-response", "synthetic-test");
        run.archive().store(pageUrl(repo, 2), ACCEPT, "[]", END, "http-response", "synthetic-test");
        for (String pr : prs) {
            int n = JsonTools.intValue(pr, "number", -1);
            run.archive().store("https://github.com/" + repo + "/pull/" + n, "text/html", "<p>page text</p>", END, "http-response", "synthetic-test");
        }
    }
    static String pr(int n, String closed, String login, String type, String body) {
        return "{\"number\":" + n + ",\"html_url\":\"https://github.com/owner/repo/pull/" + n
                + "\",\"title\":\"Fixture\",\"state\":\"closed\",\"created_at\":\"2026-05-01T00:00:00Z\",\"closed_at\":\"" + closed
                + "\",\"merged_at\":null,\"body\":\"" + body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                + "\",\"user\":{\"login\":\"" + login + "\",\"type\":\"" + type + "\"}}";
    }
    @Test public void frozenWindowOrderingAndWindowBotCountsReplayExactly() throws Exception {
        StudyRun run = run();
        pages(run, "owner/repo", pr(1, START, "human", "User", "No AI was used"), pr(2, END, "human", "User", "I used ChatGPT"),
                pr(3, END, "human", "User", "Fix typo"), pr(4, "2026-05-22T09:58:43.766424Z", "helper[bot]", "Bot", ""),
                pr(5, "2026-09-22T09:58:43.766426Z", "human", "User", ""), pr(6, END, "helper[bot]", "Bot", ""));
        assertEquals("COMPLETE_WITH_EXCLUSIONS", run.execute(client(run), null));
        var rows = CsvTools.readRows(root.resolve("pr_dataset.csv"));
        assertEquals(List.of("3", "2", "1"), rows.stream().map(r -> r.get("PR #")).toList());
        assertEquals("1", CsvTools.readRows(root.resolve("repo_summary.csv")).get(0).get("Bot PRs Excluded"));
        assertEquals(3, CsvTools.readRows(root.resolve("exclusion_audit.csv")).size());
        Path replay = temp.getRoot().toPath().resolve("replay");
        assertEquals("COMPLETE_WITH_EXCLUSIONS", StudyRun.replay(root, replay));
        assertArrayEquals(Files.readAllBytes(root.resolve("pr_dataset.csv")), Files.readAllBytes(replay.resolve("pr_dataset.csv")));
        assertFalse(Files.readString(replay.resolve("pr_dataset.csv")).contains("PENDING"));
    }
    @Test public void crashAfterCheckpointResumesWithoutLostOrDuplicateRows() throws Exception {
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "human", "User", "Fix typo"), pr(2, END, "human", "User", "I used AI"));
        run.onCheckpoint(row -> { throw new SimulatedCrash(); });
        assertThrows(SimulatedCrash.class, () -> run.execute(client(run), null));
        assertEquals(1, StudyFiles.files(root.resolve("rows"), ".properties").size());
        assertEquals("RUNNING", StudyFiles.read(root.resolve("status.properties")).getProperty("status"));
        StudyRun resumed = run();
        assertEquals("COMPLETE", resumed.execute(client(resumed), null));
        assertEquals(2, CsvTools.readRows(root.resolve("pr_dataset.csv")).size());
        assertEquals("2", CsvTools.readRows(root.resolve("repo_summary.csv")).get(0).get("Eligible PRs Reviewed"));
        assertEquals("COMPLETE", StudyRun.resume(root));
        assertEquals(2, StudyFiles.files(root.resolve("rows"), ".properties").size());
    }
    @Test public void failedRepositoryIsIncompleteAndRetryRebuildsSummary() throws Exception {
        StudyRun run = run();
        assertEquals("INCOMPLETE", run.execute(client(run), null));
        assertTrue(Files.readString(root.resolve("pr_dataset.csv")).contains("INCOMPLETE"));
        assertFalse(Files.readString(root.resolve("pr_dataset.csv")).contains("PENDING"));
        assertTrue(StudyFiles.files(root.resolve("events"), ".properties").stream().anyMatch(p -> {
            try { return StudyFiles.read(p).getProperty("action").equals("FAILED_RETRY_ON_RESUME"); } catch (IOException e) { throw new RuntimeException(e); }
        }));
        pages(run, "owner/repo", pr(1, START, "human", "User", "Fix typo"));
        assertEquals("COMPLETE", run.execute(client(run), null));
        assertEquals("1", CsvTools.readRows(root.resolve("repo_summary.csv")).get(0).get("Eligible PRs Reviewed"));
    }
    @Test public void changedConfigurationAndRepositoryListCannotAppend() throws Exception {
        run(); var p = StudyFiles.read(config); p.setProperty("seed.kappa", "99"); StudyFiles.properties(config, p);
        assertThrows(IOException.class, this::run);
        Path newRoot = temp.getRoot().toPath().resolve("new-version");
        StudyRun.initialize(config, repos, register, newRoot, true);
        assertNotEquals(StudyFiles.read(root.resolve("identity.properties")).getProperty("dataset.version"), StudyFiles.read(newRoot.resolve("identity.properties")).getProperty("dataset.version"));
        Files.writeString(repos, "different/repo\n");
        assertThrows(IOException.class, () -> StudyRun.initialize(config, repos, register, newRoot, true));
    }
    @Test public void humanLookingSubstringRequiresReviewAndExplicitDecisionIsTraceable() throws Exception {
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "roboticsfan", "User", "Fix typo"));
        assertEquals(BotDetector.Assessment.REVIEW, BotDetector.assess("roboticsfan", "User"));
        assertEquals("INCOMPLETE", run.execute(client(run), null));
        assertEquals(0, CsvTools.readRows(root.resolve("pr_dataset.csv")).size());
        assertEquals("roboticsfan", CsvTools.readRows(root.resolve("bot_review_queue.csv")).get(0).get("Login"));
        Files.writeString(register, "1,account,roboticsfan,Include,Synthetic fixture human review,2026-10-19,Test reviewer,All study tables,fixture://human-review\n", StandardOpenOption.APPEND);
        assertThrows(IOException.class, this::run);
        Path reviewedRoot = temp.getRoot().toPath().resolve("reviewed");
        StudyRun reviewed = StudyRun.initialize(config, repos, register, reviewedRoot, true);
        pages(reviewed, "owner/repo", pr(1, START, "roboticsfan", "User", "Fix typo"));
        assertEquals("COMPLETE", reviewed.execute(client(reviewed), null));
        assertEquals("Include", CsvTools.readRows(reviewedRoot.resolve("exclusion_audit.csv")).get(0).get("Action"));
    }
    @Test public void durableWritesSupportSymlinkedOutputDirectories() throws Exception {
        Path actual = temp.newFolder("actual-output").toPath();
        Path alias = temp.getRoot().toPath().resolve("output-alias"); Files.createSymbolicLink(alias, actual);
        StudyFiles.text(alias.resolve("checkpoint.txt"), "durable");
        assertEquals("durable", Files.readString(actual.resolve("checkpoint.txt")));
    }

    @Test public void exclusionsAreDrivenBySuppliedRegisterNotOneHardcodedRepository() throws Exception {
        Files.writeString(register, "1,repository,other/project,Exclude,Synthetic scope decision,2026-10-19,Fixture reviewer,All tables,fixture://scope\n", StandardOpenOption.APPEND);
        Path data = temp.newFile().toPath(); Files.writeString(data, "https://github.com/other/project\n");
        assertThrows(IllegalArgumentException.class, () -> StudyDatasetExclusionValidator.validate(List.of(data), register));
        Files.writeString(data, "https://github.com/other/project-name\n");
        assertEquals(1, StudyDatasetExclusionValidator.validate(List.of(data), register).filesChecked());
    }

    @Test public void botQueueDeduplicatesAccountsButAuditRetainsEveryPr() throws Exception {
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "roboticsfan", "User", "Fix typo"), pr(2, END, "roboticsfan", "User", "Fix typo"));
        assertEquals("INCOMPLETE", run.execute(client(run), null));
        assertEquals(1, CsvTools.readRows(root.resolve("bot_review_queue.csv")).size());
        assertEquals(2, CsvTools.readRows(root.resolve("exclusion_audit.csv")).size());
    }
    @Test public void sampleSizesAndSeedsAreFrozenAndApplied() throws Exception {
        var p = StudyFiles.read(config); p.setProperty("sample.kappa.per.repository", "1"); p.setProperty("sample.validation.per.repository", "2"); StudyFiles.properties(config, p);
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "human", "User", "Fix typo"), pr(2, END, "human", "User", "Fix typo"), pr(3, END, "human", "User", "Fix typo"));
        assertEquals("COMPLETE", run.execute(client(run), null));
        assertEquals(List.of("owner/repo#3"), CsvTools.readRows(root.resolve("kappa_sample.csv")).stream().map(r -> r.get("Sample ID")).toList());
        assertEquals(2, CsvTools.readRows(root.resolve("validation_sample.csv")).size());
        Path replay = temp.getRoot().toPath().resolve("sample-replay"); StudyRun.replay(root, replay);
        assertArrayEquals(Files.readAllBytes(root.resolve("validation_sample.csv")), Files.readAllBytes(replay.resolve("validation_sample.csv")));
    }
    @Test public void repositoryFailureCannotHideBehindAnotherCompletedRepository() throws Exception {
        Files.writeString(repos, "owner/repo\nother/repo\n");
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "human", "User", "Fix typo"));
        assertEquals("INCOMPLETE", run.execute(client(run), null));
        assertEquals(1, CsvTools.readRows(root.resolve("pr_dataset.csv")).size());
        assertEquals(2, CsvTools.readRows(root.resolve("repo_summary.csv")).size());
        assertThrows(IOException.class, () -> StudyBundle.write(root, temp.getRoot().toPath().resolve("incomplete.zip")));
        assertThrows(IOException.class, () -> StudyRun.replay(root, temp.getRoot().toPath().resolve("incomplete-replay")));
    }

    @Test public void reviewedDerivationReusesSnapshotAndOfflineNamedReanalysis() throws Exception {
        StudyRun original = run(); pages(original, "owner/repo", pr(1, START, "roboticsfan", "User", "I used ChatGPT"));
        assertEquals("INCOMPLETE", original.execute(client(original), null));
        Files.writeString(register, "1,account,roboticsfan,Include,Synthetic reviewed human,2026-10-19,Test reviewer,All tables,fixture://human\n", StandardOpenOption.APPEND);
        Path derived = temp.getRoot().toPath().resolve("derived");
        assertEquals("COMPLETE", StudyRun.derive(root, config, repos, register, derived));
        assertEquals("OFFLINE_DERIVED", StudyFiles.read(derived.resolve("identity.properties")).getProperty("source.mode"));
        assertEquals("Yes", StudyRun.offlineAnalyzer(derived).analyzeExistingPullRequest("owner/repo", 1).disclosureStatus());
        assertThrows(IOException.class, () -> StudyRun.offlineAnalyzer(derived).analyzeExistingPullRequest("owner/repo", 999));
        Path reanalyzed = temp.getRoot().toPath().resolve("reanalysis.csv");
        assertEquals(1, KappaSampleReanalysisWorkflow.reanalyze(derived.resolve("kappa_sample.csv"), reanalyzed, StudyRun.offlineAnalyzer(derived)).succeeded());
    }
    @Test public void frozenPolicyTrackerReplaysCorrelationAndRejectsChangedInput() throws Exception {
        Path tracker = temp.newFile("policy-tracker.csv").toPath();
        Files.writeString(tracker, "Repo,Repository Link,Visibility Score\nowner/repo,https://github.com/owner/repo,2\n");
        var p = StudyFiles.read(config); p.setProperty("analysis.policy.tracker", "policy-tracker.csv"); StudyFiles.properties(config, p);
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "human", "User", "I used ChatGPT"));
        assertEquals("COMPLETE", run.execute(client(run), null));
        Path replay = temp.getRoot().toPath().resolve("policy-replay");
        StudyRun.replay(root, replay);
        assertArrayEquals(Files.readAllBytes(root.resolve("visibility_summary.csv")), Files.readAllBytes(replay.resolve("visibility_summary.csv")));
        Files.writeString(tracker, "changed tracker");
        assertThrows(IOException.class, this::run);
    }
    @Test public void suppliedSupplementaryChangesCannotResumeAndArchiveMetadataIsSealed() throws Exception {
        Path file = temp.newFile("comment.txt").toPath(); Files.writeString(file, "Reviewer quotation");
        Path manifest = temp.newFile("supplementary.csv").toPath();
        Files.writeString(manifest, "ID,Kind,URL,Retrieved UTC,File,SHA256,Owner\nc1,comment,https://example.test/comment/1," + END + ",comment.txt," + StudyFiles.hash(file) + ",Test reviewer\n");
        var p = StudyFiles.read(config); p.setProperty("evidence.manifest", "supplementary.csv"); StudyFiles.properties(config, p);
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "human", "User", "Fix typo")); run.execute(client(run), null);
        Files.writeString(file, "Changed comment"); assertThrows(IOException.class, this::run);
        Path response = StudyFiles.files(root.resolve("evidence/responses"), ".properties").get(0);
        Properties metadata = StudyFiles.read(response); metadata.setProperty("retrieved.utc", "2000-01-01T00:00:00Z"); StudyFiles.properties(response, metadata);
        assertThrows(IOException.class, () -> StudyRun.replay(root, temp.getRoot().toPath().resolve("tampered")));
    }

    @Test public void rawBodiesAndSupplementarySnapshotsHaveHashesAndRejectChanges() throws Exception {
        Path policy = temp.newFile("policy.txt").toPath(); Files.writeString(policy, "All contributors must answer.\n");
        Path manifest = temp.newFile("snapshots.csv").toPath();
        Files.writeString(manifest, "ID,Kind,URL,Retrieved UTC,File,SHA256,Owner\nv1,policy,https://example.test/policy," + END + ",policy.txt," + StudyFiles.hash(policy) + ",Test reviewer\n");
        var p = StudyFiles.read(config); p.setProperty("evidence.manifest", "snapshots.csv"); StudyFiles.properties(config, p);
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "human", "User", "I used ChatGPT"));
        assertEquals("COMPLETE", run.execute(client(run), null));
        assertThrows(IOException.class, () -> run.archive().store(pageUrl("owner/repo", 1), ACCEPT, "[]", END, "http-response", "test"));
        Path evidence = StudyFiles.files(root.resolve("evidence/objects"), "").get(0);
        Files.writeString(evidence, "changed source");
        assertThrows(IOException.class, () -> StudyRun.replay(root, temp.getRoot().toPath().resolve("bad-replay")));
    }
    @Test public void freshUnpackedBundleReplaysWithStandaloneJarAndBlockedNetwork() throws Exception {
        StudyRun run = run(); pages(run, "owner/repo", pr(1, START, "human", "User", "I used ChatGPT"));
        run.execute(client(run), null);
        Path bundle = temp.getRoot().toPath().resolve("release.zip"); StudyBundle.write(root, bundle);
        Path unpacked = temp.newFolder("unpacked").toPath();
        try (var zip = new ZipInputStream(Files.newInputStream(bundle))) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                assertFalse(e.getName().contains("mesa_removal") || e.getName().contains("stac_removal"));
                Path dest = unpacked.resolve(e.getName()); Files.createDirectories(dest.getParent()); Files.copy(zip, dest);
            }
        }
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Dhttps.proxyHost=127.0.0.1", "-Dhttps.proxyPort=9", "-jar", "analyzer.jar", "--replay-study", "study", "replay")
                .directory(unpacked.toFile()).redirectErrorStream(true).start();
        boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue("Offline replay must terminate without waiting for the network", finished);
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(output, 0, process.exitValue());
        assertArrayEquals(Files.readAllBytes(root.resolve("repo_summary.csv")), Files.readAllBytes(unpacked.resolve("replay/repo_summary.csv")));
    }
    @Test public void offlineMissingSourceNeverInvokesFetcherAndRecollectionDiffsAreExplicit() throws Exception {
        EvidenceArchive archive = new EvidenceArchive(temp.newFolder().toPath(), true);
        AtomicInteger fetches = new AtomicInteger();
        assertThrows(IOException.class, () -> archive.response("https://example.test/missing", ACCEPT, () -> { fetches.incrementAndGet(); return "live"; }));
        assertEquals(0, fetches.get());
        Path one = temp.newFolder().toPath(), two = temp.newFolder().toPath();
        new EvidenceArchive(one.resolve("evidence"), false).store("https://example.test/pr", ACCEPT, "old", END, "http-response", "test");
        new EvidenceArchive(two.resolve("evidence"), false).store("https://example.test/pr", ACCEPT, "new", END, "http-response", "test");
        Path diff = temp.newFile().toPath(); EvidenceArchive.compare(one, two, diff);
        assertEquals("Changed", CsvTools.readRows(diff).get(0).get("Change"));
    }
    @Test public void reviewedBotSampleReportsDeferralsAndUnreviewedRows() throws Exception {
        Path sample = temp.newFile().toPath(), result = temp.newFile().toPath();
        Files.writeString(sample, "Login,User Type,Reviewed Label,Reviewer,Reviewed Date,Evidence\nroboticsfan,User,Human,fixture reviewer,2026-10-19,fixture://human\nhelper[bot],Bot,Bot,fixture reviewer,2026-10-19,fixture://bot\nnormal,User,Human,fixture reviewer,2026-10-19,fixture://normal\npending,User,,,,\n");
        BotRuleValidation.write(sample, result);
        var row = CsvTools.readRows(result).get(0);
        assertEquals("1", row.get("Deferred To Review")); assertEquals("0", row.get("False Bot"));
        assertEquals("1", row.get("Unreviewed")); assertEquals("PARTIAL_REVIEW", row.get("Status"));
    }
    private static class SimulatedCrash extends Error {}
}
