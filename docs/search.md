# Trình diễn tìm kiếm Lucene theo TF-IDF

Một backend Spring Boot chứa cả collector và search. Frontend demo dùng chung một Node server:
`/` thu thập và `/search` tạo chỉ mục/tìm kiếm. Hai màn hình có thanh điều hướng chung.

## Chạy và trình diễn

Từ thư mục gốc (dừng bản đang chạy trước khi build):

```powershell
mvn verify
.\start.cmd
```

Mở http://localhost:3000/search:

1. Bấm **Dùng dữ liệu mẫu** để chọn 6 văn bản tự soạn, rồi **Tạo chỉ mục**.
2. Tìm `lucene` hoặc `học bổng`. Kết quả xếp theo điểm TF-IDF giảm dần.
3. Mở **Giải thích điểm TF-IDF** ở hai kết quả để so sánh TF, IDF và fieldNorm.
4. Thử truy vấn không có trong dữ liệu; thử đổi Top 5/10/20/50.
5. Chọn `output/demo-n06/documents.pretty.json` rồi tạo lại chỉ mục để trình diễn với 3 văn bản collector có sẵn. Đây là dữ liệu lịch sử, không phải crawl mới.
6. Tắt/bật backend: chỉ mục vẫn còn. Chọn tập JSON khác và tạo lại sẽ thay thế cả tập trước.

Tại màn hình thu thập, sau khi bóc tách có thể bấm **Tải JSON để tìm kiếm**.
File `documents.json` chứa các bài thành công không trùng của lượt vừa chạy.
Trong chi tiết, **Tải JSON bài này** xuất `document.json`, mảng một bài.
Cả hai nạp được ngay ở màn hình tìm kiếm; có thể dùng nút sao chép rồi dán vào ô JSON.

### Dán JSON hoặc chọn file

1. Chọn file JSON, dùng bộ mẫu hoặc dán nội dung vào ô JSON.
2. Khi đổi nguồn, lựa chọn trước đó bị bỏ; chưa thay đổi chỉ mục hiện tại.
3. Bấm **Tạo chỉ mục**. JSON dán được kiểm tra ở bước này, không parse mỗi lần gõ.
4. JSON sai cú pháp, thiếu `text` hoặc vượt giới hạn sẽ báo lỗi và không gửi tạo chỉ mục.
5. Sau khi tạo thành công, nhập truy vấn rồi bấm **Tìm kiếm**.

Frontend nhận một object `{ "text": "Nội dung" }` hoặc mảng object và chuyển thành
mảng trước khi gọi API. File được kiểm tra ngay khi đọc; nội dung dán kiểm tra khi
bấm tạo chỉ mục. Không tự sửa JSON sai. Tạo chỉ mục mới thay thế toàn bộ tập cũ,
không tự nối thêm văn bản.

## Đầu vào và đầu ra

API nhận JSON UTF-8 là mảng 1–10.000 object, tổng request tối đa 20 MiB:

```json
[
  {"id":"1","title":"Học bổng","text":"Thông tin học bổng dành cho sinh viên.","url":"https://hust.edu.vn/"},
  {"id":"2","title":"Thực tập","text":"Sinh viên đăng ký thực tập doanh nghiệp."}
]
```

- `text` bắt buộc, chuỗi không rỗng, tối đa 2.000.000 ký tự.
- `title` tùy chọn, tối đa 2.000 ký tự; `url` tùy chọn, tối đa 8.192 ký tự.
- `id` tùy chọn, chuỗi tối đa 200 ký tự; thiếu thì dùng `doc-1`, `doc-2`… theo vị trí. ID cuối cùng phải duy nhất.
- Các trường collector khác như `links`, `contentHash`, `fetchedAt` được bỏ qua khi lập chỉ mục.
- Không nhận JSONL, object bao ngoài kiểu `{ "documents": [...] }` hoặc văn bản không có token. Frontend có thể bọc một object văn bản thành mảng; gọi API trực tiếp phải gửi mảng.
- Toàn bộ dữ liệu được kiểm tra trước khi ghi. Ghi bằng transaction Lucene và rollback khi lỗi trước commit, không công bố một phần chỉ mục.

Đầu ra chỉ mục là các file Lucene trong `data/lucene-index`, tính từ working directory của backend.
Có thể cấu hình `SEARCH_INDEX_PATH` bằng đường dẫn tuyệt đối để VS Code và IntelliJ dùng cùng chỉ mục.
Docker Compose dùng volume `collector-data` gắn vào `/app/data`; chỉ mục tồn tại qua lần tạo lại container.
Chạy một backend ghi vào mỗi thư mục chỉ mục.

## API

| Method / route | Đầu vào | Đầu ra |
|---|---|---|
| GET /api/v1/index | Không có | ready, documentCount, indexedAt, similarity |
| POST /api/v1/index | Body là mảng JSON; Content-Type: application/json | Trạng thái chỉ mục sau thay thế |
| GET /api/v1/search?q=lucene&limit=10 | q: 1–500 ký tự; limit: 1–50 | query, tokens, total, limit, similarity, results |

Mỗi kết quả có `id`, `title`, `url`, `text`, `score`, `explanation`.
`total` là số văn bản khớp chính xác; `results` chỉ chứa tối đa `limit` mục.
Chưa có chỉ mục / JSON không hợp lệ / truy vấn rỗng: HTTP 400. Quá 20 MiB: 413.
Sai Content-Type: 415. Lỗi đọc/ghi chỉ mục: 500.
Tìm không có kết quả: 200 với `total: 0`, `results: []`.

Ví dụ PowerShell, chạy từ root:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/v1/index -ContentType application/json -InFile frontend/demo/public/sample-documents.json
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/search?q=lucene&limit=10'
```

## Phương pháp và phạm vi

Apache Lucene 10.3.1; `ClassicSimilarity` được đặt trên cả IndexWriter và IndexSearcher.
Tiêu đề và nội dung ghép vào một trường `content`, không boost riêng tiêu đề.
Unicode NFC + StandardAnalyzer: tách token, lowercase, giữ dấu, danh sách stopword rỗng.
Chưa có bộ tách từ tiếng Việt nhiều âm tiết, stemming, bỏ dấu hoặc truy vấn ngữ nghĩa.
Truy vấn được phân tích bằng cùng analyzer, loại token trùng, tạo TermQuery OR.
Các ký hiệu cú pháp Lucene không được diễn giải thành toán tử hoặc wildcard.

Theo [ClassicSimilarity](https://lucene.apache.org/core/10_3_1/core/org/apache/lucene/search/similarities/ClassicSimilarity.html):
`tf = sqrt(freq)`, `idf = 1 + ln((N + 1)/(df + 1))`, `lengthNorm = 1/sqrt(length)`.
Với truy vấn TermQuery ở đây, Lucene cộng đóng góp `tf × idf × fieldNorm` của các token khớp.
Norm được Lucene mã hóa nên có lượng tử hóa độ dài. `IndexSearcher.explain` cung cấp phép tính thực tế.
Đây là biến thể TF-IDF của Lucene, không phải điểm cosine tự tính hay phần trăm liên quan.
Xem thêm [StandardAnalyzer](https://lucene.apache.org/core/10_3_1/core/org/apache/lucene/analysis/standard/StandardAnalyzer.html).

Các request truy cập chỉ mục được tuần tự hóa trong repository. Đủ cho demo một người dùng;
khi tạo chỉ mục lớn, truy vấn đợi hoàn tất. Chưa có job nền, phân trang sâu hoặc nhiều corpus độc lập.

## Điểm bắt đầu để đọc/debug

- `controller/SearchController.java`: API và giới hạn body.
- `service/SearchService.java`: kiểm tra JSON, truy vấn và điều phối.
- `repository/LuceneIndexRepository.java`: IndexWriter, FSDirectory, IndexSearcher, ClassicSimilarity.
- `model/SearchDocument.java`, `dto/IndexStatus.java`, `dto/SearchResponse.java`: hợp đồng dữ liệu.
- `frontend/demo/public/search.html`, `search.js`, `search.css`: giao diện demo.

Test: `SearchServiceTest` kiểm tra công thức điểm, ranking, persistence, thay thế, dữ liệu lỗi và collector JSON.
`SearchApiTest` kiểm tra API, body cố định/chunked quá lớn, validation.
Frontend proxy test kiểm tra các route mới và giới hạn upload riêng.
