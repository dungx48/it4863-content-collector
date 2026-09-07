package vn.edu.hust.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import vn.edu.hust.collector.crawl.*;
import static org.assertj.core.api.Assertions.*;

class DemoServerTest {
    private DemoServer server;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach void stop() { if (server != null) server.close(); }

    @Test void servesInterfaceAndExtractsOneArticle() throws Exception {
        String html = """
            <html><head><meta charset="utf-8"></head><body><article>
            <h1>Thông báo học bổng</h1><div class="entry-content"><p>%s</p>
            <a href="/files/notice.pdf">Tải thông báo</a>
            <img src="/images/scholarship.jpg" alt="Sinh viên nhận học bổng"></div></article></body></html>
            """.formatted("Nội dung dành cho sinh viên Bách khoa Hà Nội. ".repeat(10));
        server = new DemoServer(0, new UrlPolicy("hust.edu.vn"), url ->
            new Fetcher.Fetched(url, 200, "text/html", html.getBytes(StandardCharsets.UTF_8)));
        server.startForTest();

        var page = client.send(HttpRequest.newBuilder(uri("/")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Link bài viết", "Các liên kết đi ra");

        var suggestions = client.send(HttpRequest.newBuilder(uri("/api/whitelist?q=soict")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(suggestions.statusCode()).isEqualTo(200);
        var suggestionBody = json.readTree(suggestions.body());
        assertThat(suggestionBody.path("total").asInt()).isGreaterThan(9_000);
        assertThat(suggestionBody.path("urls").size()).isPositive();
        suggestionBody.path("urls").forEach(node -> assertThat(node.asText()).containsIgnoringCase("soict"));

        var response = post("{\"url\":\"https://soict.hust.edu.vn/article.html\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("title").asText()).isEqualTo("Thông báo học bổng");
        assertThat(body.path("text").asText()).contains("Nội dung dành cho sinh viên");
        assertThat(body.path("links").get(0).path("url").asText()).isEqualTo("https://soict.hust.edu.vn/files/notice.pdf");
        assertThat(body.path("links").get(0).path("text").asText()).isEqualTo("Tải thông báo");
        assertThat(body.path("links").get(1).path("url").asText()).isEqualTo("https://soict.hust.edu.vn/images/scholarship.jpg");
        assertThat(body.path("links").get(1).path("text").asText()).isEqualTo("Sinh viên nhận học bổng");
    }

    @Test void rejectsExternalAndLoginUrlsWithoutDownloading() throws Exception {
        server = new DemoServer(0, new UrlPolicy("hust.edu.vn"), url -> { throw new AssertionError("must not download"); });
        server.startForTest();
        var external = post("{\"url\":\"https://example.org/article\"}");
        assertThat(external.statusCode()).isEqualTo(400);
        assertThat(json.readTree(external.body()).path("reason").asText()).isEqualTo("INVALID_URL");
        var login = post("{\"url\":\"https://asso.hust.edu.vn/adfs/ls/\"}");
        assertThat(login.statusCode()).isEqualTo(400);
        assertThat(json.readTree(login.body()).path("message").asText()).contains("đăng nhập");
    }

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/api/extract"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + server.port() + path); }
}
