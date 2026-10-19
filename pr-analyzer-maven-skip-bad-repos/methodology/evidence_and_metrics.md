# Evidence and metric contract (2026-10-19)

This contract governs new analyzer exports. Historical CSVs and results in this repository have not been recollected or retrospectively validated. They must not be relabelled as complete evidence or final compliance. Resume and correlation reject legacy schemas without evidence coverage; collect to a new output file. Keep original datasets and coder labels for audit.

## Source coverage: PR_BODY_ONLY_V1

The automated measurement is **disclosure-like statements in the GitHub API PR body at collection time**. It is not a measurement of all discussion, actual AI use, or undisclosed AI use. Comments, reviews, commits, and full-page HTML are outside this measurement. HTML is still fetched for diagnostics; its success or failure cannot supply a disclosure or erase usable body evidence.

A nonblank API body with PR URL, author login, and creation timestamp is Complete for this scope. Empty/null bodies or missing attribution metadata are Incomplete and have disclosure status Unknown, even if HTML was fetched. This deliberately treats an empty body conservatively. Complete means source coverage, not verified detector accuracy. A complete body without a match is No *detected statement in that body*, not a human-verified absence across the PR. No inference about AI use follows from No or Unknown.

Every detected statement has its excerpt, source `PR body`, stable source ID `Repo#PR:body`, PR URL, associated PR author, and PR creation timestamp in the main dataset. The analysis metadata gives the collection interval. The creation timestamp is not a statement/edit timestamp: the current API body can have been edited, including by maintainers. Human review must verify attribution and temporal relevance before using it in compliance analysis. A historical snapshot or immutable evidence link should be retained by reviewers when available.

Automated coding rules:

- Explicit use and explicit non-use both count as disclosure presence, with `possible_positive` and `possible_negative` classifications respectively.
- Markdown blockquotes (including lazy continuation), HTML blockquotes/pre blocks, fenced code, and HTML comments are excluded. Maintainer quotations are not the contributor's statement. Unmarked quotations and subtle authorship remain human review tasks.
- Full-page maintainer/third-party statements never count, regardless of body availability.
- Unchecked boxes and unfilled fields are unanswered templates, not negative disclosures.
- Checked policy acknowledgements alone are `policy_acknowledgement`, not statements of use or non-use. In particular, Homebrew's “did not use … or disclosed” disjunction is not proof of non-use; its completed tool field can provide a use statement. Repository-specific template rules remain heuristics requiring review.
- Contradictory use/non-use answers are `possible_ambiguous`; preserve evidence and resolve them manually. Ambiguous matches may enter the automated presence count, but cannot establish compliance without an explicit reviewed assessment.

## Export fields and review inputs

The original PR metadata is retained. `AI Disclosure Present` now supports Yes, No, Unknown. `Disclosure Classification` is the automated category (`possible_positive`, `possible_negative`, `possible_ambiguous`, `policy_acknowledgement`, `none`, or Unknown). `AI Disclosure Required` remains an unresolved legacy field and is never used to calculate compliance.

| Field | Values / meaning |
| --- | --- |
| Evidence Completeness | Complete or Incomplete under the stated coverage rule |
| Evidence Source / ID / URL / Author / Created At | Provenance of the PR body; see timestamp and attribution limits above |
| Source Coverage Rule | PR_BODY_ONLY_V1 |
| HTML Scrape Success / Error | Diagnostic fetch outcome; HTML is excluded from detection |
| Review Status | MANUAL_REVIEW_REQUIRED by default; manually set Reviewed after evidence and policy review |
| Policy Acknowledgement Only | Yes for acknowledgement-only classification; No does not establish that no acknowledgement exists alongside a use statement |
| Policy Requirement | Unresolved (default), All contributors, If AI used, None |
| Policy Effective From / Through | Inclusive YYYY-MM-DD interval supported by historical policy evidence; both bounds required |
| Policy Source URL | Versioned or archived policy evidence supporting the requirement and interval |
| Policy Coded At / Policy Coder | YYYY-MM-DD coding date and coder identity |
| Reviewed AI Use | Yes, No, Unknown; independent human assessment supported by cited evidence |
| Reviewed Disclosure Present | Yes, No, Unknown; human assessment, separate from detector output |
| Reviewed Compliance | Compliant, Noncompliant, Unresolved; assessment of the actual policy obligations, not copied from presence |
| Review Evidence URL / Reviewed At / Reviewer | Evidence supporting the assessment, YYYY-MM-DD review date, reviewer identity |

Policy applicability is evaluated at **PR creation in UTC**, separately from the collection window based on `closed_at`. If a policy's relevant date differs, or its historical scope cannot be established, leave the row unresolved instead of forcing it into this model. Coding/review dates must not precede PR creation. Only closed or merged rows with Bot=No are eligible for the reviewed workflow; the input must already represent the intended study window.

Review a copy of the new dataset, preserving the automated fields. Do not invent policy requirements for the existing unresolved main dataset. Run:

```bash
mvn package
java -jar target/pr-analyzer-maven-1.0.0.jar --policy-compliance reviewed_pr_dataset.csv reviewed_compliance_summary.csv
```

The workflow requires complete body coverage, Reviewed status, dated applicable policy coding, and reviewer/evidence fields. Policies requiring all contributors to answer can include both reviewed use and non-use answers. For “If AI used”, only Reviewed AI Use=Yes is applicable; No is not applicable, and Unknown is unresolved regardless of disclosure absence. A compliant assessment also requires Reviewed Disclosure Present=Yes. A reviewed noncompliant assessment can reflect missing or insufficient disclosure. The software does not infer either assessment from the detector.

## Metric definitions and uncertainty

| Metric | Eligible population / denominator | Numerator | Uncertainty treatment |
| --- | --- | --- | --- |
| Automated Disclosure Rate | Complete-evidence PRs in the collected human closed-PR study population | Complete rows with detected use, non-use, or ambiguous disclosure-like statements | Unknown/Incomplete excluded from numerator and denominator and reported as Unknown Disclosure Count; all rows require review. Empty denominator gives a blank rate. Not compliance or AI-use prevalence. |
| Positive / Negative / Ambiguous counts | Same complete-evidence population; these are counts, not independent rates | Matching automated classification | Preliminary categories, mutually exclusive; acknowledgements counted separately. |
| No Disclosure Count | Same complete-evidence population | Complete rows without a detected use/non-use/ambiguous statement, including acknowledgement-only bodies | Never includes Unknown; does not establish undisclosed AI use or verified absence. |
| Reviewed Policy Compliance Rate | Reviewed, dated-policy-applicable, evidence-complete rows assessed Compliant or Noncompliant | Rows assessed Compliant | Report unresolved, not applicable, and excluded counts separately. Unknown AI use under conditional policies is unresolved. A rate with unresolved rows is labelled Provisional; a zero denominator is blank. |
| Visibility correlation | Repositories with a nonempty complete-evidence denominator and a unique policy-tracker match with numeric visibility | Spearman association between visibility and Automated Disclosure Rate (no compliance numerator) | Unknown PRs excluded, failed/excluded counts reported; unmatched/ambiguous repositories and undefined correlations excluded/reported by the existing correlation workflow. |

Rates describe the observed/reviewed subset. They do not impute missing evidence, prove population-wide compliance, or estimate undisclosed AI use. Report missingness and review coverage alongside every rate; unresolved cases may be systematically different. No sampling confidence interval is claimed by these descriptive outputs. Existing correlation significance is exploratory and does not correct detector error, missingness, selection bias, or multiple comparisons. Re-run validation and analysis after source-scope changes; do not reuse previous detector performance estimates as if they tested this version.

## Verification

`mvn clean test` discovers the regression harness through JUnit plus focused resume, evidence, and policy tests. CI runs that command and uploads its log and Surefire reports. The archived passing log and deliberate-assertion-failure log are in `methodology/evidence/`; the temporary failing test is removed before the clean passing run.
