package com.hotplato.tvbox.crawler.opt

import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderNull
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.SourceBean
import com.hotplato.tvbox.crawler.SpiderManager
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * JAR：每 key 单实例（复用 getCSP）。
 * JS：同 key 最多 [MAX_JS_INSTANCES] 个借还。
 */
object SpiderInstancePool {
    private const val MAX_JS_INSTANCES = 2

    private val jsIdle = ConcurrentHashMap<String, ArrayDeque<Spider>>()

    fun reset() {
        for (queue in jsIdle.values) {
            synchronized(queue) {
                while (queue.isNotEmpty()) {
                    destroyQuietly(queue.removeFirst())
                }
            }
        }
        jsIdle.clear()
    }

    fun <T> withSpider(sourceBean: SourceBean, block: (Spider) -> T): T {
        val api = sourceBean.api
        if (SpiderManager.isJsSpiderApi(api)) {
            val spider = borrowJs(sourceBean)
            try {
                return block(spider)
            } finally {
                releaseJs(sourceBean.key, spider)
            }
        }
        val spider = ApiConfig.get().getCSP(sourceBean)
        return block(spider)
    }

    private fun borrowJs(sourceBean: SourceBean): Spider {
        val key = sourceBean.key
        val queue = jsIdle.getOrPut(key) { ArrayDeque() }
        synchronized(queue) {
            val existing = queue.pollFirst()
            if (existing != null) return existing
        }
        return ApiConfig.get().createJsSpider(sourceBean)
    }

    private fun releaseJs(key: String, spider: Spider) {
        if (spider is SpiderNull) {
            destroyQuietly(spider)
            return
        }
        val queue = jsIdle.getOrPut(key) { ArrayDeque() }
        synchronized(queue) {
            if (queue.size < MAX_JS_INSTANCES) {
                queue.addLast(spider)
            } else {
                destroyQuietly(spider)
            }
        }
    }

    private fun destroyQuietly(spider: Spider) {
        try {
            spider.cancelByTag()
            spider.destroy()
        } catch (_: Throwable) {
        }
    }
}
