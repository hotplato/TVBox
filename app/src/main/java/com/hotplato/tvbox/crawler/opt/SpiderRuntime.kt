package com.hotplato.tvbox.crawler.opt

/** 配置重载时清空调度 / 池 / 缓存。 */
object SpiderRuntime {
    @JvmStatic
    fun reset() {
        SpiderResultCache.clear()
        SpiderDispatcher.reset()
        SpiderInstancePool.reset()
    }
}
