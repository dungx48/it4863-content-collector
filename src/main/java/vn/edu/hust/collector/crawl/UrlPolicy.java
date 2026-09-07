package vn.edu.hust.collector.crawl;

import java.net.*;
import java.util.*;

/** Chuẩn hóa URL và chặn các đích ngoài phạm vi hoặc không công khai. */
public class UrlPolicy {
    private final String allowedDomain;

    /** Tạo policy giới hạn trong domain gốc đã cho. */
    public UrlPolicy(String allowedDomain) { this.allowedDomain = allowedDomain.toLowerCase(Locale.ROOT); }

    /** Chuẩn hóa scheme/host/path và loại tracking query cùng fragment. */
    public String normalize(String value) {
        String input = value.strip();
        if (input.endsWith(",")) input = input.substring(0, input.length() - 1); // N06 list delimiter
        URI uri = URI.create(input).normalize();
        String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("URL must be HTTP(S) without credentials");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port != -1 && port != (scheme.equals("https") ? 443 : 80)) throw new IllegalArgumentException("Unsupported port");
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = uri.getRawQuery();
        if (query != null) query = String.join("&", Arrays.stream(query.split("&"))
            .filter(p -> !p.toLowerCase(Locale.ROOT).matches("(utm_[^=]*|fbclid|gclid)=.*")).toList());
        return scheme + "://" + host + path + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    /** Kiểm tra host bằng ranh giới domain, tránh hậu tố giả như hust.edu.vn.evil. */
    public boolean inDomain(String url, String domain) {
        String host = URI.create(url).getHost();
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }
    /** Xác nhận domain crawl nằm trong miền HUST được cấu hình. */
    public String validateDomain(String input) {
        String domain = input.strip().toLowerCase(Locale.ROOT);
        if (!domain.matches("[a-z0-9]+([.-][a-z0-9]+)*") || !inDomain("https://" + domain, allowedDomain))
            throw new IllegalArgumentException("Domain must be within " + allowedDomain);
        return domain;
    }
    /** Nhận diện sớm URL đăng nhập/SSO không thuộc nội dung công khai. */
    public boolean loginUrl(String url) {
        URI uri = URI.create(url);
        return uri.getHost().startsWith("asso.") || uri.getHost().startsWith("sso.") ||
            uri.getPath().toLowerCase(Locale.ROOT).matches(".*(/login|/signin|/sign-in|/adfs)(/.*|$)");
    }
    /** Resolve DNS và từ chối địa chỉ local/private trước khi gửi request. */
    public void checkFetch(String url, String domain) throws UnknownHostException {
        if (!inDomain(url, domain) || !inDomain(url, allowedDomain)) throw new IllegalArgumentException("Outside crawl domain");
        for (InetAddress address : InetAddress.getAllByName(URI.create(url).getHost())) {
            byte[] b = address.getAddress();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() || address.isMulticastAddress() ||
                (b.length == 16 && (b[0] & 0xfe) == 0xfc) ||
                (b.length == 4 && (b[0] & 255) == 100 && ((b[1] & 255) >= 64 && (b[1] & 255) <= 127)))
                throw new IllegalArgumentException("Non-public address");
        }
    }
}
