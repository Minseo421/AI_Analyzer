package com.example.aichecker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

public final class BotRuleValidation {
    public static void write(Path reviewed, Path output) throws IOException {
        if (reviewed.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || (java.nio.file.Files.exists(output) && java.nio.file.Files.isSameFile(reviewed, output))) throw new IOException("Validation output must not overwrite the reviewed sample");
        int tp = 0, fp = 0, tn = 0, fn = 0, deferred = 0, pending = 0;
        Set<String> ids = new HashSet<>();
        for (var row : CsvTools.readRows(reviewed)) {
            if (row.getOrDefault("Reviewed Label", "").isBlank()) { pending++; continue; }
            for (String key : List.of("Login", "User Type", "Reviewer", "Reviewed Date", "Evidence")) {
                if (row.getOrDefault(key, "").isBlank()) throw new IllegalArgumentException("Missing reviewed bot field: " + key);
            }
            LocalDate.parse(row.get("Reviewed Date"));
            String label = row.get("Reviewed Label");
            if (!List.of("Human", "Bot").contains(label)) throw new IllegalArgumentException("Reviewed Label must be Human or Bot");
            if (!ids.add(row.get("Login").toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate reviewed account");
            BotDetector.Assessment prediction = BotDetector.assess(row.get("Login"), row.get("User Type"));
            if (prediction == BotDetector.Assessment.REVIEW) deferred++;
            else if (prediction == BotDetector.Assessment.BOT && label.equals("Bot")) tp++;
            else if (prediction == BotDetector.Assessment.BOT) fp++;
            else if (label.equals("Human")) tn++;
            else fn++;
        }
        StudyFiles.text(output, "Rule,Reviewed Accounts,True Bot,False Bot,True Human,Missed Bot,Deferred To Review,Unreviewed,Status,Input SHA256\n"
                + String.join(",", BotDetector.REVISION, "" + ids.size(), "" + tp, "" + fp, "" + tn, "" + fn, "" + deferred, "" + pending,
                ids.isEmpty() ? "NOT_VALIDATED" : pending > 0 ? "PARTIAL_REVIEW" : "REVIEWED_SAMPLE_ONLY", StudyFiles.hash(reviewed)) + "\n");
    }
}
