package vn.edu.hust.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import vn.edu.hust.collector.crawl.*;
import vn.edu.hust.collector.extract.ContentExtractor;

/** Điều phối CLI, xử lý tuần tự URL đầu vào và ghi kết quả JSON/JSONL. */
public class Collector {
    /** Điểm thay thế trình tải, giúp test không gọi website thật. */
    @FunctionalInterface interface Downloader { Fetcher.Fetched fetch(String url) throws Exception; }

    /** Kết quả xử lý của mọi URL, kể cả URL bị bỏ hoặc lỗi. */
    public record Result(String originalUrl, String url, String finalUrl, Integer httpStatus,
                         String status, String reason, String contentType, String contentHash) {}

    /** Nội dung sạch của một URL được giữ lại. */
    public record Document(String url, String title, String text, List<ContentExtractor.Link> links,
                           String contentType, String contentHash, String fetchedAt) {}

    /** Bộ đếm cuối một lần chạy batch. */
    public record Summary(int processed, int kept, int skipped, int duplicate, int failed, int remaining) {}

    private record Outcome(Result result, Document document) {}
    private final UrlPolicy policy;
    private final Downloader downloader;
    private final ContentExtractor extractor = new ContentExtractor();
    private final ObjectMapper json = new ObjectMapper();

    /** Tạo collector với policy và downloader được truyền vào. */
    Collector(UrlPolicy policy, Downloader downloader) { this.policy=policy; this.downloader=downloader; }

    /** Chọn chế độ web hoặc phân tích tham số để chạy batch. */
    public static void main(String[] args) {
        try {
            if (args.length == 0 || Arrays.asList(args).contains("--help")) { help(); return; }
            if ("--serve".equals(args[0])) {
                if (args.length > 2) throw new IllegalArgumentException("Use --serve [port].");
                int port = args.length == 2 ? Integer.parseInt(args[1]) : 8080;
                DemoServer.start(port);
                return;
            }
            var options = new HashMap<String,String>();
            for (int i=0;i<args.length;i+=2) {
                if (!Set.of("--url","--input","--output","--limit").contains(args[i]) || i+1>=args.length || options.putIfAbsent(args[i],args[i+1])!=null)
                    throw new IllegalArgumentException("Invalid or repeated option. Use --help.");
            }
            if (options.containsKey("--url") == options.containsKey("--input"))
                throw new IllegalArgumentException("Provide exactly one of --url or --input.");
            int limit = Integer.parseInt(options.getOrDefault("--limit","100"));
            if (limit < 1 || limit > 50000) throw new IllegalArgumentException("--limit must be 1..50000");
            var inputs = options.containsKey("--url") ? List.of(options.get("--url")) : readInput(Path.of(options.get("--input")));
            if (inputs.isEmpty()) throw new IllegalArgumentException("Input has no HTTP(S) URL lines.");
            Path output = Path.of(options.getOrDefault("--output","output/run-"+Instant.now().toString().replace(':','-')));
            var policy = new UrlPolicy("hust.edu.vn");
            var fetcher = new Fetcher(policy,20*1024*1024,1000);
            var summary = new Collector(policy,url -> fetchWithRetry(fetcher,url)).run(inputs,output,limit);
            System.out.println("Output: " + output.toAbsolutePath());
            System.out.println(summary);
        } catch (Exception error) {
            System.err.println("Collector: " + error.getMessage()); System.exit(1);
        }
    }

    /** Đọc tối đa 10 MiB và chỉ giữ các dòng bắt đầu bằng HTTP(S). */
    static List<String> readInput(Path path) throws IOException {
        if (Files.size(path)>10*1024*1024) throw new IllegalArgumentException("Input exceeds 10 MiB");
        try (var lines=Files.lines(path,StandardCharsets.UTF_8)) {
            return lines.map(s -> s.replace("\uFEFF","").strip())
                .filter(s -> s.startsWith("http://") || s.startsWith("https://")).toList();
        }
    }

    /** Thử tối đa ba lần với lỗi I/O, HTTP 429 hoặc HTTP 5xx. */
    static Fetcher.Fetched fetchWithRetry(Fetcher fetcher,String url) throws Exception {
        for (int attempt=0;attempt<3;attempt++) {
            try {
                var response=fetcher.fetch(url,"hust.edu.vn");
                if ((response.status()!=429 && response.status()<500) || attempt==2) return response;
            } catch (IOException error) { if (attempt==2) throw error; }
            Thread.sleep(1000L*(attempt+1));
        }
        throw new IllegalStateException("Retry exhausted");
    }

    /** Xử lý tối đa {@code limit} URL và tạo một thư mục output mới. */
    public Summary run(List<String> inputs,Path directory,int limit) throws Exception {
        if (limit<1) throw new IllegalArgumentException("Limit must be positive");
        if (Files.exists(directory)) throw new IllegalArgumentException("Output directory already exists; choose a new directory.");
        Files.createDirectories(directory);
        Set<String> seenUrls=new HashSet<>(), seenHashes=new HashSet<>();
        int kept=0,skipped=0,duplicate=0,failed=0,processed=0;
        // Ghi đồng thời JSONL để phục hồi phần đã chạy và JSON mảng để dễ trình diễn.
        try (var documents=Files.newBufferedWriter(directory.resolve("documents.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
             var results=Files.newBufferedWriter(directory.resolve("results.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
             var readable=json.getFactory().createGenerator(Files.newBufferedWriter(directory.resolve("documents.json"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW))) {
            readable.useDefaultPrettyPrinter();
            readable.writeStartArray();
            for (String original : inputs.subList(0,Math.min(inputs.size(),limit))) {
                Outcome outcome=process(original,seenUrls,seenHashes);
                if (outcome.document()!=null) {
                    documents.write(json.writeValueAsString(outcome.document())); documents.newLine(); documents.flush();
                    readable.writeObject(outcome.document()); readable.flush();
                }
                results.write(json.writeValueAsString(outcome.result())); results.newLine(); results.flush();
                processed++;
                switch (outcome.result().status()) {
                    case "KEPT" -> kept++;
                    case "SKIPPED" -> skipped++;
                    case "DUPLICATE" -> duplicate++;
                    default -> failed++;
                }
                System.out.printf("[%d/%d] %s %s%n",processed,Math.min(inputs.size(),limit),outcome.result().status(),outcome.result().reason());
            }
            readable.writeEndArray();
        }
        var summary=new Summary(processed,kept,skipped,duplicate,failed,inputs.size()-processed);
        json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("summary.json").toFile(),summary);
        return summary;
    }

    /** Chuẩn hóa, tải, bóc tách và phân loại một URL mà không làm dừng cả batch. */
    private Outcome process(String original,Set<String> seenUrls,Set<String> seenHashes) {
        String url=null,finalUrl=null,type=null; Integer http=null;
        try {
            url=policy.normalize(original);
            if (!policy.inDomain(url,"hust.edu.vn")) throw new IllegalArgumentException("OUTSIDE_DOMAIN");
            if (policy.loginUrl(url)) throw new IllegalArgumentException("LOGIN_REQUIRED");
            // Chống trùng URL trước khi tải; chống trùng nội dung sau khi băm text.
            if (!seenUrls.add(url)) return outcome(original,url,null,null,"DUPLICATE","URL_MATCH",null,null,null);
            var fetched=downloader.fetch(url); finalUrl=fetched.url(); http=fetched.status(); type=fetched.mime();
            if (http!=200) return outcome(original,url,finalUrl,http,"FAILED","HTTP_"+http,type,null,null);
            var content=extractor.extract(fetched.bytes(),finalUrl,type); type=content.type();
            if (!content.keep()) return outcome(original,url,finalUrl,http,"SKIPPED",content.reason(),type,null,null);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.text().getBytes(StandardCharsets.UTF_8)));
            if (!seenHashes.add(hash)) return outcome(original,url,finalUrl,http,"DUPLICATE","CONTENT_HASH_MATCH",type,hash,null);
            var document=new Document(finalUrl,content.title(),content.text(),content.links(),type,hash,Instant.now().toString());
            return outcome(original,url,finalUrl,http,"KEPT","CONTENT",type,hash,document);
        } catch (IllegalArgumentException error) {
            return outcome(original,url,finalUrl,http,"SKIPPED",error.getMessage(),type,null,null);
        } catch (Exception error) {
            return outcome(original,url,finalUrl,http,"FAILED",error.getClass().getSimpleName()+": "+error.getMessage(),type,null,null);
        }
    }
    /** Tạo đồng thời Result và Document tùy chọn cho một URL. */
    private static Outcome outcome(String original,String url,String finalUrl,Integer http,String status,String reason,String type,String hash,Document document) {
        return new Outcome(new Result(original,url,finalUrl,http,status,reason,type,hash),document);
    }

    /** In cú pháp CLI mà không chạy thu thập. */
    private static void help() {
        System.out.println("""
            IT4863 - Public HUST content collector
            java -jar target/collector.jar --serve 8080
            java -jar target/collector.jar --url https://hust.edu.vn/example.html
            java -jar target/collector.jar --input N06_Ctrl+F --limit 100 --output output/sample

            --url      Process one URL (mutually exclusive with --input).
            --input    UTF-8 URL list; N06 headers and trailing commas are handled.
            --limit    Maximum input URL lines to process, default 100, maximum 50000.
            --output   New output directory; default output/run-<timestamp>.
            --help     Show this help.
            --serve    Start the local demo website; optional port, default 8080.

            Outputs: documents.json (readable title/text/links), documents.jsonl, results.jsonl, summary.json.
            Only supplied URLs are processed. No recursive crawling.
            """);
    }
}
