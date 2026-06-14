# Validation Protocol

This document defines how human researchers should validate AI-use disclosure coding in pull-request data.

## Rows Requiring Manual Review

Manual review is required for:

- Any row with an AI-related keyword match.
- Any row classified by the script as `Positive`, `Negative`, or `Ambiguous`.
- Any row with low or medium confidence.
- Any row where `AI Disclosure Required = Unclear`.
- Any row where the PR body is empty but comments or review comments may contain disclosure evidence.
- Any row where the detected text includes filenames or documentation references such as `CLAUDE.md`, `llms.txt`, `.cursor/rules`, `Copilot`, `Claude`, `GPT`, or `Gemini`.
- Any row where bot status or contributor status is unclear.

Rows classified as `None` with no AI-related terms may be spot-checked. The spot-check rate should be recorded in the research notes.

## Validation Sources

Reviewers should inspect:

- PR body.
- PR template checklist responses.
- PR comments.
- Review comments when available.
- Maintainer comments asking for disclosure.
- Relevant commits only if the project accepts commit-message disclosure.
- Repository policy text applicable at the time of PR submission when available.

Do not rely on the script output alone for final coding.

## Validating Positive Disclosures

Code `Disclosure Classification = Positive` when the contributor states that AI was used or that an AI tool assisted with the contribution.

Positive examples:

- `I used ChatGPT to help write the tests.`
- `This PR was generated with GitHub Copilot and reviewed manually.`
- `Assisted-by: Claude`.
- A checked PR-template item indicating AI assistance was used.

Validation requirements:

- Confirm the speaker is the contributor or the disclosure clearly refers to the contributor's work.
- Record the exact wording in `Disclosure Text` or `Explicit AI Mention`.
- Record the source in `Disclosure Location`.
- Use `Confidence = High` when wording is explicit and source is clear.

## Validating Negative Disclosures

Code `Disclosure Classification = Negative` when the contributor explicitly states that AI was not used.

Negative examples:

- `No AI was used.`
- `I did not use AI assistance.`
- `generative AI tooling used to co-author this PR? No`.
- `AI used? No`.
- `Was AI used? No`.
- `Not AI generated`.
- `No generative AI`.
- A checked PR-template item indicating no AI use.

Validation requirements:

- Confirm the statement is a response to the repository's disclosure requirement or otherwise applies to the PR.
- Record the exact wording.
- Count `AI Disclosure Present = Yes` because a disclosure statement is present.
- Code the final manual `Disclosure Classification` as `Negative`; negative disclosures still count as disclosure-present but not as evidence that AI was used.
- Do not count negative disclosures as evidence of AI-use prevalence.

## Handling Ambiguous Cases

Code `Disclosure Classification = Ambiguous` when AI is mentioned but the text does not clearly disclose contributor AI use or non-use.

Ambiguous examples:

- `Update CLAUDE.md`.
- `Add llms.txt`.
- `Document Copilot setup`.
- `This policy requires AI disclosure`.
- Maintainer asks, `Did you use AI?`, but the contributor does not answer.
- A PR template contains AI policy text but the contributor leaves the item blank.

Validation requirements:

- Set `AI Disclosure Present = Unclear` unless there is a separate explicit disclosure.
- Set `Manual Review Required = Yes`.
- Record why the case is ambiguous in `Notes`.
- Do not include ambiguous rows in the numerator for compliance unless the research team later recodes them by consensus.

## Handling No Disclosure

Code `Disclosure Classification = None` when no relevant AI disclosure or AI-related mention is found in the reviewed sources.

Validation requirements:

- Set `AI Disclosure Present = No`.
- If AI disclosure was mandatory, this may represent observable non-compliance.
- Record limitations if only the PR body was available and comments were not collected.

Absence of disclosure is not evidence that AI was not used. It only means no observable disclosure was found.

## What Should Not Be Counted As A Disclosure

Do not count these as disclosures without additional contributor self-disclosure:

- File names: `CLAUDE.md`, `llms.txt`, `.cursor/rules`, `copilot-instructions.md`.
- GitHub page chrome or marketing/navigation text, including `GitHub Copilot Write better code with AI`, `GitHub Copilot app`, `Actions Automate any workflow`, `Codespaces Instant dev environments`, `Issues Plan and track work`, `Navigation Menu`, or `Skip to content`.
- Documentation references to AI tools.
- Project policy text copied into a PR template but not answered.
- Maintainer questions or reminders without contributor response.
- Bot-generated comments about policy compliance.
- Package names, branch names, test fixture names, or examples containing AI-related terms.
- General discussion of AI in the project unless tied to the contributor's own use or non-use in the PR.

File names such as `CLAUDE.md` are not automatically disclosure. Treat them as non-disclosure unless there is separate contributor-authored text stating AI use or non-use, such as `I used Claude to draft this PR` or `No AI was used`.

## Resolving Disagreements

Use the following process:

1. Primary reviewer codes the row and records evidence.
2. Secondary reviewer independently checks rows marked uncertain, ambiguous, or high-impact.
3. If reviewers disagree, discuss using the exact PR evidence and coding definitions.
4. If disagreement remains, escalate to a third researcher or mark the row as unresolved.
5. Record final decision and rationale in `Notes`.

Disagreement categories to record:

- Classification disagreement.
- Disclosure-present disagreement.
- Policy-required disagreement.
- Confidence disagreement.
- Source-location disagreement.

## Recording Uncertainty

Use `Confidence` and `Notes` together:

- `High`: explicit wording, clear source, clear speaker.
- `Medium`: likely disclosure but context requires interpretation.
- `Low`: weak signal, unclear speaker, unclear policy applicability, or likely false positive.

Use `Manual Review Required = Yes` whenever:

- Confidence is not high.
- The script matched only a keyword.
- The text may be a filename, documentation topic, or copied policy text.
- The policy requirement is unclear.
- The PR has conflicting evidence.

## Final Compliance Review

Before calculating compliance:

- Confirm repository policy coding is complete.
- Confirm `AI Disclosure Required` is coded for each PR.
- Exclude or separately report bot PRs according to the study design.
- Separate `Positive`, `Negative`, `Ambiguous`, and `None`.
- Report ambiguous and unresolved cases separately.
- Preserve a reproducible link from each coded row to the PR URL and evidence text.
