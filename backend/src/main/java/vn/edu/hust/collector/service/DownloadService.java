package vn.edu.hust.collector.service;

import java.io.IOException;
import org.springframework.stereotype.Service;
import vn.edu.hust.collector.crawl.Fetcher;
import vn.edu.hust.collector.model.FetchedContent;

/** Retry thuộc nghiệp vụ tải, không nằm trong CLI hoặc controller. */
@Service
public class DownloadService implements Downloader {
    private final Fetcher fetcher;

    public DownloadService(Fetcher fetcher) { this.fetcher = fetcher; }

    @Override public FetchedContent fetch(String url) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var response = fetcher.fetch(url, "hust.edu.vn");
                if ((response.status() != 429 && response.status() < 500) || attempt == 2) return response;
            } catch (IOException error) {
                if (attempt == 2) throw error;
            }
            Thread.sleep(1000L * (attempt + 1));
        }
        throw new IllegalStateException("Retry exhausted");
    }
}
