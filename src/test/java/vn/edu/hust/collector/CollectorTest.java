package vn.edu.hust.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hust.collector.crawl.*;
import static org.assertj.core.api.Assertions.*;

class CollectorTest {
    @TempDir Path temporary;
    @Test void importsN06AndExportsDecisionsWithoutFetchingDuplicateUrls() throws Exception {
        Path input=temporary.resolve("N06_Ctrl+F");
        Files.writeString(input,"\uFEFFNhóm N06\nThành viên:\nhttps://hust.edu.vn/a,\nhttps://hust.edu.vn/a\nhttps://hust.edu.vn/b\nhttps://hust.edu.vn/missing\nhttps://asso.hust.edu.vn/adfs/ls/\nhttps://other.test/\n");
        var requests=new AtomicInteger();
        var collector=new Collector(new UrlPolicy("hust.edu.vn"),url -> {
            requests.incrementAndGet();
            if (url.endsWith("missing")) return new Fetcher.Fetched(url,404,"text/html",new byte[0]);
            String html="<html><meta charset='utf-8'><article><h1>Học bổng</h1><p>"+"Thông tin học bổng dành cho sinh viên. ".repeat(8)+"</p></article></html>";
            return new Fetcher.Fetched(url,200,"text/html",html.getBytes(StandardCharsets.UTF_8));
        });
        Path output=temporary.resolve("out");
        var summary=collector.run(Collector.readInput(input),output,100);
        assertThat(summary).isEqualTo(new Collector.Summary(6,1,2,2,1,0));
        assertThat(requests.get()).isEqualTo(3);
        var mapper=new ObjectMapper();
        var documents=Files.readAllLines(output.resolve("documents.jsonl"));
        assertThat(documents).hasSize(1);
        assertThat(mapper.readTree(documents.getFirst()).get("text").asText()).contains("học bổng");
        assertThat(Files.readAllLines(output.resolve("results.jsonl"))).hasSize(6);
        assertThat(mapper.readTree(output.resolve("summary.json").toFile()).get("failed").asInt()).isEqualTo(1);
        assertThatThrownBy(() -> collector.run(List.of("https://hust.edu.vn/a"),output,1)).hasMessageContaining("already exists");
    }
    @Test void limitsBatchAndPreservesEarlierResultsWhenDownloadFails() throws Exception {
        var collector=new Collector(new UrlPolicy("hust.edu.vn"),url -> { throw new java.io.IOException("fixture unavailable"); });
        var result=collector.run(List.of("https://hust.edu.vn/a","https://hust.edu.vn/b"),temporary.resolve("limited"),1);
        assertThat(result.failed()).isEqualTo(1); assertThat(result.remaining()).isEqualTo(1);
        assertThat(Files.readString(temporary.resolve("limited/results.jsonl"))).contains("fixture unavailable");
    }
}
