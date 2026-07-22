# Pull Request Coding Framework

This document defines pull-request-level coding rules for AI-use disclosure compliance.

Repository-level governance coding is a separate manual activity. See `repository_analysis_framework.md` for policy-location, visibility, enforcement, and governance-theme coding.

## General PR Coding Principles

- Code only observable evidence in the PR record.
- Do not infer actual AI use from code style, commit patterns, or contributor identity.
- Distinguish a contributor disclosure from an incidental AI-related mention.
- Preserve exact evidence text where possible.
- Mark uncertainty explicitly instead of forcing a high-confidence binary label.
- Treat automated detector output as a review queue, not final classification.

## Eligible PR Rules

Eligible PRs should match the study's sampling frame before compliance is calculated.

Coding rules:

- Include PRs from repositories selected for the study.
- Include PRs in the intended study window. For each repository, the requested number of latest closed, human-authored pull requests is collected first. A four-calendar-month eligibility cutoff is then applied using each pull request's closure timestamp (`closed_at`). Pull requests closed before the cutoff are excluded and are not replaced, so the final number analysed can be lower than the requested count.
- Include PRs with human contributors unless the research design explicitly includes bots.
- Exclude or separately report bot PRs according to the bot handling rules below.
- Record whether the PR was merged or closed unmerged.
- Preserve the PR URL so reviewers can verify context.

Allowed values depend on the dataset column:

- `Status`: `Merged`, `Closed`, or `Unknown`.
- `Bot`: `Yes` or `No`.
- Date fields: GitHub timestamps or blank when unavailable.

Manual analysis instructions:

- Confirm that sampled PRs fall inside the intended window.
- Spot-check PR status and authorship for each repository.
- Record any exclusions or sampling deviations in research notes.

## Bot Handling

Bot PRs can distort contributor compliance rates and should not be included in the human contributor denominator when the study excludes automation.

Coding rules:

- Code `Bot = Yes` when the author is a GitHub bot, app account, or known automation account.
- Code `Bot = No` when the author appears to be a human contributor.
- Treat ambiguous service accounts as manual-review cases.
- If bot PRs are excluded during collection, preserve a repository-level count in the compliance summary.

Examples:

- `dependabot[bot]`: bot.
- `renovate[bot]`: bot.
- `github-actions[bot]`: bot.
- A maintainer or contributor account with normal user activity: human unless other evidence indicates automation.

Manual analysis instructions:

- Review borderline accounts, organization apps, and usernames containing `bot`.
- Keep the denominator definition consistent across repositories.

## PR-Level Fields

| Field | Definition | Allowed values |
| --- | --- | --- |
| AI Disclosure Required | Whether the repository policy required a disclosure for this PR at the time of contribution. | `Yes`, `No`, `Unclear`, `Not applicable`, `MANUAL_REVIEW_REQUIRED` |
| AI Disclosure Present | Whether the PR contains an observable AI-use disclosure or explicit no-AI statement. | `Yes`, `No`, `Unclear` |
| Disclosure Classification | Type of disclosure or AI mention. | `Positive`, `Negative`, `Ambiguous`, `None`, `MANUAL_REVIEW_REQUIRED` before final coding |
| Disclosure Text | Exact matched text or manually extracted disclosure evidence. | Free text; use exact wording where possible. |
| Disclosure Location | Where the disclosure evidence appears. | `PR body`, `PR template checklist`, `PR comment`, `Review comment`, `Commit message`, `Maintainer comment`, `Other`, `Not found` |
| Confidence | Reviewer confidence in the classification. | `High`, `Medium`, `Low` |
| Manual Review Required | Whether a human must inspect the row before final analysis. | `Yes`, `No` |
| Notes | Reviewer notes, uncertainty, exclusions, or rationale. | Free text |

The active `pr_dataset.csv` schema may not contain every recommended review field. When a field is missing, maintain it in a companion review sheet or audit log before final analysis.

## AI Disclosure Present

Definition:

Whether the PR contains an observable disclosure statement about contributor AI use or non-use.

Coding rules:

- Code `Yes` for explicit positive disclosures.
- Code `Yes` for explicit negative disclosures such as `No AI was used`.
- Code `No` when no relevant disclosure or AI-use statement is found.
- Code `Unclear` when AI is mentioned but disclosure status cannot be determined without more context.

Examples:

- `I used ChatGPT to help write the tests.` -> `Yes`.
- `generative AI tooling used to co-author this PR? No` -> `Yes`.
- `Update CLAUDE.md` -> usually `No` or `Unclear`, not a disclosure by itself.
- GitHub page chrome such as `GitHub Copilot Write better code with AI` -> `No`.

Manual analysis instructions:

- Confirm the speaker is the contributor or that the disclosure clearly applies to the PR.
- Use `Disclosure Text` to preserve the evidence phrase.

## Disclosure Classification

| Classification | Definition | Examples | Counted as disclosure present? |
| --- | --- | --- | --- |
| Positive | Contributor states that AI was used, AI assistance was received, or an AI tool helped create the contribution. | `I used ChatGPT to draft tests`; `Assisted-by: Claude`; `Generated with GitHub Copilot and reviewed manually`. | Yes |
| Negative | Contributor states that AI was not used. | `No AI was used`; `I did not use AI assistance`; checked template item indicating no AI use. | Yes, as a disclosure statement; not as AI-use prevalence. |
| Ambiguous | AI is mentioned but not clearly as a contributor self-disclosure. | `Update CLAUDE.md`; `Add llms.txt`; `docs for Copilot users`; discussion about AI policy. | Unclear; review separately. |
| None | No AI-related disclosure or relevant AI mention found. | Empty PR body with no comments mentioning AI. | No |

Manual analysis instructions:

- Use `Positive` and `Negative` only when contributor wording clearly supports the code.
- Keep `Ambiguous` out of final compliance numerators unless recoded by consensus.
- Use `None` only after checking the available PR evidence.
- Treat script-generated `MANUAL_REVIEW_REQUIRED` as a placeholder, not final classification.

## Disclosure Text

Definition:

The exact disclosure phrase or strongest available evidence for manual validation.

Coding rules:

- Preserve exact contributor wording when possible.
- Include both the question and answer for checklist-style disclosures.
- Include the negative answer when present, such as `No`.
- Leave blank or mark `Not found` when no evidence exists, depending on the active dataset.

Examples:

- `generative AI tooling used to co-author this PR? No`
- `AI used? Yes`
- `I used GitHub Copilot to generate part of the implementation.`

Manual analysis instructions:

- Use the PR URL to verify surrounding context before final coding.
- Do not treat extracted text as final proof without review.

## Disclosure Location

Definition:

Where the disclosure evidence appears.

Allowed values:

- `PR body`
- `PR template checklist`
- `PR comment`
- `Review comment`
- `Commit message`
- `Maintainer comment`
- `Other`
- `Not found`

Manual analysis instructions:

- Prefer contributor-authored sources over scraped rendered HTML.
- Do not count GitHub navigation, page chrome, or marketing text as contributor disclosure.
- If the source is unavailable in the active dataset, record it in a companion validation sheet.

## Confidence

| Confidence | Coding rule |
| --- | --- |
| High | Evidence is explicit, source location is clear, and classification follows directly from contributor wording or checked template choice. |
| Medium | Evidence is likely a disclosure but wording, source, or context requires interpretation. |
| Low | AI-related text is detected but may be a filename, documentation topic, quoted policy, maintainer discussion, or unrelated tool reference. |

Manual analysis instructions:

- Use `Low` for detector-only keyword matches.
- Use `Medium` when the evidence appears relevant but speaker or scope is unclear.
- Use `High` only when the disclosure statement is direct and attributable.

## Manual Review Required

Definition:

Whether a human reviewer must inspect the PR before the row is usable for final analysis.

Coding rules:

- Code `Yes` for all detector-positive rows.
- Code `Yes` for ambiguous mentions, low-confidence rows, unclear bot status, or unclear policy requirement.
- Code `Yes` for sampled rows used in final compliance claims unless the validation protocol allows spot-checking.
- Code `No` only after the row has been reviewed and evidence is clear.

Manual analysis instructions:

- Keep a record of reviewer decisions and disagreements.
- Recalculate compliance after manual review updates classification and disclosure-present fields.

## Common AI Terms For Detection

Automated detection may search for:

- `AI`
- `artificial intelligence`
- `generative AI`
- `ChatGPT`
- `GPT`
- `Copilot`
- `Claude`
- `Gemini`
- `LLM`
- `automated assistance`
- `no AI used`
- `I used AI`
- `AI-assisted`

These terms are triggers for review, not final classifications.

## Non-Disclosure Examples

Do not count the following as positive or negative disclosure unless the contributor clearly connects it to their own AI use:

- File names such as `CLAUDE.md`, `llms.txt`, `.cursor/rules`, or `copilot-instructions.md`.
- GitHub page chrome or marketing text, such as `GitHub Copilot Write better code with AI`.
- Documentation about AI tools.
- Project policy language copied into a PR template but left unanswered.
- Maintainer questions or reminders without contributor response.
- Package names, branch names, or test fixtures that include AI-related terms.

## Compliance Calculation Rules

Preliminary script summary:

- `Eligible PRs Reviewed`: human PR rows included in the generated PR dataset.
- `AI Disclosure Present Count`: rows where disclosure-like text was detected.
- `Compliance Rate = AI Disclosure Present Count / Eligible PRs Reviewed`.
- `Manual Review Required Count`: rows requiring validation before final reporting.

Final research calculation:

- Denominator: eligible human PRs in scope where disclosure was required by repository policy.
- Numerator: denominator rows with validated `AI Disclosure Present = Yes`.
- Negative disclosures count as disclosure present because the contributor answered the disclosure requirement.
- Ambiguous disclosures should be reported separately unless manually recoded.
- Final compliance rates must be calculated after manual validation and policy coding are complete.
