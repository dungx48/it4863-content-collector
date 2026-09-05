# Kiểm thử bản CLI thu gọn

Chạy Maven verify ở thư mục gốc, không cần PostgreSQL hoặc Docker.

Nhóm kiểm tra: URL policy; HTTP fixture; HTML/DOCX extractor; batch import N06, dedup, xuất JSONL, không ghi đè, giới hạn batch và ghi lỗi.

Kết quả bản CLI ngày 05/09/2026: **10 test đạt, 0 thất bại, 0 lỗi, 0 bỏ qua** (URL 2, HTTP 3, extractor 3, batch 2). Maven đóng gói JAR thành công. Đã chạy JAR với --help, xác nhận URL SSO bị bỏ và xuất JSONL; đã kiểm tra parser DOCX trực tiếp từ JAR đóng gói.

Môi trường kiểm tra: Windows, JDK 25 với compiler release 21, Maven 3.9.9 tải riêng trong .tools. Chưa chạy runtime JDK 21 riêng, chưa đánh giá mẫu PDF/XLSX/PPTX thực tế hoặc crawl toàn bộ N06. Không dùng kết quả 13 test của bản full-stack trước để xác nhận bản CLI.
