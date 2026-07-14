package com.hotplato.tvbox.ui.feature.home

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.AbsSortXml
import com.hotplato.tvbox.bean.AbsXml
import com.hotplato.tvbox.bean.Movie
import com.hotplato.tvbox.bean.MovieSort
import com.hotplato.tvbox.bean.StoreBean
import com.hotplato.tvbox.server.ControlManager
import com.hotplato.tvbox.util.DefaultConfig
import com.hotplato.tvbox.viewmodel.SourceViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val homeName: String = "",
    val sorts: List<MovieSort.SortData> = emptyList(),
    val selectedSortIndex: Int = 0,
    val videos: List<Movie.Video> = emptyList(),
    val page: Int = 1,
    val storePrompt: List<StoreBean> = emptyList(),
)

class HomeViewModel : ViewModel() {
    private val sourceViewModel = SourceViewModel()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val dataInitOk = AtomicBoolean(false)
    private val jarInitOk = AtomicBoolean(false)
    private var useCacheConfig = false

    private val sortObserver = Observer<AbsSortXml?> { abs ->
        val homeKey = ApiConfig.get().homeSourceBean?.key ?: ""
        val sorts = DefaultConfig.adjustSort(
            homeKey,
            abs?.classes?.sortList ?: ArrayList(),
            true,
        )
        _uiState.update {
            it.copy(
                loading = false,
                error = null,
                sorts = sorts,
                homeName = ApiConfig.get().homeSourceBean?.name ?: "",
                selectedSortIndex = 0,
                videos = abs?.videoList ?: emptyList(),
            )
        }
        if (sorts.isNotEmpty()) {
            val first = sorts[0]
            if (first.id != "my0") {
                loadList(first, 1)
            }
        }
    }

    private val listObserver = Observer<AbsXml?> { abs ->
        val videos = abs?.movie?.videoList ?: emptyList()
        _uiState.update {
            it.copy(loading = false, videos = videos, error = if (abs == null) "列表加载失败" else null)
        }
    }

    init {
        ControlManager.get().startServer()
        sourceViewModel.sortResult.observeForever(sortObserver)
        sourceViewModel.listResult.observeForever(listObserver)
        bootstrap()
    }

    fun bootstrap(useCache: Boolean = false) {
        useCacheConfig = useCache
        dataInitOk.set(false)
        jarInitOk.set(false)
        _uiState.update { it.copy(loading = true, error = null) }
        loadConfigPipeline()
    }

    private fun loadConfigPipeline() {
        if (dataInitOk.get() && jarInitOk.get()) {
            val key = ApiConfig.get().homeSourceBean?.key
            sourceViewModel.getSort(key)
            return
        }
        if (dataInitOk.get() && !jarInitOk.get()) {
            val spider = ApiConfig.get().spider
            if (!spider.isNullOrEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, spider, object : ApiConfig.LoadConfigCallback {
                    override fun success() {
                        jarInitOk.set(true)
                        loadConfigPipeline()
                    }

                    override fun retry() {}
                    override fun error(msg: String?) {
                        jarInitOk.set(true)
                        loadConfigPipeline()
                    }

                    override fun needSelect(stores: MutableList<StoreBean>?) {}
                })
            } else {
                jarInitOk.set(true)
                loadConfigPipeline()
            }
            return
        }
        ApiConfig.get().loadConfig(useCacheConfig, object : ApiConfig.LoadConfigCallback {
            override fun success() {
                dataInitOk.set(true)
                if (ApiConfig.get().spider.isNullOrEmpty()) {
                    jarInitOk.set(true)
                }
                loadConfigPipeline()
            }

            override fun retry() {
                loadConfigPipeline()
            }

            override fun error(msg: String?) {
                _uiState.update { it.copy(loading = false, error = msg ?: "配置加载失败") }
            }

            override fun needSelect(stores: MutableList<StoreBean>?) {
                _uiState.update {
                    it.copy(loading = false, storePrompt = stores ?: emptyList())
                }
            }
        }, null)
    }

    fun selectStore(store: StoreBean) {
        viewModelScope.launch {
            // selection handled via Hawk in SettingsRepository-compatible flow
            com.orhanobut.hawk.Hawk.put(com.hotplato.tvbox.util.HawkConfig.STORE_API, store.url)
            com.orhanobut.hawk.Hawk.put(
                com.hotplato.tvbox.util.HawkConfig.STORE_NAME,
                store.name ?: "",
            )
            bootstrap(false)
        }
    }

    fun selectSort(index: Int) {
        val sorts = _uiState.value.sorts
        if (index !in sorts.indices) return
        _uiState.update { it.copy(selectedSortIndex = index, loading = true, page = 1) }
        val sort = sorts[index]
        if (sort.id == "my0") {
            _uiState.update { it.copy(loading = false, videos = emptyList()) }
            return
        }
        loadList(sort, 1)
    }

    private fun loadList(sort: MovieSort.SortData, page: Int) {
        sourceViewModel.getList(sort, page)
    }

    override fun onCleared() {
        sourceViewModel.sortResult.removeObserver(sortObserver)
        sourceViewModel.listResult.removeObserver(listObserver)
        super.onCleared()
    }
}
