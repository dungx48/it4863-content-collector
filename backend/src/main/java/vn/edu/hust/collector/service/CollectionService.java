package vn.edu.hust.collector.service;

import vn.edu.hust.collector.crawl.UrlPolicy;
import vn.edu.hust.collector.extract.ContentExtractor;
import vn.edu.hust.collector.model.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import vn.edu.hust.collector.exception.BusyException;

/** Nghiệp vụ thu thập dùng chung cho HTTP và CLI, không phụ thuộc transport. */
@Service
public class CollectionService {
    private final UrlPolicy policy;
    private final Downloader downloader;
    private final ContentExtractor extractor;
    private final Semaphore extractionSlot = new Semaphore(1);

    public CollectionService(UrlPolicy policy, Downloader downloader, ContentExtractor extractor) {
        this.policy = policy;
        this.downloader = downloader;
        this.extractor = extractor;
    }

    public String normalize(String input) {
        String url = policy.normalize(input);
        if (!policy.inDomain(url, "hust.edu.vn")) throw new IllegalArgumentException("OUTSIDE_DOMAIN");
        if (policy.loginUrl(url)) throw new IllegalArgumentException("LOGIN_REQUIRED");
        return url;
    }

    // Fetcher giữ cache robots/rate limit; tuần tự hóa để không phá trạng thái theo host.
    public CollectionOutcome collect(String input) throws Exception {
        if (!extractionSlot.tryAcquire()) throw new BusyException();
        try {
            synchronized (this) {
                long started = System.nanoTime();
                var fetched = downloader.fetch(normalize(input));
                var content = fetched.status() == 200
                    ? extractor.extract(fetched.bytes(), fetched.url(), fetched.mime()) : null;
                return new CollectionOutcome(fetched, content, (System.nanoTime() - started) / 1_000_000);
            }
        } finally {
            extractionSlot.release();
        }
    }

    public synchronized FetchedContent download(String input) throws Exception {
        return downloader.fetch(normalize(input));
    }

    public synchronized ExtractedContent extract(FetchedContent fetched) throws Exception {
        return extractor.extract(fetched.bytes(), fetched.url(), fetched.mime());
    }
}
