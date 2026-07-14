package com.hotplato.tvbox.crawler.opt

import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.SourceBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Java 回调桥：SourceViewModel 不直接写协程。
 */
object SpiderGatewayBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun interface StringCallback {
        fun onResult(json: String?)
    }

    @JvmStatic
    fun homeContent(sourceBean: SourceBean, callback: StringCallback) {
        scope.launch {
            callback.onResult(SpiderGateway.homeContent(sourceBean))
        }
    }

    @JvmStatic
    fun homeVideoContent(sourceBean: SourceBean, callback: StringCallback) {
        scope.launch {
            callback.onResult(SpiderGateway.homeVideoContent(sourceBean))
        }
    }

    @JvmStatic
    fun categoryContent(
        sourceBean: SourceBean,
        tid: String,
        pg: String,
        filterSelect: java.util.HashMap<String, String>?,
        callback: StringCallback,
    ) {
        scope.launch {
            callback.onResult(SpiderGateway.categoryContent(sourceBean, tid, pg, filterSelect))
        }
    }

    @JvmStatic
    fun detailContent(sourceBean: SourceBean, id: String, callback: StringCallback) {
        scope.launch {
            callback.onResult(SpiderGateway.detailContent(sourceBean, id))
        }
    }

    @JvmStatic
    fun searchContent(
        sourceBean: SourceBean,
        wd: String,
        quick: Boolean,
        callback: StringCallback,
    ) {
        scope.launch {
            callback.onResult(SpiderGateway.searchContent(sourceBean, wd, quick))
        }
    }

    @JvmStatic
    fun playerContent(
        sourceBean: SourceBean,
        flag: String,
        url: String,
        callback: StringCallback,
    ) {
        scope.launch {
            val vip = ApiConfig.get().vipParseFlags ?: emptyList()
            callback.onResult(SpiderGateway.playerContent(sourceBean, flag, url, vip))
        }
    }
}
