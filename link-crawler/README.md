# link-crawler — crawl hust.edu.vn ra danh sách link

Crawler Scrapy đi đệ quy trong miền `hust.edu.vn` (kể cả subdomain) và xuất ra
**một file txt chỉ gồm link** (`output/links.txt`, mỗi dòng một URL, đã chuẩn hoá
và khử trùng). Không bóc nội dung, không lưu DB — file link này dùng làm đầu vào
cho bước thu thập/bóc tách của project.

## Cài đặt

```bash
cd link-crawler
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Chạy

```bash
# mặc định: seed = sitemap + trang chủ + Giới thiệu + Tin tức
scrapy crawl hust -a max_pages=2500 -s DEPTH_LIMIT=4

# chỉ crawl tab Giới thiệu, không dùng sitemap
scrapy crawl hust -a use_sitemap=0 -a start=https://hust.edu.vn/vi/about/ -s DEPTH_LIMIT=2

# seed từ file, chỉ kiểm tra đúng các URL đó (không đi theo link)
scrapy crawl hust -a urlfile=seeds.txt -a follow=0
```

| Tham số | Ý nghĩa |
|---|---|
| `-a max_pages=N` | Dừng sau N trang HTML (0 = không giới hạn) |
| `-a use_sitemap=0/1` | Có lấy seed từ `sitemap.xml` không |
| `-a start=url1,url2` | Seed tuỳ chọn (thay cho seed mặc định) |
| `-a urlfile=path` | Seed từ file txt |
| `-a follow=0/1` | Có đi theo link tìm được không |
| `-s DEPTH_LIMIT=N` | Độ sâu tối đa |
| `HUST_LINKS_OUT=path` | Đổi file kết quả (mặc định `output/links.txt`) |

## Link nào được ghi

- Trang HTML tải thành công (HTTP 200).
- Link tài liệu (`.pdf`, `.doc(x)`, `.xls(x)`, `.ppt(x)`, …) tìm thấy trong trang —
  chỉ ghi link, không tải file.

Bị loại: link ngoài `hust.edu.vn`, ảnh/CSS/JS, các nhánh quản trị/tìm kiếm/RSS/tag,
EZproxy thư viện (`0-*`), host test (xem `config.py`).

## Crawl lịch sự

Tôn trọng `robots.txt`, tối đa 4 request song song mỗi domain, delay 0,5s +
AutoThrottle, tự giãn nhịp khi gặp 429/503 (`crawler/middlewares.py`). Có HTTP
cache 1 ngày ở `.cache/httpcache` để chạy lại không tải lại.
