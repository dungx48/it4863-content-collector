package vn.edu.hust.collector.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.collector.dto.ErrorResponse;
import vn.edu.hust.collector.exception.*;

/** Chuẩn hóa exception của mọi controller thành ErrorResponse JSON. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException error) {
        return ResponseEntity.status(error.status())
            .body(new ErrorResponse(false, error.reason(), error.getMessage()));
    }

    @ExceptionHandler(BusyException.class)
    ResponseEntity<ErrorResponse> busy(BusyException error) {
        // Retry-After gợi ý frontend thử lại sau khi tác vụ hiện tại kết thúc.
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, "1")
            .body(new ErrorResponse(false, "BUSY", error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> invalidBody(Exception error) {
        return badRequest("INVALID_REQUEST", "Yêu cầu phải chứa URL dạng chuỗi, không để trống.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> invalidQuery(ConstraintViolationException error) {
        return badRequest("INVALID_QUERY", "Từ khóa gợi ý không hợp lệ hoặc quá dài.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException error) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(new ErrorResponse(false, "UNSUPPORTED_MEDIA_TYPE", "Content-Type phải là application/json."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException error) {
        // Trả thêm Allow để client biết các HTTP method hợp lệ.
        var builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (error.getSupportedHttpMethods() != null)
            builder.allow(error.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        return builder.body(new ErrorResponse(false, "METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ."));
    }

    private ResponseEntity<ErrorResponse> badRequest(String reason, String message) {
        return ResponseEntity.badRequest().body(new ErrorResponse(false, reason, message));
    }
}
