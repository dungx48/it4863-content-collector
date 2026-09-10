package vn.edu.hust.collector.repository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Nguồn danh sách URL: whitelist classpath và file batch UTF-8. */
@Repository
public class UrlListRepository {
    public List<String> loadWhitelist() throws IOException {
        try (InputStream input = UrlListRepository.class.getResourceAsStream("/N06_Ctrl+F.txt")) {
            if (input == null) throw new IOException("Missing N06_Ctrl+F whitelist");
            var urls = new LinkedHashSet<String>();
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    String value = line.replace("\uFEFF", "").strip();
                    if (value.endsWith(",")) value = value.substring(0, value.length() - 1).strip();
                    if (value.startsWith("http://") || value.startsWith("https://")) urls.add(value);
                }
            }
            return List.copyOf(urls);
        }
    }

    public List<String> readInput(Path path) throws IOException {
        if (Files.size(path) > 10 * 1024 * 1024) throw new IllegalArgumentException("Input exceeds 10 MiB");
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.map(s -> s.replace("\uFEFF", "").strip())
                .filter(s -> s.startsWith("http://") || s.startsWith("https://")).toList();
        }
    }
}
