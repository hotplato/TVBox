package com.hotplato.tvbox.crawler.opt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局有界并发 + 每 sourceKey 互斥，包装同步 Spider 调用。
 */
object SpiderDispatcher {
    private const val GLOBAL_PERMITS = 8
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    private val global = Semaphore(GLOBAL_PERMITS)
    private val keyMutexes = ConcurrentHashMap<String, Mutex>()

    fun reset() {
        keyMutexes.clear()
    }

    private fun mutexFor(key: String): Mutex =
        keyMutexes.getOrPut(key) { Mutex() }

    suspend fun <T> withSource(
        sourceKey: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        global.withPermit {
            mutexFor(sourceKey).withLock {
                withTimeout(timeoutMs) {
                    block()
                }
            }
        }
    }
}
