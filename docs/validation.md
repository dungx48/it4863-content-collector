# Kiểm thử và phạm vi xác nhận

Cập nhật theo refactor một backend ngày 10/09/2026.

## Lệnh kiểm thử

```powershell
mvn verify
npm.cmd --prefix frontend/demo test
node --check frontend/demo/public/app.js
```

Windows sandbox có thể cần temp nằm trong workspace; xem [project-context.md](project-context.md).
Trên macOS/Linux dùng `npm` thay `npm.cmd`.

Lần cập nhật tài liệu ngày 12/09/2026: 7 test Node đạt, kiểm tra cú pháp `app.js` và
`search.js` đạt. Không chạy lại test Java vì chỉ thêm comment frontend và tài liệu.

## Phạm vi

| Nhóm | Số test | Hành vi |
|---|---:|---|
| UrlPolicyTest | 2 | Normalize, tracking/fragment, domain, protocol, credentials, login |
| FetcherTest | 3 | HTTP, robots disallow, redirect, Content-Length |
| ContentExtractorTest | 4 | HTML, login form, DOCX, link/ảnh và lazy-load |
| BatchServiceTest | 2 | Input N06, dedup URL/text, output, summary, không ghi đè, limit/lỗi tải |
| ApplicationTest | 4 | Spring context, Actuator health, API validation, lỗi và HTTP 429 |
| SearchServiceTest | 4 | Công thức TF-IDF, ranking, persistence, thay thế, validation và JSON collector |
| SearchApiTest | 1 | API index/search, validation, giới hạn body cố định và chunked |
| frontend/demo/*.test.mjs | 7 | Batch tiếp tục/lọc trùng/dừng, chuẩn hóa JSON, proxy/static và giới hạn body |

20 test Java và 7 test Node đã đạt. Test dùng fixture/loopback, không crawl HUST.
Frontend còn được kiểm tra cú pháp JavaScript.

Smoke test tìm kiếm trên Edge headless đã đạt: chọn mẫu, tạo chỉ mục, thứ hạng/giải thích điểm,
upload JSON collector, truy vấn không có kết quả, chỉ mục tồn tại sau khi backend khởi động lại,
điều hướng hai màn hình và bố cục mobile 390px. Hướng dẫn: [search.md](search.md).

Smoke test bản JAR đã đạt: chạy backend và demo ở hai tiến trình/port riêng,
health probe trả thành công và proxy giữ lỗi policy HTTP 400.
Sau khi dừng cả hai tiến trình, CLI vẫn chạy batch và xuất JSON/JSONL đúng.
`docker compose config --quiet` đã đạt; chưa build hoặc chạy container.

Spring Boot JAR phải chứa `Application.class`, controller/service/repository/model,
không chứa `web/`, HTML/CSS/JS frontend hoặc `DemoServer.class`.
Maven root chỉ build module backend; frontend test chạy bằng Node riêng.

## Giới hạn xác minh

- Bảng/hộp thoại mới và nút xuất JSON từng bài chưa có smoke test trình duyệt trong lần cập nhật này; các kết quả Edge kể trên thuộc phiên bản trước.
- Chưa có test trình duyệt tự động cho mọi thao tác import/copy/render.
- Retry/backoff chưa có fixture kiểm tra thời gian đầy đủ.
- Chưa kiểm tra mọi loại tài liệu Tika, trạng thái robots và DNS thay đổi.
- Compose/Docker cần xác minh riêng theo daemon và image có trên máy;
  cú pháp Compose hợp lệ không đồng nghĩa image đã build/chạy thành công.
- Chưa triển khai auth, database, durable queue, OCR hoặc resume batch.

Dữ liệu `output/demo-n06/` là mẫu lịch sử đã có, không phải kết quả crawl mới của lần refactor.

## Kiểm tra thủ công giao diện kết quả mới

1. Bóc tách một URL: bảng có một dòng, không tự mở hộp thoại.
2. Chạy lô có bài thành công/lỗi/trùng: bộ đếm đúng, chỉ xuất bài thành công không trùng.
3. Mở hai bài khác nhau lần lượt: nội dung, copy text và JSON riêng phải theo đúng bài đang xem.
4. Đóng bằng nút Đóng/Escape, kiểm tra focus trở về nút mở chi tiết.
5. Tải/copy cả lô và từng bài rồi nạp lại ở TF-IDF để xác nhận số văn bản.
6. Dừng giữa lô: giữ bài đã hoàn tất, không chạy URL tiếp theo.
7. Kiểm tra màn hình hẹp, bảng cuộn ngang, hộp thoại cuộn dọc và thao tác bàn phím.
8. Dán JSON sai: báo lỗi khi tạo chỉ mục, không thay chỉ mục đang có.

Đây là checklist cần chạy, không phải tuyên bố các bước đã đạt.
Tiêu chí OCR tương lai nằm trong [ocr-design.md](ocr-design.md).
