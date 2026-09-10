package vn.edu.hust.collector.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;

    private ApiException(HttpStatus status, String reason, String message) {
        super(message);
        this.status = status;
        this.reason = reason == null || reason.isBlank() ? "ERROR" : reason;
    }

    public static ApiException badRequest(String reason, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, reason, message);
    }

    public static ApiException unprocessable(String reason, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, reason, message);
    }

    public static ApiException badGateway(String reason, String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, reason, message);
    }

    public HttpStatus status() { return status; }
    public String reason() { return reason; }
}
