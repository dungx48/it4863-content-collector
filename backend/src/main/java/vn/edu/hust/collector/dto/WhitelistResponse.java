package vn.edu.hust.collector.dto;

import java.util.List;

public record WhitelistResponse(boolean success, int total, List<String> urls) {}
