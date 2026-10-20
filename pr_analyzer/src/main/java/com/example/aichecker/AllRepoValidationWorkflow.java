package com.example.aichecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AllRepoValidationWorkflow {
    public static Result sample(
            List<RepoUrl> repositories,
            Path existingValidation,
            int rowsPerRepository,
            Path output,
            LocalDate startDate,
            LocalDate endDate,
            long seed
    ) throws Exception {
        if (rowsPerRepository <= 0) {
            throw new IllegalArgumentException("ROWS_PER_REPOSITORY must be positive.");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("START_DATE and END_DATE must be valid and START_DATE must not be after END_DATE.");
        }
        if (Files.exists(output)) {
            throw new IOException("Validation sample already exists: " + output + ". Choose a new output path to avoid overwriting work.");
        }

        Set<String> excludedIds = readSampleIds(existingValidation);
        PrAnalyzer analyzer = new PrAnalyzer();
        List<PrReportRow> allRows = new ArrayList<>();
        List<String> skippedRepositories = new ArrayList<>();

        for (RepoUrl repository : KappaWorkflow.uniqueRepositories(repositories)) {
            try {
                List<PrReportRow> rows = analyzer.analyzeSeededValidationSample(
                        repository,
                        rowsPerRepository,
                        excludedIds,
                        startDate,
                        endDate,
                        seed
                );
                allRows.addAll(rows);
                for (PrReportRow row : rows) {
                    excludedIds.add(row.repository() + "#" + row.pullRequestNumber());
                }
            } catch (Exception e) {
                skippedRepositories.add(repository.fullName());
                System.out.println();
                System.out.println("Skipped repository: " + repository.fullName());
                System.out.println("Reason: " + shortMessage(e));
                System.out.println("The validation sampler will continue with the next repository.");
            }
        }

        KappaWorkflow.SampleWriteResult written = KappaWorkflow.writeSampleWithSummary(output, allRows);
        return new Result(
                written.totalRows(),
                written.rowsByRepository(),
                skippedRepositories,
                excludedIds.size(),
                startDate,
                endDate,
                seed
        );
    }

    private static Set<String> readSampleIds(Path path) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        if (path == null || !Files.exists(path) || Files.size(path) == 0) {
            return ids;
        }
        for (Map<String, String> row : CsvTools.readRows(path)) {
            String id = row.getOrDefault("Sample ID", "").trim();
            if (id.isBlank()) {
                String repo = row.getOrDefault("Repo", "").trim();
                String pr = row.getOrDefault("PR #", "").trim();
                if (!repo.isBlank() && !pr.isBlank()) {
                    id = repo + "#" + pr;
                }
            }
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        String clean = message.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= 220 ? clean : clean.substring(0, 217) + "...";
    }

    public record Result(
            int totalRows,
            Map<String, Integer> rowsByRepository,
            List<String> skippedRepositories,
            int excludedIdsAfterSampling,
            LocalDate startDate,
            LocalDate endDate,
            long seed
    ) {
        public Result {
            rowsByRepository = Map.copyOf(rowsByRepository);
            skippedRepositories = List.copyOf(skippedRepositories);
        }
    }
}
