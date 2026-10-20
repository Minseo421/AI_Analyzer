# Source code and reproducible offline checks

`AI_Analyzer_project_snapshot/` contains the Java 17 / Maven project's 25 production Java sources, standalone test harness, seven methodology documents, the stored inputs/outputs, and the original technical provenance documents. Its internal folder names and C09/C10-like audit filenames are preserved as **historical technical evidence**, not presented as this Compendium's top-level organisation.

The snapshot includes an exact **canonical UTF-8 no-BOM** governance CSV that matches the recorded governance transformation ledger; the Excel-friendly BOM copy from the supplied upload is kept separately in `04_Research_Data/02_Governance_Policies/`. Only encoding differs; the 38 repository codes do not.

From the **Compendium Submission root**:

```bash
python3 05_Code_and_Reproduction/AI_Analyzer_project_snapshot/compendium_docs/C15_build_governance_final.py
python3 05_Code_and_Reproduction/AI_Analyzer_project_snapshot/compendium_docs/C19_read_only_verification.py \
  --repo 05_Code_and_Reproduction/AI_Analyzer_project_snapshot \
  --report 02_Individual_Submissions/Minseo_Kim/Minseo_Kim_Final_P4P.docx
```

These are new compendium **audit/reconstruction utilities**, not original historical collection commands. They verify saved study outputs; they do not reproduce the entire GitHub API collection offline.

For Java source, see `AI_Analyzer_project_snapshot/pr-analyzer-maven-skip-bad-repos/README.md` and `pom.xml`. A clean compilation does not prove that the standalone main-based harness passes. The uploaded snapshot previously exhibited a harness failure at line 876, and may not include Anna's later local C01–C08 improvements. Do not claim a passing test suite without rerunning it on the final integrated version.

Live GitHub collection requires credentials and can return changed upstream content; **no secrets are included** and live recollection is not equivalent to replaying the historical September 2026 snapshot.
