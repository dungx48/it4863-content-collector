package vn.edu.hust.collector.extract;

import java.io.*;
import java.text.Normalizer;
import java.util.*;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class ContentExtractor {
    public record Extracted(String title, String text, String type, boolean keep, String reason) {}
    private final Tika tika = new Tika();

    public Extracted extract(byte[] bytes, String url, String mime) throws Exception {
        String detected = tika.detect(bytes, java.net.URI.create(url).getPath());
        boolean html = detected.contains("html") || mime.toLowerCase(Locale.ROOT).contains("html");
        if (html) {
            var doc = Jsoup.parse(new ByteArrayInputStream(bytes), null, url);
            if (!doc.select("input[type=password]").isEmpty()) return new Extracted(doc.title(), "", "text/html", false, "LOGIN_REQUIRED");
            doc.select("script,style,noscript,nav,header,footer,aside,form,iframe,.breadcrumb,.breadcrumbs,.social-share").remove();
            Element main = doc.selectFirst("article,main,[role=main],.news-content,.detail-content,.entry-content,#news-bodyhtml");
            if (main == null) main = doc.body();
            String text = clean(main.text());
            String title = doc.selectFirst("h1") == null ? doc.title() : doc.selectFirst("h1").text();
            int linkLength = main.select("a").text().length();
            boolean useful = text.length() >= 120 && linkLength < text.length() * 0.55;
            String reason = useful ? "CONTENT" : (text.isBlank() ? "EMPTY_OR_JS_REQUIRED" : "SHORT_OR_LINK_LIST");
            if (title.toLowerCase(Locale.ROOT).matches(".*(404|page not found|access denied).*")) { useful = false; reason = "SOFT_ERROR"; }
            return new Extracted(title, text, "text/html", useful, reason);
        }
        if (!(detected.equals("application/pdf") || detected.contains("officedocument") || detected.contains("msword") ||
              detected.contains("ms-excel") || detected.contains("ms-powerpoint") || detected.contains("opendocument") || detected.equals("application/rtf")))
            return new Extracted("", "", detected, false, "UNSUPPORTED_TYPE");
        var metadata = new org.apache.tika.metadata.Metadata();
        String text = clean(tika.parseToString(new ByteArrayInputStream(bytes), metadata, 2_000_000));
        String title = metadata.get("dc:title");
        if (title == null || title.isBlank()) title = java.net.URI.create(url).getPath();
        return new Extracted(title, text, detected, !text.isBlank(), text.isBlank() ? "EMPTY_OR_NEEDS_OCR" : "CONTENT");
    }
    static String clean(String value) { return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("[\\s\\p{Z}]+", " ").strip(); }
}
