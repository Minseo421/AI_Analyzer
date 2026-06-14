package com.example.aichecker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
                || mode.equals("--repos-pr-dataset");
    }

    private static String expectedUsage(String mode) {
        return switch (mode) {
            case "--latest" -> "--latest https://github.com/OWNER/REPO COUNT report.csv";
            case "--latest-pr-dataset" -> "--latest-pr-dataset https://github.com/OWNER/REPO COUNT pr_dataset_output.csv [repo_compliance_summary.csv]";
            case "--repos" -> "--repos repos.txt COUNT report.csv";
            case "--repos-pr-dataset" -> "--repos-pr-dataset repos.txt COUNT pr_dataset_output.csv [repo_compliance_summary.csv]";
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

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar https://github.com/OWNER/REPO/pull/NUMBER");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --latest https://github.com/OWNER/REPO 100 report.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --latest-pr-dataset https://github.com/OWNER/REPO 100 pr_dataset_output.csv [repo_compliance_summary.csv]");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos repos.txt 100 report.csv");
        System.out.println("  java -jar target/pr-analyzer-maven-1.0.0.jar --repos-pr-dataset repos.txt 100 pr_dataset_output.csv [repo_compliance_summary.csv]");
        System.out.println();
        System.out.println("repos.txt can contain either GitHub URLs or OWNER/REPO names, one per line.");
    }
}
