# Trình diễn tìm kiếm Lucene với nhiều mô hình

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
| GET /api/v1/search?q=lucene&limit=10 | q: 1–500 ký tự; limit: 1–50 | query, tokens, total, limit, similarity, options, results |

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

## Phương pháp mặc định (Classic TF-IDF)

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


## Chọn mô hình và tham số trên màn hình

Trong “Cấu hình tìm kiếm”, chọn mô hình, điều kiện OR/AND và Top K. Chỉ tham số
của mô hình đang chọn được hiển thị. Không cần tạo lại chỉ mục khi đổi mô hình.
API cũ không truyền option vẫn dùng Classic TF-IDF + OR.

| Query parameter | Mặc định | Giá trị |
|---|---|---|
| `model` | `classic` | `classic`, `cosine`, `bm25`, `lm` |
| `operator` | `or` | `or`: ít nhất một token; `and`: tất cả token |
| `k1` | `1.2` | Số hữu hạn >= 0; bão hòa TF của BM25 |
| `b` | `0.75` | Số hữu hạn trong [0,1]; chuẩn hóa độ dài BM25 |
| `lambda` | `0.5` | Số hữu hạn trong (0,1); trọng số mô hình văn bản LM |

Ví dụ: `/api/v1/search?q=học%20bổng&model=bm25&operator=and&k1=1&b=0.8&limit=5`.
Response thêm `options` chứa cấu hình đã dùng. `similarity` ghi mô hình cùng tham số;
`explanation` dùng đúng mô hình đang chọn. Option sai trả HTTP 400; không chấp nhận
NaN/Infinity. Giao diện giới hạn λ từ 0.01 đến 0.99 để tránh hai đầu suy biến.

- `classic`: ClassicSimilarity như trước; token truy vấn lặp chỉ tính một lần.
- `cosine`: SMART **lnc.ltc**, log tự nhiên. Văn bản: `wd = 1 + ln(tf_d)`, không IDF.
  Truy vấn: `wq = (1 + ln(tf_q)) * ln(N/df)`. Điểm là tích vô hướng chia tích chuẩn
  L2. Chuẩn văn bản tính trên **toàn bộ từ** trong trường content (title + text).
  Từ ngoài từ vựng corpus bị bỏ qua. Nếu chuẩn truy vấn bằng 0 (ví dụ mọi từ đều
  có df=N), quy ước score=0, giữ các tài liệu khớp và giải thích rõ. Xếp hạng hòa
  theo docID Lucene tăng dần. Đọc TF qua postings nên dùng được chỉ mục cũ.
- `bm25`: BM25Similarity của Lucene; IDF = `ln(1 + (N-df+0.5)/(df+0.5))`.
  Dùng explain để đối chiếu phép tính thật; không mặc định điểm số giống mọi
  biến thể BM25 trong bài giảng. `k1=0` bỏ ảnh hưởng tần suất lặp; `b=0` bỏ hiệu
  chỉnh độ dài, `b=1` hiệu chỉnh đầy đủ.
- `lm`: LMJelinekMercerSimilarity của Lucene. Theo quy ước slide,
  `P(t|d) = lambda * P(t|Md) + (1-lambda) * P(t|Mc)`.
  Lucene nhận **1-lambda** làm trọng số nền. Lucene dùng điểm log và ước lượng
  xác suất corpus có làm mịn, không trả xác suất thô. Điểm chỉ tích lũy trên
  các token khớp; OR/AND giới hạn tập ứng viên, không chấm mọi tài liệu trong corpus.

VSM giữ tần suất từ trong truy vấn; các mô hình Lucene dùng mỗi token một lần,
không áp dụng trọng số tần suất truy vấn k3. Analyzer vẫn là StandardAnalyzer,
giữ dấu, không tách từ tiếng Việt nhiều âm tiết. Chưa có so sánh hai bảng, hợp nhất
kết quả hoặc chế độ không dấu.

Cosine hiện quét postings và tính chuẩn vector mỗi truy vấn, lấy toàn bộ ứng viên
trước khi chọn Top K. Phù hợp demo corpus nhỏ; cần cache chuẩn vector theo commit
và cải tiến thu thập Top K khi corpus lớn. Không chỉ lấy Top K Classic để xếp lại.

Tham khảo chính thức:
[BM25Similarity](https://lucene.apache.org/core/10_3_1/core/org/apache/lucene/search/similarities/BM25Similarity.html),
[LMJelinekMercerSimilarity](https://lucene.apache.org/core/10_3_1/core/org/apache/lucene/search/similarities/LMJelinekMercerSimilarity.html).
