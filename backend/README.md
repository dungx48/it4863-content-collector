# Collector backend

Một backend Spring Boot duy nhất, gồm API và CLI batch. Không chứa hoặc phục vụ frontend.

```text
src/main/java/vn/edu/hust/collector/
  Application.java       # @SpringBootApplication + CLI entry point
  config/                # Spring beans + health probe
  controller/            # Spring MVC, validation và xử lý lỗi
  dto/                   # API response
  service/               # Thu thập, retry, batch/dedup, whitelist
  repository/            # Đọc URL + ghi output JSON/JSONL
  model/                 # Dữ liệu thu thập/kết quả batch
  crawl/                 # HTTP, robots, URL/IP policy
  extract/               # Jsoup/Tika parser
```

Build và chạy từ thư mục `backend`:

```powershell
mvn clean verify
java -jar target/collector.jar --serve
```

Trong IntelliJ, reload Maven rồi debug trực tiếp class `Application`. Không truyền tham số
để chạy API; dùng `--url` hoặc `--input` trong Run Configuration khi cần debug batch.

Mặc định API lắng nghe `127.0.0.1:8080`. Cấu hình bằng `SERVER_HOST`, `SERVER_PORT`,
hoặc ghi đè port bằng `--serve 8081`. Actuator `/health` trả trạng thái tiến trình;
`POST /api/v1/extract` thu thập một URL; `GET /api/v1/whitelist?q=soict` tìm gợi ý.

CLI cùng nghiệp vụ, không yêu cầu HTTP server hoặc frontend đang chạy:

```powershell
java -jar target/collector.jar --input input.txt --limit 100 --output output/new-run
```

Repository hiện lưu file; chưa dùng database. Chi tiết schema và status:
[API](../docs/api.md), [kiến trúc](../docs/architecture.md).

Tính năng tìm kiếm nằm trong cùng backend: SearchController → SearchService → LuceneIndexRepository.
Chỉ mục Lucene lưu trên ổ đĩa, xếp hạng bằng ClassicSimilarity (TF-IDF).
Xem [hướng dẫn tạo chỉ mục/tìm kiếm](../docs/search.md).
