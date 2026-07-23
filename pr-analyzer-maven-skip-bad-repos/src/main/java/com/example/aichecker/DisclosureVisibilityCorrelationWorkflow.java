package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DisclosureVisibilityCorrelationWorkflow {
    private static final List<String> SUCCESS_STATUSES = List.of("Merged", "Closed");
    private static final double ALPHA = 0.05;

    public static Result analyze(Path prDatasetPath, Path policyTrackerPath, Path summaryOutputPath, Path correlationOutputPath) throws IOException {
        List<Map<String, String>> prRows = CsvTools.readRows(prDatasetPath);
        List<Map<String, String>> policyRows = CsvTools.readRows(policyTrackerPath);
        validatePrDataset(prRows, prDatasetPath);
        validatePolicyTracker(policyRows, policyTrackerPath);

        Map<String, SummaryRow> summaries = summarizePrRows(prRows);
        PolicyIndex policyIndex = indexPolicyRows(policyRows);
        List<SummaryRow> outputRows = new ArrayList<>();
        for (SummaryRow row : summaries.values()) {
            outputRows.add(row.withPolicyMatch(policyIndex));
        }
        outputRows.sort(Comparator.comparing(SummaryRow::repository));

        List<Observation> observations = outputRows.stream()
                .filter(row -> row.correlationIncluded())
                .map(row -> new Observation(row.repository(), row.visibilityScoreNumeric(), row.disclosureRate()))
                .toList();
        SpearmanResult spearman = SpearmanResult.calculate(observations);
        writeSummary(summaryOutputPath, outputRows);
        writeCorrelation(correlationOutputPath, spearman, outputRows);
        return new Result(outputRows, spearman);
    }

    private static void validatePrDataset(List<Map<String, String>> rows, Path path) {
        requireColumns(rows, path, List.of("Repo", "PR #", "Status", "AI Disclosure Present"));
    }

    private static void validatePolicyTracker(List<Map<String, String>> rows, Path path) {
        requireColumns(rows, path, List.of("Repo", "Repository Link", "Visibility Score"));
    }

    private static void requireColumns(List<Map<String, String>> rows, Path path, List<String> required) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV has no data rows: " + path);
        }
        Map<String, String> first = rows.get(0);
        for (String column : required) {
            if (!first.containsKey(column)) {
                throw new IllegalArgumentException("Missing required column in " + path + ": " + column);
            }
        }
    }

    private static Map<String, SummaryRow> summarizePrRows(List<Map<String, String>> rows) {
        Map<String, MutableSummary> byRepo = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String repo = canonicalRepo(row.get("Repo")).orElse("");
            if (repo.isBlank()) {
                repo = clean(row.get("Repo")).toLowerCase(Locale.ROOT);
            }
            MutableSummary summary = byRepo.computeIfAbsent(repo, MutableSummary::new);
            summary.totalRows++;
            if (!isSuccessfulEligiblePr(row)) {
                summary.failedOrExcluded++;
                continue;
            }
            summary.eligible++;
            summary.successfullyAnalysedEligible++;
            String disclosed = clean(row.get("AI Disclosure Present"));
            if (disclosed.equalsIgnoreCase("Yes")) {
                summary.withDisclosure++;
            } else if (disclosed.equalsIgnoreCase("No")) {
                summary.withoutDisclosure++;
            } else {
                summary.successfullyAnalysedEligible--;
                summary.eligible--;
                summary.failedOrExcluded++;
            }
        }
        Map<String, SummaryRow> result = new LinkedHashMap<>();
        for (MutableSummary summary : byRepo.values()) {
            result.put(summary.repository, summary.toRow());
        }
        return result;
    }

    private static boolean isSuccessfulEligiblePr(Map<String, String> row) {
        String status = clean(row.get("Status"));
        String disclosure = clean(row.get("AI Disclosure Present"));
        String bot = clean(row.get("Bot"));
        return SUCCESS_STATUSES.stream().anyMatch(value -> value.equalsIgnoreCase(status))
                && !clean(row.get("Closed Date")).isBlank()
                && !bot.equalsIgnoreCase("Yes")
                && (disclosure.equalsIgnoreCase("Yes") || disclosure.equalsIgnoreCase("No"));
    }

    private static PolicyIndex indexPolicyRows(List<Map<String, String>> rows) {
        Map<String, List<PolicyRow>> byRepo = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            Set<String> keys = new LinkedHashSet<>();
            canonicalRepo(row.get("Repository Link")).ifPresent(keys::add);
            canonicalRepo(row.get("Repo")).ifPresent(keys::add);
            for (String key : keys) {
                byRepo.computeIfAbsent(key, ignored -> new ArrayList<>()).add(PolicyRow.from(row, key));
            }
        }
        return new PolicyIndex(byRepo);
    }

    static Optional<String> canonicalRepo(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) return Optional.empty();
        try {
            return Optional.of(RepoListImporter.normalizeRepository(cleaned).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static Optional<Double> parseLeadingNumber(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) return Optional.empty();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))").matcher(cleaned);
        if (!matcher.find()) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(matcher.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void writeSummary(Path path, List<SummaryRow> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Repository,Total PR Rows,Eligible PRs,Successfully Analysed Eligible PRs,PRs With Disclosure,PRs Without Disclosure,Failed or Excluded PRs,Disclosure Rate,Disclosure Rate Percentage,Policy Tracker Repo,Repository Link,Visibility Score Original,Visibility Score Numeric,Match Status,Correlation Included,Exclusion Reason");
            writer.newLine();
            for (SummaryRow row : rows) {
                writer.write(String.join(",",
                        CsvTools.csv(row.repository()),
                        CsvTools.csv(Integer.toString(row.totalRows())),
                        CsvTools.csv(Integer.toString(row.eligiblePrs())),
                        CsvTools.csv(Integer.toString(row.successfullyAnalysedEligiblePrs())),
                        CsvTools.csv(Integer.toString(row.prsWithDisclosure())),
                        CsvTools.csv(Integer.toString(row.prsWithoutDisclosure())),
                        CsvTools.csv(Integer.toString(row.failedOrExcludedPrs())),
                        CsvTools.csv(row.disclosureRateText()),
                        CsvTools.csv(row.disclosureRatePercentage()),
                        CsvTools.csv(row.policyTrackerRepo()),
                        CsvTools.csv(row.repositoryLink()),
                        CsvTools.csv(row.visibilityScoreOriginal()),
                        CsvTools.csv(row.visibilityScoreNumericText()),
                        CsvTools.csv(row.matchStatus()),
                        CsvTools.csv(row.correlationIncluded() ? "Yes" : "No"),
                        CsvTools.csv(row.exclusionReason())
                ));
                writer.newLine();
            }
        }
    }

    private static void writeCorrelation(Path path, SpearmanResult result, List<SummaryRow> rows) throws IOException {
        long excluded = rows.stream().filter(row -> !row.correlationIncluded()).count();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Metric Pair,Spearman Rho,P-Value,Sample Size,Significance Level,Significance Result,Confidence Interval Lower,Confidence Interval Upper,Excluded Repository Count,Interpretation");
            writer.newLine();
            writer.write(String.join(",",
                    CsvTools.csv("Visibility Score vs Disclosure Rate"),
                    CsvTools.csv(formatNullable(result.rho())),
                    CsvTools.csv(formatNullable(result.pValue())),
                    CsvTools.csv(Integer.toString(result.n())),
                    CsvTools.csv(String.format(Locale.ROOT, "%.2f", ALPHA)),
                    CsvTools.csv(result.significant() ? "Significant" : "Not significant"),
                    CsvTools.csv(""),
                    CsvTools.csv(""),
                    CsvTools.csv(Long.toString(excluded)),
                    CsvTools.csv(result.interpretation())
            ));
            writer.newLine();
        }
    }

    private static String formatNullable(double value) {
        if (Double.isNaN(value)) return "";
        return String.format(Locale.ROOT, "%.10f", value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static class MutableSummary {
        private final String repository;
        private int totalRows;
        private int eligible;
        private int successfullyAnalysedEligible;
        private int withDisclosure;
        private int withoutDisclosure;
        private int failedOrExcluded;

        private MutableSummary(String repository) {
            this.repository = repository;
        }

        private SummaryRow toRow() {
            if (withDisclosure + withoutDisclosure != successfullyAnalysedEligible) {
                throw new IllegalStateException("Disclosure counts do not equal successfully analysed eligible PRs for " + repository);
            }
            return new SummaryRow(repository, totalRows, eligible, successfullyAnalysedEligible, withDisclosure, withoutDisclosure, failedOrExcluded, "", "", "", "", Double.NaN, "Unmatched", false, "No policy tracker match");
        }
    }

    private record PolicyIndex(Map<String, List<PolicyRow>> byRepo) {
        private List<PolicyRow> matches(String repository) {
            return byRepo.getOrDefault(repository, List.of());
        }
    }

    private record PolicyRow(String key, String repo, String repositoryLink, String visibilityOriginal, double visibilityNumeric) {
        private static PolicyRow from(Map<String, String> row, String key) {
            String original = clean(row.get("Visibility Score"));
            return new PolicyRow(key, clean(row.get("Repo")), clean(row.get("Repository Link")), original, parseLeadingNumber(original).orElse(Double.NaN));
        }
    }

    public record SummaryRow(
            String repository,
            int totalRows,
            int eligiblePrs,
            int successfullyAnalysedEligiblePrs,
            int prsWithDisclosure,
            int prsWithoutDisclosure,
            int failedOrExcludedPrs,
            String policyTrackerRepo,
            String repositoryLink,
            String visibilityScoreOriginal,
            String matchStatus,
            double visibilityScoreNumeric,
            String rawExclusionReason,
            boolean unusedIncludedFlag,
            String unusedReason
    ) {
        private SummaryRow withPolicyMatch(PolicyIndex policyIndex) {
            List<PolicyRow> matches = policyIndex.matches(repository);
            if (successfullyAnalysedEligiblePrs == 0) {
                return withPolicy("", "", "", "Not evaluated", Double.NaN, "No successfully analysed eligible PRs");
            }
            if (matches.isEmpty()) {
                return withPolicy("", "", "", "Unmatched", Double.NaN, "No policy tracker match");
            }
            if (matches.size() > 1) {
                return withPolicy("", "", "", "Ambiguous", Double.NaN, "Multiple policy tracker rows match repository");
            }
            PolicyRow policy = matches.get(0);
            if (Double.isNaN(policy.visibilityNumeric())) {
                return withPolicy(policy.repo(), policy.repositoryLink(), policy.visibilityOriginal(), "Matched", Double.NaN, "Missing or invalid visibility score");
            }
            return withPolicy(policy.repo(), policy.repositoryLink(), policy.visibilityOriginal(), "Matched", policy.visibilityNumeric(), "");
        }

        private SummaryRow withPolicy(String policyRepo, String link, String visibilityOriginal, String matchStatus, double visibilityNumeric, String reason) {
            return new SummaryRow(repository, totalRows, eligiblePrs, successfullyAnalysedEligiblePrs, prsWithDisclosure, prsWithoutDisclosure, failedOrExcludedPrs, policyRepo, link, visibilityOriginal, matchStatus, visibilityNumeric, reason, false, "");
        }

        double disclosureRate() {
            if (successfullyAnalysedEligiblePrs == 0) return Double.NaN;
            return prsWithDisclosure * 1.0 / successfullyAnalysedEligiblePrs;
        }

        String disclosureRateText() {
            double rate = disclosureRate();
            return Double.isNaN(rate) ? "" : Double.toString(rate);
        }

        String disclosureRatePercentage() {
            double rate = disclosureRate();
            return Double.isNaN(rate) ? "" : String.format(Locale.ROOT, "%.2f%%", rate * 100.0);
        }

        String visibilityScoreNumericText() {
            return Double.isNaN(visibilityScoreNumeric) ? "" : Double.toString(visibilityScoreNumeric);
        }

        boolean correlationIncluded() {
            return successfullyAnalysedEligiblePrs > 0 && matchStatus.equals("Matched") && !Double.isNaN(visibilityScoreNumeric);
        }

        String exclusionReason() {
            return correlationIncluded() ? "" : rawExclusionReason;
        }
    }

    public record Observation(String repository, double visibility, double disclosureRate) {
    }

    public record SpearmanResult(int n, double rho, double pValue) {
        static SpearmanResult calculate(List<Observation> observations) {
            int n = observations.size();
            if (n < 3) return new SpearmanResult(n, Double.NaN, Double.NaN);
            double[] x = observations.stream().mapToDouble(Observation::visibility).toArray();
            double[] y = observations.stream().mapToDouble(Observation::disclosureRate).toArray();
            double[] rx = ranks(x);
            double[] ry = ranks(y);
            double rho = pearson(rx, ry);
            if (Double.isNaN(rho)) return new SpearmanResult(n, Double.NaN, Double.NaN);
            double p = n <= 9 ? exactPermutationPValue(rx, ry, Math.abs(rho)) : approximatePValue(rho, n);
            return new SpearmanResult(n, rho, p);
        }

        boolean significant() {
            return !Double.isNaN(pValue) && pValue < ALPHA;
        }

        String interpretation() {
            if (Double.isNaN(rho)) return "Spearman correlation is undefined because the included data have too few observations or constant values.";
            String direction = rho > 0 ? "positive" : rho < 0 ? "negative" : "no monotonic";
            double magnitude = Math.abs(rho);
            String strength = magnitude < 0.20 ? "very weak" : magnitude < 0.40 ? "weak" : magnitude < 0.60 ? "moderate" : magnitude < 0.80 ? "strong" : "very strong";
            return "The repository-level association is " + direction + " and " + strength + "; this is correlation, not causation.";
        }
    }

    static double[] ranks(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i + 1;
            while (j < n && Double.compare(values[order[i]], values[order[j]]) == 0) j++;
            double avg = (i + 1 + j) / 2.0;
            for (int k = i; k < j; k++) ranks[order[k]] = avg;
            i = j;
        }
        return ranks;
    }

    private static double pearson(double[] x, double[] y) {
        double xMean = mean(x);
        double yMean = mean(y);
        double num = 0.0;
        double xDen = 0.0;
        double yDen = 0.0;
        for (int i = 0; i < x.length; i++) {
            double xd = x[i] - xMean;
            double yd = y[i] - yMean;
            num += xd * yd;
            xDen += xd * xd;
            yDen += yd * yd;
        }
        if (xDen == 0.0 || yDen == 0.0) return Double.NaN;
        return num / Math.sqrt(xDen * yDen);
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double exactPermutationPValue(double[] rankedX, double[] rankedY, double observedAbsRho) {
        int[] permutation = new int[rankedY.length];
        boolean[] used = new boolean[rankedY.length];
        long[] counts = new long[2];
        permute(rankedX, rankedY, observedAbsRho, permutation, used, 0, counts);
        return counts[1] * 1.0 / counts[0];
    }

    private static void permute(double[] rankedX, double[] rankedY, double observedAbsRho, int[] permutation, boolean[] used, int depth, long[] counts) {
        if (depth == permutation.length) {
            double[] permutedY = new double[rankedY.length];
            for (int i = 0; i < permutation.length; i++) permutedY[i] = rankedY[permutation[i]];
            double rho = Math.abs(pearson(rankedX, permutedY));
            counts[0]++;
            if (rho + 1e-12 >= observedAbsRho) counts[1]++;
            return;
        }
        for (int i = 0; i < rankedY.length; i++) {
            if (!used[i]) {
                used[i] = true;
                permutation[depth] = i;
                permute(rankedX, rankedY, observedAbsRho, permutation, used, depth + 1, counts);
                used[i] = false;
            }
        }
    }

    private static double approximatePValue(double rho, int n) {
        if (n <= 2 || Math.abs(rho) >= 1.0) return Math.abs(rho) == 1.0 ? 0.0 : Double.NaN;
        double z = 0.5 * Math.log((1.0 + rho) / (1.0 - rho)) * Math.sqrt(n - 3.0);
        return 2.0 * (1.0 - normalCdf(Math.abs(z)));
    }

    private static double normalCdf(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return sign * y;
    }

    public record Result(List<SummaryRow> summaryRows, SpearmanResult spearmanResult) {
    }
}
