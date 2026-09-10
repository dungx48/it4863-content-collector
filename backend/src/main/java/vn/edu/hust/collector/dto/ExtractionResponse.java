package vn.edu.hust.collector.dto;

import java.util.List;
import vn.edu.hust.collector.model.Link;

public record ExtractionResponse(boolean success, String url, int httpStatus, String contentType,
                                 long elapsedMs, String title, String text, List<Link> links) {}
