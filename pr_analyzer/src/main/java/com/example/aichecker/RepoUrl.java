package com.example.aichecker;

import java.net.URI;

public record RepoUrl(String owner, String repo, String url) {
    public String fullName() {
        return owner + "/" + repo;
    }

    public static RepoUrl parse(String rawUrl) {
        String cleaned = rawUrl == null ? "" : rawUrl.trim();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Empty repository URL");
        }
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            String[] nameParts = cleaned.split("/");
            if (nameParts.length == 2) {
                return new RepoUrl(nameParts[0], nameParts[1], "https://github.com/" + cleaned);
            }
        }
        try {
            URI uri = URI.create(cleaned);
            String[] parts = uri.getPath().split("/");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Expected URL like https://github.com/OWNER/REPO");
            }
            return new RepoUrl(parts[1], parts[2], cleaned);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid repository URL: " + rawUrl);
        }
    }
}
