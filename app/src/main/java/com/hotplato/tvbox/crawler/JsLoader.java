package com.hotplato.tvbox.crawler;


import android.util.Log;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import com.hotplato.tvbox.base.App;

import com.hotplato.tvbox.crawler.opt.JarMd5Index;
import com.hotplato.tvbox.crawler.opt.SpiderRuntime;
import com.hotplato.tvbox.util.FileUtils;
import com.hotplato.tvbox.util.LOG;
import com.hotplato.tvbox.util.MD5;

import com.hotplato.tvbox.util.js.JsSpider;
import com.lzy.okgo.OkGo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JsLoader {
    private static final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Class<?>> classes = new ConcurrentHashMap<>();
    public static void destroy() {
        for (Spider spider : spiders.values()){
            destroyQuietly(spider);
        }
        spiders.clear();
        classes.clear();
        SpiderRuntime.reset();
    }
    public void clear() {
        // Clearing the map alone leaves each JsSpider's QuickJS context and executor alive.
        // A configuration reload is a lifecycle boundary, so actively destroy old instances.
        for (Spider spider : spiders.values()) {
            destroyQuietly(spider);
        }
        spiders.clear();
        classes.clear();
    }

    public static void stopAll() {
        for (Spider spider : spiders.values()){
            spider.cancelByTag();
        }
    }

    private boolean loadClassLoader(String jar, String key) {
        boolean success = false;
        Class<?> classInit = null;
        try {
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_jsapi");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            DexClassLoader classLoader = new DexClassLoader(jar, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            int count = 0;
            do {
                try {
                    classInit = classLoader.loadClass("com.github.catvod.js.Method");
                    if (classInit != null) {
                        Log.i("JSLoader", "echo-自定义jsapi代码加载成功!");
                        success = true;
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                count++;
            } while (count < 5);

            if (success) {
                classes.put(key, classInit);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return success;
    }

    private Class<?> loadJarInternal(String jar, String md5, String key) {
        if (classes.containsKey(key)){
            Log.i("JSLoader", "echo-loadJarInternal cached");
            return classes.get(key);
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/" + key + ".jar");
        if (!md5.isEmpty()) {
            if (cache.exists() && JarMd5Index.matchesConfigured(cache, md5)) {
                Log.i("JSLoader", "jsapi md5 sidecar hit, skip full-file scan");
                loadClassLoader(cache.getAbsolutePath(), key);
                return classes.get(key);
            }
            if (cache.exists() && MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                JarMd5Index.write(cache, md5);
                loadClassLoader(cache.getAbsolutePath(), key);
                return classes.get(key);
            }
        }else {
            if (cache.exists() && !FileUtils.isWeekAgo(cache)) {
                if(loadClassLoader(cache.getAbsolutePath(), key)){
                    return classes.get(key);
                }
            }
        }
        File temporary = new File(cache.getParentFile(), cache.getName() + ".download");
        try {
            File parent = cache.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            Response response = OkGo.<File>get(jar).execute();
            if (response.body() == null) return null;
            InputStream is = response.body().byteStream();
            OutputStream os = new FileOutputStream(temporary);
            try {
                byte[] buffer = new byte[2048];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } finally {
                try {
                    is.close();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (!md5.isEmpty() && !MD5.getFileMd5(temporary).equalsIgnoreCase(md5)) {
                temporary.delete();
                LOG.i("JSLoader", "jsapi md5 mismatch, keeping previous cache");
                return null;
            }
            if (cache.exists() && !cache.delete()) {
                temporary.delete();
                return null;
            }
            if (!temporary.renameTo(cache)) {
                temporary.delete();
                return null;
            }
            cache.setReadOnly();
            if (!md5.isEmpty()) {
                JarMd5Index.write(cache, md5);
            } else {
                JarMd5Index.write(cache, MD5.getFileMd5(cache));
            }
            loadClassLoader(cache.getAbsolutePath(), key);
            return classes.get(key);
        } catch (Throwable e) {
            e.printStackTrace();
            temporary.delete();
        }
        return null;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (spiders.containsKey(key)){
            Log.i("JSLoader", "echo-getSpider cached");
            return spiders.get(key);
        }
        Spider sp = createSpider(key, api, ext, jar);
        spiders.put(key, sp);
        return sp;
    }

    /**
     * 创建 JS Spider 实例，不写入静态缓存（供实例池借还多实例）。
     */
    public Spider createSpider(String key, String api, String ext, String jar) {
        Class<?> classLoader = null;
        if (jar != null && !jar.isEmpty()) {
            String[] urls = jar.split(";md5;");
            String jarUrl = urls[0];
            String jarKey = MD5.string2MD5(jarUrl);
            String jarMd5 = urls.length > 1 ? urls[1].trim() : "";
            classLoader = loadJarInternal(jarUrl, jarMd5, jarKey);
        }
        try {
            Log.i("JSLoader", "echo-createSpider load");
            Spider sp = new JsSpider(key, api, classLoader);
            sp.init(SpiderHostContext.get(App.getInstance()), ext);
            return sp;
        } catch (Throwable th) {
            LOG.i("echo-getSpider-error "+th.getMessage());
            String detail = th.getMessage() != null ? th.getMessage() : th.getClass().getSimpleName();
            return new SpiderNull(SpiderFailReason.JS_LOAD_FAILED, detail);
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            Spider proxyFun = findProxySpider(params);
            if (proxyFun != null) {
                return proxyFun.proxyLocal(params);
            }
        } catch (Throwable th) {
        }
        return null;
    }

    /**
     * Proxy calls may run concurrently with normal spider creation.  Routing by the
     * last-created spider made those requests nondeterministic.  New proxy URLs can
     * use site/source/key; old URLs remain supported only when exactly one JS spider
     * exists, which is the sole unambiguous legacy case.
     */
    private Spider findProxySpider(Map<String, String> params) {
        String key = params.get("site");
        if (key == null || key.isEmpty()) key = params.get("source");
        if (key == null || key.isEmpty()) key = params.get("key");
        if (key != null && !key.isEmpty()) return spiders.get(key);
        return spiders.size() == 1 ? spiders.values().iterator().next() : null;
    }

    private static void destroyQuietly(Spider spider) {
        try {
            spider.cancelByTag();
            spider.destroy();
        } catch (Throwable ignored) {
        }
    }
}
