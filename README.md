# AI_Analyzer

**AI-Use Disclosure Governance and Contributor Compliance in Open-Source Software**  
**Researchers:** Anna Chibel and Minseo Kim  
**Supervisor:** Dr. Kelly Blincoe, University of Auckland (2026)

## Project overview

This research investigates how open-source software (OSS) projects govern AI-assisted contributions and how contributors disclose AI use in GitHub pull requests (PRs). It combines automated analysis of PR descriptions with manual coding of repository policies, examining disclosure requirements, policy visibility, contributor responsibilities, and documented enforcement measures.

We developed a rule-based Java analyser to identify statements reporting AI use, explicitly reporting non-use, or providing an ambiguous AI-related response. The final study examined **41,338 eligible PRs across 38 repositories**, identifying observable disclosure evidence in **11,598 PRs (28.1%)**. These results measure recorded declarations, not the prevalence of actual AI use or verified compliance with repository policies.

## Repository structure

```text
AI_Analyzer/
├── README.md
├── pr_analyser/          # Analyser source code and utilities
└── research_compendium/  # Research data, documentation and reports
```

| Directory | Contents |
| --- | --- |
| [`pr_analyser/`](pr_analyser/) | The Java/Maven application used to collect and analyse GitHub PRs, identify AI-use disclosures, and run supporting validation and analysis utilities. Includes implementation and tests. |
| [`research_compendium/`](research_compendium/README.md) | Research records and outputs: IRR labels, detector-validation samples, full-repository results, governance coding, figures, reports, and reproducibility documentation. |

## Where to start

For the **research methodology, datasets, findings, and supporting evidence**, begin with the [research compendium README](research_compendium/README.md). Its subdirectories document the individual stages of the study.

For the **analyser implementation, building, testing, and execution**, see [`pr_analyser/`](pr_analyser/). To reproduce calculations from preserved research outputs, consult the [reproducibility guide](research_compendium/reproducibility/README.md).

The preserved datasets represent historical research snapshots. Re-running GitHub data collection may produce different results as repositories and PR descriptions change.
