package vn.edu.hust.collector.dto;

import jakarta.validation.constraints.*;

public record ExtractionRequest(
    @NotBlank(message = "URL không được để trống")
    @Size(max = 2_048, message = "URL quá dài")
    String url) {}
