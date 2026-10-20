# Detector validation

These evaluations compare stored detector disclosure-presence predictions with manual labels. They are separate from [human–human IRR](../irr/README.md), which also defines the coding labels. Neither evaluation is documented as an untouched held-out test.

## Expanded evaluation: 350 PRs across seven repositories

[historical_350/](historical_350/) restores exact files from analyser commit `a26a3df` (24 July 2026). The original 150 PRs and their reference labels are canonical in [irr/](../irr/); their bytes match that commit. The extension adds 50 PRs each from Cilium, pandas, Ghostty and OCaml, without overlapping the original sample.

| File in historical_350 | Role |
| --- | --- |
| [extended_validation_sample.csv](historical_350/extended_validation_sample.csv) | Additional 200 IDs and detector predictions |
| [anna_extended_labels.csv](historical_350/anna_extended_labels.csv) | Manual reference for the 200 PRs |
| [extended_consensus_labels.csv](historical_350/extended_consensus_labels.csv) | Historical single-coder reference export; Coder B is blank |
| [detector_validation.csv](historical_350/detector_validation.csv), [extended_detector_validation.csv](historical_350/extended_detector_validation.csv) | Separate 150- and 200-row comparisons |
| [combined_detector_validation.csv](historical_350/combined_detector_validation.csv), [combined_detector_metrics.csv](historical_350/combined_detector_metrics.csv) | Combined outcomes, metrics and Wilson intervals |
| [pr_dataset_output_policy_cleaned.csv](historical_350/pr_dataset_output_policy_cleaned.csv) | Historical candidate population containing all 200 extension IDs |

The extension sampler draws from saved PR data, excludes existing sample IDs and repositories, and has default seed `20260724`. The actual historical invocation/seed is not recorded. The saved candidate population matches the extension IDs and predictions, but is not a verified run manifest. The combined evaluation is 150 paired-reference PRs plus 200 singly coded PRs, not 350 double-coded PRs. Its `current-worktree` detector-version field does not identify a generating build.

## Later evaluation: 708 PRs across 38 repositories

| File | Role |
| --- | --- |
| [all_repo_validation_sample_seeded.csv](all_repo_validation_sample_seeded.csv) | Sample and stored detector predictions |
| [minseo_seeded_labels.csv](minseo_seeded_labels.csv), [anna_seeded_labels.csv](anna_seeded_labels.csv) | Disjoint sets of 361 and 347 manual labels |
| [all_manual_seeded_labels.csv](all_manual_seeded_labels.csv) | Combined manual reference, matching their union |
| [all_seeded_detector_validation.csv](all_seeded_detector_validation.csv) | Summary metrics followed by row-level comparisons |

There are 20 PRs per repository except EasyBuild (1), NetBSD (3) and Oathkeeper (4). Observed closure dates span May–August 2026; the actual configured dates and seed are unavailable. Historical sample changes were 758 → 738 → 718 → 708 rows, including exclusions of OpenTelemetry, Mesa and STAC and replacement of CNCF Landscape with CloudNativePG. [Provenance](../reproducibility/provenance.json) retains revisions and membership records.

The two coder files have no shared IDs, so they cannot establish human–human kappa. The output's `Consensus ...` columns are legacy names used by the single-reference validator. Sixteen Ambiguous labels have presence Yes and one has No; these recorded decisions are preserved.

## Metrics and verification

| Evaluation | TP | TN | FP | FN | Accuracy | Precision | Recall | F1 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 350 | 93 | 195 | 0 | 62 | 0.8229 | 1.0000 | 0.6000 | 0.7500 |
| 708 | 278 | 425 | 1 | 4 | 0.9929 | 0.9964 | 0.9858 | 0.9911 |

Accuracy = `(TP+TN)/N`; precision = `TP/(TP+FP)`; recall = `TP/(TP+FN)`; specificity = `TN/(TN+FP)`; F1 = `2TP/(2TP+FP+FN)`. Combine outcome counts, not averaged percentages. These are unweighted sample metrics, not population-weighted performance or subtype accuracy.

From the [built analyser directory](../../pr-analyzer-maven-skip-bad-repos/README.md):

```sh
mkdir -p target/reproduction
V=../Compendium_Submission/detector_validation
java -jar target/pr-analyzer-maven-1.0.0.jar --validate-detector \
  "$V/historical_350/extended_validation_sample.csv" \
  "$V/historical_350/extended_consensus_labels.csv" \
  target/reproduction/extended_validation.csv
java -jar target/pr-analyzer-maven-1.0.0.jar --combine-detector-validation \
  "$V/historical_350/detector_validation.csv" \
  "$V/historical_350/extended_detector_validation.csv" \
  target/reproduction/combined_validation.csv target/reproduction/combined_metrics.csv
java -jar target/pr-analyzer-maven-1.0.0.jar --validate-manual-labels \
  "$V/all_repo_validation_sample_seeded.csv" "$V/all_manual_seeded_labels.csv" \
  target/reproduction/seeded_validation.csv
```

These commands compare saved predictions; they do not execute detection on archived source text. New output timestamps/version metadata can differ. `--reanalyze-kappa-sample` fetches live PR evidence, so its differences can reflect edited source text as well as changed detector rules. Historical rule refinement and re-evaluation limit claims of independent performance.
