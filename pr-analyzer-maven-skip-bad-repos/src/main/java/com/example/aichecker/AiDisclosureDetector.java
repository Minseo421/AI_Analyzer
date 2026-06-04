package com.example.aichecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiDisclosureDetector {
    private static final List<Pattern> DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)ai\\s+usage\\s+disclosure.{0,500}"),
            Pattern.compile("(?is)ai\\s+disclosure.{0,500}"),
            Pattern.compile("(?is)artificial\\s+intelligence.{0,300}"),
            Pattern.compile("(?is)generative\\s+ai.{0,300}"),
            Pattern.compile("(?is)chatgpt.{0,300}"),
            Pattern.compile("(?is)github\\s+copilot.{0,300}"),
            Pattern.compile("(?is)copilot.{0,300}"),
            Pattern.compile("(?is)cursor.{0,300}"),
            Pattern.compile("(?is)claude.{0,300}"),
            Pattern.compile("(?is)gemini.{0,300}"),
            Pattern.compile("(?is)llm.{0,300}")
    );

    public DisclosureResult detect(String prBody, String htmlText) {
        List<String> sources = new ArrayList<>();
        if (prBody != null && !prBody.isBlank()) sources.add(prBody);
        if (htmlText != null && !htmlText.isBlank()) sources.add(htmlText);
        String combined = String.join("\n", sources);
        String lower = combined.toLowerCase(Locale.ROOT);
        if (lower.isBlank()) {
            return new DisclosureResult(false, "No PR body or HTML text found");
        }
        for (Pattern pattern : DISCLOSURE_PATTERNS) {
            Matcher matcher = pattern.matcher(combined);
            if (matcher.find()) {
                String evidence = matcher.group().replaceAll("\\s+", " ").trim();
                if (evidence.length() > 240) evidence = evidence.substring(0, 240) + "...";
                return new DisclosureResult(true, evidence);
            }
        }
        return new DisclosureResult(false, "No AI disclosure text detected");
    }
}
