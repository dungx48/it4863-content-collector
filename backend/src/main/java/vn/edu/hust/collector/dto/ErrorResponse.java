package vn.edu.hust.collector.dto;

public record ErrorResponse(boolean success, String reason, String message) {}
