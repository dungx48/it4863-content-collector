package vn.edu.hust.collector.model;

/** Kết quả trung gian dùng chung cho API và batch. content chỉ có khi HTTP 200. */
public record CollectionOutcome(FetchedContent fetched, ExtractedContent content, long elapsedMs) {}
