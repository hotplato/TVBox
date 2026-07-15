package com.hotplato.tvbox.ui.feature.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as lazyColumnItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.bean.Movie
import com.hotplato.tvbox.ui.component.EmptyState
import com.hotplato.tvbox.ui.component.ErrorState
import com.hotplato.tvbox.ui.component.LoadingState
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.component.VodCoverImage
import com.hotplato.tvbox.ui.live.LivePlayActivity
import com.hotplato.tvbox.ui.theme.TvBackground
import com.hotplato.tvbox.ui.theme.TvFocusBorder
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.ui.theme.TvOnBackground
import com.hotplato.tvbox.ui.theme.TvSurface

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
    var focusedIndex by remember { mutableIntStateOf(0) }
    val mineFocusRequester = remember { FocusRequester() }
    val featured = state.videos.getOrNull(focusedIndex) ?: state.videos.firstOrNull()

    LaunchedEffect(state.videos) {
        focusedIndex = 0
    }
    LaunchedEffect(restoreMineFocus) {
        if (restoreMineFocus) {
            runCatching { mineFocusRequester.requestFocus() }
            onMineFocusConsumed()
        }
    }

    if (state.storePrompt.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeBackdrop(featured)
            SelectionPanel(title = "请选择仓库") {
                state.storePrompt.forEach { store ->
                    TvFocusButton(
                        text = store.name ?: store.url,
                        onClick = { viewModel.selectStore(store) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    if (showSourcePicker) {
        val currentKey = ApiConfig.get().homeSourceBean?.key
        Box(modifier = Modifier.fillMaxSize()) {
            HomeBackdrop(featured)
            SelectionPanel(title = "请选择数据源") {
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
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeBackdrop(featured)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 24.dp),
        ) {
            HomeTopBar(
                homeName = state.homeName,
                sortLabels = state.sorts.map { it.name ?: it.id },
                selectedSortIndex = state.selectedSortIndex,
                onSelectSort = viewModel::selectSort,
                onOpenSourcePicker = { showSourcePicker = true },
                onOpenSearch = { onOpenSearch(null) },
                onOpenLive = {
                    if (ApiConfig.get().channelGroupList.isEmpty()) {
                        Toast.makeText(context, "当前配置未提供直播频道", Toast.LENGTH_SHORT).show()
                    } else {
                        context.startActivity(Intent(context, LivePlayActivity::class.java))
                    }
                },
                onOpenMine = onOpenMine,
                mineFocusRequester = mineFocusRequester,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val headerHeight = 42.dp
                val minRowHeight = 150.dp
                val maxRowHeight = 220.dp
                val heroHeight = if (featured != null) {
                    (maxHeight - headerHeight - minRowHeight).coerceIn(140.dp, 236.dp)
                } else {
                    0.dp
                }
                val rowHeight = (maxHeight - heroHeight - headerHeight)
                    .coerceIn(minRowHeight, maxRowHeight)
                val cardWidth = ((rowHeight - 8.dp) * 0.70f).coerceIn(125.dp, 152.dp)

                Column(modifier = Modifier.fillMaxSize()) {
                    if (featured != null) {
                        HomeFeaturedSection(
                            video = featured,
                            compact = heroHeight < 200.dp,
                            onOpen = {
                                openVideo(featured, onOpenDetail, onOpenSearch)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight),
                        )
                    }

                    val sectionTitle = state.sorts.getOrNull(state.selectedSortIndex)?.name
                        ?.takeIf { it.isNotBlank() } ?: "热门推荐"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .padding(start = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = TvOnBackground,
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.videos.isNotEmpty()) {
                            Text(
                                text = "${state.videos.size} 部内容",
                                style = MaterialTheme.typography.labelLarge,
                                color = TvMuted,
                            )
                        }
                    }
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
                        else -> LazyRow(
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                        ) {
                            itemsIndexed(
                                state.videos,
                                key = { index, item -> "${item.sourceKey}_${item.id}_$index" },
                            ) { index, item ->
                                HomePosterCard(
                                    title = item.name ?: "",
                                    imageUrl = item.pic,
                                    subtitle = item.note,
                                    cardWidth = cardWidth,
                                    onFocused = { focusedIndex = index },
                                    onClick = {
                                        openVideo(item, onOpenDetail, onOpenSearch)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openVideo(
    video: Movie.Video,
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onOpenSearch: (query: String?) -> Unit,
) {
    val key = video.sourceKey
    val id = video.id
    if (!key.isNullOrBlank() && !id.isNullOrBlank()) {
        onOpenDetail(key, id)
    } else if (!video.name.isNullOrBlank()) {
        onOpenSearch(video.name)
    }
}

@Composable
private fun HomeBackdrop(video: Movie.Video?) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 首页使用影院式纯色底，只保留当前影片的氛围图；全局壁纸继续供其他页面使用。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvBackground),
        )
        Crossfade(
            targetState = video?.pic,
            label = "home-backdrop",
            modifier = Modifier.fillMaxSize(),
        ) { pic ->
            if (!pic.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VodCoverImage(
                        pic = pic,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth(0.68f)
                            .fillMaxHeight(0.76f)
                            .graphicsLayer(alpha = 0.48f),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TvBackground.copy(alpha = 0.98f),
                            TvBackground.copy(alpha = 0.84f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            TvBackground.copy(alpha = 0.26f),
                            TvBackground.copy(alpha = 0.98f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun HomeFeaturedSection(
    video: Movie.Video,
    compact: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 6.dp, end = 250.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = video.name.orEmpty().ifBlank { "TVBox" },
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        val metadata = listOfNotNull(
            video.year.takeIf { it > 0 }?.toString(),
            video.area?.takeIf { it.isNotBlank() },
            video.type?.takeIf { it.isNotBlank() },
            video.note?.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
        if (!compact && metadata.isNotBlank()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodyMedium,
                color = TvOnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (!video.des.isNullOrBlank()) {
            Text(
                text = video.des.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = TvMuted,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        TvFocusButton(
            text = if (!video.sourceKey.isNullOrBlank() && !video.id.isNullOrBlank()) "查看详情" else "搜索影片",
            onClick = onOpen,
            modifier = Modifier
                .padding(top = if (compact) 8.dp else 14.dp)
                .height(48.dp)
                .widthIn(min = 128.dp),
        )
    }
}

@Composable
private fun HomePosterCard(
    title: String,
    imageUrl: String?,
    subtitle: String?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    cardWidth: Dp = 180.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(if (focused) Modifier.border(2.dp, TvFocusBorder, shape) else Modifier)
            .focusable(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSurface.copy(alpha = 0.72f),
            focusedContainerColor = TvSurface.copy(alpha = 0.92f),
            pressedContainerColor = TvSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .background(TvSurface.copy(alpha = 0.86f))
                    .clip(shape),
            ) {
                VodCoverImage(
                    pic = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(74.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, TvSurface.copy(alpha = 0.96f))),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = TvMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.82f)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(20.dp))
                .background(TvSurface.copy(alpha = 0.96f))
                .border(1.dp, TvFocusBorder.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            content()
        }
    }
}
