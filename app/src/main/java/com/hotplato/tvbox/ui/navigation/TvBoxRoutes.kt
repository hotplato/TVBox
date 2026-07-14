package com.hotplato.tvbox.ui.navigation

object TvBoxRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val COLLECT = "collect"
    const val PUSH = "push"
    const val DETAIL = "detail/{sourceKey}/{vodId}"

    fun detail(sourceKey: String, vodId: String): String =
        "detail/${encode(sourceKey)}/${encode(vodId)}"

    fun search(query: String? = null): String =
        if (query.isNullOrBlank()) SEARCH else "search?q=${encode(query)}"

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
