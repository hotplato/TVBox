package com.hotplato.tvbox.picasso;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.squareup.picasso.Downloader;

import java.io.IOException;
import java.net.URLDecoder;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Picasso OkHttp 下载器：支持图片 URL 尾部 {@code @Headers}/{@code @Referer} 等自定义头，
 * 并为豆瓣图床自动补充 Referer，避免 CDN 反爬返回 418。
 */
public final class MyOkhttpDownLoader implements Downloader {
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String DOUBAN_REFERER = "https://movie.douban.com/";

    @VisibleForTesting
    final Call.Factory client;
    private final Cache cache;
    private final boolean sharedClient;

    public MyOkhttpDownLoader(OkHttpClient client) {
        this.client = client;
        this.cache = client.cache();
        this.sharedClient = true;
    }

    public MyOkhttpDownLoader(Call.Factory client) {
        this.client = client;
        this.cache = null;
        this.sharedClient = true;
    }

    @NonNull
    @Override
    public Response load(@NonNull Request request) throws IOException {
        String url = request.url().toString();
        String header = null;
        String cookie = null;
        String ua = null;
        String referer = null;

        if (url.contains("@Headers=")) {
            header = url.split("@Headers=")[1].split("@")[0];
            header = URLDecoder.decode(header, "UTF-8");
        }
        if (url.contains("@Cookie=")) {
            cookie = url.split("@Cookie=")[1].split("@")[0];
        }
        if (url.contains("@User-Agent=")) {
            ua = url.split("@User-Agent=")[1].split("@")[0];
        }
        if (url.contains("@Referer=")) {
            referer = url.split("@Referer=")[1].split("@")[0];
        }

        url = url.split("@")[0];
        Request.Builder builder = request.newBuilder().url(url);

        if (!TextUtils.isEmpty(header)) {
            JsonObject jsonInfo = new Gson().fromJson(header, JsonObject.class);
            for (String key : jsonInfo.keySet()) {
                builder.header(key, jsonInfo.get(key).getAsString());
            }
        } else {
            if (!TextUtils.isEmpty(cookie)) {
                builder.header("Cookie", cookie);
            }
            if (!TextUtils.isEmpty(ua)) {
                builder.header("User-Agent", ua);
            } else {
                builder.header("User-Agent", DEFAULT_UA);
            }
            if (!TextUtils.isEmpty(referer)) {
                builder.header("Referer", referer);
            }
        }

        Request finalRequest = builder.build();
        if (isDoubanImageHost(url) && TextUtils.isEmpty(finalRequest.header("Referer"))) {
            finalRequest = finalRequest.newBuilder().header("Referer", DOUBAN_REFERER).build();
        }

        return client.newCall(finalRequest).execute();
    }

    private static boolean isDoubanImageHost(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            return false;
        }
        String host = httpUrl.host();
        return host.contains("doubanio.com") || host.endsWith("douban.com");
    }

    @Override
    public void shutdown() {
        if (!sharedClient && cache != null) {
            try {
                cache.close();
            } catch (IOException ignored) {
            }
        }
    }
}
