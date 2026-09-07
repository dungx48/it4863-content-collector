# Hướng dẫn trình diễn bài tập

Mục tiêu demo: đưa vào một link bài viết và hiển thị tiêu đề, nội dung văn bản, liên kết đi ra cùng URL ảnh.

## 1. Chuẩn bị

```powershell
mvn clean verify
java -jar target/collector.jar --serve 8080
```

Mở http://localhost:8080. Server chỉ chạy trên máy local và dừng bằng `Ctrl+C`.

## 2. Kịch bản trình diễn đề xuất

### Cách A — URL mẫu

1. Bấm **Dùng link bài viết mẫu**.
2. Bấm **Bóc tách**.
3. Chỉ vào ba vùng kết quả: **Tiêu đề**, **Nội dung văn bản**, **Các liên kết đi ra**.
4. Cho thấy bảng có cả URL tài liệu/trang và URL ảnh.
5. Bấm **Sao chép JSON** nếu cần trình bày dữ liệu máy đọc được.

### Cách B — gợi ý từ whitelist

1. Gõ `soict`, `act` hoặc một đoạn đường dẫn vào ô URL.
2. Chọn một trong tối đa 20 gợi ý.
3. Bấm **Bóc tách**.

Gợi ý lấy từ `N06_Ctrl+F.txt` đóng gói trong JAR. Danh sách có 9.516 URL duy nhất và hoạt động không cần truy cập GitHub lúc chạy.

### Cách C — import TXT

1. Bấm **Import danh sách .txt**.
2. Chọn file UTF-8 có nhiều URL.
3. Gõ trong ô **Tìm trong file** nếu danh sách dài.
4. Chọn một URL để điền lên ô chính rồi bấm **Bóc tách**.

Trình duyệt chỉ giữ URL thuộc `hust.edu.vn` hoặc subdomain, loại trùng và hiển thị tối đa 200 kết quả khớp mỗi lần. Toàn bộ danh sách, tối đa 50.000 URL/10 MiB, vẫn được giữ để tìm. File không được upload lên server.

## 3. Kết quả mong đợi

API thành công trả JSON dạng:

```json
{
  "success": true,
  "url": "https://soict.hust.edu.vn/bai-viet.html",
  "httpStatus": 200,
  "contentType": "text/html",
  "elapsedMs": 1234,
  "title": "Tiêu đề",
  "text": "Nội dung văn bản...",
  "links": [
    {"url": "https://soict.hust.edu.vn/tai-lieu.pdf", "text": "Tải tài liệu"},
    {"url": "https://soict.hust.edu.vn/images/anh.jpg", "text": "Mô tả ảnh"}
  ]
}
```

Quy tắc liên kết/ảnh:

- Chỉ lấy trong vùng nội dung đã chọn; menu, footer, form và nút chia sẻ bị loại.
- URL tương đối được resolve theo URL cuối sau redirect và thẻ `<base>` nếu có.
- Chỉ giữ URL HTTP(S), gồm cả link cùng domain và ngoài domain xuất hiện trong bài.
- Mô tả link ưu tiên anchor text, `aria-label`, `alt` của ảnh con rồi `title`.
- URL ảnh ưu tiên `data-src`, `data-lazy-src`, `data-original` rồi `src`.
- Mô tả ảnh ưu tiên `alt`, `figcaption` rồi `title`.
- Cặp URL/mô tả trùng bị loại; cùng URL với mô tả khác vẫn được giữ.
- Link tìm được chỉ là đầu ra, không được crawl tiếp.

## 4. Chạy cùng URL bằng CLI

```powershell
java -jar target/collector.jar `
  --url https://soict.hust.edu.vn/bai-giang-dai-chung-cua-unesco-dao-duc-trong-tri-tue-nhan-tao.html `
  --output output/demo-article-01
```

Mở `output/demo-article-01/documents.json`. Mỗi lần chạy phải dùng tên output mới.

## 5. Giải thích nhanh khi bị từ chối

| Reason | Ý nghĩa |
|---|---|
| `INVALID_URL` | URL sai, ngoài HUST hoặc thuộc login/SSO |
| `ROBOTS_DISALLOWED` | robots.txt không cho phép tải |
| `SHORT_OR_LINK_LIST` | Trang quá ngắn hoặc chủ yếu là danh sách link |
| `EMPTY_OR_JS_REQUIRED` | HTML tĩnh không có nội dung đủ dùng |
| `UNSUPPORTED_TYPE` | Định dạng chưa được hỗ trợ |
| `EMPTY_OR_NEEDS_OCR` | Tài liệu không có text trích được |

PDF/Office hiện có thể bóc tách văn bản nhưng chưa lấy hyperlink bên trong tài liệu.
