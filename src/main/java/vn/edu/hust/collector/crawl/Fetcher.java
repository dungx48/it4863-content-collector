package vn.edu.hust.collector.crawl;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import crawlercommons.robots.SimpleRobotRulesParser;

public class Fetcher {
    public static final String AGENT = "HustCourseBot";
    public record Fetched(String url, int status, String mime, byte[] bytes) {}
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final UrlPolicy policy;
    private final int maxBytes;
    private final long delay;
    private final Map<String, Long> lastRequest = new HashMap<>();
    private final Map<String, crawlercommons.robots.BaseRobotRules> robots = new HashMap<>();
    public Fetcher(UrlPolicy policy, int maxBytes, long delay) {
        this.policy = policy; this.maxBytes = maxBytes; this.delay = delay;
    }
    private synchronized HttpResponse<java.io.InputStream> request(String url, String domain) throws Exception {
        policy.checkFetch(url, domain);
        String host = URI.create(url).getHost();
        long wait = delay - (System.currentTimeMillis() - lastRequest.getOrDefault(host, 0L));
        if (wait > 0) Thread.sleep(wait);
        lastRequest.put(host, System.currentTimeMillis());
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
            .header("User-Agent", AGENT + "/0.1 (IT4863 educational crawler)").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }
    private boolean allowed(String url, String domain) throws Exception {
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        if (!robots.containsKey(origin)) {
            var response = request(origin + "/robots.txt", domain);
            try (var input = response.body()) {
                int status = response.statusCode();
                if (status == 404) robots.put(origin, new SimpleRobotRulesParser().failedFetch(404));
                else if (status == 200) {
                    byte[] bytes = input.readNBytes(512_001);
                    if (bytes.length > 512_000) throw new IllegalArgumentException("robots.txt too large");
                    robots.put(origin, new SimpleRobotRulesParser().parseContent(origin + "/robots.txt", bytes, "text/plain", AGENT));
                } else throw new IllegalArgumentException("ROBOTS_UNAVAILABLE_HTTP_" + status);
            }
        }
        var rules = robots.get(origin);
        if (rules.getCrawlDelay() > delay) Thread.sleep(Math.min(rules.getCrawlDelay(), 60_000));
        return rules.isAllowed(url);
    }
    public Fetched fetch(String url, String domain) throws Exception {
        String current = url;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (policy.loginUrl(current)) throw new IllegalArgumentException("LOGIN_REQUIRED");
            policy.checkFetch(current, domain);
            if (!allowed(current, domain)) throw new IllegalArgumentException("ROBOTS_DISALLOWED");
            var response = request(current, domain);
            try (var input = response.body()) {
                int status = response.statusCode();
                if (Set.of(301,302,303,307,308).contains(status)) {
                    current = policy.normalize(URI.create(current).resolve(response.headers().firstValue("location").orElseThrow()).toString());
                    continue;
                }
                if (status != 200) return new Fetched(current, status, "", new byte[0]);
                if (response.headers().firstValueAsLong("content-length").orElse(0) > maxBytes) throw new IllegalArgumentException("DOWNLOAD_TOO_LARGE");
                byte[] bytes = input.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) throw new IllegalArgumentException("DOWNLOAD_TOO_LARGE");
                return new Fetched(current, status, response.headers().firstValue("content-type").orElse("application/octet-stream"), bytes);
            }
        }
        throw new IllegalArgumentException("TOO_MANY_REDIRECTS");
    }
}
