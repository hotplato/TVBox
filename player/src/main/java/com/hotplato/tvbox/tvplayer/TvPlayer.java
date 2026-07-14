package com.hotplato.tvbox.tvplayer;

/**
 * Playback state and scale constants shared by {@link TvPlayerView} and controllers.
 */
public final class TvPlayer {
    private TvPlayer() {}

    public static final int SCREEN_SCALE_DEFAULT = 0;
    public static final int SCREEN_SCALE_16_9 = 1;
    public static final int SCREEN_SCALE_4_3 = 2;
    public static final int SCREEN_SCALE_MATCH_PARENT = 3;
    public static final int SCREEN_SCALE_ORIGINAL = 4;
    public static final int SCREEN_SCALE_CENTER_CROP = 5;

    public static final int STATE_ERROR = -1;
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    public static final int STATE_PREPARED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_PLAYBACK_COMPLETED = 5;
    public static final int STATE_BUFFERING = 6;
    public static final int STATE_BUFFERED = 7;
    public static final int STATE_START_ABORT = 8;

    public static final int PLAYER_NORMAL = 10;
    public static final int PLAYER_FULL_SCREEN = 11;
    public static final int PLAYER_TINY_SCREEN = 12;

    /** System MediaPlayer */
    public static final int TYPE_SYSTEM = 0;
    /** Media3 ExoPlayer */
    public static final int TYPE_MEDIA3 = 2;
}
