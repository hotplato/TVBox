package com.hotplato.tvbox.ui.util

import androidx.core.text.HtmlCompat

/** 将简介等含 HTML 标签/实体的文案转为可读纯文本（对齐遗留 DetailActivity 的 Html.fromHtml）。 */
fun htmlToPlainText(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .trim()
}
