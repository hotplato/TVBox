package com.hotplato.tvbox.crawler;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.hotplato.tvbox.base.App;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader {
    private DexClassLoader classLoader = null;
    private ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private Method proxyFun = null;

    /**
     * 不要在主线程调用我
     *
     * @param cache
     */
    public boolean load(String cache) {
        spiders.clear();
        proxyFun = null;
        boolean success = false;
        try {
            File jar = new File(cache);
            // Android 14+：动态加载的 DEX/JAR 必须为只读，否则抛 SecurityException
            if (jar.exists() && !jar.setReadOnly()) {
                System.err.println("Failed to set csp.jar read-only: " + jar.getAbsolutePath());
            }
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_csp");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            classLoader = new DexClassLoader(jar.getAbsolutePath(), cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            // make force wait here, some device async dex load
            int count = 0;
            do {
                try {
                    Class classInit = classLoader.loadClass("com.github.catvod.spider.Init");
                    if (classInit != null) {
                        Method method = classInit.getMethod("init", Context.class);
                        method.invoke(null, App.getInstance());
                        System.out.println("自定义爬虫代码加载成功!");
                        success = true;
                        try {
                            Class proxy = classLoader.loadClass("com.github.catvod.spider.Proxy");
                            Method mth = proxy.getMethod("proxy", Map.class);
                            proxyFun = mth;
                        } catch (Throwable th) {

                        }
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                count++;
            } while (count < 5);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return success;
    }

    /**
     * JAR 爬虫 api 形如 csp_Xxx；.js / http(s) URL 属于 drpy/JS 源，需 JsLoader（本仓库未集成）。
     */
    static boolean isJarSpiderApi(String api) {
        if (api == null || api.isEmpty())
            return false;
        if (api.contains("://") || api.contains("/") || api.contains("\\"))
            return false;
        if (api.endsWith(".js") || api.contains(".js?") || api.contains(".py"))
            return false;
        String clsKey = api.startsWith("csp_") ? api.substring(4) : api;
        return !clsKey.isEmpty() && clsKey.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    public Spider getSpider(String key, String cls, String ext) {
        if (spiders.containsKey(key))
            return spiders.get(key);
        if (!isJarSpiderApi(cls)) {
            // 缓存空实现，避免首页分类循环反复 ClassNotFoundException 刷屏
            Spider nullSpider = new SpiderNull();
            spiders.put(key, nullSpider);
            return nullSpider;
        }
        if (classLoader == null)
            return new SpiderNull();
        String clsKey = cls.startsWith("csp_") ? cls.substring(4) : cls;
        try {
            Spider sp = (Spider) classLoader.loadClass("com.github.catvod.spider." + clsKey).newInstance();
            sp.init(App.getInstance(), ext);
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
            Spider nullSpider = new SpiderNull();
            spiders.put(key, nullSpider);
            return nullSpider;
        }
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        try {
            String clsKey = "Json" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            Class jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        try {
            String clsKey = "Mix" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            Class jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, name, flag, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public Object[] proxyInvoke(Map params) {
        try {
            if (proxyFun != null) {
                return (Object[]) proxyFun.invoke(null, params);
            }
        } catch (Throwable th) {

        }
        return null;
    }
}
