# Sơ đồ kiến trúc

Một backend, các layer gọi nhau trong cùng JVM. Frontend chỉ là demo tùy chọn.
Chi tiết quy tắc thu thập: [architecture.md](architecture.md).

## Triển khai

```mermaid
flowchart LR
  Browser[Trình duyệt] --> Demo[Frontend demo :3000]
  Demo -->|HTTP proxy| Backend[Backend collector :8080]
  Client[API client khác] --> Backend
  Backend --> HUST[Website HUST]
  CLI[CLI cùng backend JAR] --> Output[JSON / JSONL]
```

Frontend không cần thiết để chạy API/CLI. Docker Compose có một backend và
profile `demo` để bật giao diện khi trình diễn.

## Layer trong backend

```mermaid
flowchart TD
  App[Spring Boot Application + config] --> Controller[Spring MVC CollectionController]
  Controller --> Collection[CollectionService]
  Controller --> White[WhitelistService]
  White --> URLRepo[UrlListRepository]
  App --> Batch[BatchService]
  Batch --> Collection
  Batch --> Output[BatchOutputRepository]
  Collection --> Download[DownloadService]
  Download --> Fetcher[Fetcher + UrlPolicy]
  Collection --> Parser[ContentExtractor]
  Collection -. sử dụng .-> Model[model]
  Output -. lưu .-> Model
  Controller -. response .-> DTO[dto]
```

Controller xử lý HTTP, service chứa nghiệp vụ, repository truy cập dữ liệu.
Model không phụ thuộc layer khác. `crawl`/`extract` là thành phần trong backend.

## Một request qua frontend demo

```mermaid
sequenceDiagram
  participant UI as Browser
  participant Demo as Demo proxy
  participant API as CollectionController
  participant Service as CollectionService
  participant Web as HUST
  UI->>Demo: POST /api/v1/extract {url}
  Demo->>API: Chuyển request tới backend
  API->>API: Validate JSON, giới hạn body và concurrency
  API->>Service: normalize + collect
  Service->>Web: robots.txt + GET, kiểm tra policy/redirect
  Web-->>Service: HTML/PDF/Office
  Service->>Service: Jsoup/Tika, lọc nội dung
  Service-->>API: CollectionOutcome
  API-->>Demo: JSON + HTTP status
  Demo-->>UI: Giữ nguyên response/status
```

## Batch

```mermaid
flowchart TD
  Input[TXT hoặc một URL] --> Normalize[Chuẩn hóa / domain / login]
  Normalize --> Seen{URL trùng?}
  Seen -->|Có| Duplicate[DUPLICATE]
  Seen -->|Không| Fetch[Tải qua DownloadService]
  Fetch --> Parse[Parser / chất lượng nội dung]
  Parse --> Hash{Hash text trùng?}
  Hash -->|Có| Duplicate
  Hash -->|Không| Kept[KEPT + Document]
  Normalize -->|Bị chặn| Skip[SKIPPED]
  Parse -->|Bị loại| Skip
  Fetch -->|Lỗi| Failed[FAILED]
  Kept --> Repo[BatchOutputRepository]
  Skip --> Repo
  Failed --> Repo
  Duplicate --> Repo
  Repo --> Files[documents.json + JSONL + summary]
```
