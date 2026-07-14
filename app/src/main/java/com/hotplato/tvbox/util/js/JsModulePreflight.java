package com.hotplato.tvbox.util.js;

import android.text.TextUtils;

import com.hotplato.tvbox.util.FileUtils;
import com.whl.quickjs.wrapper.UriUtil;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在进入 QuickJS evaluate 前预拉 import/require 依赖。
 * 远程模块拉空时直接失败，避免 compileModule(\"\") / 残缺模块导致原生 SIGSEGV 杀进程。
 */
public final class JsModulePreflight {
    private static final int MAX_MODULES = 64;

    private static final Pattern IMPORT_OR_REQUIRE = Pattern.compile(
            "(?:import\\s*\\(\\s*|import\\s*(?:[^'\"\\n;()]*?from\\s*)?|export\\s+[^'\"\\n]*?from\\s+|require\\s*\\(\\s*)['\"]([^'\"]+)['\"]",
            Pattern.MULTILINE);

    private JsModulePreflight() {
    }

    /**
     * @return 缺失的模块名；全部可用则 null
     */
    public static String findMissing(String entryApi, String entryContent) {
        if (TextUtils.isEmpty(entryContent)) {
            return entryApi;
        }
        // 二进制 / 缓存字节码头不扫 import
        if (entryContent.startsWith("//bb") || entryContent.startsWith("//DRPY")) {
            return null;
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        seen.add(entryApi);
        enqueueImports(entryContent, entryApi, queue, seen);

        while (!queue.isEmpty() && seen.size() <= MAX_MODULES) {
            String mod = queue.removeFirst();
            String body = FileUtils.loadModule(mod);
            if (TextUtils.isEmpty(body)) {
                return mod;
            }
            if (body.startsWith("//bb") || body.startsWith("//DRPY")) {
                continue;
            }
            enqueueImports(body, mod, queue, seen);
        }
        return null;
    }

    private static void enqueueImports(String content, String base, ArrayDeque<String> queue, Set<String> seen) {
        Matcher m = IMPORT_OR_REQUIRE.matcher(content);
        while (m.find()) {
            String spec = m.group(1);
            if (TextUtils.isEmpty(spec) || spec.startsWith("node:") || spec.equals("http") || spec.equals("https")) {
                continue;
            }
            String resolved = resolve(base, spec);
            if (resolved == null || !seen.add(resolved)) {
                continue;
            }
            // assets / 本地名交给 loadModule；远程失败才是崩溃源
            queue.addLast(resolved);
        }
    }

    private static String resolve(String base, String spec) {
        if (spec.startsWith("http://") || spec.startsWith("https://")
                || spec.startsWith("assets://") || spec.startsWith("file://")
                || spec.startsWith("clan://")) {
            return spec;
        }
        if (base != null && (base.startsWith("http://") || base.startsWith("https://")
                || base.startsWith("assets://"))) {
            try {
                return UriUtil.resolve(base, spec);
            } catch (Throwable ignored) {
                return spec;
            }
        }
        return spec;
    }
}
