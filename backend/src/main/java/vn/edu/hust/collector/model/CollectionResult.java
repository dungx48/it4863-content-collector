package vn.edu.hust.collector.model;

public record CollectionResult(String originalUrl, String url, String finalUrl, Integer httpStatus,
                               String status, String reason, String contentType, String contentHash) {}
