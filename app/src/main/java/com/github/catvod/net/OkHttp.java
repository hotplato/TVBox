package com.github.catvod.net;

import android.net.Uri;

import androidx.collection.ArrayMap;

import com.github.catvod.utils.Util;
import com.hotplato.tvbox.util.OkGoHelper;
import com.google.common.net.HttpHeaders;
import com.lzy.okgo.OkGo;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * JS 桥用的 OkHttp 门面，底层复用 OkGo / OkGoHelper 客户端与 DoH。
 */
public class OkHttp {

    private static final int TIMEOUT = 30 * 1000;

    private OkHttpClient client;
    private ProxySelector selector;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    public static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static Dns dns() {
        return OkGoHelper.dnsOverHttps != null ? OkGoHelper.dnsOverHttps : Dns.SYSTEM;
    }

    public static ProxySelector selector() {
        if (get().selector != null) return get().selector;
        return get().selector = new ProxySelector();
    }

    public void setProxy(String proxy) {
        java.net.ProxySelector.setDefault(selector());
        selector().setProxy(proxy);
        client = null;
    }

    public static OkHttpClient client() {
        if (get().client != null) return get().client;
        OkHttpClient base = OkGo.getInstance().getOkHttpClient();
        if (base == null) {
            get().client = getBuilder().build();
        } else {
            get().client = base.newBuilder().dns(dns()).proxySelector(selector()).build();
        }
        return get().client;
    }

    public static OkHttpClient client(int timeout) {
        return client().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    public static OkHttpClient noRedirect(int timeout) {
        return client().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public static OkHttpClient client(boolean redirect, int timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static String string(String url) {
        try {
            return url.startsWith("http") ? newCall(url).execute().body().string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static Call newCall(String url) {
        Uri uri = Uri.parse(url);
        if (uri.getUserInfo() != null) {
            return newCall(url, Headers.of(HttpHeaders.AUTHORIZATION, Util.basic(uri)));
        }
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url, Headers headers) {
        return client.newCall(new Request.Builder().url(url).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(headers).post(body).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            body.add(entry.getKey(), entry.getValue());
        }
        return body.build();
    }

    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.addQueryParameter(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static OkHttpClient.Builder getBuilder() {
        return new OkHttpClient.Builder()
                .addInterceptor(new OkhttpInterceptor())
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .dns(dns())
                .hostnameVerifier(SSLCompat.VERIFIER)
                .sslSocketFactory(new SSLCompat(), SSLCompat.TM)
                .proxySelector(selector());
    }
}
