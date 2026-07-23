package com.example.aichecker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Main {
    public static void main(String[] args) {
        try {
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
                PrAnalyzer analyzer = new PrAnalyzer();
                List<PrReportRow> rows = analyzer.analyzeLatestClosedHumanPrs(repoUrl, targetCount);
                CsvWriter.write(output, rows);
                printSavedSummary(output, rows);
                return;
            }
            if ((args.length == 4 || args.length == 5) && args[0].equals("--latest-pr-dataset")) {
                RepoUrl repoUrl = RepoUrl.parse(args[1]);
                int targetCount = Integer.parseInt(args[2]);
                Path output = Path.of(args[3]);
                PrAnalyzer analyzer = new PrAnalyzer();
                List<PrReportRow> rows = analyzer.analyzeLatestClosedHumanPrs(repoUrl, targetCount);
                CsvWriter.writePrDataset(output, rows);
                if (args.length == 5) {
                    Path summaryOutput = Path.of(args[4]);
                    CsvWriter.writeRepoComplianceSummary(summaryOutput, rows, analyzer.botPrsExcludedByRepository());
                    System.out.println("Repository compliance summary saved: " + summaryOutput.toAbsolutePath());
                }
                printSavedSummary(output, rows);
                return;
            }
            if (args.length == 4 && args[0].equals("--repos")) {
                Path repoListFile = Path.of(args[1]);
                int targetCountPerRepo = Integer.parseInt(args[2]);
                Path output = Path.of(args[3]);
                List<RepoUrl> repoUrls = readRepoList(repoListFile);
                PrAnalyzer analyzer = new PrAnalyzer();
                List<PrReportRow> rows = analyzer.analyzeMultipleRepos(repoUrls, targetCountPerRepo);
                CsvWriter.write(output, rows);
                printSavedSummary(output, rows);
                return;
            }
            if ((args.length == 4 || args.length == 5) && args[0].equals("--repos-pr-dataset")) {
                Path repoListFile = Path.of(args[1]);
                int targetCountPerRepo = Integer.parseInt(args[2]);
                Path output = Path.of(args[3]);
                List<RepoUrl> repoUrls = readRepoList(repoListFile);
                PrAnalyzer analyzer = new PrAnalyzer();
                List<PrReportRow> rows = analyzer.analyzeMultipleRepos(repoUrls, targetCountPerRepo);
                CsvWriter.writePrDataset(output, rows);
                if (args.length == 5) {
                    Path summaryOutput = Path.of(args[4]);
                    CsvWriter.writeRepoComplianceSummary(summaryOutput, rows, analyzer.botPrsExcludedByRepository());
                    System.out.println("Repository compliance summary saved: " + summaryOutput.toAbsolutePath());
                }
                printSavedSummary(output, rows);
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
                long seed = kappaSampleSeed();
                KappaWorkflow.RepositorySelection selection = KappaWorkflow.selectRandomRepositories(repoUrls, seed);
                printKappaRepositorySelection(selection, targetCountPerRepo);
                PrAnalyzer analyzer = new PrAnalyzer();
                List<PrReportRow> rows = analyzeSelectedKappaRepositories(selection.selectedRepositories(), targetCountPerRepo, analyzer);
                KappaWorkflow.SampleWriteResult sampleResult = KappaWorkflow.writeSampleWithSummary(output, rows);
                printKappaSampleSummary(selection.selectedRepositories(), rows, sampleResult);
                System.out.println("Kappa sample saved: " + output.toAbsolutePath());
                System.out.println("Sample rows: " + sampleResult.totalRows());
                return;
            }
            if (args.length == 3 && args[0].equals("--code-kappa-sample")) {
                Path sampleInput = Path.of(args[1]);
                Path labelsOutput = Path.of(args[2]);
                KappaWorkflow.codeSample(sampleInput, labelsOutput);
                System.out.println("Coder labels saved: " + labelsOutput.toAbsolutePath());
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
        return mode.equals("--latest")
                || mode.equals("--latest-pr-dataset")
                || mode.equals("--repos")
                || mode.equals("--repos-pr-dataset")
                || mode.equals("--sample-for-kappa")
                || mode.equals("--code-kappa-sample")
                || mode.equals("--calculate-kappa")
                || mode.equals("--create-consensus")
                || mode.equals("--validate-detector")
                || mode.equals("--reanalyze-kappa-sample")
                || mode.equals("--analyze-specific-prs")
                || mode.equals("--retry-failed-reanalysis")
                || mode.equals("--repos-from-csv");
    }

    private static String expectedUsage(String mode) {
        return switch (mode) {
            case "--latest" -> "--latest https://github.com/OWNER/REPO COUNT report.csv";
            case "--latest-pr-dataset" -> "--latest-pr-dataset https://github.com/OWNER/REPO COUNT pr_dataset_output.csv [repo_compliance_summary.csv]";
            case "--repos" -> "--repos repos.txt COUNT report.csv";
            case "--repos-pr-dataset" -> "--repos-pr-dataset repos.txt COUNT pr_dataset_output.csv [repo_compliance_summary.csv]";
            case "--sample-for-kappa" -> "--sample-for-kappa repos.txt COUNT_PER_REPO kappa_sample.csv";
            case "--code-kappa-sample" -> "--code-kappa-sample kappa_sample.csv coder_labels.csv";
            case "--calculate-kappa" -> "--calculate-kappa coder_a_labels.csv coder_b_labels.csv kappa_results.csv";
            case "--create-consensus" -> "--create-consensus coder_a_labels.csv coder_b_labels.csv consensus_labels.csv";
            case "--validate-detector" -> "--validate-detector kappa_sample.csv consensus_labels.csv detector_validation.csv";
            case "--reanalyze-kappa-sample" -> "--reanalyze-kappa-sample kappa_sample.csv kappa_sample_reanalyzed.csv";
            case "--analyze-specific-prs" -> "--analyze-specific-prs kappa_sample_reanalyzed.csv kappa_sample_completed.csv Repo#PR [Repo#PR...]";
            case "--retry-failed-reanalysis" -> "--retry-failed-reanalysis kappa_sample_reanalyzed.csv kappa_sample_retry.csv";
            case "--repos-from-csv" -> "--repos-from-csv policy-tracker.csv repos.txt [--replace]";
            default -> "";
        };
    }

    private static void printSavedSummary(Path output, List<PrReportRow> rows) {
        long disclosed = rows.stream().filter(PrReportRow::aiDisclosure).count();
        double percentage = rows.isEmpty() ? 0.0 : (disclosed * 100.0 / rows.size());
        System.out.println("CSV saved: " + output.toAbsolutePath());
        System.out.println("Human closed PRs checked: " + rows.size());
        System.out.printf("AI disclosure percentage: %.2f%% (%d/%d)%n", percentage, disclosed, rows.size());
    }

    private static long kappaSampleSeed() {
        String value = System.getenv("KAPPA_SAMPLE_SEED");
        if (value == null || value.isBlank()) {
            return ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("KAPPA_SAMPLE_SEED must be a valid integer.");
        }
    }

    private static void printKappaRepositorySelection(KappaWorkflow.RepositorySelection selection, int targetCountPerRepo) {
        System.out.println("Kappa repository selection seed: " + selection.seed());
        System.out.println("Valid repositories available: " + selection.availableRepositories());
        System.out.println("Repositories selected: " + selection.selectedRepositories().size());
        System.out.println("PRs requested per repository: " + targetCountPerRepo);
        System.out.println();
        System.out.println("Randomly selected repositories:");
        int index = 1;
        for (RepoUrl repoUrl : selection.selectedRepositories()) {
            System.out.println(index + ". " + repoUrl.fullName());
            index++;
        }
        System.out.println();
    }

    private static List<PrReportRow> analyzeSelectedKappaRepositories(List<RepoUrl> repoUrls, int targetCountPerRepo, PrAnalyzer analyzer) {
        List<PrReportRow> allRows = new ArrayList<>();
        for (RepoUrl repoUrl : repoUrls) {
            System.out.println("Processing " + repoUrl.fullName() + "...");
            List<PrReportRow> rows;
            try {
                rows = analyzer.analyzeLatestClosedHumanPrs(repoUrl, targetCountPerRepo);
            } catch (Exception e) {
                rows = List.of();
                System.out.println("WARNING: Failed to process " + repoUrl.fullName() + ": " + shortError(e.getMessage()));
            }
            System.out.println("Eligible PRs found: " + rows.size());
            System.out.println("Rows written: " + rows.size());
            if (rows.size() < targetCountPerRepo) {
                System.out.println("WARNING: " + repoUrl.fullName() + " contained only " + rows.size() + " eligible closed human PRs.");
            }
            System.out.println();
            allRows.addAll(rows);
        }
        return allRows;
    }

    private static void printKappaSampleSummary(List<RepoUrl> repoUrls, List<PrReportRow> rows, KappaWorkflow.SampleWriteResult sampleResult) {
        Map<String, Integer> eligibleByRepository = new LinkedHashMap<>();
        for (RepoUrl repoUrl : repoUrls) {
            eligibleByRepository.put(repoUrl.fullName(), 0);
        }
        for (PrReportRow row : rows) {
            eligibleByRepository.merge(row.repository(), 1, Integer::sum);
        }

        System.out.println();
        System.out.println("Kappa sample repository summary");
        for (RepoUrl repoUrl : repoUrls) {
            String repository = repoUrl.fullName();
            int eligible = eligibleByRepository.getOrDefault(repository, 0);
            int written = sampleResult.rowsByRepository().getOrDefault(repository, 0);
            System.out.println("Repository processed: " + repository);
            System.out.println("Eligible closed human PRs found: " + eligible);
            System.out.println("PRs written for repository: " + written);
        }
        System.out.println("Total rows written: " + sampleResult.totalRows());
        System.out.println();
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

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar https://github.com/OWNER/REPO/pull/NUMBER");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --latest https://github.com/OWNER/REPO 100 report.csv");
        System.out.println("      Writes up to 100 eligible closed human PRs ordered locally by closed_at descending.");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --latest-pr-dataset https://github.com/OWNER/REPO 100 pr_dataset_output.csv [repo_compliance_summary.csv]");
        System.out.println("      Uses the same closed_at four-calendar-month eligibility rule before applying the limit.");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos repos.txt 100 report.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos-pr-dataset repos.txt 100 pr_dataset_output.csv [repo_compliance_summary.csv]");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --sample-for-kappa repos.txt 50 kappa_sample.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --code-kappa-sample kappa_sample.csv anna_labels.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --calculate-kappa anna_labels.csv coworker_labels.csv kappa_results.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --create-consensus anna_labels.csv coworker_labels.csv consensus_labels.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --validate-detector kappa_sample.csv consensus_labels.csv detector_validation.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --reanalyze-kappa-sample kappa_sample.csv kappa_sample_reanalyzed.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --analyze-specific-prs kappa_sample_reanalyzed.csv kappa_sample_completed.csv OWNER/REPO#NUMBER [OWNER/REPO#NUMBER...]");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --retry-failed-reanalysis kappa_sample_reanalyzed.csv kappa_sample_retry.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos-from-csv policy-tracker.csv repos.txt [--replace]");
        System.out.println();
        System.out.println("repos.txt can contain either GitHub URLs or OWNER/REPO names, one per line.");
    }
}
