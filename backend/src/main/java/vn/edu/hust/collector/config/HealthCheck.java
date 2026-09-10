package vn.edu.hust.collector.config;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Container probe using the JRE already present in the image. */
public final class HealthCheck {
    public static void main(String[] args) {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(3)).GET().build();
            if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200) System.exit(1);
        } catch (Exception failure) { System.exit(1); }
    }
}
