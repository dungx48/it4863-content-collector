package vn.edu.hust.collector.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Đọc danh sách URL đầu vào cho batch CLI. */
@Repository
public class UrlListRepository {
    public List<String> readInput(Path path) throws IOException {
        if (Files.size(path) > 10 * 1024 * 1024) throw new IllegalArgumentException("Input exceeds 10 MiB");
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.map(s -> s.replace("\uFEFF", "").strip())
                .filter(s -> s.startsWith("http://") || s.startsWith("https://")).toList();
        }
    }
}
