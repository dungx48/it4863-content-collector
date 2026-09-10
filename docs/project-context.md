# Context dự án và hướng dẫn chuyển máy

Cập nhật: 10/09/2026.

Một backend Spring Boot duy nhất chứa controller/service/repository/model/dto/config,
URL policy, HTTP downloader và parser. Frontend demo riêng, không nằm trong JAR.
Không thêm database hoặc broker trong lần refactor này.

## Build và chạy

Yêu cầu JDK 21+, Maven 3.9+. Node.js 22+ chỉ cần khi chạy frontend demo.

```powershell
mvn clean verify
java -jar backend/target/collector.jar --serve 8080
```

Terminal khác:

```powershell
node frontend/demo/server.mjs
```

Giao diện: http://localhost:3000. API: http://127.0.0.1:8080/health.
Có thể build backend riêng: `mvn -f backend/pom.xml clean verify`.
Frontend không cần npm install. Test: `node --test frontend/demo/server.test.mjs`.

Để debug backend, mở root Maven project trong IntelliJ, reload Maven và debug
`vn.edu.hust.collector.Application`. Chạy frontend riêng bằng lệnh Node ở terminal VS Code.

Maven cục bộ của workspace, nếu chưa có mvn trong PATH:

```powershell
& .tools/apache-maven-3.9.9/bin/mvn.cmd '-Dmaven.repo.local=.cache/m2' verify
```

Nếu Windows sandbox không cho JUnit dọn temp ngoài workspace:

```powershell
New-Item -ItemType Directory -Path .cache/test-tmp -Force | Out-Null
$testTemp = (Resolve-Path .cache/test-tmp).Path
& .tools/apache-maven-3.9.9/bin/mvn.cmd '-Dmaven.repo.local=.cache/m2' "-DargLine=-Djava.io.tmpdir=$testTemp" verify
```

## Deploy

```powershell
docker compose up --build -d backend
docker compose --profile demo up --build -d
```

Lệnh đầu chỉ bật backend; lệnh sau bật thêm demo. Compose publish port trên
loopback; trong container, ứng dụng bind `0.0.0.0`.
`SERVER_HOST`/`SERVER_PORT` cấu hình backend;
`BACKEND_URL`/`DEMO_HOST`/`DEMO_PORT` cấu hình demo.

## Entry point và lưu trữ

- `backend/src/main/java/vn/edu/hust/collector/Application.java`: Spring Boot/API/CLI.
- `controller/CollectionController.java`: Spring MVC và hợp đồng HTTP.
- `service/CollectionService.java`: pipeline chung cho HTTP/CLI.
- `service/BatchService.java`: dedup và điều phối batch.
- `repository/`: URL nguồn và file output.
- `frontend/demo/`: client demo cùng proxy.

Output vẫn là documents.json, documents.jsonl, results.jsonl, summary.json.
CLI yêu cầu thư mục output mới; HTTP không ghi output. Batch chạy không cần
server hoặc frontend. Whitelist được đóng gói trong backend resources.

Đọc tiếp: [API](api.md), [kiến trúc](architecture.md), [demo](demo.md),
[kiểm thử](validation.md). `.tools/` và `.cache/` không thuộc source cần chuyển máy.
