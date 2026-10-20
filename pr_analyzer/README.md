# PR disclosure analyser

Java collector and rule-based detector for observable AI-use/non-use disclosures in GitHub pull requests. Research datasets, coding records and methods are canonical in the sibling [Compendium_Submission](../Compendium_Submission/README.md).

## Build and tests

Requires JDK 17 and Maven. The Java project has no external runtime dependencies.

```sh
mvn package
mvn test
mkdir -p target/test-tmp
java -Djava.io.tmpdir="$PWD/target/test-tmp" \
  -cp target/classes:target/test-classes com.example.aichecker.AiDisclosureDetectorHarness
```

Commands run from this directory. Maven compiles the standalone main-based harness but does not execute it automatically; the `java` command is required. The classpath separator is `;` on Windows.

**Known baseline failure:** the full harness stops at line 876 because its overwrite test expects `IOException`, while resumed coding rejects a label ID absent from the sample with `IllegalArgumentException`. This was reproduced before the organisational cleanup; later harness checks are not reached. The separate `--homebrew-only` harness mode runs the Homebrew detector checks. Source and test logic were preserved.

## Collect a new dataset

Set `GITHUB_TOKEN` in the environment for authenticated GitHub requests. Unauthenticated requests are supported with lower rate limits; no token or `.env` loader is bundled. Repository inputs accept one GitHub URL or `owner/repo` per line; blanks and `#` comments are ignored.

```sh
mkdir -p output
java -jar target/pr-analyzer-maven-1.0.0.jar --repos-pr-dataset \
  ../Compendium_Submission/full_repo_analysis/repos.txt \
  output/pr_dataset.csv output/repository_summary.csv
```

Outputs go to the paths supplied on the command line. Use a new output path for a new collection. Repository collection includes every closed human-authored PR within the inclusive rolling four-calendar-month `closed_at` window, anchored to the run's UTC start. Results are ordered by closure time and PR number descending. The legacy numeric COUNT argument is accepted but ignored; it is not a way to cap a pilot run. The API's page size of 100 is only pagination.

Multi-repository runs flush results incrementally and skip failed repositories. Reusing a nonempty output resumes by skipping existing `Repo#PR` rows, but uses a new run timestamp/window and does not rewrite the optional repository summary. Inspect logs for skipped repositories; a successful process exit alone does not certify completeness. The CLI does not expose a fixed historical timestamp for full collection.

For a single named PR:

```sh
java -jar target/pr-analyzer-maven-1.0.0.jar https://github.com/OWNER/REPO/pull/NUMBER
```

Replace the URL with the required PR. Run the JAR with no arguments to print all supported modes and their argument syntax.

## Other workflows

- [IRR verification](../Compendium_Submission/irr/README.md): `--calculate-kappa` compares saved coder files; `--create-consensus` retains agreements and flags disagreements for manual resolution.
- [Detector validation](../Compendium_Submission/detector_validation/README.md): saved-prediction comparison and combined metrics. `--reanalyze-kappa-sample` and targeted retry modes fetch live evidence for fixed IDs.
- [Research calculations](../Compendium_Submission/full_repo_analysis/README.md): `--visibility-correlation` accepts PR data or repository aggregates. `scripts/verify_results.py` uses Python 3.10+ standard-library checks, with optional SciPy verification.
- [Governance reconstruction](../Compendium_Submission/governance_coding/README.md): `scripts/build_governance.py` uses Python 3.10+ and preserved source data; it verifies existing destination bytes.
- Historical plots: `scripts/plot_historical.py --output NEW_DIRECTORY` uses NumPy, SciPy, Matplotlib and Pillow. It reads the historical 40-repository baseline, not final study data.

Sampling modes are exposed in CLI usage. `--sample-for-kappa` selects three repositories using `KAPPA_SAMPLE_SEED` or a random seed; the current collection path does not enforce the nominal per-repository count. `--sample-for-extended-validation` reads saved PR data, with default seed `20260724`. `--sample-all-repos-validation` requires explicit start/end dates and seed. Interactive coding resumes existing labels and skips already-coded IDs; unknown IDs are rejected. These interfaces do not prove the parameters used for historical samples.

## Configuration and limits

`GITHUB_CONNECT_TIMEOUT_SECONDS` and `GITHUB_REQUEST_TIMEOUT_SECONDS` override the 15-second connection and 60-second request defaults. `DETECTOR_VERSION` labels combined-evaluation metadata; setting it does not establish which executable generated stored predictions.

Implementation is in [src/main/java/com/example/aichecker](src/main/java/com/example/aichecker/): `GitHubClient` handles retrieval/retries, `PrAnalyzer` selects PRs and gathers text, `AiDisclosureDetector` applies rules, and workflow classes handle CSV operations and statistics. The harness uses synthetic responses and temporary CSVs.

Disclosure detection is heuristic. Explicit no-AI statements count as disclosures; filenames and page chrome alone should not. Policy applicability is not inferred. `AI Disclosure Required` remains `MANUAL_REVIEW_REQUIRED`; stored data may contain mixed classification formats. Bot filtering is heuristic, source text can change, and exact-PR modes do not reapply full-dataset eligibility. See the research section READMEs for the preserved evidence and limits of each result.
