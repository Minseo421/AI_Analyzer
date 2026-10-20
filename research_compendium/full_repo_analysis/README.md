# Full repository analysis

The final evidence contains **41,338 unique PRs from 38 repositories**, with 11,598 detector-observed disclosures (28.06%). [repos.txt](repos.txt) identifies the purposively selected cohort of repositories with AI-related contribution policies. A complete discovery/screening log is unavailable.

The PR CSV preamble records collection start `2026-09-22T09:58:43.766425Z` and completion `2026-09-23T08:14:59.763018Z`. Eligibility uses closed human-authored PRs, inclusive `closed_at` boundaries from 22 May to the start timestamp, including merged and unmerged PRs. Later October edits are not dated by that September preamble. Current collection has no per-repository cap; older pilot data is kept separate.

| File | Contents and relationships |
| --- | --- |
| [pr_dataset_output.csv](pr_dataset_output.csv) | One row per PR: Repo, PR number/URL, title, author, creation/closure/merge timestamps, Bot, Status, disclosure requirement/presence/classification and matched text |
| [repo_detected_disclosure_aggregates.csv](repo_detected_disclosure_aggregates.csv) | Repository totals, bot exclusions, presence/subtype counts and rounded rate; originally named repo_compliance_summary.csv |
| [policy_tracker.csv](policy_tracker.csv) | Original policy-source inventory and visibility input, including blank export rows; distinct from the canonical governance sheet |
| [repo_visibility_summary.csv](repo_visibility_summary.csv) | Repository rates joined to policy visibility; matching status, denominator and exclusions |
| [spearman_correlation.csv](spearman_correlation.csv) | Saved visibility–rate correlation: rho 0.6181438854, p 0.0000354223, n=38 |
| [OSS_38_repositories_verified.csv](OSS_38_repositories_verified.csv) | Existing combined repository/governance analysis table from 17 October |
| [OSS_38_Governance_Group_Statistics.csv](OSS_38_Governance_Group_Statistics.csv) | Existing grouped descriptive results from that analysis |

Parse CSV records, not physical lines: evidence text contains newlines. Skip the main dataset/aggregate metadata preamble to the `Repo` header. Join PRs by case-normalized `Repo#PR`, and repository tables by `Repo` or `Repository`. Preserve source spelling and values.

A repository's observed rate is `AI Disclosure Present Count / Eligible PRs Reviewed`. The pooled rate uses summed counts; governance group means are unweighted means of repository rates. Spearman uses average ranks for ties; its p-value is an approximation for this sample size. Visibility is the recorded 0–3 scale, not the obsolete 0–4 framework.

All PR-level `AI Disclosure Required` values remain `MANUAL_REVIEW_REQUIRED`. Classification contains 10,247 `MANUAL_REVIEW_REQUIRED`, 930 `possible_positive`, 99 `possible_negative`, 322 `possible_ambiguous` and 29,740 `None`. Repository subtype totals are 9,166 positive, 1,785 negative and 647 ambiguous, but the full row-level subtype assignment is unavailable. These outputs measure observed declarations, including non-use statements, not verified policy compliance or actual AI use.

## Recalculate saved results

From the [built analyser directory](../../pr-analyzer-maven-skip-bad-repos/README.md):

```sh
mkdir -p target/reproduction
F=../Compendium_Submission/full_repo_analysis
java -jar target/pr-analyzer-maven-1.0.0.jar --visibility-correlation \
  "$F/repo_detected_disclosure_aggregates.csv" "$F/policy_tracker.csv" \
  target/reproduction/visibility.csv target/reproduction/spearman.csv
python3 scripts/verify_results.py \
  --report ../Compendium_Submission/reports/Minseo_Kim_Final_P4P.docx \
  > target/reproduction/results.json
```

The Python verifier checks PR/repository totals, validation outcomes, governance group statistics, rho and numeric cells in Minseo's five Results tables. It uses the standard library; SciPy, if installed, additionally cross-checks p. It was written as an audit utility, not recovered as the original report-generation workflow.

## Figures and historical analysis

[figures/figure_index.csv](figures/figure_index.csv) maps twelve preserved PNGs to Minseo's DOCX media and SHA-256 hashes. [Main_Figures](figures/Main_Figures/) and [Appendix_Figures](figures/Appendix_Figures/) also contain SVG/PDF exports. [report_evidence.json](figures/report_evidence.json) consolidates the existing table/figure mappings, calculations and historical verification records. Original paths in that record are provenance; `current_path` fields identify relocated inputs. Anna's report figures are preserved in [Anna_Figures](figures/Anna_Figures/), using the numbering supplied by Anna. The PNG bytes are unchanged; their correspondence to embedded images in the report PDF has not been checked.

- [Figure 1: Repository disclosure rates](figures/Anna_Figures/Figure_1_Repository_Disclosure_Rates.png)
- [Figure 2: Disclosure types](figures/Anna_Figures/Figure_2_Disclosure_Types.png)
- [Figure 3: Policy visibility and disclosure rates](figures/Anna_Figures/Figure_3_Policy_Visibility_and_Disclosure_Rates.png)
- [Figure 4: Stated consequences](figures/Anna_Figures/Figure_4_Stated_Consequences.png)
- [Figure 5: Policy rationales](figures/Anna_Figures/Figure_5_Policy_Rationales.png)

The original scripts for the final twelve figures are missing. The separate [historical_40 baseline](historical_40/OSS_40_repositories_governance_disclosure_FINAL_BASELINE.csv) contains 40 repositories, 41,468 PRs and 11,592 disclosures; it must not replace final results. Its existing plotting script can regenerate nine earlier exploratory figures:

```sh
python3 scripts/plot_historical.py --output target/reproduction/historical_40
```

Run from the analyser directory with NumPy, SciPy, Matplotlib and Pillow installed. The output directory must be new. This regenerates the earlier analysis only.

Historical detector/build attribution is incomplete. Of 576 PR IDs shared with the 708-PR validation sample, `cilium/cilium#46332` has different stored presence values. Full-run data includes selective later revisions; counts reconciling does not establish a single generating detector version.
