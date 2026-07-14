package com.hotplato.tvbox.data

import com.hotplato.tvbox.util.HawkConfig
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsSnapshot(
    val apiUrl: String = "",
    val debugOpen: Boolean = false,
    val parseWebView: Boolean = true,
    val ijkCodec: String = "",
    val playType: Int = 0,
    val playRender: Int = 0,
    val playScale: Int = 0,
    val dohUrl: Int = 0,
    val homeRec: Int = 0,
    val searchView: Int = 0,
    val storeApi: String = "",
    val storeName: String = "",
)

object SettingsRepository {
    private val _settings = MutableStateFlow(readSnapshot())
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    fun refresh() {
        _settings.value = readSnapshot()
    }

    fun setApiUrl(url: String) {
        Hawk.put(HawkConfig.API_URL, url)
        refresh()
    }

    fun setDebugOpen(open: Boolean) {
        Hawk.put(HawkConfig.DEBUG_OPEN, open)
        refresh()
    }

    fun toggleDebugOpen() {
        setDebugOpen(!Hawk.get(HawkConfig.DEBUG_OPEN, false))
    }

    fun setParseWebView(system: Boolean) {
        Hawk.put(HawkConfig.PARSE_WEBVIEW, system)
        refresh()
    }

    fun toggleParseWebView() {
        setParseWebView(!Hawk.get(HawkConfig.PARSE_WEBVIEW, true))
    }

    fun setPlayType(type: Int) {
        Hawk.put(HawkConfig.PLAY_TYPE, type)
        refresh()
    }

    fun setPlayRender(render: Int) {
        Hawk.put(HawkConfig.PLAY_RENDER, render)
        refresh()
    }

    fun setPlayScale(scale: Int) {
        Hawk.put(HawkConfig.PLAY_SCALE, scale)
        refresh()
    }

    fun setDohUrl(index: Int) {
        Hawk.put(HawkConfig.DOH_URL, index)
        refresh()
    }

    fun setHomeRec(value: Int) {
        Hawk.put(HawkConfig.HOME_REC, value)
        refresh()
    }

    fun setSearchView(value: Int) {
        Hawk.put(HawkConfig.SEARCH_VIEW, value)
        refresh()
    }

    fun setIjkCodec(codec: String) {
        Hawk.put(HawkConfig.IJK_CODEC, codec)
        refresh()
    }

    fun setStore(url: String, name: String) {
        Hawk.put(HawkConfig.STORE_API, url)
        Hawk.put(HawkConfig.STORE_NAME, name)
        refresh()
    }

    fun update(block: (SettingsSnapshot) -> SettingsSnapshot) {
        _settings.update(block)
    }

    private fun readSnapshot(): SettingsSnapshot = SettingsSnapshot(
        apiUrl = Hawk.get(HawkConfig.API_URL, "") ?: "",
        debugOpen = Hawk.get(HawkConfig.DEBUG_OPEN, false),
        parseWebView = Hawk.get(HawkConfig.PARSE_WEBVIEW, true),
        ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "") ?: "",
        playType = Hawk.get(HawkConfig.PLAY_TYPE, 0),
        playRender = Hawk.get(HawkConfig.PLAY_RENDER, 0),
        playScale = Hawk.get(HawkConfig.PLAY_SCALE, 0),
        dohUrl = Hawk.get(HawkConfig.DOH_URL, 0),
        homeRec = Hawk.get(HawkConfig.HOME_REC, 0),
        searchView = Hawk.get(HawkConfig.SEARCH_VIEW, 0),
        storeApi = Hawk.get(HawkConfig.STORE_API, "") ?: "",
        storeName = Hawk.get(HawkConfig.STORE_NAME, "") ?: "",
    )
}
