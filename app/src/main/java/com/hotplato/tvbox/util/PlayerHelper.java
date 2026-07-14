package com.hotplato.tvbox.util;

import com.hotplato.tvbox.tvplayer.TvPlayer;
import com.hotplato.tvbox.tvplayer.TvPlayerView;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

public class PlayerHelper {

    /** Normalize legacy play_type values (IJK/MX/Reex) to Media3. */
    public static void migratePlayType() {
        int type = Hawk.get(HawkConfig.PLAY_TYPE, TvPlayer.TYPE_MEDIA3);
        if (type != TvPlayer.TYPE_SYSTEM && type != TvPlayer.TYPE_MEDIA3) {
            Hawk.put(HawkConfig.PLAY_TYPE, TvPlayer.TYPE_MEDIA3);
        }
    }

    public static int normalizePlayerType(int playerType) {
        if (playerType == TvPlayer.TYPE_SYSTEM || playerType == TvPlayer.TYPE_MEDIA3) {
            return playerType;
        }
        return TvPlayer.TYPE_MEDIA3;
    }

    public static void updateCfg(TvPlayerView videoView, JSONObject playerCfg) {
        int playerType = Hawk.get(HawkConfig.PLAY_TYPE, TvPlayer.TYPE_MEDIA3);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        int scale = Hawk.get(HawkConfig.PLAY_SCALE, 0);
        try {
            playerType = playerCfg.getInt("pl");
            renderType = playerCfg.getInt("pr");
            scale = playerCfg.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        playerType = normalizePlayerType(playerType);
        videoView.setBackendType(playerType);
        videoView.setRenderType(renderType);
        videoView.setScreenScaleType(scale);
    }

    public static void updateCfg(TvPlayerView videoView) {
        int playType = normalizePlayerType(Hawk.get(HawkConfig.PLAY_TYPE, TvPlayer.TYPE_MEDIA3));
        videoView.setBackendType(playType);
        videoView.setRenderType(Hawk.get(HawkConfig.PLAY_RENDER, 0));
        videoView.setScreenScaleType(Hawk.get(HawkConfig.PLAY_SCALE, 0));
    }

    public static void init() {
        migratePlayType();
    }

    public static String getPlayerName(int playType) {
        if (normalizePlayerType(playType) == TvPlayer.TYPE_MEDIA3) {
            return "Media3";
        }
        return "系统播放器";
    }

    public static String getRenderName(int renderType) {
        if (renderType == 1) {
            return "SurfaceView";
        }
        return "TextureView";
    }

    public static String getScaleName(int screenScaleType) {
        switch (screenScaleType) {
            case TvPlayer.SCREEN_SCALE_16_9:
                return "16:9";
            case TvPlayer.SCREEN_SCALE_4_3:
                return "4:3";
            case TvPlayer.SCREEN_SCALE_MATCH_PARENT:
                return "填充";
            case TvPlayer.SCREEN_SCALE_ORIGINAL:
                return "原始";
            case TvPlayer.SCREEN_SCALE_CENTER_CROP:
                return "裁剪";
            case TvPlayer.SCREEN_SCALE_DEFAULT:
            default:
                return "默认";
        }
    }
}
