package vn.edu.hust.collector.model;

import java.util.List;

public record ExtractedContent(String title, String text, String type, List<Link> links,
                               boolean keep, String reason) {}
