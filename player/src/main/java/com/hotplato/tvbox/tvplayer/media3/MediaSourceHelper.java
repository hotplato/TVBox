package com.hotplato.tvbox.tvplayer.media3;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.rtmp.RtmpDataSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
@UnstableApi
public final class MediaSourceHelper {
    private static final String TAG = "MediaSourceHelper";
    private static volatile MediaSourceHelper sInstance;

    private final Context mAppContext;
    private final String mUserAgent;

    private MediaSourceHelper(Context context) {
        mAppContext = context.getApplicationContext();
        mUserAgent = Util.getUserAgent(mAppContext, mAppContext.getApplicationInfo().name);
    }

    public static MediaSourceHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (MediaSourceHelper.class) {
                if (sInstance == null) {
                    sInstance = new MediaSourceHelper(context);
                }
            }
        }
        return sInstance;
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers) {
        if (TextUtils.isEmpty(uri)) {
            throw new IllegalArgumentException("media url is empty");
        }
        String normalized = normalizeUrl(uri.trim());
        Uri contentUri = Uri.parse(normalized);
        String scheme = contentUri.getScheme();
        if (TextUtils.isEmpty(scheme)) {
            // Without a scheme Media3 falls back to FileDataSource → ENOENT ":".
            throw new IllegalArgumentException("media url missing scheme: " + uri);
        }
        if ("rtsp".equalsIgnoreCase(scheme)) {
            return new RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(contentUri));
        }
        if ("rtmp".equalsIgnoreCase(scheme)) {
            return new ProgressiveMediaSource.Factory(new RtmpDataSource.Factory())
                    .createMediaSource(MediaItem.fromUri(contentUri));
        }
        DataSource.Factory factory = getDataSourceFactory(headers);
        int type = inferContentType(normalized);
        MediaItem item = MediaItem.fromUri(contentUri);
        switch (type) {
            case C.CONTENT_TYPE_DASH:
                return new DashMediaSource.Factory(factory).createMediaSource(item);
            case C.CONTENT_TYPE_HLS:
                return new HlsMediaSource.Factory(factory).createMediaSource(item);
            default:
                return new ProgressiveMediaSource.Factory(factory).createMediaSource(item);
        }
    }

    /**
     * Ensure network-looking urls have a scheme so DefaultDataSource does not use FileDataSource.
     */
    static String normalizeUrl(String uri) {
        String lower = uri.toLowerCase(Locale.US);
        if (lower.startsWith("//")) {
            return "https:" + uri;
        }
        if (lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("rtmp://")
                || lower.startsWith("rtsp://")
                || lower.startsWith("file:")
                || lower.startsWith("content:")
                || lower.startsWith("asset:")
                || lower.startsWith("rawresource:")) {
            return uri;
        }
        // host/path or ip:port/path without scheme → treat as https
        if (uri.contains(".") || uri.contains("/")) {
            Log.w(TAG, "url missing scheme, prefix https:// : " + uri);
            return "https://" + uri;
        }
        return uri;
    }

    private int inferContentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.contains(".mpd")) return C.CONTENT_TYPE_DASH;
        if (lower.contains(".m3u8")) return C.CONTENT_TYPE_HLS;
        return C.CONTENT_TYPE_OTHER;
    }

    private DataSource.Factory getDataSourceFactory(Map<String, String> headers) {
        // Fresh factory each time so prior request headers never stick.
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(mUserAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
                .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS);
        if (headers != null && !headers.isEmpty()) {
            Map<String, String> props = new HashMap<>();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                String key = e.getKey();
                String value = e.getValue().trim();
                if ("User-Agent".equalsIgnoreCase(key) && !TextUtils.isEmpty(value)) {
                    http.setUserAgent(value);
                } else {
                    props.put(key, value);
                }
            }
            if (!props.isEmpty()) {
                http.setDefaultRequestProperties(props);
            }
        }
        return new DefaultDataSource.Factory(mAppContext, http);
    }
}
