# Detector validation

This directory contains the samples, manual reference labels, and outputs used to evaluate the AI-use **disclosure-presence detector**. The evaluations compare **saved detector predictions** with manual labels; they do not independently determine whether AI was actually used in a pull request (PR).

Detector validation is distinct from [human–human inter-rater reliability (IRR)](../irr/README.md), which documents the coding labels and agreement between researchers. Neither detector evaluation is documented as an untouched held-out test.

## Sampling history and rationale

The **350-PR evaluation is historical**. The initial approach sought a sample targeting **95% confidence and a ±5% margin of error**. It combined the original 150 PRs from the inter-rater reliability (IRR) study with 200 additional PRs, selected through seeded random sampling of 50 PRs from each of four further repositories. However, the seven-repository sample did not adequately capture the different AI-disclosure formats used across projects, and the detector showed limited recall (60%). The approach was therefore revised to randomly sample **up to 20 eligible PRs per repository** within the rolling four-month PR-closure eligibility window, using seed `42` in the sampling workflow. The researchers contributed manual labels to one combined detector-validation dataset rather than independently double-coding the same PRs, so a second human–human IRR calculation was not applicable. Some repositories had fewer than 20 eligible PRs, resulting in **708 PRs across 38 repositories**. This broader evaluation helped identify repository-specific disclosure patterns and refine the detector. As the detector was refined using these findings, the later results should not be treated as an independent held-out assessment. The historical sampling target does not, by itself, establish a 95% confidence interval for either non-uniform repository sample.

## 1. Expanded evaluation: 350 PRs across seven repositories

The [historical_350/](historical_350/) directory preserves files from analyser commit `a26a3df` (24 July 2026). The original 150 PRs and their reference labels are maintained in [irr/](../irr/); the archived copies match those files byte-for-byte. The extension adds 50 non-overlapping PRs from each of Cilium, pandas, Ghostty, and OCaml (200 additional PRs).

| File in `historical_350/` | Purpose |
| --- | --- |
| [extended_validation_sample.csv](historical_350/extended_validation_sample.csv) | IDs and saved detector predictions for the additional 200 PRs |
| [anna_extended_labels.csv](historical_350/anna_extended_labels.csv) | Manual labels for those 200 PRs |
| [extended_consensus_labels.csv](historical_350/extended_consensus_labels.csv) | Historical reference-label export; the Coder B field is blank |
| [detector_validation.csv](historical_350/detector_validation.csv) and [extended_detector_validation.csv](historical_350/extended_detector_validation.csv) | Separate comparisons for the original 150 and additional 200 PRs |
| [combined_detector_validation.csv](historical_350/combined_detector_validation.csv) and [combined_detector_metrics.csv](historical_350/combined_detector_metrics.csv) | Combined comparisons, summary metrics, and Wilson intervals |
| [pr_dataset_output_policy_cleaned.csv](historical_350/pr_dataset_output_policy_cleaned.csv) | Saved candidate population containing all 200 extension IDs |

The extension sampler uses saved PR data, excludes previously sampled IDs and repositories, and has a default seed of `20260724`. The actual historical invocation and seed were not recorded. Although the saved candidate population matches the extension IDs and predictions, it is **not** a verified execution manifest.

**Interpretation:** The 350-PR evaluation combines 150 PRs with paired human reference coding and 200 PRs with single-coder reference labels. It is not a 350-PR double-coded IRR study. The recorded detector-version value `current-worktree` also does not identify the exact generating build.

## 2. Later evaluation: 708 PRs across 38 repositories

| File | Purpose |
| --- | --- |
| [all_repo_validation_sample_seeded.csv](all_repo_validation_sample_seeded.csv) | Sampled PRs and saved detector predictions |
| [minseo_seeded_labels.csv](minseo_seeded_labels.csv) and [anna_seeded_labels.csv](anna_seeded_labels.csv) | Disjoint manual-label sets of 361 and 347 PRs |
| [all_manual_seeded_labels.csv](all_manual_seeded_labels.csv) | Combined manual-reference labels |
| [all_seeded_detector_validation.csv](all_seeded_detector_validation.csv) | Summary metrics and row-level comparisons |

The sample contains 20 PRs per repository, except EasyBuild (1), NetBSD (3), and Oathkeeper (4). Observed PR closure dates span May–August 2026. The sampling workflow used seed `42`, as documented above, but the archived sample does not independently record the exact run configuration or date boundaries. Historical sample revisions reduced the dataset from 758 to 738, 718, and finally 708 rows. Changes included exclusions of OpenTelemetry, Mesa, and STAC, and replacement of CNCF Landscape with CloudNativePG. See [provenance.json](../reproducibility/provenance.json) for revision and repository-membership records.

The two researchers labelled **different PRs**, with no shared IDs. Their files therefore cannot be used to calculate human–human Cohen's kappa. The `Consensus ...` fields in the saved validation output are legacy column names for its single-reference comparison, not evidence of a consensus-coding exercise. The dataset preserves 16 *Ambiguous* labels marked disclosure presence *Yes* and one marked *No*.

## 3. Validation results and metrics

A positive result means that a PR was classified as containing an AI-use disclosure; a negative result means no disclosure was identified. The following outcomes compare detector predictions with the manual reference labels.

| Evaluation | TP | TN | FP | FN | Accuracy | Precision | Recall | Specificity | F1 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 350 PRs | 93 | 195 | 0 | 62 | 0.8229 | 1.0000 | 0.6000 | 1.0000 | 0.7500 |
| 708 PRs | 278 | 425 | 1 | 4 | 0.9929 | 0.9964 | 0.9858 | 0.9977 | 0.9911 |

Here, **TP** is true positives, **TN** true negatives, **FP** false positives, and **FN** false negatives. The number of evaluated PRs is

$$
N = TP + TN + FP + FN.
$$

Metrics are calculated as follows:

$$
\begin{aligned}
\mathrm{Accuracy} &= \frac{TP + TN}{N} \\
\mathrm{Precision} &= \frac{TP}{TP + FP} \\
\mathrm{Recall} &= \frac{TP}{TP + FN} \\
\mathrm{Specificity} &= \frac{TN}{TN + FP} \\
F_1 &= \frac{2TP}{2TP + FP + FN}
\end{aligned}
$$

For combined samples, calculate metrics from the **summed TP, TN, FP, and FN counts**, not by averaging percentages. These figures describe performance on the respective labelled samples; they are neither population-weighted estimates nor assessments of disclosure-subtype accuracy. The two evaluations use different samples and should not be interpreted as a controlled before-and-after comparison.

## 4. Reproducing the validation comparisons

The [Java Maven analyser project](../../pr-analyzer-maven-skip-bad-repos/README.md) provides the command-line validation utilities. **“Built analyser” means the compiled analyser application**, not another dataset or a special compendium directory. Its executable JAR is expected at `target/pr-analyzer-maven-1.0.0.jar` after building the analyser; consult the linked analyser README for build requirements.

The commands below assume that `pr-analyzer-maven-skip-bad-repos/` and `Compendium_Submission/` are sibling directories. Run them **from the root of `pr-analyzer-maven-skip-bad-repos/`**, after the JAR has been built:

```sh
mkdir -p target/reproduction
V=../Compendium_Submission/detector_validation

# Recompare the saved predictions for the additional 200 PRs.
java -jar target/pr-analyzer-maven-1.0.0.jar --validate-detector \
  "$V/historical_350/extended_validation_sample.csv" \
  "$V/historical_350/extended_consensus_labels.csv" \
  target/reproduction/extended_validation.csv

# Recompute the combined 350-PR metrics from the archived 150- and 200-PR comparisons.
java -jar target/pr-analyzer-maven-1.0.0.jar --combine-detector-validation \
  "$V/historical_350/detector_validation.csv" \
  "$V/historical_350/extended_detector_validation.csv" \
  target/reproduction/combined_validation.csv \
  target/reproduction/combined_metrics.csv

# Recompare the saved predictions and manual labels for the 708-PR sample.
java -jar target/pr-analyzer-maven-1.0.0.jar --validate-manual-labels \
  "$V/all_repo_validation_sample_seeded.csv" \
  "$V/all_manual_seeded_labels.csv" \
  target/reproduction/seeded_validation.csv
```

Generated files are written under `target/reproduction/`, leaving the archived data unchanged. The combination command uses the **archived** 150- and 200-PR comparison files; it does not consume `target/reproduction/extended_validation.csv`.

### Reproduction boundary

These commands reproduce the **comparison and metrics calculations using stored predictions**. They do **not** rerun detector classification against the historical PR descriptions. New output timestamps or version metadata may differ. The analyser's `--reanalyze-kappa-sample` option retrieves live PR evidence, so differences may arise from changes to PR text as well as detector rules. Historical rule refinement and re-evaluation also limit claims that these results measure independent, held-out performance.
