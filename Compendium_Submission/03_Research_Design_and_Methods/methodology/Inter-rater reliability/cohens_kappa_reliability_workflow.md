# Inter-Rater Reliability: Cohen's Kappa Workflow

**Research project:** AI-use disclosure in open-source software pull requests  
**Document type:** Research compendium — methodology and reproducibility record  
**Scope:** Human coding reliability for pull-request disclosure labels; relationship to automated-detector validation

## 1. Purpose and scope

This document records the procedure used to assess agreement between two human coders who classified observable AI-use disclosures in pull requests (PRs). Cohen's kappa was used to quantify agreement beyond chance. The purpose of this document is to preserve the coding procedure, analysis commands, results and supporting artifacts so that the assessment can be inspected or reproduced.

The inter-rater reliability exercise is **distinct from automated-detector validation**. Kappa compares the two researchers' labels; detector validation compares automated classifications with manually reviewed reference labels. The broader study's motivations for examining AI disclosure are discussed in the final report and are not repeated here.

## 2. Assessment overview

The study began with a **150-PR inter-rater reliability sample** drawn from three repositories, with 50 PRs reviewed per repository. The initial agreement assessment reported **Cohen's κ = 1.00**. A later, expanded manual validation dataset covered **350 PRs from seven repositories** and was used to assess the automated detector's performance.

| Dataset | Repositories | PRs | Role in the research |
| --- | ---: | ---: | --- |
| Initial inter-rater reliability sample | 3 | 150 | Assess consistency of human disclosure coding |

The initial repositories were `Homebrew/brew`, `sympy/sympy` and `kubernetes/kubernetes`.

## 3. Sampling and input data

Each reliability record identifies a specific repository and PR. The study's eligible population comprised closed, human-authored PRs within the applicable rolling four-month window, using `closed_at` for time-window eligibility; merged and unmerged closed PRs were eligible, while bot-authored PRs were excluded.

The original sampling implementation reads repository identifiers from an input file and retrieves eligible PRs. The supplied workflow describes selection of recently closed PRs, ordered by `closed_at`, while also mentioning optional randomised repository selection. These refer to potentially different sampling steps and should not be conflated. **The archived PR sample is the authoritative record of which items were actually coded.**

The command documented for generating a sample is:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --sample-for-kappa repos.txt 50 kappa_sample.csv
```

For an exact reproduction of the initial 150-PR exercise, the repository input must identify the same three repositories, and the archived analysis date, sample file, program version and any selection seed or configuration must be used. Running this command against a different repository list or a later GitHub snapshot may generate a different sample.

The sample CSV records identifying and contextual data, including the PR ID (`OWNER/REPO#NUMBER`), repository, number, URL, title, author, creation/closure/merge timestamps and status. It may also contain candidate disclosure text and a preliminary automated classification. Those fields are aids to review, not independent human judgments.

## 4. Human coding procedure

Two researchers coded the **same fixed sample independently**, using the shared disclosure coding definitions in [`coding_framework.md`](coding_framework.md). Each PR was matched across coder files using an unchanged `Sample ID` of the form `OWNER/REPO#NUMBER`.

The coding process consisted of:

1. Reviewing the selected PR description and relevant evidence available within the defined analysis scope.
2. Assigning a `Disclosure Present` value (`Yes` or `No`).
3. Assigning a `Disclosure Classification` value using the categories below.
4. Recording explanatory notes for cases requiring interpretation.
5. Preserving each researcher's original labels for comparison.

| Classification | Coding meaning |
| --- | --- |
| `Positive` | The contributor explicitly states that AI assistance was used. |
| `Negative` | The contributor explicitly states that AI assistance was not used. |
| `Ambiguous` | AI-related text is present but does not establish a clear disclosure of use or non-use. |
| `None` | No relevant AI-use disclosure was identified. |

An explicit statement that **no AI was used** was counted as disclosure present, but not as evidence of AI use. Incidental tool names, policy prompts, unchecked template options and filenames were not, by themselves, treated as affirmative use disclosures. The original `Disclosure Present` and `Disclosure Classification` columns should be retained rather than inferring one mechanically from the other, particularly for ambiguous cases.

### Coding file format

The original workflow used separate CSV files with the following fields:

```csv
Sample ID,Repo,PR #,PR URL,Disclosure Present,Disclosure Classification,Notes
```

The documented interactive command is:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --code-kappa-sample kappa_sample.csv coder_a_labels.csv
```

The second researcher uses the same sample file with a separate output filename. Independent files must not be overwritten or replaced with reconciled labels.

## 5. Agreement calculation

After coding, labels are joined by `Sample ID`. Cohen's kappa is calculated as:

\[
\kappa = \frac{P_o-P_e}{1-P_e}
\]

where `P_o` is observed agreement and `P_e` is agreement expected by chance from the coders' label distributions.

The documented calculation command is:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar \
  --calculate-kappa coder_a_labels.csv coder_b_labels.csv kappa_results.csv
```

The implementation supports comparison of `Disclosure Present` (`Yes`/`No`) and `Disclosure Classification` (`Positive`/`Negative`/`Ambiguous`/`None`). The output is described as containing matched-row counts, observed and expected agreement, kappa statistics, confusion matrices, missing-label checks and disagreements.

**Recorded initial result:** κ = **1.00** for the initial 150-PR assessment, indicating complete agreement for the reported comparison. The retained `kappa_results.csv` should be consulted before attributing this figure to both coding fields individually. As with any agreement statistic, it does not independently establish that the shared interpretation was correct.

## 6. Adjudication and reference labels

The workflow provides for examination of disagreements **after** calculating inter-rater reliability. Any differing rows should be reviewed against the original PR evidence and coding framework, and a documented consensus label should then be recorded.

For the initial assessment, the reported perfect agreement means that no disagreements were reflected in the reported kappa comparison.

Independent researcher files and any adjudicated or reference-label dataset serve different purposes and should be retained separately. Manual reference labels were subsequently used to assess the automated detector; this does not make the detector's labels part of the inter-rater reliability calculation.

## 8. Reproducibility artifacts

The following records should accompany this document in the compendium. The filenames below are those used or illustrated in the original workflow; **confirm actual names and paths against the archived project** rather than creating empty substitutes.

| Artifact | Purpose |
| --- | --- |
| Repository input list (`repos.txt` or archived equivalent) | Defines repositories used for sample generation |
| Original sample (`kappa_sample.csv` or equivalent) | Freezes the PR identifiers and review metadata |
| Coder A labels CSV | Preserves first researcher's independent judgments |
| Coder B labels CSV | Preserves second researcher's independent judgments |
| Kappa output (`kappa_results.csv` or equivalent) | Records agreement statistics and diagnostics |
| Consensus/reference labels, if separately saved | Documents final manual classifications |
| Detector-validation comparison and metrics | Supports the 350-PR evaluation results |
| [`coding_framework.md`](coding_framework.md) | Defines labels and edge-case treatment |
| [`validation_protocol.md`](validation_protocol.md) | Documents detector comparison procedure |
| Analyzer source code and build instructions | Allows the sampling and analysis commands to be inspected and rerun |

For rigorous reproduction, the compendium README should identify the **actual relative paths** for these artifacts and record the code revision, relevant configuration, analysis timestamp/cutoff, Java/build version and any random seed used. Where exact historical settings were not retained, state that limitation instead of reconstructing them as if verified.

## 9. Interpretation limits

- **Agreement is not correctness.** The reported κ = 1.00 indicates agreement for the evaluated labels, not proof that every label reflected the contributor's intent.
- **Coverage was limited.** The initial reliability assessment involved 150 PRs from three repositories and does not establish identical reliability for every repository-specific template or disclosure convention.
- **The detector was refined.** The early human-reviewed cases may have informed detector changes, so the combined validation dataset should not be described as wholly independent held-out evaluation without supporting evidence.
- **Observability was bounded.** The detector primarily analysed PR descriptions and did not systematically assess all comments, reviews, commits or linked discussions. Neither the labels nor detector output reveal undeclared AI use.

## 10. Supporting Data Files
The `IRR/` directory contains the original datasets used for the inter-rater reliability assessment, supporting transparency and reproducibility. The kappa_sample.csv file identifies the 150 pull requests selected from three repositories for independent manual coding. The `anna_labels_kappa_sample.csv` and `minseo_labels_kappa_sample.csv` files preserve the classifications independently assigned by each researcher, allowing their agreement to be independently assessed. The `kappa_sample_consensus_labels.csv` file contains the final agreed classifications established following the coding process. Together, these files provide an auditable record of the reliability assessment and allow future researchers to inspect the coding decisions and reproduce the agreement analysis.

## 10. Related compendium documentation

- [`coding_framework.md`](coding_framework.md) — classification definitions and decision rules.
- [`validation_protocol.md`](validation_protocol.md) — manual-reference comparison and evaluation metrics.
- [`methodology_workflow.md`](methodology_workflow.md) — wider data collection and analysis process.
- [`repository_analysis_framework.md`](repository_analysis_framework.md) — repository-level analysis rules.
