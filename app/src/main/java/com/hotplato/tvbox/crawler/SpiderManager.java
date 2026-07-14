package com.hotplato.tvbox.crawler;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.hotplato.tvbox.base.App;
import com.hotplato.tvbox.bean.SourceBean;
import com.hotplato.tvbox.util.LOG;
import com.hotplato.tvbox.util.MD5;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JAR / JS 爬虫统一门面：分流、生命周期与失败可观测。
 * 对外由 ApiConfig 委托，宿主 API 仍为 {@link Spider}。
 */
public class SpiderManager {
    private final JarLoader jarLoader = new JarLoader();
    private final JsLoader jsLoader = new JsLoader();
    private final Set<String> loggedNullKeys = ConcurrentHashMap.newKeySet();

    public interface JarLoadCallback {
        void success();

        void error(String msg);
    }

    public void reset() {
        jsLoader.clear();
        jarLoader.clearSpiders();
        loggedNullKeys.clear();
    }

    public static boolean isJsSpiderApi(String api) {
        return api != null && (api.endsWith(".js") || api.contains(".js?"));
    }

    public Spider getSpider(SourceBean sourceBean) {
        if (sourceBean == null) {
            return logAndReturnNull("", "", SpiderFailReason.API_UNSUPPORTED, "sourceBean=null");
        }
        String key = sourceBean.getKey();
        String api = sourceBean.getApi();
        if (isJsSpiderApi(api)) {
            Spider sp = jsLoader.getSpider(key, api, sourceBean.getExt(), sourceBean.getJar());
            return logIfNull(key, api, sp);
        }
        if (!JarLoader.isJarSpiderApi(api)) {
            return logAndReturnNull(key, api, SpiderFailReason.API_UNSUPPORTED, api);
        }
        Spider sp = jarLoader.getSpider(key, api, sourceBean.getExt());
        return logIfNull(key, api, sp);
    }

    public void loadJar(boolean useCache, String spider, JarLoadCallback callback) {
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");

        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5))) {
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                } else {
                    callback.error("");
                }
                return;
            }
        }

        OkGo.<File>get(jarUrl).execute(new AbsCallback<File>() {

            @Override
            public File convertResponse(okhttp3.Response response) throws Throwable {
                File cacheDir = cache.getParentFile();
                if (!cacheDir.exists())
                    cacheDir.mkdirs();
                if (cache.exists()) {
                    cache.setWritable(true);
                    cache.delete();
                }
                FileOutputStream fos = new FileOutputStream(cache);
                fos.write(response.body().bytes());
                fos.flush();
                fos.close();
                cache.setReadOnly();
                return cache;
            }

            @Override
            public void onSuccess(Response<File> response) {
                if (response.body().exists()) {
                    if (jarLoader.load(response.body().getAbsolutePath())) {
                        callback.success();
                    } else {
                        callback.error("");
                    }
                } else {
                    callback.error("");
                }
            }

            @Override
            public void onError(Response<File> response) {
                super.onError(response);
                callback.error("");
            }
        });
    }

    public Object[] proxyLocal(Map param) {
        if (param != null && "js".equals(String.valueOf(param.get("do")))) {
            return jsLoader.proxyInvoke(param);
        }
        return jarLoader.proxyInvoke(param);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    private Spider logIfNull(String key, String api, Spider sp) {
        if (sp instanceof SpiderNull) {
            SpiderNull nullSpider = (SpiderNull) sp;
            logNullOnce(key, api, nullSpider.getReason(), nullSpider.getDetail());
        }
        return sp;
    }

    private Spider logAndReturnNull(String key, String api, String reason, String detail) {
        SpiderNull nullSpider = new SpiderNull(reason, detail);
        logNullOnce(key, api, reason, detail);
        return nullSpider;
    }

    private void logNullOnce(String key, String api, String reason, String detail) {
        String logKey = key == null ? "" : key;
        if (!loggedNullKeys.add(logKey))
            return;
        LOG.i("SpiderNull key=" + logKey
                + " reason=" + (reason == null ? "" : reason)
                + " api=" + (api == null ? "" : api)
                + " detail=" + (detail == null ? "" : detail));
    }
}
