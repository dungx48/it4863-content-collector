# Ghi chú yêu cầu crawler từ giảng viên

Ngày ghi nhận: 12/09/2026.

Trạng thái: ghi nhận để bố trí triển khai sau; chưa thay đổi code.
Hiện trạng bên dưới tham chiếu [tài liệu kiến trúc](architecture.md), chưa phải kết quả rà soát lại code trong lần ghi chú này.

## 1. Lọc theo giới hạn truy cập

Ý giảng viên: tuân thủ robots.txt, chỉ thu thập nội dung được cho phép.

- Kiểm tra robots.txt cho User-Agent của crawler và kiểm tra lại đích redirect.
- Kết hợp phạm vi domain được phép; bỏ qua nội dung yêu cầu đăng nhập hoặc bị từ chối truy cập.
- Ghi rõ lý do bỏ qua để giải thích được kết quả thu thập.
- Khi triển khai, xác định rõ cách xử lý robots không tồn tại, lỗi mạng, lỗi máy chủ và thời hạn cache.

Theo tài liệu hiện tại: đã có robots, giới hạn domain, kiểm tra redirect và một số dấu hiệu đăng nhập. Cần rà soát mức đáp ứng trước khi bổ sung.

## 2. Kiểm soát tần suất truy cập

Ý giảng viên: không gửi quá nhiều yêu cầu, tránh ảnh hưởng website nguồn.

- Giới hạn tốc độ và số request đồng thời theo host, áp dụng cả request robots, retry và redirect.
- Chờ lâu hơn khi nguồn báo quá tải; xem xét Retry-After và tăng thời gian chờ giữa các lần thử.
- Đặt trần số lần thử và ngân sách thu thập để tránh chạy vô hạn.

Theo tài liệu hiện tại: CLI có khoảng cách request cùng host ít nhất một giây và retry giới hạn; chưa đọc Retry-After. Cần kiểm tra tính nhất quán giữa API và CLI.

## 3. Phát hiện bẫy thu thập và hành vi gian lận

Ý giảng viên: có khả năng phát hiện bẫy thu thập và hành vi gian lận.

Phạm vi đề xuất để trao đổi lại với giảng viên:

- Bẫy URL: lịch vô hạn, phân trang vô hạn, tham số tạo vô số URL, vòng lặp redirect.
- Nội dung bất thường: nhiều URL trả cùng nội dung, trang lỗi giả HTTP 200, trang đăng nhập/CAPTCHA thay cho bài viết.
- Giới hạn phòng vệ: số URL, thời gian chạy, kích thước tải, số redirect; thêm giới hạn độ sâu nếu triển khai crawl theo liên kết.
- Ghi nhận dấu hiệu và lý do dừng/bỏ qua; heuristic có thể nhận nhầm, không khẳng định phát hiện mọi hành vi gian lận.

Theo tài liệu hiện tại: có dedup URL/nội dung, giới hạn redirect/kích thước và lọc một số trang lỗi. Chưa crawl đệ quy nên bẫy sinh liên kết vô hạn là phần cần ưu tiên khi mở rộng sang crawl theo liên kết.

Điểm cần làm rõ sau: “gian lận” là website đánh lừa crawler, nội dung spam/giả mạo hay một yêu cầu khác? Chưa coi việc xác minh tính đúng sai của nội dung là yêu cầu đã chốt.

## 4. Khôi phục và tiếp tục sau gián đoạn

Ý giảng viên: tiếp tục tiến trình thu thập sau khi bị gián đoạn.

- Lưu bền vững danh sách đầu vào, URL đã hoàn tất/còn chờ, kết quả, trạng thái thử lại và dữ liệu chống trùng cần thiết.
- Khi khởi động lại, nhận diện lần chạy và tiếp tục phần chưa hoàn tất; không bỏ sót hoặc ghi trùng kết quả đã lưu.
- Phân biệt URL đang xử lý lúc bị ngắt với URL đã hoàn tất; có thể tải lại URL chưa hoàn tất.
- Xử lý dòng ghi dở và trường hợp document đã ghi nhưng result chưa ghi; tổng kết sau resume phải khớp dữ liệu thực tế.
- Ưu tiên cơ chế lưu trạng thái đơn giản phù hợp batch CLI hiện có; chưa cần quyết định thêm database hoặc broker.

Theo tài liệu hiện tại: JSONL được flush từng record nhưng chưa có checkpoint/resume; hai file document/result không được ghi như một giao dịch. Có output còn lại sau gián đoạn chưa đồng nghĩa với tiếp tục được tiến trình.

## Thứ tự và tiêu chí kiểm chứng đề xuất

1. Rà soát và hoàn thiện robots + kiểm soát tần suất: URL bị cấm không được tải, request tuân thủ khoảng chờ đã cấu hình.
2. Bổ sung resume: ngắt batch giữa chừng rồi chạy tiếp, kiểm tra không mất hoặc ghi trùng kết quả; có tình huống ngắt giữa các lần ghi file.
3. Bổ sung phát hiện bẫy theo phạm vi đã chốt: dùng dữ liệu giả lập redirect vòng, URL biến thể và nội dung trùng; kiểm tra dừng đúng giới hạn và có lý do rõ ràng.

Khi làm sau, dùng server/fixture cục bộ để demo các tình huống lỗi, tránh tạo tải thử nghiệm lên website thật.
