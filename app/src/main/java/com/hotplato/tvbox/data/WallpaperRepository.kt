package com.hotplato.tvbox.data

import android.graphics.BitmapFactory
import android.system.Os
import com.google.gson.JsonParser
import com.hotplato.tvbox.base.App
import com.hotplato.tvbox.util.HawkConfig
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class WallpaperState(
    val source: String,
    val cacheFile: File?,
    val version: Long,
) {
    val hasCache: Boolean get() = cacheFile != null
}

object WallpaperRepository {
    const val SOURCE_DEFAULT = "default"
    const val SOURCE_WALLHAVEN = "wallhaven"
    const val SOURCE_BING = "bing"
    const val SOURCE_UPLOAD = "upload"
    const val MAX_BYTES = 20L * 1024L * 1024L
    private const val WALLHAVEN_API = "https://wallhaven.cc/api/v1/search?categories=100&purity=100&atleast=1920x1080&sorting=random&order=desc"
    private const val BING_API = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN"

    private val cacheFile get() = File(App.getInstance().filesDir, "wallpaper.jpg")
    private val legacyCacheFile get() = File(App.getInstance().filesDir, "wallpaper.img")
    private val tempFile get() = File(App.getInstance().filesDir, "wallpaper.jpg.tmp")
    private val activeCacheFile: File?
        get() {
            migrateLegacyCache()
            return cacheFile.takeIf { it.isFile && it.length() > 0L }
        }
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    @JvmStatic fun currentSource(): String = _state.value.source
    @JvmStatic fun hasCache(): Boolean = _state.value.hasCache
    @JvmStatic fun cachePath(): String? = _state.value.cacheFile?.absolutePath

    @JvmStatic @Synchronized fun applyProvider(provider: String): String? {
        if (provider != SOURCE_WALLHAVEN && provider != SOURCE_BING) return "不支持的壁纸服务"
        val url = try { resolveImageUrl(provider) } catch (e: Exception) { return safeMessage(e) }
        return downloadAndApply(url, provider)
    }

    @JvmStatic @Synchronized fun refresh(): String? {
        val source = currentSource()
        if (source != SOURCE_WALLHAVEN && source != SOURCE_BING) return "当前壁纸不是在线服务"
        return applyProvider(source)
    }

    @JvmStatic @Synchronized fun applyUpload(file: File): String? {
        if (!file.isFile || file.length() <= 0L) return "未找到上传文件"
        if (file.length() > MAX_BYTES) return "图片不能超过 20 MiB"
        if (!isSupportedImage(file)) return "仅支持 JPEG、PNG、WebP 图片"
        return try {
            replaceCache(file)
            saveSource(SOURCE_UPLOAD)
            null
        } catch (e: Exception) { safeMessage(e) }
    }

    @JvmStatic @Synchronized fun clear(): String? {
        return try {
            if (cacheFile.exists() && !cacheFile.delete()) {
                throw IllegalStateException("无法删除当前壁纸")
            }
            legacyCacheFile.delete()
            tempFile.delete()
            saveSource(SOURCE_DEFAULT)
            null
        } catch (e: Exception) { safeMessage(e) }
    }

    private fun resolveImageUrl(provider: String): String {
        val connection = open(URL(if (provider == SOURCE_WALLHAVEN) WALLHAVEN_API else BING_API))
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JsonParser().parse(body).asJsonObject
        return if (provider == SOURCE_WALLHAVEN) {
            json.getAsJsonArray("data").firstOrNull()?.asJsonObject?.get("path")?.asString
                ?: throw IllegalStateException("壁纸服务没有返回图片")
        } else {
            val path = json.getAsJsonArray("images").firstOrNull()?.asJsonObject?.get("url")?.asString
                ?: throw IllegalStateException("Bing 没有返回图片")
            if (path.startsWith("http")) path else "https://www.bing.com$path"
        }
    }

    private fun downloadAndApply(url: String, provider: String): String? {
        return try {
            val connection = open(URL(url))
            if (connection.contentLengthLong > MAX_BYTES) return "图片不能超过 20 MiB"
            tempFile.delete()
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    copyWithLimit(input, output)
                    output.fd.sync()
                }
            }
            if (!isSupportedImage(tempFile)) throw IllegalStateException("服务返回的内容不是支持的图片")
            replaceCache(tempFile)
            saveSource(provider)
            null
        } catch (e: Exception) { tempFile.delete(); safeMessage(e) }
    }

    private fun replaceCache(source: File) {
        if (source != tempFile) {
            tempFile.delete()
            FileInputStream(source).use { input ->
                FileOutputStream(tempFile).use { output ->
                    copyWithLimit(input, output)
                    output.fd.sync()
                }
            }
        }
        Os.rename(tempFile.absolutePath, cacheFile.absolutePath)
    }

    private fun migrateLegacyCache() {
        if (cacheFile.isFile || !legacyCacheFile.isFile) return
        try {
            Os.rename(legacyCacheFile.absolutePath, cacheFile.absolutePath)
        } catch (_: Exception) {
            try {
                replaceCache(legacyCacheFile)
                legacyCacheFile.delete()
            } catch (_: Exception) {
                tempFile.delete()
            }
        }
    }

    private fun copyWithLimit(input: java.io.InputStream, output: FileOutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) throw IllegalStateException("图片不能超过 20 MiB")
            output.write(buffer, 0, count)
        }
    }

    private fun isSupportedImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return false
        FileInputStream(file).use { input ->
            val header = ByteArray(12)
            val count = input.read(header)
            if (count < 3) return false
            val jpeg = (header[0].toInt() and 0xFF) == 0xFF &&
                (header[1].toInt() and 0xFF) == 0xD8 &&
                (header[2].toInt() and 0xFF) == 0xFF
            val png = count >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
            val webp = count >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
            return jpeg || png || webp
        }
    }

    private fun open(url: URL): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "TVBox/1.0")
        if (responseCode !in 200..299) throw IllegalStateException("壁纸服务请求失败: $responseCode")
    }

    private fun saveSource(source: String) {
        Hawk.put(HawkConfig.WALLPAPER_SOURCE, source)
        val cache = activeCacheFile
        _state.value = WallpaperState(source, cache, _state.value.version + 1L)
    }

    private fun readState(): WallpaperState {
        val stored = Hawk.get(HawkConfig.WALLPAPER_SOURCE, SOURCE_DEFAULT) ?: SOURCE_DEFAULT
        val cache = activeCacheFile
        val source = if (cache != null) stored else SOURCE_DEFAULT
        return WallpaperState(source, cache, 0L)
    }
    private fun safeMessage(e: Exception) = e.message?.takeIf { it.isNotBlank() } ?: "壁纸操作失败"
}
