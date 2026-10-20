# Data dictionary and dataset roles

**Final main sample:** `01_PR_and_Repository_Analysis/pr_dataset_output.csv` (41,338 rows, 38 repositories) is the frozen PR-level **derived detector output**. It was collected by the Java analyzer from GitHub closed PRs. `AI Disclosure Required` values remain unresolved; do not interpret them as verified compliance.

**Aggregation:** `repo_detected_disclosure_aggregates.csv` is per-repository detected-disclosure aggregates. `repo_visibility_summary.csv` contains repository-level visibility/rate inputs and `spearman_correlation.csv` contains the stored Spearman result.

**Repository inclusion:** `repos.txt` is the 38-repository final cohort. The initial governance sheet had 40 entries; Mesa and STAC were excluded, and a CloudNativePG identity issue corrected with documented caveats.

**Governance:** `02_Governance_Policies/governance_coding_final.csv` is the research-analysis view. 

**Validation:** `04_Manual_Validation_and_Detector_Outputs/` contains the paired 150-label evidence (two independently coded files and consensus) and the split 708-label evaluation (361 Minseo and 347 Anna with no overlap), the detector outputs and PR sample files.

**Raw upstream evidence:** original complete GitHub API response bodies, rendered HTML source archives, immutable dated full policy snapshots and failed/retried request logs were **not supplied**. This compendium does not falsely present the derived PR CSV as fully raw GitHub evidence.

**Metadata / full schema:** see `03_Research_Design_and_Methods/methodology/data_dictionary.md` and `05_Code_and_Reproduction/AI_Analyzer_project_snapshot/compendium_docs/C18_RQ_traceability.md`. Historical schema descriptions may include deprecated fields; compare against stored CSV headers.
