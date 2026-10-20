# Inter-rater reliability

The retained exercise contains 150 PRs: 50 each from `Homebrew/brew`, `sympy/sympy` and `kubernetes/kubernetes`. Anna and Minseo's separate labels represent the independent coding exercise; consensus is stored separately.

| File | Role |
| --- | --- |
| [kappa_sample.csv](kappa_sample.csv) | Selected PR IDs, metadata and stored detector predictions |
| [anna_labels_kappa_sample.csv](anna_labels_kappa_sample.csv) | Anna's labels and notes |
| [minseo_labels_kappa_sample.csv](minseo_labels_kappa_sample.csv) | Minseo's labels and notes |
| [kappa_sample_consensus_labels.csv](kappa_sample_consensus_labels.csv) | Both coder fields and the agreed labels |

Join by `Sample ID` (`Repo#PR`). `Disclosure Present` is Yes/No. Classification is **Positive** (AI use stated), **Negative** (non-use stated), **Ambiguous** (unclear AI-related statement), or **None** (no relevant statement). A negative disclosure counts as present. Incidental tool names, filenames and unanswered prompts alone do not establish a disclosure. Preserve presence and classification as separately coded fields, especially for ambiguous cases.

Both coders recorded 95 Yes and 55 No; classifications are 77 Positive, 18 Negative and 55 None. All 150 paired rows agree. Cohen's kappa is **1.0 for both fields**, calculated as `(Po − Pe)/(1 − Pe)`, where `Po` is matched-label agreement and `Pe` is the sum of products of coder marginal proportions. All consensus statuses are `Agreed`; no separate adjudication log is present.

After [building the analyser](../../pr-analyzer-maven-skip-bad-repos/README.md), run from its directory:

```sh
mkdir -p target/reproduction
java -jar target/pr-analyzer-maven-1.0.0.jar --calculate-kappa \
  ../Compendium_Submission/irr/anna_labels_kappa_sample.csv \
  ../Compendium_Submission/irr/minseo_labels_kappa_sample.csv \
  target/reproduction/kappa_results.csv
```

This recalculates agreement from saved labels without GitHub access. The original kappa-run output is not preserved.

The retained sample replaced an earlier July sample at commit `20d8e58`; its predictions were updated at `24515b4`. The two samples share no PR IDs. Its observed closure dates span November 2024–July 2026, so the current collector's rolling four-month rule cannot be assumed to describe its historical selection. The actual seed, coding dates, masking conditions and exact instructions used are not recorded. Historical file/version identifiers remain in [provenance.json](../reproducibility/provenance.json). Detector performance against these labels belongs to [detector validation](../detector_validation/README.md).
