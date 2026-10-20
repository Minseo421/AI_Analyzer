package com.example.aichecker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

public final class ExclusionRegister {
    public record Decision(String scope, String id, String action, String reason, String date, String owner, String outputs, String evidence) {}
    private final Map<String, Decision> decisions = new LinkedHashMap<>();
    ExclusionRegister(Path path) throws IOException {
        for (var row : CsvTools.readRows(path)) {
            for (String key : List.of("Version", "Scope", "ID", "Action", "Reason", "Date", "Decision Owner", "Affected Outputs", "Evidence")) {
                if (row.getOrDefault(key, "").isBlank()) throw new IllegalArgumentException("Missing exclusion register field: " + key);
            }
            if (!row.get("Version").equals("1")) throw new IllegalArgumentException("Unsupported exclusion register version");
            LocalDate.parse(row.get("Date"));
            String scope = row.get("Scope"), id = row.get("ID").toLowerCase(Locale.ROOT);
            if (!List.of("repository", "pr", "account", "category", "artifact").contains(scope)
                    || !List.of("Include", "Exclude", "Review").contains(row.get("Action"))) throw new IllegalArgumentException("Invalid register decision");
            Decision decision = new Decision(scope, id, row.get("Action"), row.get("Reason"), row.get("Date"), row.get("Decision Owner"), row.get("Affected Outputs"), row.get("Evidence"));
            if (decisions.putIfAbsent(scope + ":" + id, decision) != null) throw new IllegalArgumentException("Duplicate exclusion decision: " + id);
        }
    }
    Decision find(String scope, String id) { return decisions.get(scope + ":" + id.toLowerCase(Locale.ROOT)); }
    Decision category(String id) {
        Decision d = find("category", id);
        if (d == null || !(d.action().equals("Exclude") || (id.equals("bot_review") && d.action().equals("Review")))) throw new IllegalArgumentException("Missing versioned category rule: " + id);
        return d;
    }
    Collection<Decision> decisions() { return List.copyOf(decisions.values()); }
}
