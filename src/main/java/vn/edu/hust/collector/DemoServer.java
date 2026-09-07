package vn.edu.hust.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import vn.edu.hust.collector.crawl.*;
import vn.edu.hust.collector.extract.ContentExtractor;

/** Phục vụ giao diện và API demo một URL, chỉ trên địa chỉ loopback. */
public final class DemoServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 8_192;
    private final HttpServer server;
    private final ExecutorService executor;
    private final UrlPolicy policy;
    private final Collector.Downloader downloader;
    private final ContentExtractor extractor = new ContentExtractor();
    private final ObjectMapper json = new ObjectMapper();
    private final List<String> whitelist;

    /** Tạo server với dependency có thể thay bằng fixture trong test. */
    DemoServer(int port, UrlPolicy policy, Collector.Downloader downloader) throws IOException {
        this.policy = policy;
        this.downloader = downloader;
        this.whitelist = loadWhitelist();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", this::handle);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "demo-server");
            thread.setDaemon(false);
            return thread;
        });
        server.setExecutor(executor);
    }

    /** Khởi động server production với policy HUST và Fetcher thật. */
    public static DemoServer start(int port) throws IOException {
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("Port must be 1..65535");
        var policy = new UrlPolicy("hust.edu.vn");
        var fetcher = new Fetcher(policy, 20 * 1024 * 1024, 1_000);
        var demo = new DemoServer(port, policy, url -> Collector.fetchWithRetry(fetcher, url));
        demo.server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(demo::close));
        System.out.println("Demo ready: http://localhost:" + demo.port());
        System.out.println("Press Ctrl+C to stop.");
        return demo;
    }

    /** Khởi động server fixture mà không thêm shutdown hook. */
    void startForTest() { server.start(); }

    /** Trả cổng thật, hữu ích khi test tạo server với port 0. */
    int port() { return server.getAddress().getPort(); }

    /** Định tuyến tài nguyên tĩnh và hai API của bản demo. */
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod())) {
                switch (path) {
                    case "/" -> resource(exchange, "/web/index.html", "text/html; charset=utf-8");
                    case "/styles.css" -> resource(exchange, "/web/styles.css", "text/css; charset=utf-8");
                    case "/app.js" -> resource(exchange, "/web/app.js", "text/javascript; charset=utf-8");
                    case "/api/whitelist" -> whitelist(exchange);
                    default -> jsonError(exchange, 404, "NOT_FOUND", "Không tìm thấy tài nguyên.");
                }
            } else if ("POST".equals(exchange.getRequestMethod()) && "/api/extract".equals(path)) {
                extract(exchange);
            } else {
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                jsonError(exchange, 405, "METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ.");
            }
        } catch (Exception error) {
            jsonError(exchange, 500, "INTERNAL_ERROR", "Không thể xử lý yêu cầu: " + error.getMessage());
        } finally {
            exchange.close();
        }
    }

    /** Trả tối đa 20 URL whitelist có chứa từ khóa, không phân biệt hoa thường. */
    private void whitelist(HttpExchange exchange) throws IOException {
        String query = queryParameter(exchange.getRequestURI().getRawQuery(), "q").strip().toLowerCase(Locale.ROOT);
        if (query.length() > 300) {
            jsonError(exchange, 400, "INVALID_QUERY", "Từ khóa gợi ý quá dài.");
            return;
        }
        List<String> matches = whitelist.stream()
            .filter(url -> query.isEmpty() || url.toLowerCase(Locale.ROOT).contains(query))
            .limit(20)
            .toList();
        jsonResponse(exchange, 200, Map.of(
            "success", true, "total", whitelist.size(), "urls", matches));
    }

    /** Kiểm tra request, tải một URL HUST và trả dữ liệu đã bóc tách. */
    private void extract(HttpExchange exchange) throws IOException {
        long started = System.nanoTime();
        byte[] requestBytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (requestBytes.length > MAX_REQUEST_BYTES) {
            jsonError(exchange, 413, "REQUEST_TOO_LARGE", "Yêu cầu quá lớn.");
            return;
        }
        String input;
        try {
            input = json.readTree(requestBytes).path("url").asText("").strip();
            if (input.isEmpty()) throw new IllegalArgumentException("URL không được để trống.");
        } catch (Exception invalidJson) {
            jsonError(exchange, 400, "INVALID_REQUEST", invalidJson.getMessage());
            return;
        }

        String url;
        try {
            url = policy.normalize(input);
            if (!policy.inDomain(url, "hust.edu.vn")) throw new IllegalArgumentException("Chỉ chấp nhận link thuộc hust.edu.vn.");
            if (policy.loginUrl(url)) throw new IllegalArgumentException("Bỏ qua nội dung yêu cầu đăng nhập.");
        } catch (IllegalArgumentException invalidUrl) {
            jsonError(exchange, 400, "INVALID_URL", invalidUrl.getMessage());
            return;
        }

        try {
            var fetched = downloader.fetch(url);
            if (fetched.status() != 200) {
                jsonError(exchange, 502, "HTTP_" + fetched.status(), "Website trả về HTTP " + fetched.status() + ".");
                return;
            }
            var content = extractor.extract(fetched.bytes(), fetched.url(), fetched.mime());
            if (!content.keep()) {
                jsonResponse(exchange, 422, Map.of(
                    "success", false, "reason", content.reason(),
                    "message", reasonMessage(content.reason()), "url", fetched.url(),
                    "httpStatus", fetched.status()));
                return;
            }
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            jsonResponse(exchange, 200, Map.of(
                "success", true, "url", fetched.url(), "httpStatus", fetched.status(),
                "contentType", content.type(), "elapsedMs", elapsed,
                "title", content.title(), "text", content.text(), "links", content.links()));
        } catch (IllegalArgumentException rejected) {
            jsonError(exchange, 422, rejected.getMessage(), reasonMessage(rejected.getMessage()));
        } catch (Exception failure) {
            jsonError(exchange, 502, failure.getClass().getSimpleName(), "Không tải hoặc bóc tách được trang: " + failure.getMessage());
        }
    }

    /** Đổi mã lý do nội bộ thành thông báo ngắn cho giao diện. */
    private static String reasonMessage(String reason) {
        return switch (reason == null ? "" : reason) {
            case "LOGIN_REQUIRED" -> "Trang yêu cầu đăng nhập nên không được thu thập.";
            case "ROBOTS_DISALLOWED" -> "robots.txt không cho phép bot truy cập URL này.";
            case "SHORT_OR_LINK_LIST" -> "Trang quá ngắn hoặc chủ yếu là danh sách liên kết, chưa được coi là bài viết.";
            case "EMPTY_OR_JS_REQUIRED" -> "Không tìm thấy nội dung tĩnh; trang có thể cần JavaScript.";
            case "SOFT_ERROR" -> "Nội dung có dấu hiệu là trang lỗi.";
            case "UNSUPPORTED_TYPE" -> "Định dạng tài nguyên chưa được hỗ trợ.";
            default -> reason == null || reason.isBlank() ? "Không thể xử lý trang." : reason;
        };
    }

    /** Gửi một resource đóng gói trong JAR với cache bị tắt. */
    private void resource(HttpExchange exchange, String name, String type) throws IOException {
        try (InputStream input = DemoServer.class.getResourceAsStream(name)) {
            if (input == null) { jsonError(exchange, 404, "NOT_FOUND", "Thiếu tài nguyên giao diện."); return; }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    /** Đọc whitelist classpath, bỏ phần giới thiệu, dấu phẩy cuối và URL trùng. */
    private static List<String> loadWhitelist() throws IOException {
        try (InputStream input = DemoServer.class.getResourceAsStream("/N06_Ctrl+F.txt")) {
            if (input == null) throw new IOException("Thiếu whitelist N06_Ctrl+F.");
            var urls = new LinkedHashSet<String>();
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    String value = line.replace("\uFEFF", "").strip();
                    if (value.endsWith(",")) value = value.substring(0, value.length() - 1).strip();
                    if (value.startsWith("http://") || value.startsWith("https://")) urls.add(value);
                }
            }
            return List.copyOf(urls);
        }
    }

    /** Đọc một query parameter đã percent-decode; trả chuỗi rỗng nếu thiếu. */
    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) return "";
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            if (!URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) continue;
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return "";
    }

    /** Gửi lỗi theo cùng schema success/reason/message. */
    private void jsonError(HttpExchange exchange, int status, String reason, String message) throws IOException {
        jsonResponse(exchange, status, Map.of("success", false, "reason", reason == null ? "ERROR" : reason,
            "message", message == null ? "Không thể xử lý yêu cầu." : message));
    }

    /** Serialize UTF-8 JSON và đặt các header an toàn cơ bản. */
    private void jsonResponse(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = json.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    /** Dừng listener ngay và kết thúc executor. */
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
