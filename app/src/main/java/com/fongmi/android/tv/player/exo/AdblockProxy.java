package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Rule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class AdblockProxy extends NanoHTTPD {

    private static final boolean FORCE_PROXY_SEGMENTS = false;

    // ===== 过滤结果记录 =====
    public static class FilterResult {
        public int removedSegments = 0;
        public double removedSeconds = 0;
        public String ruleName = "";
        public boolean applied = false;
    }

    private static volatile FilterResult lastResult = new FilterResult();

    public static FilterResult getLastResult() {
        return lastResult;
    }
    // ========================

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

        content = rewriteRefs(content, url);

        Rule rule = AdRuleMatcher.findRule(url);
        if (rule != null) {
            FilterResult result = new FilterResult();
            result.ruleName = rule.getName();
            result.applied = true;
            content = applyRule(content, rule, result);
            lastResult = result;
        } else {
            lastResult = new FilterResult();
        }

        return content;
    }

    private String applyRule(String content, Rule rule, FilterResult result) {
        List<String> patterns = new ArrayList<>();
        List<Double> durationTargets = new ArrayList<>();

        for (String r : rule.getRegex()) {
            if (TextUtils.isEmpty(r)) continue;
            if (r.matches("\\d+")) {
                durationTargets.add(Double.parseDouble(r));
            } else {
                patterns.add(r);
            }
        }

        boolean hadEndList = content.contains("#EXT-X-ENDLIST");

        // 先按时长过滤（此时 #EXT-X-DISCONTINUITY 还在）
        if (!durationTargets.isEmpty()) {
            content = filterByDuration(content, durationTargets, result);
        }

        // 再应用完整 regex
        for (String p : patterns) {
            try {
                content = Pattern.compile(p).matcher(content).replaceAll("");
            } catch (Exception ignored) {
            }
        }

        // 补回被 regex 删掉的 #EXT-X-ENDLIST
        if (hadEndList && !content.contains("#EXT-X-ENDLIST")) {
            content = content.trim() + "\n#EXT-X-ENDLIST\n";
        }

        return content;
    }

    private String filterByDuration(String m3u8, List<Double> targets, FilterResult result) {
        String[] lines = m3u8.split("\n", -1);

        List<int[]> segments = new ArrayList<>();
        int lastStart = 0;
        int endListIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("#EXT-X-DISCONTINUITY")) {
                if (i > lastStart) segments.add(new int[]{lastStart, i});
                lastStart = i;
            }
            if (lines[i].trim().startsWith("#EXT-X-ENDLIST")) {
                if (i > lastStart) segments.add(new int[]{lastStart, i});
                endListIndex = i;
                lastStart = -1;
                break;
            }
        }
        if (lastStart >= 0 && lastStart < lines.length) {
            segments.add(new int[]{lastStart, lines.length});
        }

        StringBuilder out = new StringBuilder();
        for (int[] seg : segments) {
            double totalSeconds = 0;
            for (int i = seg[0]; i < seg[1]; i++) {
                String t = lines[i].trim();
                if (t.startsWith("#EXTINF:")) {
                    int comma = t.indexOf(',');
                    String num = comma > 0
                            ? t.substring(8, comma).trim()
                            : t.substring(8).trim();
                    try {
                        totalSeconds += Double.parseDouble(num);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!isAdDuration(totalSeconds, targets)) {
                for (int i = seg[0]; i < seg[1]; i++) {
                    out.append(lines[i]).append('\n');
                }
            } else {
                result.removedSegments++;
                result.removedSeconds += totalSeconds;
            }
        }

        // 补回 #EXT-X-ENDLIST
        if (endListIndex >= 0) {
            out.append(lines[endListIndex]).append('\n');
        }

        return out.toString();
    }

    private boolean isAdDuration(double totalSeconds, List<Double> targets) {
        for (double t : targets) {
            if (totalSeconds >= t && totalSeconds < t + 1.0) return true;
        }
        return false;
    }

    private String rewriteRefs(String m3u8, String baseUrl) {
        int port = getListeningPort();
        Uri u = Uri.parse(baseUrl);
        String base = baseUrl;
        int q = base.indexOf('?');
        if (q >= 0) base = base.substring(0, q);
        base = base.substring(0, base.lastIndexOf('/') + 1);

        boolean encrypted = m3u8.contains("#EXT-X-KEY");

        StringBuilder sb = new StringBuilder();
        for (String line : m3u8.split("\n")) {
            String trimmed = line.trim();

            // 1. 优先处理带 URI 属性的标签
            if (trimmed.startsWith("#EXT-X-KEY")
                    || trimmed.startsWith("#EXT-X-MAP")
                    || trimmed.startsWith("#EXT-X-MEDIA")
                    || trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF")) {
                sb.append(rewriteUriAttribute(line, u, base, port)).append('\n');
                continue;
            }

            // 2. 其它注释行原样输出
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                sb.append(line).append('\n');
                continue;
            }

            // 3. URL 行
            boolean isPlaylist = trimmed.contains(".m3u8");
            boolean isSegment = trimmed.contains(".ts")
                    || trimmed.contains(".m4s")
                    || trimmed.contains(".aac")
                    || trimmed.contains(".mp4");
            boolean isKey = trimmed.endsWith(".key");

            if (!isPlaylist && !isSegment && !isKey) {
                sb.append(line).append('\n');
                continue;
            }

            String full = resolveFull(trimmed, u, base);
            boolean proxy = isPlaylist || isKey || FORCE_PROXY_SEGMENTS
                    || (isSegment && encrypted);

            if (proxy) {
                sb.append("http://127.0.0.1:").append(port)
                        .append("/?url=").append(Uri.encode(full)).append('\n');
            } else {
                sb.append(full).append('\n');
            }
        }
        return sb.toString();
    }

    /** 重写 URI="..." 属性，例如 #EXT-X-KEY 和 #EXT-X-MAP */
    private String rewriteUriAttribute(String line, Uri u, String base, int port) {
        int idx = line.indexOf("URI=\"");
        if (idx < 0) return line;
        int start = idx + 5;
        int end = line.indexOf('"', start);
        if (end < 0) return line;
        String uri = line.substring(start, end);
        String full = resolveFull(uri, u, base);
        String proxied = "http://127.0.0.1:" + port + "/?url=" + Uri.encode(full);
        return line.substring(0, start) + proxied + line.substring(end);
    }

    /** 统一解析相对 / 绝对 / 协议相对路径 */
    private String resolveFull(String url, Uri u, String base) {
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return u.getScheme() + ":" + url;
        if (url.startsWith("/")) return u.getScheme() + "://" + u.getHost() + url;
        return base + url;
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