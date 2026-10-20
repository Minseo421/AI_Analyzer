# Reproduce the research

Keep `research_compendium/` and `pr-analyzer/` as sibling directories. The commands below verify preserved outputs; a new GitHub collection uses today's repository state and is a different dataset.

1. **Identify the cohort and parameters.** Read [full repository analysis](../full_repo_analysis/README.md) for the repository list, stored collection timestamps, exclusions, columns and denominators.
2. **Verify independent PR coding.** Follow [IRR](../irr/README.md) to join the 150 selected IDs, compare the two coder files and calculate kappa from saved labels.
3. **Verify detector evaluation.** Follow [detector validation](../detector_validation/README.md) for the historical 350-PR and later 708-PR samples, reference labels, predictions and metric commands. These populations remain separate.
4. **Build and test the software.** Follow the [analyser README](../../pr-analyzer-maven-skip-bad-repos/README.md). It documents both Maven and the standalone harness, plus new data collection.
5. **Recalculate quantitative outputs.** Run the saved-data correlation and report verification commands in [full repository analysis](../full_repo_analysis/README.md). Preserved final figures and their report mapping are there too; the available plotting script regenerates only the older 40-repository figures.
6. **Audit governance coding.** Read the [canonical sheet and codebook](../governance_coding/README.md). From the analyser directory, run `python3 scripts/build_governance.py` to recreate or byte-verify the canonical CSV and its transformation ledger from the preserved raw export and final repository/PR inputs. Existing differing files are rejected. Its legacy embedded path strings remain unchanged for provenance; it does not access the original author's computer.

[provenance.json](provenance.json) retains the prior audits' historical sample/result tables, hashes and associated source revisions, plus commit/path/hash identifiers for the restored 350-PR evidence. Historical source snapshots do not identify the actual executed build. Old paths in these historical records are locators within the named Git revision, not current runnable commands.

[reports/](../reports/) preserves Anna's PDF, Minseo's DOCX, the available meeting-minutes DOCX and reference poster. Minseo's five Results tables and twelve figure PNGs have a stored verification route. Anna's report mapping and the exact submitted versions/dates of both reports remain unverified.

Remaining evidence gaps are the original API/HTML archives and dated full policy snapshots, complete repository-selection records, some sample seeds/run configurations, coding-session/masking records, per-row detector versions and final subtype lineage, flagged governance decisions, and source scripts for the final figures. Saved-data checks do not resolve these gaps or establish policy compliance.
