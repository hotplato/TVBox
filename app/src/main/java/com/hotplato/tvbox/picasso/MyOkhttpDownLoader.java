package com.hotplato.tvbox.picasso;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.hotplato.tvbox.util.ImageHttpHeaders;
import com.squareup.picasso.Downloader;

import java.io.IOException;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Picasso OkHttp 下载器：委托 {@link ImageHttpHeaders} 处理自定义头与豆瓣 Referer。
 */
public final class MyOkhttpDownLoader implements Downloader {
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
        return client.newCall(ImageHttpHeaders.apply(request)).execute();
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
