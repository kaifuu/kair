package com.wrj.platform.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HLS 视频流代理:浏览器直连公网摄像头 m3u8 通常被 CORS 拦截,
 * 这里由服务端拉流转发:播放列表内的相对/绝对分片地址统一改写为再走本代理,
 * 相对地址按「重定向后」的最终 URL 解析(部分源 302 时携带临时 token)。
 * 需登录态(Bearer),仅允许 http/https。
 */
@RestController
@RequestMapping("/api/video")
public class VideoProxyController {

    private static final Logger log = LoggerFactory.getLogger(VideoProxyController.class);

    private static final String PROXY_PATH = "/api/video/proxy?url=";
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** 直连(不走系统代理,规避本机 VPN/代理对公网摄像头源的干扰) */
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(6))
            .proxy(java.net.ProxySelector.of(null))
            .build();

    @GetMapping("/proxy")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> proxy(
            @RequestParam("url") String url, jakarta.servlet.http.HttpServletRequest request) {
        URI target = checkUrl(url);
        // 本次请求通过鉴权的令牌(header 或参数):改写子播放列表/分片时原样透传,
        // 避免 hls.js 后续请求丢失鉴权(原生 video/特殊环境带不了请求头)导致 401
        String auth = request.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7)
                : request.getParameter("token");
        try {
            HttpRequest req = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                log.warn("Video proxy upstream {} -> HTTP {}", target, resp.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(null);
            }
            URI effective = resp.uri() == null ? target : resp.uri();   // 302 后的最终地址
            String contentType = resp.headers().firstValue("Content-Type").orElse("");
            boolean playlist = contentType.contains("mpegurl")
                    || effective.getRawPath().endsWith(".m3u8")
                    || target.getRawPath().endsWith(".m3u8");

            if (playlist) {
                String body = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                        .body(new org.springframework.core.io.InputStreamResource(
                                new java.io.ByteArrayInputStream(rewrite(body, effective, token).getBytes(StandardCharsets.UTF_8))));
            }
            // 分片(ts/fmp4):原样转发字节流
            MediaType mt = contentType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(mt)
                    .body(new org.springframework.core.io.InputStreamResource(resp.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(null);
        } catch (Exception e) {
            log.warn("Video proxy error ({}): {}", target, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(null);
        }
    }

    /** 只允许 http/https 绝对地址,且长度受限 */
    private URI checkUrl(String url) {
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw new IllegalArgumentException("无效的视频流地址");
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
            throw new IllegalArgumentException("仅支持 http/https 视频流地址");
        }
        return uri;
    }

    /**
     * 改写播放列表:
     * - 非注释行(分片/子播放列表地址)→ 相对 effective 解析为绝对 → 代理地址
     * - 注释行内 URI="..." 属性(密钥/初始化段/备用流)同样改写
     * - 每个改写地址追加 &token=,让整条拉流链不依赖请求头鉴权
     */
    private String rewrite(String body, URI effective, String token) {
        StringBuilder out = new StringBuilder(body.length() + 512);
        for (String line : body.split("\r?\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                out.append(line);
            } else if (trimmed.startsWith("#")) {
                out.append(rewriteAttrUris(line, effective, token));
            } else {
                out.append(PROXY_PATH).append(enc(resolve(effective, trimmed))).append(suffix(token));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private String rewriteAttrUris(String line, URI base, String token) {
        Matcher m = URI_ATTR.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String abs = resolve(base, m.group(1));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    "URI=\"" + PROXY_PATH + enc(abs) + suffix(token) + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String suffix(String token) {
        return token == null || token.isBlank() ? "" : "&token=" + enc(token);
    }

    private String resolve(URI base, String ref) {
        try {
            return base.resolve(ref).toString();
        } catch (Exception e) {
            return ref;
        }
    }

    private String enc(String url) {
        return UriUtils.encodeQueryParam(url, StandardCharsets.UTF_8);
    }
}
