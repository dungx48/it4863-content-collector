# Frontend demo

Client HTML/CSS/JS độc lập để trình diễn collector API. Không thuộc backend JAR,
không thực hiện download/parser/dedup. Node chỉ phục vụ file tĩnh và proxy API cùng origin.
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
File TXT import được đọc trong trình duyệt, không upload.

Test: `node --test frontend/demo/server.test.mjs` từ repository root.
