package com.hotplato.tvbox.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvFocusButton(
                text = homeName.ifBlank { "TVBox" }.let { name ->
                    if (name.length > 12) name.take(12) + "…" else name
                },
                onClick = onOpenSourcePicker,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            TvFocusButton(
                text = "搜索",
                onClick = onOpenSearch,
                modifier = Modifier.width(116.dp),
            )
            TvFocusButton(
                text = "直播",
                onClick = onOpenLive,
                modifier = Modifier.width(116.dp),
            )
            TvFocusButton(
                text = "我的",
                onClick = onOpenMine,
                modifier = Modifier
                    .width(116.dp)
                    .focusRequester(mineFocusRequester),
            )
        }
        TvCategoryTabRow(
            labels = sortLabels,
            selectedIndex = selectedSortIndex,
            onSelect = onSelectSort,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
