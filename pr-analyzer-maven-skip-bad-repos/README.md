# PR AI Disclosure Analyzer

This Java Maven project supports an academic research study on AI-use disclosure governance in open source software pull requests.

The tool collects empirical PR-level data for studying:

- AI-use disclosure compliance in OSS pull requests.
- Governance mechanisms around mandatory AI disclosure.
- Repository-level disclosure compliance rates.

The analyzer produces:

1. A PR-level dataset compatible with `pr_dataset.csv`.
2. An optional repository-level compliance summary.
3. The original `report.csv` output for debugging and audit checks.

Automated detection is only a first pass. Human validation is required before reporting final research results.

## Requirements

- Java 17.
- Maven.
- A GitHub token is recommended for larger samples because unauthenticated GitHub API requests are rate-limited.

Check installed versions:

```bash
java -version
mvn -version
```

Java class file version notes:

- Class file version `61.0` means Java 17.
- Class file version `52.0` means Java 8.
- `UnsupportedClassVersionError` usually means the project was built with Java 17 but is being run with an older Java runtime.

## Build

Clone or open this repository, then enter the Maven project folder:

```bash
cd pr-analyzer-maven-skip-bad-repos
mvn clean package
```

## Input Files

`repos.txt` should contain one canonical `OWNER/REPO` repository identifier per line:

```text
apache/airflow
apache/couchdb
apache/arrow
```

Do not use GitHub PR search URLs, policy-page URLs, file URLs, or URLs with query strings. The repository list can be regenerated from the policy tracker CSV's `Repository Link` column:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --repos-from-csv policy-tracker.csv repos.txt --replace
```

The import command normalizes GitHub repository links to `OWNER/REPO`, ignores blank rows, removes duplicates, preserves CSV order, reports invalid non-empty links, and prints repositories added or removed compared with the existing `repos.txt`.

Research workbook/schema files:

- `policy-tracker.csv` / `policy_tracker.csv` / `policy_tracker.xlsx`: repository-level manual governance coding, such as policy location, visibility, enforcement, and mandatory disclosure coding.
- `pr_dataset.csv` / `pr_dataset.xlsx`: source-of-truth PR-level research dataset schema.

The analyzer reads GitHub PR data directly. It does not automatically merge policy coding into PR rows; fields such as `AI Disclosure Required` still require manual policy coding.

## Run Commands

Analyze one PR and print a console report:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar https://github.com/OWNER/REPO/pull/NUMBER
```

Original/debug modes:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --latest https://github.com/OWNER/REPO 100 report.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --repos repos.txt 100 report.csv
```

Research dataset modes:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --latest-pr-dataset https://github.com/OWNER/REPO 100 pr_dataset_output.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --repos-pr-dataset repos.txt 100 pr_dataset_output.csv
```

Research dataset plus repository compliance summary:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --latest-pr-dataset https://github.com/OWNER/REPO 100 pr_dataset_output.csv repo_compliance_summary.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --repos-pr-dataset repos.txt 100 pr_dataset_output.csv repo_compliance_summary.csv
```

Repository list maintenance:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --repos-from-csv policy-tracker.csv repos.txt --replace
```

The numeric argument is the maximum number of eligible PRs to return per repository. A PR is eligible when it is closed, has `closed_at`, was closed on or after the rolling four-calendar-month cutoff, and is not bot-authored. The cutoff is calculated in UTC from the collection timestamp as collection instant minus four calendar months, and the boundary is inclusive (`closed_at >= cutoff`). The GitHub API returns closed PRs ordered by update time, so the analyzer may fetch multiple pages, filters by `closed_at` and bot status before applying the requested limit, then orders results locally by `closed_at` descending and PR number descending. Fewer than the requested number may be returned if a repository does not contain enough eligible PRs in the window.

Inter-rater reliability modes:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --sample-for-kappa repos.txt 50 kappa_sample.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --code-kappa-sample kappa_sample.csv anna_labels.csv
java -jar target/pr-analyzer-maven-1.0.0.jar --code-kappa-sample kappa_sample.csv minseo_labels.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --calculate-kappa anna_labels.csv minseo_labels.csv kappa_results.csv
```

The kappa sample command reuses the same four-calendar-month `closed_at` eligibility rule and local closure-time ordering as the repository collection logic. Repository selection is random unless `KAPPA_SAMPLE_SEED` is set; the selected PR rows are deterministic for the fetched GitHub data and collection timestamp. `Sample ID` is stable as `Repo#PR`. The interactive coding command refuses to overwrite an existing labels file.

Human consensus and detector validation modes:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --create-consensus anna_labels.csv minseo_labels.csv consensus_labels.csv
```

Researchers should manually resolve any `Needs Resolution` rows in `consensus_labels.csv` by filling in the blank consensus fields and changing `Agreement Status` to `Resolved`.

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --validate-detector kappa_sample.csv consensus_labels.csv detector_validation.csv
```

Detector validation is separate from Cohen's Kappa: Kappa compares human coders with each other, while detector validation compares `Script AI Disclosure Present` from `kappa_sample.csv` against the completed human consensus labels.

To re-run the current detector rules against the same fixed kappa sample without selecting new PRs:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --reanalyze-kappa-sample kappa_sample.csv kappa_sample_reanalyzed.csv
```

Then compare the updated detector results against the same human consensus:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --validate-detector kappa_sample_reanalyzed.csv consensus_labels.csv detector_validation_reanalyzed.csv
```

Re-analysis preserves the original PR sample, row order, and `Sample ID` values, so changes in precision, recall, false positives, or false negatives are attributable to detector rule changes rather than a different set of pull requests.

## Output Files

### `pr_dataset_output.csv`

One row per reviewed PR. The header matches `pr_dataset.csv`.

Key columns:

- `Repo`: repository full name.
- `PR #`: GitHub pull request number.
- `PR URL`: browser URL for manual validation.
- `Title`: PR title from the GitHub API.
- `Author`: PR author login.
- `Created Date`: GitHub `created_at` timestamp.
- `Closed Date`: GitHub `closed_at` timestamp.
- `Merged Date`: GitHub `merged_at` timestamp, blank if not merged.
- `Bot`: `Yes` or `No` based on the current bot heuristic.
- `Status`: `Merged`, `Closed`, or `Unknown`.
- `AI Disclosure Required`: currently `MANUAL_REVIEW_REQUIRED`; fill from policy coding.
- `AI Disclosure Present`: `Yes` when disclosure-like text was detected, otherwise `No`.
- `Disclosure Classification`: `MANUAL_REVIEW_REQUIRED` for detected text, `None` when no disclosure is detected.
- `Disclosure Text`: extracted evidence for manual review.

Negative statements such as `No AI used` still count as `AI Disclosure Present = Yes` because the contributor answered the disclosure question. `Disclosure Text` is evidence for review, not final proof.

### `repo_compliance_summary.csv`

One row per repository when a summary output path is supplied.

Columns:

- `Repo`: repository full name.
- `Eligible PRs Reviewed`: human PR rows included in the generated PR dataset for that repository.
- `Bot PRs Excluded`: closed PRs skipped by the bot heuristic while collecting the sample.
- `AI Disclosure Present Count`: rows where disclosure-like text was detected.
- `Positive Disclosure Count`: preliminary detector count for possible positive disclosures.
- `Negative Disclosure Count`: preliminary detector count for possible negative disclosures.
- `Ambiguous Disclosure Count`: preliminary detector count for ambiguous AI disclosure mentions.
- `No Disclosure Count`: eligible reviewed rows with no detected disclosure.
- `Manual Review Required Count`: currently all eligible reviewed rows.
- `Disclosure Rate`: `AI Disclosure Present Count / Eligible PRs Reviewed`.

Classification counts are preliminary and should be treated as manual-review queues until validated.

Example summary row:

```csv
"owner/repo","3","2","2","1","1","0","1","3","0.6667"
```

### `report.csv`

The older/audit output. It is preserved for debugging and includes extra technical fields such as GitHub user type, boolean disclosure flag, HTML scrape success, and HTML scrape error.

### Inter-rater reliability files

`kappa_sample.csv` is a fixed PR sample for independent coding. It includes `Sample ID`, PR metadata, script-detected disclosure evidence, script disclosure-present value, and notes.

`anna_labels.csv` and `minseo_labels.csv` are coder-specific labels created from the same sample. Each row records:

- `Sample ID`
- `PR URL`
- `Disclosure Present`: `Yes` or `No`
- `Disclosure Classification`: `Positive`, `Negative`, `Ambiguous`, or `None`
- `Notes`

`kappa_results.csv` compares two coder files by `Sample ID` and reports matched PR count, raw agreement, Cohen's Kappa, confusion matrices, missing labels, and disagreement rows.

## Methodology Notes

- Bot PRs are excluded from the generated PR dataset by the current implementation. `Bot PRs Excluded` records how many closed PRs were skipped during collection.
- The compliance denominator is eligible human PRs reviewed. Eligibility uses GitHub `closed_at`, not `created_at`, `updated_at`, or `merged_at`; both merged and unmerged closed PRs are eligible. Filtering by bot status and the inclusive four-calendar-month `closed_at` cutoff occurs before the requested limit is applied, then rows are ordered locally by closure time. Exact-PR reanalysis, targeted analysis, retry, consensus, and validation commands operate on specifically named/sample PRs and do not reapply dataset-selection eligibility.
- AI disclosure detection is not fully reliable without human validation.
- GitHub page chrome, navigation text, Copilot marketing text, and filenames such as `CLAUDE.md` should not be counted as contributor disclosure.
- Manual review is required before final disclosure compliance rates are reported.

Detailed methodology documents:

- [`methodology/data_dictionary.md`](methodology/data_dictionary.md)
- [`methodology/workbook_mapping.md`](methodology/workbook_mapping.md)
- [`methodology/repository_analysis_framework.md`](methodology/repository_analysis_framework.md)
- [`methodology/coding_framework.md`](methodology/coding_framework.md)
- [`methodology/validation_protocol.md`](methodology/validation_protocol.md)
- [`methodology/cohens_kappa_workflow.md`](methodology/cohens_kappa_workflow.md)
- [`methodology/methodology_workflow.md`](methodology/methodology_workflow.md)

## Recommended Pilot Workflow

Start with a small run to confirm Java, Maven, and GitHub access:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --latest-pr-dataset https://github.com/apache/airflow 5 test.csv
```

Then run a larger single-repo pilot and inspect the output quality:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --latest-pr-dataset https://github.com/apache/airflow 30 airflow_pr_dataset_output.csv airflow_repo_compliance_summary.csv
```

Only after checking the pilot output should you run the full `repos.txt` dataset:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --repos-pr-dataset repos.txt 100 pr_dataset_output.csv repo_compliance_summary.csv
```

## Detector Harness

Run the lightweight detector and summary harness:

```bash
javac -encoding UTF-8 -d /tmp/pr-analyzer-test-classes src/main/java/com/example/aichecker/*.java src/test/java/com/example/aichecker/*.java
java -cp /tmp/pr-analyzer-test-classes com.example.aichecker.AiDisclosureDetectorHarness
```

The harness covers explicit positive and negative disclosure examples, GitHub Copilot page chrome, `CLAUDE.md` filename-only mentions, ambiguous disclosure text, and summary count calculation.

## Inter-Rater Reliability Workflow

Use this workflow before final coding to estimate human-human agreement. The script comparison is separate from inter-rater reliability: script-detected text may help reviewers find evidence, but Cohen's Kappa should compare independent human labels.

1. Generate a fixed sample:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --sample-for-kappa repos.txt 50 kappa_sample.csv
```

2. Give the same `kappa_sample.csv` to two coders. Coders must work independently and should open each `PR URL` to verify the context manually.

3. Each coder creates their own labels file:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --code-kappa-sample kappa_sample.csv anna_labels.csv
java -jar target/pr-analyzer-maven-1.0.0.jar --code-kappa-sample kappa_sample.csv minseo_labels.csv
```

The interactive prompt accepts `Yes` or `No` for disclosure present and `Positive`, `Negative`, `Ambiguous`, or `None` for classification. Enter `SKIP` to leave a PR out of that coder file and return to it later in a separate output file.

4. Calculate agreement:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --calculate-kappa anna_labels.csv minseo_labels.csv kappa_results.csv
```

5. Review disagreements after kappa is calculated. Resolve disagreements by discussion or third-reviewer adjudication, then create the consensus gold-standard dataset. Do not use the script to replace human judgement.

## Troubleshooting

`UnsupportedClassVersionError`:

- You are probably running Java 8 or another older runtime.
- Install/use Java 17 and confirm with `java -version`.

`mvn: command not found`:

- Maven is not installed or not on your `PATH`.
- Install Maven, then confirm with `mvn -version`.

GitHub rate limit errors:

- Set `GITHUB_TOKEN`.
- Re-run the same command after exporting the token.

Network stalls or interrupted long runs:

- Each GitHub client has a 15-second connection timeout by default.
- Each GitHub request has a 60-second full-request timeout by default.
- Override with `GITHUB_CONNECT_TIMEOUT_SECONDS` or `GITHUB_REQUEST_TIMEOUT_SECONDS` if needed.
- Multi-repository `--repos` and `--repos-pr-dataset` runs append and flush rows incrementally. Re-running the same command with the same output file resumes by skipping completed `Repo#PR` rows already present in that output. During resume, the optional repository compliance summary is not rewritten because already completed rows are not re-analysed in memory.
- Exact-PR reanalysis and retry commands keep their fixed-sample behavior.

Empty output:

- Confirm the repository URL is valid and public.
- Confirm the repository has closed human PRs.
- Check whether the requested count is too high for the available closed human PRs.
- Remember that bot PRs are skipped.

Malformed `repos.txt` entries:

- Use one canonical repository URL per line.
- Avoid PR URLs, search URLs, query strings, and browser filter URLs.
- Blank lines and lines beginning with `#` are ignored.

False positives from scraped HTML:

- The detector filters common GitHub page chrome and marketing text, but manual review is still required.
- Do not treat generic GitHub Copilot navigation/marketing text as contributor disclosure.

## Human Validation Checklist

Before reporting results:

- Verify PR URLs open correctly.
- Confirm PRs are in the intended study window.
- Confirm bots are excluded or flagged correctly.
- Review every detected AI disclosure.
- Review non-detected rows when required by the sampling/validation protocol.
- Manually classify each row as `Positive`, `Negative`, `Ambiguous`, or `None`.
- Fill or merge policy fields such as `AI Disclosure Required`.
- Calculate final compliance only after manual validation.
