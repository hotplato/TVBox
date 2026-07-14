package com.hotplato.tvbox.ui.play

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.activity.PlayActivityLegacy
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.theme.TvTheme

/**
 * Kotlin Compose shell: legacy VideoView/WebView tree from [PlayActivityLegacy],
 * with an optional Compose chrome overlay (Menu toggles).
 */
class PlayActivity : PlayActivityLegacy() {
    private var chromeVisible by mutableStateOf(false)
    private var composeHost: ComposeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = findViewById<ViewGroup>(android.R.id.content)
        composeHost = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.GONE
            isFocusable = false
            isClickable = false
            setContent {
                TvTheme {
                    // AndroidView marker kept for the interop boundary with the player surface below.
                    AndroidView(factory = { FrameLayout(it) }, modifier = Modifier)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("播放控制（Compose）", style = MaterialTheme.typography.titleMedium)
                        TvFocusButton(text = "关闭", onClick = { updateChrome(false) })
                    }
                }
            }
        }
        content.addView(
            composeHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = android.view.Gravity.TOP },
        )
    }

    private fun updateChrome(visible: Boolean) {
        chromeVisible = visible
        composeHost?.visibility = if (visible) View.VISIBLE else View.GONE
        composeHost?.isFocusable = visible
        composeHost?.isClickable = visible
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
            updateChrome(!chromeVisible)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
