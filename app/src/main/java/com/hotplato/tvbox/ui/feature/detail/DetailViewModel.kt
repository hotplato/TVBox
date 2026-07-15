package com.hotplato.tvbox.ui.feature.detail

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.AbsXml
import com.hotplato.tvbox.bean.VodInfo
import com.hotplato.tvbox.data.CollectRepository
import com.hotplato.tvbox.data.HistoryRepository
import com.hotplato.tvbox.viewmodel.SourceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val vodInfo: VodInfo? = null,
    val flags: List<String> = emptyList(),
    val selectedFlag: String? = null,
    val episodes: List<VodInfo.VodSeries> = emptyList(),
    val selectedEpisodeIndex: Int = 0,
    val collected: Boolean = false,
)

class DetailViewModel : ViewModel() {
    private val sourceViewModel = SourceViewModel()
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var sourceKey: String = ""
    private var vodId: String = ""

    private val detailObserver = Observer<AbsXml?> { abs ->
        if (abs?.movie?.videoList.isNullOrEmpty()) {
            _uiState.update { it.copy(loading = false, error = "获取详情失败") }
            return@Observer
        }
        val video = abs!!.movie.videoList[0]
        val requestedSourceKey = sourceKey
        val requestedVodId = vodId
        val info = VodInfo()
        info.setVideo(video)
        info.sourceKey = requestedSourceKey
        viewModelScope.launch {
            val (record, collected) = withContext(Dispatchers.IO) {
                HistoryRepository.find(requestedSourceKey, requestedVodId) to
                    CollectRepository.contains(requestedSourceKey, requestedVodId)
            }
            // The detail response may arrive after navigation has moved to another item.
            if (this@DetailViewModel.sourceKey != requestedSourceKey ||
                this@DetailViewModel.vodId != requestedVodId
            ) {
                return@launch
            }

            restorePlaybackSelection(info, record)
            val flags = info.seriesFlags?.mapNotNull { it.name } ?: emptyList()
            val flag = info.playFlag
            val episodes = flag?.let { info.seriesMap?.get(it) }.orEmpty()
            _uiState.update {
                it.copy(
                    loading = false,
                    error = null,
                    vodInfo = info,
                    flags = flags,
                    selectedFlag = flag,
                    episodes = episodes,
                    selectedEpisodeIndex = info.playIndex,
                    collected = collected,
                )
            }
        }
    }

    init {
        sourceViewModel.detailResult.observeForever(detailObserver)
    }

    fun load(sourceKey: String, vodId: String) {
        this.sourceKey = sourceKey
        this.vodId = vodId
        _uiState.update { DetailUiState(loading = true) }
        if (ApiConfig.get().getSource(sourceKey) == null && sourceKey != "push_agent") {
            _uiState.update { it.copy(loading = false, error = "数据源不存在") }
            return
        }
        sourceViewModel.getDetail(sourceKey, vodId)
    }

    fun selectFlag(flag: String) {
        val info = _uiState.value.vodInfo ?: return
        info.playFlag = flag
        info.playIndex = 0
        val episodes = info.seriesMap?.get(flag).orEmpty()
        _uiState.update {
            it.copy(
                selectedFlag = flag,
                episodes = episodes,
                selectedEpisodeIndex = 0,
                vodInfo = info,
            )
        }
    }

    fun toggleCollect() {
        val info = _uiState.value.vodInfo ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (_uiState.value.collected) CollectRepository.remove(sourceKey, vodId)
                else CollectRepository.save(sourceKey, info)
            }
            _uiState.update { it.copy(collected = !it.collected) }
        }
    }

    fun preparePlay(index: Int): VodInfo? {
        val info = _uiState.value.vodInfo ?: return null
        val episodes = _uiState.value.episodes
        if (episodes.isEmpty() || index !in episodes.indices) return null
        info.playIndex = index
        _uiState.update { it.copy(selectedEpisodeIndex = index, vodInfo = info) }
        viewModelScope.launch(Dispatchers.IO) {
            HistoryRepository.save(sourceKey, info)
        }
        return info
    }

    /** Applies the same record compatibility rules as the legacy detail screen. */
    private fun restorePlaybackSelection(info: VodInfo, record: VodInfo?) {
        if (record != null) {
            info.playFlag = record.playFlag
            info.playIndex = record.playIndex.coerceAtLeast(0)
            info.playerCfg = record.playerCfg
            info.reverseSort = record.reverseSort
        }

        if (info.reverseSort && !info.seriesMap.isNullOrEmpty()) info.reverse()

        val flags = info.seriesFlags?.mapNotNull { it.name }.orEmpty()
        val selectedFlag = info.playFlag?.takeIf { it in flags } ?: flags.firstOrNull()
        info.playFlag = selectedFlag
        val episodes = selectedFlag?.let { info.seriesMap?.get(it) }.orEmpty()
        if (episodes.isEmpty() || info.playIndex !in episodes.indices) {
            info.playIndex = 0
        }
    }

    override fun onCleared() {
        sourceViewModel.detailResult.removeObserver(detailObserver)
        super.onCleared()
    }
}
