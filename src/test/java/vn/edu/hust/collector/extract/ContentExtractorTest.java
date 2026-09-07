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
        assertThat(result.links()).isEmpty();
    }
    @Test void extractsArticleLinksWithAbsoluteUrlsAndDescriptions() throws Exception {
        String html="""
            <html><head><base href="https://hust.edu.vn/news/"></head><body>
            <main><nav><a href="/menu">Menu</a></nav><a href="/sidebar">Sidebar</a>
            <article><h1>Title</h1><div class="entry-content"><p>%s</p>
            <a href="../files/a.pdf"> Read   document </a>
            <a href="//example.org/info?q=1&amp;x=2">External source</a>
            <a href="../files/a.pdf">Read document</a>
            <a href="../files/a.pdf">Another description</a>
            <a href="/image"><img alt="Image description" src="a.png"></a>
            <figure><img src="../images/campus.jpg"><figcaption>HUST campus</figcaption></figure>
            <img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP" data-src="/images/lazy.jpg" title="Lazy image">
            <img src="/images/no-description.png">
            <a href="/accessible" aria-label="Accessible description"></a>
            <a href="/title" title="Title description"></a><a href="/empty"></a>
            <div class="share-icons"><a href="https://facebook.com/sharer.php?u=test">Share</a></div>
            <a href="#section">Section</a><a href="mailto:a@example.org">Email</a>
            <a href="javascript:void(0)">Action</a><a href="">Empty target</a>
            </div></article></main><footer><a href="/footer">Footer</a></footer></body></html>
            """.formatted("Article text. ".repeat(50));
        var result=extractor.extract(html.getBytes(StandardCharsets.UTF_8),"https://hust.edu.vn/news/page.html","text/html");
        assertThat(result.keep()).isTrue();
        assertThat(result.text()).doesNotContain("Sidebar", "Menu", "Footer");
        assertThat(result.links()).containsExactly(
            new ContentExtractor.Link("https://hust.edu.vn/files/a.pdf","Read document"),
            new ContentExtractor.Link("https://example.org/info?q=1&x=2","External source"),
            new ContentExtractor.Link("https://hust.edu.vn/files/a.pdf","Another description"),
            new ContentExtractor.Link("https://hust.edu.vn/image","Image description"),
            new ContentExtractor.Link("https://hust.edu.vn/news/a.png","Image description"),
            new ContentExtractor.Link("https://hust.edu.vn/images/campus.jpg","HUST campus"),
            new ContentExtractor.Link("https://hust.edu.vn/images/lazy.jpg","Lazy image"),
            new ContentExtractor.Link("https://hust.edu.vn/images/no-description.png",""),
            new ContentExtractor.Link("https://hust.edu.vn/accessible","Accessible description"),
            new ContentExtractor.Link("https://hust.edu.vn/title","Title description"),
            new ContentExtractor.Link("https://hust.edu.vn/empty",""));
    }
    @Test void readsOfficeDocument() throws Exception {
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(); var bytes = new java.io.ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Học bổng Bách khoa Hà Nội"); doc.write(bytes);
            var result=extractor.extract(bytes.toByteArray(),"https://hust.edu.vn/a.docx","application/octet-stream");
            assertThat(result.keep()).isTrue(); assertThat(result.text()).contains("Học bổng");
        }
    }
}
