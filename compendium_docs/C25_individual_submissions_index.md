# C25 — Individual submissions within the joint Research Compendium

**Status: open.** Prepared 20 October 2026 (Pacific/Auckland) at repository revision `fa38ab8e0be0e0dd27ee5043da6ae04bb7bfb488`, with the existing C15/C19 documentation and governance release. Anna's C01–C08 changes have not been integrated.

The researchers have confirmed that Anna and Minseo wrote separate individual Final Reports and intend a joint Research Compendium. Shared research evidence and each student's submitted assessment files must remain distinguishable. This index does not claim that either located draft is the exact submitted version or that Anna's report has been inspected.

## Requirement and proposed packaging

The supplied `Project_78_Rubric_and_Compendium_Checklist.pdf`, C25, page 10, requires each student's **exact submitted files** in a named directory, with filename, assignment, submission/version date and authorship, plus a joint contribution statement linking work to artifacts and decisions. Acceptance requires a reviewer to distinguish the joint work from individual submissions. Its separate-compendia assignment-comment condition is conditional; the current requested arrangement is one joint compendium.

At minimum, include each student's exact individual Final Report. Confirm the complete set of other individually submitted deliverables from the assignment/Canvas records: this workspace does not establish their required names or whether the candidate earlier reports are exact submissions. Do not substitute a coder file, a research dataset or a similarly named draft for an assessment submission.

| Proposed directory relative to the eventual compendium root | Intended contents | Current status |
| --- | --- | --- |
| `09_individual_submissions/anna/` | Anna's actual exact submitted files, preserved under their verified original filenames, with assignment/version/submission metadata and report-to-shared-evidence mapping. | Proposed only; no report supplied or packaged. |
| `09_individual_submissions/minseo/` | Minseo's actual exact submitted files, preserved under their verified original filenames, with assignment/version/submission metadata and report-to-shared-evidence mapping. | Proposed only; located report remains external; exact submitted status not confirmed. |

These directories have not been created or populated by this task. No original file has been moved, renamed, copied into a submission directory or deleted. The names above are proposed organization, not invented names of submitted artifacts. Final-report numbering is never used as the shared research identifier.

## Actual files verified and limits of verification

| Student / material | Actual existing file or evidence | What is established | What is not established |
| --- | --- | --- | --- |
| Minseo — individual Final Report | `/Users/minseokim/Desktop/P4P/Report/Minseo Kim Final P4P .docx` | Researcher-identified as Minseo's report; actual DOCX inspected in C19, including five Chapter 4 tables and twelve Chapter 4/Appendix A figure PNGs. SHA-256 `3196e5e5f828722aacb85d0b549aa9d07d483635bc58e551a4fce9ce5063fb97`, rechecked during this update. Exact path includes the space before `.docx`. | Whether this byte version is the exact submitted file, assignment identifier, submission/version date, required ancillary submissions and final authorship/contribution declaration. It is not packaged in the repository. |
| Anna — individual Final Report | No actual report supplied or identified for this audit. | Researcher confirms a separate report exists. | Actual filename/path/hash, contents, submission/version date, assignment identifier, authorship declaration, numbering, structure, figures/tables and presentation of shared analyses. No Anna report mapping is verified. |
| Individual coder allocations — shared research evidence | `pr-analyzer-maven-skip-bad-repos/anna_seeded_labels.csv`; `minseo_seeded_labels.csv`; paired `anna_labels_kappa_sample.csv` and `minseo_labels_kappa_sample.csv` in the same directory. | C10 records the actual file relationships: final validation labels are divided 347 Anna / 361 Minseo with no shared IDs; retained 150 sample has both named coder files. | These are shared research evidence, not individual Final Reports or proof of the complete contribution history, coding independence procedure or exact Canvas submissions. |

The local report directory also contains the located candidates `Mid_year_report_Minseo.pdf`, `Minseo Kim - Literature Review Report.pdf`, `Project Scope, Objectives, and Literature Review Report.docx`, `Project Scope, Objectives, and Literature Review Report_APA.docx`, `Project Scope, Objectives, and Literature Review Report_APA.pdf`, and `lit-review-draft.docx`. Their existence was checked; their contents, assignment status, authorship and exact submission/version identity were not verified for C25. Filenames alone do not establish these facts. They are **candidate records**, not accepted individual submissions, and are not silently assigned to either student's portfolio.

## Linking individual reports to shared research

Use the [shared research-artifact index](shared_research_artifacts_index.md) and its [machine-readable counterpart](shared_research_artifacts_index.json) as the joint evidence register. Shared IDs describe the dataset, method, validation evidence or numerical analysis, independently of a report's numbering or presentation. Retain source paths/versions and checksums alongside the IDs; historical development samples remain separate from later evaluation/main data.

For each actual report, record a separate mapping containing student/report identity, exact file hash, that report's local section/table/figure locator, the shared artifact ID(s), verification status and any interpretation or reproducibility limits. Distinguish citing shared evidence from authoring it; a report mapping alone does not establish individual contribution.

- **Minseo:** retain the verified 17 mappings in [C19](C19_figures_tables_reproducibility.md) and its [manifest](C19_figures_tables_manifest.json), explicitly scoped to `minseo-final-report`. C18's RQ references and Section 3.5 primary/exploratory designations also belong to his report. Numeric agreement does not imply fully reproducible plots or verified policy compliance.
- **Anna:** obtain the actual report and inspect its real contents, then create an independent mapping to shared IDs. The current machine-readable registry intentionally has a null file path/hash and no verified output mappings. Do not copy Minseo's locators or assume identical results presentation, structure or primary/exploratory labels.

The shared 38-repository dataset and reported numerical results remain unchanged. The final governance view is available but not fully policy-validated; its row-level flags and C15 provenance remain applicable to both reports. No individual report can establish per-PR policy compliance merely by referencing the observable-disclosure rates.

## Joint contribution statement: evidence and missing confirmation

The compendium needs a researcher-confirmed joint contribution statement linking each student's work to concrete artifact versions and decisions. C10's label-file allocations and the researcher's statement about Anna's pending C01–C08 work are available evidence with limited scope. They do not establish the complete division of repository selection, governance coding, detector development, statistical analysis, visual authoring, interpretation, writing or release work. Do not invent responsibility for these activities.

Before release, Anna and Minseo must confirm the statement, identify supporting commits/files/decision records where available, and distinguish jointly produced research from individually authored submissions. C01–C08 implementation remains pending integration/inspection; no claim is made here that those changes are present or verified.

## Remaining C25 acceptance gaps

| Required evidence | Current assessment / action needed |
| --- | --- |
| Both students' actual exact Final Reports | Minseo DOCX located/inspected; exact submitted byte version requires confirmation. Anna's actual report must be supplied and inspected. |
| Complete required individual submission set | Confirm assignment requirements and each student's exact deliverables; assess earlier candidate files only against that evidence. No complete set established. |
| File-level identity and metadata | For each accepted submission, record exact filename, assignment, student/authorship declaration, submission/version date and SHA-256. Do not infer submission dates from filesystem timestamps. |
| Separate packaged student directories | Agree final packaging layout, then package verified files without replacing originals; validate hashes against the verified submission versions. Proposed directories are currently empty plans. |
| Separate report-to-evidence mappings | Minseo's verified mappings retained; Anna's own mappings await her report. Confirm both reports' use/interpretation of shared evidence independently. |
| Joint contribution statement | Obtain both researchers' confirmation and artifact/decision links; named coder files alone are insufficient. |
| Final review | Demonstrate that a reviewer can identify joint research and each student's exact submissions. C18/C19 limitations remain independently open. |

**C25 is not complete.** No submission date, unprovided filename, full authorship history or Anna report content has been invented. The date at the top is this documentation date, not either student's submission date.

## Recheck after Anna's C01–C08 changes are merged

Record the merged revision and compare research source/data hashes against this baseline. Update the contribution statement only from verified changes and researcher confirmation. Preserve both individual reports as separate versioned artifacts; reassess their evidence mappings independently. Merging code does not verify Anna's report, supply missing submitted files or satisfy C25 by itself.
