package com.hotplato.tvbox.tvplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Playback progress persistence (replaces DK ProgressManager).
 */
public class ProgressStore {
    private static final String PREF = "tv_player_progress";
    private final SharedPreferences prefs;

    public ProgressStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Subclasses may ignore SharedPreferences and store elsewhere. */
    protected ProgressStore() {
        prefs = null;
    }

    public long getSavedProgress(@Nullable String key) {
        if (TextUtils.isEmpty(key) || prefs == null) return 0;
        return prefs.getLong(key, 0);
    }

    public void saveProgress(@Nullable String key, long position) {
        if (TextUtils.isEmpty(key) || position <= 0 || prefs == null) return;
        prefs.edit().putLong(key, position).apply();
    }

    public void clearProgress(@Nullable String key) {
        if (TextUtils.isEmpty(key) || prefs == null) return;
        prefs.edit().remove(key).apply();
    }
}
