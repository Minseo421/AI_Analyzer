package com.example.aichecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiDisclosureDetector {
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("(?is)<!--.*?-->");
    private static final Pattern MARKDOWN_CHECKBOX_PATTERN = Pattern.compile("(?im)^\\s*[-*+]\\s*\\[\\s*([xX]?)\\s*]\\s*(.+)$");
    private static final Pattern MARKDOWN_CHECKBOX_START_PATTERN = Pattern.compile("^\\s*[-*+]\\s*\\[\\s*([xX]?)\\s*]\\s*(.*)$");
    private static final Pattern MARKDOWN_LIST_ITEM_PATTERN = Pattern.compile("^\\s*[-*+]\\s+.*$");
    private static final Pattern GENERATED_BY_PATTERN = Pattern.compile("(?im)^\\s*Generated-by:\\s*(\\S[^\\r\\n]*)$");
    private static final Pattern EMPTY_GENERATED_BY_PATTERN = Pattern.compile("(?i)^\\s*Generated-by:\\s*$");
    private static final Pattern REPO_NAME_PATTERN = Pattern.compile("(?i)^[^/\\s]+/[^/\\s]+$");
    private static final String MODEL_VERSION = "(?:gpt\\s*-?\\s*\\d+(?:\\.\\d+)?(?:\\s*-?\\s*(?:sol|luna|terra|astra))?)";
    private static final String CLAUDE_NAME = "claude(?:\\s*\\\\?:?\\s*(?:code|sonnet|opus|fable)(?:\\s*\\d+(?:\\.\\d+)?)?(?:\\s+high)?)?";
    private static final String AI_TOOL = "(?:chatgpt|" + MODEL_VERSION + "|gpt|openai\\s+codex(?:\\s+cloud)?|github\\s+copilot|copilot|" + CLAUDE_NAME + "|google\\s+jules|jules|gemini(?:\\s+\\d+(?:\\.\\d+)?\\s+pro)?|cursor|openclaw|codex(?:\\s+cloud)?|windsurf|devin|fable|(?:generative\\s+)?ai|artificial\\s+intelligence|an?\\s+llm|llm|ai\\s+(?:assistant|agent|tooling|tools?))";
    private static final String AI_TOOL_NAME = "(?:chatgpt|" + MODEL_VERSION + "|gpt|openai\\s+codex(?:\\s+cloud)?|github\\s+copilot|copilot|" + CLAUDE_NAME + "|google\\s+jules|jules|gemini(?:\\s+\\d+(?:\\.\\d+)?\\s+pro)?|cursor|openclaw|codex(?:\\s+cloud)?|windsurf|devin|fable|llm|ai\\s+(?:assistant|agent|tooling|tools?))";
    private static final Pattern AI_IDENTITY_PATTERN = Pattern.compile("(?is)\\b(?:" + AI_TOOL + "|ai\\s+(?:assistant|agent|service|model|tool))\\b");
    private static final String AI_DISCLOSURE_HEADING = "(?:ai\\s+(?:generation\\s+)?(?:usage\\s+)?disclosure|ai\\s+disclaimer|ai\\s+tool\\s+usage|ai\\s+contribution|ai\\s+assistance|ai\\s+(?:use|usage)|generative\\s+ai\\s+(?:use|usage|disclosure)|llm\\s+note)";
    private static final Pattern TEMPLATE_AI_HEADING_PATTERN = Pattern.compile("(?i)^\\s*#{0,6}\\s*" + AI_DISCLOSURE_HEADING + "\\s*:??\\s*$");
    private static final Pattern AI_DISCLOSURE_HEADING_PATTERN = Pattern.compile("(?i)^\\s*#{0,6}\\s*" + AI_DISCLOSURE_HEADING + "\\s*:??\\s*$");
    private static final Pattern AI_INLINE_FIELD_PATTERN = Pattern.compile("(?i)^\\s*#{0,6}\\s*" + AI_DISCLOSURE_HEADING + "\\s*:\\s*(\\S.*)$");
    private static final Pattern AI_BOLD_FIELD_PATTERN = Pattern.compile("(?i)^\\s*\\*\\*" + AI_DISCLOSURE_HEADING + "\\s*:??\\*\\*\\s*(.*)$");
    private static final Pattern ANY_MARKDOWN_HEADING_PATTERN = Pattern.compile("^\\s*#{1,6}\\s+\\S.*$");
    private static final Pattern TEMPLATE_AI_QUESTION_PATTERN = Pattern.compile("(?i)^\\s*#{0,6}\\s*(?:was|were|did)\\b[^\\r\\n?]{0,160}\\b(?:generative\\s+ai|ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)\\b[^\\r\\n?]{0,160}\\?\\s*$");
    private static final Pattern AFFIRMATIVE_CHECKBOX_PATTERN = Pattern.compile("(?is)^(?:yes\\b|.*\\b(?:ai\\s+tooling|generative\\s+ai|ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)\\b[^\\r\\n]{0,120}\\b(?:used|assisted|generated)\\b)");
    private static final Pattern NEGATIVE_CHECKBOX_PATTERN = Pattern.compile("(?is)^(?:(?:no|none|n/a|not\\s+applicable)\\s*[.!]??\\s*$|.*\\b(?:no|not|without)\\b[^\\r\\n]{0,80}\\b(?:ai|chatgpt|github\\s+copilot|copilot|claude|gemini|cursor|codex|windsurf|llm)\\b[^\\r\\n]{0,120}\\b(?:used|assistance|tooling|generated)\\b)");
    private static final List<Pattern> NEGATIVE_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)\\b(?:was\\s+)?" + AI_TOOL + "\\b[^\\r\\n?]{0,160}\\?\\s*(?:no|none|n/a|not\\s+applicable)\\b"),
            Pattern.compile("(?is)\\bno\\s+(?:generative\\s+)?ai\\b[^\\r\\n.]{0,80}\\b(?:used|generated|assistance|tooling)?\\b"),
            Pattern.compile("(?is)\\bi\\s+did\\s+not\\s+use\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)\\bnot\\s+ai[-\\s]+generated\\b"),
            Pattern.compile("(?is):robot:\\s*(?:this\\s+)?(?:pr|pull\\s+request)\\s+was\\s+written\\s+by\\s+hand\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)\\bno\\s+generative\\s+ai\\b"),
            Pattern.compile("(?is)\\bai\\b[^\\r\\n.]{0,60}\\bwas\\s+not\\s+used\\b"),
            Pattern.compile("(?is)\\b(?:this\\s+)?(?:contribution|pr|pull\\s+request)\\b[^\\r\\n.]{0,80}\\b(?:completed|created|authored|written)\\s+without\\s+(?:ai|llm|ai\\s*/\\s*llm)\\b")
    );
    private static final List<Pattern> CONTEXTUAL_NEGATIVE_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)^\\s*(?:none|no)\\s*[.!]??\\s*$"),
            Pattern.compile("(?is)\\b(?:all\\s+)?code\\s+(?:was\\s+)?written\\s+manually\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)\\bwritten\\s+by\\s+myself\\b[^\\r\\n.]{0,120}(?:\\b(?:without|no)\\b[^\\r\\n.]{0,60}\\b(?:ai|llm|assistance|tools?)\\b)?"),
            Pattern.compile("(?is)\\b(?:all\\s+)?(?:code|changes?)\\s+(?:were|was)?\\s*(?:written|authored|implemented)\\s+by\\s+(?:me|myself|hand)\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)\\b(?:i\\s+)?wrote\\s+(?:this|the\\s+(?:code|tests?|docs?|documentation|pr|pull\\s+request|pull\\s+request\\s+description))\\s+myself\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)\\b(?:this\\s+)?(?:pr|pull\\s+request|code|tests?|docs?|documentation|description)\\s+was\\s+written\\s+by\\s+hand\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)\\bhuman[-\\s]+written\\b[^\\r\\n.]{0,120}")
    );
    private static final List<Pattern> POSITIVE_DISCLOSURE_PATTERNS = List.of(
            Pattern.compile("(?is)^\\s*ai\\s*:\\s*[1-5]\\s*$"),
            Pattern.compile("(?is)\\b(?:was\\s+)?" + AI_TOOL + "\\b[^\\r\\n?]{0,160}\\?\\s*(?:yes|y)\\b"),
            Pattern.compile("(?is)\\b(?:i\\s+)?used\\s+(?:an?\\s+)?" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b" + AI_TOOL + "\\b[^\\r\\n.]{0,120}\\b(?:was|were)\\s+used\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bai\\s+tooling\\s+(?:was|were)\\s+used\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\ball\\s+ai\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bai\\s+helped\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bai\\b[^\\r\\n.]{0,80}\\bwas\\s+used\\s+to\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bclaude\\s+code\\s+with\\s+(?:opus|sonnet|fable)\\s*\\d*(?:\\.\\d+)?\\s+was\\s+used\\s+to\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:this\\s+pr\\s+)?(?:was|is)\\s+written\\s+with\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:this\\s+(?:change|pr|pull\\s+request)\\s+)?(?:was|is)\\s+(?:written|authored|implemented|created)\\s+with\\s+assistance\\s+(?:from|by)\\s+" + AI_TOOL + "\\b[^\\r\\n]{0,160}"),
            Pattern.compile("(?is)\\b(?:written|authored|prepared|completed|implemented|created)\\s+with\\s+assistance\\s+(?:from|by)\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:written|authored|prepared|completed|implemented|created)\\s+with\\s+(?:the\\s+)?(?:help|assistance)\\s+of\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:code|tests?|docs?|documentation|docstrings?|pr\\s+message|pull\\s+request\\s+description)\\b[^\\r\\n.]{0,80}\\b(?:was|were)\\s+(?:written|authored|prepared|completed|implemented|created)\\s+with\\s+(?:the\\s+)?(?:help|assistance)\\s+of\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:written|authored|prepared|completed|implemented|created)\\s+with\\s+(?:ai|llm)\\s+assistance\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bdeveloped\\s+with\\s+(?:ai|llm)\\s+assistance\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:fix(?:es)?|code|tests?|docs?|docstrings?|pr\\s+message|pull\\s+request\\s+description)\\b[^\\r\\n.]{0,120}\\b(?:assisted\\s+by|written\\s+using|created\\s+using)\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:code|tests?|docs?|documentation|docstrings?|title|pr\\s+message|pull\\s+request\\s+description)\\b[^\\r\\n.]{0,120}\\b(?:written|generated|created|drafted)?\\s*using\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:i\\s+)?worked\\s+with\\s+" + AI_TOOL + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:i\\s+)?did\\s+use\\s+(?:an?\\s+)?(?:" + AI_TOOL + "|agent)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:i\\s+)?asked\\s+" + AI_TOOL_NAME + "\\s+to\\s+(?:look|reproduce|investigate|debug|review|plan|implement|write|generate|find)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:i\\s+)?asked\\s+(?:for\\s+)?(?:multiple\\s+)?reviews\\s+from\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:i\\s+)?had\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,80}\\b(?:generate|write|draft|create|review|refactor|implement)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b" + AI_TOOL_NAME + "\\b[^\\r\\n]{0,120}\\bassisted\\s+with\\s+(?:code\\s+analysis|implementation|test\\s+planning|validation|drafting|debugging|review|refactoring|documentation|tests?)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,120}\\b(?:helped|assisted)\\s+(?:me\\s+)?(?:make|making|write|draft|debug|investigate|reproduce|plan|implement|generate|review|checking|check|refactor|find)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:bug|issue|combination)\\s+found\\s+by\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:approach|solution|fix|implementation|idea)\\b[^\\r\\n.]{0,80}\\bsuggested\\s+by\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bimplemented\\s+changes\\b[^\\r\\n.]{0,160}\\bbased\\s+on\\s+what\\s+was\\s+suggested\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\breviewe?d\\s+by\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)^\\s*(?:note\\s*:\\s*)?" + AI_TOOL_NAME + "\\b[^\\r\\n]{0,120}\\b(?:did|running|model|job|mostly)\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)^\\s*" + AI_TOOL_NAME + "\\s*(?:\\\\?[:(]|-)\\s*[^\\r\\n]{0,120}\\b(?:" + MODEL_VERSION + "|opus|sonnet|fable)\\b[^\\r\\n.]{0,120}"),
            Pattern.compile("(?is)^\\s*" + AI_TOOL_NAME + "\\s*\\\\?:\\s*to\\s+(?:find|debug|investigate|reproduce|plan|implement|write|generate|review|refactor)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)^\\s*by\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:made|created)\\s+with\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:created|made|written|authored)\\s+by\\s+(?:an?\\s+)?" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\bprimarily\\s+by\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:with|using|use\\s+of)\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,120}\\b(?:plan|implement|test|review|refactor|debug|grammar|docstrings?|title)\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b(?:refactors?|reviews?|debugging|planning|implementation|tests?|docstrings?|documentation|grammar|title)\\b[^\\r\\n.]{0,120}\\b(?:with|using|use\\s+of)\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
            Pattern.compile("(?is)\\b" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,80}\\brunning\\s+(?:the\\s+)?.{0,60}\\bmodel\\b[^\\r\\n.]{0,120}"),
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
    private static final List<Pattern> NEGATED_NEGATIVE_CONTEXT_PATTERNS = List.of(
            Pattern.compile("(?is)\\bnot\\s+true\\s+that\\s*$"),
            Pattern.compile("(?is)\\bcannot\\s+confirm\\s+that\\s*$"),
            Pattern.compile("(?is)\\bcan\\s+not\\s+confirm\\s+that\\s*$"),
            Pattern.compile("(?is)\\bnot\\s+sure\\s+that\\s*$"),
            Pattern.compile("(?is)\\b(?:please\\s+)?(?:state|indicate|confirm|select|answer)\\s+whether\\s*$"),
            Pattern.compile("(?is)\\bwhether\\s*$")
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
    private static final Map<String, List<RepositoryCheckboxRule>> REPOSITORY_CHECKBOX_RULES = Map.ofEntries(
            Map.entry("apache/airflow", List.of(
                    checkboxRule("possible_positive", "ai tool used")
            )),
            Map.entry("apache/couchdb", List.of(
                    checkboxRule("possible_negative", "own work did not use ai")
            )),
            Map.entry("django/django", List.of(
                    checkboxRule("possible_negative", "no ai was used"),
                    checkboxRule("possible_negative", "did not use ai"),
                    checkboxRule("possible_positive", "ai was used"),
                    checkboxRule("possible_positive", "used ai")
            )),
            Map.entry("osgeo/gdal", List.of(
                    checkboxRule("possible_positive", "ai tools were used"),
                    checkboxRule("possible_positive", "ai was used"),
                    checkboxRule("possible_positive", "used ai")
            )),
            Map.entry("homebrew/brew", List.of(
                    checkboxRule("possible_neutral", "did not use ai llm create pr or disclosed tool model"),
                    checkboxRule("possible_positive", "ai was used generate assist generating pr")
            )),
            Map.entry("joomla/joomla-cms", List.of(
                    checkboxRule("possible_neutral", "read generative ai policy contribution either not created help ai compatible policy")
            )),
            Map.entry("cybertec-postgresql/pgwatch", List.of(
                    checkboxRule("possible_negative", "no ai automation used"),
                    checkboxRule("possible_negative", "no ai used"),
                    checkboxRule("possible_negative", "no automation used")
            )),
            Map.entry("qgis/qgis", List.of(
                    checkboxRule("possible_positive", "ai tools supported this pr")
            )),
            Map.entry("qutip/qutip", List.of(
                    checkboxRule("possible_negative", "no ai used")
            )),
            Map.entry("kornia/kornia", List.of(
                    checkboxRule("possible_negative", "no ai used"),
                    checkboxRule("possible_positive", "ai assisted used ai boilerplate refactoring manually reviewed tested every line"),
                    checkboxRule("possible_positive", "ai generated")
            ))
    );
    private static final Pattern PGWATCH_AI_AUTOMATION_FIELD_PATTERN = Pattern.compile("(?im)^\\s*(?:#{1,6}\\s*)?AI\\s*/\\s*automation\\s+tools\\s+used\\s*:?\\s*(.*)$");
    private static final List<Pattern> PGWATCH_PLACEHOLDER_PATTERNS = List.of(
            Pattern.compile("(?is)^\\s*(?:none|n/a|not\\s+applicable|no|blank)?\\s*$"),
            Pattern.compile("(?is)\\b(?:please|list|describe|specify|if\\s+any|tool\\s+name|model\\s+name|unchanged|template|placeholder)\\b")
    );
    private static final Pattern ASSISTED_BY_FIELD_PATTERN = Pattern.compile("(?i)^\\s*(?:[-*+]\\s*)?(?:[*_`\\s]*)?(?:(AI)\\s*[-\\s]*)?(?:assist(?:ed|ant))\\s*[-\\s]*by\\s*:?\\s*(.*)$");
    private static final Pattern CO_AUTHORED_BY_PATTERN = Pattern.compile("(?i)^\\s*(?:[-*+]\\s*)?co\\s*-?\\s*authored\\s*-?\\s*by\\s*:?\\s*(.*)$");
    private static final Pattern AIL_PATTERN = Pattern.compile("(?i)^\\s*(?:[-*+]\\s*)?(?:[*_`#\\s]*)?(?:AI\\s+influence\\s+level|AIL)(?:\\s+level)?\\s*(?::|=)?\\s*([0-5])\\s*[.)!`*_\\s]*$");
    private static final Pattern INLINE_AIL_PATTERN = Pattern.compile("(?i)\\b(?:AI\\s+influence\\s+level|AIL)(?:\\s+level)?\\s*(?::|=)\\s*([0-5])\\b(?!\\s*(?:\\.\\d|\\d))");
    private static final List<Pattern> PLACEHOLDER_RESPONSE_PATTERNS = List.of(
            Pattern.compile("(?is)^\\s*$"),
            Pattern.compile("(?is)^\\s*(?:n/?a|none|no|not\\s+applicable|null|nil|-+)\\s*[.!]??\\s*$"),
            Pattern.compile("(?is)^\\s*(?:<[^>]+>|\\[[^]]*(?:tool|model|score|insert|name|value|placeholder)[^]]*])\\s*$"),
            Pattern.compile("(?is)\\b(?:please|enter|insert|provide|specify|describe|list|replace|tool\\s+name|model\\s+name|instructions?)\\b")
    );

    public DisclosureResult detect(String prBody, String htmlText) {
        return detect(null, prBody, htmlText);
    }

    public DisclosureResult detect(String repository, String prBody, String htmlText) {
        DisclosureResult repositoryBodyResult = detectRepositoryRules(repository, prBody, "PR body");
        DisclosureResult bodyResult = detectInText(removeUncheckedRepositoryTemplateText(repository, prBody), "PR body");
        if (bodyResult.disclosed()) {
            if (repositoryBodyResult.disclosed()
                    && "possible_negative".equals(repositoryBodyResult.classification())
                    && "possible_positive".equals(bodyResult.classification())) {
                return new DisclosureResult(true, cleanEvidence(bodyResult.evidence() + "; contradictory template evidence: " + repositoryBodyResult.evidence()), "possible_positive", "PR body");
            }
            if (repositoryBodyResult.disclosed()) {
                return repositoryBodyResult;
            }
            return bodyResult;
        }
        if (repositoryBodyResult.disclosed()) {
            return repositoryBodyResult;
        }

        String filteredHtml = removeGitHubChrome(htmlText);
        DisclosureResult repositoryHtmlResult = detectRepositoryRules(repository, filteredHtml, "HTML fallback");
        DisclosureResult htmlResult = detectInText(removeUncheckedRepositoryTemplateText(repository, filteredHtml), "HTML fallback");
        if (htmlResult.disclosed()) {
            if (repositoryHtmlResult.disclosed()
                    && "possible_negative".equals(repositoryHtmlResult.classification())
                    && "possible_positive".equals(htmlResult.classification())) {
                return new DisclosureResult(true, cleanEvidence(htmlResult.evidence() + "; contradictory template evidence: " + repositoryHtmlResult.evidence()), "possible_positive", "HTML fallback");
            }
            if (repositoryHtmlResult.disclosed()) {
                return repositoryHtmlResult;
            }
            return htmlResult;
        }
        if (repositoryHtmlResult.disclosed()) {
            return repositoryHtmlResult;
        }

        if (isBlank(prBody) && isBlank(htmlText)) {
            return new DisclosureResult(false, "No PR body or HTML text found");
        }
        return new DisclosureResult(false, "No contributor AI disclosure text detected");
    }

    public DetectionDiagnostics diagnosePrBody(String prBody) {
        return diagnosePrBody(null, prBody);
    }

    public DetectionDiagnostics diagnosePrBody(String repository, String prBody) {
        PreparedText prepared = prepareText(prBody == null ? "" : prBody);
        DisclosureResult repositoryResult = detectRepositoryRules(repository, prBody, "PR body");
        DisclosureResult result = repositoryResult.disclosed()
                ? repositoryResult
                : detectInText(removeUncheckedRepositoryTemplateText(repository, prBody), "PR body");
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

        DisclosureResult checkboxResult = detectTemplateCheckboxes(prepared.checkedCheckboxes(), prepared.uncheckedCheckboxes(), source);
        if (checkboxResult.disclosed()) {
            return checkboxResult;
        }

        DisclosureResult structured = detectStructuredDisclosures(prepared.textWithoutCheckboxes(), source);
        if (structured.disclosed()) {
            return structured;
        }

        DisclosureResult generatedBy = detectGeneratedBy(prepared.textWithoutCheckboxes(), source);
        if (generatedBy.disclosed()) {
            return generatedBy;
        }

        DisclosureResult contextual = detectContextualSections(prepared.visibleText(), source);
        if (contextual.disclosed()) {
            String conflictText = removeAiDisclosureFieldLabels(prepared.textWithoutCheckboxes());
            if ("possible_positive".equals(contextual.classification())) {
                DisclosureResult conflictingNegative = findDisclosure(conflictText, NEGATIVE_DISCLOSURE_PATTERNS, "possible_negative", source);
                if (conflictingNegative.disclosed()) {
                    return new DisclosureResult(true, cleanEvidence(contextual.evidence() + "; " + conflictingNegative.evidence()), "possible_ambiguous", source);
                }
            } else if ("possible_negative".equals(contextual.classification())) {
                DisclosureResult conflictingPositive = findDisclosure(conflictText, POSITIVE_DISCLOSURE_PATTERNS, "possible_positive", source);
                if (conflictingPositive.disclosed()) {
                    return new DisclosureResult(true, cleanEvidence(contextual.evidence() + "; " + conflictingPositive.evidence()), "possible_ambiguous", source);
                }
            }
            return contextual;
        }

        String broadMatchText = removeAilPolicyExplanationLines(removeAiDisclosureFieldLabels(prepared.textWithoutCheckboxes()))
                .replace("\\:", ":")
                .replaceAll("[`*_]+", "");
        if (isUnresolvedYesNoAiTemplate(broadMatchText)) {
            return new DisclosureResult(false, "Unresolved AI-use yes/no template", "none", source);
        }
        DisclosureResult negative = findDisclosure(broadMatchText, NEGATIVE_DISCLOSURE_PATTERNS, "possible_negative", source);
        if (negative.disclosed()) {
            return negative;
        }
        DisclosureResult positive = findDisclosure(broadMatchText, POSITIVE_DISCLOSURE_PATTERNS, "possible_positive", source);
        if (positive.disclosed()) {
            return positive;
        }
        if (isLikelyFilenameOnlyMention(broadMatchText)) {
            return new DisclosureResult(false, "AI term appears only as a filename or path", "none", source);
        }
        return findDisclosure(broadMatchText, AMBIGUOUS_DISCLOSURE_PATTERNS, "possible_ambiguous", source);
    }

    private static DisclosureResult findDisclosure(String text, List<Pattern> patterns, String classification, String source) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                if ("possible_negative".equals(classification) && hasNegatedNegativeContext(text, matcher.start())) {
                    continue;
                }
                if ("possible_positive".equals(classification) && hasNegatedPositiveContext(text, matcher.start())) {
                    continue;
                }
                if ("possible_positive".equals(classification) && hasHypotheticalPositiveContext(text, matcher.start())) {
                    continue;
                }
                return new DisclosureResult(true, cleanEvidence(matcher.group()), classification, source);
            }
        }
        return new DisclosureResult(false, "No contributor AI disclosure text detected", "none", source);
    }

    private static boolean hasNegatedNegativeContext(String text, int matchStart) {
        String prefix = text.substring(Math.max(0, matchStart - 80), matchStart);
        for (Pattern pattern : NEGATED_NEGATIVE_CONTEXT_PATTERNS) {
            if (pattern.matcher(prefix).find()) return true;
        }
        return false;
    }

    private static boolean hasNegatedPositiveContext(String text, int matchStart) {
        String prefix = text.substring(Math.max(0, matchStart - 24), matchStart);
        return prefix.matches("(?is).*\\b(?:no|not|without)\\s+$");
    }

    private static boolean hasHypotheticalPositiveContext(String text, int matchStart) {
        String prefix = text.substring(Math.max(0, matchStart - 80), matchStart);
        return prefix.matches("(?is).*\\bif\\s+(?:i\\s+)?$");
    }

    private static String removeAilPolicyExplanationLines(String text) {
        List<String> kept = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            if (!line.matches("(?i)^\\s*(?:[-*+]\\s*)?AIL\\s+[0-5]\\s+(?:means|indicates|represents|=)\\b.*$")) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }

    private static String removeAiDisclosureFieldLabels(String text) {
        List<String> kept = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            Matcher inline = AI_INLINE_FIELD_PATTERN.matcher(line);
            if (inline.matches()) {
                kept.add(inline.group(1));
            } else {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
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
        String visibleText = stripMarkdownLinks(removeHtmlComments(text));
        List<String> checkedCheckboxes = new ArrayList<>();
        List<String> uncheckedCheckboxes = new ArrayList<>();
        String textWithoutCheckboxes = removeTemplateResponseLines(visibleText, checkedCheckboxes, uncheckedCheckboxes);
        return new PreparedText(visibleText, textWithoutCheckboxes, checkedCheckboxes, uncheckedCheckboxes);
    }

    static String removeHtmlComments(String text) {
        if (isBlank(text)) return "";
        return HTML_COMMENT_PATTERN.matcher(text).replaceAll(" ");
    }

    private static String stripMarkdownLinks(String text) {
        return text.replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");
    }

    private static String removeTemplateResponseLines(String text, List<String> checkedCheckboxes, List<String> uncheckedCheckboxes) {
        List<String> keptLines = new ArrayList<>();
        List<String> lines = List.of(text.split("\\R", -1));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.stripLeading().startsWith(">")) {
                line = line.replaceFirst("^\\s*>\\s?", "");
            }
            Matcher matcher = MARKDOWN_CHECKBOX_START_PATTERN.matcher(line);
            if (matcher.matches()) {
                List<String> labelLines = new ArrayList<>();
                labelLines.add(matcher.group(2).trim());
                while (i + 1 < lines.size() && isCheckboxContinuation(lines.get(i + 1))) {
                    i++;
                    labelLines.add(lines.get(i).trim());
                }
                String label = String.join(" ", labelLines).replaceAll("\\s+", " ").trim();
                String checkedMarker = matcher.group(1);
                if (!checkedMarker.isBlank()) {
                    checkedCheckboxes.add("[x] " + label);
                } else {
                    uncheckedCheckboxes.add("[ ] " + label);
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

    private static boolean isCheckboxContinuation(String line) {
        if (line.isBlank()) return false;
        if (MARKDOWN_CHECKBOX_START_PATTERN.matcher(line).matches()) return false;
        if (ANY_MARKDOWN_HEADING_PATTERN.matcher(line).matches()) return false;
        if (MARKDOWN_LIST_ITEM_PATTERN.matcher(line).matches()) return false;
        return line.startsWith("  ") || line.startsWith("\t");
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
            if (!negative.disclosed()) {
                negative = findDisclosure(cleaned, CONTEXTUAL_NEGATIVE_DISCLOSURE_PATTERNS, "possible_negative", source);
            }
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
                    Pattern.compile("(?is)\\b(?:used|with|generated\\s+by|assisted\\s+by)\\s+" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}"),
                    Pattern.compile("(?is)\\b(?:used\\s+for|for|help(?:ed)?(?:\\s+trying\\s+to)?)\\s+(?:grammar\\s+checks?|debug(?:ging)?|investigat(?:e|ing)|reproduc(?:e|ing)|planning|implementation|tests?|docstrings?|documentation|title|review|refactor(?:ing)?)\\b[^\\r\\n.]{0,160}"),
                    Pattern.compile("(?is)^\\s*" + AI_TOOL_NAME + "\\b[^\\r\\n.]{0,160}$")
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
            Matcher inline = AI_INLINE_FIELD_PATTERN.matcher(line);
            if (inline.matches()) {
                answers.add(inline.group(1));
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

    private static DisclosureResult detectTemplateCheckboxes(List<String> checkedCheckboxes, List<String> uncheckedCheckboxes, String source) {
        List<String> positive = new ArrayList<>();
        List<String> negative = new ArrayList<>();
        for (String checkbox : checkedCheckboxes) {
            String label = checkbox.replaceFirst("(?is)^\\[x]\\s*", "").trim();
            if (isNegativeCheckbox(label)) {
                negative.add(checkbox);
            } else if (isAiToolDisclosureCheckbox(label)) {
                positive.add(checkbox);
            } else if (isAffirmativeCheckbox(label)) {
                positive.add(checkbox);
            }
        }
        for (String checkbox : uncheckedCheckboxes) {
            String label = checkbox.replaceFirst("(?is)^\\[ ]\\s*", "").trim();
            if (isAiToolDisclosureCheckbox(label)) {
                negative.add(checkbox);
            }
        }
        if (!positive.isEmpty() && !negative.isEmpty()) {
            List<String> all = new ArrayList<>();
            all.addAll(positive);
            all.addAll(negative);
            return new DisclosureResult(true, cleanEvidence(String.join("; ", all)), "possible_ambiguous", source);
        }
        if (!negative.isEmpty()) {
            return new DisclosureResult(true, cleanEvidence(negative.get(0)), "possible_negative", source);
        }
        if (!positive.isEmpty()) {
            return new DisclosureResult(true, cleanEvidence(positive.get(0)), "possible_positive", source);
        }
        return new DisclosureResult(false, "No completed AI disclosure checkbox found", "none", source);
    }

    private static DisclosureResult detectRepositoryRules(String repository, String text, String source) {
        String canonicalRepository = canonicalRepository(repository);
        if (canonicalRepository == null || isBlank(text)) {
            return new DisclosureResult(false, "No repository-specific disclosure rule matched", "none", source);
        }
        PreparedText prepared = prepareText(text);
        List<RepositoryCheckboxRule> rules = REPOSITORY_CHECKBOX_RULES.getOrDefault(canonicalRepository, List.of());
        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();
        List<String> neutrals = new ArrayList<>();
        for (String checkbox : prepared.checkedCheckboxes()) {
            String normalized = normalizeCheckboxLabel(checkbox.replaceFirst("(?is)^\\[x]\\s*", ""));
            for (RepositoryCheckboxRule rule : rules) {
                if (containsWordsInOrder(normalized, rule.normalizedNeedle())) {
                    addByClassification(rule.classification(), checkbox, positives, negatives, neutrals);
                    break;
                }
            }
        }
        if ("qgis/qgis".equals(canonicalRepository)) {
            for (String checkbox : prepared.checkedCheckboxes()) {
                String normalized = normalizeCheckboxLabel(checkbox.replaceFirst("(?is)^\\[x]\\s*", ""));
                if (containsWordsInOrder(normalized, normalizeCheckboxLabel("ai supported development pr"))
                        || containsWordsInOrder(normalized, normalizeCheckboxLabel("ai supported this pr"))) {
                    if (!positives.contains(checkbox)) {
                        positives.add(checkbox);
                    }
                }
            }
            for (String checkbox : prepared.uncheckedCheckboxes()) {
                String normalized = normalizeCheckboxLabel(checkbox.replaceFirst("(?is)^\\[ ]\\s*", ""));
                if (containsWordsInOrder(normalized, normalizeCheckboxLabel("ai supported development pr"))
                        || containsWordsInOrder(normalized, normalizeCheckboxLabel("ai supported this pr"))) {
                    negatives.add(checkbox);
                }
            }
        }
        if ("mdanalysis/mdanalysis".equals(canonicalRepository)) {
            DisclosureResult mdanalysisResult = detectMdanalysisAiQuestion(prepared.visibleText(), source);
            if (mdanalysisResult.disclosed()) {
                addByClassification(mdanalysisResult.classification(), mdanalysisResult.evidence(), positives, negatives, neutrals);
            }
        }
        if ("searxng/searxng".equals(canonicalRepository)) {
            DisclosureResult searxngResult = detectSearxngAiPolicyConfirmation(prepared, source);
            if (searxngResult.disclosed()) {
                addByClassification(searxngResult.classification(), searxngResult.evidence(), positives, negatives, neutrals);
            }
            for (String checkbox : prepared.checkedCheckboxes()) {
                String normalized = normalizeCheckboxLabel(checkbox.replaceFirst("(?is)^\\[x]\\s*", ""));
                if (containsWordsInOrder(normalized, normalizeCheckboxLabel("not used ai tools creating pr"))
                        || containsWordsInOrder(normalized, normalizeCheckboxLabel("not used any ai tools"))) {
                    if (!negatives.contains(checkbox)) {
                        negatives.add(checkbox);
                    }
                }
            }
        }
        if ("cybertec-postgresql/pgwatch".equals(canonicalRepository)) {
            DisclosureResult fieldResult = detectPgwatchAiAutomationField(prepared.textWithoutCheckboxes(), source);
            if (fieldResult.disclosed()) {
                positives.add(fieldResult.evidence());
            }
        }
        return repositoryRuleResult(positives, negatives, neutrals, source);
    }

    private static DisclosureResult detectSearxngAiPolicyConfirmation(PreparedText prepared, String source) {
        for (String checkbox : prepared.checkedCheckboxes()) {
            String normalized = normalizeCheckboxLabel(checkbox.replaceFirst("(?is)^\\[x]\\s*", ""));
            if (containsWordsInOrder(normalized, normalizeCheckboxLabel("hereby confirm pr conforms ai policy"))) {
                return new DisclosureResult(true, cleanEvidence(checkbox), "possible_negative", source);
            }
        }
        return new DisclosureResult(false, "No checked SearXNG AI policy confirmation found", "none", source);
    }

    private static DisclosureResult detectMdanalysisAiQuestion(String text, String source) {
        List<String> lines = List.of(text.split("\\R", -1));
        for (int i = 0; i < lines.size(); i++) {
            String line = stripMarkdown(removeStrikethrough(lines.get(i)));
            String normalizedQuestion = normalizeCheckboxLabel(line);
            if (!containsWordsInOrder(normalizedQuestion, normalizeCheckboxLabel("llms ai powered tools used contribution"))) {
                continue;
            }
            String answer = "";
            int colon = line.indexOf(':');
            if (colon >= 0 && colon + 1 < line.length()) {
                answer = line.substring(colon + 1).trim();
            }
            for (int j = i + 1; j < lines.size() && answer.isBlank(); j++) {
                String next = stripMarkdown(lines.get(j)).trim();
                if (next.isBlank()) continue;
                if (ANY_MARKDOWN_HEADING_PATTERN.matcher(next).matches()
                        || MARKDOWN_CHECKBOX_START_PATTERN.matcher(next).matches()
                        || looksLikeFieldHeading(next)) {
                    break;
                }
                answer = next;
            }
            String normalizedAnswer = normalizeCheckboxLabel(removeStrikethrough(answer));
            if (normalizedAnswer.matches("(?is).*\\byes\\b.*\\bno\\b.*|.*\\bno\\b.*\\byes\\b.*")) {
                return new DisclosureResult(false, "Unresolved MDAnalysis AI-use template answer", "none", source);
            }
            if (normalizedAnswer.matches("(?is)^yes(?:\\s+.*)?$")) {
                return new DisclosureResult(true, cleanEvidence(lines.get(i).trim() + " " + answer), "possible_positive", source);
            }
            if (normalizedAnswer.matches("(?is)^no(?:\\s+.*)?$")) {
                return new DisclosureResult(true, cleanEvidence(lines.get(i).trim() + " " + answer), "possible_negative", source);
            }
        }
        return new DisclosureResult(false, "No completed MDAnalysis AI-use template answer found", "none", source);
    }

    private static DisclosureResult detectStructuredDisclosures(String text, String source) {
        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();

        DisclosureResult ail = detectAiInfluenceLevel(text, source);
        if (ail.disclosed()) {
            if ("possible_ambiguous".equals(ail.classification())) {
                return ail;
            }
            addByClassification(ail.classification(), ail.evidence(), positives, negatives, new ArrayList<>());
        }

        DisclosureResult assistedBy = detectAssistedBy(text, source);
        if (assistedBy.disclosed()) {
            if ("possible_ambiguous".equals(assistedBy.classification())) {
                return assistedBy;
            }
            addByClassification(assistedBy.classification(), assistedBy.evidence(), positives, negatives, new ArrayList<>());
        }

        DisclosureResult coAuthoredBy = detectCoAuthoredBy(text, source);
        if (coAuthoredBy.disclosed()) {
            positives.add(coAuthoredBy.evidence());
        }

        if (!positives.isEmpty() && !negatives.isEmpty()) {
            List<String> all = new ArrayList<>();
            all.addAll(positives);
            all.addAll(negatives);
            return new DisclosureResult(true, cleanEvidence(String.join("; ", all)), "possible_ambiguous", source);
        }
        if (positives.size() > 1) {
            return new DisclosureResult(true, cleanEvidence(String.join("; ", positives)), "possible_positive", source);
        }
        if (negatives.size() > 1) {
            return new DisclosureResult(true, cleanEvidence(String.join("; ", negatives)), "possible_negative", source);
        }
        if (!positives.isEmpty()) return new DisclosureResult(true, cleanEvidence(positives.get(0)), "possible_positive", source);
        if (!negatives.isEmpty()) return new DisclosureResult(true, cleanEvidence(negatives.get(0)), "possible_negative", source);
        return new DisclosureResult(false, "No structured AI disclosure field found", "none", source);
    }

    private static DisclosureResult detectAiInfluenceLevel(String text, String source) {
        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            Matcher matcher = AIL_PATTERN.matcher(line);
            if (!matcher.matches()) {
                matcher = INLINE_AIL_PATTERN.matcher(line);
                if (!matcher.find()) {
                    continue;
                }
            }
            int score = Integer.parseInt(matcher.group(1));
            if (score == 0) {
                negatives.add(line.trim());
            } else {
                positives.add(line.trim());
            }
        }
        if (!positives.isEmpty() && !negatives.isEmpty()) {
            List<String> all = new ArrayList<>();
            all.addAll(positives);
            all.addAll(negatives);
            return new DisclosureResult(true, cleanEvidence(String.join("; ", all)), "possible_ambiguous", source);
        }
        if (positives.size() > 1 || negatives.size() > 1) {
            List<String> all = positives.isEmpty() ? negatives : positives;
            return new DisclosureResult(true, cleanEvidence(String.join("; ", all)), "possible_ambiguous", source);
        }
        if (!positives.isEmpty()) return new DisclosureResult(true, cleanEvidence(positives.get(0)), "possible_positive", source);
        if (!negatives.isEmpty()) return new DisclosureResult(true, cleanEvidence(negatives.get(0)), "possible_negative", source);
        return new DisclosureResult(false, "No completed AIL score found", "none", source);
    }

    private static DisclosureResult detectAssistedBy(String text, String source) {
        List<String> lines = List.of(text.split("\\R", -1));
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = ASSISTED_BY_FIELD_PATTERN.matcher(lines.get(i));
            if (!matcher.matches()) {
                continue;
            }
            boolean aiQualifiedLabel = matcher.group(1) != null;
            List<String> answerLines = new ArrayList<>();
            String inlineAnswer = stripMarkdown(matcher.group(2));
            if (!inlineAnswer.isBlank()) {
                answerLines.add(inlineAnswer);
            }
            for (int j = i + 1; j < lines.size() && answerLines.isEmpty(); j++) {
                String next = lines.get(j);
                if (next.isBlank()) break;
                if (ANY_MARKDOWN_HEADING_PATTERN.matcher(next).matches()
                        || MARKDOWN_CHECKBOX_START_PATTERN.matcher(next).matches()
                        || looksLikeFieldHeading(next)) {
                    break;
                }
                if (next.startsWith("  ") || next.startsWith("\t")) {
                    answerLines.add(next.trim());
                    continue;
                }
                break;
            }
            String answer = stripMarkdown(String.join(" ", answerLines).replaceAll("\\s+", " ").trim());
            if (isExplicitNoUseResponse(answer)) {
                if (aiQualifiedLabel || mentionsAi(answer)) {
                    return new DisclosureResult(true, cleanEvidence(lines.get(i).trim() + " " + answer), "possible_negative", source);
                }
                continue;
            }
            if (isPlaceholderResponse(answer)) {
                continue;
            }
            if (AI_IDENTITY_PATTERN.matcher(answer).find()) {
                return new DisclosureResult(true, cleanEvidence(lines.get(i).trim() + (answerLines.isEmpty() ? "" : " " + answer)), "possible_positive", source);
            }
        }
        return new DisclosureResult(false, "No completed Assisted by field found", "none", source);
    }

    private static DisclosureResult detectCoAuthoredBy(String text, String source) {
        for (String line : text.split("\\R", -1)) {
            Matcher matcher = CO_AUTHORED_BY_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String coAuthor = stripMarkdown(matcher.group(1).trim());
            if (isPlaceholderResponse(coAuthor)) {
                continue;
            }
            if (AI_IDENTITY_PATTERN.matcher(removeContactDetails(coAuthor)).find()) {
                return new DisclosureResult(true, cleanEvidence(line), "possible_positive", source);
            }
        }
        return new DisclosureResult(false, "No AI co-author attribution found", "none", source);
    }

    private static String removeContactDetails(String value) {
        return value.replaceAll("<[^>]*>", " ")
                .replaceAll("(?i)\\bhttps?://\\S+", " ")
                .replaceAll("\\b\\S+@\\S+\\b", " ");
    }

    private static String removeUncheckedRepositoryTemplateText(String repository, String text) {
        String canonicalRepository = canonicalRepository(repository);
        if (canonicalRepository == null || isBlank(text)) return text;
        List<RepositoryCheckboxRule> rules = REPOSITORY_CHECKBOX_RULES.getOrDefault(canonicalRepository, List.of());
        if (rules.isEmpty() && !List.of("mdanalysis/mdanalysis", "searxng/searxng", "qgis/qgis").contains(canonicalRepository)) {
            return text;
        }
        List<String> kept = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            String normalized = normalizeCheckboxLabel(line);
            boolean repositoryTemplateLine = "cybertec-postgresql/pgwatch".equals(canonicalRepository)
                    && (PGWATCH_AI_AUTOMATION_FIELD_PATTERN.matcher(line).matches()
                    || PGWATCH_PLACEHOLDER_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(line).find()));
            repositoryTemplateLine = repositoryTemplateLine
                    || ("mdanalysis/mdanalysis".equals(canonicalRepository)
                    && containsWordsInOrder(normalized, normalizeCheckboxLabel("llms ai powered tools used contribution")));
            repositoryTemplateLine = repositoryTemplateLine
                    || ("searxng/searxng".equals(canonicalRepository)
                    && (containsWordsInOrder(normalized, normalizeCheckboxLabel("hereby confirm pr conforms ai policy"))
                    || containsWordsInOrder(normalized, normalizeCheckboxLabel("not used ai tools creating pr"))
                    || containsWordsInOrder(normalized, normalizeCheckboxLabel("not used any ai tools"))));
            repositoryTemplateLine = repositoryTemplateLine
                    || ("qgis/qgis".equals(canonicalRepository)
                    && (containsWordsInOrder(normalized, normalizeCheckboxLabel("ai supported development pr"))
                    || containsWordsInOrder(normalized, normalizeCheckboxLabel("ai supported this pr"))));
            if (!"osgeo/gdal".equals(canonicalRepository)) {
                for (RepositoryCheckboxRule rule : rules) {
                    if (containsWordsInOrder(normalized, rule.normalizedNeedle())) {
                        repositoryTemplateLine = true;
                        break;
                    }
                }
            }
            if (!repositoryTemplateLine) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }

    private static DisclosureResult detectPgwatchAiAutomationField(String text, String source) {
        List<String> lines = List.of(text.split("\\R", -1));
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = PGWATCH_AI_AUTOMATION_FIELD_PATTERN.matcher(lines.get(i));
            if (!matcher.matches()) {
                continue;
            }
            List<String> answerLines = new ArrayList<>();
            if (!matcher.group(1).isBlank()) {
                answerLines.add(matcher.group(1).trim());
            }
            for (int j = i + 1; j < lines.size(); j++) {
                String next = lines.get(j);
                if (ANY_MARKDOWN_HEADING_PATTERN.matcher(next).matches()
                        || MARKDOWN_CHECKBOX_START_PATTERN.matcher(next).matches()
                        || next.matches("(?i)^\\s*[A-Za-z][A-Za-z /-]{2,60}:\\s*$")) {
                    break;
                }
                answerLines.add(next.trim());
            }
            String answer = String.join(" ", answerLines).replaceAll("\\s+", " ").trim();
            if (isMeaningfulPgwatchFieldAnswer(answer)) {
                return new DisclosureResult(true, cleanEvidence("AI/automation tools used: " + answer), "possible_positive", source);
            }
        }
        return new DisclosureResult(false, "No completed pgwatch AI/automation tools field found", "none", source);
    }

    private static boolean isMeaningfulPgwatchFieldAnswer(String answer) {
        if (answer.isBlank()) return false;
        for (Pattern pattern : PGWATCH_PLACEHOLDER_PATTERNS) {
            if (pattern.matcher(answer).find()) return false;
        }
        return true;
    }

    private static boolean isPlaceholderResponse(String answer) {
        for (Pattern pattern : PLACEHOLDER_RESPONSE_PATTERNS) {
            if (pattern.matcher(answer).find()) return true;
        }
        return false;
    }

    private static boolean isExplicitNoUseResponse(String answer) {
        String normalized = normalizeCheckboxLabel(answer);
        return normalized.matches("(?is)^(?:n a|none|no|not applicable)$")
                || normalized.matches("(?is).*\\b(?:no|without|not)\\b.*\\b(?:ai|llm|assistance|tools?|automation)\\b.*")
                || normalized.matches("(?is).*\\b(?:ai|llm)\\b.*\\b(?:not|no)\\b.*\\b(?:used|assistance)\\b.*");
    }

    private static boolean mentionsAi(String value) {
        return AI_IDENTITY_PATTERN.matcher(value).find()
                || normalizeCheckboxLabel(value).matches("(?is).*\\b(?:ai|llm)\\b.*");
    }

    private static boolean looksLikeFieldHeading(String line) {
        return line.matches("(?i)^\\s*[A-Za-z][A-Za-z /-]{2,60}:\\s*$");
    }

    private static String stripMarkdown(String value) {
        return value.replaceAll("[`*_]+", "")
                .replaceAll("^\\s*>\\s*", "")
                .replace("\\:", ":")
                .trim();
    }

    private static String removeStrikethrough(String value) {
        return value.replaceAll("(?is)~~.*?~~", " ")
                .replaceAll("(?is)<strike>.*?</strike>", " ")
                .replaceAll("(?is)<s>.*?</s>", " ")
                .replaceAll("(?is)<del>.*?</del>", " ");
    }

    private static DisclosureResult repositoryRuleResult(List<String> positives, List<String> negatives, List<String> neutrals, String source) {
        int categories = (positives.isEmpty() ? 0 : 1) + (negatives.isEmpty() ? 0 : 1) + (neutrals.isEmpty() ? 0 : 1);
        if (categories > 1 || positives.size() + negatives.size() + neutrals.size() > 1) {
            List<String> all = new ArrayList<>();
            all.addAll(positives);
            all.addAll(negatives);
            all.addAll(neutrals);
            return new DisclosureResult(true, cleanEvidence(String.join("; ", all)), "possible_ambiguous", source);
        }
        if (!positives.isEmpty()) return new DisclosureResult(true, cleanEvidence(positives.get(0)), "possible_positive", source);
        if (!negatives.isEmpty()) return new DisclosureResult(true, cleanEvidence(negatives.get(0)), "possible_negative", source);
        if (!neutrals.isEmpty()) return new DisclosureResult(true, cleanEvidence(neutrals.get(0)), "possible_neutral", source);
        return new DisclosureResult(false, "No repository-specific checked AI disclosure checkbox found", "none", source);
    }

    private static void addByClassification(String classification, String evidence, List<String> positives, List<String> negatives, List<String> neutrals) {
        if ("possible_positive".equals(classification)) positives.add(evidence);
        else if ("possible_negative".equals(classification)) negatives.add(evidence);
        else neutrals.add(evidence);
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

    private static boolean isAiToolDisclosureCheckbox(String label) {
        String normalized = normalizeCheckboxLabel(label);
        return containsWordsInOrder(normalized, normalizeCheckboxLabel("ai tool"))
                || containsWordsInOrder(normalized, normalizeCheckboxLabel("ai tools"));
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

    private static boolean isUnresolvedYesNoAiTemplate(String text) {
        return text.matches("(?is).*\\b(?:llms?|ai|ai-powered|generative\\s+ai)\\b[^\\r\\n]{0,180}\\b(?:used|use)\\b[^\\r\\n]{0,80}\\byes\\s*/\\s*no\\b.*");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String canonicalRepository(String repository) {
        if (isBlank(repository)) return null;
        String cleaned = repository.trim();
        if (!REPO_NAME_PATTERN.matcher(cleaned).matches()) return null;
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private static RepositoryCheckboxRule checkboxRule(String classification, String normalizedNeedle) {
        return new RepositoryCheckboxRule(classification, normalizeCheckboxLabel(normalizedNeedle));
    }

    private static String normalizeCheckboxLabel(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\uFE0E\\uFE0F]", "")
                .replaceAll("[^\\p{Alnum}]+", " ")
                .replaceAll("\\b(?:i|this|the|a|an|to|with|for|of|or|and|is|my)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsWordsInOrder(String haystack, String needle) {
        int index = 0;
        for (String word : needle.split("\\s+")) {
            int found = haystack.indexOf(word, index);
            if (found < 0) return false;
            index = found + word.length();
        }
        return true;
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

    private record RepositoryCheckboxRule(String classification, String normalizedNeedle) {
    }
}
