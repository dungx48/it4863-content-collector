package vn.edu.hust.collector.dto;

import java.util.List;

public record SearchResponse(String query, List<String> tokens, int total, int limit,
                             String similarity, SearchOptions options, List<Hit> results) {
    public record Hit(String id, String title, String url, String text, float score, String explanation) {}
}
