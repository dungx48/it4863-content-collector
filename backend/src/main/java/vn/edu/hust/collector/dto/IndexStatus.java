package vn.edu.hust.collector.dto;

public record IndexStatus(boolean ready, int documentCount, String indexedAt, String similarity) {}
