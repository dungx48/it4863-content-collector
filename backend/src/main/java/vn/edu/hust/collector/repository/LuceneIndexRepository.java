package vn.edu.hust.collector.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import vn.edu.hust.collector.dto.*;
import vn.edu.hust.collector.model.SearchDocument;

/** Lưu một corpus đã commit trên đĩa; request không thấy chỉ mục thay dở. */
@Repository
public class LuceneIndexRepository {
    private final Path path;
    public LuceneIndexRepository(@Value("${collector.search.index-path}") String path) {
        this.path = Path.of(path).toAbsolutePath().normalize();
    }

    public static List<String> tokens(String value) throws IOException {
        // StandardAnalyzer chuẩn hóa token giống nhau khi lập chỉ mục và tìm kiếm.
        try (var analyzer = new StandardAnalyzer();
             var stream = analyzer.tokenStream("content", Normalizer.normalize(value, Normalizer.Form.NFC))) {
            var term = stream.addAttribute(CharTermAttribute.class);
            var tokens = new LinkedHashSet<String>();
            stream.reset();
            while (stream.incrementToken()) tokens.add(term.toString());
            stream.end();
            return List.copyOf(tokens);
        }
    }

    public synchronized IndexStatus status() throws IOException {
        // Thư mục tồn tại chưa chắc đã chứa một Lucene index hợp lệ.
        try (var directory = FSDirectory.open(path)) {
            if (!DirectoryReader.indexExists(directory)) return new IndexStatus(false, 0, null, "ClassicSimilarity (TF-IDF)");
            try (var reader = DirectoryReader.open(directory)) {
                return status(reader);
            }
        }
    }

    private IndexStatus status(DirectoryReader reader) throws IOException {
        return new IndexStatus(true, reader.numDocs(), reader.getIndexCommit().getUserData().get("indexedAt"),
            "ClassicSimilarity (TF-IDF)");
    }

    public synchronized IndexStatus replace(List<SearchDocument> documents) throws IOException {
        try (var directory = FSDirectory.open(path); var analyzer = new StandardAnalyzer()) {
            var config = new IndexWriterConfig(analyzer).setSimilarity(new ClassicSimilarity())
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND).setCommitOnClose(false);
            try (var writer = new IndexWriter(directory, config)) {
                try {
                    // Xóa và thêm nằm trong một commit; rollback giữ corpus cũ khi lỗi.
                    writer.deleteAll();
                    for (var source : documents) {
                        var doc = new org.apache.lucene.document.Document();
                        doc.add(new StoredField("id", source.id()));
                        doc.add(new StoredField("title", source.title()));
                        doc.add(new StoredField("url", source.url()));
                        doc.add(new StoredField("text", source.text()));
                        // Ghép title + text vào một field để điểm không có boost ẩn.
                        doc.add(new TextField("content", Normalizer.normalize(
                            source.title() + "\n" + source.text(), Normalizer.Form.NFC), Field.Store.NO));
                        writer.addDocument(doc);
                    }
                    writer.setLiveCommitData(Map.of("indexedAt", Instant.now().toString()).entrySet());
                    writer.commit();
                } catch (IOException | RuntimeException failure) {
                    writer.rollback();
                    throw failure;
                }
            }
            try (var reader = DirectoryReader.open(directory)) { return status(reader); }
        }
    }

    public synchronized SearchResponse search(String queryText, List<String> tokens, int limit) throws IOException {
        try (var directory = FSDirectory.open(path)) {
            if (!DirectoryReader.indexExists(directory))
                throw vn.edu.hust.collector.exception.ApiException.badRequest("INDEX_NOT_READY", "Hãy tạo chỉ mục trước khi tìm kiếm.");
            try (var reader = DirectoryReader.open(directory)) {
                var searcher = new IndexSearcher(reader);
                searcher.setSimilarity(new ClassicSimilarity());
                // SHOULD tạo truy vấn OR: tài liệu khớp ít nhất một token vẫn xuất hiện.
                var builder = new BooleanQuery.Builder();
                for (String token : tokens) builder.add(new TermQuery(new Term("content", token)), BooleanClause.Occur.SHOULD);
                var query = builder.build();
                var top = searcher.search(query, limit);
                var hits = new ArrayList<SearchResponse.Hit>();
                var stored = reader.storedFields();
                for (var hit : top.scoreDocs) {
                    // Stored fields trả dữ liệu gốc; explain trả phép tính điểm của Lucene.
                    var doc = stored.document(hit.doc);
                    hits.add(new SearchResponse.Hit(doc.get("id"), doc.get("title"), doc.get("url"), doc.get("text"),
                        hit.score, searcher.explain(query, hit.doc).toString()));
                }
                return new SearchResponse(queryText, tokens, searcher.count(query), limit,
                    "ClassicSimilarity (TF-IDF)", hits);
            }
        }
    }
}
