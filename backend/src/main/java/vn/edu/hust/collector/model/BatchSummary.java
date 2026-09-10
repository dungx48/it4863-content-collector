package vn.edu.hust.collector.model;

public record BatchSummary(int processed, int kept, int skipped, int duplicate, int failed, int remaining) {}
