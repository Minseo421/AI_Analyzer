package com.example.aichecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length == 5 && args[0].equals("--study")) {
                requireComplete(StudyRun.start(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]))); return;
            }
            if (args.length == 2 && args[0].equals("--resume-study")) {
                requireComplete(StudyRun.resume(Path.of(args[1]))); return;
            }
            if (args.length == 3 && args[0].equals("--replay-study")) {
                requireComplete(StudyRun.replay(Path.of(args[1]), Path.of(args[2]))); return;
            }
            if (args.length == 6 && args[0].equals("--derive-study")) {
                requireComplete(StudyRun.derive(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]), Path.of(args[5]))); return;
            }
            if (args.length == 4 && args[0].equals("--reanalyze-kappa-offline")) {
                var result = KappaSampleReanalysisWorkflow.reanalyze(Path.of(args[2]), Path.of(args[3]), StudyRun.offlineAnalyzer(Path.of(args[1])));
                if (!result.failures().isEmpty()) throw new IOException("Offline reanalysis has unavailable source IDs; inspect output failure rows");
                return;
            }
            if (args.length == 4 && args[0].equals("--compare-studies")) {
                EvidenceArchive.compare(Path.of(args[1]), Path.of(args[2]), Path.of(args[3])); return;
            }
            if (args.length >= 3 && args[0].equals("--validate-exclusions")) {
                StudyDatasetExclusionValidator.validate(java.util.Arrays.stream(args).skip(2).map(Path::of).toList(), Path.of(args[1])); return;
            }
            if (args.length == 3 && args[0].equals("--validate-bot-rule")) {
                BotRuleValidation.write(Path.of(args[1]), Path.of(args[2])); return;
            }
            if (args.length == 3 && args[0].equals("--bundle-study")) {
                StudyBundle.write(Path.of(args[1]), Path.of(args[2])); return;
            }
            if (args.length == 3 && args[0].equals("--policy-compliance")) {
                PolicyComplianceWorkflow.analyze(Path.of(args[1]), Path.of(args[2]));
                return;
            }
            if (args.length == 1) {
                PullRequestUrl prUrl = PullRequestUrl.parse(args[0]);
                PrAnalyzer analyzer = new PrAnalyzer();
                PrReportRow row = analyzer.analyzeSingle(prUrl);
                System.out.println(row.toPrettyString());
                return;
            }
            if (args.length == 4 && args[0].equals("--latest")) {
                RepoUrl repoUrl = RepoUrl.parse(args[1]);
                int targetCount = Integer.parseInt(args[2]);
                Path output = Path.of(args[3]);
                managedCollection(List.of(repoUrl), output, null, false);
                return;
            }
            if ((args.length == 4 || args.length == 5) && args[0].equals("--latest-pr-dataset")) {
                RepoUrl repoUrl = RepoUrl.parse(args[1]);
                int targetCount = Integer.parseInt(args[2]);
                Path output = Path.of(args[3]);
                managedCollection(List.of(repoUrl), output, args.length == 5 ? Path.of(args[4]) : null, true);
                return;
            }
            if (args.length == 4 && args[0].equals("--repos")) {
                Path repoListFile = Path.of(args[1]);
                int targetCountPerRepo = Integer.parseInt(args[2]);
                Path output = Path.of(args[3]);
                List<RepoUrl> repoUrls = readRepoList(repoListFile);
                managedCollection(repoUrls, output, null, false);
                return;
            }
            if ((args.length >= 3 && args.length <= 5) && args[0].equals("--repos-pr-dataset")) {
                Path repoListFile = Path.of(args[1]);
                boolean legacyCountArgument = args.length >= 4 && isInteger(args[2]);
                int targetCountPerRepo = legacyCountArgument ? Integer.parseInt(args[2]) : 0;
                int outputIndex = legacyCountArgument ? 3 : 2;
                int summaryIndex = legacyCountArgument ? 4 : 3;
                if (args.length < outputIndex + 1 || args.length > summaryIndex + 1) {
                    throw new IllegalArgumentException("Invalid arguments for --repos-pr-dataset. Expected: " + expectedUsage(args[0]));
                }
                Path output = Path.of(args[outputIndex]);
                List<RepoUrl> repoUrls = readRepoList(repoListFile);
                managedCollection(repoUrls, output, args.length == summaryIndex + 1 ? Path.of(args[summaryIndex]) : null, true);
                return;
            }
            if (args.length == 8 && args[0].equals("--sample-all-repos-validation")) {
                Path repoListFile = Path.of(args[1]);
                Path existingValidation = Path.of(args[2]);
                int rowsPerRepository = Integer.parseInt(args[3]);
                Path output = Path.of(args[4]);
                LocalDate startDate = parseIsoDate(args[5], "START_DATE");
                LocalDate endDate = parseIsoDate(args[6], "END_DATE");
                long seed = Long.parseLong(args[7]);
                List<RepoUrl> repoUrls = readRepoList(repoListFile);
                AllRepoValidationWorkflow.Result result = AllRepoValidationWorkflow.sample(
                        repoUrls,
                        existingValidation,
                        rowsPerRepository,
                        output,
                        startDate,
                        endDate,
                        seed
                );
                System.out.println();
                System.out.println("All-repository validation sample saved: " + output.toAbsolutePath());
                System.out.println("Fixed closed_at window (UTC): " + result.startDate() + " through " + result.endDate() + " inclusive");
                System.out.println("Sampling seed: " + result.seed());
                System.out.println("Repositories requested: " + repoUrls.size());
                System.out.println("Repositories represented: " + result.rowsByRepository().size());
                for (Map.Entry<String, Integer> entry : result.rowsByRepository().entrySet()) {
                    System.out.println("- " + entry.getKey() + ": " + entry.getValue());
                }
                System.out.println("Total validation rows: " + result.totalRows());
                if (!result.skippedRepositories().isEmpty()) {
                    System.out.println("Skipped repositories: " + String.join(", ", result.skippedRepositories()));
                }
                return;
            }
            if (args.length == 4 && args[0].equals("--sample-for-kappa")) {
                Path repoListFile = Path.of(args[1]);
                int targetCountPerRepo = Integer.parseInt(args[2]);
                Path output = Path.of(args[3]);
                if (Files.exists(output)) {
                    throw new java.io.IOException("Kappa sample already exists: " + output + ". Choose a new output path to avoid overwriting work.");
                }
                List<RepoUrl> repoUrls = readRepoList(repoListFile);
                Path dataset = output.resolveSibling(output.getFileName() + ".population.csv");
                managedCollection(repoUrls, dataset, null, true);
                Path run = dataset.resolveSibling(dataset.getFileName() + ".study");
                StudyFiles.atomic(output, Files.readAllBytes(run.resolve("kappa_sample.csv")));
                System.out.println("Frozen kappa sample saved: " + output.toAbsolutePath());
                return;
            }
            if (args.length == 3 && args[0].equals("--code-kappa-sample")) {
                Path sampleInput = Path.of(args[1]);
                Path labelsOutput = Path.of(args[2]);
                KappaWorkflow.codeSample(sampleInput, labelsOutput);
                System.out.println("Coder labels saved: " + labelsOutput.toAbsolutePath());
                return;
            }
            if ((args.length == 6 || args.length == 7) && args[0].equals("--sample-for-extended-validation")) {
                Path prDataset = Path.of(args[1]);
                Path existingSample = Path.of(args[2]);
                int repositoryCount = Integer.parseInt(args[3]);
                int rowsPerRepository = Integer.parseInt(args[4]);
                Path output = Path.of(args[5]);
                long seed = args.length == 7 ? Long.parseLong(args[6]) : ExtendedValidationWorkflow.DEFAULT_SAMPLE_SEED;
                ExtendedValidationWorkflow.SampleResult result = ExtendedValidationWorkflow.sample(prDataset, existingSample, repositoryCount, rowsPerRepository, output, seed);
                System.out.println("Extended validation sample saved: " + output.toAbsolutePath());
                System.out.println("Extended validation seed: " + result.seed());
                System.out.println("Candidate repositories after exclusions: " + result.candidateRepositories());
                System.out.println("Selected repositories: " + result.selectedRepositories().size());
                for (String repository : result.selectedRepositories()) {
                    System.out.println("- " + repository + ": " + result.rowsByRepository().get(repository));
                }
                System.out.println("Existing validation rows: " + result.existingRows());
                System.out.println("New validation rows: " + result.rowsWritten());
                System.out.println("Combined rows if all labelled: " + result.combinedRows());
                System.out.println("Required validation sample size: " + result.requiredRows());
                System.out.println("Meets required size if all labelled: " + (result.combinedRows() >= result.requiredRows() ? "Yes" : "No"));
                return;
            }
            if ((args.length == 3 || args.length == 5) && args[0].equals("--code-validation-sample")) {
                Path sampleInput = Path.of(args[1]);
                Path labelsOutput = Path.of(args[2]);
                if (args.length == 5) {
                    int startRow = Integer.parseInt(args[3]);
                    int endRow = Integer.parseInt(args[4]);
                    KappaWorkflow.codeSample(sampleInput, labelsOutput, startRow, endRow);
                } else {
                    KappaWorkflow.codeSample(sampleInput, labelsOutput);
                }
                System.out.println("Validation coder labels saved: " + labelsOutput.toAbsolutePath());
                return;
            }
            if (args.length == 4 && args[0].equals("--calculate-kappa")) {
                Path coderA = Path.of(args[1]);
                Path coderB = Path.of(args[2]);
                Path output = Path.of(args[3]);
                KappaWorkflow.calculateKappa(coderA, coderB, output);
                System.out.println("Kappa results saved: " + output.toAbsolutePath());
                return;
            }
            if (args.length == 4 && args[0].equals("--create-consensus")) {
                Path coderA = Path.of(args[1]);
                Path coderB = Path.of(args[2]);
                Path output = Path.of(args[3]);
                ConsensusWorkflow.ConsensusResult result = ConsensusWorkflow.createConsensus(coderA, coderB, output);
                System.out.println("Consensus labels saved: " + output.toAbsolutePath());
                printConsensusSummary(result);
                return;
            }
            if (args.length == 4 && args[0].equals("--validate-detector")) {
                Path sample = Path.of(args[1]);
                Path consensus = Path.of(args[2]);
                Path output = Path.of(args[3]);
                ConsensusWorkflow.DetectorValidationResult result = ConsensusWorkflow.validateDetector(sample, consensus, output);
                System.out.println("Detector validation saved: " + output.toAbsolutePath());
                printDetectorValidationSummary(result);
                return;
            }
            if (args.length == 4 && args[0].equals("--validate-manual-labels")) {
                Path sample = Path.of(args[1]);
                Path manualLabels = Path.of(args[2]);
                Path output = Path.of(args[3]);
                ConsensusWorkflow.DetectorValidationResult result = ConsensusWorkflow.validateDetectorAgainstManualLabels(sample, manualLabels, output);
                System.out.println("Detector validation saved: " + output.toAbsolutePath());
                printDetectorValidationSummary(result);
                return;
            }
            if (args.length == 5 && args[0].equals("--combine-detector-validation")) {
                Path originalValidation = Path.of(args[1]);
                Path extendedValidation = Path.of(args[2]);
                Path combinedRows = Path.of(args[3]);
                Path combinedMetrics = Path.of(args[4]);
                ExtendedValidationWorkflow.CombineResult result = ExtendedValidationWorkflow.combine(originalValidation, extendedValidation, combinedRows, combinedMetrics);
                System.out.println("Combined detector validation rows saved: " + combinedRows.toAbsolutePath());
                System.out.println("Combined detector metrics saved: " + combinedMetrics.toAbsolutePath());
                System.out.println("Detector version: " + result.detectorVersion());
                System.out.println("Original usable rows: " + result.original().n());
                System.out.println("Extended usable rows: " + result.extended().n());
                System.out.println("Combined usable rows: " + result.combined().n());
                System.out.println("Combined confusion matrix TP/TN/FP/FN: "
                        + result.combined().tp() + "/"
                        + result.combined().tn() + "/"
                        + result.combined().fp() + "/"
                        + result.combined().fn());
                System.out.println("Duplicate rows reported: " + result.duplicates().size());
                return;
            }
            if (args.length >= 3 && args[0].equals("--export-false-negatives")) {
                List<Path> validations = List.of(args).subList(1, args.length - 1).stream().map(Path::of).toList();
                Path output = Path.of(args[args.length - 1]);
                ConsensusWorkflow.FalseNegativeExportResult result = ConsensusWorkflow.exportFalseNegatives(validations, output);
                System.out.println("False-negative PRs saved: " + output.toAbsolutePath());
                System.out.println("Detailed validation rows read: " + result.detailedRowsRead());
                System.out.println("False negatives written: " + result.falseNegativesWritten());
                return;
            }
            if (args.length >= 2 && args[0].equals("--validate-study-exclusions")) {
                List<Path> paths = List.of(args).subList(1, args.length).stream().map(Path::of).toList();
                StudyDatasetExclusionValidator.ValidationResult result = StudyDatasetExclusionValidator.validate(paths);
                System.out.println("No repository flagged Exclude/Review in study/exclusions-v1.csv occurs in the checked files.");
                System.out.println("Files checked: " + result.filesChecked());
                return;
            }
            if (args.length == 3 && args[0].equals("--reanalyze-kappa-sample")) {
                Path sample = Path.of(args[1]);
                Path output = Path.of(args[2]);
                KappaSampleReanalysisWorkflow.ReanalysisResult result = KappaSampleReanalysisWorkflow.reanalyze(sample, output);
                System.out.println("Reanalyzed kappa sample saved: " + output.toAbsolutePath());
                printReanalysisSummary(result);
                return;
            }
            if (args.length >= 4 && args[0].equals("--analyze-specific-prs")) {
                Path sample = Path.of(args[1]);
                Path output = Path.of(args[2]);
                List<String> sampleIds = List.of(args).subList(3, args.length);
                SpecificPrAnalysisWorkflow.SpecificAnalysisResult result = SpecificPrAnalysisWorkflow.analyzeSpecificPrs(sample, output, sampleIds);
                printSpecificAnalysisSummary(result, output);
                return;
            }
            if (args.length == 3 && args[0].equals("--retry-failed-reanalysis")) {
                Path sample = Path.of(args[1]);
                Path output = Path.of(args[2]);
                SpecificPrAnalysisWorkflow.SpecificAnalysisResult result = SpecificPrAnalysisWorkflow.retryFailedPrs(sample, output);
                printSpecificAnalysisSummary(result, output);
                return;
            }
            if ((args.length == 3 || args.length == 4) && args[0].equals("--repos-from-csv")) {
                Path csv = Path.of(args[1]);
                Path output = Path.of(args[2]);
                boolean replace = args.length == 4 && args[3].equals("--replace");
                if (args.length == 4 && !replace) {
                    throw new IllegalArgumentException("Invalid arguments for --repos-from-csv. Expected: " + expectedUsage(args[0]));
                }
                RepoListImporter.ImportResult result = RepoListImporter.importFromCsv(csv, output, replace);
                printRepoImportSummary(result);
                if (!result.written()) {
                    throw new IllegalArgumentException("Repository list was not written because invalid repository links were found.");
                }
                return;
            }
            if ((args.length == 5 || args.length == 7) && args[0].equals("--visibility-correlation")) {
                Path prDataset = Path.of(args[1]);
                Path policyTracker = Path.of(args[2]);
                Path summaryOutput = Path.of(args[3]);
                Path correlationOutput = Path.of(args[4]);
                DisclosureVisibilityCorrelationWorkflow.Result result;
                if (args.length == 7) {
                    Path cleanedDatasetOutput = Path.of(args[5]);
                    Path exclusionReportOutput = Path.of(args[6]);
                    result = DisclosureVisibilityCorrelationWorkflow.analyze(prDataset, policyTracker, summaryOutput, correlationOutput, cleanedDatasetOutput, exclusionReportOutput);
                    System.out.println("Cleaned PR dataset saved: " + cleanedDatasetOutput.toAbsolutePath());
                    System.out.println("Retry/exclusion report saved: " + exclusionReportOutput.toAbsolutePath());
                } else {
                    result = DisclosureVisibilityCorrelationWorkflow.analyze(prDataset, policyTracker, summaryOutput, correlationOutput);
                }
                System.out.println("Per-repository disclosure summary saved: " + summaryOutput.toAbsolutePath());
                System.out.println("Visibility/disclosure correlation saved: " + correlationOutput.toAbsolutePath());
                System.out.println("Policy tracker repositories: " + result.policyRepositories());
                System.out.println("Cleaned PR dataset rows: " + result.cleanedPrRows());
                System.out.println("Distinct repositories found: " + result.summaryRows().size());
                System.out.println("Correlation observations used: " + result.spearmanResult().n());
                System.out.println("Spearman rho: " + formatStat(result.spearmanResult().rho()));
                System.out.println("P-value: " + formatStat(result.spearmanResult().pValue()));
                return;
            }
            if (args.length > 0 && isKnownMode(args[0])) {
                throw new IllegalArgumentException("Invalid arguments for " + args[0] + ". Expected: " + expectedUsage(args[0]));
            }
            printUsage();
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<RepoUrl> readRepoList(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<RepoUrl> repos = new ArrayList<>();
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isBlank() || cleaned.startsWith("#")) {
                continue;
            }
            repos.add(RepoUrl.parse(cleaned));
        }
        if (repos.isEmpty()) {
            throw new IllegalArgumentException("No repositories found in " + path);
        }
        return repos;
    }

    private static boolean isKnownMode(String mode) {
        return List.of("--study", "--resume-study", "--replay-study", "--derive-study", "--reanalyze-kappa-offline", "--compare-studies", "--validate-bot-rule", "--validate-exclusions", "--bundle-study").contains(mode)
                || mode.equals("--policy-compliance")
                || mode.equals("--latest")
                || mode.equals("--latest-pr-dataset")
                || mode.equals("--repos")
                || mode.equals("--repos-pr-dataset")
                || mode.equals("--sample-for-kappa")
                || mode.equals("--sample-all-repos-validation")
                || mode.equals("--code-kappa-sample")
                || mode.equals("--sample-for-extended-validation")
                || mode.equals("--code-validation-sample")
                || mode.equals("--calculate-kappa")
                || mode.equals("--create-consensus")
                || mode.equals("--validate-detector")
                || mode.equals("--validate-manual-labels")
                || mode.equals("--combine-detector-validation")
                || mode.equals("--export-false-negatives")
                || mode.equals("--validate-study-exclusions")
                || mode.equals("--reanalyze-kappa-sample")
                || mode.equals("--analyze-specific-prs")
                || mode.equals("--retry-failed-reanalysis")
                || mode.equals("--repos-from-csv")
                || mode.equals("--visibility-correlation");
    }

    private static String expectedUsage(String mode) {
        return switch (mode) {
            case "--derive-study" -> "--derive-study SOURCE CONFIG REPOS EXCLUSIONS NEW_RUN";
            case "--reanalyze-kappa-offline" -> "--reanalyze-kappa-offline RUN SAMPLE OUTPUT";
            case "--study" -> "--study CONFIG REPOS EXCLUSIONS RUN_DIRECTORY";
            case "--resume-study" -> "--resume-study RUN_DIRECTORY";
            case "--replay-study" -> "--replay-study RUN_DIRECTORY NEW_OUTPUT_DIRECTORY";
            case "--compare-studies" -> "--compare-studies OLD_RUN NEW_RUN changes.csv";
            case "--validate-exclusions" -> "--validate-exclusions REGISTER FILE [FILE...]";
            case "--validate-bot-rule" -> "--validate-bot-rule reviewed_accounts.csv validation.csv";
            case "--bundle-study" -> "--bundle-study RUN_DIRECTORY archive.zip";
            case "--latest" -> "--latest https://github.com/OWNER/REPO COUNT report.csv";
            case "--policy-compliance" -> "--policy-compliance reviewed_pr_dataset.csv reviewed_compliance_summary.csv";
            case "--latest-pr-dataset" -> "--latest-pr-dataset https://github.com/OWNER/REPO COUNT pr_dataset_output.csv [repo_compliance_summary.csv]";
            case "--repos" -> "--repos repos.txt COUNT report.csv";
            case "--repos-pr-dataset" -> "--repos-pr-dataset repos.txt pr_dataset_output.csv [repo_compliance_summary.csv]";
            case "--sample-for-kappa" -> "--sample-for-kappa repos.txt COUNT_PER_REPO kappa_sample.csv";
            case "--sample-all-repos-validation" -> "--sample-all-repos-validation repos.txt existing_validation.csv ROWS_PER_REPOSITORY output.csv START_DATE END_DATE SEED";
            case "--code-kappa-sample" -> "--code-kappa-sample kappa_sample.csv coder_labels.csv";
            case "--sample-for-extended-validation" -> "--sample-for-extended-validation pr_dataset_output_policy_cleaned.csv kappa_sample.csv REPOSITORIES ROWS_PER_REPOSITORY extended_validation_sample.csv [SEED]";
            case "--code-validation-sample" -> "--code-validation-sample validation_sample.csv coder_labels.csv [START_ROW END_ROW]";
            case "--calculate-kappa" -> "--calculate-kappa coder_a_labels.csv coder_b_labels.csv kappa_results.csv";
            case "--create-consensus" -> "--create-consensus coder_a_labels.csv coder_b_labels.csv consensus_labels.csv";
            case "--validate-detector" -> "--validate-detector kappa_sample.csv consensus_labels.csv detector_validation.csv";
            case "--validate-manual-labels" -> "--validate-manual-labels validation_sample.csv manual_labels.csv detector_validation.csv";
            case "--combine-detector-validation" -> "--combine-detector-validation detector_validation_reanalyzed.csv extended_detector_validation.csv combined_detector_validation.csv combined_detector_metrics.csv";
            case "--export-false-negatives" -> "--export-false-negatives detector_validation.csv [more_validation.csv...] false_negative_prs.csv";
            case "--validate-study-exclusions" -> "--validate-study-exclusions repos.txt pr_dataset_output.csv repo_visibility_summary_updated.csv spearman_correlation_input_updated.csv";
            case "--reanalyze-kappa-sample" -> "--reanalyze-kappa-sample kappa_sample.csv kappa_sample_reanalyzed.csv";
            case "--analyze-specific-prs" -> "--analyze-specific-prs kappa_sample_reanalyzed.csv kappa_sample_completed.csv Repo#PR [Repo#PR...]";
            case "--retry-failed-reanalysis" -> "--retry-failed-reanalysis kappa_sample_reanalyzed.csv kappa_sample_retry.csv";
            case "--repos-from-csv" -> "--repos-from-csv policy-tracker.csv repos.txt [--replace]";
            case "--visibility-correlation" -> "--visibility-correlation repo_compliance_summary.csv policy_tracker.csv repo_visibility_summary.csv visibility_correlation.csv";
            default -> "";
        };
    }

    private static LocalDate parseIsoDate(String value, String argumentName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(argumentName + " must use YYYY-MM-DD format: " + value);
        }
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void requireComplete(String status) throws IOException {
        System.out.println("Study status: " + status);
        if (!status.startsWith("COMPLETE")) throw new IOException("Study is incomplete; inspect status.properties, repository states and bot_review_queue.csv before resuming");
    }

    private static void managedCollection(List<RepoUrl> repos, Path output, Path summary, boolean dataset) throws Exception {
        Path run = output.resolveSibling(output.getFileName() + ".study");
        if (Files.exists(output) && !Files.exists(run.resolve("identity.properties"))) {
            throw new IOException("Legacy output has no frozen study configuration; choose a new output path");
        }
        Path repoFile = Files.createTempFile("study-repositories-", ".txt");
        try {
            Files.writeString(repoFile, String.join("\n", repos.stream().map(r -> r.fullName().toLowerCase(java.util.Locale.ROOT)).toList()) + "\n");
            String status = StudyRun.start(Path.of("study/study-v1.properties"), repoFile, Path.of("study/exclusions-v1.csv"), run);
            StudyFiles.atomic(output, Files.readAllBytes(run.resolve(dataset ? "pr_dataset.csv" : "report.csv")));
            if (summary != null) StudyFiles.atomic(summary, Files.readAllBytes(run.resolve("repo_summary.csv")));
            requireComplete(status);
        } finally { Files.deleteIfExists(repoFile); }
    }

    private static void printConsensusSummary(ConsensusWorkflow.ConsensusResult result) {
        System.out.println("Matched rows: " + result.matchedRows());
        System.out.println("Full agreements: " + result.fullAgreements());
        System.out.println("Disagreements needing resolution: " + result.disagreements());
        printMissingIds("Only in coder A", result.onlyInCoderA());
        printMissingIds("Only in coder B", result.onlyInCoderB());
    }

    private static void printDetectorValidationSummary(ConsensusWorkflow.DetectorValidationResult result) {
        System.out.println("Total matched rows: " + result.totalMatchedRows());
        System.out.println("True positives: " + result.truePositives());
        System.out.println("True negatives: " + result.trueNegatives());
        System.out.println("False positives: " + result.falsePositives());
        System.out.println("False negatives: " + result.falseNegatives());
        printMissingIds("Only in kappa sample", result.onlyInSample());
        printMissingIds("Only in consensus", result.onlyInConsensus());
    }

    private static void printReanalysisSummary(KappaSampleReanalysisWorkflow.ReanalysisResult result) {
        System.out.println("Total input rows: " + result.totalInputRows());
        System.out.println("Successfully re-analysed: " + result.succeeded());
        System.out.println("Failed: " + result.failed());
        System.out.println("Rows written: " + result.rowsWritten());
        for (KappaSampleReanalysisWorkflow.Failure failure : result.failures()) {
            System.out.println("- " + failure.sampleId() + ": " + failure.reason());
        }
    }

    private static void printSpecificAnalysisSummary(SpecificPrAnalysisWorkflow.SpecificAnalysisResult result, Path output) {
        System.out.println("Targeted PRs requested: " + result.requested());
        System.out.println("Successfully analysed: " + result.succeeded());
        System.out.println("Failed: " + result.failed());
        System.out.println("Rows written: " + result.rowsWritten());
        System.out.println("Output: " + output.toAbsolutePath());
        for (SpecificPrAnalysisWorkflow.Failure failure : result.failures()) {
            System.out.println("- " + failure.sampleId() + ": " + failure.reason());
        }
    }

    private static void printRepoImportSummary(RepoListImporter.ImportResult result) {
        System.out.println("Repository rows inspected: " + result.repositoryRowsInspected());
        System.out.println("Non-empty repository links: " + result.nonEmptyRepositoryLinks());
        System.out.println("Valid repositories written: " + result.validRepositoriesWritten());
        System.out.println("Duplicate repositories removed: " + result.duplicateRepositoriesRemoved());
        System.out.println("Invalid repository links: " + result.invalidRepositoryLinks());
        System.out.println("Output: " + result.output().toAbsolutePath());
        System.out.println();
        System.out.println("Repositories added:");
        printListOrNone(result.added());
        System.out.println();
        System.out.println("Repositories removed:");
        printListOrNone(result.removed());
        System.out.println();
        System.out.println("Repositories unchanged: " + result.unchanged());
        for (RepoListImporter.InvalidRepositoryLink invalid : result.invalidLinks()) {
            System.out.println("Invalid repository link at CSV row " + invalid.csvRowNumber() + ": " + invalid.value());
        }
    }

    private static void printListOrNone(List<String> values) {
        if (values.isEmpty()) {
            System.out.println("- None");
            return;
        }
        for (String value : values) {
            System.out.println("- " + value);
        }
    }

    private static void printMissingIds(String label, List<String> ids) {
        System.out.println(label + ": " + ids.size());
        for (String id : ids) {
            System.out.println("- " + id);
        }
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

    private static String formatStat(double value) {
        if (Double.isNaN(value)) return "UNDEFINED";
        return String.format(java.util.Locale.ROOT, "%.10f", value);
    }

    private static void printUsage() {
        System.out.println("Reproducible study commands:");
        for (String mode : List.of("--study", "--resume-study", "--replay-study", "--derive-study", "--bundle-study", "--compare-studies", "--reanalyze-kappa-offline", "--validate-bot-rule")) {
            System.out.println("  " + expectedUsage(mode));
        }
        System.out.println("Usage:");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar https://github.com/OWNER/REPO/pull/NUMBER");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --latest https://github.com/OWNER/REPO 100 report.csv");
        System.out.println("      Writes every eligible closed human PR in the four-calendar-month closed_at window; the numeric argument is retained for compatibility and ignored.");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --latest-pr-dataset https://github.com/OWNER/REPO 100 pr_dataset_output.csv [repo_compliance_summary.csv]");
        System.out.println("      Uses the same closed_at four-calendar-month eligibility rule with no per-repository analysis limit.");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos repos.txt 100 report.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos-pr-dataset repos.txt pr_dataset_output.csv [repo_compliance_summary.csv]");
        System.out.println("      Legacy form with COUNT is still accepted, but the numeric argument is ignored.");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --sample-for-kappa repos.txt 50 kappa_sample.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --sample-all-repos-validation repos.txt combined_human_labels.csv 20 all_repo_validation_sample_seeded.csv 2026-04-01 2026-07-31 42");
        System.out.println("      Collects PR metadata in the fixed UTC closed_at window, shuffles candidates with the fixed seed, then checks eligibility in that order until the per-repository target is reached.");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --code-kappa-sample kappa_sample.csv anna_labels.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --sample-for-extended-validation pr_dataset_output_policy_cleaned.csv kappa_sample.csv 4 50 extended_validation_sample.csv [SEED]");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --code-validation-sample validation_sample.csv coder_labels.csv [START_ROW END_ROW]");
        System.out.println("      Resumes from an existing labels file by skipping already-coded Sample IDs. START_ROW and END_ROW are 1-based and inclusive.");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --calculate-kappa anna_labels.csv coworker_labels.csv kappa_results.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --create-consensus anna_labels.csv coworker_labels.csv consensus_labels.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --validate-detector kappa_sample.csv consensus_labels.csv detector_validation.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --validate-manual-labels validation_sample.csv manual_labels.csv detector_validation.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --combine-detector-validation detector_validation_reanalyzed.csv extended_detector_validation.csv combined_detector_validation.csv combined_detector_metrics.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --export-false-negatives detector_validation.csv [more_validation.csv...] false_negative_prs.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --validate-study-exclusions repos.txt pr_dataset_output.csv repo_visibility_summary_updated.csv spearman_correlation_input_updated.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --reanalyze-kappa-sample kappa_sample.csv kappa_sample_reanalyzed.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --analyze-specific-prs kappa_sample_reanalyzed.csv kappa_sample_completed.csv OWNER/REPO#NUMBER [OWNER/REPO#NUMBER...]");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --retry-failed-reanalysis kappa_sample_reanalyzed.csv kappa_sample_retry.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos-from-csv policy-tracker.csv repos.txt [--replace]");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --policy-compliance reviewed_pr_dataset.csv reviewed_compliance_summary.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --visibility-correlation repo_compliance_summary.csv policy_tracker.csv repo_visibility_summary.csv visibility_correlation.csv");
        System.out.println("      Also accepts pr_dataset_output.csv as the first input; optional cleaned/exclusion outputs apply only to PR-level input.");
        System.out.println();
        System.out.println("repos.txt can contain either GitHub URLs or OWNER/REPO names, one per line.");
    }
}
