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

    private vn.edu.hust.collector.dto.SearchOptions options(String model, String operator, float k1, float b, float lambda) {
        return new vn.edu.hust.collector.dto.SearchOptions(model, operator, k1, b, lambda);
    }

    @Test void cosineUsesFullDocumentNormQueryFrequencyAndReranksBeforeTopK() throws Exception {
        index("""
            [{"id":"a","text":"alpha alpha beta noise"},
             {"id":"b","text":"beta beta beta beta"},
             {"id":"c","text":"other"}]
            """);
        var cosine = options("cosine", "or", 1.2f, .75f, .5f);
        var response = service.search("alpha alpha beta", 10, cosine);
        double wa = 1 + Math.log(2);
        double qa = wa * Math.log(3), qb = Math.log(3.0 / 2);
        double expected = (wa * qa + qb) / (Math.sqrt(wa * wa + 2) * Math.sqrt(qa * qa + qb * qb));
        assertThat(response.results().get(0).id()).isEqualTo("a");
        assertThat((double) response.results().get(0).score()).isCloseTo(expected, within(.000001));
        assertThat(response.results().get(0).explanation()).contains("lnc.ltc", "tf_d=2", "df=1", "contribution");
        assertThat(service.search("alpha alpha beta", 1, cosine).results().get(0)).isEqualTo(response.results().get(0));
        assertThat(service.search("alpha beta", 10, cosine).results().get(0).score()).isNotEqualTo(response.results().get(0).score());
        assertThat(service.search("alpha alpha beta unknown", 10, cosine).results()).extracting(hit -> hit.score())
            .containsExactlyElementsOf(response.results().stream().map(hit -> hit.score()).toList());
        assertThat(service.search("alpha beta", 10, options("cosine", "and", 1.2f, .75f, .5f)).total()).isEqualTo(1);
        assertThat(service.search("unknown", 10, cosine).results()).isEmpty();
        index("[{\"text\":\"alpha\"},{\"text\":\"alpha beta\"}]");
        assertThat(service.search("alpha", 10, cosine).results()).allSatisfy(hit -> {
            assertThat(hit.score()).isZero();
            assertThat(hit.explanation()).contains("Vector truy vấn bằng 0");
        });
    }

    @Test void bm25AndLmMatchFormulaAndParametersChangeScoresOnSameIndex() throws Exception {
        index("""
            [{"id":"a","text":"alpha alpha alpha alpha"},
             {"id":"b","text":"alpha beta gamma delta"},
             {"id":"c","text":"beta gamma delta other"}]
            """);
        String indexedAt = service.status().indexedAt();
        var bm25 = service.search("alpha", 10, options("bm25", "or", 1.2f, .75f, .5f));
        double idf = Math.log(1 + 1.5 / 2.5);
        assertThat((double) bm25.results().get(0).score()).isCloseTo(idf * 4 / (4 + 1.2), within(.000001));
        assertThat(bm25.results().get(0).explanation()).contains("k1", "b", "idf");
        var binary = service.search("alpha", 10, options("bm25", "or", 0, 0, .5f));
        assertThat(binary.results().get(0).score()).isEqualTo(binary.results().get(1).score());
        var lm = service.search("alpha", 10, options("lm", "or", 1.2f, .75f, .5f));
        double collectionProbability = 6.0 / 13;
        assertThat((double) lm.results().get(0).score()).isCloseTo(Math.log(1 + 1 / collectionProbability), within(.000001));
        assertThat(lm.results().get(0).explanation()).contains("lambda");
        var strongerDocument = service.search("alpha", 10, options("lm", "or", 1.2f, .75f, .8f));
        assertThat(strongerDocument.results().get(0).score()).isGreaterThan(lm.results().get(0).score());
        assertThat(service.status().indexedAt()).isEqualTo(indexedAt);
        for (String model : new String[]{"classic", "cosine", "bm25", "lm"}) {
            assertThat(service.search("alpha beta", 10, options(model, "and", 1.2f, .75f, .5f)).total()).isEqualTo(1);
            assertThat(service.search("alpha unknown", 10, options(model, "and", 1.2f, .75f, .5f)).total()).isZero();
        }
        index("[{\"text\":\"alpha alpha alpha alpha\"},{\"text\":\"alpha beta\"}]");
        assertThat(service.search("alpha", 10, options("bm25", "or", 1.2f, 0, .5f)).results().get(0).score())
            .isNotEqualTo(service.search("alpha", 10, options("bm25", "or", 1.2f, 1, .5f)).results().get(0).score());
    }

    @Test void cosineTopKDoesNotDiscardCandidatesUsingClassicRanking() throws Exception {
        index("""
            [{"id":"a","text":"alpha noise noise noise noise"},
             {"id":"b","text":"alpha beta gamma delta epsilon zeta"},
             {"id":"c","text":"other"}]
            """);
        assertThat(service.search("alpha", 1).results().get(0).id()).isEqualTo("a");
        var cosine = service.search("alpha", 1, options("cosine", "or", 1.2f, .75f, .5f));
        assertThat(cosine.total()).isEqualTo(2);
        assertThat(cosine.results().get(0).id()).isEqualTo("b");
        assertThat((double) cosine.results().get(0).score()).isCloseTo(1 / Math.sqrt(6), within(.000001));
    }
}
