package vn.edu.hust.collector.service;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.hust.collector.dto.*;
import vn.edu.hust.collector.exception.ApiException;
import vn.edu.hust.collector.model.SearchDocument;
import vn.edu.hust.collector.repository.LuceneIndexRepository;

@Service
public class SearchService {
    private final LuceneIndexRepository repository;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public SearchService(LuceneIndexRepository repository) { this.repository = repository; }

    public IndexStatus status() throws IOException { return repository.status(); }

    public IndexStatus index(byte[] body) throws IOException {
        JsonNode root;
        try { root = json.readTree(body); }
        catch (IOException error) { throw invalid("File không phải JSON hợp lệ."); }
        if (root == null || !root.isArray() || root.isEmpty() || root.size() > 10000)
            throw invalid("Cần một mảng JSON gồm 1–10.000 văn bản.");
        var documents = new ArrayList<SearchDocument>();
        var ids = new HashSet<String>();
        for (int i = 0; i < root.size(); i++) {
            var node = root.get(i);
            if (!node.isObject()) throw invalid("Văn bản thứ " + (i + 1) + " phải là object.");
            String text = field(node, "text", true, 2_000_000);
            String title = field(node, "title", false, 2000);
            String url = field(node, "url", false, 8192);
            String id = field(node, "id", false, 200);
            if (id.isBlank()) id = "doc-" + (i + 1);
            if (!ids.add(id)) throw invalid("ID bị trùng: " + id);
            if (LuceneIndexRepository.tokens(title + "\n" + text).isEmpty())
                throw invalid("Văn bản thứ " + (i + 1) + " không có token để lập chỉ mục.");
            documents.add(new SearchDocument(id, title, text, url));
        }
        // Validate the entire collection before touching the existing index.
        return repository.replace(documents);
    }

    public SearchResponse search(String query, int limit) throws IOException {
        if (query == null || query.isBlank() || query.length() > 500 || limit < 1 || limit > 50)
            throw ApiException.badRequest("INVALID_QUERY", "Truy vấn cần 1–500 ký tự; số kết quả từ 1 đến 50.");
        var tokens = LuceneIndexRepository.tokens(query);
        if (tokens.isEmpty()) throw ApiException.badRequest("INVALID_QUERY", "Truy vấn không có từ để tìm kiếm.");
        return repository.search(query.strip(), tokens, limit);
    }

    private String field(JsonNode node, String key, boolean required, int max) {
        var value = node.get(key);
        if (value == null || value.isNull()) {
            if (required) throw invalid("Thiếu trường " + key + ".");
            return "";
        }
        if (!value.isTextual() || value.textValue().length() > max || (required && value.textValue().isBlank()))
            throw invalid("Trường " + key + " phải là chuỗi hợp lệ, tối đa " + max + " ký tự.");
        return value.textValue().strip();
    }
    private ApiException invalid(String message) { return ApiException.badRequest("INVALID_CORPUS", message); }
}
