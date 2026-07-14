package com.hotplato.tvbox.ui.feature.detail

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.AbsXml
import com.hotplato.tvbox.bean.VodInfo
import com.hotplato.tvbox.cache.RoomDataManger
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
        val info = VodInfo()
        info.setVideo(video)
        info.sourceKey = sourceKey
        val flags = info.seriesFlags?.map { it.name } ?: emptyList()
        val flag = info.playFlag ?: flags.firstOrNull()
        info.playFlag = flag
        val episodes = if (flag != null) info.seriesMap[flag] ?: emptyList() else emptyList()
        _uiState.update {
            it.copy(
                loading = false,
                error = null,
                vodInfo = info,
                flags = flags.filterNotNull(),
                selectedFlag = flag,
                episodes = episodes,
            )
        }
        viewModelScope.launch {
            val collected = withContext(Dispatchers.IO) {
                RoomDataManger.getAllVodCollect().any { it.sourceKey == sourceKey && it.vodId == vodId }
            }
            _uiState.update { it.copy(collected = collected) }
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
        val episodes = info.seriesMap[flag] ?: emptyList()
        _uiState.update {
            it.copy(selectedFlag = flag, episodes = episodes, vodInfo = info)
        }
    }

    fun toggleCollect() {
        val info = _uiState.value.vodInfo ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (_uiState.value.collected) {
                    RoomDataManger.getAllVodCollect()
                        .firstOrNull { it.sourceKey == sourceKey && it.vodId == vodId }
                        ?.let { RoomDataManger.deleteVodCollect(it.id) }
                } else {
                    RoomDataManger.insertVodCollect(sourceKey, info)
                }
            }
            _uiState.update { it.copy(collected = !it.collected) }
        }
    }

    fun preparePlay(index: Int): VodInfo? {
        val info = _uiState.value.vodInfo ?: return null
        val episodes = _uiState.value.episodes
        if (episodes.isEmpty() || index !in episodes.indices) return null
        episodes.forEachIndexed { i, ep -> ep.selected = i == index }
        info.playIndex = index
        viewModelScope.launch(Dispatchers.IO) {
            RoomDataManger.insertVodRecord(sourceKey, info)
        }
        return info
    }

    override fun onCleared() {
        sourceViewModel.detailResult.removeObserver(detailObserver)
        super.onCleared()
    }
}
