# Collector HTTP API

API tạo chỉ mục và tìm kiếm (Classic TF-IDF, VSM cosine, BM25, LM): [search.md](search.md).

Backend mặc định: `http://127.0.0.1:8080`. Không phục vụ HTML, CSS hoặc JavaScript.
Frontend demo là client riêng tại port 3000. Endpoint cũ `/api/extract` vẫn là alias để giữ tương thích.

## Health

`GET /health` → HTTP 200:

```json
{"status":"UP"}
```

Đây là endpoint Spring Boot Actuator kiểm tra tiến trình, không kiểm tra khả năng truy cập mọi website HUST.

## Thu thập một URL

`POST /api/v1/extract`, `Content-Type: application/json`:

```json
{"url":"https://soict.hust.edu.vn/bai-viet.html"}
```

HTTP 200:

```json
{
  "success": true,
  "url": "https://soict.hust.edu.vn/bai-viet.html",
  "httpStatus": 200,
  "contentType": "text/html",
  "elapsedMs": 1234,
  "title": "Tiêu đề",
  "text": "Nội dung văn bản...",
  "links": [{"url":"https://soict.hust.edu.vn/tai-lieu.pdf","text":"Tải tài liệu"}]
}
```

URL trả về là đích cuối sau redirect. `links` chứa hyperlink và ảnh HTTP(S) trong
vùng bài viết. API không lưu document xuống disk; lưu JSON/JSONL thuộc CLI batch.

## Lỗi

```json
{"success":false,"reason":"INVALID_URL","message":"Chỉ chấp nhận link thuộc hust.edu.vn."}
```

| HTTP | Trường hợp |
|---|---|
| 400 | JSON sai, URL trống/không phải chuỗi, URL ngoài domain/login, query không hợp lệ |
| 404 | Route không tồn tại, gồm `/`, `/app.js`, `/styles.css` trên backend |
| 405 | Method sai; header `Allow` cho biết method hợp lệ |
| 413 | Body trên 8.192 byte |
| 415 | Content-Type khác application/json |
| 422 | Robots/policy chặn hoặc parser loại nội dung; nội dung bị loại kèm `url`, `httpStatus` |
| 429 | Một request thu thập đang chạy; `Retry-After: 1` |
| 502 | Website trả HTTP khác 200 hoặc lỗi tải/parser |
| 500 | Lỗi xử lý không dự kiến |

Mỗi response có `X-Request-Id`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.
Hiện chưa có authentication hoặc durable job queue. Mặc định bind loopback;
Docker Compose cũng chỉ publish port trên loopback. Muốn cung cấp API ra ngoài
cần bổ sung authentication, ingress và giới hạn tài nguyên phù hợp.
