# Frontend demo

Client HTML/CSS/JS độc lập để trình diễn collector API. Không thuộc backend JAR,
không tải hoặc phân tích tài nguyên nguồn. Trình duyệt điều phối batch và loại text trùng trong lượt chạy; Node chỉ phục vụ file tĩnh và proxy API cùng origin.
Không cần `npm install`, không có dependency npm.

Để chạy cả backend và frontend từ repository root: `.\start.cmd` (cần build backend trước).
Nhấn `Ctrl+C` để dừng cả hai. Có thể dùng `node scripts/dev.mjs` trên các hệ điều hành khác.

Từ repository root, chạy backend trong terminal thứ nhất:

```powershell
java -jar backend/target/collector.jar --serve 8080
```

Terminal thứ hai:

```powershell
node frontend/demo/server.mjs
```

Mở http://localhost:3000. Chế độ URL mẫu tự động: http://localhost:3000/?demo=1.

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `BACKEND_URL` | `http://127.0.0.1:8080` | Origin backend, không kèm path |
| `DEMO_HOST` | `127.0.0.1` | Địa chỉ lắng nghe demo |
| `DEMO_PORT` | `3000` | Port frontend |

Browser gọi `/api/v1/*` cùng origin; proxy chuyển sang `BACKEND_URL`, không cần mở CORS.
Frontend vẫn mở được khi backend dừng; thao tác API sẽ báo lỗi kết nối.
File TXT được đọc trong trình duyệt, không upload. Người dùng có thể chọn tối đa 10 URL để bóc tách tuần tự.

Test trên Windows: `npm.cmd --prefix frontend/demo test` từ repository root.
Trên macOS/Linux dùng `npm --prefix frontend/demo test`.

## Đọc code

| File | Trách nhiệm |
|---|---|
| `public/app.js` | Import TXT, chọn URL, gọi API, bảng kết quả, hộp thoại và xuất JSON |
| `public/batch.mjs` | Chạy tuần tự, loại text trùng, tiếp tục khi lỗi và dừng |
| `public/search.js` | Chọn nguồn JSON, tạo chỉ mục, truy vấn và hiển thị điểm |
| `public/corpus.mjs` | Kiểm tra JSON đầu vào và đổi object thành mảng |
| `public/styles.css` | Giao diện chung, điều hướng, bảng và hộp thoại |
| `public/search.css` | Bố cục riêng của tìm kiếm |
| `server.mjs` | File tĩnh và proxy API |

`latestResults` là tập xuất chung; `activeResult` là bài đang xem. Xuất chi tiết dùng
`[activeResult]`, không thay đổi tập xuất chung. Nội dung lấy từ API được gán bằng
`textContent`, không chèn thành HTML. Hộp thoại dùng `<dialog>` của trình duyệt,
đóng bằng nút Đóng hoặc Escape.

Sửa HTML/CSS/JS: tải lại trình duyệt, dùng Ctrl+F5 nếu còn cache.
Sửa `server.mjs`: khởi động lại Node. Sửa Java: dừng backend, build rồi chạy lại.

Hướng dẫn thao tác: [thu thập](../../docs/demo.md), [tìm kiếm và chọn mô hình](../../docs/search.md).
Thiết kế tương lai: [OCR](../../docs/ocr-design.md), chưa có API hoặc nút OCR.
