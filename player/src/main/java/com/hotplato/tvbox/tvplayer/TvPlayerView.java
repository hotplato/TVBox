package com.hotplato.tvbox.tvplayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hotplato.tvbox.tvplayer.controller.BaseVideoController;
import com.hotplato.tvbox.tvplayer.controller.MediaPlayerControl;
import com.hotplato.tvbox.tvplayer.media3.Media3Backend;
import com.hotplato.tvbox.tvplayer.system.SystemMediaPlayerBackend;
import com.hotplato.tvbox.tvplayer.util.PlayerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin player shell replacing DK {@code VideoView}. Hosts Media3 or system backends.
 */
public class TvPlayerView extends FrameLayout implements MediaPlayerControl, PlayerBackend.Listener {

    private final FrameLayout playerContainer;
    @Nullable
    private PlayerBackend backend;
    @Nullable
    private BaseVideoController videoController;
    @Nullable
    private ProgressStore progressStore;
    @Nullable
    private String url;
    @Nullable
    private String progressKey;
    @Nullable
    private Map<String, String> headers;

    private int backendType = TvPlayer.TYPE_MEDIA3;
    private int renderType = 0;
    private int screenScaleType = TvPlayer.SCREEN_SCALE_DEFAULT;
    private int currentPlayState = TvPlayer.STATE_IDLE;
    private int currentPlayerState = TvPlayer.PLAYER_NORMAL;
    private boolean isFullScreen;
    private boolean isMute;
    private long seekOnStart;
    private final List<OnStateChangeListener> stateListeners = new ArrayList<>();

    public interface OnStateChangeListener {
        void onPlayerStateChanged(int playerState);

        void onPlayStateChanged(int playState);
    }

    public TvPlayerView(@NonNull Context context) {
        this(context, null);
    }

    public TvPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TvPlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        playerContainer = new FrameLayout(context);
        playerContainer.setBackgroundColor(Color.BLACK);
        addView(playerContainer, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setBackendType(int type) {
        if (type != TvPlayer.TYPE_SYSTEM && type != TvPlayer.TYPE_MEDIA3) {
            type = TvPlayer.TYPE_MEDIA3;
        }
        this.backendType = type;
    }

    public void setRenderType(int renderType) {
        this.renderType = renderType;
    }

    public void setProgressStore(@Nullable ProgressStore store) {
        this.progressStore = store;
    }

    /** Compatibility: previously ProgressManager; null disables persistence. */
    public void setProgressManager(@Nullable ProgressStore store) {
        setProgressStore(store);
    }

    public void setProgressKey(@Nullable String key) {
        this.progressKey = key;
    }

    public void setUrl(String url) {
        setUrl(url, null);
    }

    public void setUrl(String url, @Nullable Map<String, String> headers) {
        this.url = url;
        this.headers = headers;
    }

    public void setVideoController(@Nullable BaseVideoController controller) {
        playerContainer.removeView(videoController);
        videoController = controller;
        if (controller != null) {
            controller.setMediaPlayer(this);
            LayoutParams lp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            playerContainer.addView(controller, lp);
        }
    }

    public void addOnStateChangeListener(OnStateChangeListener listener) {
        stateListeners.add(listener);
    }

    public void removeOnStateChangeListener(OnStateChangeListener listener) {
        stateListeners.remove(listener);
    }

    private void rebuildBackend() {
        if (backend != null) {
            backend.release();
            backend = null;
        }
        // Keep controller on top after backend adds display views
        if (videoController != null) {
            playerContainer.removeView(videoController);
        }
        if (backendType == TvPlayer.TYPE_SYSTEM) {
            backend = new SystemMediaPlayerBackend(getContext(), playerContainer, renderType);
        } else {
            backend = new Media3Backend(getContext(), playerContainer, renderType);
        }
        backend.setListener(this);
        backend.setScreenScaleType(screenScaleType);
        if (videoController != null) {
            playerContainer.addView(videoController, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    public void start() {
        if (currentPlayState == TvPlayer.STATE_PAUSED && backend != null) {
            backend.start();
            setPlayState(TvPlayer.STATE_PLAYING);
            return;
        }
        if (TextUtils.isEmpty(url)) {
            Log.e("TvPlayerView", "start aborted: empty url");
            setPlayState(TvPlayer.STATE_ERROR);
            return;
        }
        setPlayState(TvPlayer.STATE_PREPARING);
        rebuildBackend();
        if (progressStore != null) {
            String key = progressKey != null ? progressKey : url;
            seekOnStart = progressStore.getSavedProgress(key);
        } else {
            seekOnStart = 0;
        }
        backend.prepare(url, headers);
    }

    public void resume() {
        if (backend != null && currentPlayState == TvPlayer.STATE_PAUSED) {
            start();
        }
    }

    public void release() {
        saveProgress();
        if (backend != null) {
            backend.release();
            backend = null;
        }
        // Keep last url/headers so callers can release+start after setUrl; empty-url races go to ERROR.
        setPlayState(TvPlayer.STATE_IDLE);
    }

    private void saveProgress() {
        if (progressStore == null || backend == null) return;
        String key = progressKey != null ? progressKey : url;
        long pos = backend.getCurrentPosition();
        long dur = backend.getDuration();
        if (pos > 0 && dur > 0 && pos < dur - 2000) {
            progressStore.saveProgress(key, pos);
        } else if (key != null) {
            progressStore.clearProgress(key);
        }
    }

    private void setPlayState(int state) {
        currentPlayState = state;
        if (videoController != null) {
            videoController.setPlayState(state);
        }
        for (OnStateChangeListener l : stateListeners) {
            l.onPlayStateChanged(state);
        }
    }

    private void setPlayerState(int state) {
        currentPlayerState = state;
        if (videoController != null) {
            videoController.setPlayerState(state);
        }
        for (OnStateChangeListener l : stateListeners) {
            l.onPlayerStateChanged(state);
        }
    }

    @Override
    public void onPrepared() {
        setPlayState(TvPlayer.STATE_PREPARED);
        if (seekOnStart > 0 && backend != null) {
            backend.seekTo(seekOnStart);
            seekOnStart = 0;
        }
        if (backend != null && backend.isPlaying()) {
            setPlayState(TvPlayer.STATE_PLAYING);
        }
    }

    @Override
    public void onCompletion() {
        saveProgress();
        if (progressStore != null) {
            String key = progressKey != null ? progressKey : url;
            progressStore.clearProgress(key);
        }
        setPlayState(TvPlayer.STATE_PLAYBACK_COMPLETED);
    }

    @Override
    public void onError() {
        setPlayState(TvPlayer.STATE_ERROR);
    }

    @Override
    public void onInfoPlaying() {
        setPlayState(TvPlayer.STATE_PLAYING);
    }

    @Override
    public void onPaused() {
        setPlayState(TvPlayer.STATE_PAUSED);
    }

    @Override
    public void onBuffering(boolean buffering) {
        setPlayState(buffering ? TvPlayer.STATE_BUFFERING : TvPlayer.STATE_BUFFERED);
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        // no-op for now; backends apply scale themselves
    }

    @Override
    public void pause() {
        if (backend != null) {
            backend.pause();
            setPlayState(TvPlayer.STATE_PAUSED);
        }
    }

    @Override
    public long getDuration() {
        return backend == null ? 0 : backend.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return backend == null ? 0 : backend.getCurrentPosition();
    }

    @Override
    public void seekTo(long pos) {
        if (backend != null) backend.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return backend != null && backend.isPlaying();
    }

    @Override
    public int getBufferedPercentage() {
        return backend == null ? 0 : backend.getBufferedPercentage();
    }

    @Override
    public void startFullScreen() {
        Activity activity = PlayerUtils.scanForActivity(getContext());
        if (activity == null || isFullScreen) return;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        removeView(playerContainer);
        decor.addView(playerContainer);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        isFullScreen = true;
        setPlayerState(TvPlayer.PLAYER_FULL_SCREEN);
    }

    @Override
    public void stopFullScreen() {
        Activity activity = PlayerUtils.scanForActivity(getContext());
        if (activity == null || !isFullScreen) return;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.removeView(playerContainer);
        addView(playerContainer, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        isFullScreen = false;
        setPlayerState(TvPlayer.PLAYER_NORMAL);
    }

    @Override
    public boolean isFullScreen() {
        return isFullScreen;
    }

    @Override
    public void setMute(boolean isMute) {
        this.isMute = isMute;
        if (backend != null) {
            float v = isMute ? 0f : 1f;
            backend.setVolume(v, v);
        }
    }

    @Override
    public boolean isMute() {
        return isMute;
    }

    @Override
    public void setScreenScaleType(int screenScaleType) {
        this.screenScaleType = screenScaleType;
        if (backend != null) backend.setScreenScaleType(screenScaleType);
    }

    @Override
    public void setSpeed(float speed) {
        if (backend != null) backend.setSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return backend == null ? 1f : backend.getSpeed();
    }

    @Override
    public long getTcpSpeed() {
        return backend == null ? 0 : backend.getTcpSpeed();
    }

    @Override
    public void replay(boolean resetPosition) {
        if (resetPosition && backend != null) backend.seekTo(0);
        start();
    }

    @Override
    public void setMirrorRotation(boolean enable) {
        setScaleX(enable ? -1f : 1f);
    }

    @Override
    public Bitmap doScreenShot() {
        return null;
    }

    @Override
    public int[] getVideoSize() {
        return new int[]{0, 0};
    }

    @Override
    public void setRotation(float rotation) {
        if (backend != null) {
            backend.getDisplayView().setRotation(rotation);
        }
    }

    @Override
    public void startTinyScreen() {
        // not used on TV
    }

    @Override
    public void stopTinyScreen() {
        // not used on TV
    }

    @Override
    public boolean isTinyScreen() {
        return false;
    }
}
