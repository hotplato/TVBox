package com.hotplato.tvbox.tvplayer.system;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.hotplato.tvbox.tvplayer.PlayerBackend;
import com.hotplato.tvbox.tvplayer.TvPlayer;

import java.util.HashMap;
import java.util.Map;

public final class SystemMediaPlayerBackend implements PlayerBackend {
    private final Context appContext;
    private final FrameLayout container;
    private final int renderType;
    @Nullable
    private MediaPlayer mediaPlayer;
    @Nullable
    private View displayView;
    @Nullable
    private Surface surface;
    @Nullable
    private Listener listener;
    private int videoWidth;
    private int videoHeight;
    private int scaleType = TvPlayer.SCREEN_SCALE_DEFAULT;
    private boolean prepared;
    private boolean playWhenReady;
    private int bufferedPercentage;

    public SystemMediaPlayerBackend(Context context, FrameLayout container, int renderType) {
        this.appContext = context.getApplicationContext();
        this.container = container;
        this.renderType = renderType;
        ensureDisplay();
    }

    private void ensureDisplay() {
        container.removeAllViews();
        if (renderType == 1) {
            SurfaceView sv = new SurfaceView(container.getContext());
            sv.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    surface = holder.getSurface();
                    if (mediaPlayer != null) mediaPlayer.setDisplay(holder);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    surface = null;
                    if (mediaPlayer != null) mediaPlayer.setDisplay(null);
                }
            });
            displayView = sv;
        } else {
            TextureView tv = new TextureView(container.getContext());
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                    surface = new Surface(st);
                    if (mediaPlayer != null) mediaPlayer.setSurface(surface);
                    applyScale();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
                    applyScale();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                    if (surface != null) {
                        surface.release();
                        surface = null;
                    }
                    if (mediaPlayer != null) mediaPlayer.setSurface(null);
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            });
            displayView = tv;
        }
        container.addView(displayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Override
    public View getDisplayView() {
        return displayView;
    }

    @Override
    public void prepare(String url, @Nullable Map<String, String> headers) {
        releasePlayerOnly();
        prepared = false;
        playWhenReady = true;
        bufferedPercentage = 0;
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(mp -> {
            prepared = true;
            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            applyScale();
            if (listener != null) {
                listener.onPrepared();
                listener.onVideoSizeChanged(videoWidth, videoHeight);
            }
            if (playWhenReady) {
                mp.start();
                if (listener != null) listener.onInfoPlaying();
            }
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            if (listener != null) listener.onCompletion();
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            if (listener != null) listener.onError();
            return true;
        });
        mediaPlayer.setOnInfoListener((mp, what, extra) -> {
            if (listener == null) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                listener.onBuffering(true);
            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                    || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                listener.onBuffering(false);
                listener.onInfoPlaying();
            }
            return false;
        });
        mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
            bufferedPercentage = Math.max(0, Math.min(100, percent));
        });
        try {
            Map<String, String> hdr = headers == null ? new HashMap<>() : new HashMap<>(headers);
            mediaPlayer.setDataSource(appContext, Uri.parse(url), hdr);
            if (displayView instanceof SurfaceView) {
                SurfaceHolder holder = ((SurfaceView) displayView).getHolder();
                if (holder.getSurface() != null && holder.getSurface().isValid()) {
                    mediaPlayer.setDisplay(holder);
                }
            } else if (surface != null) {
                mediaPlayer.setSurface(surface);
            }
            if (listener != null) listener.onBuffering(true);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            if (listener != null) listener.onError();
        }
    }

    @Override
    public void start() {
        playWhenReady = true;
        if (mediaPlayer != null && prepared) {
            mediaPlayer.start();
            if (listener != null) listener.onInfoPlaying();
        }
    }

    @Override
    public void pause() {
        playWhenReady = false;
        if (mediaPlayer != null && prepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (listener != null) listener.onPaused();
        }
    }

    @Override
    public void seekTo(long positionMs) {
        if (mediaPlayer != null && prepared) {
            mediaPlayer.seekTo((int) positionMs);
        }
    }

    @Override
    public long getCurrentPosition() {
        try {
            return mediaPlayer == null || !prepared ? 0 : mediaPlayer.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public long getDuration() {
        try {
            return mediaPlayer == null || !prepared ? 0 : mediaPlayer.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean isPlaying() {
        try {
            return mediaPlayer != null && prepared && mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getBufferedPercentage() {
        return bufferedPercentage;
    }

    @Override
    public void setSpeed(float speed) {
        if (mediaPlayer != null && prepared) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public float getSpeed() {
        try {
            if (mediaPlayer != null && prepared) {
                return mediaPlayer.getPlaybackParams().getSpeed();
            }
        } catch (Exception ignored) {
        }
        return 1f;
    }

    @Override
    public void setVolume(float left, float right) {
        if (mediaPlayer != null) mediaPlayer.setVolume(left, right);
    }

    @Override
    public void setScreenScaleType(int scaleType) {
        this.scaleType = scaleType;
        applyScale();
    }

    private void applyScale() {
        if (!(displayView instanceof TextureView) || videoWidth <= 0 || videoHeight <= 0) return;
        TextureView tv = (TextureView) displayView;
        int viewWidth = tv.getWidth();
        int viewHeight = tv.getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;
        float sx = (float) viewWidth / videoWidth;
        float sy = (float) viewHeight / videoHeight;
        float scaleX = 1f;
        float scaleY = 1f;
        switch (scaleType) {
            case TvPlayer.SCREEN_SCALE_MATCH_PARENT:
                scaleX = sx;
                scaleY = sy;
                break;
            case TvPlayer.SCREEN_SCALE_CENTER_CROP:
                float max = Math.max(sx, sy);
                scaleX = max / sx * sx;
                scaleY = max / sy * sy;
                scaleX = max;
                scaleY = max;
                // Use matrix relative to view: map video into view
                scaleX = (videoWidth * max) / viewWidth;
                scaleY = (videoHeight * max) / viewHeight;
                break;
            case TvPlayer.SCREEN_SCALE_ORIGINAL:
                scaleX = (float) videoWidth / viewWidth;
                scaleY = (float) videoHeight / viewHeight;
                break;
            case TvPlayer.SCREEN_SCALE_16_9: {
                float target = 16f / 9f;
                float viewRatio = (float) viewWidth / viewHeight;
                if (viewRatio > target) {
                    scaleX = (viewHeight * target) / viewWidth;
                    scaleY = 1f;
                } else {
                    scaleX = 1f;
                    scaleY = (viewWidth / target) / viewHeight;
                }
                break;
            }
            case TvPlayer.SCREEN_SCALE_4_3: {
                float target = 4f / 3f;
                float viewRatio = (float) viewWidth / viewHeight;
                if (viewRatio > target) {
                    scaleX = (viewHeight * target) / viewWidth;
                    scaleY = 1f;
                } else {
                    scaleX = 1f;
                    scaleY = (viewWidth / target) / viewHeight;
                }
                break;
            }
            case TvPlayer.SCREEN_SCALE_DEFAULT:
            default: {
                float min = Math.min(sx, sy);
                scaleX = (videoWidth * min) / viewWidth;
                scaleY = (videoHeight * min) / viewHeight;
                break;
            }
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
        tv.setTransform(matrix);
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }

    @Override
    public void release() {
        releasePlayerOnly();
        container.removeAllViews();
        displayView = null;
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    private void releasePlayerOnly() {
        prepared = false;
        bufferedPercentage = 0;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }
}
