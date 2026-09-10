package vn.edu.hust.collector.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import vn.edu.hust.collector.model.*;
import vn.edu.hust.collector.repository.BatchOutputRepository;
import org.springframework.stereotype.Service;

/** Batch orchestration and deduplication; storage belongs to the repository. */
@Service
public class BatchService {
    private record Outcome(CollectionResult result, Document document) {}
    private final CollectionService collection;

    public BatchService(CollectionService collection) { this.collection = collection; }

    public BatchSummary run(List<String> inputs, Path directory, int limit) throws Exception {
        if (limit < 1 || limit > 50000) throw new IllegalArgumentException("Limit must be 1..50000");
        Set<String> seenUrls = new HashSet<>(), seenHashes = new HashSet<>();
        int kept=0, skipped=0, duplicate=0, failed=0, processed=0;
        try (var output = new BatchOutputRepository(directory)) {
            for (String original : inputs.subList(0, Math.min(inputs.size(), limit))) {
                Outcome outcome = process(original, seenUrls, seenHashes);
                output.append(outcome.result(), outcome.document());
                processed++;
                switch (outcome.result().status()) {
                    case "KEPT" -> kept++;
                    case "SKIPPED" -> skipped++;
                    case "DUPLICATE" -> duplicate++;
                    default -> failed++;
                }
                System.out.printf("[%d/%d] %s %s%n", processed, Math.min(inputs.size(),limit),
                    outcome.result().status(), outcome.result().reason());
            }
            var summary = new BatchSummary(processed, kept, skipped, duplicate, failed, inputs.size()-processed);
            output.finish(summary);
            return summary;
        }
    }

    /** Chuẩn hóa, tải, bóc tách và phân loại một URL mà không làm dừng cả batch. */
    private Outcome process(String original,Set<String> seenUrls,Set<String> seenHashes) {
        String url=null,finalUrl=null,type=null; Integer http=null;
        try {
            url=collection.normalize(original);
            // Chống trùng URL trước khi tải; chống trùng nội dung sau khi băm text.
            if (!seenUrls.add(url)) return outcome(original,url,null,null,"DUPLICATE","URL_MATCH",null,null,null);
            var fetched=collection.download(url); finalUrl=fetched.url(); http=fetched.status(); type=fetched.mime();
            if (http!=200) return outcome(original,url,finalUrl,http,"FAILED","HTTP_"+http,type,null,null);
            var content=collection.extract(fetched); type=content.type();
            if (!content.keep()) return outcome(original,url,finalUrl,http,"SKIPPED",content.reason(),type,null,null);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.text().getBytes(StandardCharsets.UTF_8)));
            if (!seenHashes.add(hash)) return outcome(original,url,finalUrl,http,"DUPLICATE","CONTENT_HASH_MATCH",type,hash,null);
            var document=new Document(finalUrl,content.title(),content.text(),content.links(),type,hash,Instant.now().toString());
            return outcome(original,url,finalUrl,http,"KEPT","CONTENT",type,hash,document);
        } catch (IllegalArgumentException error) {
            return outcome(original,url,finalUrl,http,"SKIPPED",error.getMessage(),type,null,null);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return outcome(original,url,finalUrl,http,"FAILED",error.getClass().getSimpleName()+": "+error.getMessage(),type,null,null);
        }
    }
    /** Tạo đồng thời CollectionResult và Document tùy chọn cho một URL. */
    private static Outcome outcome(String original,String url,String finalUrl,Integer http,String status,String reason,String type,String hash,Document document) {
        return new Outcome(new CollectionResult(original,url,finalUrl,http,status,reason,type,hash),document);
    }

}
