package com.example.aichecker;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** A run directory is a versioned dataset, not an append-only CSV. */
public final class StudyRun {
    private final Path root;
    private final StudyConfiguration config;
    private final ExclusionRegister register;
    private final List<RepoUrl> repos;
    private final Properties identity;
    private final EvidenceArchive archive;
    private final boolean offline;
    private Consumer<PrReportRow> checkpointHook = ignored -> {};

    private StudyRun(Path root, boolean offline) throws IOException {
        this.root = root;
        config = StudyConfiguration.load(root.resolve("config.properties"));
        identity = StudyFiles.read(root.resolve("identity.properties"));
        this.offline = offline || identity.getProperty("source.mode", "LIVE").equals("OFFLINE_DERIVED");
        verifyInput("config.properties", "config.sha256");
        verifyInput("repos.txt", "repositories.sha256");
        verifyInput("exclusions.csv", "exclusions.sha256");
        if (!config.get("runtime.sha256").equals(StudyConfiguration.runtimeHash())) throw new IOException("Implementation changed; use the archived code for resume/replay or start a new dataset version");
        if (config.properties().containsKey("evidence.manifest.sha256")
                && !StudyFiles.hash(root.resolve("supplementary.csv")).equals(config.get("evidence.manifest.sha256"))) throw new IOException("Supplementary manifest changed");
        if (config.properties().containsKey("analysis.policy.tracker.sha256")
                && !StudyFiles.hash(root.resolve("policy-tracker.csv")).equals(config.get("analysis.policy.tracker.sha256"))) throw new IOException("Frozen policy tracker changed");
        register = new ExclusionRegister(root.resolve("exclusions.csv"));
        repos = StudyConfiguration.repositories(root.resolve("repos.txt"));
        archive = new EvidenceArchive(root.resolve("evidence"), this.offline);
    }
    private void verifyInput(String file, String key) throws IOException {
        if (!StudyFiles.hash(root.resolve(file)).equals(identity.getProperty(key))) throw new IOException("Frozen input changed: " + file + "; start a new run");
    }
    static StudyRun initialize(Path configuration, Path repositories, Path exclusions, Path root, boolean offline) throws IOException {
        StudyConfiguration input = StudyConfiguration.load(configuration);
        StudyConfiguration.repositories(repositories);
        new ExclusionRegister(exclusions);
        String inputHash = StudyFiles.hash(configuration), reposHash = StudyFiles.hash(repositories), exclusionsHash = StudyFiles.hash(exclusions);
        if (Files.exists(root.resolve("identity.properties"))) {
            Properties old = StudyFiles.read(root.resolve("identity.properties"));
            if (!inputHash.equals(old.getProperty("input.sha256")) || !reposHash.equals(old.getProperty("repositories.sha256"))
                    || !exclusionsHash.equals(old.getProperty("exclusions.sha256"))) throw new IOException("Configuration changed: choose a new run directory; existing study will not be appended");
            StudyRun existing = new StudyRun(root, offline);
            String supplemental = input.optional("evidence.manifest", "");
            if (!supplemental.isBlank()) {
                Path manifest = configuration.toAbsolutePath().getParent().resolve(supplemental);
                if (!StudyFiles.hash(manifest).equals(existing.config.get("evidence.manifest.sha256"))) throw new IOException("Supplementary input changed; start a new run");
                for (var row : CsvTools.readRows(manifest)) {
                    if (!StudyFiles.hash(manifest.getParent().resolve(row.get("File"))).equals(row.get("SHA256"))) throw new IOException("Supplementary source changed; start a new run");
                }
            }
            String tracker = input.optional("analysis.policy.tracker", "");
            if (!tracker.isBlank() && !StudyFiles.hash(configuration.toAbsolutePath().getParent().resolve(tracker)).equals(existing.config.get("analysis.policy.tracker.sha256"))) throw new IOException("Policy tracker changed; start a new run");
            return existing;
        }
        Files.createDirectories(root);
        try (var children = Files.list(root)) {
            if (children.anyMatch(p -> !p.getFileName().toString().equals(".lock"))) throw new IOException("Run directory is not empty: " + root);
        }
        Properties frozen = input.properties();
        frozen.setProperty("repositories.sha256", reposHash); frozen.setProperty("exclusions.sha256", exclusionsHash);
        frozen.setProperty("runtime.sha256", StudyConfiguration.runtimeHash());
        String supplemental = input.optional("evidence.manifest", "");
        if (!supplemental.isBlank()) {
            Path manifest = configuration.toAbsolutePath().getParent().resolve(supplemental);
            frozen.setProperty("evidence.manifest.sha256", StudyFiles.hash(manifest));
            EvidenceArchive.importSupplementary(manifest, new EvidenceArchive(root.resolve("evidence"), false));
            StudyFiles.atomic(root.resolve("supplementary.csv"), Files.readAllBytes(manifest));
            frozen.setProperty("evidence.manifest", "supplementary.csv");
        }
        String tracker = input.optional("analysis.policy.tracker", "");
        if (!tracker.isBlank()) {
            Path trackerFile = configuration.toAbsolutePath().getParent().resolve(tracker);
            StudyFiles.atomic(root.resolve("policy-tracker.csv"), Files.readAllBytes(trackerFile));
            frozen.setProperty("analysis.policy.tracker.sha256", StudyFiles.hash(trackerFile));
            frozen.setProperty("analysis.policy.tracker", "policy-tracker.csv");
        }
        StudyFiles.atomic(root.resolve("repos.txt"), Files.readAllBytes(repositories));
        StudyFiles.atomic(root.resolve("exclusions.csv"), Files.readAllBytes(exclusions));
        StudyFiles.atomic(root.resolve("study-input.properties"), Files.readAllBytes(configuration));
        StudyFiles.properties(root.resolve("config.properties"), frozen);
        Properties identity = StudyFiles.values("schema.version", "1", "dataset.version", UUID.randomUUID().toString(),
                "input.sha256", inputHash, "config.sha256", StudyFiles.hash(root.resolve("config.properties")),
                "repositories.sha256", reposHash, "exclusions.sha256", exclusionsHash, "collection.started.utc", Instant.now().toString());
        StudyFiles.properties(root.resolve("identity.properties"), identity);
        return new StudyRun(root, offline);
    }
    public static String start(Path configuration, Path repositories, Path exclusions, Path root) throws Exception {
        Files.createDirectories(root);
        try (var channel = FileChannel.open(root.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Study is already running");
            StudyRun run = initialize(configuration, repositories, exclusions, root, false);
            return run.execute(new GitHubClient(run.config, run.archive), null);
        }
    }
    public static String resume(Path root) throws Exception {
        try (var channel = FileChannel.open(root.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Study is already running");
            StudyRun run = new StudyRun(root, false);
            return run.execute(new GitHubClient(run.config, run.archive), null);
        }
    }
    static void verifyFinished(Path root) throws IOException {
        StudyRun run = new StudyRun(root, true);
        Properties state = StudyFiles.read(root.resolve("status.properties"));
        if (!state.getProperty("status", "").startsWith("COMPLETE")) throw new IOException("Study is not complete");
        run.archive.verify(); run.verifyOutputs(state); run.verifyArchiveSeal();
    }
    public static PrAnalyzer offlineAnalyzer(Path root) throws IOException {
        verifyFinished(root);
        StudyRun run = new StudyRun(root, true);
        GitHubClient client = new GitHubClient(run.config, run.archive) {
            @Override public PullRequestData getPullRequest(String owner, String repo, int number) throws IOException {
                return run.archive.archivedPullRequest(owner + "/" + repo, number);
            }
        };
        return new PrAnalyzer(client, run.config);
    }
    public static String derive(Path source, Path configuration, Path repositories, Path exclusions, Path destination) throws Exception {
        if (Files.exists(destination)) throw new IOException("Derived study destination must be new");
        new EvidenceArchive(source.resolve("evidence"), true).verify();
        StudyRun derived = initialize(configuration, repositories, exclusions, destination, true);
        Path evidence = source.resolve("evidence");
        if (Files.exists(evidence)) try (var files = Files.walk(evidence)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = destination.resolve("evidence").resolve(evidence.relativize(file));
                if (Files.exists(target) && !StudyFiles.hash(target).equals(StudyFiles.hash(file))) throw new IOException("Conflicting snapshot identity in derived study; use a new supplementary source ID");
                if (!Files.exists(target)) StudyFiles.atomic(target, Files.readAllBytes(file));
            }
        }
        derived.identity.setProperty("source.mode", "OFFLINE_DERIVED");
        derived.identity.setProperty("derived.from.dataset", StudyFiles.read(source.resolve("identity.properties")).getProperty("dataset.version"));
        StudyFiles.properties(destination.resolve("identity.properties"), derived.identity);
        return derived.execute(new GitHubClient(derived.config, derived.archive), null);
    }

    public static String replay(Path source, Path destination) throws Exception {
        StudyRun original = new StudyRun(source, true);
        Properties finished = StudyFiles.read(source.resolve("status.properties"));
        if (!finished.getProperty("status", "").startsWith("COMPLETE")) throw new IOException("Only a terminal study can be replayed as a finished dataset");
        original.archive.verify();
        original.verifyOutputs(finished);
        original.verifyArchiveSeal();
        if (Files.exists(destination)) throw new IOException("Replay destination must be new");
        Files.createDirectories(destination);
        for (String name : List.of("config.properties", "identity.properties", "study-input.properties", "repos.txt", "exclusions.csv")) {
            StudyFiles.atomic(destination.resolve(name), Files.readAllBytes(source.resolve(name)));
        }
        copyTree(source.resolve("evidence"), destination.resolve("evidence"));
        if (Files.exists(source.resolve("supplementary.csv"))) Files.copy(source.resolve("supplementary.csv"), destination.resolve("supplementary.csv"));
        if (Files.exists(source.resolve("policy-tracker.csv"))) Files.copy(source.resolve("policy-tracker.csv"), destination.resolve("policy-tracker.csv"));
        StudyRun replay = new StudyRun(destination, true);
        // No old processed rows/checkpoints are copied: eligibility and detection run again.
        String status = replay.execute(new GitHubClient(replay.config, replay.archive), finished.getProperty("completed.utc"));
        for (String name : List.of("pr_dataset.csv", "repo_summary.csv", "report.csv", "exclusion_audit.csv", "exclusion_counts.csv", "bot_review_queue.csv", "kappa_sample.csv", "validation_sample.csv")) {
            if (!StudyFiles.hash(source.resolve(name)).equals(StudyFiles.hash(destination.resolve(name)))) {
                replay.status("REPLAY_MISMATCH", "NOT_COMPLETE");
                throw new IOException("Replay output differs: " + name);
            }
        }
        if (Files.exists(source.resolve("policy-tracker.csv"))) {
            for (String name : List.of("visibility_summary.csv", "visibility_correlation.csv")) {
                if (!StudyFiles.hash(source.resolve(name)).equals(StudyFiles.hash(destination.resolve(name)))) {
                    replay.status("REPLAY_MISMATCH", "NOT_COMPLETE");
                    throw new IOException("Replay output differs: " + name);
                }
            }
        }
        return status;
    }
    private static void copyTree(Path source, Path destination) throws IOException {
        if (!Files.exists(source)) return;
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) StudyFiles.atomic(destination.resolve(source.relativize(file)), Files.readAllBytes(file));
        }
    }
    StudyRun onCheckpoint(Consumer<PrReportRow> hook) { checkpointHook = hook; return this; }
    EvidenceArchive archive() { return archive; }
    StudyConfiguration configuration() { return config; }

    String execute(GitHubClient client, String replayCompleted) throws Exception {
        archive.verify();
        if (Files.exists(root.resolve("status.properties"))) {
            Properties previous = StudyFiles.read(root.resolve("status.properties"));
            if (previous.getProperty("status", "").startsWith("COMPLETE")) {
                verifyOutputs(previous);
                verifyArchiveSeal();
                return previous.getProperty("status");
            }
        }
        status("RUNNING", "");
        publish("RUNNING", "NOT_COMPLETE");
        PrAnalyzer analyzer = new PrAnalyzer(client, config);
        boolean incomplete = false;
        for (RepoUrl repo : repos) {
            String repository = repo.fullName();
            Path state = root.resolve("repositories").resolve(StudyFiles.id(repository) + ".properties");
            if (Files.exists(state) && StudyFiles.read(state).getProperty("status", "").equals("COMPLETE")) continue;
            event(repository, Files.exists(state) ? "RETRY" : "START", "Reuse archived successful responses; retry failed API requests");
            try {
                ExclusionRegister.Decision decision = register.find("repository", repository);
                if (decision != null && !decision.action().equals("Include")) {
                    audit(repository, "", "repository", decision.action(), decision, null, "Not inspected");
                    boolean review = decision.action().equals("Review");
                    repositoryStatus(state, repository, review ? "REVIEW_REQUIRED" : "COMPLETE", "Registered repository decision");
                    incomplete |= review; continue;
                }
                Map<Integer, PullRequestData> unique = new LinkedHashMap<>();
                for (int page = 1; ; page++) {
                    List<PullRequestData> prs = client.getClosedPullRequestsPage(repo.owner(), repo.repo(), page);
                    if (prs.isEmpty()) break;
                    for (PullRequestData pr : prs) {
                        if (pr.number() <= 0 || !pr.repository().equalsIgnoreCase(repository)) throw new IOException("Invalid PR identity in archived page");
                        PullRequestData first = unique.putIfAbsent(pr.number(), pr);
                        if (first != null && !first.equals(pr)) throw new IOException("Conflicting versions across pagination for " + repository + "#" + pr.number() + "; recollect a new version");
                    }
                }
                List<PullRequestData> ordered = new ArrayList<>(unique.values());
                ordered.sort(Comparator.comparing(PullRequestData::closedAt, Comparator.nullsFirst(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingInt(PullRequestData::number).reversed()));
                boolean pending = false;
                for (PullRequestData pr : ordered) {
                    String id = repository + "#" + pr.number();
                    String category = category(pr);
                    if (!category.equals("eligible")) {
                        ExclusionRegister.Decision rule = register.category(category);
                        audit(repository, Integer.toString(pr.number()), category, "Exclude", rule, pr, category.startsWith("outside") ? "No" : "Unknown");
                        continue;
                    }
                    ExclusionRegister.Decision rule = register.find("pr", id);
                    if (rule == null) rule = register.find("account", pr.author());
                    BotDetector.Assessment bot = BotDetector.assess(pr.author(), pr.userType());
                    String action = rule == null ? bot == BotDetector.Assessment.BOT ? "Exclude" : bot == BotDetector.Assessment.REVIEW ? "Review" : "Include" : rule.action();
                    if (!action.equals("Include")) {
                        if (rule == null) rule = register.category(bot == BotDetector.Assessment.BOT ? "bot" : "bot_review");
                        String exclusionCategory = rule.scope().equals("account") && action.equals("Exclude") ? "reviewed_bot"
                                : rule.scope().equals("pr") ? "registered_pr" : bot == BotDetector.Assessment.BOT ? "bot" : "bot_review";
                        audit(repository, Integer.toString(pr.number()), exclusionCategory, action, rule, pr, "Yes");
                        pending |= action.equals("Review");
                        continue;
                    }
                    if (rule != null) audit(repository, Integer.toString(pr.number()), "reviewed_account_or_pr", "Include", rule, pr, "Yes");
                    Path checkpoint = root.resolve("rows").resolve(StudyFiles.id(id) + ".properties");
                    if (!Files.exists(checkpoint)) {
                        PrReportRow row = analyzer.analyze(pr);
                        if (!row.humanAuthor()) row = withHumanAuthor(row); // explicit reviewed Include overrides bot heuristic
                        StudyFiles.record(checkpoint, row);
                        checkpointHook.accept(row);
                    }
                }
                repositoryStatus(state, repository, pending ? "REVIEW_REQUIRED" : "COMPLETE", pending ? "Resolve account/PR decisions in a new configuration" : "All unique PRs considered");
                incomplete |= pending;
                event(repository, pending ? "REVIEW_REQUIRED" : "COMPLETE", "Rows durably checkpointed");
            } catch (Exception e) {
                incomplete = true;
                String reason = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "Unknown failure" : e.getMessage());
                repositoryStatus(state, repository, "FAILED", reason);
                event(repository, "FAILED_RETRY_ON_RESUME", reason);
                if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); break; }
            }
        }
        archive.verify();
        String status = incomplete ? "INCOMPLETE" : hasExclusions() ? "COMPLETE_WITH_EXCLUSIONS" : "COMPLETE";
        String completed = incomplete ? "NOT_COMPLETE" : replayCompleted == null ? Instant.now().toString() : replayCompleted;
        publish(status, completed);
        if (!incomplete) sealArchive();
        status(status, completed);
        return status;
    }
    private String category(PullRequestData pr) {
        if (!pr.closed()) return "not_closed";
        try {
            Instant closed = Instant.parse(pr.closedAt());
            if (closed.isBefore(config.start())) return "outside_before";
            if (closed.isAfter(config.end())) return "outside_after";
        } catch (Exception e) { return "invalid_closed_at"; }
        return "eligible";
    }
    private void audit(String repo, String number, String category, String action, ExclusionRegister.Decision d, PullRequestData pr, String inWindow) throws IOException {
        StudyFiles.properties(root.resolve("decisions").resolve(StudyFiles.id(repo + "#" + number + ":" + category) + ".properties"),
                StudyFiles.values("repository", repo, "number", number, "category", category, "action", action, "rule", d.scope() + ":" + d.id(),
                        "reason", d.reason(), "date", d.date(), "owner", d.owner(), "outputs", d.outputs(), "evidence", d.evidence(),
                        "login", pr == null ? "" : pr.author(), "user.type", pr == null ? "" : pr.userType(), "in.window", inWindow));
    }
    private void event(String repository, String action, String reason) throws IOException {
        StudyFiles.properties(root.resolve("events").resolve(UUID.randomUUID() + ".properties"), StudyFiles.values(
                "repository", repository, "action", action, "reason", reason, "at.utc", Instant.now().toString(), "retry.rule", config.get("resume.rule")));
    }
    private static void repositoryStatus(Path path, String repo, String status, String reason) throws IOException {
        StudyFiles.properties(path, StudyFiles.values("repository", repo, "status", status, "reason", reason, "updated.utc", Instant.now().toString()));
    }
    private boolean hasExclusions() throws IOException {
        for (Path p : StudyFiles.files(root.resolve("decisions"), ".properties")) if (!StudyFiles.read(p).getProperty("action").equals("Include")) return true;
        return false;
    }
    private List<PrReportRow> rows() throws IOException {
        List<PrReportRow> rows = new ArrayList<>();
        for (Path p : StudyFiles.files(root.resolve("rows"), ".properties")) rows.add(StudyFiles.record(p, PrReportRow.class));
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < repos.size(); i++) order.put(repos.get(i).fullName(), i);
        rows.sort(Comparator.comparingInt((PrReportRow r) -> order.get(r.repository()))
                .thenComparing((PrReportRow r) -> Instant.parse(r.closedAt()), Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(PrReportRow::pullRequestNumber).reversed()));
        return rows;
    }
    private static PrReportRow withHumanAuthor(PrReportRow r) {
        return new PrReportRow(r.repository(), r.pullRequestNumber(), r.url(), r.title(), r.createdAt(), r.closedAt(), r.mergedAt(),
                r.author(), r.state(), r.closed(), true, r.githubUserType(), r.aiDisclosure(), r.merged(), r.aiDisclosureEvidence(),
                r.aiDisclosureClassification(), r.aiDisclosureSource(), r.htmlScrapeSuccess(), r.htmlScrapeError());
    }
    private void publish(String state, String completed) throws IOException {
        List<PrReportRow> rows = rows();
        Map<String, Integer> botCounts = new LinkedHashMap<>();
        repos.forEach(r -> botCounts.put(r.fullName(), 0));
        StringBuilder audit = new StringBuilder("Repository,PR #,Category,Action,Rule ID,Reason,Date,Decision Owner,Affected Outputs,Evidence,Login,User Type,In Study Window\n");
        StringBuilder review = new StringBuilder("Login,User Type,Reviewed Label,Reviewer,Reviewed Date,Evidence,Repository,PR #\n");
        Map<String, Integer> counts = new TreeMap<>();
        Set<String> reviewAccounts = new HashSet<>();
        for (Path p : StudyFiles.files(root.resolve("decisions"), ".properties")) {
            Properties d = StudyFiles.read(p);
            List<String> fields = List.of("repository", "number", "category", "action", "rule", "reason", "date", "owner", "outputs", "evidence", "login", "user.type", "in.window");
            audit.append(String.join(",", fields.stream().map(k -> CsvTools.csv(d.getProperty(k))).toList())).append('\n');
            String countKey = d.getProperty("repository") + "," + d.getProperty("category") + "," + d.getProperty("action") + "," + d.getProperty("in.window");
            counts.merge(countKey, 1, Integer::sum);
            if (d.getProperty("action").equals("Exclude") && List.of("bot", "reviewed_bot").contains(d.getProperty("category")) && d.getProperty("in.window").equals("Yes")) botCounts.merge(d.getProperty("repository"), 1, Integer::sum);
            if (d.getProperty("action").equals("Review") && d.getProperty("category").equals("bot_review")
                    && reviewAccounts.add(d.getProperty("login").toLowerCase(Locale.ROOT))) review.append(String.join(",", CsvTools.csv(d.getProperty("login")), CsvTools.csv(d.getProperty("user.type")), "", "", "", "", CsvTools.csv(d.getProperty("repository")), CsvTools.csv(d.getProperty("number")))).append('\n');
        }
        Path dataset = root.resolve(".dataset.tmp"), summary = root.resolve(".summary.tmp"), report = root.resolve(".report.tmp");
        String start = identity.getProperty("collection.started.utc");
        CsvWriter.writePrDataset(dataset, rows, start, completed);
        CsvWriter.writeRepoComplianceSummary(summary, rows, botCounts, start, completed);
        CsvWriter.write(report, rows);
        String metadata = CsvTools.csv("# Study Status") + "," + CsvTools.csv(state) + "\n" + CsvTools.csv("# Dataset Version") + "," + CsvTools.csv(identity.getProperty("dataset.version")) + "\n";
        StudyFiles.text(root.resolve("pr_dataset.csv"), metadata + Files.readString(dataset));
        StudyFiles.text(root.resolve("repo_summary.csv"), metadata + Files.readString(summary));
        StudyFiles.text(root.resolve("report.csv"), metadata + Files.readString(report));
        Files.delete(dataset); Files.delete(summary); Files.delete(report);
        StudyFiles.text(root.resolve("exclusion_audit.csv"), audit.toString());
        StringBuilder countCsv = new StringBuilder("Repository,Category,Action,In Study Window,Count\n");
        counts.forEach((key, n) -> countCsv.append(key).append(',').append(n).append('\n'));
        StudyFiles.text(root.resolve("exclusion_counts.csv"), countCsv.toString());
        StudyFiles.text(root.resolve("bot_review_queue.csv"), review.toString());
        List<RepoUrl> selected = repos.size() >= 3 ? KappaWorkflow.selectRandomRepositories(repos, config.seed("seed.kappa")).selectedRepositories() : repos;
        Set<String> selectedIds = new HashSet<>(); selected.forEach(r -> selectedIds.add(r.fullName()));
        List<PrReportRow> kappaRows = new ArrayList<>(), validationRows = new ArrayList<>();
        for (RepoUrl repo : repos) {
            List<PrReportRow> candidates = rows.stream().filter(r -> r.repository().equals(repo.fullName())).toList();
            if (selectedIds.contains(repo.fullName())) kappaRows.addAll(candidates.stream().limit(Integer.parseInt(config.get("sample.kappa.per.repository"))).toList());
            List<PrReportRow> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, new Random(config.seed("seed.validation") ^ repo.fullName().hashCode()));
            validationRows.addAll(shuffled.stream().limit(Integer.parseInt(config.get("sample.validation.per.repository"))).toList());
        }
        sample("kappa_sample.csv", kappaRows, metadata);
        sample("validation_sample.csv", validationRows, metadata);
        if (Files.exists(root.resolve("policy-tracker.csv")) && !rows.isEmpty()) {
            Path visibility = root.resolve(".visibility.tmp"), correlation = root.resolve(".correlation.tmp");
            DisclosureVisibilityCorrelationWorkflow.analyze(root.resolve("pr_dataset.csv"), root.resolve("policy-tracker.csv"), visibility, correlation);
            StudyFiles.text(root.resolve("visibility_summary.csv"), metadata + Files.readString(visibility));
            StudyFiles.text(root.resolve("visibility_correlation.csv"), metadata + Files.readString(correlation));
            Files.delete(visibility); Files.delete(correlation);
        } else if (Files.exists(root.resolve("policy-tracker.csv"))) {
            StudyFiles.text(root.resolve("visibility_summary.csv"), metadata + "Repository,Automated Disclosure Rate\n");
            StudyFiles.text(root.resolve("visibility_correlation.csv"), metadata + "Status\nNo eligible observations\n");
        }
    }
    private void sample(String name, List<PrReportRow> rows, String metadata) throws IOException {
        Path sample = root.resolve("." + name + ".tmp"); Files.deleteIfExists(sample);
        KappaWorkflow.writeSampleWithSummary(sample, rows);
        StudyFiles.text(root.resolve(name), metadata + Files.readString(sample)); Files.delete(sample);
    }

    private void status(String state, String completed) throws IOException {
        Properties p = StudyFiles.values("status", state, "completed.utc", completed, "updated.utc", Instant.now().toString(), "dataset.version", identity.getProperty("dataset.version"));
        if (state.startsWith("COMPLETE")) for (String file : List.of("pr_dataset.csv", "repo_summary.csv", "report.csv", "exclusion_audit.csv", "exclusion_counts.csv", "bot_review_queue.csv", "kappa_sample.csv", "validation_sample.csv", "evidence-index.properties")) p.setProperty(file + ".sha256", StudyFiles.hash(root.resolve(file)));
        if (state.startsWith("COMPLETE") && Files.exists(root.resolve("policy-tracker.csv"))) {
            p.setProperty("visibility_summary.csv.sha256", StudyFiles.hash(root.resolve("visibility_summary.csv")));
            p.setProperty("visibility_correlation.csv.sha256", StudyFiles.hash(root.resolve("visibility_correlation.csv")));
        }
        StudyFiles.properties(root.resolve("status.properties"), p);
    }
    private void sealArchive() throws IOException {
        Properties index = new Properties();
        Path evidence = root.resolve("evidence");
        if (Files.exists(evidence)) try (var paths = Files.walk(evidence)) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) index.setProperty(evidence.relativize(p).toString(), StudyFiles.hash(p));
        }
        StudyFiles.properties(root.resolve("evidence-index.properties"), index);
    }
    private void verifyArchiveSeal() throws IOException {
        Properties index = StudyFiles.read(root.resolve("evidence-index.properties"));
        Path evidence = root.resolve("evidence").toAbsolutePath().normalize();
        for (String file : index.stringPropertyNames()) {
            Path p = evidence.resolve(file).normalize();
            if (!p.startsWith(evidence) || !StudyFiles.hash(p).equals(index.getProperty(file))) throw new IOException("Frozen archive metadata/body changed: " + file);
        }
        if (Files.exists(evidence)) try (var files = Files.walk(evidence)) {
            if (files.filter(Files::isRegularFile).count() != index.size()) throw new IOException("Frozen archive file set changed");
        }
    }

    private void verifyOutputs(Properties status) throws IOException {
        for (String key : status.stringPropertyNames()) if (key.endsWith(".sha256")) {
            String file = key.substring(0, key.length() - 7);
            if (!StudyFiles.hash(root.resolve(file)).equals(status.getProperty(key))) throw new IOException("Finished output changed: " + file);
        }
    }
}
