package com.hotplato.tvbox.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;

import com.google.gson.JsonObject;
import com.hotplato.tvbox.R;
import com.orhanobut.hawk.Hawk;
import com.hotplato.tvbox.util.HawkConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
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
            if ("/api/v1/health".equals(path) && session.getMethod() == Method.GET) return health();
            if ("/api/v1/pair".equals(path) && session.getMethod() == Method.POST) return pair(body(session));
            if (!authorized(session)) return error(Response.Status.UNAUTHORIZED, "unauthorized", "请先完成电视端配对");
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
        if (session.getMethod() == Method.GET) { JsonObject r = new JsonObject(); r.addProperty("url", Hawk.get(HawkConfig.API_URL, "")); return json(Response.Status.OK, r); }
        if (session.getMethod() != Method.PUT && session.getMethod() != Method.POST) return error(Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed", "仅支持读取或更新");
        String url = body(session).optString("url", "").trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return error(Response.Status.BAD_REQUEST, "invalid_url", "配置地址必须是 HTTP(S) URL");
        if (receiver != null) receiver.onApiReceived(url);
        JsonObject r = new JsonObject(); r.addProperty("accepted", true); r.addProperty("message", "已保存，电视正在重新加载"); return json(Response.Status.ACCEPTED, r);
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
    private boolean authorized(IHTTPSession session) {
        String header = session.getHeaders().get("authorization"); if (header == null || !header.startsWith("Bearer ")) return false;
        String key = header.substring(7); Long expiry = sessions.get(key);
        if (expiry == null || expiry < System.currentTimeMillis()) { sessions.remove(key); return false; } return true;
    }
    private JSONObject body(IHTTPSession session) throws Exception { Map<String, String> files = new HashMap<>(); session.parseBody(files); String raw = files.get("postData"); return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); }
    private Response raw(int id, String mime) { InputStream in = context.getResources().openRawResource(id); try { return newFixedLengthResponse(Response.Status.OK, mime + "; charset=utf-8", in, in.available()); } catch (IOException e) { return error(Response.Status.INTERNAL_ERROR, "asset_error", "页面加载失败"); } }
    private Response ok() { JsonObject r = new JsonObject(); r.addProperty("ok", true); return json(Response.Status.OK, r); }
    private Response accepted() { JsonObject r = new JsonObject(); r.addProperty("accepted", true); return json(Response.Status.ACCEPTED, r); }
    private Response error(Response.Status status, String code, String message) { JsonObject r = new JsonObject(); r.addProperty("error", code); r.addProperty("message", message); return json(status, r); }
    private Response json(Response.Status status, JsonObject object) { return newFixedLengthResponse(status, "application/json; charset=utf-8", object.toString()); }
    private String nextCode() { return String.format(java.util.Locale.US, "%06d", random.nextInt(1_000_000)); }
    private String token() { byte[] b = new byte[24]; random.nextBytes(b); StringBuilder out = new StringBuilder(); for (byte x : b) out.append(String.format(java.util.Locale.US, "%02x", x)); return out.toString(); }
    private static String safe(String s) { return s == null || s.isEmpty() ? "请求无效" : s; }
    public String getServerAddress() { return "http://" + getLocalIPAddress(context) + ":" + serverPort + "/"; }
    public String getLoadAddress() { return "http://127.0.0.1:" + serverPort + "/"; }
    @SuppressLint("DefaultLocale") public static String getLocalIPAddress(Context context) { WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE); int ip = wifi == null ? 0 : wifi.getConnectionInfo().getIpAddress(); return ip == 0 ? "0.0.0.0" : String.format("%d.%d.%d.%d", ip & 255, ip >> 8 & 255, ip >> 16 & 255, ip >> 24 & 255); }
}
