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
import org.apache.lucene.search.similarities.*;
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
        return List.copyOf(new LinkedHashSet<>(tokenSequence(value)));
    }

    static List<String> tokenSequence(String value) throws IOException {
        // Giữ tần suất từ truy vấn cho VSM; API vẫn hiển thị danh sách token duy nhất.
        try (var analyzer = new StandardAnalyzer();
             var stream = analyzer.tokenStream("content", Normalizer.normalize(value, Normalizer.Form.NFC))) {
            var term = stream.addAttribute(CharTermAttribute.class);
            var tokens = new ArrayList<String>();
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

    public synchronized SearchResponse search(String queryText, List<String> tokens, int limit, SearchOptions options) throws IOException {
        try (var directory = FSDirectory.open(path)) {
            if (!DirectoryReader.indexExists(directory))
                throw vn.edu.hust.collector.exception.ApiException.badRequest("INDEX_NOT_READY", "Hãy tạo chỉ mục trước khi tìm kiếm.");
            try (var reader = DirectoryReader.open(directory)) {
                var searcher = new IndexSearcher(reader);
                searcher.setSimilarity(switch (options.model()) {
                    case "bm25" -> new BM25Similarity(options.k1(), options.b());
                    case "lm" -> new LMJelinekMercerSimilarity(1f - options.lambda());
                    default -> new ClassicSimilarity();
                });
                // Điều kiện khớp độc lập với mô hình xếp hạng.
                var builder = new BooleanQuery.Builder();
                for (String token : tokens) builder.add(new TermQuery(new Term("content", token)), options.operator().equals("and") ? BooleanClause.Occur.MUST : BooleanClause.Occur.SHOULD);
                var query = builder.build();
                boolean cosine = options.model().equals("cosine");
                var top = searcher.search(query, cosine ? reader.maxDoc() : limit);
                CosineScores vectors = cosine ? cosineScores(reader, queryText) : null;
                if (cosine) {
                    for (var hit : top.scoreDocs) hit.score = vectors.score(hit.doc);
                    Arrays.sort(top.scoreDocs, Comparator.comparingDouble((ScoreDoc hit) -> -hit.score)
                        .thenComparingInt(hit -> hit.doc));
                }
                var hits = new ArrayList<SearchResponse.Hit>();
                var stored = reader.storedFields();
                for (var hit : Arrays.copyOf(top.scoreDocs, Math.min(limit, top.scoreDocs.length))) {
                    // Stored fields trả dữ liệu gốc; explain trả phép tính điểm của Lucene.
                    var doc = stored.document(hit.doc);
                    hits.add(new SearchResponse.Hit(doc.get("id"), doc.get("title"), doc.get("url"), doc.get("text"),
                        hit.score, cosine ? vectors.explain(reader, hit.doc) : searcher.explain(query, hit.doc).toString()));
                }
                return new SearchResponse(queryText, tokens, searcher.count(query), limit,
                    options.label(), options, hits);
            }
        }
    }

    private record CosineScores(double[] normSquared, double[] dot, double queryNorm,
                                Map<String, Double> queryWeights) {
        float score(int doc) {
            double denominator = Math.sqrt(normSquared[doc]) * queryNorm;
            return denominator == 0 ? 0 : (float) (dot[doc] / denominator);
        }
        String explain(DirectoryReader reader, int doc) throws IOException {
            var explanation = new StringBuilder("VSM cosine lnc.ltc\nTF = 1 + ln(freq); query IDF = ln(N/df)\n");
            for (var entry : queryWeights.entrySet()) {
                var postings = MultiTerms.getTermPostingsEnum(reader, "content",
                    new org.apache.lucene.util.BytesRef(entry.getKey()), PostingsEnum.FREQS);
                int frequency = postings != null && postings.advance(doc) == doc ? postings.freq() : 0;
                double weight = frequency == 0 ? 0 : 1 + Math.log(frequency);
                explanation.append(String.format(Locale.ROOT,
                    "%s: tf_d=%d, df=%d, w_d=%.6f, w_q=%.6f, contribution=%.6f%n",
                    entry.getKey(), frequency, reader.docFreq(new Term("content", entry.getKey())),
                    weight, entry.getValue(), weight * entry.getValue()));
            }
            explanation.append(String.format(Locale.ROOT,
                "dot=%.6f; ||d||=%.6f (toàn bộ từ trong văn bản); ||q||=%.6f%nscore=%.6f",
                dot[doc], Math.sqrt(normSquared[doc]), queryNorm, score(doc)));
            if (queryNorm == 0) explanation.append("\nVector truy vấn bằng 0: tất cả từ đã biết có IDF=0 hoặc không có từ trong chỉ mục; quy ước score=0.");
            return explanation.toString();
        }
    }

    private CosineScores cosineScores(DirectoryReader reader, String query) throws IOException {
        var frequencies = new LinkedHashMap<String, Integer>();
        for (String token : tokenSequence(query)) frequencies.merge(token, 1, Integer::sum);
        var weights = new LinkedHashMap<String, Double>();
        double queryNormSquared = 0;
        for (var entry : frequencies.entrySet()) {
            int df = reader.docFreq(new Term("content", entry.getKey()));
            // Từ ngoài từ vựng không có chiều trong không gian của corpus.
            double weight = df == 0 ? 0 : (1 + Math.log(entry.getValue())) * Math.log((double) reader.numDocs() / df);
            weights.put(entry.getKey(), weight);
            queryNormSquared += weight * weight;
        }
        double[] norms = new double[reader.maxDoc()], dot = new double[reader.maxDoc()];
        // shortcut: quét postings để tính chuẩn vector mỗi truy vấn; cache theo commit khi corpus lớn.
        var terms = MultiTerms.getTerms(reader, "content").iterator();
        org.apache.lucene.util.BytesRef term;
        while ((term = terms.next()) != null) {
            double queryWeight = weights.getOrDefault(term.utf8ToString(), 0d);
            var postings = terms.postings(null, PostingsEnum.FREQS);
            for (int doc = postings.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = postings.nextDoc()) {
                double weight = 1 + Math.log(postings.freq());
                norms[doc] += weight * weight;
                dot[doc] += weight * queryWeight;
            }
        }
        return new CosineScores(norms, dot, Math.sqrt(queryNormSquared), weights);
    }
}
