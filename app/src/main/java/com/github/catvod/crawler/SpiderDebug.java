package com.github.catvod.crawler;

/**
 * 动态爬虫 JAR 的宿主 API，包名必须保持 com.github.catvod.crawler。
 */
public class SpiderDebug {
    public static void log(Throwable th) {
        try {
            android.util.Log.d("SpiderLog", th.getMessage(), th);
        } catch (Throwable th1) {

        }
    }

    public static void log(String msg) {
        try {
            android.util.Log.d("SpiderLog", msg);
        } catch (Throwable th1) {

        }
    }

    public static String ec(int i) {
        return "";
    }
}
