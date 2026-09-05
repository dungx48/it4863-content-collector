package vn.edu.hust.collector.crawl;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UrlPolicyTest {
    private final UrlPolicy policy = new UrlPolicy("hust.edu.vn");
    @Test void cleansGroupListWithoutDestroyingQueryOrEscaping() {
        assertThat(policy.normalize(" https://HUST.edu.vn/a%20b?utm_source=x&page_id=46#top, "))
            .isEqualTo("https://hust.edu.vn/a%20b?page_id=46");
        assertThat(policy.normalize("https://hust.edu.vn/file.pdf,")).isEqualTo("https://hust.edu.vn/file.pdf");
    }
    @Test void enforcesDomainBoundaryAndPublicProtocol() {
        assertThat(policy.inDomain("https://soict.hust.edu.vn/a","hust.edu.vn")).isTrue();
        assertThat(policy.inDomain("https://hust.edu.vn.evil.test/a","hust.edu.vn")).isFalse();
        assertThat(policy.inDomain("https://evilhust.edu.vn/a","hust.edu.vn")).isFalse();
        assertThatThrownBy(() -> policy.normalize("file:///etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.normalize("https://user:pass@hust.edu.vn/")).isInstanceOf(IllegalArgumentException.class);
        assertThat(policy.loginUrl("https://asso.hust.edu.vn/adfs/ls/")).isTrue();
    }
}
