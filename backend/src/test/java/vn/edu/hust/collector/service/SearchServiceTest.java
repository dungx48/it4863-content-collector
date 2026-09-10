package vn.edu.hust.collector.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hust.collector.repository.LuceneIndexRepository;
import vn.edu.hust.collector.exception.ApiException;
import static org.assertj.core.api.Assertions.*;

class SearchServiceTest {
    @TempDir Path temp;
    SearchService service;
    @BeforeEach void setup() { service = new SearchService(new LuceneIndexRepository(temp.resolve("index").toString())); }
    private void index(String json) throws Exception { service.index(json.getBytes(StandardCharsets.UTF_8)); }

    @Test void classicTfIdfScoresMatchFormulaAndRankTermFrequency() throws Exception {
        index("""
            [{"id":"a","text":"lucene lucene lucene lucene"},
             {"id":"b","text":"lucene java code data"},
             {"id":"c","text":"java code data text"}]
            """);
        var response = service.search("LUCENE lucene", 10);
        assertThat(response.tokens()).containsExactly("lucene");
        assertThat(response.total()).isEqualTo(2);
        assertThat(response.results()).extracting(hit -> hit.id()).containsExactly("a", "b");
        double idf = 1 + Math.log(4.0 / 3.0);
        assertThat((double) response.results().get(0).score()).isCloseTo(idf, within(0.00001));
        assertThat((double) response.results().get(1).score()).isCloseTo(idf / 2, within(0.00001));
        assertThat(response.results().get(0).explanation()).contains("tf", "idf", "fieldNorm");
        assertThat(response.similarity()).contains("ClassicSimilarity");
    }

    @Test void persistsReplacesWithoutDuplicatesAndPreservesIndexOnInvalidImport() throws Exception {
        index("[{\"text\":\"học bổng\",\"title\":\"Tin mới\"}]");
        String time = service.status().indexedAt();
        var reopened = new SearchService(new LuceneIndexRepository(temp.resolve("index").toString()));
        assertThat(reopened.status().documentCount()).isEqualTo(1);
        assertThat(reopened.search(Normalizer.normalize("HỌC", Normalizer.Form.NFD), 10).total()).isEqualTo(1);
        assertThat(reopened.search("hoc", 10).total()).isZero();
        for (String invalid : new String[]{"[]", "{}", "null", "[{\"text\":4}]", "[{\"text\":\"!!!\"}]",
                "[{\"text\":\"ok\"},{\"title\":\"missing text\"}]", "[{\"text\":\"a\"}] []",
                "[{\"id\":\"same\",\"text\":\"a\"},{\"id\":\"same\",\"text\":\"b\"}]"}) {
            assertThatThrownBy(() -> index(invalid)).isInstanceOf(ApiException.class);
            assertThat(service.status().indexedAt()).isEqualTo(time);
            assertThat(service.search("học", 10).total()).isEqualTo(1);
        }
        index("[{\"text\":\"thực tập\"}]");
        assertThat(service.status().documentCount()).isEqualTo(1);
        assertThat(service.search("học", 10).total()).isZero();
        assertThat(service.search("thực", 10).total()).isEqualTo(1);
    }

    @Test void handlesMissingIndexEmptyQueryLimitsAndUnmatchedQuery() throws Exception {
        assertThat(service.status().ready()).isFalse();
        assertThatThrownBy(() -> service.search("hello", 10)).isInstanceOf(ApiException.class);
        index("[{\"text\":\"alpha\"},{\"text\":\"beta\"}]");
        assertThat(service.search("alpha beta", 1).total()).isEqualTo(2);
        assertThat(service.search("alpha beta", 1).results()).hasSize(1);
        assertThat(service.search("unknown", 10).results()).isEmpty();
        for (String query : new String[]{"", "   ", "!!!", "a".repeat(501)})
            assertThatThrownBy(() -> service.search(query, 10)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.search("alpha", 51)).isInstanceOf(ApiException.class);
    }

    @Test void importsExistingCollectorExport() throws Exception {
        var source = Path.of("../output/demo-n06/documents.pretty.json");
        service.index(Files.readAllBytes(source));
        assertThat(service.status().documentCount()).isEqualTo(3);
        assertThat(service.search("thực tập", 10).total()).isPositive();
    }
}
