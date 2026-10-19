package com.example.aichecker;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import static com.example.aichecker.PolicyComplianceWorkflow.Outcome.*;

public class PolicyComplianceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Map<String, String> reviewed() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Repo", "owner/repo"); row.put("PR #", "1");
        row.put("Status", "Closed"); row.put("Bot", "No");
        row.put("Evidence Completeness", "Complete"); row.put("Source Coverage Rule", "PR_BODY_ONLY_V1");
        row.put("Review Status", "Reviewed"); row.put("Reviewer", "reviewer"); row.put("Reviewed At", "2026-10-19");
        row.put("Review Evidence URL", "https://github.com/owner/repo/pull/1");
        row.put("Policy Source URL", "https://github.com/owner/repo/blob/policy-version/CONTRIBUTING.md");
        row.put("Policy Coder", "coder"); row.put("Policy Coded At", "2026-10-19");
        row.put("Policy Effective From", "2026-01-01"); row.put("Policy Effective Through", "2026-10-19");
        row.put("Created Date", "2026-03-01T00:00:00Z"); row.put("Policy Requirement", "All contributors");
        row.put("Reviewed Disclosure Present", "Yes"); row.put("Reviewed AI Use", "Unknown");
        row.put("Reviewed Compliance", "Compliant"); row.put("AI Disclosure Present", "No");
        return row;
    }

    @Test public void allContributorsPolicyUsesReviewedAssessmentIndependentlyOfDetector() {
        var row = reviewed();
        assertEquals(COMPLIANT, PolicyComplianceWorkflow.assess(row));
        row.put("Reviewed Disclosure Present", "No");
        assertEquals(UNRESOLVED, PolicyComplianceWorkflow.assess(row));
        row.put("Reviewed Compliance", "Noncompliant");
        assertEquals(NONCOMPLIANT, PolicyComplianceWorkflow.assess(row));
    }

    @Test public void conditionalPolicyCannotInferAiUseFromAbsentDisclosure() {
        var row = reviewed(); row.put("Policy Requirement", "If AI used");
        row.put("Reviewed Disclosure Present", "No"); row.put("Reviewed Compliance", "Noncompliant");
        assertEquals(UNRESOLVED, PolicyComplianceWorkflow.assess(row));
        row.put("Reviewed AI Use", "No");
        assertEquals(NOT_APPLICABLE, PolicyComplianceWorkflow.assess(row));
        row.put("Reviewed AI Use", "Yes");
        assertEquals(NONCOMPLIANT, PolicyComplianceWorkflow.assess(row));
        row.put("Reviewed Disclosure Present", "Yes"); row.put("Reviewed Compliance", "Compliant");
        assertEquals(COMPLIANT, PolicyComplianceWorkflow.assess(row));
    }

    @Test public void policyDatesMustCoverPrCreation() {
        var row = reviewed();
        row.put("Policy Effective From", "2026-04-01");
        assertEquals(UNRESOLVED, PolicyComplianceWorkflow.assess(row));
        row = reviewed(); row.put("Policy Effective Through", "2026-02-01");
        assertEquals(UNRESOLVED, PolicyComplianceWorkflow.assess(row));
        row = reviewed(); row.put("Policy Coded At", "not a date");
        assertEquals(UNRESOLVED, PolicyComplianceWorkflow.assess(row));
    }

    @Test public void unresolvedAndMissingReviewEvidenceCannotProduceCompliance() {
        for (String column : List.of("Review Status", "Policy Requirement", "Policy Source URL", "Policy Coded At",
                "Policy Coder", "Reviewer", "Reviewed At", "Review Evidence URL", "Evidence Completeness")) {
            var row = reviewed(); row.put(column, "");
            assertEquals(column, UNRESOLVED, PolicyComplianceWorkflow.assess(row));
        }
    }

    @Test public void summaryReportsDenominatorUncertaintyAndNoPolicySeparately() throws Exception {
        var compliant = reviewed();
        var noncompliant = reviewed(); noncompliant.put("PR #", "2"); noncompliant.put("Reviewed Compliance", "Noncompliant");
        var unknown = reviewed(); unknown.put("PR #", "3"); unknown.put("Policy Requirement", "Unresolved");
        var notRequired = reviewed(); notRequired.put("PR #", "4"); notRequired.put("Policy Requirement", "None");
        var input = temp.newFile().toPath(); var output = temp.newFile().toPath();
        var fields = compliant.keySet().stream().toList();
        StringBuilder csv = new StringBuilder(String.join(",", fields)).append('\n');
        for (var row : List.of(compliant, noncompliant, unknown, notRequired)) {
            csv.append(String.join(",", fields.stream().map(f -> CsvTools.csv(row.get(f))).toList())).append('\n');
        }
        Files.writeString(input, csv);
        PolicyComplianceWorkflow.analyze(input, output);
        var summary = CsvTools.readRows(output).get(0);
        assertEquals("2", summary.get("Reviewed Applicable Denominator"));
        assertEquals("0.5000", summary.get("Reviewed Policy Compliance Rate"));
        assertEquals("1", summary.get("Unresolved Count"));
        assertEquals("1", summary.get("Not Applicable Count"));
        assertTrue(summary.get("Analysis Status").startsWith("Provisional"));
    }
}
