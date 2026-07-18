package com.example.aichecker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvTools {
    public static List<Map<String, String>> readRows(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        List<List<String>> records = parseRecords(content);
        List<Map<String, String>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<String> header = records.get(0);
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.size() == 1 && record.get(0).isBlank()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int h = 0; h < header.size(); h++) {
                String value = h < record.size() ? record.get(h) : "";
                row.put(header.get(h), value);
            }
            rows.add(row);
        }
        return rows;
    }

    public static List<String> readHeader(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        List<List<String>> records = parseRecords(content);
        if (records.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(records.get(0));
    }

    public static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static List<List<String>> parseRecords(String content) throws IOException {
        List<List<String>> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            List<String> record = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            int next;
            while ((next = reader.read()) != -1) {
                char ch = (char) next;
                if (inQuotes) {
                    if (ch == '"') {
                        reader.mark(1);
                        int maybeQuote = reader.read();
                        if (maybeQuote == '"') {
                            field.append('"');
                        } else {
                            inQuotes = false;
                            if (maybeQuote != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append(ch);
                    }
                } else if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    record.add(field.toString());
                    field.setLength(0);
                } else if (ch == '\n') {
                    record.add(field.toString());
                    records.add(record);
                    record = new ArrayList<>();
                    field.setLength(0);
                } else if (ch != '\r') {
                    field.append(ch);
                }
            }
            record.add(field.toString());
            if (!(record.size() == 1 && record.get(0).isEmpty())) {
                records.add(record);
            }
        }
        return records;
    }
}
