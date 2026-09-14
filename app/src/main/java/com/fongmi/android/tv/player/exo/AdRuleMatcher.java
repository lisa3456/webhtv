package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Rule;

import java.util.List;

public class AdRuleMatcher {

    /** 当前 URL 是否命中点播配置里的 rules */
    public static boolean shouldProxy(String url) {
        return findRule(url) != null;
    }

    /** 找到命中的 Rule，没有返回 null */
    public static Rule findRule(String url) {
        if (TextUtils.isEmpty(url)) return null;
        String host = Uri.parse(url).getHost();
        if (host == null) return null;

        List<Rule> rules = VodConfig.get().getRules();
        if (rules == null || rules.isEmpty()) return null;

        for (Rule rule : rules) {
            if (matchesHost(rule, host)) return rule;
        }
        return null;
    }

    private static boolean matchesHost(Rule rule, String host) {
        List<String> hosts = rule.getHosts();
        if (hosts == null || hosts.isEmpty()) return false;
        for (String h : hosts) {
            if (TextUtils.isEmpty(h)) continue;
            if (host.contains(h)) return true;
        }
        return false;
    }

    /** 检查 URL 是否被该 Rule 的 exclude 排除 */
    public static boolean isExcluded(Rule rule, String url) {
        if (rule == null || url == null) return false;
        for (String e : rule.getExclude()) {
            if (!TextUtils.isEmpty(e) && url.contains(e)) return true;
        }
        return false;
    }
}