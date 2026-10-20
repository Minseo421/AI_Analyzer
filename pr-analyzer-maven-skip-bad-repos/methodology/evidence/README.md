# C01–C04 acceptance evidence — 2026-10-19

- [Passing clean Maven run](maven-test-pass.txt): `mvn --batch-mode clean test`, exit 0, 19 tests, zero failures/errors/skips. Includes the existing regression harness and focused contract tests.
- [Deliberate broken assertion](maven-test-intentional-failure.txt): the same command with a temporary JUnit test asserting 1 equals 2, exit 1, 20 tests and exactly one failure. The log includes the probe source; the probe was removed before the final clean run.
- CI workflow: repository root `.github/workflows/test.yml`, with the Maven subdirectory as its working directory. It preserves failures through `tee` using `pipefail` and archives the log and Surefire reports. The workflow has been added; a remote CI run has not been triggered here.

| Change | Implementation and verification |
| --- | --- |
| C01 | Surefire/JUnit discovery; ResumeContractTest covers preserved labels, appending missing IDs, completed reruns, and foreign/duplicate/missing IDs. RegressionTest executes the original harness. |
| C02 | Automated Disclosure Rate is separate from reviewed compliance. PolicyComplianceTest covers all-contributors versus conditional policies, historical dates, unresolved coding, review requirements, and reported denominators. |
| C03 | EvidenceContractTest covers missing body + HTML failure, usable body + HTML failure, exported fetch errors/provenance, Unknown counts, correlation exclusions for both input formats, and legacy schema rejection. |
| C04 | Body-only detection; fixtures cover third-party page text, maintainer quotations, unfilled templates, policy acknowledgements, use/non-use statements, and contradictions. Positives export source ID, URL, associated PR author, and PR creation date. |

The [evidence and metric contract](../evidence_and_metrics.md) documents populations, numerators, denominators, uncertainty, source limitations, and review fields. Code and fixtures are implemented; existing historical study data are not relabelled or retroactively reviewed. Dated policy coding, human evidence assessment, recollection, and rerunning empirical validation remain research work before publishing final results.

## C05–C08 — 2026-10-19

[Clean package/test log](c05-c08-maven-test-pass.txt): `mvn --batch-mode clean package`, exit 0, **36 tests**, zero failures/errors/skips. `StudyRunTest` adds 17 tests covering frozen boundary/order/seed settings, individual PR crash recovery, failed-repository retry, changed configuration/list rejection, reviewed-account gating, derived datasets, optional tracker replay, archived source/metadata integrity, source-change reporting, explicit registers, and bot validation.

The release test creates a bundle, unpacks it into a fresh directory, and invokes the included standalone JAR with an invalid HTTPS proxy. Replay reconstructs the tables without copied PR checkpoints or GitHub requests and verifies identical output hashes. The old removal directories are absent from the bundle; `git check-attr export-ignore` also reports them excluded from Git archives.

See [reproducible study workflow](../reproducible_studies.md) for commands, status definitions, register formats, and limitations. The initial UTC bounds derive from the existing dataset timestamp; seeds are explicitly new defaults. Synthetic test labels do not constitute empirical bot-rule validation. Historical API/policy snapshots and a real reviewed account sample were not supplied and have not been invented. Legacy standalone live inspection/validation tools are documented separately from the managed study path.
