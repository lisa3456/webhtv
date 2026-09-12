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
        Rule rule = AdRuleMatcher.findRule(url);
        if (rule == null) return fetch(url);

        String content = fetch(url);
        if (TextUtils.isEmpty(content)) return content;

        // 1. 先补全 TS 相对路径
        content = rewriteTsUrls(content, url);

        // 2. 应用 regex
        for (String r : rule.getRegex()) {
            if (TextUtils.isEmpty(r)) continue;
            try {
                content = Pattern.compile(r, Pattern.DOTALL)
                        .matcher(content)
                        .replaceAll("");
            } catch (Exception ignored) {
                // 跳过非法正则
            }
        }

        return content;
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

    private String rewriteTsUrls(String m3u8, String baseUrl) {
        String base = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
        Uri u = Uri.parse(baseUrl);
        StringBuilder sb = new StringBuilder();
        for (String line : m3u8.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()
                    && !trimmed.startsWith("#")
                    && (trimmed.contains(".ts") || trimmed.contains(".m4s"))) {
                if (trimmed.startsWith("http")) {
                    sb.append(trimmed);
                } else if (trimmed.startsWith("/")) {
                    sb.append(u.getScheme()).append("://")
                            .append(u.getHost()).append(trimmed);
                } else {
                    sb.append(base).append(trimmed);
                }
            } else {
                sb.append(line);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}