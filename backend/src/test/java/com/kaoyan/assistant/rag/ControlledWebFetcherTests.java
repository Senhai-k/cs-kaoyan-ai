package com.kaoyan.assistant.rag;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlledWebFetcherTests {

    private final ControlledWebFetcher fetcher = new ControlledWebFetcher();

    @Test
    void rejectsHomepagesPrivateAddressesAndNonStandardPorts() {
        assertThatThrownBy(() -> fetcher.fetch("https://example.edu.cn/"))
                .isInstanceOf(ControlledWebFetcher.WebFetchException.class)
                .hasMessageContaining("exact official article");
        assertThatThrownBy(() -> fetcher.fetch("http://127.0.0.1/private/article"))
                .isInstanceOf(ControlledWebFetcher.WebFetchException.class)
                .hasMessageContaining("private or reserved");
        assertThatThrownBy(() -> fetcher.fetch("https://example.edu.cn:8443/article"))
                .isInstanceOf(ControlledWebFetcher.WebFetchException.class)
                .hasMessageContaining("non-standard");
    }

    @Test
    void allowsFakeIpBenchmarkRangeOnlyWhenExplicitlyEnabled() throws Exception {
        ControlledWebFetcher disabled = new ControlledWebFetcher(false);
        ControlledWebFetcher enabled = new ControlledWebFetcher(true);
        InetAddress fakeIp = InetAddress.getByName("198.18.0.10");

        assertThat(disabled.isPublicAddress(fakeIp)).isFalse();
        assertThat(enabled.isPublicAddress(fakeIp)).isTrue();
        assertThat(enabled.isPublicAddress(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(enabled.isPublicAddress(InetAddress.getByName("10.0.0.8"))).isFalse();
        assertThat(enabled.isPublicAddress(InetAddress.getByName("198.51.100.8"))).isFalse();
    }

    @Test
    void extractsArticleTextAndRemovesExecutableOrNavigationContent() {
        byte[] html = """
                <!doctype html><html><head><title>2026 复试办法</title><script>bad()</script></head>
                <body><nav>无关导航</nav><main><h1>计算机学院复试办法</h1>
                <p>本办法适用于 2026 年硕士研究生复试录取工作。</p>
                <p>考生须按官方通知准备材料并参加复试。</p></main><footer>版权</footer></body></html>
                """.getBytes(StandardCharsets.UTF_8);

        ControlledWebFetcher.FetchedContent result = fetcher.extract(
                URI.create("https://example.edu.cn/admission/2026/retest.html"),
                200, "text/html; charset=utf-8", html
        );

        assertThat(result.title()).isEqualTo("2026 复试办法");
        assertThat(result.rawText()).contains("计算机学院复试办法", "准备材料")
                .doesNotContain("bad()", "无关导航", "版权");
    }

    @Test
    void extractsUniversityCmsNewsContentWithoutSurroundingPortalNavigation() {
        byte[] html = """
                <!doctype html><html><head><title>2026年复试录取工作安排</title></head>
                <body><div class="site-menu">首页 博士招生 硕士招生 信息查询</div><form name="_newscontent_fromname">
                <div class="v_news_content"><h1>2026年硕士研究生复试录取工作安排</h1>
                <p>各学院应公布复试工作实施细则，并严格执行差额复试要求。</p>
                <p>考生应以学院发布的具体安排为准。</p></div></form>
                <div class="friend-links">校内部门网站 联系我们</div></body></html>
                """.getBytes(StandardCharsets.UTF_8);

        ControlledWebFetcher.FetchedContent result = fetcher.extract(
                URI.create("https://example.edu.cn/info/1007/5734.htm"),
                200, "text/html; charset=utf-8", html
        );

        assertThat(result.rawText()).contains("复试工作实施细则", "差额复试")
                .doesNotContain("博士招生", "校内部门网站");
    }

    @Test
    void extractsRichTextEditorBodyWithoutAdmissionPortalNavigation() {
        byte[] html = """
                <!doctype html><html><head><title>硕士研究生招生复试录取工作办法</title></head>
                <body class="detail-block"><div class="portal-menu">博士招生 硕士招生 信息公示</div>
                <div class="tc-body-content"><div class="article-title">复试录取工作办法</div>
                <div class="mce-content-body"><h2>复试工作准备</h2>
                <p>各院系自主确定复试差额比例，差额比例一般不低于120%。</p>
                <p>复试成绩占总成绩的权重一般在25%至50%的范围内。</p></div></div></body></html>
                """.getBytes(StandardCharsets.UTF_8);

        ControlledWebFetcher.FetchedContent result = fetcher.extract(
                URI.create("https://example.edu.cn/post/3510"),
                200, "text/html; charset=utf-8", html
        );

        assertThat(result.rawText()).contains("差额比例", "25%至50%")
                .doesNotContain("博士招生", "信息公示");
    }
}
