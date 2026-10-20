# Governance Coding Sheet — README

## Purpose and scope

`governance_coding_final.csv` records the manual coding of AI-related contribution policies for **38 open-source repositories**. Each row represents one repository. The sheet describes **written governance requirements and their presentation**, not whether contributors complied with them or whether maintainers enforced them in practice.

## Coding procedure

Repository identities were matched to the study dataset, and relevant policy documents, contribution guidelines, and PR templates were consulted. Codes were assigned from **explicit policy wording** (and, for the `PR template` field, the template itself): ordinal fields record the highest applicable requirement or consequence supported by the policy; binary fields record whether a provision was identified (1) or not identified (0). Multiple rationale categories could be selected for one repository. Where wording was conditional or unclear, the intended interpretation was documented in notes rather than inferred from PR behaviour.

## Field definitions

| Field(s) | Coding rule |
| --- | --- |
| `Repo`, `Repository URL`, `Policy URL` | Repository identifier and links to its repository and policy source. |
| `Visibility score (0-3; from tracker)`, `Visibility source note` | Policy discoverability, carried over from the visibility tracker: **0** no policy; **1** external/organisation-level source; **2** in-repository guidance; **3** PR-workflow integration. Notes clarify the source where provided. |
| `Disclosure detail` | Detail **required by the policy**, not detail actually supplied by a contributor: **1** disclose AI use; **2** name the tool; **3** explain how it was used; **4** provide detailed traceability. `N/A` means no applicable disclosure-detail level was assigned. |
| `PR template` | **1** the PR template contains an AI-use disclosure box/field; **0** it does not. This is a binary check for an actual disclosure box in the template, **separate from policy visibility**. A policy link or mention without a disclosure box is not sufficient for **1**. |
| `AI in discusion` | **1** a restriction on AI-written communication with reviewers/maintainers was identified; **0** no such restriction was coded. The original column spelling is retained. |
| `Responsibility` | Contributor duties: **0** none; **1** accountable for submitted work; **2** must explain/justify work; **3** must review, test or validate AI-assisted work. |
| `Attribution` | Rights and provenance requirements: **0** none; **1** general intellectual-property duty; **2** licence/DCO/CLA compatibility; **3** explicit provenance documentation. Tool naming alone belongs under disclosure detail. |
| `AI-use constraints` | Restrictions beyond disclosure: **0** none; **1** bulk/low-effort submissions restricted; **2** AI use restricted in specified contexts; **3** general prohibition. |
| `Stated consequence` | Consequences **specified in policy**, not observed actions: **0** none; **1** correction/additional review; **2** rejection/closure; **3** future participation restriction. |
| `Disclosure in PR required`, `Disclosure in commit required` | Independently coded **1** only where disclosure is expressly required in the PR or commit respectively; otherwise **0**. |
| `Rationale: ...` (six columns), `Other rationale text` | Independent **1/0** flags for explicitly stated transparency, code quality, reviewer workload, human accountability, rights/provenance, and other reasons. The text field records the stated *other* rationale. |
| `Evidence URL(s)`, `Evidence excerpt / coding justification`, `Coding notes` | Supporting references, quotations and interpretive notes where recorded. |
| `Source CSV Record`, `Source Dataset SHA256` | Original row reference and source-dataset fingerprint for traceability. |
| `Identity Verification Status`, `Coding Validation Status`, `Unresolved Coding Fields`, `Validation Notes`, `Researcher Confirmed Corrections`, `Policy Evidence Record` | Audit metadata showing identity checks, the extent of revalidation, unresolved questions, confirmed changes and any separate evidence record. |

## Interpretation and verification limits

Most governance codes were **carried forward rather than independently revalidated** during preparation of this final CSV. The sheet flags outstanding verification issues, including those concerning CloudNativePG; the Curl disclosure-detail correction is explicitly recorded as researcher-confirmed. Evidence excerpts and coding notes are incomplete for many rows, and linked policy pages may have changed since the PR sampling period. Accordingly, the sheet should be read alongside the linked policies, available evidence records and study methodology; **a coded requirement does not establish historical applicability, actual disclosure or enforcement**.
