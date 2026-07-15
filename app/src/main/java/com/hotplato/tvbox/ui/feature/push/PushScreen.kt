package com.hotplato.tvbox.ui.feature.push

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.server.ControlManager
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.ui.tv.QRCodeGen

@Composable
fun PushScreen(
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val address = remember { ControlManager.get().getAddress(false) ?: "" }
    val qrBitmap: Bitmap? = remember(address) {
        if (address.isBlank()) null
        else QRCodeGen.generateBitmap(address, 320, 320, 4)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvFocusButton(text = "返回", onClick = onBack)
            Text(
                text = "远程推送",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "推送二维码",
                    modifier = Modifier.size(300.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    text = "手机/电脑扫描二维码，或在浏览器访问：",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvMuted,
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.titleLarge,
                )
                TvFocusButton(
                    text = "推送剪贴板内容",
                    onClick = {
                        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString()?.trim()
                        if (!text.isNullOrBlank()) {
                            onOpenDetail("push_agent", text)
                        }
                    },
                )
            }
        }
    }
}
