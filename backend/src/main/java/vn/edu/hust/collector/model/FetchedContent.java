package vn.edu.hust.collector.model;

public record FetchedContent(String url, int status, String mime, byte[] bytes) {}
