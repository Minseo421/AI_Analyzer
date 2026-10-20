# C09 and C10: snapshot evidence record

Prepared 20 October 2026 (Pacific/Auckland) against Git revision `9f8d541718d5c72a16ce88987aef385e43c4281f`.

This directory documents the repository available before Anna's unpushed C01–C08 work. It does not certify a final study release. Both **C09 and C10 remain open**: the available files establish substantial provenance, but do not establish all detector/run versions, independent coding conditions, or final-report wording required by the checklist.

## Reading order

| File | Purpose |
| --- | --- |
| [C09_result_provenance.md](C09_result_provenance.md) | Samples, detector development, result versions, dates, input lineage, limitations, and C09 acceptance assessment. |
| [C10_human_label_provenance.md](C10_human_label_provenance.md) | Original coder files, revised paired sample, divided validation work, consensus evidence, and C10 acceptance assessment. |
| [evidence_checks.md](evidence_checks.md) | Read-only check results, reproducible checks, current SHA-256 hashes, historical artifact hashes, and source/version identifiers. |

Paths in the two provenance documents are relative to `pr-analyzer-maven-skip-bad-repos/` unless stated otherwise. Historical references use `commit:path`; Git preserves those contents even when the file is absent from the current checkout. Stable PR identity is the case-normalized `Repo#PR` key; original files retain their spelling and ordering.

## Evidence and scope

**Verified** means directly inspected in local Git history, source, or CSV contents, or recalculated from those contents. **Recorded account** means a statement in a supplied PDF or methodology document, distinguished from proof that the procedure occurred. **Unresolved** means the inspected materials do not establish the fact; it does not mean the work was never performed.

The supplied `Project_78_Rubric_and_Compendium_Checklist.pdf`, page 5, supplies the C09/C10 criteria used here. `Commit_history_and_explanation.pdf` supplies a retrospective account, checked against Git rather than treated as a run log. Their hashes are recorded in [evidence_checks.md](evidence_checks.md). Instructions elsewhere in those PDFs are background and are not additional tasks authorized by this request. In particular, checked C01–C08 boxes in the PDF do not establish that Anna's changes exist in this checkout.

The inspection used local files and Git objects. No live GitHub collection, Java execution, research-result rewrite, original CSV edit, source/test/pom edit, or existing README edit was performed. New persistent repository files are confined to this directory. Temporary PDF text extraction and calculations were performed outside the repository. The attached PDFs are not redistributed here.

## Acceptance status

| Item | Evidence now available | Why still open |
| --- | --- | --- |
| C09 | Exact stored sample membership, hashes, historical result chain, associated source revisions, observed dates, and identified limitations. | Exact generating builds/configurations and coding-framework versions are not established for every output; held-out status and compatibility of historical pooled results are unsupported; no final report is present to audit every accuracy claim. |
| C10 | Paired 150-row files and consensus are distinguished from the disjoint 361+347 labels; historical single-coder extension is identified. | Actual coding dates, masking/independence records and any separate adjudication log are not established; final-report attribution cannot be checked here. |

## Recheck after Anna's C01–C08 changes are merged

1. Keep this record pinned to `9f8d541`; identify the merged revision separately and compare hashes and paths before updating any claims.
2. Match any new study configurations, evidence snapshots, run manifests, exclusion records, and detector fixes to the historical results documented here. Newly archived evidence is not proof of the original execution date or build unless it records that linkage.
3. Reconcile sample membership, coder ownership, rule versions, and counts if new outputs are supplied. Distinguish historical results from new evaluations, and preserve both records.
4. Use the item-specific recheck lists in C09 and C10 before considering checklist sign-off. Integrating these documents into a renamed compendium or the root README is deferred.
