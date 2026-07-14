package com.hotplato.tvbox.crawler.opt

import com.hotplato.tvbox.util.LOG
import java.io.File

/**
 * JAR 旁路 MD5：与配置 `;md5;` 一致时跳过全文件扫描。
 */
object JarMd5Index {
    @JvmStatic
    fun sidecar(jarFile: File): File = File(jarFile.parentFile, jarFile.name + ".md5")

    @JvmStatic
    fun read(jarFile: File): String? {
        val side = sidecar(jarFile)
        if (!side.exists()) return null
        return try {
            side.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            LOG.i("JarMd5Index", "read failed: ${t.message}")
            null
        }
    }

    @JvmStatic
    fun write(jarFile: File, md5: String) {
        if (md5.isBlank()) return
        val side = sidecar(jarFile)
        try {
            side.parentFile?.mkdirs()
            side.writeText(md5.trim().lowercase(), Charsets.UTF_8)
        } catch (t: Throwable) {
            LOG.i("JarMd5Index", "write failed: ${t.message}")
        }
    }

    @JvmStatic
    fun delete(jarFile: File) {
        try {
            sidecar(jarFile).delete()
        } catch (_: Throwable) {
        }
    }

    /** 旁路命中配置 MD5 时可跳过全文件 MD5。 */
    @JvmStatic
    fun matchesConfigured(jarFile: File, configuredMd5: String): Boolean {
        if (configuredMd5.isBlank() || !jarFile.exists()) return false
        val side = read(jarFile) ?: return false
        return side.equals(configuredMd5.trim(), ignoreCase = true)
    }
}
