package com.hotplato.tvbox.ui.image

import android.content.Context
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient

object CoilSetup {
    @JvmStatic
    fun init(context: Context, client: OkHttpClient) {
        val imageLoader = ImageLoader.Builder(context.applicationContext)
            .components { add(TvImageFetcher.Factory(client)) }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
