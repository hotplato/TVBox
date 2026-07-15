package com.hotplato.tvbox.api;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.Spider;
import com.hotplato.tvbox.crawler.SpiderManager;
import com.hotplato.tvbox.base.App;
import com.hotplato.tvbox.bean.LiveChannelGroup;
import com.hotplato.tvbox.bean.IJKCode;
import com.hotplato.tvbox.bean.LiveChannelItem;
import com.hotplato.tvbox.bean.ParseBean;
import com.hotplato.tvbox.bean.SourceBean;
import com.hotplato.tvbox.bean.StoreBean;
import com.hotplato.tvbox.server.ControlManager;
import com.hotplato.tvbox.util.AdBlocker;
import com.hotplato.tvbox.util.DefaultConfig;
import com.hotplato.tvbox.util.GsonHolder;
import com.hotplato.tvbox.util.HawkConfig;
import com.hotplato.tvbox.util.MD5;
import com.hotplato.tvbox.util.DiagnosticLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class ApiConfig {
    private static ApiConfig instance;
    private LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource;
    private ParseBean mDefaultParse;
    private List<LiveChannelGroup> liveChannelGroupList;
    private List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    private List<IJKCode> ijkCodes;
    private String spider = null;
    public String wallpaper = "";

    private SourceBean emptyHome = new SourceBean();

    private final SpiderManager spiderManager = new SpiderManager();
    private List<StoreBean> storeBeanList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService configExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "config-io"));

    private ApiConfig() {
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        parseBeanList = new ArrayList<>();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        LoadConfigCallback mainCallback = onMain(callback);
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl.isEmpty()) {
            mainCallback.error("-1");
            return;
        }
        long startedAt = System.currentTimeMillis();
        DiagnosticLog.info("Config", (useCache ? "开始加载缓存配置 " : "开始联网加载配置 ") + DiagnosticLog.redactUrl(apiUrl));
        fetchJson(apiUrl, useCache, new FetchCallback() {
            @Override
            public void onSuccess(String json) {
                configExecutor.execute(() -> {
                    try {
                        handleRootConfig(apiUrl, json, useCache, mainCallback);
                        DiagnosticLog.info("Config", "配置解析完成", System.currentTimeMillis() - startedAt);
                    } catch (Throwable th) {
                        DiagnosticLog.error("Config", "配置解析失败: " + safeMessage(th), System.currentTimeMillis() - startedAt);
                        mainCallback.error("解析配置失败");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                DiagnosticLog.error("Config", "配置加载失败: " + msg, System.currentTimeMillis() - startedAt);
                mainCallback.error(msg);
            }
        });
    }

    /**
     * 用户选中仓库后加载对应单仓配置。
     */
    public void loadSelectedStore(StoreBean store, boolean useCache, LoadConfigCallback callback) {
        LoadConfigCallback mainCallback = onMain(callback);
        if (store == null || TextUtils.isEmpty(store.getUrl())) {
            mainCallback.error("多仓列表为空");
            return;
        }
        Hawk.put(HawkConfig.STORE_API, store.getUrl());
        Hawk.put(HawkConfig.STORE_NAME, store.getName() != null ? store.getName() : "");
        loadChildConfig(store.getUrl(), useCache, mainCallback);
    }

    public static void clearStoreSelection() {
        Hawk.put(HawkConfig.STORE_API, "");
        Hawk.put(HawkConfig.STORE_NAME, "");
    }

    public List<StoreBean> getStoreBeanList() {
        return new ArrayList<>(storeBeanList);
    }

    public boolean isMultiStore() {
        return storeBeanList != null && !storeBeanList.isEmpty();
    }

    private void handleRootConfig(String apiUrl, String json, boolean useCache, LoadConfigCallback callback) {
        JsonObject infoJson = GsonHolder.gson.fromJson(json, JsonObject.class);
        if (infoJson == null) {
            callback.error("解析配置失败");
            return;
        }
        if (infoJson.has("urls") && infoJson.get("urls").isJsonArray()) {
            List<StoreBean> stores = parseStoreList(infoJson.getAsJsonArray("urls"));
            if (stores.isEmpty()) {
                storeBeanList = new ArrayList<>();
                callback.error("多仓列表为空");
                return;
            }
            storeBeanList = stores;
            StoreBean remembered = findRememberedStore(stores);
            if (remembered != null) {
                Hawk.put(HawkConfig.STORE_NAME, remembered.getName());
                loadChildConfig(remembered.getUrl(), useCache, callback);
            } else {
                callback.needSelect(stores);
            }
            return;
        }
        storeBeanList = new ArrayList<>();
        parseJson(apiUrl, json);
        callback.success();
    }

    private StoreBean findRememberedStore(List<StoreBean> stores) {
        String storeApi = Hawk.get(HawkConfig.STORE_API, "");
        if (TextUtils.isEmpty(storeApi))
            return null;
        for (StoreBean bean : stores) {
            if (storeApi.equals(bean.getUrl()))
                return bean;
        }
        return null;
    }

    private List<StoreBean> parseStoreList(JsonArray urls) {
        List<StoreBean> stores = new ArrayList<>();
        int index = 1;
        for (JsonElement el : urls) {
            if (el == null || !el.isJsonObject())
                continue;
            JsonObject obj = el.getAsJsonObject();
            String url = DefaultConfig.safeJsonString(obj, "url", "").trim();
            if (url.isEmpty())
                continue;
            String name = DefaultConfig.safeJsonString(obj, "name", "").trim();
            if (name.isEmpty())
                name = "仓库" + index;
            stores.add(new StoreBean(name, url));
            index++;
        }
        return stores;
    }

    private void loadChildConfig(String storeUrl, boolean useCache, LoadConfigCallback callback) {
        fetchJson(storeUrl, useCache, new FetchCallback() {
            @Override
            public void onSuccess(String json) {
                configExecutor.execute(() -> {
                    try {
                        parseJson(storeUrl, json);
                        callback.success();
                    } catch (Throwable th) {
                        DiagnosticLog.error("Config", "子仓解析失败: " + safeMessage(th));
                        callback.error("解析配置失败");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                callback.error(msg);
            }
        });
    }

    private interface FetchCallback {
        void onSuccess(String json);

        void onError(String msg);
    }

    private void fetchJson(String url, boolean useCache, FetchCallback callback) {
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(url));
        if (useCache && cache.exists()) {
            configExecutor.execute(() -> {
                long startedAt = System.currentTimeMillis();
                try {
                    callback.onSuccess(fixContentPath(url, readFile(cache)));
                    DiagnosticLog.info("Config", "命中配置缓存 " + DiagnosticLog.redactUrl(url), System.currentTimeMillis() - startedAt);
                } catch (Throwable th) {
                    DiagnosticLog.warn("Config", "读取缓存失败，改为联网: " + safeMessage(th));
                    fetchRemoteJson(url, cache, callback, true);
                }
            });
            return;
        }
        fetchRemoteJson(url, cache, callback, true);
    }

    private void fetchRemoteJson(String url, File cache, FetchCallback callback, boolean allowCacheFallback) {
        String apiFix = url;
        if (url.startsWith("clan://")) {
            apiFix = clanToAddress(url);
        }
        long startedAt = System.currentTimeMillis();
        DiagnosticLog.info("Config", "请求远程配置 " + DiagnosticLog.redactUrl(url));
        OkGo.<String>get(apiFix)
                .tag(url)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String json = response.body();
                            configExecutor.execute(() -> {
                                try {
                                    writeCacheAtomically(cache, json);
                                    DiagnosticLog.info("Config", "远程配置已缓存 " + DiagnosticLog.redactUrl(url), System.currentTimeMillis() - startedAt);
                                    callback.onSuccess(fixContentPath(url, json));
                                } catch (Throwable th) {
                                    DiagnosticLog.error("Config", "保存配置缓存失败: " + safeMessage(th));
                                    callback.onError("解析配置失败");
                                }
                            });
                        } catch (Throwable th) {
                            DiagnosticLog.error("Config", "读取远程配置失败: " + safeMessage(th));
                            callback.onError("解析配置失败");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        configExecutor.execute(() -> {
                            if (allowCacheFallback && cache.exists()) {
                                try {
                                    callback.onSuccess(fixContentPath(url, readFile(cache)));
                                    DiagnosticLog.warn("Config", "网络失败，回退旧缓存 " + DiagnosticLog.redactUrl(url), System.currentTimeMillis() - startedAt);
                                    return;
                                } catch (Throwable th) {
                                    DiagnosticLog.error("Config", "回退缓存失败: " + safeMessage(th));
                                }
                            }
                            callback.onError("拉取配置失败\n" + (response.getException() != null ? response.getException().getMessage() : ""));
                        });
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result = "";
                        if (response.body() == null) {
                            result = "";
                        } else {
                            result = response.body().string();
                        }
                        if (url.startsWith("clan")) {
                            result = clanContentFix(clanToAddress(url), result);
                        }
                        return result;
                    }
                });
    }

    private String readFile(File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = bReader.readLine()) != null) {
            sb.append(s).append("\n");
        }
        bReader.close();
        return sb.toString();
    }

    private void writeCacheAtomically(File cache, String json) throws Throwable {
        // A minimal JSON-object check prevents a transient HTML error page from replacing a known-good cache.
        if (GsonHolder.gson.fromJson(json, JsonObject.class) == null)
            throw new IllegalArgumentException("配置不是 JSON 对象");
        File dir = cache.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs())
            throw new IllegalStateException("无法创建缓存目录");
        File temp = new File(cache.getAbsolutePath() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            fos.write(json.getBytes("UTF-8"));
            fos.flush();
        }
        if (cache.exists() && !cache.delete())
            throw new IllegalStateException("无法替换旧缓存");
        if (!temp.renameTo(cache))
            throw new IllegalStateException("无法完成缓存替换");
    }

    /** Refreshes raw config files only; active sources and spiders stay untouched until the next launch. */
    public void refreshConfigCache() {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (TextUtils.isEmpty(apiUrl)) return;
        File rootCache = new File(App.getInstance().getFilesDir(), MD5.encode(apiUrl));
        DiagnosticLog.info("Config", "后台检查配置更新 " + DiagnosticLog.redactUrl(apiUrl));
        fetchRemoteJson(apiUrl, rootCache, new FetchCallback() {
            @Override
            public void onSuccess(String rootJson) {
                configExecutor.execute(() -> {
                    try {
                        JsonObject root = GsonHolder.gson.fromJson(rootJson, JsonObject.class);
                        if (root != null && root.has("urls") && root.get("urls").isJsonArray()) {
                            StoreBean selected = findRememberedStore(parseStoreList(root.getAsJsonArray("urls")));
                            if (selected != null) {
                                File childCache = new File(App.getInstance().getFilesDir(), MD5.encode(selected.getUrl()));
                                fetchRemoteJson(selected.getUrl(), childCache, new FetchCallback() {
                                    @Override public void onSuccess(String ignored) { DiagnosticLog.info("Config", "后台子仓更新完成"); }
                                    @Override public void onError(String msg) { DiagnosticLog.warn("Config", "后台子仓更新失败: " + msg); }
                                }, false);
                            }
                        }
                        DiagnosticLog.info("Config", "后台配置更新完成，下次启动生效");
                    } catch (Throwable th) {
                        DiagnosticLog.warn("Config", "后台配置检查失败: " + safeMessage(th));
                    }
                });
            }

            @Override public void onError(String msg) { DiagnosticLog.warn("Config", "后台配置更新失败: " + msg); }
        }, false);
    }

    private LoadConfigCallback onMain(LoadConfigCallback callback) {
        return new LoadConfigCallback() {
            @Override public void success() { mainHandler.post(callback::success); }
            @Override public void retry() { mainHandler.post(callback::retry); }
            @Override public void error(String msg) { mainHandler.post(() -> callback.error(msg)); }
            @Override public void needSelect(List<StoreBean> stores) { mainHandler.post(() -> callback.needSelect(stores)); }
        };
    }

    private static String safeMessage(Throwable th) {
        return th == null || th.getMessage() == null ? th == null ? "未知错误" : th.getClass().getSimpleName() : th.getMessage();
    }


    public void loadJar(boolean useCache, String spider, LoadConfigCallback callback) {
        spiderManager.loadJar(useCache, spider, new SpiderManager.JarLoadCallback() {
            @Override
            public void success() {
                callback.success();
            }

            @Override
            public void error(String msg) {
                callback.error(msg);
            }
        });
    }

    private void parseJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = GsonHolder.gson.fromJson(jsonStr, JsonObject.class);
        spiderManager.reset();
        sourceBeanList.clear();
        parseBeanList.clear();
        mHomeSource = null;
        mDefaultParse = null;
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        // wallpaper
        wallpaper = DefaultConfig.safeJsonString(infoJson, "wallpaper", "");
        // 远端站点源
        SourceBean firstSite = null;
        for (JsonElement opt : infoJson.get("sites").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.get("name").getAsString().trim());
            sb.setType(obj.get("type").getAsInt());
            sb.setApi(obj.get("api").getAsString().trim());
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            sb.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
            sb.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            if (firstSite == null)
                firstSite = sb;
            sourceBeanList.put(siteKey, sb);
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null)
                setSourceBean(firstSite);
            else
                setSourceBean(sh);
        }
        // 需要使用vip解析的flag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析地址
        for (JsonElement opt : infoJson.get("parses").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            ParseBean pb = new ParseBean();
            pb.setName(obj.get("name").getAsString().trim());
            pb.setUrl(obj.get("url").getAsString().trim());
            String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
            pb.setExt(ext);
            pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
            parseBeanList.add(pb);
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse))
                for (ParseBean pb : parseBeanList) {
                    if (pb.getName().equals(defaultParse))
                        setDefaultParse(pb);
                }
            if (mDefaultParse == null)
                setDefaultParse(parseBeanList.get(0));
        }
        // 直播源
        liveChannelGroupList.clear();           //修复从后台切换重复加载频道列表
        try {
            String lives = infoJson.get("lives").getAsJsonArray().toString();
            int index = lives.indexOf("proxy://");
            if (index != -1) {
                int endIndex = lives.lastIndexOf("\"");
                String url = lives.substring(index, endIndex);
                url = DefaultConfig.checkReplaceProxy(url);

                //clan
                String extUrl = Uri.parse(url).getQueryParameter("ext");
                if (extUrl != null && !extUrl.isEmpty()) {
                    String extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                    if (extUrlFix.startsWith("clan://")) {
                        extUrlFix = clanContentFix(clanToAddress(apiUrl), extUrlFix);
                        extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        url = url.replace(extUrl, extUrlFix);
                    }
                }
                LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
                liveChannelGroup.setGroupName(url);
                liveChannelGroupList.add(liveChannelGroup);
            } else {
                loadLives(infoJson.get("lives").getAsJsonArray());
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        // 广告地址（可选）
        JsonArray ads = infoJson.getAsJsonArray("ads");
        if (ads != null) {
            for (JsonElement host : ads) {
                AdBlocker.addAdHost(host.getAsString());
            }
        }
        // IJK解码配置（可选；部分订阅如 jsm.json 不提供 ijk）
        boolean foundOldSelect = false;
        String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "");
        ijkCodes = new ArrayList<>();
        JsonElement ijkEl = infoJson.get("ijk");
        if (ijkEl != null && ijkEl.isJsonArray()) {
            for (JsonElement opt : ijkEl.getAsJsonArray()) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                ijkCodes.add(codec);
            }
        }
        if (ijkCodes.isEmpty()) {
            IJKCode fallback = new IJKCode();
            fallback.setName("默认");
            fallback.setOption(new LinkedHashMap<>());
            fallback.selected(true);
            ijkCodes.add(fallback);
        } else if (!foundOldSelect) {
            ijkCodes.get(0).selected(true);
        }
        DiagnosticLog.info("Config", "解析摘要：sites=" + sourceBeanList.size()
                + " parses=" + parseBeanList.size()
                + " liveGroups=" + liveChannelGroupList.size()
                + " spider=" + (TextUtils.isEmpty(spider) ? "无" : "已配置")
                + " home=" + (mHomeSource == null ? "无" : mHomeSource.getKey()));
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            if (groupElement == null || !groupElement.isJsonObject())
                continue;
            JsonObject groupObj = groupElement.getAsJsonObject();
            JsonElement groupNameEl = groupObj.get("group");
            JsonElement channelsEl = groupObj.get("channels");
            if (groupNameEl == null || groupNameEl.isJsonNull() || channelsEl == null || !channelsEl.isJsonArray())
                continue;
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = groupNameEl.getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1)
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else
                liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            for (JsonElement channelElement : channelsEl.getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1)
                        sourceNames.add(splitText[1]);
                    else
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            // 空分组无法播放，保留它会让直播页在选择默认频道时越界。
            if (!liveChannelGroup.getLiveChannels().isEmpty()) {
                liveChannelGroupList.add(liveChannelGroup);
            }
        }
    }

    public String getSpider() {
        return spider;
    }

    public Spider getCSP(SourceBean sourceBean) {
        return spiderManager.getSpider(sourceBean);
    }

    /** JS 实例池用：不写入 JsLoader 静态缓存。 */
    public Spider createJsSpider(SourceBean sourceBean) {
        return spiderManager.createJsSpider(sourceBean);
    }

    public Object[] proxyLocal(Map param) {
        return spiderManager.proxyLocal(param);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return spiderManager.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return spiderManager.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void retry();

        void error(String msg);

        void needSelect(List<StoreBean> stores);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key))
            return null;
        return sourceBeanList.get(key);
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null)
            this.mDefaultParse.setDefault(false);
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        return vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        if (ijkCodes == null || ijkCodes.isEmpty())
            return null;
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return ijkCodes.get(0);
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://", fix);
    }

    /**
     * 将配置 JSON 中的相对路径（如 "./jar/spider.jar"）展开为相对配置 URL 目录的绝对地址。
     */
    String fixContentPath(String url, String content) {
        if (TextUtils.isEmpty(content) || !content.contains("\"./"))
            return content;
        if (TextUtils.isEmpty(url))
            return content;
        url = url.replace("file://", "clan://localhost/");
        if (!url.startsWith("http") && !url.startsWith("clan://"))
            url = "http://" + url;
        if (url.startsWith("clan://"))
            url = clanToAddress(url);
        int slash = url.lastIndexOf("/");
        if (slash < 0)
            return content;
        return content.replace("./", url.substring(0, slash + 1));
    }
}
