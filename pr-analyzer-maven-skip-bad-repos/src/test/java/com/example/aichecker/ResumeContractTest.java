package com.example.aichecker;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class ResumeContractTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String HEADER = "Sample ID,Repo,PR #,PR URL,Disclosure Present,Disclosure Classification,Notes\n";
    private static final String FIRST = "owner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1,Yes,Positive,keep this note\n";

    private Path sample() throws Exception {
        Path p = temp.newFile().toPath();
        Files.writeString(p, "Sample ID,Repo,PR #,PR URL\nowner/repo#1,owner/repo,1,https://github.com/owner/repo/pull/1\nowner/repo#2,owner/repo,2,https://github.com/owner/repo/pull/2\n");
        return p;
    }

    @Test public void resumesPreservingLabelsAndAppendsOnlyMissingIds() throws Exception {
        Path sample = sample(), labels = temp.newFile().toPath();
        Files.writeString(labels, HEADER + FIRST);
        var original = System.in;
        try {
            System.setIn(new ByteArrayInputStream("No\nNone\nnew note\n".getBytes(StandardCharsets.UTF_8)));
            KappaWorkflow.codeSample(sample, labels);
            String completed = Files.readString(labels);
            assertTrue(completed.startsWith(HEADER + FIRST));
            var rows = CsvTools.readRows(labels);
            assertEquals(2, rows.size());
            assertEquals("owner/repo#2", rows.get(1).get("Sample ID"));
            assertEquals("No", rows.get(1).get("Disclosure Present"));
            assertEquals("new note", rows.get(1).get("Notes"));
            System.setIn(new ByteArrayInputStream(new byte[0]));
            KappaWorkflow.codeSample(sample, labels);
            assertEquals(completed, Files.readString(labels));
        } finally { System.setIn(original); }
    }

    @Test public void rejectsForeignIdsWithoutChangingLabels() throws Exception {
        reject(HEADER + FIRST.replace("owner/repo#1", "foreign/repo#1"), "not present");
    }
    @Test public void rejectsDuplicateIdsWithoutChangingLabels() throws Exception {
        reject(HEADER + FIRST + FIRST, "Duplicate Sample ID");
    }
    @Test public void rejectsMissingIdsWithoutChangingLabels() throws Exception {
        reject(HEADER + FIRST.replace("owner/repo#1", ""), "Missing Sample ID");
    }
    private void reject(String contents, String message) throws Exception {
        Path labels = temp.newFile().toPath(), sample = sample();
        Files.writeString(labels, contents);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> KappaWorkflow.codeSample(sample, labels));
        assertTrue(error.getMessage().contains(message));
        assertEquals(contents, Files.readString(labels));
    }
}
