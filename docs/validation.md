# Kiểm thử và phạm vi xác nhận

Cập nhật: **08/09/2026**.

## Kết quả gần nhất

Môi trường: Windows, JDK 25 chạy mã biên dịch với `release 21`, Maven 3.9.9.

```powershell
node --check src/main/resources/web/app.js
mvn -Dmaven.repo.local=.cache/m2 -B -ntp clean verify
```

Kết quả:

- JavaScript không có lỗi cú pháp.
- Maven build thành công và tạo `target/collector.jar`.
- **13 test đạt, 0 thất bại, 0 lỗi, 0 bỏ qua.**
- JAR chứa `DemoServer.class`, `web/app.js` và `N06_Ctrl+F.txt`.

## Các nhóm test

| Nhóm | Số test | Hành vi được kiểm tra |
|---|---:|---|
| `UrlPolicyTest` | 2 | Chuẩn hóa URL, bỏ tracking/fragment, domain boundary, protocol, credentials và login ASSO |
| `FetcherTest` | 3 | HTTP thành công, robots disallow, redirect ngoài origin và giới hạn Content-Length |
| `ContentExtractorTest` | 4 | HTML bỏ navigation, login form, DOCX, link/ảnh tuyệt đối, mô tả và lazy-load |
| `CollectorTest` | 2 | Import N06, dedup URL/text, JSON/JSONL, summary, không ghi đè, limit và lỗi downloader |
| `DemoServerTest` | 2 | Web tĩnh, API whitelist, API extract, link ảnh, URL ngoài HUST và login |

Test HTTP dùng server loopback và fixture, không crawl HUST. `CollectorTest` tiêm downloader giả để kết quả ổn định.

## Kiểm tra dữ liệu thật

Whitelist lấy từ `bangoc/hust.edu.vn-2026`, đóng gói tại `src/main/resources/N06_Ctrl+F.txt`:

- 9.523 dòng.
- 9.516 URL duy nhất.
- 0 URL ngoài `hust.edu.vn` hoặc subdomain.

Đã chạy JAR với bài viết UNESCO trên SoICT. File local `output/demo-image-links/documents.json` chứa:

- 1 tài liệu được giữ.
- 6 URL đi ra.
- 5 URL ảnh.

Thư mục này bị `.gitignore` bỏ qua; output lịch sử đã commit nằm tại `output/demo-n06/` và phản ánh phiên bản cũ hơn.

## Điều chưa được test đầy đủ

- CLI parser chưa có test riêng cho mọi tổ hợp option sai.
- Retry/backoff chưa có fixture xác nhận thời gian và đủ ba lần thử.
- Chưa kiểm tra mọi trạng thái robots.txt, redirect và DNS thay đổi.
- Chưa có mẫu thực tế cho XLSX, PPTX, ODF và RTF.
- Chưa kiểm tra OCR, vì project chưa cấu hình OCR.
- Chưa đánh giá precision/recall của heuristic trên toàn whitelist.
- Frontend mới kiểm tra cú pháp và API; chưa có bộ test trình duyệt tự động.
- Chưa chạy toàn bộ 9.516 URL, vì việc này tốn thời gian và phụ thuộc trạng thái website.

## Cách kiểm tra trước khi nộp

```powershell
mvn clean verify
java -jar target/collector.jar --serve 8080
```

Trên giao diện, kiểm tra lần lượt:

1. Gõ `soict` và chọn một gợi ý whitelist.
2. Import một TXT có URL hợp lệ, URL trùng và URL ngoài HUST.
3. Chọn URL đã import rồi bấm **Bóc tách**.
4. Kiểm tra tiêu đề, văn bản, liên kết và URL ảnh.
5. Sao chép JSON và kiểm tra các trường `title`, `text`, `links`.

Dừng server bằng `Ctrl+C` sau khi kiểm tra.
