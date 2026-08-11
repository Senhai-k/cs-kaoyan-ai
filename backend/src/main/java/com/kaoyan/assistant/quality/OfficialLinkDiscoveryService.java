package com.kaoyan.assistant.quality;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class OfficialLinkDiscoveryService {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_LINKS_SCANNED = 300;
    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_NAVIGATION_PAGES = 8;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Set<String> STATIC_SUFFIXES = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".css", ".js", ".ico", ".zip", ".rar"
    );

    private final DiscoveryTransport transport;
    private final HostAddressResolver addressResolver;
    private final boolean allowFakeIpRange;

    @Autowired
    public OfficialLinkDiscoveryService(
            @Value("${app.official-link-discovery.allow-fake-ip:false}") boolean allowFakeIpRange) {
        this(new JavaHttpDiscoveryTransport(), host -> InetAddress.getAllByName(host), allowFakeIpRange);
    }

    OfficialLinkDiscoveryService(DiscoveryTransport transport, HostAddressResolver addressResolver) {
        this(transport, addressResolver, false);
    }

    OfficialLinkDiscoveryService(DiscoveryTransport transport, HostAddressResolver addressResolver,
                                 boolean allowFakeIpRange) {
        this.transport = transport;
        this.addressResolver = addressResolver;
        this.allowFakeIpRange = allowFakeIpRange;
    }

    public List<OfficialLinkCandidate> discover(String officialEntryUrl, DataCollectionTarget target) {
        if (target == null) {
            throw new DiscoveryException("collection target not found");
        }
        URI initial = validateEntryUri(officialEntryUrl);
        FetchedPage entryPage = fetchHtmlPage(initial, initial);
        Map<String, OfficialLinkCandidate> candidates = new LinkedHashMap<>();
        mergeExactCandidates(candidates, extractCandidates(entryPage.uri(), target, entryPage.body()), target);
        for (URI navigationUri : navigationUris(entryPage.uri(), target, entryPage.body())) {
            try {
                FetchedPage page = fetchHtmlPage(initial, navigationUri);
                mergeExactCandidates(candidates, extractCandidates(page.uri(), target, page.body()), target);
            } catch (DiscoveryException ignored) {
                // One inaccessible navigation page must not discard candidates from other official sections.
            }
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingInt(OfficialLinkCandidate::score).reversed()
                        .thenComparing(OfficialLinkCandidate::title)
                        .thenComparing(OfficialLinkCandidate::sourceUrl))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private FetchedPage fetchHtmlPage(URI entryUri, URI startUri) {
        URI current = startUri;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validateResolvedAddresses(current);
            PageResponse response = request(current);
            if (response.status() >= 300 && response.status() < 400) {
                closeQuietly(response.body());
                if (redirect == MAX_REDIRECTS) {
                    throw new DiscoveryException("official entry has too many redirects");
                }
                if (response.location() == null || response.location().isBlank()) {
                    throw new DiscoveryException("official entry redirect has no location");
                }
                URI next = validateEntryUri(current.resolve(response.location()).toString());
                requireSameHost(entryUri, next, "cross-domain redirects are not allowed");
                current = next;
                continue;
            }
            if (response.status() < 200 || response.status() >= 300) {
                closeQuietly(response.body());
                throw new DiscoveryException("official entry returned HTTP " + response.status());
            }
            if (!response.contentType().toLowerCase(Locale.ROOT).contains("html")) {
                closeQuietly(response.body());
                throw new DiscoveryException("official entry must return HTML");
            }
            if (response.contentLength() > MAX_RESPONSE_BYTES) {
                closeQuietly(response.body());
                throw new DiscoveryException("official entry response exceeds 1 MB");
            }
            byte[] body = readLimited(response.body());
            return new FetchedPage(current, body);
        }
        throw new DiscoveryException("official link discovery failed");
    }

    private void mergeExactCandidates(Map<String, OfficialLinkCandidate> destination,
                                      List<OfficialLinkCandidate> discovered,
                                      DataCollectionTarget target) {
        String year = String.valueOf(target.targetYear());
        for (OfficialLinkCandidate candidate : discovered) {
            String searchable = (candidate.title() + " " + candidate.sourceUrl()).toLowerCase(Locale.ROOT);
            if (!searchable.contains(year)) {
                continue;
            }
            destination.merge(candidate.sourceUrl(), candidate,
                    (left, right) -> left.score() >= right.score() ? left : right);
        }
    }

    private List<URI> navigationUris(URI pageUri, DataCollectionTarget target, byte[] html) {
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(html), null, pageUri.toString());
        } catch (IOException exception) {
            return List.of();
        }
        LinkedHashSet<URI> result = new LinkedHashSet<>();
        int scanned = 0;
        for (Element link : document.select("a[href]")) {
            if (++scanned > MAX_LINKS_SCANNED || result.size() >= MAX_NAVIGATION_PAGES) {
                break;
            }
            String title = normalizeText(link.text() + " " + link.attr("title"));
            URI uri = candidateUri(pageUri, link.attr("href"));
            if (uri == null || uri.equals(pageUri) || !isNavigationLink(title, uri, target)) {
                continue;
            }
            result.add(uri);
        }
        return List.copyOf(result);
    }

    private boolean isNavigationLink(String title, URI uri, DataCollectionTarget target) {
        String text = (title + " " + uri).toLowerCase(Locale.ROOT);
        if (containsExcludedScope(text)) {
            return false;
        }
        if (text.contains(String.valueOf(target.targetYear()))) {
            return true;
        }
        List<String> navigationKeywords = List.of(
                "硕士招生", "招生信息", "通知公告", "复试录取", "复试工作", "历年分数线",
                "复试基本分数线", "招生目录", "专业目录", "拟录取", "录取公示"
        );
        return navigationKeywords.stream().anyMatch(text::contains)
                || keywordsFor(target.documentType()).stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(text::contains);
    }

    public URI validateAcceptedCandidate(String officialEntryUrl, String candidateUrl) {
        URI entry = validateEntryUriWithoutDns(officialEntryUrl);
        URI candidate = validatePublicUri(candidateUrl, false);
        requireSameHost(entry, candidate, "candidate URL must use the registered official host");
        return candidate;
    }

    List<OfficialLinkCandidate> extractCandidates(URI pageUri, DataCollectionTarget target, byte[] html) {
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(html), null, pageUri.toString());
        } catch (IOException exception) {
            throw new DiscoveryException("failed to parse official entry HTML");
        }
        Map<String, OfficialLinkCandidate> unique = new LinkedHashMap<>();
        int scanned = 0;
        for (Element link : document.select("a[href]")) {
            if (++scanned > MAX_LINKS_SCANNED) {
                break;
            }
            URI uri = candidateUri(pageUri, link.attr("href"));
            if (uri == null || hasStaticSuffix(uri.getPath())) {
                continue;
            }
            String title = normalizeText(link.text());
            if (title.isBlank()) {
                title = normalizeText(link.attr("title"));
            }
            if (title.isBlank()) {
                title = uri.getPath();
            }
            if (title.length() > 200) {
                title = title.substring(0, 200);
            }
            RankedLink ranked = rank(title, uri, target);
            if (ranked.score() <= 0) {
                continue;
            }
            String normalizedUrl = uri.toString();
            OfficialLinkCandidate candidate = new OfficialLinkCandidate(
                    target.id(), title, normalizedUrl, ranked.score(), ranked.keywords(), pageUri.toString()
            );
            unique.merge(normalizedUrl, candidate,
                    (left, right) -> left.score() >= right.score() ? left : right);
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt(OfficialLinkCandidate::score).reversed()
                        .thenComparing(OfficialLinkCandidate::title)
                        .thenComparing(OfficialLinkCandidate::sourceUrl))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private RankedLink rank(String title, URI uri, DataCollectionTarget target) {
        String titleLower = title.toLowerCase(Locale.ROOT);
        String urlLower = uri.toString().toLowerCase(Locale.ROOT);
        if (containsExcludedScope(titleLower + " " + urlLower)) {
            return new RankedLink(0, List.of());
        }
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        int score = 0;
        boolean documentKeywordMatched = false;
        String year = String.valueOf(target.targetYear());
        if (titleLower.contains(year)) {
            score += 50;
            matched.add(year);
        } else if (urlLower.contains(year)) {
            score += 35;
            matched.add(year);
        }
        for (String keyword : keywordsFor(target.documentType())) {
            String lower = keyword.toLowerCase(Locale.ROOT);
            if (titleLower.contains(lower)) {
                score += 32;
                matched.add(keyword);
                documentKeywordMatched = true;
            } else if (urlLower.contains(lower)) {
                score += 12;
                matched.add(keyword);
                documentKeywordMatched = true;
            }
        }
        if (requiresDocumentKeyword(target.documentType()) && !documentKeywordMatched) {
            return new RankedLink(0, List.of());
        }
        if (!matched.isEmpty() && looksLikeArticle(uri.getPath())) {
            score += 8;
        }
        return new RankedLink(matched.isEmpty() ? 0 : score, List.copyOf(matched));
    }

    private List<String> keywordsFor(String documentType) {
        String type = documentType == null ? "" : documentType;
        if (type.contains("目录")) {
            return List.of("专业目录", "招生目录", "硕士目录", "招生专业");
        }
        if (type.contains("复试") && !type.contains("分数") && !type.contains("复试线")) {
            return List.of("复试录取", "复试办法", "复试方案", "复试工作");
        }
        if (type.contains("录取")) {
            return List.of("拟录取", "录取名单", "录取公示");
        }
        if (type.contains("分数") || type.contains("复试线")) {
            return List.of("复试分数线", "基本分数线", "复试线", "分数线");
        }
        return List.of(type, "硕士招生", "研究生招生").stream().filter(value -> !value.isBlank()).toList();
    }

    private boolean containsExcludedScope(String value) {
        return value.contains("博士") || value.contains("本科") || value.contains("推免")
                || value.contains("夏令营") || value.contains("强基计划");
    }

    private boolean requiresDocumentKeyword(String documentType) {
        String type = documentType == null ? "" : documentType;
        return type.contains("目录") || type.contains("录取") || type.contains("分数")
                || type.contains("复试线") || type.contains("复试");
    }

    private URI candidateUri(URI pageUri, String href) {
        if (href == null || href.isBlank() || href.startsWith("#")) {
            return null;
        }
        URI candidate;
        try {
            candidate = pageUri.resolve(href.trim());
            candidate = new URI(candidate.getScheme(), candidate.getUserInfo(), candidate.getHost(), candidate.getPort(),
                    candidate.getPath(), candidate.getQuery(), null).normalize();
            candidate = validatePublicUri(candidate.toString(), false);
            requireSameHost(pageUri, candidate, "candidate URL must use the registered official host");
            return candidate;
        } catch (DiscoveryException exception) {
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private URI validateEntryUri(String value) {
        return validateEntryUriWithoutDns(value);
    }

    private URI validateEntryUriWithoutDns(String value) {
        return validatePublicUri(value, true);
    }

    private URI validatePublicUri(String value, boolean allowHomepage) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim()).normalize();
        } catch (RuntimeException exception) {
            throw new DiscoveryException("official URL is invalid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.toString().length() > 500 || !("http".equals(scheme) || "https".equals(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new DiscoveryException("only public HTTP(S) URLs are allowed");
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new DiscoveryException("non-standard URL ports are not allowed");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new DiscoveryException("local network hosts are not allowed");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!allowHomepage && (path.isBlank() || "/".equals(path))
                && (uri.getQuery() == null || uri.getQuery().isBlank())) {
            throw new DiscoveryException("candidate must be an exact page URL, not a homepage");
        }
        return uri;
    }

    private void validateResolvedAddresses(URI uri) {
        try {
            InetAddress[] addresses = addressResolver.resolve(uri.getHost());
            if (addresses == null || addresses.length == 0) {
                throw new DiscoveryException("official host has no DNS address");
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new DiscoveryException("official entry resolves to a private or reserved address");
                }
            }
        } catch (DiscoveryException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DiscoveryException("failed to resolve official host");
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && (second == 0 || second == 168))
                    && (allowFakeIpRange || !(first == 198 && (second == 18 || second == 19)))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            int fourth = Byte.toUnsignedInt(bytes[3]);
            return (first & 0xfe) != 0xfc
                    && !(first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8);
        }
        return false;
    }

    private void requireSameHost(URI expected, URI candidate, String message) {
        if (!expected.getHost().equalsIgnoreCase(candidate.getHost())) {
            throw new DiscoveryException(message);
        }
    }

    private PageResponse request(URI uri) {
        try {
            return transport.get(uri);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DiscoveryException("official link discovery was interrupted");
        } catch (IOException exception) {
            throw new DiscoveryException("failed to read official entry");
        }
    }

    private byte[] readLimited(InputStream body) {
        try (body) {
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new DiscoveryException("official entry response exceeds 1 MB");
            }
            return bytes;
        } catch (IOException exception) {
            throw new DiscoveryException("failed to read official entry");
        }
    }

    private boolean hasStaticSuffix(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return STATIC_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    private boolean looksLikeArticle(String path) {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return value.matches(".*(?:/post/|/info/|/article/|/notice/|/\\d{4}/).*")
                || value.matches(".*\\.(?:html?|shtml|pdf)$");
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private void closeQuietly(InputStream body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException ignored) {
        }
    }

    record RankedLink(int score, List<String> keywords) {
    }

    record FetchedPage(URI uri, byte[] body) {
    }

    record PageResponse(int status, String contentType, String location, long contentLength, InputStream body) {
    }

    @FunctionalInterface
    interface DiscoveryTransport {
        PageResponse get(URI uri) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface HostAddressResolver {
        InetAddress[] resolve(String host) throws IOException;
    }

    private static final class JavaHttpDiscoveryTransport implements DiscoveryTransport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        @Override
        public PageResponse get(URI uri) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "cs-kaoyan-ai-link-discovery/1.0")
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return new PageResponse(
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.headers().firstValue("Location").orElse(null),
                    response.headers().firstValueAsLong("Content-Length").orElse(-1),
                    response.body()
            );
        }
    }

    public static class DiscoveryException extends IllegalArgumentException {
        public DiscoveryException(String message) {
            super(message);
        }
    }
}
