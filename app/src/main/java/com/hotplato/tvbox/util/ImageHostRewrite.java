package com.hotplato.tvbox.util;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 荐片等源站封面 CDN 轮换：旧 JAR 仍输出已失效域名（如 {@code static.ztcuc.com}，NXDOMAIN），
 * 按官方发现链路刷新 {@code imgDomain}，在拉图前改写 host。
 * <p>
 * 发现算法对齐开源 CatVod {@code Jianpian}：AliDNS TXT → {@code wangerniu.<domain>} →
 * {@code /api/v2/settings/resourceDomainConfig}。
 */
public final class ImageHostRewrite {
    private static final String TAG = "ImageHostRewrite";
    private static final long REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);
    private static final String DNS_TXT_URL =
            "https://dns.alidns.com/resolve?name=swrdsfeiujo25sw.cc&type=TXT";
    private static final String JP_UA =
            "Mozilla/5.0 (Linux; Android 9; V2196A Build/PQ3A.190705.08211809; wv) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 "
                    + "Mobile Safari/537.36;webank/h5face;webank/1.0;netType:NETWORK_WIFI;"
                    + "appVersion:416;packageName:com.jp3.xg3";

    /** 历史失效、不再直连的荐片图床 host。 */
    private static final Set<String> LEGACY_DEAD_HOSTS = new HashSet<>(Arrays.asList(
            "static.ztcuc.com",
            "static.ztcgi.com",
            "img.ztcuc.com",
            "cdn.ztcuc.com"
    ));

    private static final Object LOCK = new Object();
    private static volatile List<String> liveHosts = new ArrayList<>();
    private static volatile long loadedAt;

    private ImageHostRewrite() {
    }

    /**
     * 若 URL 指向已知失效荐片图床，则换成当前 {@code imgDomain}；否则原样返回。
     * 保留 {@code @Headers=} 等尾部扩展。
     */
    @NonNull
    public static String apply(@NonNull String urlOri) {
        if (TextUtils.isEmpty(urlOri) || !urlOri.startsWith("http")) {
            return urlOri;
        }
        String[] split = urlOri.split("@", 2);
        String base = split[0];
        String suffix = split.length > 1 ? "@" + split[1] : "";
        HttpUrl parsed = HttpUrl.parse(base);
        if (parsed == null) {
            return urlOri;
        }
        String host = parsed.host();
        if (!shouldRewrite(host)) {
            return urlOri;
        }
        String live = primaryLiveHost();
        if (TextUtils.isEmpty(live) || live.equalsIgnoreCase(host)) {
            return urlOri;
        }
        HttpUrl rewritten = parsed.newBuilder().host(live).build();
        return rewritten.toString() + suffix;
    }

    private static boolean shouldRewrite(String host) {
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        String h = host.toLowerCase(Locale.US);
        return LEGACY_DEAD_HOSTS.contains(h)
                || h.endsWith(".ztcuc.com")
                || h.endsWith(".ztcgi.com");
    }

    @Nullable
    private static String primaryLiveHost() {
        refreshIfNeeded();
        List<String> hosts = liveHosts;
        return hosts.isEmpty() ? null : hosts.get(0);
    }

    /** 启动时预热，避免首批封面同步发现域名。 */
    public static void prefetch() {
        new Thread(() -> {
            try {
                refreshIfNeeded();
            } catch (Throwable ignored) {
            }
        }, "img-host-rewrite").start();
    }

    private static void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (!liveHosts.isEmpty() && now - loadedAt < REFRESH_INTERVAL_MS) {
            return;
        }
        synchronized (LOCK) {
            now = System.currentTimeMillis();
            if (!liveHosts.isEmpty() && now - loadedAt < REFRESH_INTERVAL_MS) {
                return;
            }
            try {
                List<String> hosts = fetchLiveImgHosts();
                if (!hosts.isEmpty()) {
                    liveHosts = hosts;
                    loadedAt = System.currentTimeMillis();
                    Log.i(TAG, "imgDomain refreshed: " + hosts);
                }
            } catch (Throwable t) {
                Log.w(TAG, "imgDomain refresh failed: " + t.getMessage());
            }
        }
    }

    @NonNull
    private static List<String> fetchLiveImgHosts() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .dns(new PreferIpv4Dns(Dns.SYSTEM))
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
        String txtJson = httpGet(client, DNS_TXT_URL, null);
        JsonObject txtRoot = new JsonParser().parse(txtJson).getAsJsonObject();
        JsonArray answer = txtRoot.getAsJsonArray("Answer");
        if (answer == null || answer.size() == 0) {
            return new ArrayList<>();
        }
        String parts = answer.get(0).getAsJsonObject().get("data").getAsString().replace("\"", "");
        for (String d : parts.split(",")) {
            String domain = d.trim();
            if (domain.isEmpty()) {
                continue;
            }
            String site = "https://wangerniu." + domain;
            String cfg = httpGet(client, site + "/api/v2/settings/resourceDomainConfig", site);
            if (TextUtils.isEmpty(cfg)) {
                continue;
            }
            JsonObject root = new JsonParser().parse(cfg).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || !data.has("imgDomain")) {
                continue;
            }
            String imgDomain = data.get("imgDomain").getAsString();
            List<String> hosts = new ArrayList<>();
            for (String item : imgDomain.split(",")) {
                String host = normalizeHost(item.trim());
                if (!TextUtils.isEmpty(host) && !hosts.contains(host)) {
                    hosts.add(host);
                }
            }
            if (!hosts.isEmpty()) {
                return hosts;
            }
        }
        return new ArrayList<>();
    }

    @Nullable
    private static String normalizeHost(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            HttpUrl u = HttpUrl.parse(s);
            return u != null ? u.host() : null;
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        return s.toLowerCase(Locale.US);
    }

    private static String httpGet(OkHttpClient client, String url, @Nullable String referer) throws Exception {
        Request.Builder b = new Request.Builder().url(url).header("User-Agent", JP_UA);
        if (!TextUtils.isEmpty(referer)) {
            b.header("Referer", referer);
        }
        try (Response response = client.newCall(b.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return "";
            }
            return response.body().string();
        }
    }
}
