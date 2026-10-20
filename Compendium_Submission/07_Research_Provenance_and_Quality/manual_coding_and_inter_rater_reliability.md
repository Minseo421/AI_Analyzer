# C10: human-label independence and adjudication

**Status: open; file relationships verified, historical independence and final-report compliance not certified.** Reviewed 20 October 2026 (Pacific/Auckland), Git revision `9f8d541718d5c72a16ce88987aef385e43c4281f`. See [scope](../05_Code_and_Reproduction/AI_Analyzer_project_snapshot/compendium_docs/index.md), [C09](detector_result_version_history.md), and [hashes/checks](original_working_snapshot_hash_checks.md).

## Current label files and what they establish

| File | Verified role and coverage | What it does not establish |
| --- | --- | --- |
| `anna_labels_kappa_sample.csv` | Anna-named paired coder file; 150 IDs matching current `kappa_sample.csv`. | Actual coding session dates, masking, or absence of discussion. |
| `minseo_labels_kappa_sample.csv` | Minseo-named paired coder file; the same 150 IDs. | Independence cannot be inferred solely from separate filenames or agreement. |
| `kappa_sample_consensus_labels.csv` | 150 complete consensus labels; all statuses `Agreed`, all consensus notes empty. Label values match both paired files. Coder A notes match Anna; Coder B notes match Minseo. | No recorded disagreement resolutions, rationale, or third-party review for this version. Perfect agreement is not evidence of a separate adjudication session. |
| `minseo_seeded_labels.csv` | 361 labels on the current all-repository sample; exact ID ownership in this file. | Does not constitute a second rating of Anna's portion. |
| `anna_seeded_labels.csv` | 347 labels on the current sample; disjoint from Minseo's 361. | Does not constitute a second rating of Minseo's portion. |
| `all_manual_seeded_labels.csv` | 708 labels equal to the union of the two seeded coder files, including label values, PR metadata and notes when matched by ID. | A combined singly coded reference, with no additional independent rating established. |
| `all_seeded_detector_validation.csv` | Stored comparison of the seeded detector output against those 708 human labels. All detailed rows match recalculation. | Its column names `Consensus Disclosure Present` and `Consensus Classification` do not prove consensus or dual coding. |

All of these files remain unmodified. Their exact current hashes are recorded in [evidence_checks.md](original_working_snapshot_hash_checks.md).

## Which 150 PRs were paired

The retained paired sample is exactly the `Sample ID` list in current `kappa_sample.csv` and both `*_labels_kappa_sample.csv` files:

| Repository | 1-based PR record positions in current sample | Paired PRs |
| --- | --- | --- |
| Homebrew/brew | 1–50 | 50 |
| sympy/sympy | 51–100 | 50 |
| kubernetes/kubernetes | 101–150 | 50 |

Positions count data records after the CSV header, not physical text lines; evidence can contain line breaks. `Sample ID` and PR URL identify the exact PRs and are authoritative over row positions. There are no duplicate or missing IDs between the sample, two coder files and consensus.

Both coders record 95 `Yes` and 55 `No` labels; classification counts are 77 Positive, 18 Negative and 55 None. Both fields agree on all 150 PRs. Recalculated Cohen's kappa is 1.0 for disclosure presence and 1.0 for classification. This describes the retained paired files; it does not independently establish how they were produced. Detector-versus-consensus performance on these same PRs is a different comparison: 78 TP, 55 TN, 0 FP, 17 FN.

### The earlier paired sample must remain distinct

The earliest locally recorded pair is `af408d4:anna_labels.csv` (8 July 2026) and `ac913c2:minseo_labels.csv` (16 July), corresponding to `0cc5579:kappa_sample.csv`. It contains 50 PRs each from apache/airflow, cloudnative-pg/cloudnative-pg and kubernetes/kubernetes. A read-only comparison finds 150 matched IDs and identical presence/classification labels in that archived pair too. `f7f12b4:consensus_labels.csv` has 150 `Agreed` rows and empty consensus notes.

The current 150 PRs are a replacement sample introduced on 22 July at `20d8e58`, with **no shared PR IDs** with the earlier sample. Anna's labels change at that commit. Minseo's labels change at `9e8f0da` later that day, which also saves the earlier labels as `minseo_labels_backup.csv`. On 16 September, `0f46bee` renames `anna_labels.csv`, `minseo_labels.csv` and `consensus_labels.csv` to their current kappa-specific names without changing their bytes.

Thus the current files preserve the two coder streams for the **revised** sample; they are not untouched copies of the first July coding exercise. Git retains the earlier versions. The inspected local CSV history contains no saved `kappa_results.csv` artifact. The kappa values above are calculations from the archived/current paired files, not newly discovered original kappa-run logs.

## Which 708 PRs were divided between researchers

The current sample has 708 unique IDs, each appearing in exactly one seeded coder file. Ownership by sample record is verified as:

| Researcher file | Current sample record positions | Labels |
| --- | --- | --- |
| `minseo_seeded_labels.csv` | 1–361 | 361 |
| `anna_seeded_labels.csv` | 362–708 | 347 |

Originally the 758-row sample stored at `82b6268` was split into records 1–380 in `3bba769:minseo_seeded_labels.csv` and records 381–758 in `3479280:anna_seeded_labels.csv`. Commit `c6696a3` adds resumable ranged manual coding; the first Minseo label commit explicitly names the 1–380 range. These are PR data-record positions, not spreadsheet/text line numbers. Repository removals and replacement subsequently changed the files and current positions.

Current repository ownership is:

| Researcher | Repository IDs (original file spelling retained) |
| --- | --- |
| Minseo | apache/airflow; apache/couchdb; apache/arrow; cilium/cilium; cloudnative-pg/cloudnative-pg; curl/curl; django/django; easybuilders/easybuild; fedify-dev/fedify; ghostty-org/ghostty; go-gitea/gitea; Homebrew/brew; iree-org/iree; joomla/joomla-cms; kubernetes/kubernetes; kornia/kornia; apache/kvrocks; llvm/llvm-project; matplotlib/matplotlib. |
| Anna | NetBSD/src; numpy/numpy; napari/napari; ocaml/ocaml; ory/oathkeeper; openedx/openedx-platform; pandas-dev/pandas; cybertec-postgresql/pgwatch; qgis/qgis; scipy/scipy; pola-rs/polars; processing/p5.js; qutip/qutip; searxng/searxng; sympy/sympy; MDAnalysis/mdanalysis; kiali/kiali; stan-dev/stan; cuthbertLab/music21. |

Each repository contributes 20 PRs except easybuilders/easybuild (1, Minseo), NetBSD/src (3, Anna) and ory/oathkeeper (4, Anna). The exact membership is the current seeded sample joined to the coder files by `Sample ID`; a researcher name inferred from file ownership is not a per-PR signed review record.

The combined reference has 282 disclosure-present and 426 absent labels. Classifications are 188 Positive, 78 Negative, 17 Ambiguous and 425 None. Sixteen Ambiguous rows have presence `Yes` and one has `No`. These values are preserved as stored and are not silently reconciled with methodology that describes `Unclear` for ambiguity. The presence comparison therefore measures agreement with the saved human presence field, not a newly harmonized interpretation of Ambiguous.

No human–human kappa can be estimated from the two seeded files as stored because they have **zero shared IDs**. Detector–human agreement on 708 PRs must not be presented as human inter-rater reliability.

## Coding instructions versus evidence of actual practice

`methodology/cohens_kappa_workflow.md` prescribes separate independent coding without discussing classifications before coding is complete, followed by kappa and then disagreement review. `methodology/validation_protocol.md` similarly describes independent coding and recording final decisions and rationale. These are available instructions, not logs proving those steps occurred.

The sample CSVs contain `Script AI Disclosure Present` and detected-text columns. The methodology permits script-detected text as evidence to inspect. The current `KappaWorkflow.printSample` interactive display prints sample ID, repository, PR number, URL and title without detector predictions; inspecting source is not proof this interface was used rather than the unmasked CSV. There is no inspected session record establishing which material each coder saw. Human–human independence and blinding to detector predictions are distinct questions, both requiring evidence beyond file agreement.

Actual coding dates are not stored as row fields. Git provides these file-history dates, which must not be substituted for coding-session dates:

| Artifact event | Git author date |
| --- | --- |
| Earliest Anna / Minseo files | 8 July / 16 July 2026 |
| Revised Anna / Minseo files | 22 July 2026 (`20d8e58` / `9e8f0da`) |
| Revised consensus update | 23 July (`24515b4`) |
| Current paired-file renames | 16 September (`0f46bee`) |
| Initial seeded Minseo / Anna files | 11 September (`3bba769` / `3479280`) |
| Seeded label rechecks | 14 September (`374c4f9`, `767ef70`) |
| Current seeded Minseo / Anna file last edits | 16 October (`44c2808` / `9f8d541`) |

The current coding framework last changes at `e3dc0ee` on 11 August. Earlier paired label artifacts predate it, and framework snapshots changed during July as well. The source blobs in [evidence_checks.md](original_working_snapshot_hash_checks.md) identify instructions present at selected commits, but do not prove the exact revision supplied to coders.

## Adjudication and label revisions: available evidence

For the retained 150-row sample, all consensus rows are `Agreed`; there are no `Resolved`/`Needs Resolution` statuses or consensus rationales. Anna has one nonempty coder note and Minseo has five. No separate disagreement/adjudication log is present among the tracked repository files inspected. The same all-agreed structure exists in the earliest archived consensus. This establishes the saved agreement state, not an independently documented adjudication process or the absence of discussions outside Git.

For the 708-row sample, the union of coder files matches all combined labels and notes. There are 165 nonempty notes (133 Minseo, 32 Anna), but no explicit second-coder/adjudicator/date field. Between `c0b7407` and `767ef70`, two Minseo label pairs and one Anna label pair change; the Minseo notes also change on one shared row. Those diffs establish revisions during rechecking, without identifying the reviewer, rationale for each revision, timing relative to detector inspection, or an independent second rating. Repository removal/replacement changes are separately traced in [C09](detector_result_version_history.md).

The validator implementation explains the misleading output column names: `ConsensusWorkflow.validateDetectorAgainstManualLabels` maps a single file's `Disclosure Present` and `Disclosure Classification` into internal consensus-named fields and calls validation without requiring a consensus status. This code path can produce `Consensus ...` columns from singly coded labels. The current inputs/results are consistent with that mapping; the exact executed historical command is not logged.

### Historical 200-row extension

At `a26a3df`, `anna_extended_labels.csv` contains 200 labels. Every row in `extended_consensus_labels.csv` has empty Coder B presence/classification, `Agreement Status = Resolved`, and `Consensus Notes = Single-coder consensus from anna_extended_labels.csv`. This is explicit evidence of a single-coder reference, despite the consensus filename and status. Instructions mention `minseo_extended_labels.csv`, but no such CSV path was found in reachable local history. Its absence from inspected history does not establish that no external copy or coding work exists.

The 350-row combined evaluation therefore combines a paired 150-row consensus with a singly coded 200-row extension. It must not be described as 350 independently double-coded PRs.

## Evidence-bounded wording for a future report

The following describes the stored artifacts and still requires review against any additional team records:

> The retained reliability exercise contains 150 PRs from three repositories, with labels in separate Anna and Minseo files. The stored labels agree on both disclosure presence and classification for all 150 PRs (recalculated Cohen's kappa 1.0 for each). A separate 708-PR detector-validation reference combines 361 Minseo-labelled and 347 Anna-labelled PRs with no overlapping IDs. That reference is singly coded per PR; it is not a 708-PR inter-rater reliability or dual-coder consensus dataset. The inspected repository does not establish actual coding dates, masking conditions, or a separate adjudication record.

## C10 acceptance assessment and unresolved information

The supplied checklist, page 5, requires the report to attribute kappa only to the paired sample and not describe a merged file as dual-coder consensus without separate adjudication evidence. Its requested changes also include preservation of original files, coding dates/instructions/disagreements/decisions and masking detector columns. **C10 is not complete.**

| Requirement | Assessment |
| --- | --- |
| Preserve and identify original/revised coder files | Verified current and historical paths/hashes; all originals untouched. |
| Distinguish paired reliability from divided validation | Verified and documented, including the earlier replaced sample and single-coder 200-row extension. |
| Coding dates and actual instructions | Commit dates and available instruction versions identified; session dates and instructions actually used remain unresolved. |
| Independence and detector masking | Intended process documented; actual blinding/independence is not established by inspected records. |
| Disagreement/adjudication decisions | Stored all-agreed consensus and label diffs described; no separate log recovered. Do not invent resolutions, meetings, or reviewer identities. |
| Final report correctly attributes kappa/consensus | Evidence-bounded draft wording supplied; no final report in the tracked snapshot to verify. |

## Recheck after Anna's C01–C08 changes are merged

1. Preserve this baseline and compare coder, sample, consensus and validation hashes at the merged revision. Any changed labels need a new version and an evidence-linked explanation.
2. Locate existing dated coding-session records, the exact instructions supplied, whether detector fields were hidden, and any independence attestations. Identify retrospective recollections explicitly if original records are unavailable.
3. Locate any original disagreement list, before/after labels, adjudicator, decision dates and evidence-linked rationale. Do not treat `Agreed`, `Resolved` or consensus-named columns as substitutes.
4. Check whether an independently coded `minseo_extended_labels.csv` or a second review of seeded PRs exists outside the inspected history. If supplied, verify its IDs, dates and relation to the stored results before changing this description.
5. Reconfirm that only the paired 150 supports the reported human–human kappa, that 361+347 is described as divided work, and that the 350-row extension is characterized accurately. Review final report wording before C10 sign-off.
