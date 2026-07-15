package com.hotplato.tvbox.ui.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

        if (state.groups.isEmpty()) {
            when {
                state.error != null -> ErrorState(message = state.error!!, onRetry = { viewModel.search() })
                state.query.isBlank() -> EmptyState("输入关键字并搜索")
                else -> EmptyState("未找到可搜索的数据源")
            }
            return@Column
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            SourceNavigation(
                state = state,
                onSelect = viewModel::selectSource,
                modifier = Modifier
                    .width(210.dp)
                    .fillMaxHeight(),
            )
            SearchResults(
                state = state,
                onOpenDetail = onOpenDetail,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SourceNavigation(
    state: SearchUiState,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        item {
            Text("搜索来源", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 2.dp))
        }
        item {
            TvFocusButton(
                text = "全部 (${state.completedSources}/${state.totalSources})",
                selected = state.selectedSourceKey == null,
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.groups, key = { it.sourceKey }) { group ->
            TvFocusButton(
                text = "${group.sourceName} ${groupStatusText(group)}",
                selected = state.selectedSourceKey == group.sourceKey,
                onClick = { onSelect(group.sourceKey) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = state.groups.filter { state.selectedSourceKey == null || it.sourceKey == state.selectedSourceKey }
    val resultCount = groups.sumOf { it.items.size }

    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = if (state.loading) "正在搜索 ${state.completedSources}/${state.totalSources}" else "找到 $resultCount 个结果",
            color = TvMuted,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (!state.loading && resultCount == 0 && state.selectedSourceKey == null) {
            EmptyState("全部数据源均未找到相关内容")
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(groups, key = { it.sourceKey }) { group ->
                SourceResultGroup(group, onOpenDetail)
            }
        }
    }
}

@Composable
private fun SourceResultGroup(
    group: SourceSearchGroup,
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${group.sourceName} · ${groupStatusText(group)}",
            style = MaterialTheme.typography.titleMedium,
        )
        when {
            group.items.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(group.items, key = { "${it.sourceKey}_${it.id}" }) { item ->
                    val sourceLabel = group.sourceName
                    val subtitle = listOfNotNull(sourceLabel, item.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    TvPosterCard(
                        title = item.name ?: "",
                        imageUrl = item.pic,
                        subtitle = subtitle,
                        cardWidth = 168.dp,
                        onClick = {
                            val key = item.sourceKey ?: group.sourceKey
                            val id = item.id ?: return@TvPosterCard
                            onOpenDetail(key, id)
                        },
                    )
                }
            }
            else -> Text(groupStatusDetail(group), color = TvMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun groupStatusText(group: SourceSearchGroup): String = when (group.status) {
    SourceSearchStatus.SEARCHING -> "搜索中"
    SourceSearchStatus.SUCCESS -> "${group.items.size} 条"
    SourceSearchStatus.EMPTY -> "无结果"
    SourceSearchStatus.FAILED -> "失败"
}

private fun groupStatusDetail(group: SourceSearchGroup): String = when (group.status) {
    SourceSearchStatus.SEARCHING -> "正在从该来源获取结果…"
    SourceSearchStatus.EMPTY -> "该来源未找到相关内容"
    SourceSearchStatus.FAILED -> "该来源搜索失败"
    SourceSearchStatus.SUCCESS -> ""
}
