package vn.edu.hust.collector;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import vn.edu.hust.collector.config.HealthCheck;
import vn.edu.hust.collector.repository.UrlListRepository;
import vn.edu.hust.collector.service.*;

/** Spring Boot entry point; starts REST API by default and supports a non-web batch mode. */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        try {
            if (Arrays.asList(args).contains("--help")) { help(); return; }
            if (args.length == 1 && "--health-check".equals(args[0])) {
                HealthCheck.main(new String[0]);
                return;
            }
            if (args.length == 0 || "--serve".equals(args[0])) {
                if (args.length > 2) throw new IllegalArgumentException("Use --serve [port].");
                var springArgs = new ArrayList<String>();
                if (args.length == 2) springArgs.add("--server.port=" + parsePort(args[1]));
                SpringApplication.run(Application.class, springArgs.toArray(String[]::new));
                return;
            }
            runBatch(args);
        } catch (Exception error) {
            System.err.println("Collector: " + error.getMessage());
            System.exit(1);
        }
    }

    private static void runBatch(String[] args) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).run()) {
            var options = new HashMap<String,String>();
            for (int i=0;i<args.length;i+=2) {
                if (!Set.of("--url","--input","--output","--limit").contains(args[i]) || i+1>=args.length || options.putIfAbsent(args[i],args[i+1])!=null)
                    throw new IllegalArgumentException("Invalid or repeated option. Use --help.");
            }
            if (options.containsKey("--url") == options.containsKey("--input"))
                throw new IllegalArgumentException("Provide exactly one of --url or --input.");
            int limit = Integer.parseInt(options.getOrDefault("--limit","100"));
            if (limit < 1 || limit > 50000) throw new IllegalArgumentException("--limit must be 1..50000");
            var repository = context.getBean(UrlListRepository.class);
            var inputs = options.containsKey("--url") ? List.of(options.get("--url")) : repository.readInput(Path.of(options.get("--input")));
            if (inputs.isEmpty()) throw new IllegalArgumentException("Input has no HTTP(S) URL lines.");
            Path output = Path.of(options.getOrDefault("--output","output/run-"+Instant.now().toString().replace(':','-')));
            var summary = context.getBean(BatchService.class).run(inputs, output, limit);
            System.out.println("Output: " + output.toAbsolutePath());
            System.out.println(summary);
        }
    }

    private static int parsePort(String raw) {
        int port = Integer.parseInt(raw);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
        return port;
    }

    /** In cú pháp CLI mà không chạy thu thập. */
    private static void help() {
        System.out.println("""
            IT4863 - Public HUST content collector
            java -jar backend/target/collector.jar --serve 8080
            java -jar backend/target/collector.jar --url https://hust.edu.vn/example.html
            java -jar backend/target/collector.jar --input N06_Ctrl+F --limit 100 --output output/sample

            --url      Process one URL (mutually exclusive with --input).
            --input    UTF-8 URL list; N06 headers and trailing commas are handled.
            --limit    Maximum input URL lines to process, default 100, maximum 50000.
            --output   New output directory; default output/run-<timestamp>.
            --help     Show this help.
            --serve    Start the backend API (frontend runs separately); optional port, default 8080.
            --health-check  Probe /health for the container health check.

            Outputs: documents.json (readable title/text/links), documents.jsonl, results.jsonl, summary.json.
            Only supplied URLs are processed. No recursive crawling.
            """);
    }
}
