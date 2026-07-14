package com.hotplato.tvbox.tvplayer;

public final class TvPlayerConfig {
    private static final TvPlayerConfig INSTANCE = new TvPlayerConfig();

    public boolean mEnableOrientation = false;
    public boolean mAdaptCutout = true;
    public boolean mPlayOnMobileNetwork = true;
    public boolean mIsEnableLog = false;

    private TvPlayerConfig() {}

    public static TvPlayerConfig get() {
        return INSTANCE;
    }

    public boolean playOnMobileNetwork() {
        return mPlayOnMobileNetwork;
    }
}
