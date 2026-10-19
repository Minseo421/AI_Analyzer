package com.example.aichecker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reviewed compliance is independent of the automated detector's presence flag. */
public final class PolicyComplianceWorkflow {
    private PolicyComplianceWorkflow() {}

    enum Outcome { COMPLIANT, NONCOMPLIANT, NOT_APPLICABLE, UNRESOLVED, EXCLUDED }

    static Outcome assess(Map<String, String> row) {
        if (!List.of("Merged", "Closed").contains(value(row, "Status"))
                || !"No".equals(value(row, "Bot"))) return Outcome.EXCLUDED;
        if (!"Reviewed".equals(value(row, "Review Status"))
                || !"Complete".equals(value(row, "Evidence Completeness"))
                || !"PR_BODY_ONLY_V1".equals(value(row, "Source Coverage Rule"))) return Outcome.UNRESOLVED;
        for (String column : List.of("Policy Source URL", "Policy Coder", "Review Evidence URL", "Reviewer")) {
            if (value(row, column).isBlank()) return Outcome.UNRESOLVED;
        }
        try {
            LocalDate created = Instant.parse(value(row, "Created Date")).atOffset(java.time.ZoneOffset.UTC).toLocalDate();
            LocalDate from = LocalDate.parse(value(row, "Policy Effective From"));
            LocalDate through = LocalDate.parse(value(row, "Policy Effective Through"));
            LocalDate coded = LocalDate.parse(value(row, "Policy Coded At"));
            LocalDate reviewed = LocalDate.parse(value(row, "Reviewed At"));
            if (created.isBefore(from) || created.isAfter(through) || through.isBefore(from)
                    || coded.isBefore(created) || reviewed.isBefore(created)) return Outcome.UNRESOLVED;
        } catch (java.time.DateTimeException e) {
            return Outcome.UNRESOLVED;
        }
        String requirement = value(row, "Policy Requirement");
        if ("None".equals(requirement)) return Outcome.NOT_APPLICABLE;
        if ("If AI used".equals(requirement)) {
            // Neither absent disclosure nor an automated negative establishes actual AI use.
            if ("No".equals(value(row, "Reviewed AI Use"))) return Outcome.NOT_APPLICABLE;
            if (!"Yes".equals(value(row, "Reviewed AI Use"))) return Outcome.UNRESOLVED;
        } else if (!"All contributors".equals(requirement)) return Outcome.UNRESOLVED;
        String disclosure = value(row, "Reviewed Disclosure Present");
        String assessment = value(row, "Reviewed Compliance");
        if ("Compliant".equals(assessment) && "Yes".equals(disclosure)) return Outcome.COMPLIANT;
        // A reviewed failure may also reflect an insufficient disclosure; it needs cited evidence.
        if ("Noncompliant".equals(assessment) && List.of("Yes", "No").contains(disclosure)) return Outcome.NONCOMPLIANT;
        return Outcome.UNRESOLVED;
    }

    public static void analyze(Path reviewedDataset, Path output) throws IOException {
        if (reviewedDataset.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || (Files.exists(output) && Files.isSameFile(reviewedDataset, output))) {
            throw new IllegalArgumentException("Compliance output must not overwrite the reviewed input dataset");
        }
        List<Map<String, String>> rows = CsvTools.readRows(reviewedDataset);
        if (rows.isEmpty()) throw new IllegalArgumentException("Reviewed dataset has no rows");
        Map<String, int[]> counts = new LinkedHashMap<>();
        var ids = new HashSet<String>();
        for (Map<String, String> row : rows) {
            String repo = RepoUrl.parse(value(row, "Repo")).fullName().toLowerCase(Locale.ROOT);
            int number = Integer.parseInt(value(row, "PR #"));
            if (number <= 0 || !ids.add(repo + "#" + number)) {
                throw new IllegalArgumentException("Invalid or duplicate PR ID: " + repo + "#" + number);
            }
            counts.computeIfAbsent(repo, ignored -> new int[Outcome.values().length])[assess(row).ordinal()]++;
        }
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("Repo,Compliant Count,Noncompliant Count,Reviewed Applicable Denominator,Not Applicable Count,Unresolved Count,Excluded Count,Reviewed Policy Compliance Rate,Analysis Status\n");
            for (var entry : counts.entrySet()) {
                int[] n = entry.getValue();
                int denominator = n[0] + n[1];
                writer.write(String.join(",", CsvTools.csv(entry.getKey()), "" + n[0], "" + n[1], "" + denominator,
                        "" + n[2], "" + n[3], "" + n[4],
                        denominator == 0 ? "" : String.format(Locale.ROOT, "%.4f", (double) n[0] / denominator),
                        CsvTools.csv(n[3] > 0 ? "Provisional: unresolved rows excluded" : denominator == 0 ? "No applicable reviewed rows" : "Reviewed")));
                writer.newLine();
            }
        }
    }

    private static String value(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").trim();
    }
}
