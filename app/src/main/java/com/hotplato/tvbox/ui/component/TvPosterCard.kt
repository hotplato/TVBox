package com.hotplato.tvbox.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.theme.TvFocusBorder
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.ui.theme.TvSurface

@Composable
fun TvPosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, TvFocusBorder, shape) else Modifier),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSurface,
            focusedContainerColor = TvSurface,
            pressedContainerColor = TvSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            VodCoverImage(
                pic = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Text(
                text = title.take(40),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle.take(24),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
