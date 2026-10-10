package vn.edu.hust.collector.dto;

import vn.edu.hust.collector.exception.ApiException;

/** Lambda là trọng số mô hình văn bản theo slide; Lucene dùng trọng số nền 1-lambda. */
public record SearchOptions(String model, String operator, float k1, float b, float lambda) {
    public SearchOptions {
        if (!java.util.Set.of("classic", "cosine", "bm25", "lm").contains(model)
                || !java.util.Set.of("or", "and").contains(operator)
                || !Float.isFinite(k1) || k1 < 0
                || !Float.isFinite(b) || b < 0 || b > 1
                || !Float.isFinite(lambda) || lambda <= 0 || lambda >= 1 || 1f - lambda == 1f)
            throw ApiException.badRequest("INVALID_SEARCH_OPTIONS",
                "Mô hình: classic/cosine/bm25/lm; toán tử: or/and; k1 >= 0; b từ 0 đến 1; 0 < lambda < 1.");
    }
    public static SearchOptions defaults() { return new SearchOptions("classic", "or", 1.2f, .75f, .5f); }
    public String label() {
        return switch (model) {
            case "cosine" -> "VSM cosine (lnc.ltc)";
            case "bm25" -> "BM25 (k1=" + k1 + ", b=" + b + ")";
            case "lm" -> "LM Jelinek–Mercer (lambda văn bản=" + lambda + ")";
            default -> "ClassicSimilarity (TF-IDF)";
        };
    }
}
