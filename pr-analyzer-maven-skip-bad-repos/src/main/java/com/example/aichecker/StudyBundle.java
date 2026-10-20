package com.example.aichecker;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

/** Explicit release contents exclude historical scratch directories and local credentials. */
public final class StudyBundle {
    public static void write(Path run, Path output) throws Exception {
        if (Files.exists(output)) throw new IOException("Bundle already exists");
        StudyRun.verifyFinished(run);
        if (!StudyFiles.read(run.resolve("status.properties")).getProperty("status", "").startsWith("COMPLETE")) throw new IOException("Cannot release an incomplete study");
        new EvidenceArchive(run.resolve("evidence"), true).verify();
        Path code = Path.of(StudyBundle.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path jar = Files.createTempFile("study-analyzer-", ".jar");
        Path zip = Files.createTempFile("study-release-", ".zip");
        try {
            if (Files.isRegularFile(code)) Files.copy(code, jar, StandardCopyOption.REPLACE_EXISTING);
            else {
                Manifest manifest = new Manifest();
                manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
                manifest.getMainAttributes().putValue("Main-Class", "com.example.aichecker.Main");
                try (var out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
                    for (Path file : StudyFiles.files(code.resolve("com/example/aichecker"), ".class")) {
                        out.putNextEntry(new JarEntry("com/example/aichecker/" + file.getFileName()));
                        Files.copy(file, out); out.closeEntry();
                    }
                }
            }
            try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("analyzer.jar")); Files.copy(jar, out); out.closeEntry();
                out.putNextEntry(new ZipEntry("REPLAY.txt"));
                out.write("Requires Java 17. Offline: java -jar analyzer.jar --replay-study study replay\nNo Maven or GitHub access is required.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.closeEntry();
                for (String input : java.util.List.of("config.properties", "identity.properties", "study-input.properties", "repos.txt", "exclusions.csv", "supplementary.csv", "status.properties", "evidence-index.properties", "evidence", "events", "repositories", "rows", "decisions", "pr_dataset.csv", "repo_summary.csv", "report.csv", "exclusion_audit.csv", "exclusion_counts.csv", "bot_review_queue.csv", "kappa_sample.csv", "validation_sample.csv", "policy-tracker.csv", "visibility_summary.csv", "visibility_correlation.csv")) {
                    Path path = run.resolve(input);
                    if (!Files.exists(path)) continue;
                    try (var files = Files.walk(path)) {
                        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                            out.putNextEntry(new ZipEntry("study/" + run.relativize(file).toString().replace('\\', '/')));
                            Files.copy(file, out); out.closeEntry();
                        }
                    }
                }
            }
            StudyFiles.atomic(output, Files.readAllBytes(zip));
        } finally { Files.deleteIfExists(jar); Files.deleteIfExists(zip); }
    }
}
