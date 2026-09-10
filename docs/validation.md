# Kiểm thử và phạm vi xác nhận

Cập nhật theo refactor một backend ngày 10/09/2026.

## Lệnh kiểm thử

```powershell
mvn verify
node --test frontend/demo/server.test.mjs
node --check frontend/demo/public/app.js
```

Windows sandbox có thể cần temp nằm trong workspace; xem [project-context.md](project-context.md).

## Phạm vi

| Nhóm | Số test | Hành vi |
|---|---:|---|
| UrlPolicyTest | 2 | Normalize, tracking/fragment, domain, protocol, credentials, login |
| FetcherTest | 3 | HTTP, robots disallow, redirect, Content-Length |
| ContentExtractorTest | 4 | HTML, login form, DOCX, link/ảnh và lazy-load |
| BatchServiceTest | 2 | Input N06, dedup URL/text, output, summary, không ghi đè, limit/lỗi tải |
| ApplicationTest | 5 | Spring context, Actuator health, API/whitelist, validation, lỗi và HTTP 429 |
| SearchServiceTest | 4 | Công thức TF-IDF, ranking, persistence, thay thế, validation và JSON collector |
| SearchApiTest | 1 | API index/search, validation, giới hạn body cố định và chunked |
| frontend/demo/server.test.mjs | 3 | Proxy giữ payload/status, static, giới hạn body riêng cho index/extract, backend dừng |

21 test Java và 3 test Node đã đạt. Test dùng fixture/loopback, không crawl HUST.
Frontend còn được kiểm tra cú pháp JavaScript.

Smoke test tìm kiếm trên Edge headless đã đạt: chọn mẫu, tạo chỉ mục, thứ hạng/giải thích điểm,
upload JSON collector, truy vấn không có kết quả, chỉ mục tồn tại sau khi backend khởi động lại,
điều hướng hai màn hình và bố cục mobile 390px. Hướng dẫn: [search.md](search.md).

Smoke test bản JAR đã đạt: chạy backend và demo ở hai tiến trình/port riêng,
health probe trả thành công, proxy lấy whitelist và giữ lỗi policy HTTP 400.
Sau khi dừng cả hai tiến trình, CLI vẫn chạy batch và xuất JSON/JSONL đúng.
`docker compose config --quiet` đã đạt; chưa build hoặc chạy container.

Spring Boot JAR phải chứa `Application.class`, controller/service/repository/model và whitelist,
không chứa `web/`, HTML/CSS/JS frontend hoặc `DemoServer.class`.
Maven root chỉ build module backend; frontend test chạy bằng Node riêng.

## Giới hạn xác minh

- Không chạy toàn bộ whitelist 9.516 URL hoặc đánh giá precision/recall heuristic.
- Chưa có test trình duyệt tự động cho mọi thao tác import/copy/render.
- Retry/backoff chưa có fixture kiểm tra thời gian đầy đủ.
- Chưa kiểm tra mọi loại tài liệu Tika, trạng thái robots và DNS thay đổi.
- Compose/Docker cần xác minh riêng theo daemon và image có trên máy;
  cú pháp Compose hợp lệ không đồng nghĩa image đã build/chạy thành công.
- Chưa triển khai auth, database, durable queue, OCR hoặc resume batch.

Dữ liệu `output/demo-n06/` là mẫu lịch sử đã có, không phải kết quả crawl mới của lần refactor.
