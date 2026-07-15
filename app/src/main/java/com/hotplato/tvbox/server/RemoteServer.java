package com.hotplato.tvbox.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;

import com.google.gson.JsonObject;
import com.hotplato.tvbox.R;
import com.hotplato.tvbox.data.WallpaperRepository;
import com.orhanobut.hawk.Hawk;
import com.hotplato.tvbox.util.HawkConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD;

/** Small, authenticated LAN control server.  Feature handlers deliberately live here rather
 * than exposing the historical file/proxy server surface. */
public class RemoteServer extends NanoHTTPD {
    public static int serverPort = 9978;
    private static final long PAIR_TTL_MS = 5 * 60_000L;
    private static final long SESSION_TTL_MS = 30 * 60_000L;
    private final Context context;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private volatile String pairingCode = nextCode();
    private volatile long pairingExpiresAt = System.currentTimeMillis() + PAIR_TTL_MS;
    private volatile boolean started;
    private DataReceiver receiver;

    public RemoteServer(int port, Context context) {
        super(port);
        this.context = context.getApplicationContext();
    }

    @Override public void start(int timeout, boolean daemon) throws IOException { super.start(timeout, daemon); started = true; }
    @Override public void stop() { super.stop(); started = false; sessions.clear(); }
    public boolean isStarting() { return started; }
    public synchronized String refreshPairingCode() { pairingCode = nextCode(); pairingExpiresAt = System.currentTimeMillis() + PAIR_TTL_MS; return pairingCode; }
    public synchronized String getPairingCode() { if (System.currentTimeMillis() >= pairingExpiresAt) refreshPairingCode(); return pairingCode; }
    public long getPairingRemainingSeconds() { return Math.max(0L, (pairingExpiresAt - System.currentTimeMillis() + 999L) / 1000L); }
    public void setDataReceiver(DataReceiver receiver) { this.receiver = receiver; }
    public DataReceiver getDataReceiver() { return receiver; }

    @Override public Response serve(IHTTPSession session) {
        String path = session.getUri();
        try {
            if (session.getMethod() == Method.GET && "/".equals(path)) return raw(R.raw.index, MIME_HTML);
            if (session.getMethod() == Method.GET && "/remote.css".equals(path)) return raw(R.raw.style, "text/css");
            if (session.getMethod() == Method.GET && "/remote.js".equals(path)) return raw(R.raw.script, "application/javascript");
            if (session.getMethod() == Method.GET && "/wallpaper.html".equals(path)) return raw(R.raw.wallpaper, MIME_HTML);
            if ("/api/v1/health".equals(path) && session.getMethod() == Method.GET) return health();
            if ("/api/v1/pair".equals(path) && session.getMethod() == Method.POST) return pair(body(session));
            if (!authorized(session)) return error(Response.Status.UNAUTHORIZED, "unauthorized", "请先完成电视端配对");
            if ("/api/v1/wallpaper".equals(path)) return wallpaper(session);
            if ("/api/v1/wallpaper/refresh".equals(path) && session.getMethod() == Method.POST) return wallpaperResult(WallpaperRepository.refresh());
            if ("/api/v1/wallpaper/upload".equals(path) && session.getMethod() == Method.POST) return uploadWallpaper(session);
            if ("/api/v1/config".equals(path)) return config(session);
            if ("/api/v1/commands/search".equals(path) && session.getMethod() == Method.POST) return search(body(session));
            if ("/api/v1/quick-play".equals(path) && session.getMethod() == Method.POST) return quickPlay(body(session));
            if ("/api/v1/playback".equals(path)) return playback(session);
            return error(Response.Status.NOT_FOUND, "not_found", "接口不存在");
        } catch (Exception e) { return error(Response.Status.BAD_REQUEST, "bad_request", safe(e.getMessage())); }
    }

    private Response health() {
        JsonObject data = new JsonObject(); data.addProperty("ok", true); data.addProperty("port", serverPort);
        data.addProperty("address", getServerAddress()); data.addProperty("pairingExpiresIn", getPairingRemainingSeconds());
        return json(Response.Status.OK, data);
    }
    private Response pair(JSONObject input) {
        String code = input.optString("code", "").trim();
        if (System.currentTimeMillis() >= pairingExpiresAt || !getPairingCode().equals(code)) return error(Response.Status.UNAUTHORIZED, "invalid_pairing_code", "配对码错误或已过期");
        String token = token(); sessions.put(token, System.currentTimeMillis() + SESSION_TTL_MS);
        JsonObject data = new JsonObject(); data.addProperty("token", token); data.addProperty("expiresIn", SESSION_TTL_MS / 1000L);
        return json(Response.Status.OK, data);
    }
    private Response config(IHTTPSession session) throws Exception {
        if (session.getMethod() == Method.GET) { JsonObject r = new JsonObject(); r.addProperty("url", Hawk.get(HawkConfig.API_URL, "")); r.add("settings", settingsJson()); return json(Response.Status.OK, r); }
        if (session.getMethod() != Method.PUT && session.getMethod() != Method.POST) return error(Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed", "仅支持读取或更新");
        JSONObject input = body(session);
        String url = input.optString("url", Hawk.get(HawkConfig.API_URL, "")).trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return error(Response.Status.BAD_REQUEST, "invalid_url", "配置地址必须是 HTTP(S) URL");
        applySettings(input.optJSONObject("settings"));
        boolean reload = !url.equals(Hawk.get(HawkConfig.API_URL, ""));
        if (reload && receiver != null) receiver.onApiReceived(url);
        JsonObject r = new JsonObject(); r.addProperty("accepted", reload); r.addProperty("message", reload ? "已保存，电视正在重新加载" : "设置已保存"); return json(reload ? Response.Status.ACCEPTED : Response.Status.OK, r);
    }
    private Response search(JSONObject input) {
        String word = input.optString("word", "").trim(); if (word.isEmpty()) return error(Response.Status.BAD_REQUEST, "invalid_word", "请输入搜索内容");
        if (receiver != null) receiver.onTextReceived(word); return ok();
    }
    private Response quickPlay(JSONObject input) {
        String url = input.optString("url", "").trim(); if (url.isEmpty()) return error(Response.Status.BAD_REQUEST, "invalid_url", "请输入播放地址");
        if (receiver != null) receiver.onQuickPlayReceived(url, input.optString("title", "远程播放")); return accepted();
    }
    private Response playback(IHTTPSession session) throws Exception {
        if (receiver == null) return error(Response.Status.CONFLICT, "unavailable", "播放器不可用");
        if (session.getMethod() == Method.GET) return json(Response.Status.OK, receiver.getPlaybackState());
        if (session.getMethod() != Method.POST) return error(Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed", "仅支持读取或控制");
        JSONObject input = body(session); String action = input.optString("action", "");
        String reason = receiver.onPlaybackCommand(action, input.optDouble("value", 0));
        if (reason != null) return error(Response.Status.CONFLICT, "unsupported", reason);
        return ok();
    }
    private Response wallpaper(IHTTPSession session) throws Exception {
        if (session.getMethod() == Method.GET) {
            JsonObject data = new JsonObject();
            data.addProperty("source", WallpaperRepository.currentSource());
            data.addProperty("hasWallpaper", WallpaperRepository.hasCache());
            return json(Response.Status.OK, data);
        }
        if (session.getMethod() == Method.DELETE) return wallpaperResult(WallpaperRepository.clear());
        if (session.getMethod() != Method.PUT) return error(Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed", "仅支持读取、切换或恢复");
        String provider = body(session).optString("provider", "").trim();
        return wallpaperResult(WallpaperRepository.applyProvider(provider));
    }
    private Response uploadWallpaper(IHTTPSession session) throws Exception {
        String contentLength = session.getHeaders().get("content-length");
        if (contentLength != null) try {
            if (Long.parseLong(contentLength) > WallpaperRepository.MAX_BYTES + 1024L * 1024L) {
                return error(Response.Status.BAD_REQUEST, "file_too_large", "图片不能超过 20 MiB");
            }
        } catch (NumberFormatException ignored) { }
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String path = files.get("file");
        if (path == null) return error(Response.Status.BAD_REQUEST, "missing_file", "请上传 file 字段");
        try {
            return wallpaperResult(WallpaperRepository.applyUpload(new File(path)));
        } finally {
            new File(path).delete();
        }
    }
    private Response wallpaperResult(String message) {
        if (message != null) {
            Response.Status status = message.contains("不是在线服务") ? Response.Status.CONFLICT : Response.Status.BAD_REQUEST;
            return error(status, "wallpaper_failed", message);
        }
        JsonObject data = new JsonObject();
        data.addProperty("ok", true);
        data.addProperty("message", "壁纸已更新");
        data.addProperty("source", WallpaperRepository.currentSource());
        data.addProperty("hasWallpaper", WallpaperRepository.hasCache());
        return json(Response.Status.OK, data);
    }
    private boolean authorized(IHTTPSession session) {
        String header = session.getHeaders().get("authorization"); if (header == null || !header.startsWith("Bearer ")) return false;
        String key = header.substring(7); Long expiry = sessions.get(key);
        if (expiry == null || expiry < System.currentTimeMillis()) { sessions.remove(key); return false; } return true;
    }
    private JSONObject body(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>(); session.parseBody(files);
        String raw = files.get("postData");
        // NanoHTTPD stores PUT bodies in a temporary file while POST bodies are in postData.
        if (raw == null && files.get("content") != null) {
            try (InputStream input = new FileInputStream(files.get("content")); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                raw = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
    }
    private Response raw(int id, String mime) { InputStream in = context.getResources().openRawResource(id); try { return newFixedLengthResponse(Response.Status.OK, mime + "; charset=utf-8", in, in.available()); } catch (IOException e) { return error(Response.Status.INTERNAL_ERROR, "asset_error", "页面加载失败"); } }
    private Response ok() { JsonObject r = new JsonObject(); r.addProperty("ok", true); return json(Response.Status.OK, r); }
    private Response accepted() { JsonObject r = new JsonObject(); r.addProperty("accepted", true); return json(Response.Status.ACCEPTED, r); }
    private Response error(Response.Status status, String code, String message) { JsonObject r = new JsonObject(); r.addProperty("error", code); r.addProperty("message", message); return json(status, r); }
    private Response json(Response.Status status, JsonObject object) { return newFixedLengthResponse(status, "application/json; charset=utf-8", object.toString()); }
    private String nextCode() { return String.format(java.util.Locale.US, "%06d", random.nextInt(1_000_000)); }
    private String token() { byte[] b = new byte[24]; random.nextBytes(b); StringBuilder out = new StringBuilder(); for (byte x : b) out.append(String.format(java.util.Locale.US, "%02x", x)); return out.toString(); }
    private static String safe(String s) { return s == null || s.isEmpty() ? "请求无效" : s; }
    private JsonObject settingsJson() {
        JsonObject value = new JsonObject();
        value.addProperty("debugOpen", Hawk.get(HawkConfig.DEBUG_OPEN, false));
        value.addProperty("parseWebView", Hawk.get(HawkConfig.PARSE_WEBVIEW, true));
        value.addProperty("playType", Hawk.get(HawkConfig.PLAY_TYPE, 2));
        value.addProperty("playRender", Hawk.get(HawkConfig.PLAY_RENDER, 0));
        value.addProperty("playScale", Hawk.get(HawkConfig.PLAY_SCALE, 0));
        value.addProperty("dohUrl", Hawk.get(HawkConfig.DOH_URL, 0));
        value.addProperty("homeRec", Hawk.get(HawkConfig.HOME_REC, 0));
        value.addProperty("searchView", Hawk.get(HawkConfig.SEARCH_VIEW, 0));
        return value;
    }
    private void applySettings(JSONObject value) {
        if (value == null) return;
        if (value.has("debugOpen")) Hawk.put(HawkConfig.DEBUG_OPEN, value.optBoolean("debugOpen"));
        if (value.has("parseWebView")) Hawk.put(HawkConfig.PARSE_WEBVIEW, value.optBoolean("parseWebView"));
        if (value.has("playType")) Hawk.put(HawkConfig.PLAY_TYPE, value.optInt("playType") == 0 ? 0 : 2);
        if (value.has("playRender")) Hawk.put(HawkConfig.PLAY_RENDER, value.optInt("playRender") == 1 ? 1 : 0);
        if (value.has("playScale")) { int scale = value.optInt("playScale"); if (scale >= 0 && scale <= 5) Hawk.put(HawkConfig.PLAY_SCALE, scale); }
        if (value.has("dohUrl")) { int doh = value.optInt("dohUrl"); if (doh >= 0 && doh <= 100) Hawk.put(HawkConfig.DOH_URL, doh); }
        if (value.has("homeRec")) { int home = value.optInt("homeRec"); if (home >= 0 && home <= 2) Hawk.put(HawkConfig.HOME_REC, home); }
        if (value.has("searchView")) Hawk.put(HawkConfig.SEARCH_VIEW, value.optInt("searchView") == 1 ? 1 : 0);
    }
    public String getServerAddress() { return "http://" + getLocalIPAddress(context) + ":" + serverPort + "/"; }
    public String getLoadAddress() { return "http://127.0.0.1:" + serverPort + "/"; }
    @SuppressLint("DefaultLocale") public static String getLocalIPAddress(Context context) { WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE); int ip = wifi == null ? 0 : wifi.getConnectionInfo().getIpAddress(); return ip == 0 ? "0.0.0.0" : String.format("%d.%d.%d.%d", ip & 255, ip >> 8 & 255, ip >> 16 & 255, ip >> 24 & 255); }
}
