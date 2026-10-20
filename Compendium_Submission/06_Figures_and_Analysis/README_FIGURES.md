# Charts, tables and analysis records

`Minseo_Report/Main_Figures/` contains exact rendered figure images matching the six figures embedded in **Minseo's supplied individual Final Report** (byte-identical PNG checks). `Minseo_Report/Appendix_Figures/` similarly preserves the six appendix figures; see `Minseo_report_figure_index.csv`.

The ZIP also includes the charts' SVG and PDF vector versions when present. Their existence does not establish the original plotting commands used for the paper.

`Minseo_report_reproducibility_audit.md` and the JSON manifest document the 17 numbered Minseo tables/figures. These are *report-local identifiers*, NOT identifiers for Anna's separate report.

`shared_research_artifact_index.*` identifies joint results using descriptive IDs for use across both students' reports. The original manifest stores absolute historical computer paths; use the packaged paths in this README and figure index for portable navigation.

Original plotting workflows for all 12 submitted figures were not located in the primary source snapshot. An earlier `oss_generate_nine_figures.py` file is provided under `08_Historical_Experiments_and_Future_Work` but references an older **40-repository** dataset, and must not be claimed to regenerate these final figures.
