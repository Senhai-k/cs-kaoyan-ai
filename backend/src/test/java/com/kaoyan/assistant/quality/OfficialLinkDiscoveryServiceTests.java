package com.kaoyan.assistant.quality;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfficialLinkDiscoveryServiceTests {

    private static final DataCollectionTarget RETEST_TARGET = new DataCollectionTarget(
            13L, 5L, "2026年复试分数线", "复试分数线", 2026,
            "https://yzb.example.edu.cn/", "PENDING", null, true, "", ""
    );

    @Test
    void rejectsEntriesThatResolveToPrivateAddressesBeforeRequesting() throws Exception {
        OfficialLinkDiscoveryService service = new OfficialLinkDiscoveryService(
                uri -> { throw new AssertionError("transport must not be called"); },
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")}
        );

        assertThatThrownBy(() -> service.discover("https://yzb.example.edu.cn/", RETEST_TARGET))
                .isInstanceOf(OfficialLinkDiscoveryService.DiscoveryException.class)
                .hasMessageContaining("private or reserved");
    }

    @Test
    void allowsFakeIpBenchmarkRangeOnlyWhenExplicitlyEnabled() throws Exception {
        byte[] body = "<a href='/info/2026/retest-score.html'>2026年复试分数线</a>"
                .getBytes(StandardCharsets.UTF_8);
        OfficialLinkDiscoveryService disabled = new OfficialLinkDiscoveryService(
                uri -> new OfficialLinkDiscoveryService.PageResponse(
                        200, "text/html", null, body.length, new ByteArrayInputStream(body)
                ),
                host -> new InetAddress[]{InetAddress.getByName("198.18.0.10")}
        );
        OfficialLinkDiscoveryService enabled = new OfficialLinkDiscoveryService(
                uri -> new OfficialLinkDiscoveryService.PageResponse(
                        200, "text/html", null, body.length, new ByteArrayInputStream(body)
                ),
                host -> new InetAddress[]{InetAddress.getByName("198.18.0.10")},
                true
        );

        assertThatThrownBy(() -> disabled.discover("https://yzb.example.edu.cn/", RETEST_TARGET))
                .hasMessageContaining("private or reserved");
        assertThat(enabled.discover("https://yzb.example.edu.cn/", RETEST_TARGET))
                .extracting(OfficialLinkCandidate::sourceUrl)
                .containsExactly("https://yzb.example.edu.cn/info/2026/retest-score.html");
    }

    @Test
    void parsesHomepageFiltersCrossDomainAndHomepageLinksAndDeduplicatesCandidates() throws Exception {
        String html = """
                <!doctype html><html><body>
                <a href="/">首页</a>
                <a href="https://news.other.edu.cn/2026/retest.html">外站复试线</a>
                <a href="/post/2026-score.html#notice">2026年硕士研究生复试基本分数线</a>
                <a href="https://yzb.example.edu.cn/post/2026-score.html">重复链接</a>
                <a href="/post/2026-news.html">2026年硕士招生通知</a>
                <a href="/assets/banner.jpg">招生图片</a>
                </body></html>
                """;
        OfficialLinkDiscoveryService service = serviceWithHtml(html);

        List<OfficialLinkCandidate> candidates = service.discover(
                "https://yzb.example.edu.cn/", RETEST_TARGET
        );

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).sourceUrl()).isEqualTo("https://yzb.example.edu.cn/post/2026-score.html");
        assertThat(candidates.get(0).matchedKeywords()).contains("2026", "基本分数线");
        assertThat(candidates).extracting(OfficialLinkCandidate::sourceUrl)
                .doesNotContain("https://yzb.example.edu.cn/", "https://news.other.edu.cn/2026/retest.html");
    }

    @Test
    void keepsOnlyExactTargetYearCandidates() throws Exception {
        String html = """
                <html><body>
                <a href="/notice/master-admission.html">硕士研究生招生通知</a>
                <a href="/info/2026/retest-score.html">2026年复试分数线</a>
                <a href="/info/2025/retest-score.html">2025年复试分数线</a>
                </body></html>
                """;
        OfficialLinkDiscoveryService service = serviceWithHtml(html);

        List<OfficialLinkCandidate> candidates = service.discover(
                "https://yzb.example.edu.cn/", RETEST_TARGET
        );

        assertThat(candidates).extracting(OfficialLinkCandidate::title)
                .containsExactly("2026年复试分数线");
    }

    @Test
    void followsBoundedSameHostNavigationAndRejectsDoctoralResults() throws Exception {
        byte[] homepage = """
                <a href="/retest/list.htm">复试录取</a>
                <a href="/info/2026/phd.html">2026年博士拟录取名单</a>
                """.getBytes(StandardCharsets.UTF_8);
        byte[] listing = """
                <a href="/info/2026/master-rule.html">2026年硕士研究生复试录取工作办法</a>
                <a href="/info/2026/phd-rule.html">2026年博士研究生复试录取办法</a>
                """.getBytes(StandardCharsets.UTF_8);
        OfficialLinkDiscoveryService service = new OfficialLinkDiscoveryService(
                uri -> {
                    byte[] body = uri.getPath().contains("/retest/") ? listing : homepage;
                    return new OfficialLinkDiscoveryService.PageResponse(
                            200, "text/html", null, body.length, new ByteArrayInputStream(body)
                    );
                },
                publicResolver()
        );
        DataCollectionTarget ruleTarget = new DataCollectionTarget(
                14L, 5L, "2026年复试录取细则", "复试录取细则", 2026,
                "https://yzb.example.edu.cn/", "PENDING", null, true, "", ""
        );

        assertThat(service.discover("https://yzb.example.edu.cn/", ruleTarget))
                .extracting(OfficialLinkCandidate::sourceUrl)
                .containsExactly("https://yzb.example.edu.cn/info/2026/master-rule.html");
    }

    @Test
    void rejectsCrossDomainRedirectsAndUnsafeAcceptedCandidates() throws Exception {
        Queue<OfficialLinkDiscoveryService.PageResponse> responses = new ArrayDeque<>();
        responses.add(new OfficialLinkDiscoveryService.PageResponse(
                302, "text/html", "https://other.edu.cn/notices", 0, new ByteArrayInputStream(new byte[0])
        ));
        OfficialLinkDiscoveryService service = new OfficialLinkDiscoveryService(
                uri -> responses.remove(), publicResolver()
        );

        assertThatThrownBy(() -> service.discover("https://yzb.example.edu.cn/", RETEST_TARGET))
                .hasMessageContaining("cross-domain");
        assertThatThrownBy(() -> service.validateAcceptedCandidate(
                "https://yzb.example.edu.cn/", "https://other.edu.cn/post/1"
        )).hasMessageContaining("registered official host");
        assertThatThrownBy(() -> service.validateAcceptedCandidate(
                "https://yzb.example.edu.cn/", "https://yzb.example.edu.cn/"
        )).hasMessageContaining("exact page URL");
    }

    private OfficialLinkDiscoveryService serviceWithHtml(String html) throws Exception {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        return new OfficialLinkDiscoveryService(
                uri -> new OfficialLinkDiscoveryService.PageResponse(
                        200, "text/html; charset=utf-8", null, body.length, new ByteArrayInputStream(body)
                ),
                publicResolver()
        );
    }

    private OfficialLinkDiscoveryService.HostAddressResolver publicResolver() throws Exception {
        InetAddress address = InetAddress.getByName("93.184.216.34");
        return host -> new InetAddress[]{address};
    }
}
