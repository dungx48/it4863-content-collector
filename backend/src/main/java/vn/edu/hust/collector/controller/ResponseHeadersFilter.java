package vn.edu.hust.collector.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Thêm header truy vết/bảo vệ và chặn sớm body extract quá lớn. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseHeadersFilter extends HttpFilter {
    private static final int MAX_REQUEST_BYTES = 8_192;

    @Override protected void doFilter(HttpServletRequest request, HttpServletResponse response,
                                      FilterChain chain) throws IOException, ServletException {
        response.setHeader("X-Request-Id", UUID.randomUUID().toString());
        // Mọi response có ID riêng để đối chiếu log và không được browser cache.
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (request.getMethod().equals("POST") && request.getRequestURI().endsWith("/extract")
                && request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            // Chỉ chặn sớm khi Content-Length đã biết; proxy/controller vẫn giới hạn body thật.
            byte[] body = "{\"success\":false,\"reason\":\"REQUEST_TOO_LARGE\",\"message\":\"Yêu cầu quá lớn.\"}"
                .getBytes(StandardCharsets.UTF_8);
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType("application/json;charset=UTF-8");
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }
        chain.doFilter(request, response);
    }
}
