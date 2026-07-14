package com.hotplato.tvbox.crawler;

import com.hotplato.tvbox.util.LOG;

import java.security.Permission;

/**
 * 尽量拦截爬虫 JAR 里的 {@link System#exit}，避免「源不可用」时连带杀壳进程。
 * 新系统可能禁用 SecurityManager，失败则静默跳过。
 */
public final class ProcessExitGuard {
    private static final String TAG = "ProcessExitGuard";
    private static volatile boolean installed;

    private ProcessExitGuard() {
    }

    @SuppressWarnings("removal")
    public static void install() {
        if (installed) {
            return;
        }
        synchronized (ProcessExitGuard.class) {
            if (installed) {
                return;
            }
            try {
                System.setSecurityManager(new SecurityManager() {
                    @Override
                    public void checkPermission(Permission perm) {
                        // allow all
                    }

                    @Override
                    public void checkPermission(Permission perm, Object context) {
                        // allow all
                    }

                    @Override
                    public void checkExit(int status) {
                        if (shouldBlockExit()) {
                            LOG.i(TAG, "blocked System.exit(" + status + ")");
                            throw new SecurityException("blocked System.exit(" + status + ")");
                        }
                    }
                });
                installed = true;
                LOG.i(TAG, "installed");
            } catch (Throwable t) {
                LOG.i(TAG, "unavailable: " + t.getMessage());
            }
        }
    }

    private static boolean shouldBlockExit() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : st) {
            String cn = e.getClassName();
            // 允许本应用主动退出
            if (cn.contains("com.hotplato.tvbox.util.AppManager")) {
                return false;
            }
            // 爬虫 / 迅雷 / 混淆后的 catvod 包
            if (cn.contains("com.github.catvod")
                    || cn.contains("com.xunlei")
                    || cn.contains("catvod.spider")
                    || cn.contains("catvod.parser")
                    || cn.contains("parser.merge")) {
                return true;
            }
        }
        return false;
    }
}
