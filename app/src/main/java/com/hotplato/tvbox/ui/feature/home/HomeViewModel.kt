package com.hotplato.tvbox.ui.feature.home

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.AbsSortXml
import com.hotplato.tvbox.bean.AbsXml
import com.hotplato.tvbox.bean.Movie
import com.hotplato.tvbox.bean.MovieSort
import com.hotplato.tvbox.bean.SourceBean
import com.hotplato.tvbox.bean.StoreBean
import com.hotplato.tvbox.bean.VodInfo
import com.hotplato.tvbox.cache.RoomDataManger
import com.hotplato.tvbox.server.ControlManager
import com.hotplato.tvbox.util.DefaultConfig
import com.hotplato.tvbox.util.DiagnosticLog
import com.hotplato.tvbox.util.HawkConfig
import com.hotplato.tvbox.viewmodel.SourceViewModel
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
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
    private var homeSourceRec: List<Movie.Video> = emptyList()
    private var bootstrapStartedAt = 0L
    private var sortStartedAt = 0L
    private var listStartedAt = 0L
    private var refreshScheduled = false

    private val sortObserver = Observer<AbsSortXml?> { abs ->
        DiagnosticLog.info("Home", if (abs == null) "首页分类加载失败" else "首页分类加载完成", elapsed(sortStartedAt))
        val homeKey = ApiConfig.get().homeSourceBean?.key ?: ""
        val sorts = DefaultConfig.adjustSort(
            homeKey,
            abs?.classes?.sortList ?: ArrayList(),
            true,
        )
        homeSourceRec = abs?.videoList ?: emptyList()
        _uiState.update {
            it.copy(
                loading = false,
                error = null,
                sorts = sorts,
                homeName = ApiConfig.get().homeSourceBean?.name ?: "",
                selectedSortIndex = 0,
                videos = emptyList(),
            )
        }
        if (sorts.isEmpty()) return@Observer
        val first = sorts[0]
        if (first.id == "my0") {
            loadHomeRec()
        } else {
            loadList(first, 1)
        }
    }

    private val listObserver = Observer<AbsXml?> { abs ->
        DiagnosticLog.info("Home", if (abs == null) "首页列表加载失败" else "首页列表加载完成", elapsed(listStartedAt))
        val videos = abs?.movie?.videoList ?: emptyList()
        _uiState.update {
            it.copy(loading = false, videos = videos, error = if (abs == null) "列表加载失败" else null)
        }
    }

    init {
        ControlManager.get().startServer()
        sourceViewModel.sortResult.observeForever(sortObserver)
        sourceViewModel.listResult.observeForever(listObserver)
        bootstrap(useCache = true)
    }

    fun bootstrap(useCache: Boolean = false) {
        useCacheConfig = useCache
        bootstrapStartedAt = System.currentTimeMillis()
        dataInitOk.set(false)
        jarInitOk.set(false)
        _uiState.update { it.copy(loading = true, error = null) }
        loadConfigPipeline()
    }

    private fun loadConfigPipeline() {
        if (dataInitOk.get() && jarInitOk.get()) {
            val key = ApiConfig.get().homeSourceBean?.key
            DiagnosticLog.info("Home", "配置与爬虫已就绪，开始加载首页", elapsed(bootstrapStartedAt))
            if (useCacheConfig && !refreshScheduled) {
                refreshScheduled = true
                ApiConfig.get().refreshConfigCache()
            }
            sortStartedAt = System.currentTimeMillis()
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
            Hawk.put(HawkConfig.STORE_API, store.url)
            Hawk.put(HawkConfig.STORE_NAME, store.name ?: "")
            bootstrap(false)
        }
    }

    fun switchHomeSource(source: SourceBean) {
        ApiConfig.get().setSourceBean(source)
        _uiState.update {
            it.copy(
                homeName = source.name ?: "",
                loading = true,
                error = null,
                sorts = emptyList(),
                videos = emptyList(),
                selectedSortIndex = 0,
            )
        }
        val key = source.key
        sourceViewModel.getSort(key)
    }

    fun selectSort(index: Int) {
        val sorts = _uiState.value.sorts
        if (index !in sorts.indices) return
        _uiState.update { it.copy(selectedSortIndex = index, loading = true, page = 1, videos = emptyList()) }
        val sort = sorts[index]
        if (sort.id == "my0") {
            loadHomeRec()
            return
        }
        loadList(sort, 1)
    }

    private fun loadList(sort: MovieSort.SortData, page: Int) {
        listStartedAt = System.currentTimeMillis()
        sourceViewModel.getList(sort, page)
    }

    /** 对齐遗留 UserFragment：0 豆瓣热播 / 1 数据源推荐 / 2 历史记录 */
    private fun loadHomeRec() {
        when (Hawk.get(HawkConfig.HOME_REC, 0)) {
            1 -> {
                _uiState.update {
                    it.copy(loading = false, videos = homeSourceRec, error = null)
                }
            }
            2 -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val vodList = RoomDataManger.getAllVodRecord(10).map { toVideo(it) }
                    _uiState.update {
                        it.copy(loading = false, videos = vodList, error = null)
                    }
                }
            }
            else -> loadDoubanHot()
        }
    }

    private fun loadDoubanHot() {
        try {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DATE)
            val today = String.format("%d%d%d", year, month, day)
            val requestDay = Hawk.get("home_hot_day", "")
            if (requestDay == today) {
                val json = Hawk.get("home_hot", "")
                if (!json.isNullOrEmpty()) {
                    _uiState.update {
                        it.copy(loading = false, videos = parseDoubanHots(json), error = null)
                    }
                    return
                }
            }
            OkGo.get<String>(
                "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=$year,$year",
            ).tag("home_hot").execute(object : AbsCallback<String>() {
                override fun onSuccess(response: Response<String>) {
                    val netJson = response.body() ?: ""
                    Hawk.put("home_hot_day", today)
                    Hawk.put("home_hot", netJson)
                    _uiState.update {
                        it.copy(loading = false, videos = parseDoubanHots(netJson), error = null)
                    }
                }

                override fun convertResponse(response: okhttp3.Response): String {
                    return response.body()!!.string()
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    _uiState.update {
                        it.copy(loading = false, videos = emptyList(), error = null)
                    }
                }
            })
        } catch (th: Throwable) {
            th.printStackTrace()
            _uiState.update { it.copy(loading = false, videos = emptyList(), error = null) }
        }
    }

    private fun parseDoubanHots(json: String): List<Movie.Video> {
        val result = ArrayList<Movie.Video>()
        try {
            val infoJson = Gson().fromJson(json, JsonObject::class.java) ?: return result
            val array: JsonArray = infoJson.getAsJsonArray("data") ?: return result
            for (ele: JsonElement in array) {
                val obj = ele as JsonObject
                val vod = Movie.Video()
                vod.name = obj.get("title")?.asString
                vod.note = obj.get("rate")?.asString
                vod.pic = obj.get("cover")?.asString
                result.add(vod)
            }
        } catch (_: Throwable) {
        }
        return result
    }

    private fun toVideo(vodInfo: VodInfo): Movie.Video {
        val vod = Movie.Video()
        vod.id = vodInfo.id
        vod.sourceKey = vodInfo.sourceKey
        vod.name = vodInfo.name
        vod.pic = vodInfo.pic
        if (!vodInfo.playNote.isNullOrEmpty()) {
            vod.note = "上次看到${vodInfo.playNote}"
        }
        return vod
    }

    override fun onCleared() {
        OkGo.getInstance().cancelTag("home_hot")
        sourceViewModel.sortResult.removeObserver(sortObserver)
        sourceViewModel.listResult.removeObserver(listObserver)
        super.onCleared()
    }

    private fun elapsed(startedAt: Long): Long? =
        startedAt.takeIf { it > 0 }?.let { System.currentTimeMillis() - it }
}
