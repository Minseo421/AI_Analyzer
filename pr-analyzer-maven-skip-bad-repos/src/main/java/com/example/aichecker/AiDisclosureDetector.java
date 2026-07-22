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
    private static final String AI_TOOL = "(?:chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|(?:generative\\s+)?ai|artificial\\s+intelligence|an?\\s+llm|llm)";
    private static final String AI_TOOL_NAME = "(?:chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)";
    private static final Pattern TEMPLATE_AI_HEADING_PATTERN = Pattern.compile("(?i)^\\s*#{0,6}\\s*(?:ai\\s+(?:generation\\s+)?(?:usage\\s+)?disclosure|ai\\s+(?:use|usage)|generative\\s+ai\\s+(?:use|usage|disclosure))\\s*:??\\s*$");
    private static final Pattern AI_DISCLOSURE_HEADING_PATTERN = Pattern.compile("(?i)^\\s*#{1,6}\\s*(?:ai\\s+(?:generation\\s+)?(?:usage\\s+)?disclosure|ai\\s+(?:use|usage)|generative\\s+ai\\s+(?:use|usage|disclosure))\\s*:??\\s*$");
    private static final Pattern AI_BOLD_FIELD_PATTERN = Pattern.compile("(?i)^\\s*\\*\\*(?:ai\\s+(?:generation\\s+)?(?:usage\\s+)?disclosure|ai\\s+(?:use|usage)|generative\\s+ai\\s+(?:use|usage|disclosure))\\s*:??\\*\\*\\s*(.*)$");
    private static final Pattern ANY_MARKDOWN_HEADING_PATTERN = Pattern.compile("^\\s*#{1,6}\\s+\\S.*$");
    private static final Pattern TEMPLATE_AI_QUESTION_PATTERN = Pattern.compile("(?i)^\\s*#{0,6}\\s*(?:was|were|did)\\b[^\\r\\n?]{0,160}\\b(?:generative\\s+ai|ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)\\b[^\\r\\n?]{0,160}\\?\\s*$");
    private static final Pattern AFFIRMATIVE_CHECKBOX_PATTERN = Pattern.compile("(?is)^(?:yes\\b|.*\\b(?:ai\\s+tooling|generative\\s+ai|ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)\\b[^\\r\\n]{0,120}\\b(?:used|assisted|generated)\\b)");
    private static final Pattern NEGATIVE_CHECKBOX_PATTERN = Pattern.compile("(?is)^(?:no\\b|none\\b|n/a\\b|not\\s+applicable\\b|.*\\b(?:no|not|without)\\b[^\\r\\n]{0,80}\\b(?:ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)\\b[^\\r\\n]{0,120}\\b(?:used|assistance|tooling|generated)\\b)");
    private static final List<Pattern> NEGATIVE_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)\\b(?:was\\s+)?" + AI_TOOL + "\\b[^\\r\\n?]{0,160}\\?\\s*(?:no|none|n/a|not\\s+applicable)\\b"),
            Pattern.compile("(?is)\\bno\\s+(?:generative\\s+)?ai\\b[^\\r\\n.]{0,80}\\b(?:used|generated|assistance|tooling)?\\b"),
            Pattern.compile("(?is)\\bi\\s+did\\s+not\\s+use\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)\\bnot\\s+ai[-\\s]+generated\\b"),
            Pattern.compile("(?is)\\bno\\s+generative\\s+ai\\b")
    );
    private static final List<Pattern> POSITIVE_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)\\b(?:was\\s+)?" + AI_TOOL + "\\b[^\\r\\n?]{0,160}\\?\\s*(?:yes|y)\\b"),
            Pattern.compile("(?is)\\b(?:i\\s+)?used\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:this\\s+pr\\s+)?(?:was|is)\\s+written\\s+with\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b" + AI_TOOL_NAME + "\\s+helped\\s+(?:generate|write|draft|refactor|implement|create)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bgenerated\\s+(?:\\w+\\s+){0,4}with\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:ai|llm)[-\\s]+assisted\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bassisted-by:\\s*" + AI_TOOL + "\\b[^\\r\\n.]{0,160}")
    );
    private static final List<Pattern> AMBIGUOUS_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)\\bai\\s+usage\\s+disclosure\\b[^\\r\\n]{0,240}"),
            Pattern.compile("(?is)\\bai\\s+disclosure\\b[^\\r\\n]{0,240}"),
            Pattern.compile("(?is)\\b(?:generative\\s+ai|artificial\\s+intelligence)\\b[^\\r\\n]{0,240}"),
            Pattern.compile("(?is)\\bminor\\s+ai\\s+help\\b[^\\r\\n]{0,120}"),
            Pattern.compile("(?is)\\b(?:did\\s+you|was|were)\\b[^\\r\\n?]{0,120}\\b(?:ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)\\b[^\\r\\n?]{0,120}\\?")
    );
    private static final List<Pattern> CONTEXTUAL_AMBIGUOUS_PATTERNS = List.of(
            Pattern.compile("(?is)^\\s*(?:n/a|not\\s+applicable|codex|" + AI_TOOL_NAME + "|minor\\s+ai\\s+help)\\s*$"),
            Pattern.compile("(?is)\\b(?:maybe|minor|some|partial)\\b[^\\r\\n.]{0,80}\\b(?:ai|" + AI_TOOL_NAME + ")\\b[^\\r\\n.]{0,80}")
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

    public DetectionDiagnostics diagnosePrBody(String prBody) {
        PreparedText prepared = prepareText(prBody == null ? "" : prBody);
        DisclosureResult result = detectInText(prBody, "PR body");
        return new DetectionDiagnostics(
                prBody == null ? "" : prBody,
                prepared.visibleText(),
                prepared.checkedCheckboxes(),
                prepared.uncheckedCheckboxes(),
                visibleGeneratedByFields(prepared.textWithoutCheckboxes()),
                result
        );
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

        DisclosureResult contextual = detectContextualSections(prepared.visibleText(), source);
        if (contextual.disclosed()) {
            if ("possible_positive".equals(contextual.classification())) {
                DisclosureResult conflictingNegative = findDisclosure(prepared.textWithoutCheckboxes(), NEGATIVE_DISCLOSURE_PATTERNS, "possible_negative", source);
                if (conflictingNegative.disclosed()) {
                    return new DisclosureResult(true, cleanEvidence(contextual.evidence() + "; " + conflictingNegative.evidence()), "possible_ambiguous", source);
                }
            } else if ("possible_negative".equals(contextual.classification())) {
                DisclosureResult conflictingPositive = findDisclosure(prepared.textWithoutCheckboxes(), POSITIVE_DISCLOSURE_PATTERNS, "possible_positive", source);
                if (conflictingPositive.disclosed()) {
                    return new DisclosureResult(true, cleanEvidence(contextual.evidence() + "; " + conflictingPositive.evidence()), "possible_ambiguous", source);
                }
            }
            return contextual;
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

    private static DisclosureResult findDisclosure(String text, List<Pattern> patterns, String classification, String source) {
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
        String visibleText = removeHtmlComments(text);
        List<String> checkedCheckboxes = new ArrayList<>();
        List<String> uncheckedCheckboxes = new ArrayList<>();
        String textWithoutCheckboxes = removeTemplateResponseLines(visibleText, checkedCheckboxes, uncheckedCheckboxes);
        return new PreparedText(visibleText, textWithoutCheckboxes, checkedCheckboxes, uncheckedCheckboxes);
    }

    static String removeHtmlComments(String text) {
        if (isBlank(text)) return "";
        return HTML_COMMENT_PATTERN.matcher(text).replaceAll(" ");
    }

    private static String removeTemplateResponseLines(String text, List<String> checkedCheckboxes, List<String> uncheckedCheckboxes) {
        List<String> keptLines = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            if (line.stripLeading().startsWith(">")) {
                continue;
            }
            Matcher matcher = MARKDOWN_CHECKBOX_PATTERN.matcher(line);
            if (matcher.matches()) {
                String checkedMarker = matcher.group(1);
                if (!checkedMarker.isBlank()) {
                    checkedCheckboxes.add("[x] " + matcher.group(2).trim());
                } else {
                    uncheckedCheckboxes.add("[ ] " + matcher.group(2).trim());
                }
                continue;
            }
            if (EMPTY_GENERATED_BY_PATTERN.matcher(line).matches()
                    || TEMPLATE_AI_HEADING_PATTERN.matcher(line).matches()
                    || TEMPLATE_AI_QUESTION_PATTERN.matcher(line).matches()) {
                continue;
            }
            keptLines.add(line);
        }
        return String.join("\n", keptLines);
    }

    private static DisclosureResult detectContextualSections(String text, String source) {
        List<String> answers = extractAiDisclosureAnswers(text);
        List<DisclosureResult> positives = new ArrayList<>();
        List<DisclosureResult> negatives = new ArrayList<>();
        List<DisclosureResult> ambiguous = new ArrayList<>();
        for (String answer : answers) {
            String cleaned = answer.strip();
            if (cleaned.isBlank()) {
                continue;
            }
            DisclosureResult negative = findDisclosure(cleaned, NEGATIVE_DISCLOSURE_PATTERNS, "possible_negative", source);
            if (negative.disclosed()) {
                negatives.add(negative);
                continue;
            }
            DisclosureResult positive = findDisclosure(cleaned, POSITIVE_DISCLOSURE_PATTERNS, "possible_positive", source);
            if (positive.disclosed()) {
                positives.add(positive);
                continue;
            }
            DisclosureResult contextualPositive = findDisclosure(cleaned, List.of(
                    Pattern.compile("(?is)\\b(?:yes\\b[^\\r\\n.]{0,120})?" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,120}\\b(?:used|assisted|generated|wrote|write|drafted|created|refactored|implemented)\\b[^\\r\\n.]{0,120}"),
                    Pattern.compile("(?is)\\b(?:used|with|generated\\s+by|assisted\\s+by)\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}")
            ), "possible_positive", source);
            if (contextualPositive.disclosed()) {
                positives.add(contextualPositive);
                continue;
            }
            DisclosureResult contextualAmbiguous = findDisclosure(cleaned, CONTEXTUAL_AMBIGUOUS_PATTERNS, "possible_ambiguous", source);
            if (contextualAmbiguous.disclosed()) {
                ambiguous.add(contextualAmbiguous);
            }
        }
        if ((!positives.isEmpty() && !negatives.isEmpty())
                || (positives.size() + negatives.size() + ambiguous.size() > 1 && !ambiguous.isEmpty())) {
            return new DisclosureResult(true, cleanEvidence(String.join("; ", answers)), "possible_ambiguous", source);
        }
        if (!positives.isEmpty()) return positives.get(0);
        if (!negatives.isEmpty()) return negatives.get(0);
        if (!ambiguous.isEmpty()) return ambiguous.get(0);
        return new DisclosureResult(false, "No completed AI disclosure section found", "none", source);
    }

    private static List<String> extractAiDisclosureAnswers(String text) {
        List<String> answers = new ArrayList<>();
        List<String> lines = List.of(text.split("\\R", -1));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher bold = AI_BOLD_FIELD_PATTERN.matcher(line);
            if (bold.matches()) {
                answers.add(bold.group(1));
                continue;
            }
            if (!AI_DISCLOSURE_HEADING_PATTERN.matcher(line).matches()) {
                continue;
            }
            List<String> answerLines = new ArrayList<>();
            for (int j = i + 1; j < lines.size(); j++) {
                String next = lines.get(j);
                if (ANY_MARKDOWN_HEADING_PATTERN.matcher(next).matches()) {
                    break;
                }
                if (MARKDOWN_CHECKBOX_PATTERN.matcher(next).matches()
                        || TEMPLATE_AI_QUESTION_PATTERN.matcher(next).matches()
                        || EMPTY_GENERATED_BY_PATTERN.matcher(next).matches()) {
                    continue;
                }
                answerLines.add(next);
            }
            answers.add(String.join("\n", answerLines).strip());
        }
        return answers;
    }

    private static List<String> visibleGeneratedByFields(String text) {
        List<String> fields = new ArrayList<>();
        Matcher matcher = GENERATED_BY_PATTERN.matcher(text);
        while (matcher.find()) {
            fields.add(cleanEvidence(matcher.group()));
        }
        return fields;
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

    public record DetectionDiagnostics(
            String rawPrBody,
            String cleanedPrBody,
            List<String> checkedCheckboxes,
            List<String> uncheckedCheckboxes,
            List<String> visibleGeneratedByFields,
            DisclosureResult result
    ) {
        public DetectionDiagnostics {
            checkedCheckboxes = List.copyOf(checkedCheckboxes);
            uncheckedCheckboxes = List.copyOf(uncheckedCheckboxes);
            visibleGeneratedByFields = List.copyOf(visibleGeneratedByFields);
        }
    }

    private record PreparedText(String visibleText, String textWithoutCheckboxes, List<String> checkedCheckboxes, List<String> uncheckedCheckboxes) {
    }
}
