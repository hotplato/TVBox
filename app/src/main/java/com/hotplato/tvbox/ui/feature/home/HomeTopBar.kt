package com.hotplato.tvbox.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.hotplato.tvbox.ui.component.TvCategoryTabRow
import com.hotplato.tvbox.ui.component.TvFocusButton

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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvFocusButton(
            text = homeName.ifBlank { "TVBox" }.let { name ->
                if (name.length > 10) name.take(10) + "…" else name
            },
            onClick = onOpenSourcePicker,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        TvCategoryTabRow(
            labels = sortLabels,
            selectedIndex = selectedSortIndex,
            onSelect = onSelectSort,
            modifier = Modifier.weight(1f),
        )
        TvFocusButton(text = "搜索", onClick = onOpenSearch)
        TvFocusButton(text = "直播", onClick = onOpenLive)
        TvFocusButton(
            text = "我的",
            onClick = onOpenMine,
            modifier = Modifier.focusRequester(mineFocusRequester),
        )
    }
}
