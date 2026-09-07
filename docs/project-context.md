# Context dự án và hướng dẫn chuyển máy

Cập nhật: **08/09/2026**.

## Mục tiêu hiện tại

Đây là bài IT4863 về phát triển mã nguồn hỗ trợ thu thập dữ liệu. Chương trình nhận URL HUST công khai và trả về:

- Tiêu đề.
- Nội dung văn bản đã làm sạch.
- Liên kết đi ra với URL đầy đủ và mô tả.
- URL ảnh với mô tả từ `alt`, `figcaption` hoặc `title`.

Luồng chính:

```text
URL → UrlPolicy → robots.txt/HTTP → Jsoup hoặc Tika → lọc nội dung
    → chống trùng text → JSON/JSONL
```

Project có hai cách chạy:

- Web local để trình diễn một URL.
- CLI để xử lý một URL hoặc batch từ file TXT.

Project chưa có Lucene, tìm kiếm, database, Docker, JavaScript renderer, OCR hoặc crawl đệ quy.

## Thành phần cần biết

| Thành phần | Vai trò |
|---|---|
| `Collector` | CLI, batch, retry, dedup và ghi output |
| `DemoServer` | Web local, API bóc tách và API gợi ý whitelist |
| `UrlPolicy` | Chuẩn hóa URL, kiểm tra domain/IP và nhận diện login |
| `Fetcher` | robots.txt, HTTP, redirect, delay và giới hạn byte |
| `ContentExtractor` | Bóc tách HTML/tài liệu và quyết định giữ/bỏ |
| `N06_Ctrl+F.txt` | 9.516 URL duy nhất dùng cho gợi ý |
| `web/` | HTML, CSS và JavaScript của giao diện |

Đọc theo thứ tự: [README](../README.md) → [demo](demo.md) → [sơ đồ](design-diagrams.md) → [thiết kế](architecture.md) → [kiểm thử](validation.md).

## Thiết lập trên máy mới

Yêu cầu JDK 21+ và Maven 3.9+. Repo không có Maven Wrapper.

```powershell
git clone <URL-repository>
cd it4863-content-collector
java -version
mvn -version
mvn clean verify
java -jar target/collector.jar --help
```

Maven tạo JAR kèm dependency tại `target/collector.jar`. Nếu muốn dùng cache nằm trong workspace:

```powershell
mvn -Dmaven.repo.local=.cache/m2 clean verify
```

## Chạy web demo

```powershell
java -jar target/collector.jar --serve 8080
```

Mở http://localhost:8080. Ô URL hỗ trợ:

- Dán URL trực tiếp.
- Gợi ý từ whitelist đóng gói trong JAR.
- Import TXT tối đa 10 MiB, lọc URL HUST và chọn từng URL.

Server chỉ bind loopback. Dừng bằng `Ctrl+C`. Sau khi sửa Java hoặc resource web, cần build và khởi động lại server để thấy bản mới.

## Chạy CLI

Một URL:

```powershell
java -jar target/collector.jar --url https://soict.hust.edu.vn/bai-viet.html --output output/single-01
```

Batch từ file tùy chọn:

```powershell
java -jar target/collector.jar --input input.txt --limit 100 --output output/batch-01
```

Batch từ whitelist trong source:

```powershell
java -jar target/collector.jar --input "src/main/resources/N06_Ctrl+F.txt" --limit 100 --output output/n06-01
```

Mỗi lần chạy cần một thư mục output chưa tồn tại. Batch không resume và không dùng dữ liệu dedup của lần chạy trước.

## File cần đồng bộ

| Thành phần | Trạng thái Git | Ghi chú |
|---|---|---|
| `src/`, `pom.xml`, `docs/`, `README.md` | Cần commit | Mã nguồn, whitelist và tài liệu |
| `output/demo-input.txt`, `output/demo-n06/` | Được theo dõi | Dữ liệu minh họa lịch sử |
| Các thư mục khác trong `output/` | Bị ignore | Sao lưu riêng nếu cần |
| `target/` | Bị ignore | Build lại trên máy mới |
| `.tools/`, `.cache/`, `.idea/` | Bị ignore | Không cần chuyển |

Trước khi đổi máy:

```powershell
git status --short
git add <các-file-cần-lưu>
git commit -m "..."
git push
```

Git chỉ đồng bộ nội dung đã commit và push.

## Xử lý lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| Không tìm thấy `mvn` | Cài Maven và kiểm tra `PATH` |
| Java không hỗ trợ release 21 | Kiểm tra JDK mà `mvn -version` đang dùng |
| Không tải được dependency | Kiểm tra mạng/proxy tới Maven Central |
| `collector.jar` không xóa được khi build | Dừng server Java cũ bằng `Ctrl+C` rồi build lại |
| `Input has no HTTP(S) URL lines` | File phải là UTF-8 và có URL bắt đầu bằng `http://` hoặc `https://` |
| `Output directory already exists` | Chọn tên output mới |
| `LOGIN_REQUIRED` | URL thuộc login/SSO và bị bỏ trước khi tải nội dung |
| `ROBOTS_DISALLOWED` | robots.txt không cho bot truy cập URL |
| `SHORT_OR_LINK_LIST` | Text quá ngắn hoặc tỷ lệ anchor text quá cao |
| `EMPTY_OR_JS_REQUIRED` | HTML tĩnh không có nội dung đủ dùng |
| `EMPTY_OR_NEEDS_OCR` | Tài liệu không có text trích được, có thể là bản scan |

## Khi phát triển tiếp

Sau khi đổi hành vi:

1. Cập nhật test phù hợp với thay đổi.
2. Chạy `mvn clean verify` và `node --check src/main/resources/web/app.js`.
3. Cập nhật README nếu cách chạy thay đổi.
4. Cập nhật architecture nếu thay đổi policy, schema hoặc luồng.
5. Cập nhật validation bằng kết quả thực tế vừa chạy.

Các hướng mở rộng chỉ nên làm sau khi đề bài yêu cầu: đánh giá chất lượng trên mẫu N06, thêm renderer JavaScript/OCR, resume batch, rồi mới xây dựng index và tìm kiếm.
