package com.hotplato.tvbox.ui.feature.detail

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.component.ErrorState
import com.hotplato.tvbox.ui.component.LoadingState
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.component.VodCoverImage
import com.hotplato.tvbox.ui.play.PlayActivity
import com.hotplato.tvbox.ui.theme.TvMuted

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvFocusButton(text = "返回", onClick = onBack)
                    TvFocusButton(
                        text = if (state.collected) "已收藏" else "收藏",
                        onClick = viewModel::toggleCollect,
                        selected = state.collected,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    VodCoverImage(
                        pic = info.pic,
                        contentDescription = info.name,
                        modifier = Modifier
                            .width(180.dp)
                            .height(260.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Column(
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .weight(1f),
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
                            text = (info.des ?: "").take(240),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.flags) { flag ->
                        TvFocusButton(
                            text = flag,
                            onClick = { viewModel.selectFlag(flag) },
                            selected = flag == state.selectedFlag,
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    itemsIndexed(state.episodes) { index, ep ->
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
                            selected = ep.selected,
                        )
                    }
                }
            }
        }
    }
}
