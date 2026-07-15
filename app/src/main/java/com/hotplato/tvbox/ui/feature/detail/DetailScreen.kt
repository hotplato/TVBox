package com.hotplato.tvbox.ui.feature.detail

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.hotplato.tvbox.ui.theme.TvFocusBorder
import com.hotplato.tvbox.ui.theme.TvMuted
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
        state.loading -> LoadingState(onBack = onBack)
        state.error != null && state.vodInfo == null ->
            ErrorState(
                message = state.error!!,
                onRetry = { viewModel.load(sourceKey, vodId) },
                onBack = onBack,
            )
        else -> {
            val info = state.vodInfo!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvFocusButton(text = "返回", onClick = onBack)
                    TvFocusButton(
                        text = if (state.collected) "已收藏" else "收藏",
                        onClick = viewModel::toggleCollect,
                        selected = state.collected,
                    )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        VodCoverImage(
                            pic = info.pic,
                            contentDescription = info.name,
                            modifier = Modifier
                                .width(192.dp)
                                .height(216.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = info.name ?: "", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = info.note ?: "",
                                color = TvMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = listOfNotNull(
                                    info.year.takeIf { it > 0 }?.toString(),
                                    info.area,
                                    info.type,
                                ).joinToString(" · "),
                                color = TvMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = htmlToPlainText(info.des).take(240),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                item { Text(text = "播放线路", style = MaterialTheme.typography.titleMedium) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.flags) { flag ->
                            PlaybackSourceTab(
                                text = flag,
                                selected = flag == state.selectedFlag,
                                onClick = { viewModel.selectFlag(flag) },
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = "选集 · 共 ${state.episodes.size} 集",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.episodes.mapIndexed { index, episode -> index to episode }.chunked(6)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { (index, ep) ->
                        TvFocusButton(
                            text = ep.name ?: "第${index + 1}集",
                            onClick = {
                                val playInfo = viewModel.preparePlay(index) ?: return@TvFocusButton
                                val intent = Intent(context, PlayActivity::class.java).apply {
                                    putExtra("sourceKey", sourceKey)
                                    putExtra("VodInfo", playInfo)
                                }
                                context.startActivity(intent)
                            },
                            selected = index == state.selectedEpisodeIndex,
                            modifier = Modifier.weight(1f),
                        )
                        }
                        repeat(6 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
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
