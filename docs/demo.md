# Hướng dẫn trình diễn bài tập

Mục tiêu demo: đưa vào một link bài viết và hiển thị tiêu đề, nội dung văn bản, liên kết đi ra cùng URL ảnh.

## 1. Chuẩn bị

```powershell
mvn clean verify
java -jar backend/target/collector.jar --serve 8080
```

Mở terminal thứ hai và chạy frontend riêng:

```powershell
node frontend/demo/server.mjs
```

Mở http://localhost:3000. Backend API ở port 8080. Dừng từng tiến trình bằng `Ctrl+C`.

## 2. Kịch bản trình diễn đề xuất

### Cách A — URL mẫu

1. Bấm **Dùng link bài viết mẫu**.
2. Bấm **Bóc tách**.
3. Trong bảng kết quả, bấm **Xem chi tiết**.
4. Xem tiêu đề, nội dung; mở **Liên kết và ảnh đi ra** để xem các URL.
5. Bấm **Sao chép JSON bài này** hoặc **Tải JSON bài này** nếu cần dữ liệu riêng bài đó.
6. Bấm **Đóng** hoặc Escape để quay lại bảng.

### Cách B — import TXT

1. Bấm **Import danh sách .txt**.
2. Chọn file UTF-8 có nhiều URL.
3. Gõ trong ô **Tìm trong file** nếu danh sách dài.
4. Chọn tối đa 10 URL rồi bấm **Bóc tách các URL đã chọn**.
5. Theo dõi bảng trạng thái; lỗi của một URL không làm dừng các URL còn lại.
6. Bấm **Tải JSON để tìm kiếm** hoặc **Sao chép JSON** để đưa mảng kết quả thành công sang TF-IDF.

Trình duyệt chỉ giữ URL thuộc `hust.edu.vn` hoặc subdomain, loại trùng và hiển thị tối đa 200 kết quả khớp mỗi lần. Toàn bộ danh sách, tối đa 50.000 URL/10 MiB, vẫn được giữ để tìm. Batch chạy tuần tự tối đa 10 URL, loại text trùng chính xác và có thể dừng giữa chừng. File không được upload lên server.

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
java -jar backend/target/collector.jar `
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

## 6. Bảng kết quả và xuất JSON

Bảng có trạng thái, tiêu đề/lỗi, nguồn và nút xem chi tiết. Nguồn hiển thị tên miền;
di chuột để xem URL đầy đủ hoặc bấm để mở trang nguồn. Bảng cuộn riêng khi dài.
Kết quả một URL cũng dùng bảng này. Không tự mở chi tiết sau khi hoàn tất.

| Trạng thái | Hành vi |
|---|---|
| Thành công | Xem chi tiết được; có trong tập JSON xuất chung |
| Trùng nội dung | Text giống bài đã giữ trong lượt này; không xuất thêm |
| Lỗi | Hiển thị lý do; tiếp tục URL kế tiếp |
| Đã dừng | Request đang xử lý bị hủy; không bắt đầu URL tiếp theo |

URL chưa chạy sau khi dừng không có dòng kết quả. Hủy ở trình duyệt không bảo đảm
tác vụ đang chạy phía backend kết thúc ngay. Các bài đã hoàn tất vẫn được giữ.

Chi tiết hiển thị HTTP, định dạng, số ký tự, số liên kết, thời gian, tiêu đề và text.
Liên kết/ảnh mặc định thu gọn. Có thể sao chép riêng text hoặc xuất JSON bài đang mở.

| Vị trí | Nút | Dữ liệu |
|---|---|---|
| Dưới bảng | Tải JSON để tìm kiếm | `documents.json`: mảng bài thành công không trùng |
| Dưới bảng | Sao chép JSON | Cùng tập dữ liệu với file cả lô |
| Trong chi tiết | Tải JSON bài này | `document.json`: mảng chứa đúng bài đang xem |
| Trong chi tiết | Sao chép JSON bài này | Cùng mảng một bài |
| Trong chi tiết | Sao chép ở nội dung | Text thuần của bài đang xem |

Xuất chung có sau khi lượt chạy kết thúc hoặc dừng và có ít nhất một bài thành công.
Có thể mở và xuất riêng một bài đã thành công ngay khi lô còn chạy.
Kết quả thu thập chỉ nằm trong RAM trình duyệt: tải lại trang, chuyển sang TF-IDF
hoặc chạy lượt mới sẽ mất tập kết quả hiện tại. Hãy tải/copy trước khi chuyển màn hình.
Các file tải nhiều lần có thể được trình duyệt tự thêm hậu tố vào tên.

## 7. Đưa kết quả sang TF-IDF

1. Tải/copy JSON cả lô hoặc từng bài.
2. Chọn **Tìm kiếm TF-IDF** trên thanh điều hướng.
3. Chọn file hoặc dán JSON rồi bấm **Tạo chỉ mục**.
4. Nhập truy vấn; xem thứ hạng và giải thích điểm.

Chi tiết định dạng, giới hạn và API: [search.md](search.md).

## 8. Lỗi khi chạy demo

### Cổng 8080 hoặc 3000 đang bận

`EADDRINUSE` nghĩa là có tiến trình khác đang nghe trên cổng đó. Kiểm tra:

```powershell
netstat -ano | Select-String ':8080\s+.*LISTENING|:3000\s+.*LISTENING'
```

Cột cuối là PID. Dùng `Get-Process -Id <PID>` để xác định tiến trình.
Nếu là phiên demo cũ, ưu tiên Ctrl+C tại terminal đã chạy nó; nếu terminal đã đóng,
dùng `Stop-Process -Id <PID>` với PID vừa kiểm tra rồi chạy lại `.\start.cmd`.
Không dùng PID từ lần chạy trước hoặc dừng tiến trình chưa rõ nguồn gốc.

### Build không ghi được collector.jar

Dừng backend đang dùng JAR rồi chạy `mvn verify`. Nếu chưa có Maven trong PATH:

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd verify
.\start.cmd
```

### Không copy được hoặc giao diện chưa cập nhật

Nếu trình duyệt từ chối clipboard, dùng tải JSON. Khi giao diện cũ còn trong cache,
nhấn Ctrl+F5. Kiểm tra backend bằng `http://127.0.0.1:8080/health` khi gọi API lỗi.

### Ảnh/PDF scan không trích được text

Hiện chưa có OCR. Link ảnh/PDF trong kết quả chỉ là tham chiếu, không tự được tải tiếp.
Xem [thiết kế OCR đề xuất](ocr-design.md).
