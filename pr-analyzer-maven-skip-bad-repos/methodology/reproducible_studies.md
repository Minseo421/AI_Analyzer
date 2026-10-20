# Versioned study runs — C05–C08

## Start, resume, replay

```bash
mvn clean package
java -jar target/pr-analyzer-maven-1.0.0.jar --study study/study-v1.properties repos.txt study/exclusions-v1.csv runs/study-v1
java -jar target/pr-analyzer-maven-1.0.0.jar --resume-study runs/study-v1
java -jar target/pr-analyzer-maven-1.0.0.jar --replay-study runs/study-v1 runs/replayed-v1
java -jar target/pr-analyzer-maven-1.0.0.jar --bundle-study runs/study-v1 study-v1.zip
```

An unpacked bundle contains `analyzer.jar`, the run directory, and `REPLAY.txt`. It needs Java 17, but neither Maven dependencies nor GitHub access:

```bash
java -jar analyzer.jar --replay-study study replay
```

Replay recomputes eligibility, exclusions, detection, dataset, repository summary, kappa sample, and seeded validation sample from the archived responses. It does not copy processed PR checkpoints. It compares the regenerated table hashes with the originals and fails on any difference. If a frozen policy tracker is supplied, visibility summary/correlation are also regenerated and compared. This is a testable guarantee for tables produced by this managed workflow, not a claim to reconstruct older files with missing inputs.

The existing `--latest`, `--latest-pr-dataset`, `--repos`, `--repos-pr-dataset`, and `--sample-for-kappa` collection entry points now use managed runs and the versioned settings in `study/`. Their sidecar directory is `<output>.study` (kappa uses `<output>.population.csv.study`). They refuse to resume legacy CSVs without a frozen manifest. The run directory is authoritative; requested legacy CSV paths are published copies. Repository-collection COUNT is still deprecated; configured sample sizes control kappa and validation samples.

Direct single-PR inspection and the older standalone live validation/reanalysis commands remain exploratory tools. They are not the versioned study workflow and cannot establish historical reproducibility. Use the archived reanalysis command for study evidence:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --reanalyze-kappa-offline runs/study-v1 runs/study-v1/kappa_sample.csv reanalyzed.csv
```

Only PRs actually present in the frozen API responses are available; missing PRs are reported as failures. No fallback to GitHub occurs.

## C05: frozen configuration

`study/study-v1.properties` is a versioned starting configuration. Its inclusive UTC bounds are **2026-05-22T09:58:43.766425Z through 2026-09-22T09:58:43.766425Z**. These are derived from the existing dataset's recorded collection start and the old four-calendar-month rule. They are a documented default, not a newly asserted research-team decision. Review them before collecting a study.

The kappa seed `20261019` and validation seed `20261020` are explicit new defaults, not recovered historical random seeds. Sample sizes default to 50 per repository. Kappa selects three distinct repositories using the recorded seed (all repositories when fewer than three exist); each contributes up to the configured number of rows in closure-time order. Validation shuffles each repository's candidates using its recorded seed combined with the canonical repository name. Neither sampling step uses the current clock or environment seed variables.

At initialization, the run freezes:

- UTC start/end instants, inclusive boundary rule, closure field, and repository-list/closure-time/PR-number ordering.
- Exact repository-list and exclusion-register SHA-256 hashes and copies. Canonical repository order is the first occurrence in that list; casing and duplicate entries normalize deterministically.
- Both seeds, sample sizes, bot-rule version, detector revision, and SHA-256 over all executable analyzer classes, including nested classes.
- API base, version, accept type, pagination, timeout values, maximum attempts, retry rule, and authentication policy. Study timeout settings override timeout environment variables. Tokens are never persisted.
- Supplemental-evidence manifest and optional policy-tracker hashes and copies.

`identity.properties` records a unique dataset version and collection start separately from eligibility dates. `config.properties` is the actual frozen configuration. `--resume-study` reads these copies, not current defaults or the current clock. Re-running `--study` against an existing directory rejects different configuration/input hashes. Frozen files must not be edited. Use a new run directory for changed settings or a live recollection. A completed run is read-only and validated, not silently recollected. A different executable must use a new dataset version; bundles carry the exact executable required for replay.

GitHub is not a transactional snapshot service. First successful responses are frozen as collection progresses. A resumed live run reuses those responses and requests only missing ones. Conflicting versions of the same PR across pagination cause an incomplete run and require a new recollection rather than silently choosing a version. The deterministic guarantee is for the same archived inputs and configuration, not for live recollections on different dates.

## C06: durable progress and completion

Each completed PR gets its own record in `rows/`, written with file flush, atomic rename, and directory flush. Source responses are archived before processing. A process interruption cannot leave a partially appended CSV row. On resume, completed PR IDs and completed repositories are reused; failed repositories retry using their cached successful responses. An exclusive run lock prevents concurrent writers. Temporary files are not checkpoints.

`repositories/` records each repository's status, reason, and update time. Immutable event records in `events/` record START, RETRY, COMPLETE, REVIEW_REQUIRED, and FAILED_RETRY_ON_RESUME with the retry rule and error. API failures also have archived attempt metadata. Repeated failures remain visible and do not become exclusions automatically.

All canonical tables are rebuilt from durable checkpoints at publication, including after resume. The run status is set to RUNNING before publication, and a terminal manifest containing output hashes is written last. Thus an interruption between table replacements cannot silently publish a terminal set. No `PENDING` completion timestamp is used: incomplete tables say `NOT_COMPLETE`, and final tables carry the final collection timestamp. Table comments include dataset version and study status.

| Status | Meaning |
| --- | --- |
| RUNNING | Active or interrupted; not a finished study. Resume and inspect repository states. |
| INCOMPLETE | A repository failed or a review decision is unresolved. CLI exits nonzero. Partial tables are explicitly labelled and cannot be bundled as a completed study. |
| COMPLETE | All listed repositories were fully considered, with no exclusion decisions applied. |
| COMPLETE_WITH_EXCLUSIONS | All listed repositories were considered or explicitly excluded by the register; every exclusion is recorded. This includes ordinary out-of-window PRs. |
| REPLAY_MISMATCH | Regenerated outputs differ; not a completed replay. |

Completion describes collection, not policy-review quality or detector validity. API/HTML evidence coverage remains separately exported under the C01–C04 contract. HTML failure is diagnostic under the body-only scope; API failure or unresolved bot identity blocks collection completion. A malformed closure date is an explicit recorded exclusion, not an inferred in-window PR.

## C07: immutable evidence and changes

`evidence/objects/<sha256>` contains the original UTF-8 API/HTML response text. `evidence/responses/` maps source URL and accept type to a stable source ID, retrieval UTC instant, content hash, kind, and collection owner. PR API bodies retain PR IDs, author metadata, timestamps, and the full body. HTTP request credentials are not stored. Archived HTML failures retain their errors; offline replay reproduces them. A completed `evidence-index.properties` seals both raw objects and metadata. Missing/corrupt objects or changed metadata fail verification.

The detector's scope is PR bodies only; it does not require or measure comments. If comments are relevant to a human review, preserve their original API JSON, including comment ID, author, and creation/update timestamps, as supplemental evidence. Human-provided dated policy snapshots are imported the same way. Use `study/supplementary-evidence.csv`:

```csv
ID,Kind,URL,Retrieved UTC,File,SHA256,Owner
policy-commit-id,policy,https://github.com/owner/repo/blob/COMMIT/CONTRIBUTING.md,2026-10-19T00:00:00Z,snapshots/policy.txt,ACTUAL_SHA256,Actual reviewer
comment-123,comment,https://api.github.com/repos/owner/repo/issues/comments/123,2026-10-19T00:00:00Z,snapshots/comment-123.json,ACTUAL_SHA256,Actual reviewer
```

Paths are relative to that manifest. Supply actual content hashes, IDs, retrieval dates, and owner identities. These examples are not real evidence. Empty manifests explicitly mean no supplemental evidence was supplied. No policies or historical comments are invented. Policy applicability still requires the dated human coding defined in `evidence_and_metrics.md`.

To include the visibility tables, set `analysis.policy.tracker=../policy_tracker.csv` in a copied configuration before initialization. The tracker is frozen; its current values are not retroactive policy snapshots. Reviewed policy-compliance outputs and independently produced manual-analysis files are separate downstream artifacts; archive their reviewed inputs before making reproducibility claims about them.

For live recollection, use a **new run directory**, then compare source text:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --compare-studies runs/study-v1 runs/study-v2 source-changes.csv
```

The comparison reports Added, Removed, Changed, and Unchanged source IDs with both hashes. Do not overwrite a previous source under the same run identity.

To apply new exclusion/account decisions or detector settings to the same archived inputs, make a new derived version:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --derive-study runs/study-v1 revised.properties repos.txt reviewed-exclusions.csv runs/reviewed-v2
```

Derivation records its parent dataset and remains offline, including when resumed. It can reuse a partial run's available sources, but missing pages still make it incomplete. Conflicting supplemental source identities are rejected; use a distinct snapshot ID for a changed policy version.

## C08: exclusions and bot validation

`study/exclusions-v1.csv` replaces the one-repository hard-coded exclusion. Validate downstream files against any chosen register with `--validate-exclusions REGISTER FILE [FILE...]`; the older `--validate-study-exclusions` flag uses the versioned default. Each decision records version, scope, ID, Include/Exclude/Review action, reason, date recorded, decision owner, affected outputs, and evidence. Scopes are repository, PR (`owner/repo#number`), account, category, and release artifact. Repository decisions are applied first; within the configured window, PR decisions take precedence over account decisions and then the bot rule. Include cannot override the study's time/closure eligibility rules.

API type Bot and the `[bot]` suffix identify automatic bots. A User login containing `bot`, names resembling automation accounts, blank identities, or unknown account types require review. Human-looking names such as `roboticsfan` are never automatically labelled bots or silently counted as eligible humans in managed runs. The run remains INCOMPLETE until a reviewed account/PR decision is provided in a new configuration. One account appears once in `bot_review_queue.csv`; its affected PRs remain individually traceable in `exclusion_audit.csv`.

`exclusion_audit.csv` records IDs, categories, actions, rule IDs, reasons, dates, owners, affected outputs, evidence references, login/type, and whether the PR is in the study window. `exclusion_counts.csv` retains per-repository/category/action/window counts. `Bot PRs Excluded` counts automatic and reviewed bots **inside** the configured window; old bots outside it are counted only under window exclusions. The frozen register also retains repository decisions for repositories already omitted from the input list.

Validate the bot rule against a genuinely reviewed account sample:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --validate-bot-rule reviewed-accounts.csv bot-rule-validation.csv
```

Use `study/bot-review-template.csv` or the generated queue. Reviewed Label must be Human or Bot with reviewer, review date, and evidence. The output reports true/false bot and human counts among automatic classifications, deferred-to-review accounts separately, unreviewed rows, and the input hash. Blank/unreviewed samples are NOT_VALIDATED; partial review is labelled explicitly. Deferrals are not treated as correct predictions. Tests use clearly synthetic reviewed fixtures; they are not empirical validation of study accounts. A real reviewed sample has not been supplied in this task.

The legacy `punkpeye/awesome-mcp-servers` exclusion is carried as Review because its original rationale/date/owner are unavailable. The register's 2026-10-19 date is the migration date, not an invented historical decision date. The `mesa_removal/` and `stac_removal/` directories are historical scratch compiled artifacts; their empty before/staged folders provide no authoritative removal provenance. They are preserved locally, excluded from Git archives with `.gitattributes`, and omitted from explicit release bundles. They must not supply runtime classes or research inputs.

## Acceptance evidence

`StudyRunTest` covers exact boundary inclusion, ordering, in-window bot counts, crash after a PR checkpoint, repository failure/retry, changed configuration/list rejection, account-review blocking, reviewed derivation, named offline reanalysis, policy-tracker replay, supplemental-source and archive-metadata integrity, source comparisons, and a fresh unpacked standalone-JAR replay with network access blocked through an invalid proxy. The existing suite remains active. The passing build log is archived in `methodology/evidence/c05-c08-maven-test-pass.txt`.

Historical processed CSVs cannot recover original API bodies, missing policy snapshots, unknown past random seeds, or undocumented exclusion decisions. They remain historical artifacts; the new workflow does not retroactively certify them.
