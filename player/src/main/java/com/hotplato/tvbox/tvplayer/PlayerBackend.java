package com.hotplato.tvbox.tvplayer;

import android.view.View;

import androidx.annotation.Nullable;

import java.util.Map;

public interface PlayerBackend {
    interface Listener {
        void onPrepared();

        void onCompletion();

        void onError();

        void onInfoPlaying();

        void onPaused();

        void onBuffering(boolean buffering);

        void onVideoSizeChanged(int width, int height);
    }

    void setListener(@Nullable Listener listener);

    View getDisplayView();

    void prepare(String url, @Nullable Map<String, String> headers);

    void start();

    void pause();

    void seekTo(long positionMs);

    long getCurrentPosition();

    long getDuration();

    boolean isPlaying();

    int getBufferedPercentage();

    void setSpeed(float speed);

    float getSpeed();

    void setVolume(float left, float right);

    void setScreenScaleType(int scaleType);

    long getTcpSpeed();

    void release();
}
