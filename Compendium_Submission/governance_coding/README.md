# Governance coding

[governance_coding_final.csv](governance_coding_final.csv) is the canonical manual coding sheet for the 38 repositories listed in [repos.txt](../full_repo_analysis/repos.txt). It contains repository-level policy observations used for the report's governance counts and grouped disclosure comparisons. The researchers reviewed the coding collaboratively; this was not an independent governance IRR study.

Coding draws on linked contributor guidance, PR templates and policy documents. Minseo’s report specifies the highest applicable visibility level when guidance appears in several places; Level 3 includes AI-policy notices or links and does not require an explicit disclosure question. Each row retains URLs, excerpts/justifications, notes and validation flags. Blank evidence fields are missing records, not proof of absence. Current policy links do not establish what applied to every historical PR.

| Columns | Interpretation |
| --- | --- |
| Repo, Repository URL, Policy URL | Repository identity and policy source |
| Visibility score (0–3) | 0 no identifiable policy; 1 external/separate guidance requiring discovery; 2 repository documentation without a direct PR-workflow requirement; 3 AI guidance displayed during PR submission (template, form or checklist) |
| Disclosure detail | 1 basic declaration; 2 tool identified; 3 use explained; 4 detailed traceability; N/A retained separately |
| PR template | 1 present, 0 absent; distinct from a disclosure requirement |
| Disclosure in PR / commit required | Separate 1/0 policy codes; not per-PR applicability decisions |
| Responsibility | 0 none; 1 accountable; 2 explain work; 3 review/testing required; N/A as recorded |
| Attribution | 0 none; 1 general IP duty; 2 licence/DCO; 3 documented provenance |
| AI-use constraints | 0 none; 1 bulk/low-effort; 2 specific contexts; 3 general prohibition |
| Stated consequence | 0 none; 1 correction/review; 2 rejection/closure; 3 participation restriction; N/A separate. This codes written provisions, not observed enforcement |
| Six rationale flags | 1 stated, 0 absent: transparency, quality, reviewer workload, accountability, rights/provenance and other; multiple flags are permitted |
| Other rationale text, evidence, coding notes | Supporting wording and rationale |
| Source/validation metadata | Original row/hash, identity checks, unresolved fields and confirmed corrections |

The source header ` AI in discusion` is retained verbatim; its definition is unresolved and it is excluded from the final verifier's group comparisons. Do not substitute the earlier tracker's composite Governance Score or obsolete 0–4 visibility/enforcement definitions for these variables.

The canonical sheet derives from the preserved [40-repository export](../reproducibility/governance_sources/governance_coding_40_repository_original.csv). The [change ledger](../reproducibility/governance_sources/governance_coding_transformations.json) records all 85 cell changes: identity/URL standardisation, policy-URL whitespace, and Curl's researcher-confirmed detail correction from 0 to 1. Mesa/STAC are excluded to match the final cohort. Other coding values, including three N/A detail entries, remain unchanged. The [annotated policy tracker](../reproducibility/governance_sources/policy_tracker_reviewed.csv) is a distinct source/review record, not a second canonical coding sheet.

## Policy review

The preserved 20 October 2026 review supported CloudNativePG identity through its [contributing guide](https://github.com/cloudnative-pg/cloudnative-pg/blob/main/CONTRIBUTING.md), [shared guidelines](https://github.com/cloudnative-pg/governance/blob/main/CONTRIBUTING.md) and [AI policy](https://github.com/cloudnative-pg/governance/blob/main/AI_POLICY.md). It did not establish applicability to CNCF Landscape. Attribution=0, constraints=0, commit requirement=0 and consequence=2 remain flagged for review; the conditional PR requirement also needs interpretation. No codes were changed to resolve those uncertainties.

The same review retained [Curl's policy source](https://curl.se/dev/contribute.html#on-ai-use-in-curl); its issue/security-report and PR scopes differ. Curl's detail correction was researcher-confirmed, not inferred from numerical agreement. Neither this correction nor the identity check validates all other codes or their historical applicability.

The CSV and ledger retain the legacy `compendium_docs/data/policy_evidence/2026-10-20_cloudnativepg_curl_policy_verification.md` identifier to preserve exact original bytes; that record is now consolidated in this section. The ledger's other old paths identify the original export layout. Use the linked files here and the [reproduction workflow](../reproducibility/README.md) for current locations and verification.
