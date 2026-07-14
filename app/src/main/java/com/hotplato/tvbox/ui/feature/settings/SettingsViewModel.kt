package com.hotplato.tvbox.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.data.SettingsRepository
import com.hotplato.tvbox.data.SettingsSnapshot
import com.hotplato.tvbox.tvplayer.TvPlayer
import com.hotplato.tvbox.util.OkGoHelper
import com.hotplato.tvbox.util.PlayerHelper
import okhttp3.HttpUrl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel : ViewModel() {
    val uiState: StateFlow<SettingsSnapshot> = SettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.settings.value)

    fun toggleDebug() = SettingsRepository.toggleDebugOpen()

    fun toggleParseWebView() = SettingsRepository.toggleParseWebView()

    fun saveApiUrl(url: String) {
        val old = SettingsRepository.settings.value.apiUrl
        SettingsRepository.setApiUrl(url)
        if (old != url) {
            ApiConfig.clearStoreSelection()
        }
    }

    fun selectStore(url: String, name: String) {
        SettingsRepository.setStore(url, name)
    }

    fun cyclePlayType() {
        val current = PlayerHelper.normalizePlayerType(SettingsRepository.settings.value.playType)
        val next = if (current == TvPlayer.TYPE_SYSTEM) TvPlayer.TYPE_MEDIA3 else TvPlayer.TYPE_SYSTEM
        SettingsRepository.setPlayType(next)
    }

    fun cyclePlayRender() {
        val next = if (SettingsRepository.settings.value.playRender == 0) 1 else 0
        SettingsRepository.setPlayRender(next)
    }

    fun cyclePlayScale() {
        val scales = intArrayOf(
            TvPlayer.SCREEN_SCALE_DEFAULT,
            TvPlayer.SCREEN_SCALE_16_9,
            TvPlayer.SCREEN_SCALE_4_3,
            TvPlayer.SCREEN_SCALE_MATCH_PARENT,
            TvPlayer.SCREEN_SCALE_ORIGINAL,
            TvPlayer.SCREEN_SCALE_CENTER_CROP,
        )
        val current = SettingsRepository.settings.value.playScale
        val idx = scales.indexOf(current).let { if (it < 0) 0 else it }
        SettingsRepository.setPlayScale(scales[(idx + 1) % scales.size])
    }

    fun cycleDoh() {
        val size = OkGoHelper.dnsHttpsList.size.coerceAtLeast(1)
        val next = (SettingsRepository.settings.value.dohUrl + 1) % size
        SettingsRepository.setDohUrl(next)
        val url = OkGoHelper.getDohUrl(next)
        OkGoHelper.dnsOverHttps.setUrl(if (url.isEmpty()) null else HttpUrl.parse(url))
    }

    fun cycleHomeRec() {
        val next = (SettingsRepository.settings.value.homeRec + 1) % 3
        SettingsRepository.setHomeRec(next)
    }

    fun cycleSearchView() {
        val next = if (SettingsRepository.settings.value.searchView == 0) 1 else 0
        SettingsRepository.setSearchView(next)
    }
}
