package vn.edu.hust.collector.crawl;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class FetcherTest {
    HttpServer server;
    String origin;
    Fetcher fetcher;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        origin="http://127.0.0.1:"+server.getAddress().getPort();
        UrlPolicy testPolicy=new UrlPolicy("127.0.0.1") {
            @Override public void checkFetch(String url,String domain) {
                if (!url.startsWith(origin+"/")) throw new IllegalArgumentException("Outside test origin");
            }
            @Override public String normalize(String value) { return URI.create(value).normalize().toString(); }
        };
        fetcher=new Fetcher(testPolicy,1024,0);
        server.createContext("/robots.txt",exchange -> {
            byte[] data="User-agent: *\nDisallow: /private\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,data.length); try(var body=exchange.getResponseBody()) { body.write(data); }
        });
        server.createContext("/ok",exchange -> {
            byte[] data="hello".getBytes(); exchange.sendResponseHeaders(200,data.length);
            try(var body=exchange.getResponseBody()) { body.write(data); }
        });
        server.createContext("/redirect",exchange -> {
            exchange.getResponseHeaders().add("Location","https://outside.test/"); exchange.sendResponseHeaders(302,-1); exchange.close();
        });
        server.createContext("/large",exchange -> {
            exchange.sendResponseHeaders(200,2048); try(var body=exchange.getResponseBody()) { body.write(new byte[2048]); }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void downloadsAllowedContent() throws Exception { assertThat(fetcher.fetch(origin+"/ok","127.0.0.1").bytes()).isEqualTo("hello".getBytes()); }
    @Test void rejectsRobotsDeniedAndExternalRedirect() {
        assertThatThrownBy(() -> fetcher.fetch(origin+"/private","127.0.0.1")).hasMessageContaining("ROBOTS_DISALLOWED");
        assertThatThrownBy(() -> fetcher.fetch(origin+"/redirect","127.0.0.1")).hasMessageContaining("Outside test origin");
    }
    @Test void limitsDownloadSize() { assertThatThrownBy(() -> fetcher.fetch(origin+"/large","127.0.0.1")).hasMessageContaining("DOWNLOAD_TOO_LARGE"); }
}
