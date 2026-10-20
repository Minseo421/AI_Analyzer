# Reproducibility

This directory documents how to check the preserved data and results for the **AI-use disclosure governance in open-source software** study. The research materials are in `research_compendium/`; the Java analyser, tests and supporting scripts are in the sibling `pr_analyser/` directory.

The steps below reproduce **calculations from archived files** where possible. They do not recreate the exact historical GitHub collection: pull requests and repository policies can change after the original analysis.

## Repository layout

```text
AI_Analyzer/
├── pr_analyser/             # Analyser source code, tests and scripts
└── research_compendium/     # Research data, evidence and reports
    ├── irr/
    ├── detector_validation/
    ├── full_repo_analysis/
    │   └── figures/
    │       ├── Anna_figures/
    │       └── Minseo_Figures/
    ├── governance_coding/
    ├── reproducibility/
    └── reports/
```

## Reproduction workflow

1. **Build the analyser.** Follow the [analyser README](../../pr_analyser/README.md) for Java/Maven setup, build instructions and available commands. Run analyser commands from `AI_Analyzer/pr_analyser/`.
2. **Review the final dataset.** See [full repository analysis](../full_repo_analysis/README.md) for the repository list, sampling window, inclusion/exclusion rules, PR-level data and aggregate results. The final dataset covers **41,338 eligible PRs across 38 repositories**. Keep the earlier 40-repository baseline separate.
3. **Recalculate inter-rater agreement.** See [IRR](../irr/README.md) for the 150-PR sample, both researchers' independent labels, consensus labels and Cohen's kappa calculation.
4. **Recalculate detector-validation metrics.** See [detector validation](../detector_validation/README.md) for the historical 350-PR evaluation and the later 708-PR evaluation. These use preserved predictions and manual reference labels; they are not interchangeable samples or independent held-out tests.
5. **Check the quantitative analysis.** Use the saved PR aggregates and policy-visibility inputs described in [full repository analysis](../full_repo_analysis/README.md) to recalculate disclosure rates and the visibility correlation. These checks operate on archived results rather than recollecting GitHub PRs.
6. **Review manual governance coding.** See [governance coding](../governance_coding/README.md) for the repository-level coding sheet, variable definitions and coding procedure. The archived `scripts/build_governance.py` utility can check the stored CSV transformation from its preserved inputs, but does not repeat the researchers' manual coding. These codes describe documented policy requirements, not observed enforcement.
7. **Locate the report figures.** See the [figures README](../full_repo_analysis/figures/README.md) for the separate **Anna_figures/** and **Minseo_Figures/** sets. These are preserved exports used in the individual reports, not two separate datasets. The original generation scripts for all final figures are not available, so exact regeneration is not guaranteed.

## Example: recalculate saved results

After building the analyser, run the following from `AI_Analyzer/pr_analyser/`. The JAR name follows the command documented in the study materials; check the analyser README if your build produces a different name.

```sh
mkdir -p target/reproduction

# Recalculate human–human agreement from the saved labels.
java -jar target/pr-analyzer-maven-1.0.0.jar --calculate-kappa \
  ../research_compendium/irr/anna_labels_kappa_sample.csv \
  ../research_compendium/irr/minseo_labels_kappa_sample.csv \
  target/reproduction/kappa_results.csv

# Recalculate policy-visibility association from archived repository data.
F=../research_compendium/full_repo_analysis
java -jar target/pr-analyzer-maven-1.0.0.jar --visibility-correlation \
  "$F/repo_detected_disclosure_aggregates.csv" "$F/policy_tracker.csv" \
  target/reproduction/visibility.csv target/reproduction/spearman.csv
```

Further validation commands and input-file descriptions are documented in the corresponding compendium subdirectories. Outputs go into `target/reproduction/` so the archived research files remain unchanged.

## Evidence and limits

[`provenance.json`](provenance.json) records historical data revisions, references and available file hashes. The final reports and other supporting project records are under [`../reports/`](../reports/).

The archived evidence supports checking saved calculations and reviewing coding decisions, but does not fully preserve every historical API response, dated policy version, executed detector build or figure-generation script. **Detected disclosure means a recorded AI-related statement was recognised; it does not establish actual AI use, full policy compliance or enforcement.**
