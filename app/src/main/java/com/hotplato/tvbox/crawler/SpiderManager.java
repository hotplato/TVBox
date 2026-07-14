package com.hotplato.tvbox.crawler;

import android.os.Handler;
import android.os.Looper;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JAR / JS 爬虫统一门面：分流、生命周期与失败可观测。
 * 对外由 ApiConfig 委托，宿主 API 仍为 {@link Spider}。
 */
public class SpiderManager {
    private final JarLoader jarLoader = new JarLoader();
    private final JsLoader jsLoader = new JsLoader();
    private final Set<String> loggedNullKeys = ConcurrentHashMap.newKeySet();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService jarLoadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jar-load");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });

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

        if ((!md5.isEmpty() || useCache) && cache.exists()) {
            jarLoadExecutor.execute(() -> {
                if (useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                    loadJarAndPost(cache.getAbsolutePath(), callback);
                } else {
                    mainHandler.post(() -> downloadJar(jarUrl, cache, callback));
                }
            });
            return;
        }

        downloadJar(jarUrl, cache, callback);
    }

    private void downloadJar(String jarUrl, File cache, JarLoadCallback callback) {
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
                if (response.body() != null && response.body().exists()) {
                    loadJarOnBackground(response.body().getAbsolutePath(), callback);
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

    /** DexClassLoader / Init 必须在后台；结果回调切回主线程。 */
    private void loadJarOnBackground(String path, JarLoadCallback callback) {
        jarLoadExecutor.execute(() -> loadJarAndPost(path, callback));
    }

    private void loadJarAndPost(String path, JarLoadCallback callback) {
        boolean ok = jarLoader.load(path);
        LOG.i("SpiderManager", "JarLoader.load thread=" + Thread.currentThread().getName() + " ok=" + ok);
        mainHandler.post(() -> {
            if (ok) {
                callback.success();
            } else {
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
