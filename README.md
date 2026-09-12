# IT4863 — Công cụ thu thập nội dung HUST

Project thực hiện đúng phần **phát triển mã nguồn hỗ trợ thu thập**:

> Link bài viết → tải dữ liệu → bóc tách tiêu đề, nội dung văn bản, liên kết và ảnh đi ra.

Mỗi liên kết/ảnh đi ra gồm URL HTTP(S) đầy đủ và văn bản mô tả. Dữ liệu thu thập có thể nạp vào màn hình tìm kiếm Lucene/TF-IDF. Project chưa có database, OCR hoặc crawl đệ quy.

## Kiến trúc

**Một backend duy nhất** trong `backend/`, chia controller, service, repository,
model, dto và config. Download, parser và batch đều chạy trong backend này.
Frontend ở `frontend/demo/` chỉ là client trình diễn và chạy riêng trên port 3000.

```text
Frontend demo :3000 → HTTP → Backend :8080
                              controller → service → repository
                                            ↓
                                      crawl / extract
```

## Chức năng

- Nhận một URL HUST trên giao diện hoặc CLI.
- Import file TXT trong trình duyệt, chọn tối đa 10 URL và bóc tách tuần tự.
- Chạy batch nhiều URL bằng CLI.
- Tải có kiểm tra domain, địa chỉ IP, robots.txt, redirect và giới hạn dung lượng.
- Bóc tách HTML bằng Jsoup; PDF/Office/ODF/RTF bằng Apache Tika.
- Lấy tiêu đề, văn bản, thẻ liên kết và nguồn ảnh trong vùng bài viết.
- Chuẩn hóa Unicode tiếng Việt và loại nội dung trùng bằng SHA-256 của text.
- Xuất JSON dễ đọc, JSONL theo dòng và báo cáo tổng hợp.
- Xem kết quả trong bảng, mở chi tiết từng bài và tải/copy JSON một bài hoặc cả lô.
- Nạp file hoặc dán JSON để lập chỉ mục và tìm kiếm Lucene/TF-IDF.

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

Kết quả là JAR kèm dependency tại `backend/target/collector.jar`.

## Chạy backend và frontend demo

Chạy cả hai bằng một lệnh tại repository root (sau khi build):

```powershell
.\start.cmd
```

Launcher chờ backend sẵn sàng rồi mở frontend. Truy cập http://localhost:3000;
nhấn `Ctrl+C` để dừng cả hai. Nếu một tiến trình lỗi, tiến trình còn lại cũng được dừng.
Không cần Docker hoặc npm install. Khi sửa Java, chạy lại `mvn verify` trước khi khởi động.
Trên macOS/Linux có thể dùng `node scripts/dev.mjs`.

Hoặc chạy từng tiến trình riêng:

Terminal 1 — backend API độc lập:

```powershell
java -jar backend/target/collector.jar --serve 8080
```

Terminal 2 — frontend demo (Node.js 22+, không cần npm install):

```powershell
node frontend/demo/server.mjs
```

Mở http://localhost:3000. API nằm tại http://127.0.0.1:8080; `/health` trả trạng thái.
Frontend dùng proxy cùng origin tới backend, không cần CORS. Có thể chỉ chạy backend
và gọi API bằng client bất kỳ. `--serve` không còn phục vụ giao diện từ JAR.

Demo hỗ trợ URL trực tiếp và import TXT trong trình duyệt. Batch giao diện tiếp tục khi một URL lỗi,
loại nội dung trùng chính xác và cho tải/sao chép mảng JSON để dùng cho TF-IDF. URL mẫu tự động: http://localhost:3000/?demo=1.
Dừng từng tiến trình bằng `Ctrl+C`.

### Docker Compose

```powershell
docker compose up --build -d backend
# Khi cần thêm frontend demo:
docker compose --profile demo up --build -d
```

Frontend thuộc profile tùy chọn; backend luôn chạy độc lập.
Các port chỉ publish trên loopback. Xem [Compose profiles](https://docs.docker.com/compose/how-tos/profiles/).

### Cấu hình

| Biến | Mặc định | Tiến trình |
|---|---|---|
| `SERVER_HOST` | `127.0.0.1` | Backend |
| `SERVER_PORT` | `8080` | Backend; `--serve <port>` ghi đè |
| `BACKEND_URL` | `http://127.0.0.1:8080` | Proxy của demo |
| `DEMO_HOST` | `127.0.0.1` | Demo |
| `DEMO_PORT` | `3000` | Demo |

Hợp đồng endpoint: [docs/api.md](docs/api.md).

## Chạy CLI

Một URL:

```powershell
java -jar backend/target/collector.jar --url https://soict.hust.edu.vn/bai-viet.html --output output/single-01
```

Một file TXT, mỗi dòng có một URL và có thể có dấu phẩy cuối dòng:

```powershell
java -jar backend/target/collector.jar --input input.txt --limit 100 --output output/batch-01
```

Các option:

| Option | Ý nghĩa |
|---|---|
| `--serve [port]` | Mở backend API; mặc định 8080 |
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
backend/                     # Một ứng dụng Spring Boot, một JAR/container
  pom.xml
  src/main/java/vn/edu/hust/collector/
    Application.java         # @SpringBootApplication + CLI entry point
    controller/              # Spring MVC, validation, HTTP status
    service/                 # Thu thập, retry và batch/dedup
    repository/              # URL nguồn + JSON/JSONL output
    model/                   # Record dữ liệu nghiệp vụ
    dto/                     # API response
    config/                  # Spring beans và health probe
    crawl/                   # URL/IP, robots, HTTP, redirect
    extract/                 # Jsoup/Tika
  src/main/resources/        # Cấu hình/logging; không chứa frontend
  src/test/java/
frontend/demo/               # Giao diện độc lập, không dependency npm
  public/                    # HTML/CSS/JS
  server.mjs                 # Static server + API proxy
compose.yaml                 # Backend + profile demo tùy chọn
pom.xml                      # Maven aggregator chỉ chứa backend
docs/                        # API, kiến trúc, chạy và kiểm thử
```

Build từ root bằng `mvn clean verify`, hoặc build riêng bằng `mvn -f backend/pom.xml clean verify`.
Chi tiết: [backend](backend/README.md), [frontend demo](frontend/demo/README.md).

## Giới hạn hiện tại

- Chỉ chấp nhận `hust.edu.vn` và subdomain, bỏ URL đăng nhập/SSO.
- Tối đa 20 MiB mỗi tài nguyên, năm redirect và ba lần thử tải.
- Xử lý tuần tự; chưa có resume hoặc lưu raw HTML/tài liệu gốc.
- Chưa render JavaScript và chưa cấu hình OCR cho PDF scan.
- Hyperlink bên trong PDF/Office chưa được đưa vào `links`.
- Quy tắc chất lượng HTML có thể bỏ nhầm bài quá ngắn hoặc giữ nhầm trang danh mục.

## Tài liệu

- [Đề xuất OCR ảnh/PDF](docs/ocr-design.md) — thiết kế, chưa triển khai.
- [Trình diễn tìm kiếm Lucene / TF-IDF](docs/search.md) — mở `/search` trên frontend demo.
- [Hướng dẫn trình diễn](docs/demo.md)
- [Thiết kế chi tiết](docs/architecture.md)
- [Sơ đồ thiết kế](docs/design-diagrams.md)
- [Thiết lập và chuyển máy](docs/project-context.md)
- [Phạm vi kiểm thử](docs/validation.md)
