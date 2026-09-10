package vn.edu.hust.collector.repository;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import vn.edu.hust.collector.model.*;

/** Một phiên ghi batch; chỉ repository biết JSONL, đường dẫn và cách flush. */
public final class BatchOutputRepository implements AutoCloseable {
    private final Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final BufferedWriter documents;
    private final BufferedWriter results;
    private final JsonGenerator readable;

    public BatchOutputRepository(Path directory) throws IOException {
        this.directory = directory;
        if (directory.toAbsolutePath().getParent() != null) Files.createDirectories(directory.toAbsolutePath().getParent());
        try { Files.createDirectory(directory); }
        catch (FileAlreadyExistsException exists) {
            throw new IllegalArgumentException("Output directory already exists; choose a new directory.");
        }
        var opened = new ArrayList<Closeable>();
        try {
            documents = writer("documents.jsonl"); opened.add(documents);
            results = writer("results.jsonl"); opened.add(results);
            var arrayWriter = writer("documents.json"); opened.add(arrayWriter);
            readable = json.getFactory().createGenerator(arrayWriter); opened.add(readable);
            readable.useDefaultPrettyPrinter();
            readable.writeStartArray();
        } catch (IOException | RuntimeException error) {
            Collections.reverse(opened);
            for (var resource : opened) {
                try { resource.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
            }
            throw error;
        }
    }

    private BufferedWriter writer(String name) throws IOException {
        return Files.newBufferedWriter(directory.resolve(name), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    public void append(CollectionResult result, Document document) throws IOException {
        if (document != null) {
            documents.write(json.writeValueAsString(document)); documents.newLine(); documents.flush();
            readable.writeObject(document); readable.flush();
        }
        results.write(json.writeValueAsString(result)); results.newLine(); results.flush();
    }

    public void finish(BatchSummary summary) throws IOException {
        readable.writeEndArray();
        readable.flush();
        json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("summary.json").toFile(), summary);
    }

    @Override public void close() throws IOException {
        // try-with-resources đóng mọi stream kể cả khi một close thất bại.
        try (var d = documents; var r = results; var a = readable) { }
    }
}
