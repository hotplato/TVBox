package com.hotplato.tvbox.ui.image

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.hotplato.tvbox.util.DefaultConfig
import com.hotplato.tvbox.util.ImageHttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okio.Buffer

/** 标记走工程 OkHttp3 拉图，避开 Coil 自带 OkHttp4 网络栈。 */
data class TvImageUrl(val url: String)

/**
 * Coil 2 默认网络栈需要 OkHttp 4 / Okio 3，
 * 本工程强制 OkHttp 3.12 + Okio 2.8，因此用 OkGo 同款 Call.Factory。
 */
class TvImageFetcher(
    private val url: String,
    private val callFactory: Call.Factory,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val request = ImageHttpHeaders.apply(Request.Builder().url(url).build())
        callFactory.newCall(request).execute().use { response ->
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw IllegalStateException("HTTP ${response.code()} for $url")
            }
            val bytes = body.bytes()
            val buffer = Buffer().write(bytes)
            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = body.contentType()?.toString()?.substringBefore(';')?.trim(),
                dataSource = DataSource.NETWORK,
            )
        }
    }

    class Factory(
        private val callFactory: Call.Factory,
    ) : Fetcher.Factory<TvImageUrl> {
        override fun create(data: TvImageUrl, options: Options, imageLoader: ImageLoader): Fetcher {
            return TvImageFetcher(data.url, callFactory, options)
        }
    }
}

fun tvImageUrl(pic: String?): TvImageUrl? {
    if (pic.isNullOrBlank()) return null
    return TvImageUrl(DefaultConfig.checkReplaceProxy(pic))
}
