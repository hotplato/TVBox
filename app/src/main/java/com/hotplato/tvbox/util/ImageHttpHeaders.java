package com.hotplato.tvbox.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 图片请求头处理：剥离 URL 尾部 {@code @Headers}/{@code @Referer} 等扩展，
 * 并为豆瓣图床自动补 Referer，避免 CDN 反爬返回 418。
 * <p>
 * Picasso 与 Coil 共用，避免 Compose 路径漏掉防盗链逻辑。
 */
public final class ImageHttpHeaders {
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String DOUBAN_REFERER = "https://movie.douban.com/";

    private ImageHttpHeaders() {
    }

    @NonNull
    public static Request apply(@NonNull Request request) {
        String url = request.url().toString();
        String header = null;
        String cookie = null;
        String ua = null;
        String referer = null;

        if (url.contains("@Headers=")) {
            header = url.split("@Headers=")[1].split("@")[0];
            try {
                header = URLDecoder.decode(header, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }
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
            if (jsonInfo != null) {
                for (String key : jsonInfo.keySet()) {
                    builder.header(key, jsonInfo.get(key).getAsString());
                }
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
        return finalRequest;
    }

    @NonNull
    public static Interceptor interceptor() {
        return chain -> {
            Response response = chain.proceed(apply(chain.request()));
            return response;
        };
    }

    private static boolean isDoubanImageHost(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            return false;
        }
        String host = httpUrl.host();
        return host.contains("doubanio.com") || host.endsWith("douban.com");
    }
}
