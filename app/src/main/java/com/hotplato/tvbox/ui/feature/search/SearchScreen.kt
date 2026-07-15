package com.hotplato.tvbox.ui.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
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
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.ui.theme.TvOnBackground
import com.hotplato.tvbox.ui.theme.TvSurface

@Composable
fun SearchScreen(
    initialQuery: String?,
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChange(initialQuery)
            viewModel.search(initialQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
        ) {
            TvFocusButton(text = "返回", onClick = onBack)
            BasicTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvSurface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(color = TvOnBackground),
                cursorBrush = SolidColor(TvOnBackground),
                singleLine = true,
                decorationBox = { inner ->
                    if (state.query.isEmpty()) {
                        Text("输入关键字搜索", color = TvMuted, style = MaterialTheme.typography.titleMedium)
                    }
                    inner()
                },
            )
            TvFocusButton(text = "搜索", onClick = { viewModel.search() })
        }

        when {
            state.loading -> LoadingState()
            state.error != null && state.items.isEmpty() ->
                ErrorState(message = state.error!!, onRetry = { viewModel.search() })
            state.items.isEmpty() -> EmptyState(
                if (state.query.isBlank()) "输入关键字并搜索" else "未找到相关内容",
            )
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
                        cardWidth = 192.dp,
                        onClick = {
                            val key = item.sourceKey ?: ""
                            val id = item.id ?: return@TvPosterCard
                            onOpenDetail(key, id)
                        },
                    )
                }
            }
        }
    }
}
