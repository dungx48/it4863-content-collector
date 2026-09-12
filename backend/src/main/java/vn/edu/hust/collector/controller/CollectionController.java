package vn.edu.hust.collector.controller;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.collector.dto.*;
import vn.edu.hust.collector.exception.*;
import vn.edu.hust.collector.service.*;

/** Adapter HTTP: kiểm tra request và đổi kết quả service thành response API. */
@RestController
public class CollectionController {
    private final CollectionService collection;

    public CollectionController(CollectionService collection) {
        this.collection = collection;
    }

    @PostMapping(
        path = {"/api/v1/extract", "/api/extract"},
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> extract(@Valid @RequestBody ExtractionRequest request) {
        // Trả lỗi URL trước khi bắt đầu tải để client nhận mã lỗi rõ ràng.
        String url;
        try {
            url = collection.normalize(request.url());
        } catch (IllegalArgumentException invalid) {
            throw ApiException.badRequest("INVALID_URL", reasonMessage(invalid.getMessage()));
        }

        try {
            var outcome = collection.collect(url);
            var fetched = outcome.fetched();
            if (fetched.status() != 200) {
                throw ApiException.badGateway("HTTP_" + fetched.status(),
                    "Website trả về HTTP " + fetched.status() + ".");
            }
            var content = outcome.content();
            // HTTP 200 vẫn có thể bị từ chối vì nội dung rỗng, ngắn hoặc không hỗ trợ.
            if (!content.keep()) {
                return ResponseEntity.unprocessableContent().body(new RejectedExtractionResponse(
                    false, content.reason(), reasonMessage(content.reason()), fetched.url(), fetched.status()));
            }
            return ResponseEntity.ok(new ExtractionResponse(true, fetched.url(), fetched.status(), content.type(),
                outcome.elapsedMs(), content.title(), content.text(), content.links()));
        } catch (BusyException busy) {
            throw busy;
        } catch (ApiException expected) {
            throw expected;
        } catch (IllegalArgumentException rejected) {
            throw ApiException.unprocessable(rejected.getMessage(), reasonMessage(rejected.getMessage()));
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw ApiException.badGateway("COLLECTION_FAILED", "Không tải hoặc bóc tách được trang.");
        }
    }

    static String reasonMessage(String reason) {
        // Đổi mã nội bộ thành thông báo tiếng Việt; mã lạ vẫn được giữ để debug.
        return switch (reason == null ? "" : reason) {
            case "OUTSIDE_DOMAIN" -> "Chỉ chấp nhận link thuộc hust.edu.vn.";
            case "LOGIN_REQUIRED" -> "Trang yêu cầu đăng nhập nên không được thu thập.";
            case "ROBOTS_DISALLOWED" -> "robots.txt không cho phép bot truy cập URL này.";
            case "SHORT_OR_LINK_LIST" -> "Trang quá ngắn hoặc chủ yếu là danh sách liên kết.";
            case "EMPTY_OR_JS_REQUIRED" -> "Không tìm thấy nội dung tĩnh; trang có thể cần JavaScript.";
            case "SOFT_ERROR" -> "Nội dung có dấu hiệu là trang lỗi.";
            case "UNSUPPORTED_TYPE" -> "Định dạng tài nguyên chưa được hỗ trợ.";
            default -> reason == null || reason.isBlank() ? "Không thể xử lý trang." : reason;
        };
    }
}
