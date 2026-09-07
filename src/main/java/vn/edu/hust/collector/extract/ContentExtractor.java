package vn.edu.hust.collector.extract;

import java.io.*;
import java.text.Normalizer;
import java.util.*;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Bóc tách title, text, liên kết và ảnh từ HTML hoặc tài liệu hỗ trợ. */
public class ContentExtractor {
    /** Một URL đi ra và văn bản mô tả; text có thể rỗng. */
    public record Link(String url, String text) {}

    /** Kết quả bóc tách kèm quyết định giữ/bỏ và lý do. */
    public record Extracted(String title, String text, String type, List<Link> links, boolean keep, String reason) {}
    private final Tika tika = new Tika();

    /** Nhận dạng loại dữ liệu, bóc tách nội dung và áp quy tắc chất lượng. */
    public Extracted extract(byte[] bytes, String url, String mime) throws Exception {
        String detected = tika.detect(bytes, java.net.URI.create(url).getPath());
        boolean html = detected.contains("html") || mime.toLowerCase(Locale.ROOT).contains("html");
        if (html) {
            var doc = Jsoup.parse(new ByteArrayInputStream(bytes), null, url);
            if (!doc.select("input[type=password]").isEmpty()) return new Extracted(doc.title(), "", "text/html", List.of(), false, "LOGIN_REQUIRED");
            String title = doc.selectFirst("h1") == null ? doc.title() : doc.selectFirst("h1").text();
            doc.select("script,style,noscript,nav,header,footer,aside,form,iframe,.breadcrumb,.breadcrumbs,.social-share,.share-icons").remove();
            Element main = null;
            // Ưu tiên vùng bài viết cụ thể trước các container rộng như <main>.
            for (String selector : List.of("#news-bodyhtml", ".entry-content", ".elementor-widget-theme-post-content", ".news-content", ".detail-content", "article", "main", "[role=main]")) {
                main = doc.selectFirst(selector);
                if (main != null) break;
            }
            if (main == null) main = doc.body();
            List<Link> links = extractLinks(main);
            String text = clean(main.text());
            int linkLength = main.select("a").text().length();
            boolean useful = text.length() >= 120 && linkLength < text.length() * 0.55;
            String reason = useful ? "CONTENT" : (text.isBlank() ? "EMPTY_OR_JS_REQUIRED" : "SHORT_OR_LINK_LIST");
            if (title.toLowerCase(Locale.ROOT).matches(".*(404|page not found|access denied).*")) { useful = false; reason = "SOFT_ERROR"; }
            return new Extracted(clean(title), text, "text/html", links, useful, reason);
        }
        if (!(detected.equals("application/pdf") || detected.contains("officedocument") || detected.contains("msword") ||
              detected.contains("ms-excel") || detected.contains("ms-powerpoint") || detected.contains("opendocument") || detected.equals("application/rtf")))
            return new Extracted("", "", detected, List.of(), false, "UNSUPPORTED_TYPE");
        var metadata = new org.apache.tika.metadata.Metadata();
        String text = clean(tika.parseToString(new ByteArrayInputStream(bytes), metadata, 2_000_000));
        String title = metadata.get("dc:title");
        if (title == null || title.isBlank()) title = java.net.URI.create(url).getPath();
        return new Extracted(title, text, detected, List.of(), !text.isBlank(), text.isBlank() ? "EMPTY_OR_NEEDS_OCR" : "CONTENT");
    }
    /** Lấy thẻ a và nguồn ảnh theo thứ tự DOM, đồng thời loại cặp trùng. */
    private static List<Link> extractLinks(Element main) {
        var links = new LinkedHashSet<Link>();
        for (Element element : main.select("a[href], img[src], img[data-src], img[data-lazy-src], img[data-original]")) {
            if (element.tagName().equals("a")) {
                String href = element.attr("href").strip();
                if (href.isEmpty() || href.startsWith("#")) continue;
                String absolute = httpUrl(element, "href");
                if (absolute.isEmpty()) continue;
                String description = clean(element.text());
                if (description.isEmpty()) description = clean(element.attr("aria-label"));
                if (description.isEmpty()) description = clean(element.select("img[alt]").stream().map(img -> img.attr("alt")).collect(java.util.stream.Collectors.joining(" ")));
                if (description.isEmpty()) description = clean(element.attr("title"));
                links.add(new Link(absolute, description));
                continue;
            }

            String absolute = "";
            for (String attribute : List.of("data-src", "data-lazy-src", "data-original", "src")) {
                absolute = httpUrl(element, attribute);
                if (!absolute.isEmpty()) break;
            }
            if (absolute.isEmpty()) continue;
            String description = clean(element.attr("alt"));
            if (description.isEmpty()) description = figureCaption(element);
            if (description.isEmpty()) description = clean(element.attr("title"));
            links.add(new Link(absolute, description));
        }
        return List.copyOf(links);
    }

    /** Resolve thuộc tính thành URL HTTP(S) tuyệt đối; trả rỗng nếu không hợp lệ. */
    private static String httpUrl(Element element, String attribute) {
        String value = element.attr(attribute).strip();
        if (value.isEmpty() || value.startsWith("#")) return "";
        String absolute = element.absUrl(attribute);
        try {
            var uri = java.net.URI.create(absolute);
            if (uri.getHost() == null || uri.getUserInfo() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return "";
            return absolute;
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }

    /** Tìm figcaption gần nhất bao quanh ảnh. */
    private static String figureCaption(Element image) {
        for (Element ancestor = image.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (!ancestor.tagName().equals("figure")) continue;
            Element caption = ancestor.selectFirst("figcaption");
            return caption == null ? "" : clean(caption.text());
        }
        return "";
    }
    /** Chuẩn hóa Unicode NFC và gộp mọi khoảng trắng liên tiếp. */
    static String clean(String value) { return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("[\\s\\p{Z}]+", " ").strip(); }
}
