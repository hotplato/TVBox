package com.hotplato.tvbox.ui.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.theme.TvFocusBorder
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.ui.theme.TvPrimary
import com.hotplato.tvbox.ui.theme.TvSurfaceVariant

@Composable
fun HomeTopBar(
    homeName: String,
    sortLabels: List<String>,
    selectedSortIndex: Int,
    onSelectSort: (Int) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenMine: () -> Unit,
    mineFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeSourceButton(
                homeName = homeName,
                onClick = onOpenSourcePicker,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            HomeShortcutButton(
                label = "搜索",
                icon = HomeShortcutIcon.Search,
                onClick = onOpenSearch,
            )
            HomeShortcutButton(
                label = "直播",
                icon = HomeShortcutIcon.Live,
                onClick = onOpenLive,
            )
            HomeShortcutButton(
                label = "我的",
                icon = HomeShortcutIcon.Profile,
                onClick = onOpenMine,
                modifier = Modifier
                    .focusRequester(mineFocusRequester),
            )
        }
        HomeCategoryTabRow(
            labels = sortLabels,
            selectedIndex = selectedSortIndex,
            onSelect = onSelectSort,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HomeSourceButton(
    homeName: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, TvFocusBorder, shape) else Modifier)
            .focusable(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = TvPrimary,
            pressedContainerColor = TvPrimary.copy(alpha = 0.86f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
    ) {
        Text(
            text = homeName.ifBlank { "TVBox" }.let { name ->
                if (name.length > 12) name.take(12) + "…" else name
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun HomeCategoryTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in labels.indices) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            var focused by remember { mutableStateOf(false) }
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focused) Modifier.border(1.dp, TvFocusBorder, shape) else Modifier)
                    .focusable(),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = if (index == selectedIndex) 0.18f else 0.06f),
                    focusedContainerColor = Color.White.copy(alpha = 0.20f),
                    pressedContainerColor = TvPrimary.copy(alpha = 0.82f),
                    contentColor = TvMuted,
                    focusedContentColor = Color.White,
                    pressedContentColor = Color.White,
                ),
                shape = ClickableSurfaceDefaults.shape(shape = shape),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(24.dp)
                            .height(2.dp)
                            .background(if (index == selectedIndex) Color.White else Color.Transparent),
                    )
                }
            }
        }
    }
}

private enum class HomeShortcutIcon { Search, Live, Profile }

/** Compact, icon-led shortcuts keep the home header light while retaining a generous TV focus target. */
@Composable
private fun HomeShortcutButton(
    label: String,
    icon: HomeShortcutIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val iconColor = if (focused) Color.White else TvMuted

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(86.dp)
            .height(52.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, TvFocusBorder, shape) else Modifier)
            .focusable(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSurfaceVariant,
            focusedContainerColor = TvPrimary,
            pressedContainerColor = TvPrimary.copy(alpha = 0.85f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ShortcutIcon(icon = icon, tint = iconColor)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ShortcutIcon(icon: HomeShortcutIcon, tint: Color) {
    Canvas(modifier = Modifier.size(19.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)
        when (icon) {
            HomeShortcutIcon.Search -> {
                drawCircle(tint, radius = size.minDimension * .27f, center = Offset(size.width * .43f, size.height * .43f), style = stroke)
                drawLine(tint, Offset(size.width * .62f, size.height * .62f), Offset(size.width * .84f, size.height * .84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            HomeShortcutIcon.Live -> {
                drawRoundRect(tint, Offset(size.width * .12f, size.height * .22f), androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .56f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(tint, Offset(size.width * .42f, size.height * .86f), Offset(size.width * .58f, size.height * .86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(tint, radius = size.minDimension * .10f, center = center)
            }
            HomeShortcutIcon.Profile -> {
                drawCircle(tint, radius = size.minDimension * .20f, center = Offset(size.width / 2f, size.height * .34f), style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * .16f, size.height * .48f),
                    size = androidx.compose.ui.geometry.Size(size.width * .68f, size.height * .40f),
                    style = stroke,
                )
            }
        }
    }
}
