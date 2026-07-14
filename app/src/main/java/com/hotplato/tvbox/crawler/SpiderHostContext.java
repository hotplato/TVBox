package com.hotplato.tvbox.crawler;

import android.app.Application;
import android.content.Context;
import android.view.WindowManager;

import com.hotplato.tvbox.util.LOG;

import java.lang.reflect.Proxy;

/**
 * 交给动态爬虫 JAR 的宿主 Application。
 * <p>
 * JAR 的 {@code Init.init} 会把入参强转 {@link Application}，因此必须是 Application 子类。
 * <b>禁止伪装包名</b>：伪装成 {@code com.fongmi.android.tv} 会导致 Toast 走
 * {@code enqueueTextToast} 时系统抛 {@code SecurityException: Package ... is not owned by uid}，主线程直接 FATAL。
 */
public final class SpiderHostContext extends Application {
    private static final String TAG = "SpiderHostContext";

    private static volatile SpiderHostContext instance;

    private SpiderHostContext() {
    }

    public static SpiderHostContext get(Context base) {
        if (instance == null) {
            synchronized (SpiderHostContext.class) {
                if (instance == null) {
                    Application real = resolveApplication(base);
                    SpiderHostContext host = new SpiderHostContext();
                    host.attachBaseContext(real);
                    instance = host;
                }
            }
        }
        return instance;
    }

    private static Application resolveApplication(Context base) {
        if (base instanceof Application) {
            return (Application) base;
        }
        Context app = base.getApplicationContext();
        if (app instanceof Application) {
            return (Application) app;
        }
        throw new IllegalStateException("SpiderHostContext requires Application");
    }

    @Override
    public Context getApplicationContext() {
        // 保持自身，以便 getSystemService(WINDOW) 包装生效
        return this;
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (WINDOW_SERVICE.equals(name) && service != null) {
            return wrapWindowManager((WindowManager) service);
        }
        return service;
    }

    private Object wrapWindowManager(WindowManager wm) {
        return Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(),
                new Class<?>[]{WindowManager.class},
                (proxy, method, args) -> {
                    if ("addView".equals(method.getName())
                            && args != null
                            && args.length > 0
                            && args[0] instanceof android.view.View) {
                        if (RemoteToastFilter.shouldBlockView((android.view.View) args[0])) {
                            LOG.i(TAG, "blocked WindowManager toast");
                            return null;
                        }
                    }
                    return method.invoke(wm, args);
                });
    }
}
