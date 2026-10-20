# Final dataset audit — 17 October 2026

## Source and scope

Data source: `AI_Analyzer-main(3).zip` uploaded for the final reanalysis. The report was updated from the prior 39-repository draft. The new ZIP contains 38 repositories in `repos.txt`, `repo_compliance_summary.csv`, `pr_dataset_output.csv` and `policy_tracker.csv`. Mesa (Python), cncf/landscape and StacDev/stac do not appear in the final 38 repositories; cloudnative-pg/cloudnative-pg is included.

## Recalculated results

- 38 repositories; 41,338 unique eligible PRs; 11,598 disclosure-present PRs; 29,740 without detected disclosures.
- Overall PR-weighted disclosure rate: **28.06%**.
- Positive 9,166; negative 1,785; ambiguous 647. These add to 11,598 exactly.
- Spearman policy visibility versus repository disclosure rate: **rho = 0.6181438854, two-sided p = 0.0000354223, n = 38**.
- Visibility counts Low 9, Moderate 10, High 19; unweighted mean disclosure rates **12.02%, 21.53%, 51.24%**, respectively.
- Updated manually labelled detector evaluation: **708 PRs**, TP 278, TN 425, FP 1, FN 4; accuracy **99.29%**. This evaluated sample was involved in prior rule refinement, so it is not a fully held-out independent test.
- There are 3 repositories with fewer than 20 eligible PRs (two have 1, one has 4). Rates for such repositories are unstable, but they remain in the pre-specified repository-level analysis.

## Cross-checks and interpretation

- Per-repository counts in the summary match all 41,338 PR-level rows, with no duplicated repository–PR identifiers.
- The four aggregate classification counts reconcile exactly. The PR-level export still records `MANUAL_REVIEW_REQUIRED` for many subtype fields even though the repository summary contains detailed subtype totals. Thus subtype totals can be checked at repository level, but not every assignment can be audited from the PR-level CSV alone.
- The ZIP's latest `spearman_correlation.csv` is consistent with independent calculations from `repo_compliance_summary.csv` and the latest policy visibility scores, including napari/napari = 3.
- Governance categories are manually coded, and the original ZIP does not include a complete history of every policy. Statistical consistency is not proof that every historical PR was subject to the same exact policy wording.
- `stan-dev/stan` has visibility score 2 in `policy_tracker.csv` but a blank Policy URL and Repository Link. Its official GitHub wiki contains an AI Contribution Policy at https://github.com/stan-dev/stan/wiki/AI-Contribution-Policy (verified separately on 17 October 2026); the ZIP's coding was not changed on the basis of this external verification.
- Both the main Spearman test and all nine additional governance visualisations are repository-level; supplementary governance plots are descriptive, not additional hypothesis tests.
- The manuscript reports only observable disclosure declarations, not independently verified AI use or true compliance with conditional policy requirements.

## Document and figure QA

- Report: Times New Roman 12pt body, 38-repository results, six regenerated Chapter 4 figures, five Chapter 4 tables.
- The rendered document has 33 total PDF pages; pages **8–30 inclusive** (23 pages) cover Introduction through Conclusions/Future Work. The 2026 P4P Handbook counts the 30-page and 8,000–13,000-word limits across these chapters, excluding frontmatter, references and appendices.
- Main body text and included tables contain approximately 11,100 words (word processor counting may vary slightly).
- Supplementary figure package contains all nine previously requested governance comparisons as PNG, SVG, PDF, plus this dataset and group summaries.

## Items to double-check before signing/submission

- Review the Signed Statement text and actual signatures against the official University template; no signatures were generated or forged.
- If the DOCX is edited after export, update the static Table of Contents page numbers.
- Confirm the `stan-dev/stan` Policy Tracker metadata is filled for reproducibility in the shared source workbook.
- Confirm the final submission includes the supplementary figure bundle if Section 4.6 refers to it.
