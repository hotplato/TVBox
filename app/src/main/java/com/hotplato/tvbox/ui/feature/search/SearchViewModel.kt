package com.hotplato.tvbox.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.Movie
import com.hotplato.tvbox.viewmodel.SourceViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<Movie.Video> = emptyList(),
)

class SearchViewModel : ViewModel() {
    private val sourceViewModel = SourceViewModel()
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        sourceViewModel.searchResult.observeForever { abs ->
            val videos = abs?.movie?.videoList ?: emptyList()
            _uiState.update {
                it.copy(
                    loading = false,
                    items = videos,
                    error = if (abs == null) "搜索失败" else null,
                )
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun search(query: String = _uiState.value.query) {
        val q = query.trim()
        if (q.isEmpty()) return
        _uiState.update { it.copy(query = q, loading = true, error = null, items = emptyList()) }
        viewModelScope.launch {
            val homeKey = ApiConfig.get().homeSourceBean?.key
            if (homeKey.isNullOrBlank()) {
                _uiState.update { it.copy(loading = false, error = "请先配置并加载数据源") }
                return@launch
            }
            sourceViewModel.getSearch(homeKey, q)
        }
    }

    override fun onCleared() {
        sourceViewModel.searchResult.removeObserver { }
        super.onCleared()
    }
}
