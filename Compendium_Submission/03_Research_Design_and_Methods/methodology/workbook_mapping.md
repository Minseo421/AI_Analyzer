# Workbook Mapping

Source schemas inspected:

- `policy-tracker.csv`
- `pr_dataset.csv`

The original source CSV files are treated as schema references and were not modified.

## Current Script Output

Existing `CsvWriter.write(...)` output:

`Repository`, `Pull request number`, `URL`, `Author`, `State`, `Closed`, `Human author`, `GitHub user type`, `AI Disclosure`, `Merged`, `AI Disclosure evidence`, `HTML scrape success`, `HTML scrape error`

New `CsvWriter.writePrDataset(...)` output exactly matches `pr_dataset.csv`:

`Repo`, `PR #`, `PR URL`, `Title`, `Author`, `Created Date`, `Closed Date`, `Merged Date`, `Bot`, `Status`, `AI Disclosure Required`, `AI Disclosure Present`, `Disclosure Classification`, `Disclosure Text`

## `policy-tracker.csv`

| Workbook column | Can script generate? | Manual coding required? | Existing code field mapping | Missing in current implementation? | Notes |
| --- | --- | --- | --- | --- | --- |
| Repo | Yes | No, if using `owner/repo` naming | `PrReportRow.repository()` | No | Generated from the repository being analyzed. |
| Notes | No | Yes | None | Yes | Researcher notes require manual coding. |
| Repository Link | Partially | Usually no | Derived from `PrReportRow.repository()` as `https://github.com/{owner}/{repo}` or from `PrReportRow.url()` | Yes | Not emitted by current code. |
| Stars | No | Yes, unless repository metadata fetching is added | None | Yes | Current GitHub API client only fetches PR data. |
| License | No | Yes, unless repository metadata fetching is added | None | Yes | Current GitHub API client does not fetch license metadata. |
| Policy URL | No | Yes | None | Yes | Requires repository policy discovery and validation. |
| Policy Location | No | Yes | None | Yes | Requires manual or separate policy-file inspection. |
| Visibility Score | No | Yes | None | Yes | Research coding field. |
| Enforcement Score | No | Yes | None | Yes | Research coding field. |
| PR template | No | Yes | None | Yes | Could be script-assisted later, but not currently fetched. |
| CI Check | No | Yes | None | Yes | Could be script-assisted later, but not currently fetched. |
| Mandatory Disclosure | No | Yes | None | Yes | Policy interpretation field. |
| Transparency | No | Yes | None | Yes | Research coding field. |
| Accountability | No | Yes | None | Yes | Research coding field. |
| Reviewer Burden | No | Yes | None | Yes | Research coding field. |
| Quality | No | Yes | None | Yes | Research coding field. |
| Trust | No | Yes | None | Yes | Research coding field. |

## `pr_dataset.csv`

| Workbook column | Can script generate? | Manual coding required? | Existing code field mapping | Missing in current implementation? | New dataset output behavior |
| --- | --- | --- | --- | --- | --- |
| Repo | Yes | No | `PrReportRow.repository()` | No | Writes repository full name. |
| PR # | Yes | No | `PrReportRow.pullRequestNumber()` | No | Writes GitHub PR number. |
| PR URL | Yes | No | `PrReportRow.url()` | No | Writes GitHub PR browser URL. |
| Title | Yes | No | `PrReportRow.title()` from GitHub `title` | Was missing | Writes PR title. |
| Author | Yes | No | `PrReportRow.author()` | No | Writes GitHub login. |
| Created Date | Yes | No | `PrReportRow.createdAt()` from GitHub `created_at` | Was missing | Writes GitHub timestamp. |
| Closed Date | Yes | No | `PrReportRow.closedAt()` from GitHub `closed_at` | Was missing | Writes GitHub timestamp or blank. |
| Merged Date | Yes | No | `PrReportRow.mergedAt()` from GitHub `merged_at` | Was missing | Writes GitHub timestamp or blank. |
| Bot | Yes | Validate if needed | Derived from `!PrReportRow.humanAuthor()` | No | Writes `Yes` or `No` using existing bot heuristic. |
| Status | Yes | Validate if needed | Derived from `PrReportRow.merged()`, `PrReportRow.closed()`, `PrReportRow.state()` | Partially | Writes `Merged`, `Closed`, `Open`, raw state, or `UNKNOWN`. |
| AI Disclosure Required | No | Yes | None | Yes | Writes `MANUAL_REVIEW_REQUIRED` because this depends on policy coding. |
| AI Disclosure Present | Yes | Validate before final analysis | `PrReportRow.aiDisclosure()` | No | Writes `Yes` or `No` from detector output. |
| Disclosure Classification | Partially | Yes for disclosed rows | Derived from `PrReportRow.aiDisclosure()` | Partially | Writes `None` when no disclosure is detected; writes `MANUAL_REVIEW_REQUIRED` when AI-related text is detected. |
| Disclosure Text | Yes, when detected | Validate before final analysis | `PrReportRow.aiDisclosureEvidence()` | No | Writes extracted detector evidence for disclosed rows; blank otherwise. |

## Assumptions

- `policy-tracker.csv` is the intended policy source file even though the task text used `policy_tracker.csv`.
- PR dates use GitHub API timestamps directly rather than truncating to `YYYY-MM-DD`, preserving the source-of-truth API value.
- The script can detect AI-related text, but it cannot reliably decide whether the mention is an affirmative disclosure, a negative disclosure, a policy quote, or incidental text. Detected rows therefore keep `Disclosure Classification` as `MANUAL_REVIEW_REQUIRED`.
- `AI Disclosure Required` is a policy-level field and must be merged or coded from `policy-tracker.csv`; the PR analyzer does not infer it from PR content.
