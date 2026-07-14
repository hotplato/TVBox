package com.hotplato.tvbox.ui.feature.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as lazyColumnItemsIndexed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.ui.component.EmptyState
import com.hotplato.tvbox.ui.component.ErrorState
import com.hotplato.tvbox.ui.component.LoadingState
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.component.TvPosterCard
import com.hotplato.tvbox.ui.live.LivePlayActivity

@Composable
fun HomeScreen(
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onOpenSearch: (query: String?) -> Unit,
    onOpenMine: () -> Unit,
    restoreMineFocus: Boolean = false,
    onMineFocusConsumed: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSourcePicker by remember { mutableStateOf(false) }
    val mineFocusRequester = remember { FocusRequester() }

    LaunchedEffect(restoreMineFocus) {
        if (restoreMineFocus) {
            runCatching { mineFocusRequester.requestFocus() }
            onMineFocusConsumed()
        }
    }

    if (state.storePrompt.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("请选择仓库", style = MaterialTheme.typography.headlineMedium)
            state.storePrompt.forEach { store ->
                TvFocusButton(
                    text = store.name ?: store.url,
                    onClick = { viewModel.selectStore(store) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    if (showSourcePicker) {
        val currentKey = ApiConfig.get().homeSourceBean?.key
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("请选择数据源", style = MaterialTheme.typography.headlineMedium)
            TvFocusButton(
                text = "取消",
                onClick = { showSourcePicker = false },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lazyColumnItemsIndexed(ApiConfig.get().sourceBeanList) { _, site ->
                    TvFocusButton(
                        text = site.name ?: site.key ?: "",
                        selected = site.key == currentKey,
                        onClick = {
                            viewModel.switchHomeSource(site)
                            showSourcePicker = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        HomeTopBar(
            homeName = state.homeName,
            sortLabels = state.sorts.map { it.name ?: it.id },
            selectedSortIndex = state.selectedSortIndex,
            onSelectSort = viewModel::selectSort,
            onOpenSourcePicker = { showSourcePicker = true },
            onOpenSearch = { onOpenSearch(null) },
            onOpenLive = { context.startActivity(Intent(context, LivePlayActivity::class.java)) },
            onOpenMine = onOpenMine,
            mineFocusRequester = mineFocusRequester,
        )

        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            when {
                state.loading && state.videos.isEmpty() -> LoadingState()
                state.error != null && state.videos.isEmpty() ->
                    ErrorState(message = state.error!!, onRetry = { viewModel.bootstrap() })
                state.videos.isEmpty() -> EmptyState(
                    if (state.sorts.getOrNull(state.selectedSortIndex)?.id == "my0") {
                        "暂无推荐内容"
                    } else {
                        "当前分类暂无内容"
                    },
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        state.videos,
                        key = { index, item -> "${item.sourceKey}_${item.id}_$index" },
                    ) { _, item ->
                        TvPosterCard(
                            title = item.name ?: "",
                            imageUrl = item.pic,
                            subtitle = item.note,
                            onClick = {
                                val key = item.sourceKey
                                val id = item.id
                                if (!key.isNullOrBlank() && !id.isNullOrBlank()) {
                                    onOpenDetail(key, id)
                                } else if (!item.name.isNullOrBlank()) {
                                    onOpenSearch(item.name)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
