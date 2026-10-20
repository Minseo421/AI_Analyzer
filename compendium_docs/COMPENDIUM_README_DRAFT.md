# Project 78: AI-Use Disclosure Governance and Contributor Compliance in Open-Source Software

**Joint Research Compendium — draft entry point, not a submitted release.** Team: Anna and Minseo; their individual Final Reports are separate submissions. Release version/date, full contributor names/roles, report/portfolio submission versions and maintenance contact remain to be confirmed. Documentation prepared 20 October 2026 (Pacific/Auckland), reviewing revision `3f5e8cd389da12b5c7792d4ab851fc2a8feb4e23`. Anna's C01–C08 work has not been merged.

This draft is stored at `compendium_docs/COMPENDIUM_README_DRAFT.md`; the existing root `README.md` is unchanged. **All paths and commands below are relative to the compendium root**, containing `compendium_docs/` and `pr-analyzer-maven-skip-bad-repos/`. Paths are printed explicitly so they remain valid when this draft is adopted as the root README and the ZIP is extracted elsewhere. No project rename or directory migration has been performed.

The compendium preserves joint research code, data, methods, human-label evidence, statistical outputs and provenance, including historical development work. It also indexes each student's individual submissions without assuming common report numbering. This is currently a research snapshot with unresolved evidence and reproduction gaps, not certification that all checklist items pass. The companion `compendium_docs/COMPENDIUM_PACKAGING_CHECKLIST.md` records the proposed release gates; no ZIP has been built or submitted.

## Research aims and measurement

- **RQ1:** How are OSS projects adapting contribution governance practices in response to AI-assisted development?
- **RQ2:** To what extent do contributors comply with AI disclosure policies through observable disclosures in OSS pull requests?
- **RQ3:** How do policy visibility and governance characteristics relate to observed AI-use disclosure rates across OSS repositories?

The stored main measurement is **detector-observed disclosure presence**, including explicit statements of AI non-use. It measures neither actual AI-use prevalence nor verified policy compliance. All 41,338 main PR rows have unresolved `AI Disclosure Required = MANUAL_REVIEW_REQUIRED`; repository requirement codes do not establish dated per-PR applicability or fulfillment. RQ1's cross-sectional policy coding describes practices but does not independently establish temporal adaptation or causation. RQ3 associations concern selected repositories, not causal effects across OSS generally.

## Start here: practical reading order

| Order | Root-relative file | What to learn |
| --- | --- | --- |
| 1 | `compendium_docs/shared_research_artifacts_index.md` and `.json` | Stable descriptive IDs for shared datasets, methods, validation and analyses; separate report mappings. |
| 2 | `compendium_docs/C18_RQ_traceability.md` | RQs → operational definitions, populations, variables, selection/window choices, methods and findings. |
| 3 | `compendium_docs/C15_governance_coding_provenance.md` | Final governance view, exact transformations, source identity correction, confirmed Curl correction and unresolved coding. |
| 4 | `compendium_docs/C09_result_provenance.md` and `C10_human_label_provenance.md` | Detector/sample versions, historical results, paired versus divided human labels and independence/adjudication limits. |
| 5 | `compendium_docs/C19_figures_tables_reproducibility.md` and `C19_figures_tables_manifest.json` | Verified Minseo-specific outputs, exact inputs/columns/calculations, reported values and reproduction limits. |
| 6 | `pr-analyzer-maven-skip-bad-repos/methodology/` and `README.md` | Research procedures and tool implementation. Some schema/example names are historical; the existing-data register below governs this snapshot. |
| 7 | `compendium_docs/evidence_checks.md` | Historical source/data checksums and read-only checks, pinned to their original revisions. |
| 8 | `compendium_docs/C25_individual_submissions_index.md` and `COMPENDIUM_PACKAGING_CHECKLIST.md` | Actual versus missing individual files, proposed packaging and C25–C28 gates. |

`compendium_docs/index.md` is the earlier C09/C10 entry point, retained as a dated historical record. Its original statements about report availability and scope describe that audit, not the later C15/C19 records. Read newer evidence alongside it; do not silently rewrite its historical checksums or claims.

## Actual folder structure

```text
./
  README.md                              Existing short command note; not replaced
  compendium_docs/
    COMPENDIUM_README_DRAFT.md            This proposed entry point
    COMPENDIUM_PACKAGING_CHECKLIST.md     Proposed release checklist and current checks
    C09_result_provenance.md              Historical detector/results provenance
    C10_human_label_provenance.md         Coder, consensus and adjudication evidence
    C15_governance_coding_provenance.md   Derived final governance release and limits
    C15_build_governance_final.py         New governance transformation/verification utility
    C18_RQ_traceability.md                RQ-to-evidence mapping
    C19_figures_tables_reproducibility.md Minseo output audit and reproduction gaps
    C19_figures_tables_manifest.json      Detailed Minseo-specific output manifest
    C19_read_only_verification.py         New offline numerical audit utility
    C25_individual_submissions_index.md   Separate student submissions and missing evidence
    shared_research_artifacts_index.md   Shared descriptive research IDs
    shared_research_artifacts_index.json Machine-readable shared IDs/report mappings
    evidence_checks.md                   Historical checks and source hashes
    index.md                             Original C09/C10 documentation index
    data/
      governance_coding_final.csv
      governance_coding_transformations.json
      historical_raw/governance_coding_40_repository_original.csv
      policy_evidence/2026-10-20_cloudnativepg_curl_policy_verification.md
  pr-analyzer-maven-skip-bad-repos/
    README.md                            Existing tool instructions, including live modes
    pom.xml                              Java 17 target; Maven build configuration
    repos.txt                            Final 38 canonical repository IDs
    *.csv                                Archived PR, label and statistical outputs below
    methodology/                         Seven research-procedure/schema documents
    src/main/java/com/example/aichecker/  25 production Java source files
    src/test/java/com/example/aichecker/  Standalone AiDisclosureDetectorHarness.java
```

This is the current layout, not the checklist's suggested future numbered-folder structure. Individual-submission directories, a release-wide file/checksum manifest, citation/reuse files and a final release archive are not present. Local `.git/`, `.env`, `.DS_Store`, build outputs and caches are outside the proposed distributable contents. Do not ZIP the workspace wholesale; use a reviewed inclusion list.

## Existing data: authoritative roles and limitations

In this table, `A/` means `pr-analyzer-maven-skip-bad-repos/` and `D/` means `compendium_docs/data/`. These are explanatory abbreviations, not actual directory names or environment variables.

| Existing file | Purpose, key fields and boundary |
| --- | --- |
| `A/repos.txt` | Final 38-repository identity/membership list. Matches canonical identities in main PR data, repository summaries and the final governance view. Original discovery/screening decisions are not fully archived. |
| `A/pr_dataset_output.csv` | Archived main processed dataset: 41,338 unique PRs, 38 repositories; 11,598 disclosure-present and 29,740 absent. `Repo`, `PR #`, `PR URL`, dates, `Bot`, `Status`, `AI Disclosure Required`, `AI Disclosure Present`, `Disclosure Classification`, `Disclosure Text`. Contains an analysis-time preamble before the true header; do not count it as PR data. Evidence snippets are not complete immutable raw API responses. |
| `A/repo_compliance_summary.csv` | Archived repository aggregates: eligible/present/absent counts, positive/negative/ambiguous counts and `Disclosure Compliance Rate`. The rate is observed disclosure despite its legacy label. Final subtype totals reproduce numerically but lack complete matching PR-level subtype lineage. This file also has a metadata preamble. |
| `A/repo_visibility_summary.csv` | Archived joined 38-repository analysis input: `Repository`, `Successfully Analysed Eligible PRs`, `PRs With Disclosure`, `Disclosure Rate`, `Visibility Score Numeric`, matching/inclusion fields. One observation per repository; includes very unequal PR denominators. |
| `A/spearman_correlation.csv` | Archived visibility/rate statistic: `Spearman Rho`, `P-Value`, `Sample Size` and related fields. Saved rho 0.6181438854, p 0.0000354223, n=38; confidence-interval cells blank. |
| `A/policy_tracker.csv` | Earlier policy-source/visibility inventory: policy links, location, notes, `Visibility Score` and mixed-format flags. Has blank export records and incomplete fields. Its `Governance Score` does not substitute for the separately coded governance dimensions. |
| `D/governance_coding_final.csv` | **Newly constructed authoritative final-38 analysis view**, not asserted to be the exact historical execution file. Includes `Repo`, `Policy URL`, `Visibility score (0-3; from tracker)`, `Disclosure detail (1 basic; 2 tool; 3 use explained; 4 trace)`, separate requirement/template/governance/rationale fields and row-level provenance/validation flags. Complete policy validation remains open. |
| `D/historical_raw/governance_coding_40_repository_original.csv` | Byte-identical preserved raw governance export, including Mesa/STAC, original identity/coding issues, footer/side summaries. Historical source, not final-analysis input. |
| `D/governance_coding_transformations.json` | Exact exclusions, identity mapping, 85 changed fields, source/final hashes and authority for the derived view. The single researcher-confirmed disclosure-detail correction is Curl 0→1; no blanket recoding is applied. |
| `D/policy_evidence/2026-10-20_cloudnativepg_curl_policy_verification.md` | Limited dated official-policy verification supporting current identity/source traceability. Contains unresolved CloudNativePG coding questions; not a historical policy snapshot valid for every PR. |
| `A/kappa_sample.csv` | Retained 150-PR paired reliability sample: 50 each from Homebrew/brew, sympy/sympy and kubernetes/kubernetes; `Sample ID`, PR identity, stored detector presence and evidence. This differs from the earlier historical 150. |
| `A/anna_labels_kappa_sample.csv`; `A/minseo_labels_kappa_sample.csv` | Both named coders' labels for those 150 IDs: `Disclosure Present`, `Disclosure Classification`, `Notes`. Retained labels agree for both fields, kappa 1.0; retained files alone do not prove masking/independent coding conditions. |
| `A/kappa_sample_consensus_labels.csv` | Retained consensus and Coder A/B fields, `Agreement Status`, `Consensus Notes`. All retained rows agree. No separate comprehensive adjudication log was identified; do not invent disagreements/resolution sessions. |
| `A/all_repo_validation_sample_seeded.csv` | Final 708-row saved validation sample/predictions: `Sample ID`, PR identity/dates, `Script AI Disclosure Present`, evidence and reanalysis fields. Up to 20 PRs per repository, with three small-sample exceptions; observed period differs from main data. |
| `A/minseo_seeded_labels.csv`; `A/anna_seeded_labels.csv` | **Divided**, not paired, labels for 361 and 347 PRs respectively, with no shared IDs. Coder ownership is evidence of these label-file allocations, not a complete contribution statement. |
| `A/all_manual_seeded_labels.csv` | Combined 708-row human reference, union of the divided label files. `Disclosure Present`, `Disclosure Classification`, `Notes`; not 708 double-coded consensus cases. |
| `A/all_seeded_detector_validation.csv` | Archived summary and detailed detector/human comparison. TP/TN/FP/FN = 278/425/1/4. Consensus-named columns do not establish dual coding or held-out independence. The file contains distinct summary and detailed CSV tables; parse the intended header. |

Final governance visibility is **0–3** (observed 1–3); disclosure detail is separately **1–4**, with N/A retained. C15 excludes Mesa/STAC, verifies the current CloudNativePG policy chain before correcting its identity, and records Curl's confirmed detail correction. Seven detail-Level-1 repositories have mean observed disclosure rate 38.60%. Authoritative sample/declared coding does not mean fully validated policy interpretation or historical applicability.

## Procedures and source-code responsibilities

The evidence flow is: policy-source inventory and manual coding → PR collection/detection → human reliability/evaluation → repository aggregation and statistical comparison → individual report presentations. The final governance view and audit scripts were added later for compendium reconstruction/verification; they are not invented historical generation records.

| Procedure / source | Responsibility and important limit |
| --- | --- |
| `methodology/methodology_workflow.md`; `repository_analysis_framework.md` | Intended repository selection, manual policy coding and study workflow. Legacy schema/scale descriptions differ from retained final fields. Use C15/C18's explicit final definitions and retain original documents as historical procedures. |
| `methodology/coding_framework.md` | PR eligibility, observable positive/negative disclosure and ambiguity. Human interpretation and policy applicability remain separate from a regex match. |
| `methodology/validation_protocol.md`; `cohens_kappa_workflow.md` | Intended independent coding, agreement and validation procedures. C10 distinguishes intent from retained procedural proof. |
| `methodology/data_dictionary.md`; `workbook_mapping.md` | Earlier schema/workbook descriptions. Named workbooks are not thereby present; no chart-bearing workbooks were recovered in the bounded C19 inventory. |
| `Main.java`; `RepoListImporter.java`; `RepoUrl.java`; `PullRequestUrl.java` | CLI dispatch and input/identity parsing. Read the actual source/help before using older tool README examples; names and sample outputs in examples are not an inventory of existing files. |
| `GitHubClient.java`; `JsonTools.java`; `HtmlTools.java`; `HtmlData.java` | HTTP/API, JSON and HTML handling. Live modes use `GITHUB_TOKEN` if provided. No credentials are needed for archived-data numerical checks. Reanalysis modes can fetch live evidence even when PR identities are fixed. |
| `PrAnalyzer.java`; `BotDetector.java`; `AiDisclosureDetector.java` | PR eligibility, bot heuristics and rule-based observable disclosure detection. Current collection uses inclusive UTC closure boundaries from analysis instant minus four calendar months, including merged and closed-unmerged PRs. The numeric legacy collection-count argument does not establish a per-repository cap. |
| `PullRequestData.java`; `PullRequestSummary.java`; `PrReportRow.java`; `DisclosureResult.java`; `CsvTools.java`; `CsvWriter.java` | Data carriers, parsing and report/dataset/summary serialization. Stored evidence/classification fields are not complete per-PR policy adjudication. |
| `KappaWorkflow.java`; `ConsensusWorkflow.java` | Human-label agreement, consensus and detector/reference evaluation. Human–human kappa and detector accuracy are separate comparisons. |
| `AllRepoValidationWorkflow.java`; `ExtendedValidationWorkflow.java`; `KappaSampleReanalysisWorkflow.java`; `SpecificPrAnalysisWorkflow.java` | Sampling, historical extension and fixed-ID reanalysis workflows. Their presence does not prove the exact historical seed/configuration/build; some operations fetch live data. |
| `DisclosureVisibilityCorrelationWorkflow.java`; `StudyDatasetExclusionValidator.java` | Joining repository policy/rates, average-rank Spearman inference and exclusion checks. Neither generates the reported visual plots. |
| `src/test/java/com/example/aichecker/AiDisclosureDetectorHarness.java` | Standalone `main` test harness with synthetic/fake HTTP fixtures and temporary files. It must be invoked explicitly; compilation or Maven lifecycle success alone is not evidence it passed. |

Methodology paths in the first five rows are under `pr-analyzer-maven-skip-bad-repos/`; Java names are under its `src/main/java/com/example/aichecker/` unless otherwise stated. The main stored closure dates span 22 May–22 September 2026, with September collection metadata retained after later edits. The 708 evaluation has observed closures May–August 2026; retained reliability data include earlier dates. See C09/C18 for exact timestamps, sample roles and unresolved original run conditions. Do not apply one nominal window to all samples.

## Shared research versus individual reports

The shared index assigns descriptive IDs such as `data.pr-disclosure-main`, `data.governance-final`, `evidence.detector-validation-708` and `analysis.visibility-rate-association`. These identify research artifacts independently of a student's report layout. Research authorship/contributions require a separate confirmed joint statement.

C19 preserves **Minseo's** verified Tables 4.1–4.5, Figures 4.1–4.6 and Appendix Figures A.1–A.6. His report is identified and hashed but remains outside this repository; exact submitted-version status is not confirmed. All C18/C19 section/table/figure references and C19's legacy `report_id`/`report` fields refer to Minseo only. The manifest links each output to shared IDs and exact data columns/calculations. His Section 3.5 designates visibility descriptives, Spearman and Cliff's delta as primary; other governance comparisons are exploratory/descriptive without additional significance tests. This does not establish Anna's report presentation.

Anna's actual report has not been supplied or verified. Her path/hash are null and her verified output mappings are empty. Do not infer that she has matching table numbers, figure numbers, sections or presentation of analyses. C25 proposes separate `09_individual_submissions/anna/` and `09_individual_submissions/minseo/` directories; neither is populated here. Exact individual filenames, assignment/date/version metadata, remaining deliverables and a researcher-confirmed contribution statement must be supplied before release.

## Offline numerical reproduction: supported now

Start in the compendium root. No Git checkout, GitHub token, live API call, external workbook or private local directory is required for the default numerical check. Python 3.12.7 was used in the recorded checks; a broader supported Python-version range has not been tested. Most calculations use the standard library; SciPy 1.13.1 was used for the optional independent p-value cross-check. Without SciPy, that cross-check is skipped; rho, the saved p value and other standard-library arithmetic remain available. No pinned full environment/lockfile is supplied yet.

```sh
python3 --version
python3 compendium_docs/C19_read_only_verification.py --help
python3 compendium_docs/C19_read_only_verification.py
```

The audit reads packaged CSVs and prints JSON to stdout; it has no file-writing, plotting or network calls. It checks unique main IDs, repository rates/counts, saved validation outcomes, numerical governance groups, Spearman rho and Cliff's delta. It emits `table_numeric_cells`, `visibility_groups`, `confusion_matrix`, `metrics`, `governance_final` and other fields. Its `Table 4.x` output keys are compatibility labels for **Minseo's** audited tables, not shared research IDs. Direct report-cell comparison is performed only when the optional `--report` argument is supplied.

Expected existing-data results include 41,338 PRs; 38 repositories; 11,598 present; TP/TN/FP/FN 278/425/1/4; rho about 0.6181438854; Cliff's delta about 0.8128654971; and governance n=38, detail-Level-1 n=7/mean 38.60%. Saved rho/p agreement tolerance in C19 is 5e-11; reported percentages are compared at their displayed precision. A successful process returns zero. This checks stored-input arithmetic, not original collection completeness, historical detector replay or visual regeneration.

### Verify or reconstruct the declared governance view

```sh
python3 compendium_docs/C15_build_governance_final.py
```

This **new** transformation utility uses the packaged `compendium_docs/data/historical_raw/governance_coding_40_repository_original.csv`, final `repos.txt` and main PR CSV. With all outputs present it verifies their exact bytes; if a derived destination is absent it creates it, and it refuses to overwrite a differing file. Expected output: 38 final rows, two exclusions, 85 field changes and `fully_validated: false`. Raw SHA-256 is `e33ee272a42c56dd49cd94dde0c1b1daaaad4d797b46540d45880bf3cdaf48e2`; final-governance SHA-256 is `8cb51b4b29ecfe68202af11502d17d876819838f5da2b8bd1077bbbbc1920921`.

The builder retains a legacy absolute source location as provenance/fallback and cross-checks that external original if it happens to exist. It first reads the packaged raw copy; the private location is **not required** when that copy is present. Do not remove the packaged raw source from a release or describe the script's absolute fallback as a portable input. This command verifies the declared later reconstruction, not the historical report-generation process.

### Optional comparison with the actual Minseo report

The audit supports `--report` for Minseo's specific Chapter 4 table/caption layout. Once his **verified actual DOCX** is packaged under the agreed C25 directory, pass its real root-relative path using that argument, quoted if it contains spaces. The existing tool's `--help` documents the option. No executable report-path example is given here because the repository does not yet contain that file. Do not create a substitute filename or pass Anna's report to this Minseo-specific parser without separately verifying compatibility.

Prior C19 checks matched all five Minseo tables numerically; Table 4.4 remains only partially reproducible because final PR-level subtype lineage is incomplete. Four tables satisfy C19's numeric-input boundary, all 12 audited figures lack their original plotting workflow, and no figure is classified as fully reproducible. Extracting existing report PNGs is preservation, not regeneration from data. Exact Word typography and original plot settings/environment are not reconstructed.

## Java build and explicit test route

Java source/target is 17 in `pom.xml`; a JDK and Maven are needed. The C14 check used JDK 21.0.7, Maven 3.9.15, macOS 15.6/aarch64. These are **current verification tools**, not established historical research-runtime versions. The POM pins compiler plugin 3.13.0 and JAR plugin 3.4.2; no Python/environment lockfile or Maven wrapper is supplied. Maven may need plugin downloads on a fresh machine. Offline Maven works only with required plugins/dependencies already cached; the cache is not part of the release.

From an extracted working copy, the supported compilation and explicit harness commands are:

```sh
java -version
mvn -version
mvn -f pr-analyzer-maven-skip-bad-repos/pom.xml test-compile
java -cp 'pr-analyzer-maven-skip-bad-repos/target/classes:pr-analyzer-maven-skip-bad-repos/target/test-classes' com.example.aichecker.AiDisclosureDetectorHarness
```

The classpath separator above is for macOS/Linux; use `;` between classpath entries on Windows. Compilation writes `target/` in that working copy; the harness uses temporary fixtures. Do not include those compiled outputs or temporary files in the release. This is the standalone harness's explicit main invocation, not a claim that Maven automatically executes it. These test commands perform no study collection.

**Current outcome:** compilation passed with cached offline Maven in an isolated copy of `src/` and `pom.xml`. The explicit harness exited 1 at line 876: `IllegalArgumentException: Existing labels file contains a Sample ID not present in the validation sample: owner/repo#1` (`KappaWorkflow.loadCompletedSampleIds`). Fake HTTP fixture log lines are not live API collection. This failure is preserved; subsequent tests after that point did not run. Recheck after Anna's changes; do not describe the current test suite as passing or compilation as full C27 reproduction.

The tool-specific README documents additional CLI modes, including collection, interactive coding, correlation and reanalysis. They are not prerequisites for the offline checks above and are not frozen-data reproduction commands here. No live collection or detector reanalysis command is included in this guide because current external evidence and rolling dates can change outcomes. Any later new collection/evaluation must be versioned separately and must not replace the archived results.

## Historical research, supporting records and future work

C09/C10 and `evidence_checks.md` preserve exact historical Git object paths/hashes for earlier 150/350 evaluations, detector changes, seeded revisions and successive correlation outputs. Those are development/provenance records, not additional independent replications of the final sample. Some historical CSVs exist only in Git objects, not the checkout. **A ZIP excludes `.git/`, so commit references alone are insufficient:** selected historical bytes must be exported into a separately labelled archive with hashes and an index before claiming a self-contained compendium. Do not restore old results over current files.

C19 identifies external older rationale/source exports, earlier reports, blank data-entry/schema files and the earlier failed harness review. These are candidate supporting/history materials, not automatically final-analysis inputs or exact individual submissions. The source checklist and retrospective commit-history PDFs were inspected in earlier audits and are not redistributed in this repository. Relevant project plans, meeting/action records, journals, literature/scope work, decisions, diagrams and applicable media still need an evidence-backed inventory. No physical equipment, CAD or material-calibration files are identified in this software-study snapshot; confirm applicability rather than inventing them.

Submission repairs take priority: resolve exact build/evidence provenance, policy-coding validity and historical applicability; obtain the actual individual submissions and plotting workflows; restore final subtype lineage; fix and execute tests; then demonstrate independent fresh-copy reproduction. Future research is separate: validate additional policies/time periods, investigate unequal repository denominators and confounding, improve disclosure fixtures with error examples and documented rule rationale, and assess longitudinal policy change only with dated evidence. These are proposals, not analyses already performed or changes to reported results.

For later extensions, begin with the stable artifact ID and data contract in C18/C15, then add documented fixtures to the standalone harness, preserve old rule/results versions under C09, and write new outputs separately. A new policy rule or exploratory test must not be silently backdated into the archived study. Confirm the research definition and historical evidence before interpreting an absent disclosure as noncompliance.

## Reuse, access and maintenance

A release license, data/third-party reuse conditions, restricted-material access route and maintenance contact are not established by the current files. Confirm them before distribution; public repository links alone do not establish unrestricted redistribution rights for every copied policy, PR excerpt, report or media item. Include only materials with a documented purpose/source and appropriate permission/access explanation. Do not package `.env`, tokens, credential files, caches, `.git/` or unnecessary binaries; archived-data checks need none of them.

Some historical manifests retain machine-specific source paths as provenance, and their checksums must remain intact. They are not required reproduction inputs. Before C26 approval, provide portable release path mappings and decide how the archival records meet the checklist's machine-specific-path requirement without silently rewriting history. All operational inputs in this guide are packaged relative paths; unresolved external materials are explicitly pending.

Team roles/contact, agreed maintenance arrangement, full submission metadata and the final release citation/version/date must be filled from researcher-confirmed facts. No submission date is inferred from this documentation date or filesystem timestamps.

## Release status and recheck after Anna's C01–C08 changes are merged

C09, C10, C15, C18, C19 and C25 remain open for the reasons in their individual assessments. **C14 is a draft only:** the root README is unchanged and both individual submissions/all output procedures cannot yet be located from a complete packaged release. C26–C28 are preparation work, not completed archive/security, independent reproduction or submission checks.

After integration, record the new revision, compare code/data/configuration hashes and re-run the affected checks without overwriting this baseline. Reconcile source and methodology/schema drift, verify the harness and each report's independent evidence mapping, and retain all historical provenance. Use `compendium_docs/COMPENDIUM_PACKAGING_CHECKLIST.md` to supply missing material, stage only approved contents, review the extracted archive independently and record the actual final ZIP size/hash and intended submission. No code merge alone supplies missing plots, historical policy evidence or Anna's report.
