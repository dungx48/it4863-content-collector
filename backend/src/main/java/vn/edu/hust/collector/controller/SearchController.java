package vn.edu.hust.collector.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.hust.collector.dto.*;
import vn.edu.hust.collector.service.SearchService;

/** Endpoint trạng thái, tạo chỉ mục và tìm kiếm với mô hình/tham số tùy chọn. */
@RestController
@RequestMapping("/api/v1")
public class SearchController {
    public static final int MAX_CORPUS_BYTES = 20 * 1024 * 1024;
    private final SearchService service;
    public SearchController(SearchService service) { this.service = service; }

    @GetMapping("/index")
    public IndexStatus status() throws IOException { return service.status(); }

    @PostMapping(value = "/index", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> index(HttpServletRequest request) throws IOException {
        // Chặn cả body có Content-Length và body chunked trước khi parse JSON.
        if (request.getContentLengthLong() > MAX_CORPUS_BYTES) return tooLarge();
        byte[] body = request.getInputStream().readNBytes(MAX_CORPUS_BYTES + 1);
        if (body.length > MAX_CORPUS_BYTES) return tooLarge();
        return ResponseEntity.ok(service.index(body));
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam(defaultValue = "") String q,
                                 @RequestParam(defaultValue = "10") int limit,
                                 @RequestParam(defaultValue = "classic") String model,
                                 @RequestParam(defaultValue = "or") String operator,
                                 @RequestParam(defaultValue = "1.2") float k1,
                                 @RequestParam(defaultValue = "0.75") float b,
                                 @RequestParam(defaultValue = "0.5") float lambda) throws IOException {
        return service.search(q, limit, new SearchOptions(model, operator, k1, b, lambda));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> storageError(IOException failure) {
        org.slf4j.LoggerFactory.getLogger(SearchController.class).error("Lucene index I/O failed", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(false, "INDEX_IO_ERROR", "Không đọc/ghi được chỉ mục. Kiểm tra quyền truy cập thư mục chỉ mục và log backend."));
    }

    private ResponseEntity<ErrorResponse> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ErrorResponse(false, "REQUEST_TOO_LARGE", "Tập văn bản không được vượt quá 20 MiB."));
    }
}
