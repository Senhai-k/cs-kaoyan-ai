package com.kaoyan.assistant.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Component
public class ControlledWebFetcher {

    private static final int MAX_REDIRECTS = 4;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient client;
    private final boolean allowFakeIpRange;

    @Autowired
    public ControlledWebFetcher(
            @Value("${app.official-link-discovery.allow-fake-ip:false}") boolean allowFakeIpRange) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), allowFakeIpRange);
    }

    ControlledWebFetcher() {
        this(false);
    }

    ControlledWebFetcher(HttpClient client) {
        this(client, false);
    }

    ControlledWebFetcher(HttpClient client, boolean allowFakeIpRange) {
        this.client = client;
        this.allowFakeIpRange = allowFakeIpRange;
    }

    public FetchedContent fetch(String sourceUrl) {
        URI initial = validatePublicArticleUri(sourceUrl);
        URI current = initial;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validateResolvedAddresses(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "cs-kaoyan-ai-controlled-fetcher/1.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/pdf,text/plain;q=0.9")
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new WebFetchException("web capture was interrupted", current.toString(), null, null);
            } catch (IOException ex) {
                throw new WebFetchException("failed to fetch official page", current.toString(), null, null);
            }
            int status = response.statusCode();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                if (redirect == MAX_REDIRECTS) {
                    throw new WebFetchException("too many redirects", current.toString(), status, contentType);
                }
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    throw new WebFetchException(
                            "redirect response has no location", current.toString(), status, contentType
                    );
                }
                URI next = validatePublicArticleUri(current.resolve(location).toString());
                if (!sameOfficialHost(initial.getHost(), next.getHost())) {
                    throw new WebFetchException(
                            "cross-domain redirects are not allowed", next.toString(), status, contentType
                    );
                }
                current = next;
                continue;
            }
            if (status < 200 || status >= 300) {
                closeQuietly(response.body());
                throw new WebFetchException("official page returned HTTP " + status,
                        current.toString(), status, contentType);
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > MAX_RESPONSE_BYTES) {
                closeQuietly(response.body());
                throw new WebFetchException("web response exceeds 5 MB", current.toString(), status, contentType);
            }
            byte[] bytes = readLimited(response.body(), current, status, contentType);
            return extract(current, status, contentType, bytes);
        }
        throw new WebFetchException("web capture failed", current.toString(), null, null);
    }

    URI validatePublicArticleUri(String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            throw new WebFetchException("official URL is invalid", value, null, null);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.toString().length() > 500) {
            throw new WebFetchException("official URL exceeds 500 characters", value, null, null);
        }
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new WebFetchException("only public HTTP(S) URLs are allowed", value, null, null);
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new WebFetchException("non-standard URL ports are not allowed", value, null, null);
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new WebFetchException("local network hosts are not allowed", value, null, null);
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ((path.isBlank() || "/".equals(path)) && (uri.getQuery() == null || uri.getQuery().isBlank())) {
            throw new WebFetchException("replace the site homepage with an exact official article URL", value, null, null);
        }
        return uri;
    }

    private void validateResolvedAddresses(URI uri) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                throw new WebFetchException("official host has no DNS address", uri.toString(), null, null);
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new WebFetchException("official URL resolves to a private or reserved address",
                            uri.toString(), null, null);
                }
            }
        } catch (IOException ex) {
            throw new WebFetchException("failed to resolve official host", uri.toString(), null, null);
        }
    }

    boolean isPublicAddress(InetAddress address) {
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

    private boolean sameOfficialHost(String first, String second) {
        String left = first.toLowerCase(Locale.ROOT);
        String right = second.toLowerCase(Locale.ROOT);
        return left.equals(right) || left.endsWith("." + right) || right.endsWith("." + left);
    }

    private byte[] readLimited(InputStream body, URI uri, int status, String contentType) {
        try (body) {
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new WebFetchException("web response exceeds 5 MB", uri.toString(), status, contentType);
            }
            return bytes;
        } catch (IOException ex) {
            throw new WebFetchException("failed to read official page", uri.toString(), status, contentType);
        }
    }

    FetchedContent extract(URI uri, int status, String contentType, byte[] bytes) {
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        try {
            if (normalizedType.contains("application/pdf") || startsWithPdf(bytes)) {
                try (PDDocument pdf = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    String text = normalizeText(stripper.getText(pdf));
                    if (text.isBlank()) {
                        throw new WebFetchException("PDF contains no text layer; OCR is required",
                                uri.toString(), status, contentType);
                    }
                    return new FetchedContent(uri.toString(), status, contentType, (long) bytes.length, "", text);
                }
            }
            if (normalizedType.contains("text/html") || normalizedType.contains("application/xhtml+xml")
                    || looksLikeHtml(bytes)) {
                Document document = Jsoup.parse(new ByteArrayInputStream(bytes), null, uri.toString());
                Element content = document.selectFirst(
                        "main,article,#content,.content,.article-content,.v_news_content,.wp_articlecontent,"
                                + ".news-content,.news_content,.TRS_Editor,.mce-content-body"
                );
                if (content == null) {
                    document.select("script,style,noscript,svg,nav,footer,header,form").remove();
                    content = document.body();
                } else {
                    content.select("script,style,noscript,svg,nav,footer,header").remove();
                }
                String text = normalizeText(content.wholeText());
                if (text.length() < 30) {
                    throw new WebFetchException("official page has too little extractable text",
                            uri.toString(), status, contentType);
                }
                return new FetchedContent(uri.toString(), status, contentType, (long) bytes.length,
                        normalizeText(document.title()), text);
            }
            if (normalizedType.contains("text/plain")) {
                String text = normalizeText(new String(bytes, charsetFrom(contentType)));
                if (text.length() < 30) {
                    throw new WebFetchException("official text response is empty or too short",
                            uri.toString(), status, contentType);
                }
                return new FetchedContent(uri.toString(), status, contentType, (long) bytes.length, "", text);
            }
        } catch (IOException ex) {
            throw new WebFetchException("failed to parse official content", uri.toString(), status, contentType);
        }
        throw new WebFetchException("only HTML, plain text and PDF content are supported",
                uri.toString(), status, contentType);
    }

    private Charset charsetFrom(String contentType) {
        for (String part : contentType.split(";")) {
            String value = part.trim();
            if (value.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                try {
                    return Charset.forName(value.substring("charset=".length()).replace("\"", ""));
                } catch (Exception ignored) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private boolean startsWithPdf(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private boolean looksLikeHtml(byte[] bytes) {
        String prefix = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.US_ASCII)
                .toLowerCase(Locale.ROOT);
        return prefix.contains("<!doctype html") || prefix.contains("<html");
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace("\uFEFF", "")
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    public record FetchedContent(String finalUrl, Integer httpStatus, String contentType,
                                 Long responseSize, String title, String rawText) {
    }

    public static class WebFetchException extends IllegalArgumentException {
        private final String finalUrl;
        private final Integer httpStatus;
        private final String contentType;

        public WebFetchException(String message, String finalUrl, Integer httpStatus, String contentType) {
            super(message);
            this.finalUrl = finalUrl;
            this.httpStatus = httpStatus;
            this.contentType = contentType;
        }

        public String finalUrl() { return finalUrl; }
        public Integer httpStatus() { return httpStatus; }
        public String contentType() { return contentType; }
    }
}
