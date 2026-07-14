package com.hotplato.tvbox.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hotplato.tvbox.R
import com.hotplato.tvbox.ui.util.vodImageModel

/** 点播封面：加载中 / 失败 / 无图统一占位，避免空白。 */
@Composable
fun VodCoverImage(
    pic: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val model = vodImageModel(pic)
    // layer-list 不用 painterResource（Compose 仅支持矢量/位图）；走 Coil 拉 Android Drawable
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model ?: R.drawable.img_loading_placeholder)
            .placeholder(R.drawable.img_loading_placeholder)
            .error(R.drawable.img_loading_placeholder)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
