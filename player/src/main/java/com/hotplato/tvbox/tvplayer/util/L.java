package com.hotplato.tvbox.tvplayer.util;

import android.util.Log;

public final class L {
    private static final String TAG = "TVPlayer";
    private static boolean isDebug = false;

    private L() {}

    public static void setDebug(boolean debug) {
        isDebug = debug;
    }

    public static void d(String msg) {
        if (isDebug) Log.d(TAG, msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
    }
}
