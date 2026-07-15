package com.hotplato.tvbox.crawler.opt

import com.github.catvod.crawler.SpiderNull
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.SourceBean
import com.hotplato.tvbox.util.LOG
import com.hotplato.tvbox.util.DiagnosticLog
import org.json.JSONObject

/**
 * Suspend 门面：缓存 → 调度 → 实例池 → 同步 Spider API。
 */
object SpiderGateway {

    suspend fun homeContent(sourceBean: SourceBean): String? {
        val key = SpiderResultCache.homeKey(sourceBean.key)
        SpiderResultCache.get(key)?.let { logCacheHit(sourceBean, "home"); return it }
        return invoke(sourceBean, "home", key, SpiderResultCache.TTL_HOME_MS) { sp ->
            sp.homeContent(true)
        }
    }

    suspend fun homeVideoContent(sourceBean: SourceBean): String? {
        val key = SpiderResultCache.homeVideoKey(sourceBean.key)
        SpiderResultCache.get(key)?.let { logCacheHit(sourceBean, "homeVideo"); return it }
        return invoke(sourceBean, "homeVideo", key, SpiderResultCache.TTL_HOME_MS) { sp ->
            sp.homeVideoContent()
        }
    }

    suspend fun categoryContent(
        sourceBean: SourceBean,
        tid: String,
        pg: String,
        filterSelect: java.util.HashMap<String, String>?,
    ): String? {
        val filter = filterSelect?.toString() ?: ""
        val key = SpiderResultCache.categoryKey(sourceBean.key, tid, pg, filter)
        SpiderResultCache.get(key)?.let { logCacheHit(sourceBean, "category"); return it }
        return invoke(sourceBean, "category tid=$tid pg=$pg", key, SpiderResultCache.TTL_CATEGORY_MS) { sp ->
            sp.categoryContent(tid, pg, true, filterSelect)
        }
    }

    suspend fun detailContent(sourceBean: SourceBean, id: String): String? {
        val key = SpiderResultCache.detailKey(sourceBean.key, id)
        SpiderResultCache.get(key)?.let { logCacheHit(sourceBean, "detail"); return it }
        return invoke(sourceBean, "detail id=$id", key, SpiderResultCache.TTL_DETAIL_MS) { sp ->
            sp.detailContent(listOf(id))
        }
    }

    suspend fun searchContent(sourceBean: SourceBean, wd: String, quick: Boolean): String? {
        val key = SpiderResultCache.searchKey(sourceBean.key, wd, quick)
        SpiderResultCache.get(key)?.let { logCacheHit(sourceBean, "search"); return it }
        return invoke(sourceBean, "search quick=$quick queryLength=${wd.length}", key, SpiderResultCache.TTL_SEARCH_MS) { sp ->
            sp.searchContent(wd, quick)
        }
    }

    suspend fun playerContent(
        sourceBean: SourceBean,
        flag: String,
        url: String,
        vipFlags: List<String>,
    ): String? {
        val cacheKey = SpiderResultCache.playKey(sourceBean.key, flag, url)
        SpiderResultCache.get(cacheKey)?.let { logCacheHit(sourceBean, "player"); return it }
        val raw = invokeNoCache(sourceBean, "player flag=$flag urlLength=${url.length}") { sp ->
            sp.playerContent(flag, url, vipFlags)
        }?.takeIf { it.isNotBlank() } ?: return null
        val normalized = try {
            PlayUrlFastPath.normalize(JSONObject(raw))?.toString() ?: raw
        } catch (_: Throwable) {
            raw
        }
        if (normalized.isNotBlank()) {
            SpiderResultCache.put(cacheKey, normalized, SpiderResultCache.TTL_PLAY_MS)
        }
        return normalized
    }

    private suspend fun invokeNoCache(
        sourceBean: SourceBean,
        operation: String,
        call: (com.github.catvod.crawler.Spider) -> String?,
    ): String? {
        val startedAt = System.currentTimeMillis()
        DiagnosticLog.info("SpiderCall", "开始 key=${sourceBean.key} $operation")
        return try {
            SpiderDispatcher.withSource(sourceBean.key) {
                SpiderInstancePool.withSpider(sourceBean) { sp ->
                    if (sp is SpiderNull) {
                        LOG.i(
                            "SpiderGateway",
                            "SpiderNull key=${sourceBean.key} reason=${sp.reason}",
                        )
                        return@withSpider null
                    }
                    call(sp).also { logResult(sourceBean, operation, it, startedAt) }
                }
            }
        } catch (t: Throwable) {
            DiagnosticLog.error("SpiderCall", "失败 key=${sourceBean.key} $operation ${t.javaClass.simpleName}: ${t.message}", System.currentTimeMillis() - startedAt)
            LOG.i(
                "SpiderGateway",
                "invoke failed key=${sourceBean.key}: ${t.javaClass.simpleName}: ${t.message}",
            )
            null
        }
    }

    private suspend fun invoke(
        sourceBean: SourceBean,
        operation: String,
        cacheKey: String,
        ttlMs: Long,
        call: (com.github.catvod.crawler.Spider) -> String?,
    ): String? {
        val startedAt = System.currentTimeMillis()
        DiagnosticLog.info("SpiderCall", "开始 key=${sourceBean.key} $operation")
        return try {
            SpiderDispatcher.withSource(sourceBean.key) {
                SpiderInstancePool.withSpider(sourceBean) { sp ->
                    if (sp is SpiderNull) {
                        LOG.i(
                            "SpiderGateway",
                            "SpiderNull key=${sourceBean.key} reason=${sp.reason}",
                        )
                        return@withSpider null
                    }
                    val result = call(sp)
                    if (!result.isNullOrBlank()) {
                        SpiderResultCache.put(cacheKey, result, ttlMs)
                    }
                    logResult(sourceBean, operation, result, startedAt)
                    result
                }
            }
        } catch (t: Throwable) {
            DiagnosticLog.error("SpiderCall", "失败 key=${sourceBean.key} $operation ${t.javaClass.simpleName}: ${t.message}", System.currentTimeMillis() - startedAt)
            LOG.i(
                "SpiderGateway",
                "invoke failed key=${sourceBean.key}: ${t.javaClass.simpleName}: ${t.message}",
            )
            null
        }
    }

    private fun logCacheHit(sourceBean: SourceBean, operation: String) {
        DiagnosticLog.info("SpiderCall", "缓存命中 key=${sourceBean.key} $operation")
    }

    private fun logResult(sourceBean: SourceBean, operation: String, result: String?, startedAt: Long) {
        DiagnosticLog.info(
            "SpiderCall",
            "完成 key=${sourceBean.key} $operation resultBytes=${result?.length ?: 0}",
            System.currentTimeMillis() - startedAt,
        )
    }
}
