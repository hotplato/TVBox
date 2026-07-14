package com.hotplato.tvbox.util;

import android.util.Log;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class LOG {
    private static String TAG = "TVBox";

    public static void e(String msg) {
        Log.e(TAG, "" + msg);
    }

    public static void e(Throwable t) {
        Log.e(TAG, t == null ? "null" : Log.getStackTraceString(t));
    }

    public static void e(String tag, String msg) {
        Log.e(tag == null ? TAG : tag, "" + msg);
    }

    public static void i(String msg) {
        Log.i(TAG, "" + msg);
    }

    public static void i(String tag, String msg) {
        Log.i(tag == null ? TAG : tag, "" + msg);
    }
}