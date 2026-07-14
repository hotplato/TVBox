package com.hotplato.tvbox.ui.util

import com.hotplato.tvbox.ui.image.TvImageUrl
import com.hotplato.tvbox.ui.image.tvImageUrl

/** Compose 封面 model：空串置空，展开 proxy://，并走 OkHttp3 Fetcher。 */
fun vodImageModel(pic: String?): TvImageUrl? = tvImageUrl(pic)
