package com.hotplato.tvbox.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.hotplato.tvbox.event.RefreshEvent;
import com.hotplato.tvbox.receiver.SearchReceiver;
import com.hotplato.tvbox.ui.MainActivity;
import com.hotplato.tvbox.ui.play.PlayActivity;
import com.hotplato.tvbox.util.HawkConfig;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;


/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 */
public class ControlManager {
    private static ControlManager instance;
    private RemoteServer mServer = null;
    public static Context mContext;

    private ControlManager() {

    }

    public static ControlManager get() {
        if (instance == null) {
            synchronized (ControlManager.class) {
                if (instance == null) {
                    instance = new ControlManager();
                }
            }
        }
        return instance;
    }

    public static void init(Context context) {
        mContext = context;
    }

    public String getAddress(boolean local) {
        if (mServer == null) return "";
        return local ? mServer.getLoadAddress() : mServer.getServerAddress();
    }

    public String getPairingCode() { return mServer == null ? "------" : mServer.getPairingCode(); }
    public long getPairingRemainingSeconds() { return mServer == null ? 0 : mServer.getPairingRemainingSeconds(); }
    public void refreshPairingCode() { if (mServer != null) mServer.refreshPairingCode(); }

    public void startServer() {
        if (mServer != null) {
            return;
        }
        do {
            mServer = new RemoteServer(RemoteServer.serverPort, mContext);
            mServer.setDataReceiver(new DataReceiver() {
                @Override
                public void onTextReceived(String text) {
                    if (!TextUtils.isEmpty(text)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("title", text);
                        intent.setAction(SearchReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, SearchReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }

                @Override
                public void onApiReceived(String url) {
                    Hawk.put(HawkConfig.API_URL, url);
                    Hawk.put(HawkConfig.STORE_API, "");
                    Hawk.put(HawkConfig.STORE_NAME, "");
                    Intent intent = new Intent(mContext, MainActivity.class);
                    intent.putExtra(MainActivity.EXTRA_REMOTE_CONFIG_CHANGED, true);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mContext.startActivity(intent);
                }

                @Override
                public void onPushReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url));
                }

                @Override public void onQuickPlayReceived(String url, String title) {
                    Intent intent = new Intent(mContext, PlayActivity.class);
                    intent.putExtra(PlayActivity.EXTRA_REMOTE_URL, url);
                    intent.putExtra(PlayActivity.EXTRA_REMOTE_TITLE, title);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mContext.startActivity(intent);
                }

                @Override public String onPlaybackCommand(String action, double value) {
                    return RemotePlaybackBridge.command(action, value);
                }

                @Override public JsonObject getPlaybackState() { return RemotePlaybackBridge.state(); }
            });
            try {
                mServer.start();
                break;
            } catch (IOException ex) {
                RemoteServer.serverPort++;
                mServer.stop();
            }
        } while (RemoteServer.serverPort < 9999);
    }

    public void stopServer() {
        if (mServer != null && mServer.isStarting()) {
            mServer.stop();
        }
    }
}
