package com.example.aichecker;

public record PullRequestData(
        String repository,
        int number,
        String url,
        String title,
        String createdAt,
        String closedAt,
        String mergedAt,
        String state,
        boolean closed,
        String author,
        String userType,
        boolean merged,
        String body
) {
}
