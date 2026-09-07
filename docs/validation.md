# Kiểm thử bản CLI thu gọn

Chạy Maven verify ở thư mục gốc, không cần PostgreSQL hoặc Docker.

Nhóm kiểm tra: URL policy; HTTP fixture; HTML/DOCX extractor; batch import N06, dedup, xuất JSONL, không ghi đè, giới hạn batch và ghi lỗi.

Kết quả bản CLI ngày 05/09/2026: **10 test đạt, 0 thất bại, 0 lỗi, 0 bỏ qua** (URL 2, HTTP 3, extractor 3, batch 2). Maven đóng gói JAR thành công. Đã chạy JAR với --help, xác nhận URL SSO bị bỏ và xuất JSONL; đã kiểm tra parser DOCX trực tiếp từ JAR đóng gói.

Môi trường kiểm tra: Windows, JDK 25 với compiler release 21, Maven 3.9.9 tải riêng trong .tools. Chưa chạy runtime JDK 21 riêng, chưa đánh giá mẫu PDF/XLSX/PPTX thực tế hoặc crawl toàn bộ N06. Không dùng kết quả 13 test của bản full-stack trước để xác nhận bản CLI.

## Đối chiếu khi bổ sung tài liệu ngày 05/09/2026

Kết quả Windows phía trên là ghi nhận có từ trước, không phải lần chạy lại trong phiên viết tài liệu. Lần này đã đọc toàn bộ 4 class production, 4 class test, pom và dữ liệu demo; không đổi mã Java.

| Nhóm test | Số test | Nội dung assertion hiện có |
|---|---|---|
| UrlPolicyTest | 2 | Tracking/fragment/dấu phẩy, query/escaping, ranh giới domain, protocol/credentials, ASSO |
| FetcherTest | 3 | Tải thành công, robots disallow, redirect ngoài origin, giới hạn Content-Length |
| ContentExtractorTest | 3 | HTML bài viết bỏ nav, password form, đọc DOCX tạo trong test |
| CollectorTest | 2 | Import N06, dedup URL/text, JSONL/summary, không ghi đè, limit, lỗi downloader |

Test batch tiêm downloader giả nên không kiểm tra fetchWithRetry production. Test HTTP thay policy để dùng localhost, không kiểm chứng DNS/IP công khai thực tế. Chưa có test riêng cho CLI parsing, retry/backoff, robots lỗi/redirect/cache, hard timeout, ngưỡng HTML, soft error và toàn bộ định dạng Tika.

Đã thử build trên macOS arm64:

```sh
mvn -B clean verify
mvn -B -Dmaven.repo.local=.cache/m2 clean verify
```

Lệnh đầu không có quyền tạo repository tại `~/.m2/repository`. Lệnh thứ hai chuyển cache vào workspace nhưng không resolve được `repo.maven.apache.org` trong môi trường hạn chế mạng. **Build dừng trước compile/test; không có kết quả test mới để xác nhận đạt.** `java -version` báo JDK 25.0.4, trong khi Maven 3.9.16 dùng JDK 26.0.2; cần kiểm tra JAVA_HOME khi tái lập trên máy khác.

Mẫu đã commit tại `output/demo-n06` có 6 kết quả (3 KEPT gồm 2 HTML và 1 PDF, 1 SKIPPED, 2 DUPLICATE). Đây là đầu ra lịch sử của một mẫu nhỏ, không thay thế test tự động/đánh giá toàn N06; lần viết tài liệu này không tải lại URL mẫu.

Trên máy có JDK/Maven và mạng đầy đủ, chạy lại `mvn clean verify`, `java -jar target/collector.jar --help`, rồi smoke test ASSO và mẫu theo [hướng dẫn chuyển máy](project-context.md). Ghi kết quả mới nếu cần dùng làm bằng chứng kiểm thử.
