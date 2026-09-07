# IT4863 — Công cụ thu thập nội dung HUST

Project thực hiện đúng phần **phát triển mã nguồn hỗ trợ thu thập**:

> Link bài viết → tải dữ liệu → bóc tách tiêu đề, nội dung văn bản, liên kết và ảnh đi ra.

Mỗi liên kết/ảnh đi ra gồm URL HTTP(S) đầy đủ và văn bản mô tả. Project chưa triển khai công cụ tìm kiếm, Lucene, database hoặc crawl đệ quy.

## Chức năng

- Nhận một URL HUST trên giao diện hoặc CLI.
- Gợi ý URL từ whitelist `N06_Ctrl+F` gồm 9.516 URL duy nhất.
- Import file TXT trong trình duyệt, lọc URL HUST và chọn một URL để demo.
- Chạy batch nhiều URL bằng CLI.
- Tải có kiểm tra domain, địa chỉ IP, robots.txt, redirect và giới hạn dung lượng.
- Bóc tách HTML bằng Jsoup; PDF/Office/ODF/RTF bằng Apache Tika.
- Lấy tiêu đề, văn bản, thẻ liên kết và nguồn ảnh trong vùng bài viết.
- Chuẩn hóa Unicode tiếng Việt và loại nội dung trùng bằng SHA-256 của text.
- Xuất JSON dễ đọc, JSONL theo dòng và báo cáo tổng hợp.

## Yêu cầu môi trường

- JDK 21 trở lên.
- Maven 3.9 trở lên.
- Có mạng khi Maven cần tải dependency và khi thu thập website.

Kiểm tra môi trường:

```powershell
java -version
mvn -version
```

## Build

Chạy tại thư mục có `pom.xml`:

```powershell
mvn clean verify
```

Kết quả là JAR kèm dependency tại `target/collector.jar`.

## Chạy giao diện demo

```powershell
java -jar target/collector.jar --serve 8080
```

Mở http://localhost:8080 và dùng một trong ba cách:

1. Dán trực tiếp một link bài viết HUST.
2. Gõ tên miền/đường dẫn rồi chọn gợi ý từ whitelist.
3. Bấm **Import danh sách .txt**, lọc và chọn một URL trong file.

Import TXT chỉ tạo danh sách để chọn từng URL trên giao diện. Muốn xử lý toàn bộ file, dùng chế độ CLI batch. File import tối đa 10 MiB, được đọc ngay trong trình duyệt và không gửi lên server. Dừng server bằng `Ctrl+C`.

Có thể mở http://localhost:8080/?demo=1 để chạy URL mẫu tự động.

## Chạy CLI

Một URL:

```powershell
java -jar target/collector.jar --url https://soict.hust.edu.vn/bai-viet.html --output output/single-01
```

Một file TXT, mỗi dòng có một URL và có thể có dấu phẩy cuối dòng:

```powershell
java -jar target/collector.jar --input input.txt --limit 100 --output output/batch-01
```

Chạy trực tiếp whitelist đã đóng gói trong source:

```powershell
java -jar target/collector.jar --input "src/main/resources/N06_Ctrl+F.txt" --limit 100 --output output/n06-01
```

Các option:

| Option | Ý nghĩa |
|---|---|
| `--serve [port]` | Mở giao diện local; mặc định 8080 |
| `--url <URL>` | Xử lý một URL; không dùng cùng `--input` |
| `--input <file>` | Đọc danh sách URL UTF-8 |
| `--limit <n>` | Số dòng URL tối đa; mặc định 100, tối đa 50.000 |
| `--output <dir>` | Thư mục kết quả mới |
| `--help` | In hướng dẫn CLI |

Thư mục output phải chưa tồn tại. Công cụ chỉ xử lý URL được cung cấp, không tự crawl các link tìm thấy.

## Đầu ra

```text
output/batch-01/
  documents.json    # Mảng JSON dễ đọc của các tài liệu được giữ
  documents.jsonl   # Mỗi dòng là một tài liệu được giữ
  results.jsonl     # Kết quả của mọi URL đã xử lý
  summary.json      # Bộ đếm cuối batch
```

Một phần tử trong `documents.json`:

```json
{
  "url": "https://soict.hust.edu.vn/bai-viet.html",
  "title": "Tiêu đề bài viết",
  "text": "Nội dung văn bản đã làm sạch...",
  "links": [
    {"url": "https://soict.hust.edu.vn/tai-lieu.pdf", "text": "Tải tài liệu"},
    {"url": "https://soict.hust.edu.vn/images/anh.jpg", "text": "Ảnh hoạt động"}
  ],
  "contentType": "text/html",
  "contentHash": "...",
  "fetchedAt": "2026-09-08T00:00:00Z"
}
```

| Status | Ý nghĩa |
|---|---|
| `KEPT` | Nội dung đạt quy tắc và được xuất |
| `SKIPPED` | Bị policy hoặc quy tắc nội dung loại |
| `DUPLICATE` | Trùng URL chuẩn hóa hoặc trùng text |
| `FAILED` | Lỗi HTTP, tải, parser hoặc filesystem |

JSONL được flush sau mỗi URL. Nếu batch bị ngắt, các dòng đã ghi vẫn có thể đọc; `summary.json` chỉ xuất khi batch kết thúc bình thường.

## Cấu trúc project

```text
src/main/java/vn/edu/hust/collector/
  Collector.java                 # CLI, batch, dedup và output
  DemoServer.java                # Web local, API extract và whitelist
  crawl/UrlPolicy.java           # Chuẩn hóa, domain và địa chỉ IP
  crawl/Fetcher.java             # robots.txt, HTTP, redirect và dung lượng
  extract/ContentExtractor.java  # Title, text, link, ảnh và giữ/bỏ
src/main/resources/
  N06_Ctrl+F.txt                 # Whitelist dùng cho gợi ý
  web/                           # HTML, CSS và JavaScript của demo
src/test/java/                   # 13 test tự động
docs/                            # Tài liệu kỹ thuật và trình diễn
```

## Giới hạn hiện tại

- Chỉ chấp nhận `hust.edu.vn` và subdomain, bỏ URL đăng nhập/SSO.
- Tối đa 20 MiB mỗi tài nguyên, năm redirect và ba lần thử tải.
- Xử lý tuần tự; chưa có resume hoặc lưu raw HTML/tài liệu gốc.
- Chưa render JavaScript và chưa cấu hình OCR cho PDF scan.
- Hyperlink bên trong PDF/Office chưa được đưa vào `links`.
- Quy tắc chất lượng HTML có thể bỏ nhầm bài quá ngắn hoặc giữ nhầm trang danh mục.

## Tài liệu

- [Hướng dẫn trình diễn](docs/demo.md)
- [Thiết kế chi tiết](docs/architecture.md)
- [Sơ đồ thiết kế](docs/design-diagrams.md)
- [Thiết lập và chuyển máy](docs/project-context.md)
- [Phạm vi kiểm thử](docs/validation.md)
