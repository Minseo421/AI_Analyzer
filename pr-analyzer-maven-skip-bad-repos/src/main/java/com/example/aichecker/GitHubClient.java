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

public class GitHubClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String token = System.getenv("GITHUB_TOKEN");

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
        return sendGet(url);
    }

    private String sendGet(String url) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "pr-ai-disclosure-analyzer");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        HttpResponse<String> response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url + " body=" + response.body());
        }
        return response.body();
    }

    private PullRequestData parsePullRequestObject(String repository, String json) {
        int number = JsonTools.intValue(json, "number", -1);
        String url = JsonTools.stringValue(json, "html_url", "");
        String state = JsonTools.stringValue(json, "state", "");
        String mergedAt = JsonTools.nullableStringValue(json, "merged_at");
        String closedAt = JsonTools.nullableStringValue(json, "closed_at");
        String body = JsonTools.nullableStringValue(json, "body");
        String userObject = JsonTools.objectValue(json, "user");
        String author = JsonTools.stringValue(userObject, "login", "");
        String userType = JsonTools.stringValue(userObject, "type", "");
        return new PullRequestData(repository, number, url, state, closedAt != null, author, userType, mergedAt != null, body == null ? "" : body);
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
