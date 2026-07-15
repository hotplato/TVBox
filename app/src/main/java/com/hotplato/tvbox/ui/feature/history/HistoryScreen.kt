package com.hotplato.tvbox.ui.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.hotplato.tvbox.ui.theme.TvAccent

@Composable
fun HistoryScreen(
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Text(
                text = "观看历史",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f),
            )
            TvFocusButton(
                text = if (state.deleteMode) "完成" else "删除",
                onClick = viewModel::toggleDeleteMode,
                selected = state.deleteMode,
            )
        }
        if (state.deleteMode) {
            Text(
                text = "删除模式：选择条目即可删除",
                color = TvAccent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(message = state.error!!, onRetry = viewModel::refresh)
            state.items.isEmpty() -> EmptyState("暂无观看历史")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { "${it.sourceKey}_${it.id}" }) { item ->
                    TvPosterCard(
                        title = item.name ?: "",
                        imageUrl = item.pic,
                        subtitle = item.note,
                        onClick = {
                            viewModel.onItemAction(item) {
                                onOpenDetail(it.sourceKey, it.id!!)
                            }
                        },
                    )
                }
            }
        }
    }
}
