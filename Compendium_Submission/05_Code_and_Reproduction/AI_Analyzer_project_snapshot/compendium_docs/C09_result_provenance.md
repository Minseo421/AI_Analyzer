# C09: detector and sample provenance

**Status: open; evidence record prepared, acceptance not certified.** Reviewed 20 October 2026 (Pacific/Auckland), Git revision `9f8d541718d5c72a16ce88987aef385e43c4281f`. See [scope and evidence definitions](index.md) and [hashes and checks](evidence_checks.md).

## Result register

Numbers below identify saved historical results or verify current artifacts. They do not replace reported research results. TP/TN/FP/FN refer to **disclosure presence**, including explicit negative disclosures, rather than AI-use prevalence or classification accuracy.

| Result/sample | Exact artifacts and version | Verified result | Detector provenance and limit |
| --- | --- | --- | --- |
| Earlier 150-PR reliability/development sample | `0cc5579:kappa_sample.csv`; labels first stored as `af408d4:anna_labels.csv` and `ac913c2:minseo_labels.csv`; `f7f12b4:consensus_labels.csv` and `detector_validation.csv`. | 50 PRs each from apache/airflow, cloudnative-pg/cloudnative-pg and kubernetes/kubernetes. Saved detector counts at `f7f12b4`: 31/109/9/1; accuracy 0.9333. | Original predictions are stored in the sample. Source at the sample/output commits is recoverable, but neither file records a generating build hash. Subsequent rule changes and reanalysis make this historical development evidence. |
| Revised 150-PR reliability/development sample, retained today | Sample introduced at `20d8e58`; current `kappa_sample.csv` last changed at `24515b4`. Current labels: `anna_labels_kappa_sample.csv`, `minseo_labels_kappa_sample.csv`, `kappa_sample_consensus_labels.csv`. Historical output: `24515b4:detector_validation.csv`. | 50 each from Homebrew/brew, sympy/sympy and kubernetes/kubernetes. Both coder files cover the same 150 IDs, with kappa 1.0 for presence and classification. Stored detector counts: 78/55/0/17; accuracy 0.8867, recall 0.8211. | July predictions, not a new run of the October detector. The sample is byte-identical to its `24515b4` version. Rule changes and repeated evaluation are recorded in July history. |
| Initial 350-PR combined evaluation | `a26a3df:combined_detector_validation.csv` and `combined_detector_metrics.csv`, combining `detector_validation.csv` with `extended_detector_validation.csv`. | Revised 150 plus 200 additional PRs; 350 distinct IDs. Counts 93/195/0/62; saved accuracy 0.8229, precision 1.0000, recall 0.6000, F1 0.7500. | Metrics record detector version only as `current-worktree`. This is not an exact build identifier. The two component outputs have stored predictions but no immutable source-evidence/run manifest establishing compatible generating versions. |
| Later all-repository evaluation, final retained 708 rows | Current `all_repo_validation_sample_seeded.csv`, `all_manual_seeded_labels.csv`, `all_seeded_detector_validation.csv`, with separate coder files. Last output edit `9f8d541`. | 708 distinct IDs across 38 repositories. Counts 278/425/1/4; saved accuracy 0.9929, precision 0.9964, recall 0.9858, F1 0.9911. Recalculation matches all 708 detailed output rows. | Most retained prediction fields match September's `9502c13` sample; October adds a replacement repository and removes repositories. The final comparison timestamp does not establish that every prediction was rerun with the October detector. |
| Main 38-repository dataset | Current `pr_dataset_output.csv`, `repo_compliance_summary.csv`; initial large dataset at `6feb773`, later edits through `9f8d541`. | 41,338 unique PRs; 11,598 disclosure-present, 29,740 absent. Summary totals agree by repository. | A revised dataset assembled across multiple commits, including selective October changes. No row-level generating detector/build manifest is stored. |
| Current Spearman result | `policy_tracker.csv` and `repo_visibility_summary.csv` support `spearman_correlation.csv` at `9f8d541`; main PR/summary counts reconcile. | 38 repository observations; saved rho 0.6181438854, p 0.0000354223. Independent rank calculation reproduces these to the saved decimal precision. | Correlation code last changed at `f1908cc`; counts inherit the main dataset's mixed provenance. Exact historical command/input route is not recorded: source accepts either PR data or a repository summary. |

All current inputs/outputs above have SHA-256 hashes in [evidence_checks.md](evidence_checks.md). Historical artifacts are identified there by commit, path, SHA-256 and Git blob. Associated detector, collection and coding-framework source blobs are also recorded. **A source snapshot at an output commit identifies available code, not a verified generating executable.**

## The two different 150-PR samples

The July 8 sample at `0cc5579` and the July 22 sample at `20d8e58` have **zero shared PR IDs**, including different Kubernetes PRs. They must not be described as one unchanged reliability sample. The earlier Anna and Minseo files cover the July 8 IDs; the current renamed coder files cover the July 22 IDs. See [C10](C10_human_label_provenance.md) for file replacement and consensus details.

The local history establishes this sequence:

| Local author date | Commit | Verified change relevant to provenance |
| --- | --- | --- |
| 8 July 2026 | `0cc5579` | Stores the earlier 150-row sample. |
| 18 July | `f7f12b4`, `4fc6ffb` | Stores initial consensus/validation; adds fixed-ID sample reanalysis workflow. |
| 20 July | `e51ae75` | Changes comment-related detection and stores completed/reanalysed sample outputs. |
| 22 July | `20d8e58` | Replaces sample with Homebrew/SymPy/Kubernetes and replaces Anna's labels; adds random repository selection with an optional environment seed. |
| 22 July | `57fa0c1`, `9e8f0da` | Changes detector rules and stores reanalysis; replaces Minseo's labels and preserves the older set in `minseo_labels_backup.csv`. |
| 23 July | `24515b4` | Updates sample and consensus; stores the revised 150-row detector-validation output. |
| 16 September | `0f46bee` | Renames the two coder files and consensus with 100% content similarity; deletes several historical outputs from the checkout. |

The supplied commit-history PDF, pages 1–2, retrospectively connects sample replacement/reanalysis to detection difficulties. Git independently verifies the replacements and rule/output edits. It does not establish the actual random seed, coding sessions, or a preregistered selection decision.

`KappaSampleReanalysisWorkflow.java` preserves IDs but calls `PrAnalyzer.analyzeExistingPullRequest`, which fetches source evidence again. Therefore fixed IDs alone do not isolate rule changes: changed PR bodies/comments and retrieval coverage can also affect predictions. The README's stronger attribution of all differences to rules is not adopted here.

## Initial 350-PR evaluation and its limitations

At `a26a3df` (24 July 2026), `extended_validation_sample.csv` contains 50 PRs each from cilium/cilium, pandas-dev/pandas, ghostty-org/ghostty and ocaml/ocaml. These 200 IDs do not overlap the revised 150. All 200 occur in the stored `pr_dataset_output_policy_cleaned.csv`, and all 200 script-presence flags match that candidate population file.

`ExtendedValidationWorkflow.java` samples from an existing PR dataset, excludes existing sample IDs and represented repositories, and provides default seed `20260724`. The stored artifacts establish membership, but do not prove that the default rather than an explicit seed was used or exactly which input bytes were supplied. The cleaned dataset stored at this commit is a verified matching candidate input, not a certified execution manifest.

The extension's saved counts are TP 15, TN 140, FP 0, FN 45. Its human reference is explicitly single-coder: all 200 rows of `extended_consensus_labels.csv` have empty Coder B labels and the note `Single-coder consensus from anna_extended_labels.csv`. A second extension coder file is mentioned in instructions but was not found among CSV paths in reachable local Git history. The filename and `Resolved` status are insufficient evidence of dual coding or adjudication.

The combined output sums 150+200 row outcomes, rather than averaging component accuracy. It records 0 duplicate rows, target 342 and achieved 350. Its saved Wilson interval for accuracy is 0.7794–0.8593. These are preserved historical calculations; the numerical interval does not establish random independent PR sampling across the study population.

The two component sets each allocate 50 PRs per selected repository, with different human-label structures. The pooled metrics are unweighted performance on those sampled PRs. No population sampling weights, repository inclusion probabilities, or cluster adjustment are recorded in the inspected metrics. They should not be presented as population-weighted performance. Detector source at `24515b4` and `a26a3df` has the same blob, but `PrAnalyzer.java` and `coding_framework.md` differ; identical detector source alone does not prove compatible end-to-end executions.

`9dcc9c6:combined_human_labels.csv` later stores 350 IDs matching this combined evaluation's membership. It is an archived combined human reference, not a third independent evaluation. Historical extension and combined files were deleted at `c0b7407`/`0f46bee`; they remain recoverable from Git.

## Later seeded sample: 758 to 708

| Version/date | Membership and evidence |
| --- | --- |
| `82b6268`, 10 September 2026 | Introduces seeded workflow and a 758-row sample across 41 repositories. Merge `a125865` follows. |
| `3bba769` / `3479280`, 11 September | Stores 380 Minseo labels and 378 Anna labels. |
| `c0b7407`, 14 September | First combined labels and saved evaluation: 758 rows, TP 203, TN 464, FP 7, FN 84. |
| `374c4f9` / `767ef70`, 14 September | Rule/sample/label revisions and a rechecked output; `767ef70` saves 758-row counts 256/467/1/34. |
| `779a704`, 16 September | Removes 20 open-telemetry/opentelemetry-collector IDs: 738 rows. |
| `39d3345` / `9502c13`, 16 September | Further rule changes and reanalysis; final saved September comparison has 738 rows, 276/457/1/4, timestamp `2026-09-16T00:11:06.327490Z`. |
| `b824757`, 16 October | Replaces 20 cncf/landscape IDs with 20 cloudnative-pg/cloudnative-pg IDs. Total remains 738. |
| `44c2808`, 16 October | Removes 20 mesa/mesa IDs: 718 rows. |
| `9f8d541`, 16 October | Removes 10 StacDev/stac IDs: 708 rows across 38 repositories. |

The final 708 rows include 35 repositories with 20 PRs each, plus easybuilders/easybuild (1), NetBSD/src (3) and ory/oathkeeper (4). Thus the metrics pool a roughly equal-per-repository sample, not a sample proportional to the main dataset's repository sizes. No population weighting is recorded.

Comparing `9502c13` with the current sample finds 688 shared IDs, 50 removed IDs and 20 added IDs. For all 688 shared IDs, script presence, detected text and stored closed date are unchanged. This does not prove their predictions are wrong under later rules; it establishes that no changed values document an October rerun for those fields. The final output's `Analysed timestamp` is `2026-10-16T08:51:17.512858Z`: it dates the comparison artifact, not every prediction, human label, or source retrieval.

The final 708 IDs have no overlap with the revised July 150 or the July combined 350. However, four overlap the **earlier** July 8 development sample: `cloudnative-pg/cloudnative-pg#10421`, `cloudnative-pg/cloudnative-pg#10970`, `cloudnative-pg/cloudnative-pg#11039`, and `kubernetes/kubernetes#140038`. Disjointness from one historical sample does not establish independence from detector development. Rule, sample and human-label revisions during September also prevent an unsupported held-out claim.

## Dates and windows

CSV PR dates describe PR events, not coding or collection times. Commit dates describe when files entered history, not when coding or a detector run occurred.

| Artifact | Observed minimum–maximum Closed Date (UTC) | Collection/window evidence and uncertainty |
| --- | --- | --- |
| Earlier 150 (`0cc5579`) | `2026-04-22T16:09:26Z`–`2026-07-08T06:38:22Z` | Stored by 8 July. Exact collection instant/configuration not recorded. |
| Revised/current 150 | `2024-11-08T05:24:44Z`–`2026-07-21T07:00:56Z` | Stored as a replacement on 22 July, then reanalysed. This observed span does not satisfy a uniform contemporary four-month closure window; later methodology must not be retroactively applied to it. |
| Historical extension 200 | `2026-06-09T05:02:55Z`–`2026-07-23T07:30:19Z` | Sample drawn from stored dataset; archived by 24 July. Exact selection invocation/seed not established. |
| Final seeded 708 | `2026-05-01T02:29:45Z`–`2026-08-31T23:52:14Z` | Dates are consistent with May–August, but do not alone prove exact configured boundaries. Sampler takes explicit start/end dates and seed; no saved invocation establishes the actual values. |
| Main 41,338 | `2026-05-22T10:07:17Z`–`2026-09-22T09:55:38Z` | Preamble records started `2026-09-22T09:58:43.766425Z`, completed `2026-09-23T08:14:59.763018Z`. Source uses a rolling four-calendar-month cutoff; the preamble remains after October edits and does not date those edits. |

The CLI help example in `Main.java` supplies `2026-04-01 2026-07-31 42` for all-repository sampling. It is an example, not a run record; the saved seeded sample includes August closures. Neither seed 42 nor that April–July window is asserted as actual provenance.

## Main dataset and Spearman lineage

`6feb773` introduces the large September dataset: **41,468 parsed PR records across 40 repositories**, excluding CSV metadata and header. The commit-history PDF's “41479 prs” matches neither that parsed PR count nor the current dataset count; do not use text-line counts as PR counts.

`8279af2` changes Homebrew detector rules on 15 October; `07acbf8` updates dataset/summary files. `da9c42c` changes Joomla dataset/summary records. `b824757` replaces CNCF with CloudNativePG, followed by Mesa and StacDev removals. The retrospective PDF describes selective same-PR reanalysis and replacement in the same period. Git verifies the affected files and membership edits, but supplies no per-row build or retrieval ledger.

Current classifications are 10,247 `MANUAL_REVIEW_REQUIRED`, 930 `possible_positive`, 99 `possible_negative`, 322 `possible_ambiguous`, and 29,740 `None`. All 41,338 `AI Disclosure Required` values are `MANUAL_REVIEW_REQUIRED`. These fields do not establish fully human-validated classification or policy compliance. Their different forms are an observable provenance limitation, not a basis for recoding them here.

The main dataset includes 576 of the final 708 evaluation IDs. Among those, `cilium/cilium#46332` has stored detector presence `No` in the seeded sample and `Yes` in the main dataset. The inspected records do not resolve whether a rule version, retrieved source text, or another transformation caused this difference. Counts can reconcile internally while their generating versions remain unresolved.

| Historical correlation artifact | Revision | Repository n | Saved rho | Saved p |
| --- | --- | --- | --- | --- |
| `visibility_correlation.csv` | `0acb386` (23 July) | 36 | 0.6236612231 | 0.0000268111 |
| `visibility_correlation_updated.csv` | `f63847b` (24 July); unchanged value at `9a8546c` (11 August) | 37 | 0.5902074945 | 0.0001205895 |
| `spearman_correlation.csv` | `f1908cc` (24 September) | 40 | 0.7061336627 | 0.0000003573 |
| Same path | `65cf3a9` (16 October) | 40 | 0.6510396022 | 0.0000053951 |
| Same path | `b824757` (16 October); same file retained at `44c2808` | 39 | 0.6192256252 | 0.0000263737 |
| Same path, current | `9f8d541` (16 October) | 38 | 0.6181438854 | 0.0000354223 |

These are successive analysis artifacts, not replications on one unchanged input. Current counts, policy matches and rank correlation were checked; older correlation values were read from Git, not independently regenerated in this task. Current source ranks with average ranks for ties and uses a large-sample p approximation for n > 9. Correlation interval fields are blank. This record does not perform the separate robustness work requested by C12.

## C09 acceptance assessment and unresolved information

The supplied checklist, page 5, requires each reported accuracy claim to identify its exact sample and detector, and combined metrics to use compatible versions with sampling weights or limitations documented. This record identifies stored samples/results and limitations, but **C09 is not complete**.

| Requirement | Assessment |
| --- | --- |
| Code/input hashes, sample identity and output versions | Current hashes and selected historical hashes recorded; associated source revisions distinguished from unverified generating builds. |
| Detector-rule version for each accuracy claim | Unresolved for actual generating executions. July artifacts and September/October mixtures cannot all be attributed to current HEAD. |
| Collection time and coding-framework version | Available preambles/commit dates recorded; actual historical invocations, some retrieval times and the instructions actually used during coding remain unresolved. Framework snapshots are hashed, not asserted as instructions actually received. |
| Separate tuning from held-out evaluation | Historical development/reanalysis is identified; no inspected evidence certifies a frozen, untouched held-out evaluation. |
| Compatible pooled results and sampling limitations | Historical pooling and sample structure documented. No new pooling is performed; exact execution compatibility and population weights are unverified. |
| Every final reported accuracy claim mapped | No final report/figure manifest is present in the tracked snapshot; downstream wording and all reported claims cannot be signed off here. |

## Recheck after Anna's C01–C08 changes are merged

1. Compare merged code and CSV hashes with this frozen baseline; keep changes and any new results as a separate version.
2. Find original run configurations/logs and archived API/HTML/policy evidence linking each output to a build, UTC sampling boundaries, seed, input hashes and coding-framework revision. Mark later recollection as a new evidence version.
3. Resolve the 150-row historical window, the seeded sample's actual dates/seed, the mixed main-data classification versions, and `cilium/cilium#46332` using archived evidence rather than current live pages.
4. Check whether the October Homebrew detector was evaluated on all retained validation IDs. If new evaluation is needed, preserve historical outputs and explicitly record development reuse and whether any sample was genuinely held out.
5. Review new exclusion records against the 758→738→718→708 chain and the CNCF/CloudNativePG replacement. Verify coder ownership and membership after any changes.
6. Map final report/table accuracy and correlation claims to exact artifact versions; assess pooled-version compatibility and sampling limitations before considering C09 complete.
