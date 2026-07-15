package com.hotplato.tvbox.ui.feature.detail

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.component.ErrorState
import com.hotplato.tvbox.ui.component.LoadingState
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.component.VodCoverImage
import com.hotplato.tvbox.ui.play.PlayActivity
import com.hotplato.tvbox.ui.theme.TvBackground
import com.hotplato.tvbox.ui.theme.TvFocusBorder
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.ui.theme.TvOnBackground
import com.hotplato.tvbox.ui.theme.TvPrimary
import com.hotplato.tvbox.ui.theme.TvSurfaceVariant
import com.hotplato.tvbox.ui.util.htmlToPlainText

@Composable
fun DetailScreen(
    sourceKey: String,
    vodId: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    LaunchedEffect(sourceKey, vodId) {
        viewModel.load(sourceKey, vodId)
    }

    when {
        state.loading -> LoadingState()
        state.error != null && state.vodInfo == null ->
            ErrorState(
                message = state.error!!,
                onRetry = { viewModel.load(sourceKey, vodId) },
            )
        else -> {
            val info = state.vodInfo!!
            Box(modifier = Modifier.fillMaxSize()) {
                DetailBackdrop(pic = info.pic)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 42.dp, vertical = 24.dp),
                ) {
                    DetailHero(
                        name = info.name.orEmpty(),
                        note = info.note,
                        metadata = listOfNotNull(
                            info.year.takeIf { it > 0 }?.toString(),
                            info.area?.takeIf { it.isNotBlank() },
                            info.type?.takeIf { it.isNotBlank() },
                            info.lang?.takeIf { it.isNotBlank() },
                        ).joinToString("  ·  "),
                        director = info.director,
                        actor = info.actor,
                        description = htmlToPlainText(info.des),
                        collected = state.collected,
                        onToggleCollect = viewModel::toggleCollect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(286.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DetailPlaybackPanel(
                        state = state,
                        onSelectFlag = viewModel::selectFlag,
                        onPlayEpisode = { index ->
                            val playInfo = viewModel.preparePlay(index) ?: return@DetailPlaybackPanel
                            val intent = Intent(context, PlayActivity::class.java).apply {
                                putExtra("sourceKey", sourceKey)
                                putExtra("VodInfo", playInfo)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBackdrop(pic: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        if (!pic.isNullOrBlank()) {
            VodCoverImage(
                pic = pic,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.68f)
                    .fillMaxHeight(0.78f)
                    .graphicsLayer(alpha = 0.52f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TvBackground,
                            TvBackground.copy(alpha = 0.94f),
                            TvBackground.copy(alpha = 0.42f),
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
                            TvBackground.copy(alpha = 0.34f),
                            TvBackground,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun DetailHero(
    name: String,
    note: String?,
    metadata: String,
    director: String?,
    actor: String?,
    description: String,
    collected: Boolean,
    onToggleCollect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(end = 360.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = name.ifBlank { "未命名影片" },
            style = MaterialTheme.typography.headlineLarge,
            color = TvOnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelLarge,
                color = TvPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodyMedium,
                color = TvOnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!director.isNullOrBlank()) {
            DetailInfoLine(label = "导演", value = director)
        }
        if (!actor.isNullOrBlank()) {
            DetailInfoLine(label = "主演", value = actor)
        }
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TvMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(top = 2.dp),
            )
        }
        TvFocusButton(
            text = if (collected) "已收藏" else "收藏",
            onClick = onToggleCollect,
            selected = collected,
            modifier = Modifier
                .padding(top = 7.dp)
                .widthIn(min = 104.dp),
        )
    }
}

@Composable
private fun DetailPlaybackPanel(
    state: DetailUiState,
    onSelectFlag: (String) -> Unit,
    onPlayEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        DetailSectionTitle(
            title = "播放线路",
            trailing = if (state.flags.isNotEmpty()) "${state.flags.size} 条线路" else null,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        ) {
            items(state.flags) { flag ->
                PlaybackSourceTab(
                    text = flag,
                    selected = flag == state.selectedFlag,
                    onClick = { onSelectFlag(flag) },
                )
            }
        }
        DetailSectionTitle(
            title = "选集",
            trailing = "共 ${state.episodes.size} 集",
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(
                items = state.episodes,
                key = { index, episode -> "${state.selectedFlag}_${episode.url}_$index" },
            ) { index, episode ->
                TvFocusButton(
                    text = episode.name ?: "第${index + 1}集",
                    onClick = { onPlayEpisode(index) },
                    selected = index == state.selectedEpisodeIndex,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DetailInfoLine(label: String, value: String) {
    Text(
        text = "$label  $value",
        style = MaterialTheme.typography.bodyMedium,
        color = TvMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailSectionTitle(title: String, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvOnBackground,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = TvMuted,
            )
        }
    }
}

@Composable
private fun PlaybackSourceTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 120.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.drawBehind {
                    drawRoundRect(
                        color = TvFocusBorder,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                } else Modifier,
            ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSurfaceVariant,
            focusedContainerColor = TvSurfaceVariant,
            pressedContainerColor = TvSurfaceVariant,
            contentColor = if (selected) TvPrimary else Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .drawBehind {
                    if (selected) {
                        drawLine(
                            color = TvPrimary,
                            start = Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                            end = Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
