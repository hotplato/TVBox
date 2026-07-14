package com.hotplato.tvbox.crawler;

/**
 * Spider 装载失败原因（写入 {@link com.github.catvod.crawler.SpiderNull}，供日志诊断）。
 */
public final class SpiderFailReason {
    public static final String JAR_NOT_LOADED = "JAR_NOT_LOADED";
    public static final String JAR_CLASS_INVALID = "JAR_CLASS_INVALID";
    public static final String JS_LOAD_FAILED = "JS_LOAD_FAILED";
    public static final String API_UNSUPPORTED = "API_UNSUPPORTED";

    private SpiderFailReason() {
    }
}
