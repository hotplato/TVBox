package com.hotplato.tvbox.ui.feature.push

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.server.ControlManager
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.ui.tv.QRCodeGen
import kotlinx.coroutines.delay

@Composable
fun PushScreen(onOpenDetail: (sourceKey: String, vodId: String) -> Unit, onBack: () -> Unit) {
    val manager = ControlManager.get()
    val address = remember { manager.getAddress(false) }
    val qrBitmap: Bitmap? = remember(address) { address.takeIf { it.isNotBlank() && !it.contains("0.0.0.0") }?.let { QRCodeGen.generateBitmap(it, 320, 320, 4) } }
    var remaining by remember { mutableLongStateOf(manager.pairingRemainingSeconds) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { while (true) { remaining = manager.pairingRemainingSeconds; delay(1000) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("远程控制", style = MaterialTheme.typography.headlineMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (qrBitmap != null) Image(qrBitmap.asImageBitmap(), "远程控制二维码", Modifier.size(300.dp))
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(if (qrBitmap == null) "未检测到局域网地址" else "扫码或在浏览器访问：", color = TvMuted, style = MaterialTheme.typography.bodyLarge)
                Text(address.ifBlank { "服务尚未启动" }, style = MaterialTheme.typography.titleLarge)
                Text("配对码", style = MaterialTheme.typography.titleMedium, color = TvMuted)
                Text(manager.pairingCode, style = MaterialTheme.typography.displayMedium)
                Text("${remaining}s 后过期", color = TvMuted, style = MaterialTheme.typography.bodyLarge)
                TvFocusButton(text = "刷新配对码", onClick = { manager.refreshPairingCode(); remaining = manager.pairingRemainingSeconds })
            }
        }
    }
}
