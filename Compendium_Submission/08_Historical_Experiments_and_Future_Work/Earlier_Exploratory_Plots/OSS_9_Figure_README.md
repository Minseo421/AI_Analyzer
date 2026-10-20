# OSS governance and observed PR disclosure — nine figures

## Files

- `OSS_Governance_9_Results_Graphs.pdf`: nine figures, one per page, vector content.
- `OSS_9_Graphs_Overview.png`: quick 3 × 3 preview.
- `OSS_Results_9_Figures/`: nine high-resolution PNG files and nine scalable SVG files.
- `OSS_9_Figure_Group_Summary.csv`: all group-level descriptive values used for plots.
- `OSS_40_repositories_governance_disclosure_FINAL_BASELINE.csv`: the exact 40-repository input used.
- `oss_generate_nine_figures.py`: reproducible generation script (needs numpy, scipy, matplotlib, Pillow).

## Inputs and checks

- 40 GitHub OSS repositories, 41,468 eligible closed PRs; 11,592 disclosure-present PRs; pooled rate 27.95% (unrounded 27.9541%).
- Preserves the current agreed baseline including 816 internally neutral cases counted as disclosure-present. This is an interim measurement decision, not confirmation of actual AI use or policy compliance.
- `napari/napari` visibility = 3; `curl/curl` disclosure detail = 1.
- Visibility score counts: 1: 10 repositories; 2: 11; 3: 19.
- Visibility Spearman correlation across 40 repository-level observations: rho = 0.6510396022; p = 5.39508445e-06. The direction/strength may be sensitive to disclosure definition and other repository characteristics. No causal interpretation is warranted.

## What plots mean

- In figures 1, 2, 4–9: each teal point is one repository's observed disclosure rate; the box shows the interquartile range and median (when n >= 2); the orange diamond is the **unweighted mean** repository disclosure rate in that category.
- In figure 3: the blue-green dot is the **unweighted mean** across repositories where that rationale is documented; the grey dot is the unweighted mean where it is not; their signed difference is shown in percentage points. Counts are shown as `n YES / NO`.
- A group with n=1 is shown but is not adequate for a reliable generalisation; n=0 categories are explicitly retained and marked `no data`.
- N/A values are kept distinct and **not placed on an ordinal numeric scale**.
- Overall disclosure of 27.95% is the **PR-weighted pooled** rate. It is not equal to an unweighted mean of the 40 repository-level rates.
- The plotted figures are exploratory descriptive comparisons; **only Figure 1** has an accompanying previously specified Spearman correlation. No significance claims are made for Figures 2–9.

## Figure names

1. Visibility vs. disclosure rate
2. Disclosure detail vs. disclosure rate
3. Policy rationales (six binary variables) vs. disclosure rate
4. Disclosure required in PR vs. disclosure rate
5. Contributor responsibility vs. disclosure rate
6. AI-use restrictions vs. disclosure rate
7. Stated policy consequences vs. disclosure rate
8. Attribution obligations vs. disclosure rate
9. PR template presence vs. disclosure rate

## Warnings when writing the Results chapter

1. High policy visibility and PR template presence substantially overlap. In this dataset, 18 of 19 high-visibility repositories have a PR template; these are not independent exposures or causal effects.
2. Not all repositories require disclosure in PRs. The denominator includes **all eligible closed PRs**, regardless of whether AI assistance was used; consequently these rates cannot be labelled verified compliance rates.
3. Four repositories have fewer than 20 eligible PRs, including two with only one PR. Plotting each repository is transparent, but small PR denominators make those rates unstable.
4. Disclosure detail 4 has n=3; responsibility 0 has n=1; AI-use constraints 3 has n=1; consequence 1 and attribution 3 have n=0. Avoid generalising from these groups.
5. Coding contains no stated consequence score 1 and no attribution score 3; these are **unobserved categories** rather than zero-rate groups.
6. A policy rationale flag = 1 indicates it is explicitly **stated**, not necessarily the maintainer's actual motivation.
7. Charts and analyses use the supplied repository-level coding and current baseline; policy source dates and classifications have not been independently re-audited.
