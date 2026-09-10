package vn.edu.hust.collector.model;

import java.util.List;

public record Document(String url, String title, String text, List<Link> links,
                       String contentType, String contentHash, String fetchedAt) {}
