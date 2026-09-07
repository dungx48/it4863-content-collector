# Context dự án và hướng dẫn chuyển máy

Cập nhật: **05/09/2026**. Tài liệu giúp bạn quay lại project mà không cần nhớ hội thoại hoặc môi trường máy cũ.

## 1. Project này đang làm gì?

Đây là bài IT4863: chương trình Java dòng lệnh nhận danh sách URL HUST, tải nội dung công khai, chọn nội dung có ích và xuất text phục vụ bài tìm kiếm sau. Hiện project dừng ở **thu thập và chuẩn bị dữ liệu**, chưa có công cụ tìm kiếm hoặc giao diện.

Luồng cần nhớ: **URL → chuẩn hóa/kiểm tra → robots và HTTP → Jsoup/Tika → giữ/bỏ → chống trùng text → JSONL**.

Chương trình xử lý tuần tự, không tự tìm thêm link. Không cần database, Docker, API key hay `.env`. Cần mạng tải dependency lần đầu và thu thập website; có thể đọc dữ liệu mẫu ngay khi chưa build.

## 2. Đọc gì để lấy lại context nhanh?

| Thứ tự | File | Bạn sẽ hiểu |
|---|---|---|
| 1 | [README](../README.md) | Phạm vi và lệnh chạy |
| 2 | [summary mẫu](../output/demo-n06/summary.json), [results mẫu](../output/demo-n06/results.jsonl) | Một lần chạy cho kết quả gì |
| 3 | [text mẫu](../output/demo-n06/document-1.txt) | Sản phẩm cuối là văn bản như thế nào |
| 4 | [Collector.java](../src/main/java/vn/edu/hust/collector/Collector.java) | Đọc main → run → process, rồi readInput/retry |
| 5 | [thiết kế chi tiết](architecture.md) | Dữ liệu, policy, HTTP, extractor và lỗi |
| 6 | [kiểm thử](validation.md) và `src/test/java/` | Hành vi có fixture kiểm chứng và khoảng trống |

## 3. Hiện trạng mã nguồn và dữ liệu

Repo tại thời điểm đọc có hai commit trước lần bổ sung tài liệu này:

- `c8910d1` — `Build focused HUST content collection CLI`.
- `df1fdab` — `Add real N06 sample inputs and collection outputs`.

Maven artifact là `vn.edu.hust:content-collector:0.1.0`. Có 4 class production và 4 class test (10 phương thức test). Source nằm dưới `src/main/java/vn/edu/hust/collector/`.

Mẫu đã commit có 6 URL, kết quả lịch sử: 3 giữ, 1 bỏ, 2 trùng, 0 lỗi, gồm HTML và PDF. Chưa có đánh giá chất lượng toàn danh sách N06. Checkout không có tài liệu yêu cầu đầy đủ để xác định toàn bộ tiêu chí chấm bài; phạm vi tài liệu này được đối chiếu từ code và README.

## 4. Những gì cần mang sang máy mới

| Thành phần | Clone Git có không? | Cách xử lý |
|---|---|---|
| Source, pom, docs, test | Có, nếu đã commit/push | Clone/pull đúng branch |
| `output/demo-input.txt`, `output/demo-n06/` | Có | Đọc và thử nhanh |
| File `N06_Ctrl+F` đầy đủ | Không có trong repo hiện tại | Chép từ máy cũ/nguồn bài trước |
| Kết quả khác trong `output/` | Mặc định bị ignore | Chuyển riêng nếu muốn giữ lịch sử |
| `target/collector.jar` | Bị ignore | Build lại |
| `.tools/`, `.cache/`, `*.log` | Bị ignore | Cài công cụ/tải cache lại khi cần |
| `archive/previous-full-stack/` | Không được theo dõi; checkout hiện không có | Chỉ khôi phục nếu còn bản lưu riêng |

Git không đồng bộ thay đổi chưa commit hoặc file bị ignore. Trước khi đổi máy, kiểm tra `git status --short`, commit/push source/docs muốn giữ và sao lưu riêng input/output quan trọng. Lần viết tài liệu này không tự commit/push.

## 5. Thiết lập và chạy

### Kiểm tra môi trường

Cài JDK 21 trở lên và Maven 3.9+ theo README hiện tại. Ưu tiên JDK 21 nếu muốn kiểm tra đúng baseline `maven.compiler.release`. Repo chưa kèm `mvnw`, nên `mvn` phải có trong PATH.

Chạy ở thư mục có `pom.xml`:

```sh
git status --short
java -version
mvn -version
mvn clean verify
java -jar target/collector.jar --help
```

Kiểm tra Java home trong `mvn -version`: Maven có thể dùng JDK khác lệnh `java`. Thiết lập JAVA_HOME/PATH nhất quán nếu build/runtime lệch nhau. Không sao chép nguyên đường dẫn JDK từ máy khác.

`mvn clean verify` chạy test rồi tạo JAR kèm dependency. Test HTTP mở server loopback cục bộ, không crawl HUST. Môi trường hạn chế socket có thể ảnh hưởng nhóm test này.

### Kiểm tra CLI không cần truy cập website

```sh
java -jar target/collector.jar --url https://asso.hust.edu.vn/adfs/ls/ --output output/check-login-01
```

URL bị policy bỏ trước HTTP. Kỳ vọng `processed=1`, `skipped=1`, bộ đếm khác bằng 0, results có LOGIN_REQUIRED, documents rỗng. Mỗi lần chạy cần tên output chưa tồn tại.

### Chạy mẫu có mạng

```sh
java -jar target/collector.jar --input output/demo-input.txt --limit 6 --output output/demo-local-01
```

Đọc summary trước, rồi results và documents trong `output/demo-local-01/`. Không dùng `--output output/demo-n06` vì thư mục mẫu đã tồn tại. Website có thể trả kết quả khác mẫu lịch sử trong Git.

### Chạy danh sách đầy đủ

Sau khi đặt file N06 vào thư mục project:

```sh
java -jar target/collector.jar --input "N06_Ctrl+F" --limit 100 --output output/n06-sample-01
```

Xem chất lượng text/lý do bỏ trên mẫu trước khi tăng lên:

```sh
java -jar target/collector.jar --input "N06_Ctrl+F" --limit 50000 --output output/n06-full-01
```

Limit tính theo dòng URL, gồm lỗi/trùng. Mỗi lệnh chạy từ đầu, không nối tiếp batch trước. `remaining > 0` nghĩa là có dòng ngoài limit; không tự chạy phần còn lại.

## 6. Hiểu output và xử lý sự cố

CLI chỉ tự tạo `documents.jsonl`, `results.jsonl`, `summary.json`. Pretty JSON và `.txt` trong demo là bản xem thuận tiện, không phải output tự động của mọi lần chạy. Mỗi dòng JSONL là một object, không đọc cả file như mảng JSON.

| Hiện tượng | Ý nghĩa/cách xử lý |
|---|---|
| Không tìm thấy mvn | Cài Maven, kiểm tra PATH; repo không có wrapper |
| Java báo release/version không hỗ trợ | Kiểm tra cả java -version và JDK Maven dùng |
| Maven không tạo được local repository | Kiểm tra quyền `.m2`; có thể dùng `mvn -Dmaven.repo.local=.cache/m2 clean verify` |
| Maven không resolve dependency | Kiểm tra DNS/mạng/proxy tới Maven Central; cache mới vẫn cần mạng |
| Không tìm thấy N06 | File đầy đủ chưa có trong Git; chuyển riêng hoặc dùng demo-input |
| Input has no HTTP(S) URL lines | Dòng phải có prefix http:// hoặc https:// chữ thường sau strip |
| Output directory already exists | Chọn tên output mới, không tạo sẵn thư mục đích |
| documents rỗng | Xem status/reason trong results; không đồng nghĩa crash |
| ROBOTS_UNAVAILABLE_HTTP_* | Không đọc được robots theo policy, ghi SKIPPED |
| SHORT_OR_LINK_LIST | Nội dung ngắn/nhiều link; xem HTML để đánh giá bỏ nhầm |
| EMPTY_OR_NEEDS_OCR | Parser không có text; kiểm tra tài liệu gốc, có thể là bản scan |
| Chạy xong nhưng có FAILED | Lỗi từng URL không nhất thiết làm exit code khác 0; đọc summary |
| Có JSONL nhưng thiếu summary | Batch chưa xong hoặc lỗi/ngắt khi ghi; kiểm tra dòng cuối |

Không có resume. Muốn chạy tiếp cần tạo danh sách URL riêng và output mới. Dedup không chia sẻ giữa các lần chạy; ghép nhiều tập documents cần xử lý trùng theo contentHash nếu muốn corpus duy nhất.

## 7. Khi tiếp tục phát triển

Đọc bảng “Điểm sửa và kiểm chứng” trong [thiết kế](architecture.md). Các việc còn thiếu đáng xem xét: đánh giá heuristic bằng mẫu N06 có nhãn, test retry/định dạng tài liệu, hỗ trợ chạy dài/resume nếu cần. Đây là gợi ý công việc tiếp theo, chưa phải tính năng có sẵn hoặc yêu cầu đã chốt.

Sau thay đổi hành vi, cập nhật đồng thời:

1. README nếu đổi cách chạy/phạm vi.
2. architecture nếu đổi luồng/schema/policy/giới hạn.
3. validation với lệnh, môi trường, kết quả đã thực sự chạy.
4. Tài liệu này nếu đổi thiết lập hoặc cách chuyển dữ liệu giữa máy.

Có thể dùng đoạn sau để mở phiên với trợ lý trên máy mới:

```text
Đây là repo IT4863 Content Collector, Java CLI thu thập URL HUST và xuất JSONL.
Hãy đọc README.md, docs/project-context.md, docs/architecture.md và
docs/validation.md, sau đó kiểm tra git status và code liên quan trước khi sửa.
Phạm vi hiện tại: xử lý URL đầu vào tuần tự, Jsoup/Tika, chưa có search/server/DB.
Không giả định archive hay danh sách N06 đầy đủ có trên máy.
Việc tôi muốn làm tiếp: [ghi nhiệm vụ cụ thể ở đây].
```
