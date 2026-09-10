package vn.edu.hust.collector.dto;

public record RejectedExtractionResponse(boolean success, String reason, String message,
                                         String url, int httpStatus) {}
