package com.hotplato.tvbox.ui.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.component.TvFocusButton

@Composable
fun MineScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCollect: () -> Unit,
    onOpenPush: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            TvFocusButton(text = "返回", onClick = onBack)
            Text(text = "我的", style = MaterialTheme.typography.headlineMedium)
        }
        TvFocusButton(text = "历史记录", onClick = onOpenHistory, modifier = Modifier.fillMaxWidth())
        TvFocusButton(text = "我的收藏", onClick = onOpenCollect, modifier = Modifier.fillMaxWidth())
        TvFocusButton(text = "远程控制", onClick = onOpenPush, modifier = Modifier.fillMaxWidth())
        TvFocusButton(text = "设置", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
    }
}
