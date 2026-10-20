# Full repository analysis

This directory contains the datasets and analysis outputs supporting the study's final examination of **38 open-source repositories** and **41,338 eligible pull requests (PRs)**. The analyser identified AI-use disclosure evidence in **11,598 PRs (28.06%)**. These results are presented in the two researchers' final reports; their respective figures are preserved separately under [`figures/`](figures/README.md).

The full analysis collected closed, human-authored PRs within a rolling four-calendar-month window, including both merged and unmerged PRs. Collection began on **22 September 2026 at 09:58 UTC** and finished on 23 September. The analysis did not impose a per-repository PR cap.

## Data files

| File or directory | Purpose |
| --- | --- |
| [`repos.txt`](repos.txt) | Repository cohort selected for analysis. |
| [`pr_dataset_output.csv`](pr_dataset_output.csv) | PR-level metadata, saved disclosure decisions and matched evidence. |
| [`repo_detected_disclosure_aggregates.csv`](repo_detected_disclosure_aggregates.csv) | Eligible PR counts, detected disclosures and disclosure-type totals for each repository. |
| [`policy_tracker.csv`](policy_tracker.csv) | Policy-source inventory and visibility scores used in the quantitative analysis; distinct from the manual governance coding sheet. |
| [`repo_visibility_summary.csv`](repo_visibility_summary.csv) | Repository disclosure rates joined with policy visibility scores. |
| [`spearman_correlation.csv`](spearman_correlation.csv) | Saved Spearman correlation between policy visibility and observed disclosure rates. |
| [`OSS_38_repositories_verified.csv`](OSS_38_repositories_verified.csv) | Combined repository/governance analysis table. |
| [`OSS_38_Governance_Group_Statistics.csv`](OSS_38_Governance_Group_Statistics.csv) | Descriptive statistics for the governance group comparisons. |
| [`figures/`](figures/README.md) | Figures used in Anna's and Minseo's separate reports, organised by researcher. |
| [`historical_40/`](historical_40/) | Earlier 40-repository analysis outputs retained for research history; **not** the final dataset. |

Some CSVs contain metadata lines before their headers and quoted multi-line fields. For PR-level joins, identify records using repository and PR number.

## Analysis and interpretation

Each repository's observed disclosure rate is calculated as:

$$
\text{Disclosure rate} = \frac{\text{PRs with detected disclosure}}{\text{Eligible PRs analysed}} \times 100\%.
$$

The pooled disclosure rate uses totals across all eligible PRs, while repository-level comparisons treat repositories as separate observations. The recorded relationship between policy visibility (scores 0–3) and repository disclosure rate is **Spearman's $\rho = 0.6181$** ($p \approx 0.0000354$, $n=38$); this is an association, not evidence of causation.

A detected disclosure may state that AI **was used**, **was not used**, or contain an **ambiguous** AI-related declaration. The reported rate therefore measures **recorded disclosure evidence**, not the prevalence of AI-assisted contributions or verified compliance with repository policies. The archived PR-level subtype fields do not fully reconstruct the repository-level subtype totals; consult the aggregate CSV for those counts.

## Figures used in the reports

The [`figures/README.md`](figures/README.md) identifies the five figures provided for **Anna's report** and the six numbered figure exports provided for **Minseo's report**, with links to the corresponding image files. Some figures present the same underlying results in different forms; they are organised by **report authorship**, not as separate research samples. The final figure exports have been retained as evidence rather than presented as automatically reproducible charts.

## Recalculating preserved results

From `AI_Analyzer/pr_analyser/`, after building the Java analyser as described in its [README](../../pr_analyser/README.md):

```sh
mkdir -p target/reproduction
F=../research_compendium/full_repo_analysis

java -jar target/pr-analyzer-maven-1.0.0.jar --visibility-correlation \
  "$F/repo_detected_disclosure_aggregates.csv" "$F/policy_tracker.csv" \
  target/reproduction/visibility.csv target/reproduction/spearman.csv
```

The separate `scripts/verify_results.py` utility can audit saved totals and report tables if the required report file is available; see the analyser's documentation. These saved-data checks do **not** regenerate all final report figures or repeat historical GitHub collection. The existing historical plotting script applies to the earlier 40-repository analysis only.

For the wider reproduction workflow and evidence limitations, see [`../reproducibility/README.md`](../reproducibility/README.md).
