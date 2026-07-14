package com.hotplato.tvbox.crawler.opt

import android.util.LruCache

/**
 * Spider 结果内存缓存（LRU + TTL）。
 */
object SpiderResultCache {
    private const val MAX_ENTRIES = 64

    const val TTL_HOME_MS = 60_000L
    const val TTL_CATEGORY_MS = 60_000L
    const val TTL_SEARCH_MS = 30_000L
    const val TTL_DETAIL_MS = 120_000L
    const val TTL_PLAY_MS = 90_000L

    private data class Entry(val value: String, val expireAt: Long)

    private val cache = object : LruCache<String, Entry>(MAX_ENTRIES) {}

    @Synchronized
    fun get(key: String): String? {
        val entry = cache.get(key) ?: return null
        if (System.currentTimeMillis() > entry.expireAt) {
            cache.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: String, ttlMs: Long) {
        if (value.isBlank()) return
        cache.put(key, Entry(value, System.currentTimeMillis() + ttlMs))
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }

    fun homeKey(sourceKey: String) = "home|$sourceKey"
    fun homeVideoKey(sourceKey: String) = "homeVideo|$sourceKey"
    fun categoryKey(sourceKey: String, tid: String, pg: String, filter: String) =
        "cat|$sourceKey|$tid|$pg|$filter"

    fun searchKey(sourceKey: String, wd: String, quick: Boolean) =
        "search|$sourceKey|$wd|$quick"

    fun detailKey(sourceKey: String, id: String) = "detail|$sourceKey|$id"
    fun playKey(sourceKey: String, flag: String, url: String) = "play|$sourceKey|$flag|$url"
}
