# Methodology Workflow

This document defines the research workflow for studying AI-use disclosure governance in open source software pull requests.

The workflow separates manual repository governance analysis from PR-level data collection and coding.

```text
Repository Analysis Phase
    ↓
Policy Coding
    ↓
PR Collection
    ↓
PR Coding
    ↓
Compliance Analysis
```

## Workbook Schema Note

The current workbooks are the source of truth for active data entry:

- `policy_tracker.xlsx` / `policy-tracker.csv`: repository-level governance dataset.
- `pr_dataset.xlsx` / `pr_dataset.csv`: pull-request-level compliance dataset.

The PR analyzer script generates PR-level rows and optional repository compliance summaries. It does not generate repository-level governance coding such as visibility, enforcement, transparency, accountability, or legal/licensing rationale fields. Those fields are manually coded by researchers using `repository_analysis_framework.md`.

## 1. Repository Analysis Phase

| Item | Description |
| --- | --- |
| Purpose | Select repositories and collect repository-level evidence of AI-use disclosure governance. |
| Inputs | Candidate repository list, repository metadata, contribution guides, PR templates, issue templates, CI workflows, policy pages, maintainer documentation. |
| Outputs | Included repository list and policy evidence in `policy_tracker.xlsx` or `policy-tracker.csv`. |
| Relevant spreadsheet fields | `Repo`, `Repository Link`, `Stars`, `License`, `Policy URL`, `Policy Location`, `Notes`. |
| Human validation required | Confirm each repository is public, active, in scope, and has relevant contribution-governance material. |
| Limitations | Repository selection may overrepresent visible or popular projects. Policies may appear outside standard files or change over time. |

Recommended inclusion criteria:

- Repository is public and open source.
- Repository accepts external pull requests.
- Repository has observable governance artifacts such as `CONTRIBUTING.md`, PR templates, issue templates, CI checks, maintainer comments, or policy pages.
- Repository has enough recent PR activity to support compliance analysis.

Manual analysis instructions:

- Use canonical repository URLs.
- Record stable policy URLs wherever possible.
- Do not rely on the PR analysis script for repository governance coding.

## 2. Policy Coding

| Item | Description |
| --- | --- |
| Purpose | Convert collected policy evidence into comparable repository-level governance variables. |
| Inputs | Collected policy text, workflow evidence, PR templates, CI checks, maintainer review evidence. |
| Outputs | Coded repository-level fields in `policy_tracker.xlsx` or `policy-tracker.csv`. |
| Relevant spreadsheet fields | `Policy Location`, `Visibility Score`, `Enforcement Score`, `PR template`, `CI Check`, `Mandatory Disclosure`, `Transparency`, `Accountability`, `Reviewer Burden`, `Quality`, `Trust`, `Legal`. |
| Human validation required | At least one researcher should verify each coded policy. Borderline cases should receive notes. |
| Limitations | Coding involves judgment. A high visibility or enforcement score does not prove actual contributor compliance. |

Use `repository_analysis_framework.md` for repository-level definitions, allowed values, examples, and manual analysis instructions.

## 3. PR Collection

| Item | Description |
| --- | --- |
| Purpose | Collect pull requests used to observe contributor compliance with AI-use disclosure policies. |
| Inputs | Repository list, target sample size, date window, GitHub API responses. |
| Outputs | PR rows in `pr_dataset.xlsx`, `pr_dataset_output.csv`, or an intermediate CSV for manual import. |
| Relevant spreadsheet fields | `Repo`, `PR #`, `PR URL`, `Title`, `Author`, `Created Date`, `Closed Date`, `Merged Date`, `Bot`, `Status`. |
| Human validation required | Verify the sample frame, date window, bot filtering, and PR status interpretation. |
| Limitations | GitHub API data changes over time. PR edits, deleted comments, force pushes, and changed templates can affect observability. |

The collection script should record, at minimum:

- Repository name.
- PR number.
- PR URL.
- PR title.
- Contributor login.
- Created date.
- Closed or merged date.
- Merged/closed status.
- Bot status or bot exclusion count.

Bot handling:

- The current script filters bot PRs out of generated PR dataset rows.
- `repo_compliance_summary.csv` records `Bot PRs Excluded`.
- Researchers should review borderline accounts and keep the denominator definition consistent.

## 4. PR Coding

| Item | Description |
| --- | --- |
| Purpose | Code whether each reviewed PR contains an observable AI-use disclosure or explicit no-AI statement. |
| Inputs | PR body, PR template checklist, PR comments, review comments when available, PR URL, detected text. |
| Outputs | Validated PR-level disclosure fields in `pr_dataset.xlsx` or companion review columns. |
| Relevant spreadsheet fields | `AI Disclosure Required`, `AI Disclosure Present`, `Disclosure Classification`, `Disclosure Text`, `Disclosure Location`, `Confidence`, `Manual Review Required`, `Notes`. |
| Human validation required | Required for all detected positive, negative, and ambiguous cases; recommended for all rows used in final quantitative claims. |
| Limitations | Automated detection cannot reliably infer intent. Mentions of files such as `CLAUDE.md`, docs for AI tools, or package names are not necessarily contributor disclosures. |

Use `coding_framework.md` for PR-level coding definitions and `validation_protocol.md` for review rules.

Important PR coding rules:

- Negative disclosures such as `No AI was used` count as `AI Disclosure Present = Yes`.
- GitHub page chrome, navigation text, Copilot marketing text, and filenames such as `CLAUDE.md` should not be counted as contributor disclosure.
- Script-generated `MANUAL_REVIEW_REQUIRED` values are placeholders until human validation.

## 5. Compliance Analysis

| Item | Description |
| --- | --- |
| Purpose | Measure whether contributors comply with mandatory AI-use disclosure policies. |
| Inputs | Validated PR-level disclosure fields and manually coded repository-level policy fields. |
| Outputs | Per-repository and cross-repository compliance metrics. |
| Relevant spreadsheet fields | `Mandatory Disclosure`, `AI Disclosure Required`, `AI Disclosure Present`, `Disclosure Classification`, `Merged Date`, `Closed Date`, `Bot`. |
| Human validation required | Confirm denominator and numerator definitions before reporting results. |
| Limitations | Absence of disclosure is not proof that AI was not used. Compliance means observable compliance with disclosure policy, not actual AI-use prevalence. |

Preliminary script-generated calculation:

- `Eligible PRs Reviewed`: human PR rows included in the generated PR dataset for that repository. The input collection should already be limited to the approved eligibility window, such as the 3-month span.
- `Bot PRs Excluded`: closed PRs skipped by the script's bot heuristic while collecting the human PR sample.
- `AI Disclosure Present Count`: rows where disclosure-like text was detected.
- `Compliance Rate = AI Disclosure Present Count / Eligible PRs Reviewed`.
- `Positive Disclosure Count`, `Negative Disclosure Count`, and `Ambiguous Disclosure Count` are preliminary detector categories until manually validated.
- `Manual Review Required Count` is set to the number of eligible PRs reviewed because final compliance rates require validation.

Final research calculation:

- Denominator: eligible human PRs in scope where disclosure was required by repository policy.
- Numerator: denominator rows with validated `AI Disclosure Present = Yes`.
- Negative disclosures count as disclosure present because the contributor answered the disclosure requirement.
- Ambiguous cases should be reported separately unless manually recoded by consensus.
- Final compliance rates should be calculated only after repository policy coding and PR-level validation are complete.

## Cross-Repository Comparison

After repository analysis and PR validation are complete, compare governance variables with observed PR-level compliance.

Recommended comparisons:

- Mandatory vs non-mandatory disclosure policies.
- High vs low visibility scores.
- High vs low enforcement scores.
- PR-template mechanisms vs documentation-only policies.
- Merged vs closed-unmerged PRs.

Human validation required:

- Review outliers and projects with small PR samples before drawing comparative claims.
- Avoid interpreting correlation as causation; projects differ in community norms, contributor base, policy age, and PR workflow maturity.
