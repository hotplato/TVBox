package com.hotplato.tvbox.ui.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.bean.VodInfo
import com.hotplato.tvbox.data.EventBusBridge
import com.hotplato.tvbox.data.HistoryRepository
import com.hotplato.tvbox.event.RefreshEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<VodInfo> = emptyList(),
    val deleteMode: Boolean = false,
)

class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

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
            runCatching { HistoryRepository.load() }
                .onSuccess { list ->
                    _uiState.update { it.copy(loading = false, items = list, error = null) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, error = e.message ?: "加载失败")
                    }
                }
        }
    }

    fun toggleDeleteMode() {
        _uiState.update { it.copy(deleteMode = !it.deleteMode) }
    }

    fun onItemAction(item: VodInfo, onOpen: (VodInfo) -> Unit) {
        val deleteMode = _uiState.value.deleteMode
        if (deleteMode) {
            viewModelScope.launch {
                HistoryRepository.delete(item)
                refresh()
            }
        } else {
            onOpen(item)
        }
    }
}
