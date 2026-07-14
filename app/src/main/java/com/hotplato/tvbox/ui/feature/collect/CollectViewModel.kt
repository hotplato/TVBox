package com.hotplato.tvbox.ui.feature.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.cache.VodCollect
import com.hotplato.tvbox.data.CollectRepository
import com.hotplato.tvbox.data.EventBusBridge
import com.hotplato.tvbox.event.RefreshEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<VodCollect> = emptyList(),
    val deleteMode: Boolean = false,
)

sealed interface CollectEffect {
    data class OpenDetail(val sourceKey: String, val vodId: String) : CollectEffect
    data class OpenSearch(val title: String) : CollectEffect
}

class CollectViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CollectUiState())
    val uiState: StateFlow<CollectUiState> = _uiState.asStateFlow()

    private val _effects = MutableStateFlow<CollectEffect?>(null)
    val effects: StateFlow<CollectEffect?> = _effects.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            EventBusBridge.refreshEvents().collect { event ->
                if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { CollectRepository.load() }
                .onSuccess { list ->
                    _uiState.update { it.copy(loading = false, items = list) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    fun toggleDeleteMode() {
        _uiState.update { it.copy(deleteMode = !it.deleteMode) }
    }

    fun onItemClick(item: VodCollect) {
        if (_uiState.value.deleteMode) {
            viewModelScope.launch {
                CollectRepository.delete(item.id)
                refresh()
            }
            return
        }
        if (ApiConfig.get().getSource(item.sourceKey) != null) {
            _effects.value = CollectEffect.OpenDetail(item.sourceKey, item.vodId)
        } else {
            _effects.value = CollectEffect.OpenSearch(item.name ?: "")
        }
    }

    fun consumeEffect() {
        _effects.value = null
    }
}
