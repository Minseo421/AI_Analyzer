package com.example.aichecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiDisclosureDetector {
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("(?is)<!--.*?-->");
    private static final Pattern MARKDOWN_CHECKBOX_PATTERN = Pattern.compile("(?im)^\\s*[-*+]\\s*\\[\\s*([xX]?)\\s*]\\s*(.+)$");
    private static final Pattern GENERATED_BY_PATTERN = Pattern.compile("(?im)^\\s*Generated-by:\\s*(\\S[^\\r\\n]*)$");
    private static final Pattern EMPTY_GENERATED_BY_PATTERN = Pattern.compile("(?i)^\\s*Generated-by:\\s*$");
    private static final Pattern TEMPLATE_AI_HEADING_PATTERN = Pattern.compile("(?i)^\\s*#{0,6}\\s*(?:ai\\s+usage\\s+disclosure|ai\\s+disclosure)\\s*$");
    private static final Pattern AFFIRMATIVE_CHECKBOX_PATTERN = Pattern.compile("(?is)^(?:yes\\b|.*\\b(?:ai\\s+tooling|generative\\s+ai|ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|llm)\\b[^\\r\\n]{0,120}\\b(?:used|assisted|generated)\\b)");
    private static final Pattern NEGATIVE_CHECKBOX_PATTERN = Pattern.compile("(?is)^(?:no\\b|none\\b|n/a\\b|not\\s+applicable\\b|.*\\b(?:no|not|without)\\b[^\\r\\n]{0,80}\\b(?:ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|llm)\\b[^\\r\\n]{0,120}\\b(?:used|assistance|tooling|generated)\\b)");
    private static final List<Pattern> NEGATIVE_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)\\b(?:was\\s+)?(?:generative\\s+ai(?:\\s+tooling)?|ai|artificial\\s+intelligence|llm|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor)\\b[^\\r\\n?]{0,160}\\?\\s*(?:no|none|n/a|not\\s+applicable)\\b"),
            Pattern.compile("(?is)\\bno\\s+(?:generative\\s+)?ai\\b[^\\r\\n.]{0,80}\\b(?:used|generated|assistance|tooling)?\\b"),
            Pattern.compile("(?is)\\bi\\s+did\\s+not\\s+use\\s+(?:generative\\s+)?ai\\b[^\\r\\n.]{0,80}"),
            Pattern.compile("(?is)\\bnot\\s+ai[-\\s]+generated\\b"),
            Pattern.compile("(?is)\\bno\\s+generative\\s+ai\\b")
    );
    private static final List<Pattern> POSITIVE_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)\\b(?:was\\s+)?(?:generative\\s+ai(?:\\s+tooling)?|ai|artificial\\s+intelligence|llm|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor)\\b[^\\r\\n?]{0,160}\\?\\s*(?:yes|y)\\b"),
            Pattern.compile("(?is)\\b(?:i\\s+)?used\\s+(?:chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|(?:generative\\s+)?ai|an?\\s+llm)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:this\\s+pr\\s+)?(?:was|is)\\s+written\\s+with\\s+(?:chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|(?:generative\\s+)?ai|an?\\s+llm)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|(?:generative\\s+)?ai|an?\\s+llm)\\s+helped\\s+generate\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bgenerated\\s+with\\s+(?:chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|(?:generative\\s+)?ai|an?\\s+llm)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:ai|llm)[-\\s]+assisted\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bassisted-by:\\s*(?:chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|(?:generative\\s+)?ai|llm)\\b[^\\r\\n.]{0,160}")
    );
    private static final List<Pattern> AMBIGUOUS_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)\\bai\\s+usage\\s+disclosure\\b[^\\r\\n]{0,240}"),
            Pattern.compile("(?is)\\bai\\s+disclosure\\b[^\\r\\n]{0,240}"),
            Pattern.compile("(?is)\\b(?:generative\\s+ai|artificial\\s+intelligence)\\b[^\\r\\n]{0,240}"),
            Pattern.compile("(?is)\\b(?:did\\s+you|was|were)\\b[^\\r\\n?]{0,120}\\b(?:ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|llm)\\b[^\\r\\n?]{0,120}\\?")
    );
    private static final List<String> GITHUB_CHROME_PHRASES = List.of(
            "github copilot write better code with ai",
            "github copilot app",
            "actions automate any workflow",
            "codespaces instant dev environments",
            "issues plan and track work",
            "navigation menu",
            "skip to content",
            "mcp registry"
    );

    public DisclosureResult detect(String prBody, String htmlText) {
        DisclosureResult bodyResult = detectInText(prBody, "PR body");
        if (bodyResult.disclosed()) {
            return bodyResult;
        }

        String filteredHtml = removeGitHubChrome(htmlText);
        DisclosureResult htmlResult = detectInText(filteredHtml, "HTML fallback");
        if (htmlResult.disclosed()) {
            return htmlResult;
        }

        if (isBlank(prBody) && isBlank(htmlText)) {
            return new DisclosureResult(false, "No PR body or HTML text found");
        }
        return new DisclosureResult(false, "No contributor AI disclosure text detected");
    }

    private DisclosureResult detectInText(String text, String source) {
        if (isBlank(text)) {
            return new DisclosureResult(false, "No text found", "none", source);
        }
        PreparedText prepared = prepareText(text);
        if (isBlank(prepared.visibleText())) {
            return new DisclosureResult(false, "No visible contributor text found", "none", source);
        }

        DisclosureResult checkboxResult = detectCheckedCheckboxes(prepared.checkedCheckboxes(), source);
        if (checkboxResult.disclosed()) {
            return checkboxResult;
        }

        DisclosureResult generatedBy = detectGeneratedBy(prepared.textWithoutCheckboxes(), source);
        if (generatedBy.disclosed()) {
            return generatedBy;
        }

        DisclosureResult negative = findDisclosure(prepared.textWithoutCheckboxes(), NEGATIVE_DISCLOSURE_PATTERNS, "possible_negative", source);
        if (negative.disclosed()) {
            return negative;
        }
        DisclosureResult positive = findDisclosure(prepared.textWithoutCheckboxes(), POSITIVE_DISCLOSURE_PATTERNS, "possible_positive", source);
        if (positive.disclosed()) {
            return positive;
        }
        if (isLikelyFilenameOnlyMention(prepared.textWithoutCheckboxes())) {
            return new DisclosureResult(false, "AI term appears only as a filename or path", "none", source);
        }
        return findDisclosure(prepared.textWithoutCheckboxes(), AMBIGUOUS_DISCLOSURE_PATTERNS, "possible_ambiguous", source);
    }

    private DisclosureResult findDisclosure(String text, List<Pattern> patterns, String classification, String source) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return new DisclosureResult(true, cleanEvidence(matcher.group()), classification, source);
            }
        }
        return new DisclosureResult(false, "No contributor AI disclosure text detected", "none", source);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String cleanEvidence(String value) {
        String evidence = value.replaceAll("\\s+", " ").trim();
        if (evidence.length() > 300) evidence = evidence.substring(0, 300) + "...";
        return evidence;
    }

    private static PreparedText prepareText(String text) {
        String visibleText = HTML_COMMENT_PATTERN.matcher(text).replaceAll(" ");
        List<String> checkedCheckboxes = new ArrayList<>();
        String textWithoutCheckboxes = removeTemplateResponseLines(visibleText, checkedCheckboxes);
        return new PreparedText(visibleText, textWithoutCheckboxes, checkedCheckboxes);
    }

    private static String removeTemplateResponseLines(String text, List<String> checkedCheckboxes) {
        List<String> keptLines = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            Matcher matcher = MARKDOWN_CHECKBOX_PATTERN.matcher(line);
            if (matcher.matches()) {
                String checkedMarker = matcher.group(1);
                if (!checkedMarker.isBlank()) {
                    checkedCheckboxes.add("[x] " + matcher.group(2).trim());
                }
                continue;
            }
            if (EMPTY_GENERATED_BY_PATTERN.matcher(line).matches() || TEMPLATE_AI_HEADING_PATTERN.matcher(line).matches()) {
                continue;
            }
            keptLines.add(line);
        }
        return String.join("\n", keptLines);
    }

    private static DisclosureResult detectCheckedCheckboxes(List<String> checkedCheckboxes, String source) {
        List<String> positive = new ArrayList<>();
        List<String> negative = new ArrayList<>();
        for (String checkbox : checkedCheckboxes) {
            String label = checkbox.replaceFirst("(?is)^\\[x]\\s*", "").trim();
            if (isNegativeCheckbox(label)) {
                negative.add(checkbox);
            } else if (isAffirmativeCheckbox(label)) {
                positive.add(checkbox);
            }
        }
        if (!positive.isEmpty() && !negative.isEmpty()) {
            return new DisclosureResult(true, cleanEvidence(String.join("; ", checkedCheckboxes)), "possible_ambiguous", source);
        }
        if (!negative.isEmpty()) {
            return new DisclosureResult(true, cleanEvidence(negative.get(0)), "possible_negative", source);
        }
        if (!positive.isEmpty()) {
            return new DisclosureResult(true, cleanEvidence(positive.get(0)), "possible_positive", source);
        }
        return new DisclosureResult(false, "No checked AI disclosure checkbox found", "none", source);
    }

    private static DisclosureResult detectGeneratedBy(String text, String source) {
        Matcher matcher = GENERATED_BY_PATTERN.matcher(text);
        if (matcher.find()) {
            return new DisclosureResult(true, cleanEvidence(matcher.group()), "possible_positive", source);
        }
        return new DisclosureResult(false, "No completed Generated-by field found", "none", source);
    }

    private static boolean isAffirmativeCheckbox(String label) {
        return AFFIRMATIVE_CHECKBOX_PATTERN.matcher(label).find();
    }

    private static boolean isNegativeCheckbox(String label) {
        return NEGATIVE_CHECKBOX_PATTERN.matcher(label).find();
    }

    private static String removeGitHubChrome(String text) {
        if (isBlank(text)) return "";
        List<String> keptLines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String normalized = normalize(line);
            boolean chrome = false;
            for (String phrase : GITHUB_CHROME_PHRASES) {
                if (normalized.contains(phrase)) {
                    chrome = true;
                    break;
                }
            }
            if (!chrome) {
                keptLines.add(line);
            }
        }
        return String.join("\n", keptLines);
    }

    private static boolean isLikelyFilenameOnlyMention(String text) {
        String withoutFileRefs = text.replaceAll("(?is)\\b(?:CLAUDE\\.md|llms\\.txt|copilot-instructions\\.md|\\.cursor/rules)\\b", " ");
        String lower = withoutFileRefs.toLowerCase(Locale.ROOT);
        return !(lower.contains("ai")
                || lower.contains("artificial intelligence")
                || lower.contains("generative")
                || lower.contains("chatgpt")
                || lower.contains("copilot")
                || lower.contains("claude")
                || lower.contains("gemini")
                || lower.contains("cursor")
                || lower.contains("llm"));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private record PreparedText(String visibleText, String textWithoutCheckboxes, List<String> checkedCheckboxes) {
    }
}
