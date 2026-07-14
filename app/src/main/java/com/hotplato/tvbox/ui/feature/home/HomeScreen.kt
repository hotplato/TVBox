package com.hotplato.tvbox.ui.feature.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.component.EmptyState
import com.hotplato.tvbox.ui.component.ErrorState
import com.hotplato.tvbox.ui.component.LoadingState
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.component.TvPosterCard
import com.hotplato.tvbox.ui.live.LivePlayActivity
import com.hotplato.tvbox.ui.theme.TvMuted

@Composable
fun HomeScreen(
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onOpenSearch: (query: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCollect: () -> Unit,
    onOpenPush: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.homeName.ifBlank { "TVBox" },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(text = "我的", color = TvMuted, style = MaterialTheme.typography.labelLarge)
            TvFocusButton(text = "直播", onClick = {
                context.startActivity(Intent(context, LivePlayActivity::class.java))
            })
            TvFocusButton(text = "搜索", onClick = { onOpenSearch(null) })
            TvFocusButton(text = "设置", onClick = onOpenSettings)
            TvFocusButton(text = "历史", onClick = onOpenHistory)
            TvFocusButton(text = "收藏", onClick = onOpenCollect)
            TvFocusButton(text = "推送", onClick = onOpenPush)

            Text(
                text = "分类",
                color = TvMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.sorts) { index, sort ->
                    TvFocusButton(
                        text = sort.name ?: sort.id,
                        onClick = { viewModel.selectSort(index) },
                        selected = index == state.selectedSortIndex,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
