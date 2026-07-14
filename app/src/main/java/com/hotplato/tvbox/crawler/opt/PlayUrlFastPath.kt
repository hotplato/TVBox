package com.hotplato.tvbox.crawler.opt

import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

/**
 * 播放 JSON 直出规范化：可播直链则强制 parse=0。
 */
object PlayUrlFastPath {
    private val DIRECT_EXT = Pattern.compile(
        "\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?|#|$)",
        Pattern.CASE_INSENSITIVE,
    )

    @JvmStatic
    fun normalize(raw: JSONObject?): JSONObject? {
        if (raw == null) return null
        return try {
            val parse = raw.optString("parse", "1") == "1" || raw.optInt("parse", 1) == 1
            val jx = raw.optString("jx", "0") == "1" || raw.optInt("jx", 0) == 1
            val url = raw.optString("url", "")
            val playUrl = raw.optString("playUrl", "")
            val combined = (playUrl + url).trim()
            if ((!parse && !jx) || isDirectPlayUrl(combined) || isDirectPlayUrl(url)) {
                raw.put("parse", 0)
                raw.put("jx", 0)
            }
            raw
        } catch (_: Throwable) {
            raw
        }
    }

    @JvmStatic
    fun isDirectPlayUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.US)
        if (lower.contains("=http") || lower.contains("=https")) return false
        // strip query for path match: use regex on full string
        return DIRECT_EXT.matcher(url).find()
    }
}
