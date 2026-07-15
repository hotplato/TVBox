package com.hotplato.tvbox.crawler;

import android.os.Handler;
import android.os.Looper;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.hotplato.tvbox.base.App;
import com.hotplato.tvbox.bean.SourceBean;
import com.hotplato.tvbox.crawler.opt.JarMd5Index;
import com.hotplato.tvbox.crawler.opt.SpiderRuntime;
import com.hotplato.tvbox.util.LOG;
import com.hotplato.tvbox.util.DiagnosticLog;
import com.hotplato.tvbox.util.MD5;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
        DiagnosticLog.info("Spider", "重置 JAR/JS 爬虫实例与结果缓存");
        jsLoader.clear();
        jarLoader.clearSpiders();
        loggedNullKeys.clear();
        SpiderRuntime.reset();
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
        DiagnosticLog.info("Spider", "获取爬虫 key=" + key + " api=" + api);
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

    /** JS 实例池：创建不入缓存的 Spider（失败为 SpiderNull）。 */
    public Spider createJsSpider(SourceBean sourceBean) {
        if (sourceBean == null) {
            return logAndReturnNull("", "", SpiderFailReason.API_UNSUPPORTED, "sourceBean=null");
        }
        String key = sourceBean.getKey();
        String api = sourceBean.getApi();
        if (!isJsSpiderApi(api)) {
            return logAndReturnNull(key, api, SpiderFailReason.API_UNSUPPORTED, api);
        }
        Spider sp = jsLoader.createSpider(key, api, sourceBean.getExt(), sourceBean.getJar());
        return logIfNull(key, api, sp);
    }

    public void loadJar(boolean useCache, String spider, JarLoadCallback callback) {
        long startedAt = System.currentTimeMillis();
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");
        DiagnosticLog.info("Spider", "准备装载 JAR cache=" + cache.getName()
                + " exists=" + cache.exists() + " useCache=" + useCache + " md5=" + (!md5.isEmpty()));

        if ((!md5.isEmpty() || useCache) && cache.exists()) {
            jarLoadExecutor.execute(() -> {
                if (useCache) {
                    DiagnosticLog.info("Spider", "命中 JAR 缓存");
                    loadJarAndPost(cache.getAbsolutePath(), callback, startedAt);
                    return;
                }
                if (JarMd5Index.matchesConfigured(cache, md5)) {
                    LOG.i("SpiderManager", "jar md5 sidecar hit, skip full-file scan");
                    DiagnosticLog.info("Spider", "JAR 校验缓存命中");
                    loadJarAndPost(cache.getAbsolutePath(), callback, startedAt);
                    return;
                }
                if (MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                    JarMd5Index.write(cache, md5);
                    loadJarAndPost(cache.getAbsolutePath(), callback, startedAt);
                } else {
                    JarMd5Index.delete(cache);
                    mainHandler.post(() -> downloadJar(jarUrl, cache, md5, callback, startedAt));
                }
            });
            return;
        }

        downloadJar(jarUrl, cache, md5, callback, startedAt);
    }

    private void downloadJar(String jarUrl, File cache, String md5, JarLoadCallback callback, long startedAt) {
        DiagnosticLog.info("Spider", "下载爬虫 JAR " + DiagnosticLog.redactUrl(jarUrl));
        OkGo.<File>get(jarUrl).execute(new AbsCallback<File>() {

            @Override
            public File convertResponse(okhttp3.Response response) throws Throwable {
                File cacheDir = cache.getParentFile();
                if (!cacheDir.exists())
                    cacheDir.mkdirs();
                if (response.body() == null) throw new IllegalStateException("JAR response body is empty");
                File temporary = new File(cacheDir, cache.getName() + ".download");
                long bytes = 0;
                try (InputStream input = response.body().byteStream();
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        bytes += count;
                    }
                    output.getFD().sync();
                } catch (Throwable throwable) {
                    temporary.delete();
                    throw throwable;
                }
                String actualMd5 = MD5.getFileMd5(temporary);
                if (!md5.isEmpty() && !actualMd5.equalsIgnoreCase(md5)) {
                    temporary.delete();
                    throw new IllegalStateException("JAR md5 mismatch");
                }
                if (cache.exists() && !cache.delete()) {
                    temporary.delete();
                    throw new IllegalStateException("Unable to replace cached JAR");
                }
                if (!temporary.renameTo(cache)) {
                    temporary.delete();
                    throw new IllegalStateException("Unable to publish downloaded JAR");
                }
                cache.setReadOnly();
                JarMd5Index.write(cache, md5.isEmpty() ? actualMd5 : md5);
                DiagnosticLog.info("Spider", "JAR 下载完成 bytes=" + bytes + " md5=" + (!md5.isEmpty()));
                return cache;
            }

            @Override
            public void onSuccess(Response<File> response) {
                if (response.body() != null && response.body().exists()) {
                    loadJarOnBackground(response.body().getAbsolutePath(), callback, startedAt);
                } else {
                    DiagnosticLog.error("Spider", "JAR 下载结果为空", System.currentTimeMillis() - startedAt);
                    callback.error("");
                }
            }

            @Override
            public void onError(Response<File> response) {
                super.onError(response);
                Throwable exception = response.getException();
                DiagnosticLog.error("Spider", "JAR 下载失败: " + (exception == null ? "未知错误" : exception.getClass().getSimpleName() + ": " + exception.getMessage()), System.currentTimeMillis() - startedAt);
                callback.error("");
            }
        });
    }

    /** DexClassLoader / Init 必须在后台；结果回调切回主线程。 */
    private void loadJarOnBackground(String path, JarLoadCallback callback, long startedAt) {
        jarLoadExecutor.execute(() -> loadJarAndPost(path, callback, startedAt));
    }

    private void loadJarAndPost(String path, JarLoadCallback callback, long startedAt) {
        boolean ok = jarLoader.load(path);
        LOG.i("SpiderManager", "JarLoader.load thread=" + Thread.currentThread().getName() + " ok=" + ok);
        if (ok) {
            DiagnosticLog.info("Spider", "JAR 装载完成", System.currentTimeMillis() - startedAt);
        } else {
            DiagnosticLog.error("Spider", "JAR 装载失败", System.currentTimeMillis() - startedAt);
        }
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
