package com.example.aichecker;

/**
 * Lightweight metadata used only for building the validation-sampling population.
 * Detailed PR data is fetched only after the seeded shuffle, as candidates are
 * checked for eligibility.
 */
public record PullRequestSummary(
        String repository,
        int number,
        String url,
        String title,
        String createdAt,
        String closedAt,
        String updatedAt,
        String state,
        String author,
        String userType
) {
}
