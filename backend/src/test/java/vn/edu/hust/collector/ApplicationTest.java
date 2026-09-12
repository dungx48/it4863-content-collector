package vn.edu.hust.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import vn.edu.hust.collector.model.FetchedContent;
import vn.edu.hust.collector.service.Downloader;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@ContextConfiguration(classes = {Application.class, ApplicationTest.FixtureConfiguration.class})
class ApplicationTest {
    @LocalServerPort int port;
    @Autowired FixtureDownloader downloader;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void rejectUnexpectedDownload() {
        downloader.use(url -> { throw new AssertionError("unexpected download: " + url); });
    }

    @Test void startsSpringContextAndExtractsOneArticle() throws Exception {
        String html = """
            <html><head><meta charset="utf-8"></head><body><article>
            <h1>Thông báo học bổng</h1><div class="entry-content"><p>%s</p>
            <a href="/files/notice.pdf">Tải thông báo</a>
            <img src="/images/scholarship.jpg" alt="Sinh viên nhận học bổng"></div></article></body></html>
            """.formatted("Nội dung dành cho sinh viên Bách khoa Hà Nội. ".repeat(10));
        downloader.use(url -> new FetchedContent(url, 200, "text/html", html.getBytes(StandardCharsets.UTF_8)));

        assertThat(get("/health").statusCode()).isEqualTo(200);
        assertThat(get("/").statusCode()).isEqualTo(404);
        assertThat(get("/app.js").statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/whitelist?q=soict").statusCode()).isEqualTo(404);

        var response = post("{\"url\":\"https://soict.hust.edu.vn/article.html\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("title").asText()).isEqualTo("Thông báo học bổng");
        assertThat(body.path("text").asText()).contains("Nội dung dành cho sinh viên");
        assertThat(body.path("links").get(0).path("url").asText())
            .isEqualTo("https://soict.hust.edu.vn/files/notice.pdf");
        assertThat(body.path("links").get(1).path("url").asText())
            .isEqualTo("https://soict.hust.edu.vn/images/scholarship.jpg");
    }

    @Test void rejectsInvalidRequestsWithoutDownloading() throws Exception {
        for (String payload : new String[]{"", "null", "[]", "{}", "{\"url\":1}", "{\"url\":\"\"}"}) {
            assertThat(post(payload).statusCode()).as(payload).isEqualTo(400);
        }
        assertThat(post("x".repeat(8_193)).statusCode()).isEqualTo(413);
        assertThat(post("{\"url\":\"https://example.org/article\"}").statusCode()).isEqualTo(400);
        var login = post("{\"url\":\"https://asso.hust.edu.vn/adfs/ls/\"}");
        assertThat(login.statusCode()).isEqualTo(400);
        assertThat(json.readTree(login.body()).path("message").asText()).contains("đăng nhập");

        var noType = client.send(HttpRequest.newBuilder(uri("/api/v1/extract"))
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(noType.statusCode()).isEqualTo(415);
        var wrongMethod = get("/api/v1/extract");
        assertThat(wrongMethod.statusCode()).isEqualTo(405);
        assertThat(wrongMethod.headers().firstValue("Allow")).contains("POST");
    }

    @Test void mapsUpstreamFailuresAndRejectedContent() throws Exception {
        downloader.use(url -> {
            if (url.endsWith("missing")) return new FetchedContent(url, 404, "", new byte[0]);
            if (url.endsWith("failure")) throw new java.io.IOException("fixture failure");
            return new FetchedContent(url, 200, "text/html",
                "<html><body>short</body></html>".getBytes(StandardCharsets.UTF_8));
        });
        assertThat(post("{\"url\":\"https://hust.edu.vn/missing\"}").statusCode()).isEqualTo(502);
        assertThat(post("{\"url\":\"https://hust.edu.vn/failure\"}").statusCode()).isEqualTo(502);
        var rejected = post("{\"url\":\"https://hust.edu.vn/short\"}");
        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(json.readTree(rejected.body()).path("reason").asText()).isEqualTo("SHORT_OR_LINK_LIST");
    }

    @Test void actuatorHealthRemainsResponsiveWhileCollectionAppliesBackpressure() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        downloader.use(url -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("fixture timeout");
            return new FetchedContent(url, 404, "", new byte[0]);
        });
        var first = client.sendAsync(request("/api/v1/extract", "{\"url\":\"https://hust.edu.vn/a\"}"),
            HttpResponse.BodyHandlers.ofString());
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(get("/health").statusCode()).isEqualTo(200);
            var busy = post("{\"url\":\"https://hust.edu.vn/b\"}");
            assertThat(busy.statusCode()).isEqualTo(429);
            assertThat(busy.headers().firstValue("Retry-After")).contains("1");
        } finally {
            release.countDown();
        }
        assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(502);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(java.time.Duration.ofSeconds(5)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(request("/api/v1/extract", body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String path, String body) {
        return HttpRequest.newBuilder(uri(path)).timeout(java.time.Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean @Primary FixtureDownloader fixtureDownloader() { return new FixtureDownloader(); }
    }

    static final class FixtureDownloader implements Downloader {
        private volatile Downloader delegate = url -> { throw new AssertionError("unexpected download"); };
        void use(Downloader next) { delegate = next; }
        @Override public FetchedContent fetch(String url) throws Exception { return delegate.fetch(url); }
    }
}
