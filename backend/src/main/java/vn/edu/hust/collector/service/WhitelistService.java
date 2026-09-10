package vn.edu.hust.collector.service;

import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.hust.collector.repository.UrlListRepository;

@Service
public class WhitelistService {
    private final List<String> urls;

    public WhitelistService(UrlListRepository repository) throws IOException {
        urls = repository.loadWhitelist();
    }

    public List<String> search(String input) {
        String query = input.strip().toLowerCase(Locale.ROOT);
        if (query.length() > 300) throw new IllegalArgumentException("Từ khóa gợi ý quá dài.");
        return urls.stream().filter(url -> url.toLowerCase(Locale.ROOT).contains(query)).limit(20).toList();
    }

    public int total() { return urls.size(); }
}
