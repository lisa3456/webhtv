package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Rule;

import java.io.IOException;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class AdblockProxy extends NanoHTTPD {

    /** 分片是否也走代理：默认 false，分片直连 CDN，性能最优。
     *  如果分片 403，改成 true。 */
    private static final boolean FORCE_PROXY_SEGMENTS = false;

    private final OkHttpClient client = new OkHttpClient();

    public AdblockProxy(int port) {
        super(port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String target = session.getParms().get("url");
        if (TextUtils.isEmpty(target)) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST,
                    "text/plain", "missing url");
        }

        try {
            if (target.contains(".m3u8")) {
                String cleaned = processM3u8(target);
                return newFixedLengthResponse(Response.Status.OK,
                        "application/vnd.apple.mpegurl", cleaned);
            }
            return proxyPass(target);
        } catch (Exception e) {
            try {
                return proxyPass(target);
            } catch (Exception ex) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                        "text/plain", "proxy error");
            }
        }
    }

    private String processM3u8(String url) throws IOException {
        String content = fetch(url);
        if (TextUtils.isEmpty(content)) return content;

        // 1. 重写引用：子 m3u8 / key 走代理，分片默认直连
        content = rewriteRefs(content, url);

        // 2. 应用 rules
        Rule rule = AdRuleMatcher.findRule(url);
        if (rule != null) {
            for (String r : rule.getRegex()) {
                if (TextUtils.isEmpty(r)) continue;
                try {
                    content = Pattern.compile(r).matcher(content).replaceAll("");
                } catch (Exception ignored) {
                }
            }
        }

        return content;
    }

    private String rewriteRefs(String m3u8, String baseUrl) {
        int port = getListeningPort();
        Uri u = Uri.parse(baseUrl);
        String base = baseUrl;
        int q = base.indexOf('?');
        if (q >= 0) base = base.substring(0, q);
        base = base.substring(0, base.lastIndexOf('/') + 1);

        StringBuilder sb = new StringBuilder();
        for (String line : m3u8.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                sb.append(line).append('\n');
                continue;
            }

            boolean isPlaylist = trimmed.contains(".m3u8");
            boolean isSegment = trimmed.contains(".ts")
                    || trimmed.contains(".m4s")
                    || trimmed.contains(".aac")
                    || trimmed.contains(".mp4");
            boolean isKey = trimmed.contains(".key");

            if (!isPlaylist && !isSegment && !isKey) {
                sb.append(line).append('\n');
                continue;
            }

            String full;
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                full = trimmed;
            } else if (trimmed.startsWith("//")) {
                full = u.getScheme() + ":" + trimmed;
            } else if (trimmed.startsWith("/")) {
                full = u.getScheme() + "://" + u.getHost() + trimmed;
            } else {
                full = base + trimmed;
            }

            if (isPlaylist || isKey || FORCE_PROXY_SEGMENTS) {
                sb.append("http://127.0.0.1:").append(port)
                        .append("/?url=").append(Uri.encode(full))
                        .append('\n');
            } else {
                sb.append(full).append('\n');
            }
        }
        return sb.toString();
    }

    private String fetch(String url) throws IOException {
        Request req = new Request.Builder().url(url).build();
        try (okhttp3.Response resp = client.newCall(req).execute()) {
            return resp.body() != null ? resp.body().string() : "";
        }
    }

    private Response proxyPass(String url) throws IOException {
        Request req = new Request.Builder().url(url).build();
        okhttp3.Response resp = client.newCall(req).execute();
        return newFixedLengthResponse(
                Response.Status.lookup(resp.code()),
                resp.header("Content-Type", "application/octet-stream"),
                resp.body() != null ? resp.body().byteStream() : null,
                resp.body() != null ? resp.body().contentLength() : -1);
    }
}