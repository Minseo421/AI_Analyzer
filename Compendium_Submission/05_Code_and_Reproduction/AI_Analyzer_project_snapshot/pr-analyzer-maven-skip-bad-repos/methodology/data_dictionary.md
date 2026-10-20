# Data Dictionary

This document describes every column currently present in `policy_tracker.xlsx` and `pr_dataset.xlsx`.

## Schema Mismatch Note

The current workbook schemas do not include every field needed by the full research design.

Missing from `pr_dataset.xlsx`:

- PR URL.
- PR title.
- PR created date and separate closed/merged date.
- Author appears bot.
- External contributor.
- Disclosure classification.
- Disclosure text.
- Disclosure location.
- Confidence.
- Manual review required.
- Notes.

These fields should be added to the workbook or stored in a companion reviewed CSV before final analysis.

## `policy_tracker.xlsx`

Sheet: `Policy Tracker`

| Column name | Meaning | Allowed values | Example value | Source |
| --- | --- | --- | --- | --- |
| Repo | Repository or project name being coded. | Repository name or `owner/repo`; use a consistent naming convention. | `Apache Airflow` | Manual |
| Stars | Approximate GitHub star count at time of collection. | Number or compact string such as `40k`; record collection date separately if possible. | `40k` | Manual or script-generated |
| Policy URL | Stable URL to the governance policy evidence. | URL, `link`, or blank if none found. Prefer a full URL. | `https://github.com/apache/airflow/blob/main/CONTRIBUTING.rst` | Manual |
| Policy Location | File, page, or workflow location where the policy appears. | `CONTRIBUTING.md`, `README.md`, `PULL_REQUEST_TEMPLATE.md`, `docs/`, `CI workflow`, `None found`, or free text. | `CONTRIBUTING.md` | Manual |
| Visibility Score | Score for how visible the policy is to contributors. | `0`, `1`, `2`, `3`, `4`. | `4` | Manual |
| Enforcement Score | Score for how strongly the policy is operationalized or enforced. | `0`, `1`, `2`, `3`, `4`. | `3` | Manual |
| PR Template | Whether the policy appears in or is supported by a PR template. | `Yes`, `No`, `Unclear`. | `Yes` | Manual |
| CI Check | Whether a CI/app/check appears to enforce or prompt disclosure. | `Yes`, `No`, `Unclear`. | `No` | Manual |
| Mandatory Disclosure | Whether AI-use disclosure is mandatory for contributors. | `Yes`, `No`, `Unclear`. | `Yes` | Manual |
| Transparency | Policy motivation flag: transparency about AI assistance. | `1` present, `0` absent, blank if uncoded. | `1` | Manual |
| Accountability | Policy motivation flag: contributor responsibility for AI-assisted work. | `1` present, `0` absent, blank if uncoded. | `1` | Manual |
| Reviewer Burden | Policy motivation flag: reducing reviewer workload or improving reviewability. | `1` present, `0` absent, blank if uncoded. | `1` | Manual |
| Quality | Policy motivation flag: code quality, correctness, maintainability, or testing. | `1` present, `0` absent, blank if uncoded. | `1` | Manual |
| Trust | Policy motivation flag: trust, provenance, authorship, or traceability. | `1` present, `0` absent, blank if uncoded. | `0` | Manual |
| Legal | Policy motivation flag: legal, licensing, copyright, or IP risk. | `1` present, `0` absent, blank if uncoded. | `0` | Manual |

## `pr_dataset.xlsx`

Sheet: `Pull Request Dataset`

| Column name | Meaning | Allowed values | Example value | Source |
| --- | --- | --- | --- | --- |
| Repo | Repository or project name associated with the PR. | Must match `policy_tracker.xlsx` naming convention where possible. | `Airflow` | Script-generated or manual |
| PR # | Pull request number in the repository. | Integer. | `45123` | Script-generated |
| Date | Date associated with the PR sample. The team must define whether this means created date, closed date, or merged date. | ISO date `YYYY-MM-DD` preferred. | `2026-05-10` | Script-generated or manual |
| Contributor | GitHub login of the PR author. | GitHub username; blank only if unavailable. | `user123` | Script-generated |
| AI Disclosure Required | Whether the repository policy required AI-use disclosure for this PR. | `Yes`, `No`, `Unclear`, `Not applicable`. | `Yes` | Manual or script-assisted from policy dataset |
| AI Disclosure Present | Whether a disclosure or explicit no-AI statement is present. | `Yes`, `No`, `Unclear`. | `Yes` | Script-generated then manually validated |
| Explicit AI Mention | Exact text or summary of the AI-related disclosure/mention. | Free text; exact phrase preferred. | `ChatGPT used for tests` | Script-generated then manually validated |
| Merged | Whether the PR was merged. | `Yes`, `No`, `Unclear`. | `Yes` | Script-generated |
| Closed | Whether the PR was closed. | `Yes`, `No`, `Unclear`. | `No` | Script-generated |

## Recommended Additional PR Columns

These fields are required by the research design but are not currently in `pr_dataset.xlsx`.

| Column name | Meaning | Allowed values | Example value | Source |
| --- | --- | --- | --- | --- |
| PR URL | Browser URL for the PR. | URL. | `https://github.com/apache/airflow/pull/45123` | Script-generated |
| PR Title | PR title. | Free text. | `Fix scheduler test failure` | Script-generated |
| Created Date | PR creation date. | ISO date or timestamp. | `2026-05-10` | Script-generated |
| Closed/Merged Date | Date PR was closed or merged. | ISO date or timestamp, blank if open and out of scope. | `2026-05-12` | Script-generated |
| Author Appears Bot | Whether the author appears to be automated. | `Yes`, `No`, `Unclear`. | `No` | Script-generated then manually validated |
| External Contributor | Whether author appears external to project maintainers. | `Yes`, `No`, `Unclear`. | `Yes` | Script-assisted/manual |
| Disclosure Classification | Disclosure category. | `Positive`, `Negative`, `Ambiguous`, `None`. | `Positive` | Script-generated then manually validated |
| Disclosure Text | Exact disclosure phrase or extracted evidence. | Free text. | `Assisted-by: Claude` | Script-generated then manually validated |
| Disclosure Location | Where disclosure evidence appears. | `PR body`, `PR template checklist`, `PR comment`, `Review comment`, `Commit message`, `Maintainer comment`, `Other`, `Not found`. | `PR body` | Script-generated then manually validated |
| Confidence | Confidence in disclosure classification. | `High`, `Medium`, `Low`. | `High` | Script-generated then manually validated |
| Manual Review Required | Whether row requires human validation before final analysis. | `Yes`, `No`. | `Yes` | Script-generated then manually validated |
| Notes | Researcher notes and uncertainty. | Free text. | `AI mention is a file name, not disclosure.` | Manual |
