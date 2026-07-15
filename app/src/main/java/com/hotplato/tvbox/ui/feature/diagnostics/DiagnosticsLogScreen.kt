package com.hotplato.tvbox.ui.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.util.DiagnosticLog

@Composable
fun DiagnosticsLogScreen(onBack: () -> Unit) {
    val entries by DiagnosticLog.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp),
        ) {
            Text(text = "运行日志", style = MaterialTheme.typography.headlineMedium)
            TvFocusButton(text = "复制全部", onClick = { copyLogs(context) })
            TvFocusButton(text = "清空", onClick = DiagnosticLog::clear)
        }
        if (entries.isEmpty()) {
            Text("暂无运行日志", style = MaterialTheme.typography.bodyLarge, color = TvMuted)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.timestamp.toString() + it.message }) { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = "${DiagnosticLog.formatTime(entry.timestamp)} ${entry.level}/${entry.module}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TvMuted,
                        )
                        Text(
                            text = entry.message + (entry.durationMs?.let { " (${it}ms)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun copyLogs(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("TVBox 运行日志", DiagnosticLog.exportText()))
}
