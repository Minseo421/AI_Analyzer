# Read-only evidence checks and version identifiers

Reviewed 20 October 2026 (Pacific/Auckland), source revision `9f8d541718d5c72a16ce88987aef385e43c4281f`. This is the baseline for [C09](C09_result_provenance.md) and [C10](C10_human_label_provenance.md); both checklist items remain open.

## Method and limits

Local CSV parsing used Python 3.12.7; the independent correlation comparison used SciPy 1.13.1 on macOS-15.6-arm64-arm-64bit. Git was read with `git log`, `git show`, `git diff`, `git status` and `git rev-parse`. PDF text was read locally using macOS PDFKit. No Java commands, tests, live GitHub calls, or data-collection commands were run.

CSV files were parsed with the CSV parser, not counted by physical text lines. Main dataset/summary metadata preambles were skipped by finding the actual header. Validation outputs contain summary and detailed tables; the `Sample ID` header identifies the detailed table. Blank policy rows were counted separately. PR keys were compared case-insensitively as `Repo#PR`, preserving original files and spelling. Dates below come from stored contents and Git; no missing coding dates were inferred.

Initial `git status --short` was empty. Final scope verification is recorded at the end of this file. A zero mismatch count verifies file consistency, not source authenticity, human independence, or validity of the study design.

## Checks performed

| Check | Result |
| --- | --- |
| Current paired sample coverage | 150 unique IDs in each sample/coder/consensus file; identical ID sets; 50 PRs each from Homebrew, SymPy and Kubernetes. |
| Paired human agreement | 150/150 agreement for presence and classification; kappa 1.0 for each. Expected chance agreement: 0.5355555556 and 0.4123555556, respectively. |
| Retained consensus | 150 Agreed rows; no missing consensus labels; 0 consensus notes; Coder A notes match Anna, Coder B notes match Minseo. |
| Earliest archived pair | 150 matched IDs; presence and classification kappa both 1.0; a different sample from the current 150. |
| Current 150 detector comparison | TP 78, TN 55, FP 0, FN 17; agrees with archived `24515b4:detector_validation.csv` detailed rows. |
| July extension / pooled detailed counts | Extension 200: 15/140/0/45; combined 350: 93/195/0/62 (TP/TN/FP/FN); no shared IDs between 150 and 200. |
| Extension candidate population | 200/200 IDs occur in `a26a3df:pr_dataset_output_policy_cleaned.csv`; all 200 script-presence flags agree. This supports a candidate input, not an exact run invocation. |
| Seeded coder ownership | 361 Minseo + 347 Anna; 0 shared IDs. Current sample records 1–361 and 362–708 respectively. Original 758-row ownership: 1–380 and 381–758. |
| Combined seeded labels | Exact union by ID, PR metadata, label fields and notes; 0 missing/extra IDs and 0 field mismatches. |
| Seeded sample / labels / detailed output | Each has 708 unique IDs, identical sets; all reanalysis statuses Success; canonical sample IDs and PR URLs agree. |
| Current 708 detector comparison | Recomputed TP 278, TN 425, FP 1, FN 4; all 708 saved detailed outcomes, script values, manual values and classifications agree. |
| Historical/current sample overlaps | Earlier July 150 vs revised July 150: 0; revised July 350 vs current 708: 0; earlier July 150 vs current 708: 4. |
| Evaluation/main overlaps | Current 708 vs main: 576; revised July 150 vs main: 138; July 350 vs main: 338. These are ID overlaps, not an independence test. |
| Cross-output prediction discrepancy | Among 576 shared seeded/main IDs, exactly one presence value differs: cilium/cilium#46332, seeded No vs main Yes. Cause unresolved. |
| July sample preservation | Current `kappa_sample.csv` is byte-identical to `24515b4:kappa_sample.csv`; all 150 records/fields unchanged. |
| September-to-current seeded sample | 688 shared IDs, 50 removed, 20 added; 0 changes among shared IDs for presence, detected text and Closed Date. |
| Main unique IDs / totals | 41,338 PR records, 41,338 unique canonical IDs, 38 repositories; Yes 11,598 / No 29,740. |
| Repository summaries | All 38 per-repository total/present/absent counts match main PR rows; aggregate 41,338 / 11,598 / 29,740. Visibility summary has 0 count mismatches. |
| Repository membership | Main data, compliance summary, visibility summary, seeded sample and repos.txt have the same 38 canonical repositories. |
| Policy rows | 38 populated rows and 455 empty CSV records after the header (493 total). Original blanks preserved. |
| Policy-to-analysis join | A strict link-only join covers 37 repositories. Stan has a blank Repository Link and matches by its canonical Repo value `stan-dev/stan`; Gitea's original score has trailing whitespace trimmed in the summary. After those source-supported normalizations, all 38 identities, names/links, visibility values, numeric scores and inclusion flags agree. This checks stored coding consistency, not the underlying policy evidence. |
| Current Spearman | n=38; independent rho 0.6181438853564616, p=0.00003542225161607963; matches saved 10 decimal places (absolute tolerance 5e-11). |
| Historical correlation versions | Values read from their Git artifacts; no claim of independent historical regeneration. |
| Research integrity scope | No interpretation of duplicate-free IDs or matching counts as evidence of actual coder independence, held-out selection, or a common detector build. |

## Reproduce the principal checks without collection

Run the following from the Git root. It only reads repository files and Git objects and prints results. SciPy is optional for the p-value cross-check; the standard-library rank calculation still checks rho without it. This is a CSV consistency check, not a replay of detection on archived raw source evidence. For the recorded baseline, run against revision `9f8d541`; later results may legitimately differ.

```python
from pathlib import Path
import csv, io, subprocess, collections, math, hashlib
base = Path("pr-analyzer-maven-skip-bad-repos")

def read(name, rev=None, header="Sample ID"):
    text = (subprocess.check_output(
        ["git", "show", f"{rev}:{base.as_posix()}/{name}"], text=True
    ) if rev else (base / name).read_text(encoding="utf-8-sig"))
    rows = list(csv.reader(io.StringIO(text)))
    i = next(i for i, row in enumerate(rows) if row and row[0] == header)
    data = [dict(zip(rows[i], row)) for row in rows[i+1:]
            if row and len(row) == len(rows[i]) and row[0]]
    if header == "Sample ID":
        data = [r for r in data if "#" in r["Sample ID"]]
    if header == "Repo" and "PR #" in rows[i]:
        data = [r for r in data if r["PR #"].isdigit()]
    return data

def key(r):
    return r.get("Sample ID", r.get("Repo", "") + "#" + r.get("PR #", "")).lower()

def index(rows):
    result = {key(r): r for r in rows}
    assert len(result) == len(rows), "Duplicate canonical PR ID"
    return result

def matrix(sample, labels, label_col):
    si, li = index(sample), index(labels)
    assert si.keys() == li.keys(), "Sample/label IDs differ"
    names = {("Yes", "Yes"): "TP", ("No", "No"): "TN",
             ("Yes", "No"): "FP", ("No", "Yes"): "FN"}
    return dict(collections.Counter(names[(si[k]["Script AI Disclosure Present"],
                                          li[k][label_col])] for k in si))

k = read("kappa_sample.csv")
a = index(read("anna_labels_kappa_sample.csv"))
m = index(read("minseo_labels_kappa_sample.csv"))
c = index(read("kappa_sample_consensus_labels.csv"))
assert index(k).keys() == a.keys() == m.keys() == c.keys()
for col in ["Disclosure Present", "Disclosure Classification"]:
    aa = collections.Counter(r[col] for r in a.values())
    mm = collections.Counter(r[col] for r in m.values())
    n = len(a)
    po = sum(a[x][col] == m[x][col] for x in a) / n
    pe = sum(aa[x] * mm[x] for x in aa) / n**2
    print("Paired", col, "n", n, "kappa", (po-pe)/(1-pe))
print("150 detector", matrix(k, list(c.values()), "Consensus Disclosure Present"))
s = read("all_repo_validation_sample_seeded.csv")
mi = index(read("minseo_seeded_labels.csv"))
an = index(read("anna_seeded_labels.csv"))
h = index(read("all_manual_seeded_labels.csv"))
assert not mi.keys() & an.keys()
assert (mi.keys() | an.keys()) == h.keys() == index(s).keys()
for ident, row in {**mi, **an}.items():
    assert row == h[ident], ("Merged label differs", ident)
assert {key(r) for r in s[:len(mi)]} == mi.keys()
assert {key(r) for r in s[len(mi):]} == an.keys()
print("Seeded ownership", len(mi), len(an), "overlap", len(mi.keys() & an.keys()))
print("708 detector", matrix(s, list(h.values()), "Disclosure Present"))
v = index(read("all_seeded_detector_validation.csv"))
assert v.keys() == h.keys()
for r in s:
    ident = key(r)
    expected = {("Yes", "Yes"): "True Positive", ("No", "No"): "True Negative",
                ("Yes", "No"): "False Positive", ("No", "Yes"): "False Negative"}[
                    (r["Script AI Disclosure Present"], h[ident]["Disclosure Present"])]
    assert (v[ident]["Script Disclosure Present"], v[ident]["Consensus Disclosure Present"],
            v[ident]["Consensus Classification"], v[ident]["Detector Outcome"]) == (
                r["Script AI Disclosure Present"], h[ident]["Disclosure Present"],
                h[ident]["Disclosure Classification"], expected)
old = index(read("kappa_sample.csv", "0cc5579"))
combined = index(read("combined_detector_validation.csv", "a26a3df"))
print("Overlaps: earlier150/revised150, earlier150/708, July350/708",
      len(old.keys() & a.keys()), len(old.keys() & h.keys()),
      len(combined.keys() & h.keys()))
main = read("pr_dataset_output.csv", header="Repo")
main_i = index(main)
counts = collections.defaultdict(collections.Counter)
for r in main:
    counts[r["Repo"].lower()][r["AI Disclosure Present"]] += 1
print("Main", len(main), "repositories", len(counts),
      dict(collections.Counter(r["AI Disclosure Present"] for r in main)))
for r in read("repo_compliance_summary.csv", header="Repo"):
    co = counts[r["Repo"].lower()]
    assert [int(r[x]) for x in ["Eligible PRs Reviewed", "AI Disclosure Present Count",
                                "No Disclosure Count"]] == [sum(co.values()), co["Yes"], co["No"]]
vis = read("repo_visibility_summary.csv", header="Repository")
for r in vis:
    co = counts[r["Repository"].lower()]
    assert [int(r[x]) for x in ["Successfully Analysed Eligible PRs", "PRs With Disclosure",
                                "PRs Without Disclosure"]] == [sum(co.values()), co["Yes"], co["No"]]
shared = index(s).keys() & main_i.keys()
print("Seeded/main shared", len(shared), "presence mismatches",
      [x for x in sorted(shared) if index(s)[x]["Script AI Disclosure Present"] !=
       main_i[x]["AI Disclosure Present"]])

def ranks(values):
    order = sorted(range(len(values)), key=values.__getitem__)
    out = [0.0] * len(values)
    i = 0
    while i < len(values):
        j = i+1
        while j < len(values) and values[order[j]] == values[order[i]]:
            j += 1
        for q in order[i:j]:
            out[q] = (i+1+j)/2
        i = j
    return out

def pearson(x, y):
    mx, my = sum(x)/len(x), sum(y)/len(y)
    return sum((a-mx)*(b-my) for a,b in zip(x,y)) / math.sqrt(
        sum((a-mx)**2 for a in x) * sum((b-my)**2 for b in y))

included = [r for r in vis if r["Correlation Included"] == "Yes"]
x = [float(r["Visibility Score Numeric"]) for r in included]
y = [float(r["Disclosure Rate"]) for r in included]
rho = pearson(ranks(x), ranks(y))
saved = read("spearman_correlation.csv", header="Metric Pair")[0]
assert len(included) == int(saved["Sample Size"])
assert abs(rho - float(saved["Spearman Rho"])) <= 5e-11
print("Rank rho", rho, "n", len(included))
try:
    from scipy.stats import spearmanr
except ImportError:
    print("SciPy unavailable; p-value cross-check skipped")
else:
    result = spearmanr(x, y)
    assert abs(result.pvalue - float(saved["P-Value"])) <= 5e-11
    print("Independent p-value", result.pvalue)
original = subprocess.check_output(["git", "show", f"24515b4:{base}/kappa_sample.csv"])
assert original == (base / "kappa_sample.csv").read_bytes()
for path in sorted(base.glob("*.csv")):
    print("SHA256", path.name, hashlib.sha256(path.read_bytes()).hexdigest())
print("Principal checks passed")
```

The recorded execution of this block passed. It reproduces the principal current checks and selected historical membership checks; additional historical source/label comparisons in the table used the Git objects identified below. It prints current hashes for comparison with the manifest; it does not overwrite any files.

## Current artifact and source SHA-256 manifest

All paths below are relative to `pr-analyzer-maven-skip-bad-repos/`. Hashes cover exact bytes, including metadata and line endings; Git blob IDs below use Git's object hash convention and are not SHA-256 file hashes.

| Relative path | SHA-256 | Last path change at reviewed HEAD |
| --- | --- | --- |
| `kappa_sample.csv` | `14749f96582b0ec6c5aaec9b9f808a0e527a5e416691b0476034b0c569b331ca` | 24515b4 2026-07-23T00:05:19+12:00 Updated cohens kappa calculation |
| `anna_labels_kappa_sample.csv` | `24de11ca8c273b0f748ae53f07afbb97b7355464cad70ebedace64712cd463c9` | 0f46bee 2026-09-16T21:46:07+12:00 update files |
| `minseo_labels_kappa_sample.csv` | `93cfddafa3b784e47643d095440c7a308e1b291813d3077615bd97cb32686e9b` | 0f46bee 2026-09-16T21:46:07+12:00 update files |
| `kappa_sample_consensus_labels.csv` | `bd3e248e0bbf57bca62da53ae55e1ff3c902bb711aeeb40f18afa64b80dd24a6` | 0f46bee 2026-09-16T21:46:07+12:00 update files |
| `all_repo_validation_sample_seeded.csv` | `a438f28f27b736683db5b8a37b3f0a48fcfd01366a4cf1bd8bfaa43a9e22e1f5` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `minseo_seeded_labels.csv` | `7f6101f7ffc0e08dd8275ae7d5415f5bee5680d8f07b4dee7cd5a2f8ceabfa0e` | 44c2808 2026-10-16T16:50:28+13:00 fix: remove mesa |
| `anna_seeded_labels.csv` | `b6c9f8bc5bc9f5217916cc11d28d4f26dcc678d784adb1b90b8f7eb96c86cfca` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `all_manual_seeded_labels.csv` | `00c2e3c78e82706c728201300b702b558292246e1cfc411a780625a6da1c1f3f` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `all_seeded_detector_validation.csv` | `7d2168b979336b2b01b118f047d14cf4148abfd391493149c340f45db2c1120c` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `pr_dataset_output.csv` | `16ded4f6eac9ff75301648e50be43b826866529bb78729d4f45330a96e7e5f02` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `repo_compliance_summary.csv` | `a8ea43da8492385fc5f6688824b9c247c188312e0b5945c3a971f0bfa151e35c` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `policy_tracker.csv` | `87bba2bf4396d5c97072db479c226fbc0066ec8c76c2ba2d5dfd7ab9c166d420` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `repo_visibility_summary.csv` | `dd98ea79d95007db60f3fcc9abeaae381f7883c1dd00f880740eb95fcd364ae1` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `spearman_correlation.csv` | `99f3906af92170a3b68ec00231c21c7f3f613b6a9c013f1681d1ad3d43366f7e` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `repos.txt` | `4a59364d514a2efc3eec1cb1fa3f0660b9c41abdc0a11dc9aed31345b116c989` | 9f8d541 2026-10-16T21:59:11+13:00 stac dev |
| `src/main/java/com/example/aichecker/AiDisclosureDetector.java` | `9e8b761a72cdbc8a6386a9bcb18cd0c03f358534b7958d6fa721ef1ea626035b` | 8279af2 2026-10-15T22:06:14+13:00 fix: homebrew rules |
| `src/main/java/com/example/aichecker/PrAnalyzer.java` | `72b099bfcfd3cde716ab945fb53074709c01e6de3d6fcd31ac4bc1d92fc78d31` | 6feb773 2026-09-23T20:29:09+12:00 final repo analysis |
| `src/main/java/com/example/aichecker/KappaWorkflow.java` | `69f793ef5aab4303030b95d0cb489c30f3383b00be13cf7bd064ee30e5129923` | 39d3345 2026-09-16T11:29:03+12:00 tighten rules |
| `src/main/java/com/example/aichecker/ConsensusWorkflow.java` | `1447ced3d80b32a7f9d7bdf0620c169c626a8a7202bd9255448c0e7c249eea30` | 39d3345 2026-09-16T11:29:03+12:00 tighten rules |
| `src/main/java/com/example/aichecker/ExtendedValidationWorkflow.java` | `386f9789f2a36136e8ede73f445bd86e98b76acafb06830ce8a8d0c42c90cdb8` | 39d3345 2026-09-16T11:29:03+12:00 tighten rules |
| `src/main/java/com/example/aichecker/AllRepoValidationWorkflow.java` | `6643fcac7e8ac8df75cb66387d3da76be3b64b71b8e153129a3cd730914f6afb` | 82b6268 2026-09-10T17:02:49+12:00 Add reproducible seeded PR sampling workflow |
| `src/main/java/com/example/aichecker/KappaSampleReanalysisWorkflow.java` | `9bdea3d16b8cb193eb2670721aaef6764085c75f05bbfd7431702815fa8eb2f0` | 4fc6ffb 2026-07-18T21:52:51+12:00 update kappa_labels |
| `src/main/java/com/example/aichecker/DisclosureVisibilityCorrelationWorkflow.java` | `3f785cc0c68e69242e59fad311668b1521611f6bbe9770cfecfc38eee8c9e23e` | f1908cc 2026-09-24T14:43:59+12:00 spearman correlation |
| `src/main/java/com/example/aichecker/Main.java` | `0de2e852ca11e938ab1e7665d9ae7f790998a1a1792d1fbefb686698b430966b` | f1908cc 2026-09-24T14:43:59+12:00 spearman correlation |
| `src/main/java/com/example/aichecker/GitHubClient.java` | `00e750b4df4f6ce8dfbdbb6833b3aef322603715350e3276b1f4fa0289a4858e` | 82b6268 2026-09-10T17:02:49+12:00 Add reproducible seeded PR sampling workflow |
| `src/main/java/com/example/aichecker/BotDetector.java` | `5214c478449d6ecee7ad34f6729cbb6b74a52995b38213799874c2cc5495bce1` | 37820c2 2026-06-05T10:48:54+12:00 initial code |
| `methodology/coding_framework.md` | `92d1fa04eaa9e6ddecdc61ea33335a83cad79b6d21c5c7323b563d4a2dab98f7` | e3dc0ee 2026-08-11T19:57:56+12:00 imrpoved rules |
| `methodology/validation_protocol.md` | `894c99a9cb980db6fe324c343eebe4b5e821338a49f047ed379e20ef3bbcad8b` | e3dc0ee 2026-08-11T19:57:56+12:00 imrpoved rules |
| `methodology/cohens_kappa_workflow.md` | `34b71c96bc08073648e8efd13c0f297d333028b3f7f97554bb93597dce53bba2` | e3dc0ee 2026-08-11T19:57:56+12:00 imrpoved rules |
| `README.md` | `4830573cdf90cb1d0c90d7571eca4d20e79c9ab9c83d203e81915a03674d9bdf` | c0b7407 2026-09-14T10:24:58+12:00 all_seeded_detector_validation |

## Historical artifact identifiers

Each historical artifact can be inspected without checking out another revision:

```sh
git show 24515b4:pr-analyzer-maven-skip-bad-repos/detector_validation.csv
git show a26a3df:pr-analyzer-maven-skip-bad-repos/combined_detector_metrics.csv
git show 9502c13:pr-analyzer-maven-skip-bad-repos/all_repo_validation_sample_seeded.csv
```

Paths are relative to `pr-analyzer-maven-skip-bad-repos/`. The commit and path select immutable Git contents; the SHA-256 adds a portable byte-level fingerprint. These identifiers do not certify actual generating builds.

| Commit | Relative path | SHA-256 | Git blob ID |
| --- | --- | --- | --- |
| `0cc5579` | `kappa_sample.csv` | `27cf789b9c15df175145d5f5f4cd491e0b94458b0acf871eddfb50b461b44c12` | `71cc4a3893d90c739d878897a09cddbb6728a330` |
| `af408d4` | `anna_labels.csv` | `3cd49b369e24ac49008731a14d5b503a07c7f2032615621f40fd55630e43f5cf` | `98c037ddd185c57ee4f25360a2c94536af128bf3` |
| `ac913c2` | `minseo_labels.csv` | `c232576923b361489b86f68ced097a08c7b6d61307500f7a02b5df1045b1bfc1` | `bdd2e6d93f004f4537093a253966b6d8d15904cc` |
| `f7f12b4` | `consensus_labels.csv` | `851f9a9c11db37c4328bfdbc3eaf79971fbe00c820199d1ab96e59ebef632dfa` | `03824be5dbfe761b093a9c8efc4244a67f1abd02` |
| `f7f12b4` | `detector_validation.csv` | `546d4ef0141dbafb70644fbe79bfd090e6c0433983a469db5b935e08c0dd6616` | `12da599fef373cc2b432e1481617636253c5a50f` |
| `20d8e58` | `kappa_sample.csv` | `a873a0c0a8eb8db764058880bcc44e2c68b49c5e18cecbd6e9cce79e7cb1f922` | `d799b37be4a9b5946f87a2fcceac64a22d0d066b` |
| `20d8e58` | `anna_labels.csv` | `24de11ca8c273b0f748ae53f07afbb97b7355464cad70ebedace64712cd463c9` | `f51c28d045665fb618b1378d85871e8748563ce3` |
| `57fa0c1` | `kappa_sample_reanalyzed.csv` | `14749f96582b0ec6c5aaec9b9f808a0e527a5e416691b0476034b0c569b331ca` | `9ceb4084155536c2b0554fc3e05b84079a0049c5` |
| `9e8f0da` | `minseo_labels.csv` | `93cfddafa3b784e47643d095440c7a308e1b291813d3077615bd97cb32686e9b` | `2b342abdfd7ee745750730972f7c177c92f16c06` |
| `9e8f0da` | `minseo_labels_backup.csv` | `c232576923b361489b86f68ced097a08c7b6d61307500f7a02b5df1045b1bfc1` | `bdd2e6d93f004f4537093a253966b6d8d15904cc` |
| `24515b4` | `kappa_sample.csv` | `14749f96582b0ec6c5aaec9b9f808a0e527a5e416691b0476034b0c569b331ca` | `9ceb4084155536c2b0554fc3e05b84079a0049c5` |
| `24515b4` | `consensus_labels.csv` | `bd3e248e0bbf57bca62da53ae55e1ff3c902bb711aeeb40f18afa64b80dd24a6` | `4488d8bdff780201ae5054a272235ea6e38536b6` |
| `24515b4` | `detector_validation.csv` | `2e75d5c615a1f032760a09e1f904bf21f440342b4bfeca326f03e66e08e2306e` | `b6038bc9b18e7c09ce5dc1d621a345d81fd109c7` |
| `a26a3df` | `extended_validation_sample.csv` | `7f17b2cc198101b8a44b729a4095e954a93e4fba6d416d91967c5fe43120312f` | `7bf3182d7dcf812afce61cd875c09ab30b354a9f` |
| `a26a3df` | `anna_extended_labels.csv` | `e3d2795057442c2f6a6ae8213cfe200f8f8e069668303e02afc788fa373e99be` | `cb49f8857e6b4f0e9d87b2bf660407a644b1086b` |
| `a26a3df` | `extended_consensus_labels.csv` | `f1ec043a4ed7362e2151df3151d7bfa051d625d31523723fbd7f8083ebe37ab6` | `4f6f359a80f1a10cb3eb9d07e239ead13d35d600` |
| `a26a3df` | `extended_detector_validation.csv` | `13d3ccdb90ffc49b52c257e02346f9502d330d807802f2ad29b297a505d7a232` | `84c937cb38cda2bd5d0920f78e0d9e2237dfd5b1` |
| `a26a3df` | `combined_detector_validation.csv` | `4986ee36e9d8e849d469d713a0138202276f6019c850c8619ca46e0b85255efc` | `5719582fcdaf9afe0be67bcd2cf2823898dee51a` |
| `a26a3df` | `pr_dataset_output_policy_cleaned.csv` | `919c23d182063189ea5c791e59402448dc272974a9c78728dcb5a7c7b72967ab` | `ae9b1b8e6067a7aeaa4c4e830079691d69800497` |
| `9dcc9c6` | `combined_human_labels.csv` | `5c47d587834458acd9fc6a82b16b7eb88fadb6a76c6013f3522468bedf5f407a` | `1611f0742e57b68cd236df04b9bc7ee6e9486a98` |
| `82b6268` | `all_repo_validation_sample_seeded.csv` | `53d195cf2644aac35e95de5165f67882edd970a8795d30b497f662f88455852a` | `1b6e20c0e157358b3c6b6da2feb22af0def51faf` |
| `3bba769` | `minseo_seeded_labels.csv` | `2ff582d9499d721f1b6f367483b9244adbe2338d55b620c2aa42cc78dd476046` | `119af62a967c79faab5cecf40366a26ed5369e91` |
| `3479280` | `anna_seeded_labels.csv` | `41dad3627a2ee5f3038d1dc318b1a010e641ac2e82e65373a6acbb72e2075982` | `c1d3d593908c539e01b0168e4f2fd6384a05b73a` |
| `c0b7407` | `all_repo_validation_sample_seeded.csv` | `53d195cf2644aac35e95de5165f67882edd970a8795d30b497f662f88455852a` | `1b6e20c0e157358b3c6b6da2feb22af0def51faf` |
| `c0b7407` | `all_seeded_detector_validation.csv` | `addaa64555f3c8992aba4be523bc76f69db5b7fa433a06d9051ffab5d02992a3` | `c9e16e3c0b303dcbb2c5988b54c4e7ccc4dfcf47` |
| `767ef70` | `all_seeded_detector_validation_rechecked.csv` | `50f1877fff8ea4f4905263fcdae67d62492ea22cb2b273b408a17a934c98f537` | `4d74d70f83a5eef1c609c917a07c7835973cdd12` |
| `779a704` | `all_repo_validation_sample_seeded.csv` | `2968fed6bc36dd4985a9274ce3112366bbf51841520c1b96630736813313ed1f` | `a9ae13d6de3c2b7b18945e901be6f2efedae461d` |
| `779a704` | `all_seeded_detector_validation.csv` | `b0ad4e754308580db16fdff455c04e8fa26c00c7c8a150cb84aa1f191bc71cef` | `ce10ec80a502ff66448aff29e0c15b15da78a16c` |
| `9502c13` | `all_repo_validation_sample_seeded.csv` | `8fbc820c3b4fc3ed2e8744ca502d6a86cdbae02a914be9583250efa1c752b77a` | `93ff733747720e977a102c28107ad5ac4cc1c83d` |
| `9502c13` | `all_seeded_detector_validation.csv` | `6ab8717f1a3b4c74848d9fa333f00d76920b597b60720b771f107fd0e53c12c0` | `ae34653e8aa14af007f2499f1ec13b65853210c9` |
| `6feb773` | `pr_dataset_output.csv` | `127f44bf62c1755a7f2cb13070f7195d9eaebafa1ec1b3090c89991f779d44de` | `d954fff9f1ddec8a32977467aa3adf8b4843b4cc` |
| `b824757` | `all_repo_validation_sample_seeded.csv` | `cffa01fd7c186846bd12b96c88ba866ea83507c54b4c17b32f6261fbfca309bd` | `75a2824580ec3fe3906fc98898492a67458fa4f8` |
| `b824757` | `all_seeded_detector_validation.csv` | `174bc21174ed92bcfcbf95118f8357359038eef1a1c82325e8778d992af1bba3` | `4d8c7a6f3aff603f77b599150b55ab531e30dc8c` |
| `b824757` | `pr_dataset_output.csv` | `3d5baf0a08e70d3707b433e93f5370ff20f43aa98d7995d26f1e5f9b6dac0aa8` | `191f9c81c75b3fd6e9860f5e4cc1b5bfd1155eed` |
| `44c2808` | `all_repo_validation_sample_seeded.csv` | `34445fdce94051361dc4fcd49fd011c3994e5609125e0856ee658ceeacd40d9e` | `0e757a03aeb63e1ae1757b54d979389027878263` |
| `44c2808` | `pr_dataset_output.csv` | `112aece4ae5c265c0534e193a0456f06b83e2196ad64273d59f36a95c061f84d` | `978ed45a868dc51ad4007870d314afc7101d2734` |
| `0acb386` | `visibility_correlation.csv` | `f9e843c338b39e3465c43e57fcfcfa2d2074ca40236735ae3f6411cc03fdec45` | `0b9da1ba69b0156cefaede591845cf8e71f6f6e2` |
| `f63847b` | `visibility_correlation_updated.csv` | `bc0537289d184b23025941ff1ada7e2eaa5c7b5e76783cd758e6f82297413f39` | `947c12920a94f706a17294b17f89eac300270596` |
| `9a8546c` | `visibility_correlation_updated.csv` | `bc0537289d184b23025941ff1ada7e2eaa5c7b5e76783cd758e6f82297413f39` | `947c12920a94f706a17294b17f89eac300270596` |
| `f1908cc` | `spearman_correlation.csv` | `c0f3336aa86b36fae822844111ae68e511b9125483e3e1b0753d3d4223c54f06` | `bd48cf950f5e791427e56c46fa04f437607aca0c` |
| `65cf3a9` | `spearman_correlation.csv` | `2ad4dbde97e4dd61920bf395e926e5b86db4eedb8572468d818b9c4a9db8a009` | `84bec4fa691568023e51c3792e43037cc31fe9da` |
| `b824757` | `spearman_correlation.csv` | `1da056959509bb03ffdf1d32c8db3902d6fdc10a5b1593f4c3ec7a34510bd3c6` | `681b5b57872fd6a0bd7771353c0135287c3d651e` |
| `9f8d541` | `spearman_correlation.csv` | `99f3906af92170a3b68ec00231c21c7f3f613b6a9c013f1681d1ad3d43366f7e` | `1dda485fb1dca239a23b351e8a14f79f916b9130` |
| `a26a3df` | `combined_detector_metrics.csv` | `3844c441bd2f05517d6fa97fb2813c03faaebcdbf8e6f81a6177a117ccdefa98` | `de215412490732d8b75151ffeeeab6463277eabb` |
| `a26a3df` | `detector_validation.csv` | `2e75d5c615a1f032760a09e1f904bf21f440342b4bfeca326f03e66e08e2306e` | `b6038bc9b18e7c09ce5dc1d621a345d81fd109c7` |
| `24515b4` | `anna_labels.csv` | `24de11ca8c273b0f748ae53f07afbb97b7355464cad70ebedace64712cd463c9` | `f51c28d045665fb618b1378d85871e8748563ce3` |
| `24515b4` | `minseo_labels.csv` | `93cfddafa3b784e47643d095440c7a308e1b291813d3077615bd97cb32686e9b` | `2b342abdfd7ee745750730972f7c177c92f16c06` |
| `0acb386` | `pr_dataset_output.csv` | `900e7dc451c4a303992d158beb3a41c75f05825182188e1b77211c6c6f074c78` | `5fdd20f88d393693321dfa0afe7e23abf963852f` |
| `0acb386` | `pr_dataset(Policy Tracker).csv` | `f0a8f4804359bb368f20d1b7f92f26827206ff855a3dcc0a17013593b3e16254` | `86399fd25ae706fc8f0cf58e5ef3a17c74f20ac2` |
| `f63847b` | `pr_dataset_output_policy_cleaned.csv` | `919c23d182063189ea5c791e59402448dc272974a9c78728dcb5a7c7b72967ab` | `ae9b1b8e6067a7aeaa4c4e830079691d69800497` |
| `f63847b` | `pr_dataset(Policy Tracker).csv` | `f0d4825b4f6046ebbfeeb5f1d961f486d12b19fe7ae3348a6fd4d48191fdc5b1` | `2e436b926794dabe2311507ee052ed6a102064fd` |
| `9a8546c` | `pr_dataset_output.csv` | `ce204c2ec087612ccaa6af9632dbb6a31b01d806800e180c33535455cc63f54b` | `f331c27e386496d8cb7fc46fefa1dc40bd0c9c62` |
| `f1908cc` | `repo_visibility_summary.csv` | `9e0ba03a094f7ac91f8c2351ad4993fa69080c0f2b687bb9e7e3da18453e1595` | `03a9b74f6f85dc33a4ed811cf456ee122b05fccb` |
| `f1908cc` | `policy_tracker.csv` | `7c1ec8894da1d2c880e47a5d79b73e261324809803ce460466f6e5b72245b1cf` | `d5f5079f284a496501c1a89c556ec57a78ddf6b0` |

## Associated source and coding-framework versions

These are snapshots present in Git at the named revisions. They are **not** asserted to be the exact builds or instruction copies actually used. Detector and PrAnalyzer together are only part of the processing pipeline; a full commit identifies all tracked source at that revision. No actual runtime build, raw-evidence snapshot or uncommitted worktree state can be recovered merely from commit proximity.

| Revision | Full Git commit | Git author date |
| --- | --- | --- |
| `0cc5579` | `0cc557978772aca6738998dc64bc59d651d4733e` | 2026-07-08T18:53:10+12:00 |
| `f7f12b4` | `f7f12b49e221c9928f5dc1fb7b659ef884918be2` | 2026-07-18T14:27:07+12:00 |
| `e51ae75` | `e51ae75c34c0f8c077b2673a226e1ea6be92f54f` | 2026-07-20T21:16:10+12:00 |
| `20d8e58` | `20d8e58b488f76edfa6c3c741fb39421723c7771` | 2026-07-22T17:29:02+12:00 |
| `57fa0c1` | `57fa0c195cb9a6bdd6be50642e8fba840cd362e6` | 2026-07-22T20:56:36+12:00 |
| `24515b4` | `24515b42965535e063802421e7672a0bab614c3d` | 2026-07-23T00:05:19+12:00 |
| `a26a3df` | `a26a3dff2dd49b62227a2842c215650becfff5d3` | 2026-07-24T10:22:10+12:00 |
| `82b6268` | `82b6268fdd8199f84ad86d4c39739c3189b2469d` | 2026-09-10T17:02:49+12:00 |
| `c0b7407` | `c0b7407f28779b99c6446f34929e40456d893047` | 2026-09-14T10:24:58+12:00 |
| `9502c13` | `9502c13ffa1197842b2a498c00c07503cb64edc8` | 2026-09-16T12:20:51+12:00 |
| `6feb773` | `6feb7731ce88cdb290e8a802bd796e3366ace6f4` | 2026-09-23T20:29:09+12:00 |
| `f1908cc` | `f1908cc2ebe1969dc95ccca2446eb5d616f64b3b` | 2026-09-24T14:43:59+12:00 |
| `8279af2` | `8279af2e108ba970c2ecf719f9d596e9459046dd` | 2026-10-15T22:06:14+13:00 |
| `9f8d541` | `9f8d541718d5c72a16ce88987aef385e43c4281f` | 2026-10-16T21:59:11+13:00 |

All values in the next table are **Git blob IDs**. Column filenames refer to `src/main/java/com/example/aichecker/` for Java and `methodology/` for the framework.

| Revision | AiDisclosureDetector.java | PrAnalyzer.java | coding_framework.md |
| --- | --- | --- | --- |
| `0cc5579` | `88e17f95136ff4154b52526f80bca2c9b5e39276` | `b9e9b608ead8bebf7975caaa07e115aa66a0ad30` | `458b847351680480cdd77cd4d5bd1e9e0c436dda` |
| `f7f12b4` | `88e17f95136ff4154b52526f80bca2c9b5e39276` | `b9e9b608ead8bebf7975caaa07e115aa66a0ad30` | `458b847351680480cdd77cd4d5bd1e9e0c436dda` |
| `e51ae75` | `f54788231eb86cabd00e49ad80e2baff6b4ff6bb` | `084bb4ca12df1578c6fc68d5f3edf54fd22510fa` | `458b847351680480cdd77cd4d5bd1e9e0c436dda` |
| `20d8e58` | `f54788231eb86cabd00e49ad80e2baff6b4ff6bb` | `084bb4ca12df1578c6fc68d5f3edf54fd22510fa` | `458b847351680480cdd77cd4d5bd1e9e0c436dda` |
| `57fa0c1` | `580fa46af52a45a413fd30d81d787c31e282592f` | `084bb4ca12df1578c6fc68d5f3edf54fd22510fa` | `458b847351680480cdd77cd4d5bd1e9e0c436dda` |
| `24515b4` | `580fa46af52a45a413fd30d81d787c31e282592f` | `986f2a0a565348bc7c62fb1e3cb67a14b5114be6` | `bf86722d90c806ca140015fc47cc1d0e3597e187` |
| `a26a3df` | `580fa46af52a45a413fd30d81d787c31e282592f` | `2096832579b83fef66abaee75a383a32c7ce1b5b` | `99d84ec27b3024fd4950fdee4a0970d8dddd605a` |
| `82b6268` | `70331cca7e0ccd9745a5de1b220a14fe213222de` | `40a71589d09ad5e8b0b9b56411966de22e00c221` | `09c894c25982c1effe2b8726054c241802542daf` |
| `c0b7407` | `70331cca7e0ccd9745a5de1b220a14fe213222de` | `40a71589d09ad5e8b0b9b56411966de22e00c221` | `09c894c25982c1effe2b8726054c241802542daf` |
| `9502c13` | `453f31ee7d8229d7a0505c049d4ade9b385e3228` | `40a71589d09ad5e8b0b9b56411966de22e00c221` | `09c894c25982c1effe2b8726054c241802542daf` |
| `6feb773` | `453f31ee7d8229d7a0505c049d4ade9b385e3228` | `d18333da3329b330b2c059c4a8370272e7c7e49d` | `09c894c25982c1effe2b8726054c241802542daf` |
| `f1908cc` | `453f31ee7d8229d7a0505c049d4ade9b385e3228` | `d18333da3329b330b2c059c4a8370272e7c7e49d` | `09c894c25982c1effe2b8726054c241802542daf` |
| `8279af2` | `fe0eb84f36934358ca4cae9e9ea241d4eecea85b` | `d18333da3329b330b2c059c4a8370272e7c7e49d` | `09c894c25982c1effe2b8726054c241802542daf` |
| `9f8d541` | `fe0eb84f36934358ca4cae9e9ea241d4eecea85b` | `d18333da3329b330b2c059c4a8370272e7c7e49d` | `09c894c25982c1effe2b8726054c241802542daf` |

## Supplied document source record

| Supplied document | Relevant content | SHA-256 |
| --- | --- | --- |
| `Commit_history_and_explanation.pdf` | Retrospective commit account; pages 1–6, checked against Git. | `5569ea8fb67620a913c771827fb52b7877878cf96ae7e1ad77e58ce205625a39` |
| `Project_78_Rubric_and_Compendium_Checklist.pdf` | C09/C10 requested changes and acceptance criteria, page 5; reviewed revision, page 1. | `17dd88e325c3692f361acf615617b86f1f74cb1271415bacaef6ea31a7a9e1db` |

The commit account contains abbreviated references that do not match the local commits (`Ad63fd0`, `6f3b773`). The matching locally verified dataset commits are `ad6efd0` and `6feb773`; the account is not used as an immutable execution log. Its wording about generated/reanalysed samples is separated from direct membership comparisons in C09.

## Recheck after Anna's C01–C08 changes are merged

Repeat principal checks against the merged revision, compare all relevant file hashes, and record a new dated result. Preserve these baseline values. Update paths only with an explicit old-to-new mapping; do not interpret new source code or newly recollected PR text as evidence of the original detector run.

## Final scope verification

`git diff --exit-code HEAD --` returned success with no tracked changes. `git status --short --untracked-files=all` listed exactly the four new Markdown files under `compendium_docs/`. All 30 baseline artifact/source SHA-256 hashes above remained unchanged. The embedded principal-check block executed successfully. Relative documentation links, fenced code blocks, required recheck sections and trailing whitespace were checked locally. These checks verify the requested documentation scope; they do not close the unresolved C09/C10 acceptance requirements.
