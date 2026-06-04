# PR AI Disclosure Analyzer

This Java Maven project checks whether closed human-created GitHub pull requests include AI usage disclosure text.

It works for any public GitHub repository, not only `coreruleset/coreruleset`.

## Build

```bash
mvn clean package
```

## Analyze one PR

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar https://github.com/coreruleset/coreruleset/pull/4493
```

## Analyze latest closed human PRs for one repository

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --latest https://github.com/coreruleset/coreruleset 100 report.csv
```

The CSV includes a `Repository` column, so the repo name is saved together with every PR row.

## Analyze multiple repositories

Create `repos.txt`:

```text
https://github.com/coreruleset/coreruleset
https://github.com/fedify-dev/fedify
OWASP/owasp-mastg
```

Then run:

```bash
java -jar target/pr-analyzer-maven-1.0.0.jar --repos repos.txt 100 report.csv
```

This checks 100 latest closed human PRs per repository and saves one combined `report.csv`.

## CSV columns

- Repository
- Pull request number
- URL
- Author
- State
- Closed
- Human author
- GitHub user type
- AI Disclosure
- Merged
- AI Disclosure evidence
- HTML scrape success
- HTML scrape error

## GitHub API rate limit

For many repositories, you may hit GitHub's unauthenticated API rate limit.

Set a GitHub token before running:

```bash
export GITHUB_TOKEN=your_token_here
```

Then run the same command again.
