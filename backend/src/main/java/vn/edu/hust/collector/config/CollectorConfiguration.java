package vn.edu.hust.collector.config;

import org.springframework.context.annotation.*;
import vn.edu.hust.collector.crawl.*;
import vn.edu.hust.collector.extract.ContentExtractor;

@Configuration
public class CollectorConfiguration {
    @Bean UrlPolicy urlPolicy() { return new UrlPolicy("hust.edu.vn"); }

    @Bean Fetcher fetcher(UrlPolicy policy) {
        return new Fetcher(policy, 20 * 1024 * 1024, 1_000);
    }

    @Bean ContentExtractor contentExtractor() { return new ContentExtractor(); }
}
