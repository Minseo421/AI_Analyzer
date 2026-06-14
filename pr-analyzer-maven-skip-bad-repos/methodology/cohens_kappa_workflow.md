# Cohen's Kappa Reliability Workflow

This document explains the inter-rater reliability workflow for the AI-use disclosure compliance study.

The project investigates whether contributors to open source software pull requests disclose AI assistance when repository governance requires or encourages disclosure. Before using automated detection at full scale, researchers should verify that human coders can apply the coding framework consistently.

## Purpose

Inter-rater reliability is important because disclosure coding involves interpretation. Pull requests may contain explicit AI-use statements, negative disclosure responses, copied policy text, file names such as `CLAUDE.md`, or incidental mentions of AI tools. Researchers need evidence that two independent coders can interpret these cases consistently.

Human coding is still required even though the repository includes an automated detector. The detector can identify likely evidence and reduce search effort, but it cannot fully determine speaker intent, source context, policy applicability, or whether an AI-related phrase is a contributor disclosure rather than GitHub page chrome or documentation text.

Cohen's Kappa measures agreement beyond chance. It compares the observed agreement between two coders with the agreement expected if coders were assigning labels according to their overall label distributions. This is stronger than raw percent agreement because it accounts for agreement that may occur simply because one label is common.

Using Cohen's Kappa improves research validity by:

- Testing whether the coding categories are clear enough to apply consistently.
- Identifying ambiguous cases before full-scale coding.
- Supporting transparent reporting of human coding reliability.
- Creating a defensible consensus dataset for validating the automated detector.

## Overview of the Workflow

```text
Generate PR Sample
    ↓
Researcher A Codes PRs
    ↓
Researcher B Codes PRs
    ↓
Calculate Cohen's Kappa
    ↓
Review Disagreements
    ↓
Create Consensus Dataset
    ↓
Validate Automated Detector
```

Stages:

| Stage | Purpose |
| --- | --- |
| Generate PR Sample | Create a fixed set of eligible pull requests for both researchers to code. |
| Researcher A Codes PRs | First researcher independently labels disclosure presence and classification. |
| Researcher B Codes PRs | Second researcher independently labels the same PRs without discussing decisions. |
| Calculate Cohen's Kappa | Quantify agreement for disclosure presence and disclosure classification. |
| Review Disagreements | Inspect disagreement rows after kappa has been calculated. |
| Create Consensus Dataset | Resolve disagreements and produce the gold-standard human-coded labels. |
| Validate Automated Detector | Compare script output against the consensus dataset. |

## Sample Selection

The reliability sample should be drawn from repositories included in the study. The same PR sample must be reviewed by both researchers.

The implemented sample command reuses the existing PR collection logic:

- It reads repositories from `repos.txt`.
- It collects latest closed human PRs per repository.
- It excludes bots according to the current project bot heuristic.
- It uses deterministic ordering from the GitHub API collection path rather than random sampling.
- It assigns stable sample IDs as `Repo#PR`, for example `apache/airflow#12345`.

Example command:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --sample-for-kappa repos.txt 50 kappa_sample.csv
```

`kappa_sample.csv` contains one row per sampled PR:

| Column | Meaning |
| --- | --- |
| Sample ID | Stable unique identifier for matching coder labels. |
| Repo | Repository full name. |
| PR # | Pull request number. |
| PR URL | Browser URL for manual review. |
| Title | Pull request title. |
| Author | GitHub login of PR author. |
| Created Date | GitHub creation timestamp. |
| Closed Date | GitHub closed timestamp. |
| Merged Date | GitHub merged timestamp, blank if not merged. |
| Status | `Merged`, `Closed`, or `Unknown`. |
| Disclosure Text detected by script | Script-extracted candidate evidence, if any. |
| Script AI Disclosure Present | Script's preliminary `Yes` / `No` disclosure-present value. |
| Notes | Blank field for sample-level notes if needed. |

The script's detected text may help coders locate possible evidence, but coders must still verify the PR page manually when necessary.

## Independent Coding Rules

Researchers must:

- Work independently.
- Not discuss classifications before coding is complete.
- Review the actual PR page when necessary.
- Use the definitions in [`coding_framework.md`](coding_framework.md).
- Treat script output as evidence to inspect, not as a label to copy.
- Preserve the original `Sample ID` exactly.

### Disclosure Present

Allowed values:

| Value | Definition |
| --- | --- |
| Yes | The PR contains an observable AI-use disclosure or explicit no-AI statement. |
| No | No relevant AI disclosure or explicit no-AI statement is found. |

### Disclosure Classification

Allowed values:

| Value | Definition | Example |
| --- | --- | --- |
| Positive | Contributor states that AI was used or assisted the PR. | `I used ChatGPT to help generate test cases.` |
| Negative | Contributor states that AI was not used. | `Generative AI tooling used to co-author this PR? No` |
| Ambiguous | AI is mentioned but not clearly as contributor use or non-use. | `This updates CLAUDE.md` |
| None | No AI-related disclosure found. | No AI-related disclosure found in the PR record. |

Negative disclosures count as disclosure present because the contributor answered the disclosure question. They should not be counted as evidence that AI was used.

## Recording Labels

Each researcher records labels in a separate CSV file. The implemented interactive mode can create this file:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --code-kappa-sample kappa_sample.csv anna_labels.csv
```

The labels file includes:

```csv
Sample ID,Repo,PR #,PR URL,Disclosure Present,Disclosure Classification,Notes
apache/airflow#12345,apache/airflow,12345,https://github.com/apache/airflow/pull/12345,Yes,Negative,"AI disclosure checkbox marked No"
```

Important rules:

- Do not modify `Sample ID`.
- Do not merge two coders' labels into one file before calculating kappa.
- Use one labels file per researcher.
- If coding manually in a spreadsheet, keep the same column names.
- If a PR is skipped, record it later in a separate labels file or rerun coding with a new output path. The interactive mode will not overwrite an existing labels file.

## Calculating Cohen's Kappa

Cohen's Kappa uses:

- Observed agreement, `Po`: the proportion of matched PRs where both coders chose the same label.
- Expected agreement, `Pe`: the proportion of agreement expected by chance based on each coder's label distribution.

Formula:

```text
kappa = (Po - Pe) / (1 - Pe)
```

Plain-language interpretation:

- `Po` asks: how often did the coders agree?
- `Pe` asks: how often would they be expected to agree just because some labels are common?
- Kappa asks: how much better is the observed agreement than chance agreement?

Implemented command:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --calculate-kappa anna_labels.csv coworker_labels.csv kappa_results.csv
```

The command matches rows by `Sample ID` and calculates kappa for:

- `Disclosure Present`: `Yes` / `No`.
- `Disclosure Classification`: `Positive` / `Negative` / `Ambiguous` / `None`.

`kappa_results.csv` includes:

- Number of matched PRs.
- Raw agreement.
- Expected agreement.
- Cohen's Kappa.
- Confusion matrices.
- Missing labels.
- Disagreement rows.
- Interpretation note.

## Interpreting Kappa

Common interpretation guidelines:

| Kappa Range | Interpretation |
| --- | --- |
| `< 0.20` | Poor |
| `0.21-0.40` | Fair |
| `0.41-0.60` | Moderate |
| `0.61-0.80` | Substantial |
| `0.81-1.00` | Almost Perfect |

These ranges are guidelines rather than strict rules. Interpretation should consider the number of PRs, class imbalance, the ambiguity of the coding task, and the role of the reliability exercise in the broader study.

## Resolving Disagreements

Disagreements should be reviewed only after kappa has been calculated.

Resolution process:

1. Review disagreement rows together.
2. Open the PR URL and inspect the relevant evidence.
3. Refer back to [`coding_framework.md`](coding_framework.md).
4. Discuss the coding decision and identify the source of disagreement.
5. Update coding rules if recurring ambiguities are found.
6. Produce a final consensus classification.

The consensus dataset becomes the project's gold-standard dataset for the reliability sample. Do not replace the independent coder files; preserve them for auditability.

## Validating the Automated Detector

Detector validation is separate from Cohen's Kappa.

```text
Human Consensus Dataset
            ↓
Compare Against Script Output
            ↓
Measure:
- False positives
- False negatives
- Precision
- Recall
```

Cohen's Kappa measures human-human agreement. Detector validation measures script-human agreement against the consensus labels. Keeping these separate prevents the automated detector from influencing the reliability estimate.

Suggested detector validation terms:

| Term | Meaning |
| --- | --- |
| False positive | Script detected disclosure, but consensus says no disclosure is present. |
| False negative | Script missed a disclosure that consensus says is present. |
| Precision | Among script-positive rows, the proportion confirmed by consensus. |
| Recall | Among consensus-positive rows, the proportion detected by the script. |

## Threats to Validity

Important threats include:

- Ambiguous disclosures where AI is mentioned but contributor use or non-use is unclear.
- Repository-specific wording in PR templates or governance documents.
- False positives from GitHub UI, navigation text, or Copilot marketing text.
- False positives from filenames such as `CLAUDE.md`, `llms.txt`, or `copilot-instructions.md`.
- Human interpretation differences, especially for copied policy text or maintainer questions.
- Limited sample size, which can make kappa unstable.
- Class imbalance, especially if most PRs contain no disclosure.
- Missing PR comments or review comments if they are not collected.

Researchers should document these limitations when reporting reliability results.

## Recommended Workflow for This Project

1. Generate a sample of approximately 50 PRs.
2. Have both researchers independently code all PRs.
3. Calculate Cohen's Kappa.
4. Resolve disagreements.
5. Create a consensus dataset.
6. Compare the automated detector against the consensus dataset.
7. Proceed with full-scale data collection.

Recommended commands:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --sample-for-kappa repos.txt 50 kappa_sample.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --code-kappa-sample kappa_sample.csv anna_labels.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --code-kappa-sample kappa_sample.csv coworker_labels.csv
```

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --calculate-kappa anna_labels.csv coworker_labels.csv kappa_results.csv
```

## References

- Cohen, J. (1960). A coefficient of agreement for nominal scales. *Educational and Psychological Measurement*, 20(1), 37-46.
- McHugh, M. L. (2012). Interrater reliability: The kappa statistic. *Biochemia Medica*, 22(3), 276-282.
- Artstein, R., & Poesio, M. (2008). Inter-coder agreement for computational linguistics. *Computational Linguistics*, 34(4), 555-596.
- See also: [`coding_framework.md`](coding_framework.md) and [`validation_protocol.md`](validation_protocol.md).

## Assumptions Made

- The reliability sample is deterministic and based on latest closed human PRs collected by the existing analyzer.
- Bot PRs are excluded according to the current project methodology and implementation.
- Both researchers use the same `kappa_sample.csv`.
- Script evidence may be shown to coders, but coders independently verify evidence and assign labels.
- Consensus labels, not script labels, become the gold standard.

## Expected Input Files

- `repos.txt`: canonical repository URLs or `OWNER/REPO` entries.
- `kappa_sample.csv`: generated reliability sample.
- Two coder label files, such as `anna_labels.csv` and `coworker_labels.csv`.

## Related Methodology Documents

- [`coding_framework.md`](coding_framework.md)
- [`validation_protocol.md`](validation_protocol.md)
- [`methodology_workflow.md`](methodology_workflow.md)
- [`repository_analysis_framework.md`](repository_analysis_framework.md)
- [`workbook_mapping.md`](workbook_mapping.md)
