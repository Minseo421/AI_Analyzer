package com.example.aichecker;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GitHubClient {
    private static final String DEFAULT_ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION = "2022-11-28";
    private static boolean unauthenticatedWarningPrinted = false;

    private final HttpClient client;
    private final String token;

    public GitHubClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), System.getenv("GITHUB_TOKEN"));
    }

    GitHubClient(HttpClient client, String token) {
        this.client = client;
        this.token = normalizeToken(token);
    }

    public PullRequestData getPullRequest(String owner, String repo, int number) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + enc(owner) + "/" + enc(repo) + "/pulls/" + number;
        String json = sendGet(url);
        return parsePullRequestObject(owner + "/" + repo, json);
    }

    public List<PullRequestData> getClosedPullRequestsPage(String owner, String repo, int page) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + enc(owner) + "/" + enc(repo)
                + "/pulls?state=closed&sort=updated&direction=desc&per_page=100&page=" + page;
        String json = sendGet(url);
        List<String> objects = JsonTools.splitTopLevelObjects(json);
        List<PullRequestData> result = new ArrayList<>();
        for (String object : objects) {
            result.add(parsePullRequestObject(owner + "/" + repo, object));
        }
        return result;
    }

    public String fetchHtml(String url) throws IOException, InterruptedException {
        return sendGet(url, "text/html");
    }

    private String sendGet(String url) throws IOException, InterruptedException {
        return sendGet(url, DEFAULT_ACCEPT);
    }

    private String sendGet(String url, String acceptHeader) throws IOException, InterruptedException {
        if (!isAuthenticated()) {
            warnUnauthenticatedOnce();
        }
        HttpRequest request = buildGetRequest(URI.create(url), acceptHeader, token);
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(errorMessage(url, response.statusCode(), response.body(), response.headers(), isAuthenticated()));
        }
        return response.body();
    }

    private boolean isAuthenticated() {
        return token != null;
    }

    static synchronized void warnUnauthenticatedOnce() {
        if (!unauthenticatedWarningPrinted) {
            System.err.println("WARNING: No GITHUB_TOKEN environment variable found.");
            System.err.println("GitHub requests will be unauthenticated and may hit GitHub rate limits.");
            unauthenticatedWarningPrinted = true;
        }
    }

    static synchronized void resetUnauthenticatedWarningForTests() {
        unauthenticatedWarningPrinted = false;
    }

    static HttpRequest buildGetRequest(URI uri, String acceptHeader, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", acceptHeader)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "pr-ai-disclosure-analyzer");
        String normalizedToken = normalizeToken(token);
        if (normalizedToken != null) {
            builder.header("Authorization", "Bearer " + normalizedToken);
        }
        return builder.GET().build();
    }

    static String errorMessage(String url, int statusCode, String body, HttpHeadersLike headers, boolean authenticated) {
        String base = "HTTP " + statusCode + " for " + url;
        if (statusCode == 401) {
            return base + ". GitHub authentication failed. Check that GITHUB_TOKEN is valid and has not expired.";
        }
        if (statusCode == 403 && isRateLimitBody(body)) {
            String remaining = headers.firstValue("x-ratelimit-remaining").orElse("unknown");
            return base + ". GitHub API rate limit exceeded. Authentication enabled: " + (authenticated ? "Yes" : "No")
                    + ". Remaining rate limit: " + remaining
                    + ". If this was unexpected, verify that GITHUB_TOKEN is set, valid, and has not expired.";
        }
        return base + " body=" + body;
    }

    private static String errorMessage(String url, int statusCode, String body, java.net.http.HttpHeaders headers, boolean authenticated) {
        return errorMessage(url, statusCode, body, name -> headers.firstValue(name), authenticated);
    }

    private static boolean isRateLimitBody(String body) {
        return body != null && body.toLowerCase(java.util.Locale.ROOT).contains("rate limit");
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PullRequestData parsePullRequestObject(String repository, String json) {
        int number = JsonTools.intValue(json, "number", -1);
        String url = JsonTools.stringValue(json, "html_url", "");
        String title = JsonTools.stringValue(json, "title", "");
        String state = JsonTools.stringValue(json, "state", "");
        String createdAt = JsonTools.nullableStringValue(json, "created_at");
        String mergedAt = JsonTools.nullableStringValue(json, "merged_at");
        String closedAt = JsonTools.nullableStringValue(json, "closed_at");
        String body = JsonTools.nullableStringValue(json, "body");
        String userObject = JsonTools.objectValue(json, "user");
        String author = JsonTools.stringValue(userObject, "login", "");
        String userType = JsonTools.stringValue(userObject, "type", "");
        return new PullRequestData(
                repository,
                number,
                url,
                title,
                createdAt == null ? "" : createdAt,
                closedAt == null ? "" : closedAt,
                mergedAt == null ? "" : mergedAt,
                state,
                closedAt != null,
                author,
                userType,
                mergedAt != null,
                body == null ? "" : body
        );
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    interface HttpHeadersLike {
        Optional<String> firstValue(String name);
    }
}
