# Mô hình thiết kế bằng sơ đồ

Đọc từ trên xuống: hệ thống làm gì → class nào phụ trách → một URL đi qua các bước nào → dữ liệu được ghi ở đâu. Các sơ đồ mô tả code hiện tại, đối chiếu ngày 05/09/2026. Quy tắc đầy đủ nằm trong [thiết kế chi tiết](architecture.md).

Các khối `mermaid` hiển thị thành hình trong trình xem Markdown hỗ trợ Mermaid, chẳng hạn trên GitHub. Nếu IDE chỉ hiện mã sơ đồ, mở file bằng chế độ Markdown preview có hỗ trợ Mermaid.

## 1. Toàn cảnh: đầu vào và sản phẩm cuối

```mermaid
flowchart LR
    User["Bạn chạy lệnh Java"] --> App
    Input["Một URL hoặc file danh sách N06"] --> App
    subgraph Local["Máy của bạn"]
        App["Content Collector<br/>Một chương trình Java CLI"]
        Docs["documents.jsonl<br/>Text được giữ để dùng cho tìm kiếm"]
        Results["results.jsonl<br/>Kết quả và lý do của từng URL"]
        Summary["summary.json<br/>Thống kê khi batch kết thúc"]
        App --> Docs
        App --> Results
        App --> Summary
    end
    App -->|"GET robots.txt và tài nguyên"| Web["Website HUST<br/>hust.edu.vn và subdomain"]
    Web -->|"HTTP status, HTML, PDF, Office"| App
    classDef output fill:#dcfce7,stroke:#15803d,color:#14532d;
    class Docs,Results,Summary output;
```

Bạn cung cấp URL. Collector tải và chuyển nội dung thành văn bản cùng báo cáo. Các file output nằm trên máy chạy lệnh; chức năng index/tìm kiếm sẽ là bước phát triển sau.

## 2. Bốn class phối hợp như thế nào?

```mermaid
flowchart TB
    CLI["Lệnh java -jar collector.jar"] --> C
    subgraph JVM["Cùng một JVM — gọi phương thức trực tiếp"]
        C["Collector<br/>Điều phối, retry, chống trùng, ghi file"]
        P["UrlPolicy<br/>Chuẩn hóa URL, kiểm tra domain và IP"]
        F["Fetcher<br/>Robots, HTTP, redirect, giới hạn tải"]
        E["ContentExtractor<br/>Lấy title/text, quyết định giữ hay bỏ"]
        C -->|"1. Chuẩn hóa và kiểm tra đầu vào"| P
        C -->|"2. Tải qua Downloader và fetchWithRetry"| F
        F -->|"Kiểm tra đích trước request"| P
        C -->|"3. Truyền byte, URL cuối và MIME"| E
        E --> J["Jsoup<br/>Đọc HTML"]
        E --> T["Tika<br/>Nhận dạng loại và đọc tài liệu"]
        F --> R["crawler-commons<br/>Đọc quy tắc robots.txt"]
    end
    F <-->|"HTTP"| Web["Website HUST"]
    C -->|"4. Serialize bằng Jackson"| Files["JSONL và JSON trên ổ đĩa"]
```

**Collector là đầu mối điều phối.** Fetcher trả byte về Collector; Collector mới gọi ContentExtractor. Fetcher không trực tiếp gọi extractor. `Downloader` là điểm thay thế cách tải trong test; production truyền một lambda gọi Fetcher có retry.

| Nếu muốn hiểu hoặc sửa… | Đọc class |
|---|---|
| Lệnh chạy, limit, retry, chống trùng, output | [Collector](../src/main/java/vn/edu/hust/collector/Collector.java) |
| URL nào được chấp nhận | [UrlPolicy](../src/main/java/vn/edu/hust/collector/crawl/UrlPolicy.java) |
| Cách tải và xử lý robots/redirect | [Fetcher](../src/main/java/vn/edu/hust/collector/crawl/Fetcher.java) |
| Vì sao nội dung được giữ/bỏ | [ContentExtractor](../src/main/java/vn/edu/hust/collector/extract/ContentExtractor.java) |

## 3. Một URL được giữ: ai gọi ai, theo thứ tự nào?

Sơ đồ này minh họa đường đi thành công của một URL mới, chưa trùng text. Các nhánh lỗi nằm ở sơ đồ tiếp theo.

```mermaid
sequenceDiagram
    actor U as Người chạy
    participant C as Collector
    participant P as UrlPolicy
    participant F as Fetcher
    participant W as Website HUST
    participant E as ContentExtractor
    participant O as File output
    U->>C: --url hoặc --input, limit, output
    C->>C: Đọc input và tạo thư mục output mới
    loop Từng URL trong limit, xử lý tuần tự
        C->>P: normalize, inDomain, loginUrl
        P-->>C: URL chuẩn hóa được chấp nhận
        C->>C: Kiểm tra và thêm vào seenUrls
        C->>F: Downloader → fetchWithRetry → fetch
        F->>P: Kiểm tra domain và IP đích
        opt Origin chưa có robots trong cache
            F->>W: GET /robots.txt sau kiểm tra và delay
            W-->>F: Robots 200 hoặc 404
            F->>F: Parse và cache quy tắc
        end
        F->>F: Kiểm tra robots cho phép URL
        F->>P: Kiểm tra đích trước request tài nguyên
        F->>W: GET tài nguyên sau delay
        W-->>F: HTTP 200 và body
        F-->>C: Fetched: URL cuối, status, MIME, bytes
        C->>E: extract(bytes, finalUrl, MIME)
        E-->>C: Extracted: title, text, type, keep=true
        C->>C: SHA-256 text, kiểm tra và thêm seenHashes
        C->>O: Ghi Document vào documents.jsonl và flush
        C->>O: Ghi Result KEPT vào results.jsonl và flush
    end
    C->>O: Ghi summary.json
    C-->>U: Đường dẫn output và thống kê
```

Nếu tài nguyên redirect, Fetcher kiểm tra lại đích mới và robots của đích đó trước khi tải tiếp. Nếu lỗi I/O hoặc phản hồi tài nguyên 429/5xx, Collector thử tối đa 3 lượt tổng cộng. Không phải mọi lỗi robots đều được retry; xem [thiết kế HTTP](architecture.md).

## 4. Vì sao URL thành KEPT, SKIPPED, DUPLICATE hoặc FAILED?

```mermaid
flowchart TD
    Start["Một dòng URL trong batch"] --> Valid{"Chuẩn hóa và policy đầu vào đạt?"}
    Valid -->|"Không"| Skip["SKIPPED<br/>Bỏ theo quy tắc hoặc IllegalArgumentException"]
    Valid -->|"Có"| Seen{"URL chuẩn hóa đã gặp?"}
    Seen -->|"Có"| DupURL["DUPLICATE<br/>URL_MATCH"]
    Seen -->|"Chưa"| Mark["Thêm URL vào seenUrls"]
    Mark --> Fetch["Tải: policy đích, robots, HTTP và retry"]
    Fetch -->|"Bị policy hoặc giới hạn từ chối"| Skip
    Fetch -->|"Exception khác sau xử lý retry"| Fail["FAILED<br/>Lỗi tải, HTTP hoặc bóc tách"]
    Fetch -->|"Có phản hồi cuối"| HTTP{"HTTP 200?"}
    HTTP -->|"Không"| Fail
    HTTP -->|"Có"| Extract["Bóc tách HTML hoặc tài liệu"]
    Extract -->|"IllegalArgumentException"| Skip
    Extract -->|"Exception khác"| Fail
    Extract -->|"Có kết quả"| Useful{"Nội dung đạt quy tắc giữ?"}
    Useful -->|"Không"| Skip
    Useful -->|"Có"| Hash{"SHA-256 text đã gặp?"}
    Hash -->|"Có"| DupText["DUPLICATE<br/>CONTENT_HASH_MATCH"]
    Hash -->|"Chưa"| Kept["KEPT<br/>CONTENT"]
    Kept --> Doc["Ghi documents.jsonl"]
    Doc --> Result["Ghi results.jsonl"]
    Skip --> Result
    Fail --> Result
    DupURL --> Result
    DupText --> Result
    Result --> Next["Tiếp tục URL sau; hết batch thì ghi summary"]
    classDef good fill:#dcfce7,stroke:#15803d,color:#14532d;
    classDef skip fill:#fef3c7,stroke:#b45309,color:#78350f;
    classDef bad fill:#fee2e2,stroke:#b91c1c,color:#7f1d1d;
    classDef dup fill:#dbeafe,stroke:#1d4ed8,color:#1e3a8a;
    class Kept,Doc good;
    class Skip skip;
    class Fail bad;
    class DupURL,DupText dup;
```

**Trùng URL được phát hiện trước tải; trùng text được phát hiện sau bóc tách.** URL được đánh dấu đã gặp trước khi tải nên một URL từng tải lỗi vẫn bị `URL_MATCH` khi xuất hiện lại trong cùng batch. Sơ đồ giả định ghi file thành công; lỗi filesystem có thể dừng cả batch.

## 5. Bên trong ContentExtractor

```mermaid
flowchart TD
    Bytes["Byte tải về + URL cuối + MIME HTTP"] --> Detect["Tika nhận dạng loại nội dung"]
    Detect --> IsHTML{"Loại detect hoặc MIME HTTP chứa html?"}
    IsHTML -->|"Có"| Password{"HTML có ô nhập password?"}
    Password -->|"Có"| Login["Bỏ: LOGIN_REQUIRED"]
    Password -->|"Không"| DOM["Jsoup bỏ menu, script, form...<br/>Chọn vùng main và lấy title/text"]
    DOM --> CleanHTML["Chuẩn hóa NFC và khoảng trắng"]
    CleanHTML --> Rule{"Text từ 120 ký tự,<br/>text link dưới 55%,<br/>title không báo lỗi mềm?"}
    Rule -->|"Có"| Keep["Trả keep=true cùng title/text/type"]
    Rule -->|"Không"| DropHTML["Bỏ: SHORT_OR_LINK_LIST,<br/>EMPTY_OR_JS_REQUIRED hoặc SOFT_ERROR"]
    IsHTML -->|"Không"| Supported{"Loại tài liệu trong allowlist?"}
    Supported -->|"Không"| Unsupported["Bỏ: UNSUPPORTED_TYPE"]
    Supported -->|"Có"| Parse["Tika đọc PDF, Office, ODF, RTF<br/>và chuẩn hóa text"]
    Parse --> Empty{"Text rỗng?"}
    Empty -->|"Có"| OCR["Bỏ: EMPTY_OR_NEEDS_OCR"]
    Empty -->|"Không"| Keep
```

Sơ đồ này mô tả các kết quả bình thường của extractor; exception được Collector xử lý như sơ đồ 4. HTML dùng ngưỡng độ dài/tỷ lệ link, còn tài liệu chỉ cần text không rỗng. Các nhãn “JS_REQUIRED” hoặc “NEEDS_OCR” là tín hiệu xem lại, chưa phải kết luận chắc chắn về nguồn.

## 6. Dữ liệu nào tạm thời, dữ liệu nào còn sau khi thoát?

```mermaid
flowchart LR
    subgraph RAM["RAM — chỉ trong lần chạy"]
        Inputs["List URL đầu vào"]
        Seen["seenUrls và seenHashes<br/>Chống trùng trong batch"]
        Cache["Robots cache và thời điểm request"]
        Fetched["Fetched<br/>URL cuối, status, MIME, bytes"]
        Extracted["Extracted<br/>title, text, type, keep, reason"]
        Fetched --> Extracted
    end
    Extracted -->|"Đạt quy tắc và chưa trùng text"| Document["Document"]
    Fetched -.->|"Thông tin phản hồi nếu có"| Result["Result của từng URL"]
    Extracted -.->|"Quyết định nội dung"| Result
    Inputs -.->|"URL gốc"| Result
    Seen -.->|"Quyết định trùng"| Result
    subgraph Disk["Ổ đĩa — còn sau khi chương trình thoát"]
        Docs["documents.jsonl<br/>Chỉ Document được giữ"]
        Results["results.jsonl<br/>Mọi URL đã xử lý"]
        Summary["summary.json<br/>Bộ đếm cuối batch"]
    end
    Document --> Docs
    Result --> Results
    Counts["Bộ đếm trong Collector"] -->|"Khi batch kết thúc"| Summary
```

Không lưu raw HTML/PDF. Chạy lại tạo cache/tập chống trùng mới và cần thư mục output mới. JSONL được flush từng record; summary chỉ có khi batch kết thúc bình thường. Hai file JSONL không được ghi trong một transaction.

## 7. Đối chiếu với mẫu thật trong repo

```mermaid
flowchart LR
    Input["6 URL trong demo-input.txt"] --> Batch["Collector"]
    Batch --> K["3 KEPT<br/>2 HTML + 1 PDF"]
    Batch --> S["1 SKIPPED<br/>URL ASSO đăng nhập"]
    Batch --> D["2 DUPLICATE<br/>1 trùng URL + 1 trùng text"]
    K --> Docs["documents.jsonl: 3 dòng"]
    K --> Results["results.jsonl: 6 dòng"]
    S --> Results
    D --> Results
```

Đây là [kết quả lịch sử đã commit](../output/demo-n06/results.jsonl), có 0 FAILED và 0 remaining. Chạy lại trên website thật có thể cho kết quả khác. Để đọc code theo sơ đồ: bắt đầu từ `Collector.main → run → process`, rồi mở Fetcher hoặc ContentExtractor tùy bước muốn tìm hiểu.
