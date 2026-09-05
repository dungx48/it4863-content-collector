# IT4863 — Công cụ thu thập nội dung HUST

Project tập trung vào đề bài:

**URL → kiểm tra khả dụng → tải về → phân loại giữ/bỏ → bóc tách nội dung tìm kiếm.**

Dùng danh sách `N06_Ctrl+F` của bài trước. Chương trình Java dòng lệnh xử lý từng URL và xuất văn bản/báo cáo ra file, không cần database hoặc server.

## Stack giữ lại

| Thành phần | Mục đích |
|---|---|
| Java 21 + Maven | Chạy và đóng gói chương trình |
| JDK HttpClient | Kiểm tra HTTP, tải tài nguyên, xử lý redirect |
| Jsoup | Đọc HTML, lấy tiêu đề và nội dung chính |
| Apache Tika | Nhận dạng và đọc PDF/DOCX/XLSX/PPTX và tài liệu khác |
| crawler-commons | Đọc robots.txt |
| Jackson | Xuất JSON/JSONL |

Spring Boot, REST API, PostgreSQL/Flyway, Lucene, Angular, Docker và Playwright không còn trong project đang build. Ranking, highlight, phân trang và crawl đệ quy được gác lại. Text tiếng Việt được giữ nguyên dấu, chuẩn hóa Unicode để dùng cho bài tìm kiếm sau.

Phần web/search cũ nằm ở `archive/previous-full-stack/` để có thể lấy lại; Maven tại thư mục gốc không build phần đó. Đây là lưu trữ mã cũ, không phải hệ thống đang được duy trì.

## Build và chạy

Cần JDK 21 trở lên, Maven 3.9+:

```sh
mvn clean verify
java -jar target/collector.jar --help
```

Xử lý một URL:

```sh
java -jar target/collector.jar --url https://hust.edu.vn/ --output output/single
```

Trang chủ có thể bị loại vì không phải trang nội dung; nên dùng URL bài viết để kiểm tra bóc tách.

Đặt file nhóm vào thư mục làm việc và thử 100 dòng URL đầu:

```sh
java -jar target/collector.jar --input N06_Ctrl+F --limit 100 --output output/sample
```

Sau khi đánh giá mẫu, có thể xử lý toàn danh sách:

```sh
java -jar target/collector.jar --input N06_Ctrl+F --limit 50000 --output output/full
```

Mặc định limit 100, tối đa 50.000. Giới hạn tính theo dòng URL, gồm dòng lỗi/trùng; phần giới thiệu không tính. Công cụ xử lý dấu phẩy cuối dòng của file nhóm và không mở rộng sang link mới.

Thư mục output phải chưa tồn tại để tránh ghi đè. Nếu không truyền, tự tạo `output/run-<thời điểm>`. Mỗi lần chạy độc lập, chưa có resume.

## Đầu ra

```text
output/sample/
  documents.jsonl   # Một nội dung được giữ trên mỗi dòng
  results.jsonl     # Kết quả từng URL, kể cả lỗi/bị bỏ
  summary.json      # Số đã xử lý, giữ, bỏ, trùng, lỗi, còn lại
```

Documents chứa URL, title, text, contentType, contentHash và fetchedAt.
Results chứa URL gốc/chuẩn hóa/cuối sau redirect, mã HTTP nếu có, status, reason, loại nội dung và hash nếu có.

| Status | Ý nghĩa |
|---|---|
| KEPT | Giữ và xuất nội dung |
| SKIPPED | Bỏ theo quy tắc: login, ngoài domain, danh mục, định dạng chưa hỗ trợ… |
| DUPLICATE | URL hoặc text trùng trong lần chạy |
| FAILED | Lỗi HTTP, tải hoặc bóc tách; xem reason |

Kết quả flush sau mỗi URL. Nếu bị ngắt, các dòng đã ghi vẫn còn; summary chỉ có khi batch kết thúc. Chưa lưu file tải gốc, chỉ lưu text/metadata. URL trùng được bỏ trước tải; text trùng được phát hiện sau tải/bóc tách.

## Cấu trúc đang sử dụng

```text
pom.xml
src/main/java/vn/edu/hust/collector/
  Collector.java                 # CLI, đọc danh sách, điều phối, xuất kết quả
  crawl/UrlPolicy.java           # Chuẩn hóa/kiểm tra URL
  crawl/Fetcher.java             # HTTP, robots, redirect, giới hạn tải
  extract/ContentExtractor.java  # Bóc tách và quyết định giữ/bỏ
src/test/java/                   # Test URL, HTTP, extractor, batch output
docs/architecture.md
docs/validation.md
archive/previous-full-stack/     # Phần cũ, không tham gia build
```

Chỉ xử lý URL công khai thuộc hust.edu.vn/subdomain; bỏ login/SSO. Tối đa 20 MiB mỗi tài nguyên, 5 redirect, độ trễ request cùng host 1 giây. Retry lỗi I/O và HTTP 429/5xx tối đa 3 lần.

Quy tắc HTML cần đánh giá trên mẫu nhóm, có thể bỏ nhầm thông báo ngắn hoặc giữ nhầm danh mục. Chưa có JavaScript renderer hoặc OCR. Xem [kiến trúc và giới hạn](docs/architecture.md), [kiểm thử](docs/validation.md).
