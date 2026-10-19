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
