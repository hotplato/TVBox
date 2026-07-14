package com.hotplato.tvbox.bean;

import androidx.annotation.NonNull;

import com.hotplato.tvbox.tvplayer.TvPlayer;
import com.hotplato.tvbox.tvplayer.TvPlayerView;
import com.hotplato.tvbox.util.HawkConfig;
import com.hotplato.tvbox.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;

    public void init(TvPlayerView videoView) {
        try {
            defaultPlayerConfig.put("pl", PlayerHelper.normalizePlayerType(
                    Hawk.get(HawkConfig.PLAY_TYPE, TvPlayer.TYPE_MEDIA3)));
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(TvPlayerView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getLiveChannelPlayer(TvPlayerView videoView, String channelName) {
        JSONObject playerConfig = Hawk.get(channelName, null);
        if (playerConfig == null) {
            if (!currentPlayerConfig.toString().equals(defaultPlayerConfig.toString()))
                getDefaultLiveChannelPlayer(videoView);
            return;
        }
        try {
            playerConfig.put("pl", PlayerHelper.normalizePlayerType(playerConfig.getInt("pl")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")) {
                videoView.setScreenScaleType(playerConfig.getInt("sc"));
            } else {
                PlayerHelper.updateCfg(videoView, playerConfig);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    /** UI index: 0=系统, 1=Media3 */
    public int getLivePlayerType() {
        try {
            int playerType = PlayerHelper.normalizePlayerType(currentPlayerConfig.getInt("pl"));
            return playerType == TvPlayer.TYPE_SYSTEM ? 0 : 1;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void changeLivePlayerType(TvPlayerView videoView, int playerTypeIndex, String channelName) {
        JSONObject playerConfig = currentPlayerConfig;
        try {
            if (playerTypeIndex == 0) {
                playerConfig.put("pl", TvPlayer.TYPE_SYSTEM);
            } else {
                playerConfig.put("pl", TvPlayer.TYPE_MEDIA3);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public void changeLivePlayerScale(@NonNull TvPlayerView videoView, int playerScale, String channelName) {
        videoView.setScreenScaleType(playerScale);

        JSONObject playerConfig = currentPlayerConfig;
        try {
            playerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }
}
