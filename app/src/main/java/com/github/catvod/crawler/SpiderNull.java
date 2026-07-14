package com.github.catvod.crawler;

/**
 * 动态爬虫 JAR / JS 的空实现宿主 API，包名必须保持 com.github.catvod.crawler。
 * 可携带失败原因供日志诊断；对外方法行为与空 Spider 一致。
 */
public class SpiderNull extends Spider {
    private final String reason;
    private final String detail;

    public SpiderNull() {
        this("", "");
    }

    public SpiderNull(String reason, String detail) {
        this.reason = reason == null ? "" : reason;
        this.detail = detail == null ? "" : detail;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
