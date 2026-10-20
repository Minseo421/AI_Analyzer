# C15 — Final governance dataset and transformation provenance

Prepared 20 October 2026 (Pacific/Auckland), research snapshot `fa38ab8e0be0e0dd27ee5043da6ae04bb7bfb488`. **C15 remains open:** final sample membership and the explicitly confirmed correction are established; all policy coding is not independently validated.

## Authoritative analysis view and historical source

The researcher requested a separate authoritative governance dataset containing exactly the 38 repositories in the final report. The new [governance_coding_final.csv](data/governance_coding_final.csv) is that compendium analysis view. It is not represented as the original historical file used during report generation or as a fully validated dataset.

The full original 40-repository export is preserved byte-for-byte as [historical_raw/governance_coding_40_repository_original.csv](data/historical_raw/governance_coding_40_repository_original.csv). Original external source: `/Users/minseokim/Desktop/P4P/GPT/repository_dataset(Governance_Coding_Sheet).csv`. The raw copy retains its footer, repeated/blank export headers and right-hand summary columns. Neither original nor historical copy is a final-analysis input; the raw copy is retained for provenance and explicit transformation replay.

| Artifact | SHA-256 / role |
| --- | --- |
| Original/historical raw | `e33ee272a42c56dd49cd94dde0c1b1daaaad4d797b46540d45880bf3cdaf48e2` |
| Final 38-repository CSV | `8cb51b4b29ecfe68202af11502d17d876819838f5da2b8bd1077bbbbc1920921` |
| [governance_coding_transformations.json](data/governance_coding_transformations.json) | Every changed source field, before/after value, source CSV record, reason and authority; exclusions, row mapping, input hashes and final hash. |
| [C15_build_governance_final.py](C15_build_governance_final.py) | New deterministic transformation utility. Existing differing destinations are rejected rather than overwritten. Re-running on the same inputs verifies byte identity. |
| [Policy verification record](data/policy_evidence/2026-10-20_cloudnativepg_curl_policy_verification.md) | Dated official-policy link chain, source URLs, limited verification findings and unresolved coding questions. |

## Every transformation

1. **Preserve the raw export exactly.** The historical/raw copy has the same hash as the external original. It is retained as source evidence, not substituted for the final data.
2. **Select the final population.** Exclude `mesa/mesa` and `stacdev/stac` because they are absent from final `pr-analyzer-maven-skip-bad-repos/repos.txt` and `pr_dataset_output.csv`, as required by the researcher's instruction. The historical exclusion commits are `44c2808` and `9f8d541`, respectively. This does not invent a policy-based exclusion rationale.
3. **Verify and correct CloudNativePG identity.** The raw display name `CloudNativePg Landscape` and URL `https://github.com/cncf/landscape` are corrected to `cloudnative-pg/cloudnative-pg` and its canonical repository URL only after checking the official target-repository contributing guide → shared governance guide → AI policy chain. See the linked evidence record. This verifies current project applicability, not the policy's historical effective date or every existing code.
4. **Apply only Curl's confirmed coding correction.** For `curl/curl`, `Disclosure detail (1 basic; 2 tool; 3 use explained; 4 trace)` changes from `0` to `1`. The researcher explicitly confirmed that 0 was an earlier coding/data-entry error and that Level 1 was the final decision used in the report. Record authority: researcher confirmation in this session, 20 October 2026. This resolves the uncertainty about intentional recoding; it is not inferred from matching results.
5. **Standardize identifiers and URL whitespace.** `Repo` uses the exact final `repos.txt` spelling; `Repository URL` is canonical; surrounding whitespace is trimmed from `Policy URL`. No policy destination is changed by the trimming. Each changed cell is logged, not hidden behind a blanket cleaning claim.
6. **Produce an analysis-only table.** Retain the first 24 named governance/evidence columns; omit export-side summary columns, blank headers and footer from this derived view only. Preserve all 40 source rows/columns in the historical/raw file. Keep final rows in `repos.txt` order and serialize UTF-8/LF CSV.
7. **Append provenance/validation metadata.** `Source CSV Record`, `Source Dataset SHA256`, `Identity Verification Status`, `Coding Validation Status`, `Unresolved Coding Fields`, `Validation Notes`, `Researcher Confirmed Corrections`, `Policy Evidence Record` distinguish identity checks, confirmed corrections and carried-forward coding. These are new audit metadata, not invented governance observations.

There are **85 logged field changes**, limited to repository identities/URLs, policy-URL surrounding whitespace and Curl's one disclosure-detail cell. No other governance coding value changes. All three original disclosure-detail N/A values remain N/A; no general 0→1 conversion is applied. The ledger is authoritative for the exact row-by-row change list.

## Coding definitions and limits

The final report's Tables 3.2–3.3 and Section 3.3 provide the recorded category definitions. The CSV retains the original explicit headers so each code remains tied to its source variable.

| Variable | Allowed analysis values / interpretation |
| --- | --- |
| Visibility | `0`–`3`, where higher values describe more direct contributor-facing visibility; final sample has only 1–3. Not interchangeable with enforcement. |
| Disclosure detail | `1` basic declaration; `2` identify tool; `3` explain AI use; `4` detailed traceability; `N/A` for non-classifiable requirements. Curl is the single confirmed correction. |
| PR template / PR requirement / commit requirement | Separate binary 0/1 fields. Template presence is not identical to an explicit AI-disclosure requirement, and a repository requirement code is not a per-PR applicability decision. |
| Responsibility | 0 none; 1 general accountability; 2 explain/justify work; 3 review/testing required; N/A where coded. |
| Attribution | 0 none; 1 general IP duty; 2 licence/DCO/CLA; 3 documented provenance. |
| AI-use constraints | 0 none; 1 bulk/low-effort restrictions; 2 specific contexts; 3 general prohibition. |
| Stated consequence | 0 none stated; 1 correction/review; 2 rejection/closure; 3 participation restriction; N/A kept separate. Written provisions do not establish actual enforcement. |
| Six rationale indicators | Separate 0/1 flags: transparency, code quality, reviewer workload, human accountability, rights/provenance, other stated. Multiple codes per repository are permitted. |
| ` AI in discusion` | Retained verbatim from the source for provenance. Its meaning is not established here; it is not used as a final analysis factor or substituted for a defined requirement field. |

No `Governance Score` or `Enforcement Score` is invented in the final dataset. The older policy tracker's composite and old methodology columns do not replace the report's separate governance dimensions. Fields without a complete definition or evidence trail remain flagged rather than assumed validated.

## Validation status and unresolved coding

Every final row explicitly indicates that its complete coding has not been independently revalidated. Most values are carried forward, not newly verified against all current or historical policy sources. Some original evidence excerpts/notes are blank or contain placeholder source descriptions; these are preserved rather than filled with invented text.

CloudNativePG's **identity** is supported by current official sources, but its row flags possible coding conflicts in Attribution, AI-use constraints, commit-disclosure requirement and stated consequence, plus interpretation of the conditional PR requirement. Those values are deliberately unchanged pending coding review. Curl's **detail correction** is researcher-confirmed; the rest of Curl's coding and its historical applicability are not thereby validated. Current policy verification is dated new evidence, not a historical policy snapshot.

The dataset is therefore authoritative for final sample identity and declared analysis values, while **not fully validated** for policy correctness, historical applicability or verified contributor compliance. The ledger has `fully_validated = false` and `historical_policy_applicability_verified = false`.

## Checks and reproducibility

Final canonical repository identifiers are unique and equal the sets in both `repos.txt` and the 41,338-row PR dataset. Mesa/STAC are absent. There are exactly 38 data rows. Source coding cells are unchanged except Curl's confirmed correction; the transformation ledger records all identifier/whitespace changes.

Using the final CSV joined to archived repository rates gives **seven Level-1 repositories**, mean `38.60496654224786%`, which reproduces the report's **38.60%**. The three N/A detail rows remain separate. All Table 4.2 category counts and all five reported tables' numeric cells match the existing final report. This numerical agreement is a consistency check, not validation of all governance codes.

```sh
python3 compendium_docs/C15_build_governance_final.py
python3 compendium_docs/C19_read_only_verification.py \
  --report '/Users/minseokim/Desktop/P4P/Report/Minseo Kim Final P4P .docx'
```

The builder creates or verifies the separately named derived artifacts without replacing originals; the C19 utility reads the final governance CSV by default. The original 40-row source is not used for final numerical analyses. Full source/command hashes and updated artifact mapping are in [C19](C19_figures_tables_reproducibility.md) and its [manifest](C19_figures_tables_manifest.json).

## Remaining C15/C19 acceptance gaps

Curl's intentional correction and the final 38-row membership are resolved. Current CloudNativePG project identity is verified. Remaining work includes coding-review decisions for its flagged fields, a complete evidence-backed codebook for all retained analysis columns, dated policy snapshots/applicability, other rows' validation and missing evidence, and the C19 plotting workflows. Neither C15 nor C19 is marked complete.
