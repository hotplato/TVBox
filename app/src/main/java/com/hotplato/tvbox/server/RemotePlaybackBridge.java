package com.hotplato.tvbox.server;

import com.google.gson.JsonObject;

/** The HTTP thread only reaches this process-wide bridge; activities own actual player objects. */
public final class RemotePlaybackBridge {
    public interface Target {
        String command(String action, double value);
        JsonObject state();
    }
    private static volatile Target target;
    private RemotePlaybackBridge() { }
    public static void register(Target value) { target = value; }
    public static void unregister(Target value) { if (target == value) target = null; }
    public static String command(String action, double value) { Target valueTarget = target; return valueTarget == null ? "当前没有可控制的播放器" : valueTarget.command(action, value); }
    public static JsonObject state() { Target valueTarget = target; if (valueTarget != null) return valueTarget.state(); JsonObject state = new JsonObject(); state.addProperty("available", false); return state; }
}
