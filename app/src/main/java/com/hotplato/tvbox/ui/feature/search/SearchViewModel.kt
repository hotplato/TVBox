package com.hotplato.tvbox.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.AbsXml
import com.hotplato.tvbox.bean.Movie
import com.hotplato.tvbox.bean.SourceBean
import com.hotplato.tvbox.viewmodel.SourceViewModel
import com.lzy.okgo.OkGo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class SourceSearchStatus {
    SEARCHING,
    SUCCESS,
    EMPTY,
    FAILED,
}

data class SourceSearchGroup(
    val sourceKey: String,
    val sourceName: String,
    val status: SourceSearchStatus = SourceSearchStatus.SEARCHING,
    val items: List<Movie.Video> = emptyList(),
)

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val totalSources: Int = 0,
    val completedSources: Int = 0,
    val groups: List<SourceSearchGroup> = emptyList(),
    /** null means all sources. */
    val selectedSourceKey: String? = null,
)

class SearchViewModel : ViewModel() {
    private val sourceViewModel = SourceViewModel()
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val requestSequence = AtomicLong(0)
    private val activeRequestTags = LinkedHashSet<String>()
    private var activeSearchJob: Job? = null

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun selectSource(sourceKey: String?) {
        _uiState.update { it.copy(selectedSourceKey = sourceKey) }
    }

    fun search(query: String = _uiState.value.query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return

        activeSearchJob?.cancel()
        cancelActiveRequests()
        val requestId = requestSequence.incrementAndGet()
        val sources = orderedSearchableSources()
        if (sources.isEmpty()) {
            _uiState.update {
                it.copy(
                    query = keyword,
                    loading = false,
                    error = "没有可搜索的数据源",
                    totalSources = 0,
                    completedSources = 0,
                    groups = emptyList(),
                    selectedSourceKey = null,
                )
            }
            return
        }

        _uiState.value = SearchUiState(
            query = keyword,
            loading = true,
            totalSources = sources.size,
            groups = sources.map { SourceSearchGroup(it.key, it.name ?: it.key) },
        )
        activeSearchJob = viewModelScope.launch {
            val semaphore = Semaphore(5)
            sources.forEach { source ->
                launch(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        if (requestId != requestSequence.get()) return@launch
                        val tag = "compose_search_${requestId}_${source.key}"
                        synchronized(activeRequestTags) { activeRequestTags.add(tag) }
                        val result = searchSource(source, keyword, tag)
                        publishResult(requestId, source.key, result)
                    } catch (_: CancellationException) {
                        // A newer query owns the screen state.
                    } catch (_: Throwable) {
                        publishResult(requestId, source.key, null)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    private fun orderedSearchableSources(): List<SourceBean> {
        val all = ApiConfig.get().sourceBeanList.filter { it.isSearchable && !it.key.isNullOrBlank() }
        val homeKey = ApiConfig.get().homeSourceBean?.key
        return all.sortedBy { if (it.key == homeKey) 0 else 1 }
    }

    private suspend fun searchSource(source: SourceBean, keyword: String, tag: String): AbsXml? =
        suspendCancellableCoroutine { continuation ->
            sourceViewModel.getSearch(source.key, keyword, tag) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation { OkGo.getInstance().cancelTag(tag) }
        }

    private fun publishResult(requestId: Long, sourceKey: String, result: AbsXml?) {
        if (requestId != requestSequence.get()) return
        val videos = result?.movie?.videoList?.toList().orEmpty()
        _uiState.update { state ->
            if (requestId != requestSequence.get()) return@update state
            val groups = state.groups.map { group ->
                if (group.sourceKey != sourceKey) group else group.copy(
                    status = when {
                        result == null -> SourceSearchStatus.FAILED
                        videos.isEmpty() -> SourceSearchStatus.EMPTY
                        else -> SourceSearchStatus.SUCCESS
                    },
                    items = videos,
                )
            }
            val completed = groups.count { it.status != SourceSearchStatus.SEARCHING }
            state.copy(
                groups = groups,
                completedSources = completed,
                loading = completed < state.totalSources,
                error = null,
            )
        }
        synchronized(activeRequestTags) {
            activeRequestTags.remove("compose_search_${requestId}_$sourceKey")
        }
    }

    private fun cancelActiveRequests() {
        requestSequence.incrementAndGet()
        val tags = synchronized(activeRequestTags) {
            activeRequestTags.toList().also { activeRequestTags.clear() }
        }
        tags.forEach { OkGo.getInstance().cancelTag(it) }
    }

    override fun onCleared() {
        activeSearchJob?.cancel()
        cancelActiveRequests()
        super.onCleared()
    }
}
