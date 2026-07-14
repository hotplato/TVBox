package com.hotplato.tvbox.crawler;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.hotplato.tvbox.util.LOG;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 过滤第三方 JAR / 接口弹出的客户端白名单恐吓 Toast。
 * 通过 Hook {@link Toast} 内部 INotificationManager，并配合 {@link SpiderHostContext} 的 WindowManager 包装。
 */
public final class RemoteToastFilter {
    private static final String TAG = "RemoteToastFilter";

    private static final String[] BLOCK_KEYWORDS = {
            "不能与本接口完全兼容",
            "检测到您使用的不是原版",
            "本接口完全免费",
    };

    private static volatile boolean installed;

    private RemoteToastFilter() {
    }

    public static boolean shouldBlock(CharSequence text) {
        if (text == null) {
            return false;
        }
        String s = text.toString();
        if (s.isEmpty()) {
            return false;
        }
        for (String keyword : BLOCK_KEYWORDS) {
            if (s.contains(keyword)) {
                return true;
            }
        }
        // 同系恐吓文案：点名 EasyBox / OK影视 且谈「兼容」
        return (s.contains("EasyBox") || s.contains("OK影视")) && s.contains("兼容");
    }

    public static boolean shouldBlockView(View view) {
        return shouldBlock(extractText(view));
    }

    public static CharSequence extractText(View view) {
        if (view == null) {
            return null;
        }
        if (view instanceof TextView) {
            return ((TextView) view).getText();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < group.getChildCount(); i++) {
                CharSequence child = extractText(group.getChildAt(i));
                if (child != null && child.length() > 0) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(child);
                }
            }
            return sb.length() > 0 ? sb : null;
        }
        return null;
    }

    public static void install() {
        if (installed) {
            return;
        }
        synchronized (RemoteToastFilter.class) {
            if (installed) {
                return;
            }
            try {
                hookToastService();
                installed = true;
                LOG.i(TAG, "toast filter installed");
            } catch (Throwable t) {
                LOG.e(TAG, "toast filter install failed: " + t.getMessage());
            }
        }
    }

    private static void hookToastService() throws Exception {
        Method getService = Toast.class.getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object service = getService.invoke(null);
        if (service == null) {
            // 触发一次以初始化 sService（不 show）
            Toast.makeText(com.hotplato.tvbox.base.App.getInstance(), " ", Toast.LENGTH_SHORT);
            service = getService.invoke(null);
        }
        if (service == null) {
            return;
        }
        if (Proxy.isProxyClass(service.getClass())) {
            return;
        }
        Class<?> iNotificationManager = Class.forName("android.app.INotificationManager");
        final Object original = service;
        Object proxy = Proxy.newProxyInstance(
                iNotificationManager.getClassLoader(),
                new Class<?>[]{iNotificationManager},
                (p, method, args) -> {
                    String name = method.getName();
                    if (name != null && (name.contains("enqueueToast") || name.contains("enqueueTextToast"))) {
                        CharSequence text = findCharSequence(args);
                        if (shouldBlock(text)) {
                            LOG.i(TAG, "blocked toast: " + text);
                            return defaultValue(method.getReturnType());
                        }
                    }
                    try {
                        return method.invoke(original, args);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        // 包名伪装或跨包 Toast 会触发，吞掉以免主线程 FATAL 杀进程
                        if (cause instanceof SecurityException
                                && String.valueOf(cause.getMessage()).contains("not owned by uid")) {
                            LOG.i(TAG, "swallowed toast SecurityException: " + cause.getMessage());
                            return defaultValue(method.getReturnType());
                        }
                        throw e;
                    }
                });
        setToastService(proxy);
    }

    private static void setToastService(Object proxy) throws Exception {
        Field field;
        try {
            field = Toast.class.getDeclaredField("sService");
        } catch (NoSuchFieldException e) {
            // 部分 ROM 字段名不同，忽略（仍可由 SpiderHostContext 拦截旧路径 Toast）
            LOG.e(TAG, "Toast.sService not found");
            return;
        }
        field.setAccessible(true);
        try {
            field.set(null, proxy);
        } catch (IllegalAccessException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                forceSet(field, proxy);
            } else {
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                field.set(null, proxy);
            }
        }
    }

    private static void forceSet(Field field, Object value) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", Field.class);
        Method staticFieldBase = unsafeClass.getMethod("staticFieldBase", Field.class);
        Method putObject = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
        Object base = staticFieldBase.invoke(unsafe, field);
        long offset = (Long) staticFieldOffset.invoke(unsafe, field);
        putObject.invoke(unsafe, base, offset, value);
    }

    private static CharSequence findCharSequence(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof CharSequence) {
                return (CharSequence) arg;
            }
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
