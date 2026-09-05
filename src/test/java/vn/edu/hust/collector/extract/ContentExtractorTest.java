package vn.edu.hust.collector.extract;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContentExtractorTest {
    private final ContentExtractor extractor = new ContentExtractor();
    @Test void keepsArticleButRemovesNavigation() throws Exception {
        String html="<html><head><meta charset='utf-8'><title>HUST</title></head><body><nav>Menu noise</nav><article><h1>Học bổng</h1><p>" +
            "Thông báo học bổng dành cho sinh viên Đại học Bách khoa Hà Nội. ".repeat(5) + "</p><a href='/a.pdf'>Tài liệu</a></article></body></html>";
        var result=extractor.extract(html.getBytes(StandardCharsets.UTF_8),"https://hust.edu.vn/news","text/html");
        assertThat(result.keep()).isTrue(); assertThat(result.title()).isEqualTo("Học bổng");
        assertThat(result.text()).doesNotContain("Menu noise");
    }
    @Test void rejectsLoginEvenWithHttpSuccess() throws Exception {
        var result=extractor.extract("<html><input type='password'></html>".getBytes(),"https://hust.edu.vn/","text/html");
        assertThat(result.keep()).isFalse(); assertThat(result.reason()).isEqualTo("LOGIN_REQUIRED");
    }
    @Test void readsOfficeDocument() throws Exception {
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(); var bytes = new java.io.ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Học bổng Bách khoa Hà Nội"); doc.write(bytes);
            var result=extractor.extract(bytes.toByteArray(),"https://hust.edu.vn/a.docx","application/octet-stream");
            assertThat(result.keep()).isTrue(); assertThat(result.text()).contains("Học bổng");
        }
    }
}
