package vn.edu.hust.collector.config;

import org.springframework.context.annotation.*;
import vn.edu.hust.collector.crawl.*;
import vn.edu.hust.collector.extract.ContentExtractor;

/** Khai báo các thành phần không do Spring tự tạo từ annotation. */
@Configuration
public class CollectorConfiguration {
    /** Giới hạn mọi URL thu thập trong hust.edu.vn và subdomain. */
    @Bean UrlPolicy urlPolicy() { return new UrlPolicy("hust.edu.vn"); }

    /** Mỗi tài nguyên tối đa 20 MiB, cách request cùng host ít nhất một giây. */
    @Bean Fetcher fetcher(UrlPolicy policy) {
        return new Fetcher(policy, 20 * 1024 * 1024, 1_000);
    }

    /** Một extractor dùng chung Tika và Jsoup cho API/CLI. */
    @Bean ContentExtractor contentExtractor() { return new ContentExtractor(); }
}
