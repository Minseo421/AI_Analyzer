package com.example.aichecker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvWriter {
    public static void write(Path path, List<PrReportRow> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(String.join(",",
                    csv("Repository"),
                    csv("Pull request number"),
                    csv("URL"),
                    csv("Author"),
                    csv("State"),
                    csv("Closed"),
                    csv("Human author"),
                    csv("GitHub user type"),
                    csv("AI Disclosure"),
                    csv("Merged"),
                    csv("AI Disclosure evidence"),
                    csv("HTML scrape success"),
                    csv("HTML scrape error")
            ));
            writer.newLine();
            for (PrReportRow row : rows) {
                writer.write(String.join(",",
                        csv(row.repository()),
                        csv(Integer.toString(row.pullRequestNumber())),
                        csv(row.url()),
                        csv(row.author()),
                        csv(row.state()),
                        csv(Boolean.toString(row.closed())),
                        csv(Boolean.toString(row.humanAuthor())),
                        csv(row.githubUserType()),
                        csv(Boolean.toString(row.aiDisclosure())),
                        csv(Boolean.toString(row.merged())),
                        csv(row.aiDisclosureEvidence()),
                        csv(Boolean.toString(row.htmlScrapeSuccess())),
                        csv(row.htmlScrapeError())
                ));
                writer.newLine();
            }
        }
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
