package com.hotplato.tvbox.tvplayer.media3;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.hotplato.tvbox.player.R;
import com.hotplato.tvbox.tvplayer.PlayerBackend;
import com.hotplato.tvbox.tvplayer.TvPlayer;

import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
@UnstableApi
public final class Media3Backend implements PlayerBackend {
    private static final String TAG = "Media3Backend";
    private static final long FIRST_FRAME_TIMEOUT_MS = 12_000L;

    private final Context appContext;
    private final FrameLayout container;
    private final PlayerView playerView;
    private final MediaSourceHelper sourceHelper;
    @Nullable
    private ExoPlayer player;
    @Nullable
    private Listener listener;
    private float speed = 1f;
    private boolean preparing;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean renderedFirstFrame;
    private final Runnable firstFrameTimeout = new Runnable() {
        @Override
        public void run() {
            if (player == null || renderedFirstFrame || !player.getPlayWhenReady()) return;
            Log.w(TAG, "video frame was not rendered after decoder initialization");
            player.setPlayWhenReady(false);
            preparing = false;
            if (listener != null) {
                listener.onError("当前设备不支持该视频编码，请切换播放线路", false);
            }
        }
    };

    public Media3Backend(Context context, FrameLayout container, int renderType) {
        this.appContext = context.getApplicationContext();
        this.container = container;
        this.sourceHelper = MediaSourceHelper.getInstance(context);
        int layout = renderType == 1
                ? R.layout.tv_media3_player_surface
                : R.layout.tv_media3_player_texture;
        playerView = (PlayerView) LayoutInflater.from(context).inflate(layout, container, false);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Remove previous display children but keep controller overlays (added later by TvPlayerView)
        container.addView(playerView, 0);
    }

    @Override
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Override
    public View getDisplayView() {
        return playerView;
    }

    @Override
    public void prepare(String url, @Nullable Map<String, String> headers) {
        releasePlayerOnly();
        renderedFirstFrame = false;
        if (url == null || url.trim().isEmpty()) {
            if (listener != null) listener.onError();
            return;
        }
        MediaSource mediaSource;
        try {
            mediaSource = sourceHelper.getMediaSource(url, headers);
        } catch (Exception e) {
            Log.e(TAG, "bad media url: " + url, e);
            if (listener != null) listener.onError();
            return;
        }
        player = new ExoPlayer.Builder(appContext).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (listener == null) return;
                if (preparing && playbackState == Player.STATE_READY) {
                    preparing = false;
                    listener.onPrepared();
                    listener.onInfoPlaying();
                    return;
                }
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        listener.onBuffering(true);
                        break;
                    case Player.STATE_READY:
                        listener.onBuffering(false);
                        break;
                    case Player.STATE_ENDED:
                        listener.onCompletion();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                cancelFirstFrameTimeout();
                preparing = false;
                if (listener != null) {
                    boolean decoderError = isDecoderError(error);
                    listener.onError(
                            decoderError ? "当前设备不支持该视频编码，请切换播放线路" : null,
                            !decoderError
                    );
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (listener != null) {
                    listener.onVideoSizeChanged(videoSize.width, videoSize.height);
                }
                if (videoSize.width > 0 && videoSize.height > 0 && !renderedFirstFrame) {
                    scheduleFirstFrameTimeout();
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                renderedFirstFrame = true;
                cancelFirstFrameTimeout();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (listener == null || preparing) return;
                if (isPlaying) {
                    listener.onInfoPlaying();
                } else if (player != null && player.getPlaybackState() == Player.STATE_READY) {
                    listener.onPaused();
                }
            }
        });
        preparing = true;
        player.setMediaSource(mediaSource);
        player.setPlaybackParameters(new PlaybackParameters(speed));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    public void start() {
        if (player != null) player.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    public void seekTo(long positionMs) {
        if (player != null) player.seekTo(positionMs);
    }

    @Override
    public long getCurrentPosition() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return player == null ? 0 : Math.max(player.getDuration(), 0);
    }

    @Override
    public boolean isPlaying() {
        if (player == null) return false;
        int state = player.getPlaybackState();
        return (state == Player.STATE_BUFFERING || state == Player.STATE_READY) && player.getPlayWhenReady();
    }

    @Override
    public int getBufferedPercentage() {
        return player == null ? 0 : player.getBufferedPercentage();
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed;
        if (player != null) player.setPlaybackParameters(new PlaybackParameters(speed));
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public void setVolume(float left, float right) {
        if (player != null) player.setVolume((left + right) / 2f);
    }

    @Override
    public void setScreenScaleType(int scaleType) {
        int resize;
        switch (scaleType) {
            case TvPlayer.SCREEN_SCALE_MATCH_PARENT:
                resize = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                break;
            case TvPlayer.SCREEN_SCALE_CENTER_CROP:
                resize = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                break;
            case TvPlayer.SCREEN_SCALE_DEFAULT:
            case TvPlayer.SCREEN_SCALE_16_9:
            case TvPlayer.SCREEN_SCALE_4_3:
            case TvPlayer.SCREEN_SCALE_ORIGINAL:
            default:
                resize = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
        }
        playerView.setResizeMode(resize);
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }

    @Override
    public void release() {
        releasePlayerOnly();
        container.removeView(playerView);
    }

    private void releasePlayerOnly() {
        cancelFirstFrameTimeout();
        preparing = false;
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void scheduleFirstFrameTimeout() {
        mainHandler.removeCallbacks(firstFrameTimeout);
        mainHandler.postDelayed(firstFrameTimeout, FIRST_FRAME_TIMEOUT_MS);
    }

    private void cancelFirstFrameTimeout() {
        mainHandler.removeCallbacks(firstFrameTimeout);
    }

    private static boolean isDecoderError(PlaybackException error) {
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED:
                return true;
            default:
                return false;
        }
    }
}
