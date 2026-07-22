package com.example.aichecker;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class GitHubClient {
    private static final String DEFAULT_ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION = "2022-11-28";
    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MILLIS = 250;
    private static final long MAX_BACKOFF_MILLIS = 8_000;
    private static boolean unauthenticatedWarningPrinted = false;

    private final Supplier<HttpClient> clientFactory;
    private final Sleeper sleeper;
    private final String token;
    private HttpClient client;

    public GitHubClient() {
        this(GitHubClient::newHttpClient, System.getenv("GITHUB_TOKEN"), Thread::sleep);
    }

    GitHubClient(HttpClient client, String token) {
        this(() -> client, token, Thread::sleep);
    }

    GitHubClient(Supplier<HttpClient> clientFactory, String token, Sleeper sleeper) {
        this.clientFactory = clientFactory;
        this.sleeper = sleeper;
        this.token = normalizeToken(token);
        this.client = clientFactory.get();
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
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpRequest request = buildGetRequest(URI.create(url), acceptHeader, token);
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    logFetch(url, attempt, "success", "Success");
                    return response.body();
                }
                String message = errorMessage(url, response.statusCode(), response.body(), response.headers(), isAuthenticated());
                HttpStatusException failure = new HttpStatusException(response.statusCode(), message, response.headers().firstValue("Retry-After"));
                if (!isRetryableStatus(response.statusCode())) {
                    logFetch(url, attempt, "permanent_http_" + response.statusCode(), "Failed");
                    throw failure;
                }
                lastFailure = failure;
                logFetch(url, attempt, "transient_http_" + response.statusCode(), attempt == MAX_ATTEMPTS ? "Failed" : "Retrying");
                if (attempt == MAX_ATTEMPTS) {
                    throw new IOException("GitHub fetch failed after " + MAX_ATTEMPTS + " attempts: " + sanitize(message), failure);
                }
                sleepBeforeRetry(attempt, failure.retryAfter());
            } catch (IOException e) {
                if (e instanceof HttpStatusException) {
                    throw e;
                }
                lastFailure = e;
                boolean retryable = isRetryableException(e);
                String category = retryable ? transientExceptionCategory(e) : "permanent_io";
                logFetch(url, attempt, category, retryable && attempt < MAX_ATTEMPTS ? "Retrying" : "Failed");
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw new IOException("GitHub fetch failed after " + attempt + " attempt" + (attempt == 1 ? "" : "s") + ": " + sanitize(e.getMessage()), e);
                }
                if (isGoAway(e)) {
                    client = clientFactory.get();
                }
                sleepBeforeRetry(attempt, Optional.empty());
            }
        }
        throw lastFailure == null ? new IOException("GitHub fetch failed") : lastFailure;
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

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private static boolean isRetryableException(IOException e) {
        return e instanceof SocketTimeoutException
                || e instanceof java.net.http.HttpTimeoutException
                || e instanceof SocketException
                || isGoAway(e)
                || lowerMessage(e).contains("connection reset")
                || lowerMessage(e).contains("unexpectedly closed")
                || lowerMessage(e).contains("closed connection")
                || lowerMessage(e).contains("connection closed")
                || lowerMessage(e).contains("timed out")
                || lowerMessage(e).contains("timeout");
    }

    private static boolean isGoAway(IOException e) {
        return lowerMessage(e).contains("goaway");
    }

    private static String transientExceptionCategory(IOException e) {
        if (isGoAway(e)) return "http2_goaway";
        if (e instanceof SocketTimeoutException || e instanceof java.net.http.HttpTimeoutException || lowerMessage(e).contains("timeout")) return "socket_timeout";
        if (lowerMessage(e).contains("connection reset")) return "connection_reset";
        if (lowerMessage(e).contains("unexpectedly closed") || lowerMessage(e).contains("closed connection") || lowerMessage(e).contains("connection closed")) return "connection_closed";
        return "transient_io";
    }

    private static String lowerMessage(Throwable e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        Throwable cause = e.getCause();
        while (cause != null) {
            message += " " + (cause.getMessage() == null ? "" : cause.getMessage());
            cause = cause.getCause();
        }
        return message.toLowerCase(Locale.ROOT);
    }

    private void sleepBeforeRetry(int attempt, Optional<String> retryAfter) throws InterruptedException {
        long delay = retryAfter.flatMap(GitHubClient::parseRetryAfterMillis)
                .orElseGet(() -> backoffMillis(attempt));
        sleeper.sleep(delay);
    }

    private static long backoffMillis(int attempt) {
        long exponential = Math.min(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS * (1L << Math.max(0, attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 2 + 1));
        return Math.min(MAX_BACKOFF_MILLIS, exponential + jitter);
    }

    private static Optional<Long> parseRetryAfterMillis(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String cleaned = value.trim();
        try {
            long seconds = Long.parseLong(cleaned);
            return Optional.of(Math.max(0, seconds * 1000L));
        } catch (NumberFormatException ignored) {
            try {
                long millis = Duration.between(java.time.Instant.now(), ZonedDateTime.parse(cleaned).toInstant()).toMillis();
                return Optional.of(Math.max(0, millis));
            } catch (DateTimeParseException ignoredDate) {
                return Optional.empty();
            }
        }
    }

    private static void logFetch(String url, int attempt, String category, String outcome) {
        System.err.println("GitHub fetch repo=" + repositoryFromUrl(url)
                + " pr=" + prNumberFromUrl(url)
                + " attempt=" + attempt
                + " category=" + category
                + " outcome=" + outcome);
    }

    private static String repositoryFromUrl(String url) {
        MatcherLike matcher = matchApiPullUrl(url);
        return matcher == null ? "unknown" : matcher.owner() + "/" + matcher.repo();
    }

    private static String prNumberFromUrl(String url) {
        MatcherLike matcher = matchApiPullUrl(url);
        return matcher == null ? "unknown" : matcher.number();
    }

    private static MatcherLike matchApiPullUrl(String url) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/repos/([^/]+)/([^/]+)/pulls/(\\d+)").matcher(url);
        if (!matcher.find()) return null;
        return new MatcherLike(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "Unknown error";
        String sanitized = value;
        if (token != null) {
            sanitized = sanitized.replace(token, "[REDACTED]");
        }
        return sanitized.replace('\n', ' ').replace('\r', ' ').trim();
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

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static class HttpStatusException extends IOException {
        private final int statusCode;
        private final Optional<String> retryAfter;

        HttpStatusException(int statusCode, String message, Optional<String> retryAfter) {
            super(message);
            this.statusCode = statusCode;
            this.retryAfter = retryAfter == null ? Optional.empty() : retryAfter;
        }

        int statusCode() {
            return statusCode;
        }

        Optional<String> retryAfter() {
            return retryAfter;
        }
    }

    private record MatcherLike(String owner, String repo, String number) {
    }
}
