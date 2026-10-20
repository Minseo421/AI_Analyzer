# RESEARCH COMPENDIUM — Project 78

## AI-Use Disclosure Governance and Contributor Compliance in Open-Source Software

**Joint research project:** Minseo Kim and Anna Chibel; supervisor Dr. Kelly Blincoe; Faculty of Engineering and Design, The University of Auckland.  
**Compendium assembly:** 20 October 2026.  
**Release status:** PROVISIONAL

## Study summary and research interpretation

38 deliberately selected GitHub OSS projects with documented AI-related contribution policies were analysed. The PR study covers 41,338 eligible closed PRs from 22 May to 22 September 2026, using a fixed timestamp cutoff (09:58:43 UTC). There were 11,598 detected AI-related disclosures (28.06%); this includes **positive, negative, and eligible ambiguous AI-related responses**, not independently verified AI use. The 708-label detector evaluation was split (361 Minseo, 347 Anna); the separately paired 150-PR inter-rater sample had κ = 1.00. This does **not** establish dual coding for 708 PRs. Spearman's ρ = 0.6181 (n=38) characterises an observational association, not a causal effect.

The research **does not verify policy compliance for each PR**. The exported `AI Disclosure Required` field remains `MANUAL_REVIEW_REQUIRED`; absence of a declaration does not prove nondisclosure of actual AI use or a policy violation. Governance policy snapshots and coding are not independently time-aligned with every PR.

## Recommended reading order

| Folder | Contents | How to use it |
|---|---|---|
| `01_Project_Overview_and_Records/` | Project summary, available meeting notes, Git history, poster reference | Start with scope, milestones and provenance |
| `02_Individual_Submissions/` | Minseo Final Report; separate Anna placeholder, individual submission index | Verify exact student-provided Canvas documents before final upload |
| `03_Research_Design_and_Methods/` | Seven original methodology files, RQ–evidence map, governance coding and policy-source verification | Learn selection criteria, coding framework and analysis units |
| `04_Research_Data/` | Final 38-repo cohort, 41,338 PR output, governance coding, 150/350/708 validation evidence, historical 40-row governance source | Inspect archived data and coded inputs |
| `05_Code_and_Reproduction/` | Complete clean Java source-project snapshot, Maven config, stored data, rule-based detector, tests, Python verification scripts | Build software and rerun **saved-data audits** |
| `06_Figures_and_Analysis/` | Minseo's final 12 figures as embedded in her DOCX, vector sources, 17-artifact report manifest, shared research index, secondary analysis notes | Trace joint study results to report-specific figures |
| `07_Research_Provenance_and_Quality/` | Detector history, independent human-label record, hash checks and original governance transformation history | Understand limitations and changes in methodology |
| `08_Historical_Experiments_and_Future_Work/` | Earlier 40-repo plots and script clearly marked historical, future research priorities | Inspect work not in final report and extension opportunities |
| `09_Release_Verification/` | Verification results, file inventory and pending-file notice | Check release completeness and reproducibility boundaries |

### Where the two reports fit

This joint compendium archives one **shared research evidence base**. `06_Figures_and_Analysis/shared_research_artifact_index.md` defines descriptive analysis IDs, independent of report page/table numbering. The Minseo-specific index maps her figures and tables to that common evidence. The **Anna report mapping cannot be completed without Anna's actual report**; no figure numbers or conclusions are inferred for her.

## Reproduction: saved data, without fetching GitHub

Prerequisites: Python 3.10+ (only Python standard library is needed for the stored-data audit); Java 17 and Maven for compiling/running the original analyzer.

Execute from the extracted **Compendium Submission** root:

```bash
# Verify exact reported numerical table cells against Minseo's supplied DOCX and stored inputs:
python3 05_Code_and_Reproduction/AI_Analyzer_project_snapshot/compendium_docs/C19_read_only_verification.py \
  --repo 05_Code_and_Reproduction/AI_Analyzer_project_snapshot \
  --report 02_Individual_Submissions/Minseo_Kim/Minseo_Kim_Final_P4P.docx

# Recreate or byte-verify the canonical 38-repository governance CSV/ledger from preserved original 40-row source:
python3 05_Code_and_Reproduction/AI_Analyzer_project_snapshot/compendium_docs/C15_build_governance_final.py
```

The C15 source uses its own `pr-analyzer-maven-skip-bad-repos/repos.txt` and frozen PR dataset. The snapshot's canonical governance CSV is UTF-8 without a byte-order mark to match the original transformation ledger; an **identical-value UTF-8 BOM Excel copy** is provided in `04_Research_Data/02_Governance_Policies/`. Both CSV versions preserve all 38 records and governance codes. The provided archive has no `.env` or live GitHub token.

The saved-data audit reproduces the report's Table 4.1–4.5 numeric cells from frozen CSVs. **It does not regenerate the original GitHub collection** and does not prove final image plotting-code reproducibility. The final 12 images are preserved byte-for-byte from the report's Oct-17 output material; earlier plotting source under `08_...` was based on a 40-repository analysis and is not a substitute.

### Software build and tests

```bash
cd 05_Code_and_Reproduction/AI_Analyzer_project_snapshot/pr-analyzer-maven-skip-bad-repos
mvn test
```

Maven test success by itself may **not run the standalone main-based `AiDisclosureDetectorHarness`**, because the supplied original project lacks a verified harness execution binding. The unmerged uploaded version was reported to fail one harness expectation near line 876. The team must merge and verify Anna's newer C01–C08 implementation and capture a clean test log; this provisional release makes no passing-harness claim.


**Before submitting:** see `09_Release_Verification/IMPORTANT_BEFORE_CANVAS_SUBMISSION.md`, add the missing individual files, verify the full team contribution statement, rerun tests after merging latest code, and rename the final ZIP using the **actual University usernames** in `78_usernameA_usernameB-Compendium.zip` format.
