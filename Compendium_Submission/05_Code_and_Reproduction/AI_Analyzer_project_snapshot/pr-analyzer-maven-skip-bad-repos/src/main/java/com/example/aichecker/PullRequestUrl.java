package com.example.aichecker;

import java.net.URI;

public record PullRequestUrl(String owner, String repo, int number, String url) {
    public static PullRequestUrl parse(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String[] parts = uri.getPath().split("/");
            if (parts.length < 5 || !parts[3].equals("pull")) {
                throw new IllegalArgumentException("Expected URL like https://github.com/OWNER/REPO/pull/NUMBER");
            }
            return new PullRequestUrl(parts[1], parts[2], Integer.parseInt(parts[4]), rawUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pull request URL: " + rawUrl);
        }
    }
}
