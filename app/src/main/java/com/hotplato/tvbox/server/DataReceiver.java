package com.hotplato.tvbox.server;

import com.google.gson.JsonObject;

public interface DataReceiver {
    void onTextReceived(String text);
    void onApiReceived(String url);
    void onPushReceived(String url);
    void onQuickPlayReceived(String url, String title);
    /** null means command handled. */
    String onPlaybackCommand(String action, double value);
    JsonObject getPlaybackState();
}
