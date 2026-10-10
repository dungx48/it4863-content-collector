package vn.edu.hust.collector;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.hust.collector.controller.SearchController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SearchApiTest {
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("collector.search.index-path", () -> temp.resolve("index").toString());
    }
    @Autowired WebApplicationContext context;

    @Test void importsQueriesAndRejectsMalformedOrOversizedBodies() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(get("/api/v1/index")).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false));
        mvc.perform(get("/api/v1/search").param("q", "lucene")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/index").contentType("application/json")
            .content("[{\"text\":\"Lucene học bổng\"}]".getBytes(StandardCharsets.UTF_8)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.documentCount").value(1));
        mvc.perform(get("/api/v1/search").param("q", "HỌC")).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.results[0].score").isNumber());
        mvc.perform(post("/api/v1/index").contentType("application/json").content("oops"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("INVALID_CORPUS"));
        mvc.perform(post("/api/v1/index").contentType("text/plain").content("[]"))
            .andExpect(status().isUnsupportedMediaType());
        for (String model : new String[]{"classic", "cosine", "bm25", "lm"})
            mvc.perform(get("/api/v1/search").param("q", "học").param("model", model).param("operator", "and"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.model").value(model))
                .andExpect(jsonPath("$.options.operator").value("and"));
        for (String[] option : new String[][]{{"model", "unknown"}, {"operator", "not"}, {"k1", "-1"},
                {"k1", "NaN"}, {"k1", "Infinity"}, {"b", "1.1"}, {"b", "NaN"}, {"lambda", "0"},
                {"lambda", "1"}, {"lambda", "NaN"}, {"k1", "oops"}})
            mvc.perform(get("/api/v1/search").param("q", "học").param(option[0], option[1]))
                .andExpect(status().isBadRequest());
        byte[] tooLarge = new byte[SearchController.MAX_CORPUS_BYTES + 1];
        mvc.perform(post("/api/v1/index").contentType("application/json").content(tooLarge))
            .andExpect(status().is(413));
        mvc.perform(post("/api/v1/index").contentType("application/json").content(tooLarge)
            .with(request -> new org.springframework.mock.web.MockHttpServletRequest() {
                { setMethod("POST"); setRequestURI("/api/v1/index"); setContentType("application/json"); setContent(tooLarge); }
                @Override public long getContentLengthLong() { return -1; }
                @Override public int getContentLength() { return -1; }
            })).andExpect(status().is(413));
        mvc.perform(get("/api/v1/search").param("q", "lucene").param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", "lucene").param("limit", "no")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/index")).andExpect(jsonPath("$.documentCount").value(1));
    }
}
