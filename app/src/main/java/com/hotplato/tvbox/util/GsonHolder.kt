package com.hotplato.tvbox.util

import com.google.gson.Gson

/** 进程内复用 Gson，避免热点路径反复 new。 */
object GsonHolder {
    @JvmField
    val gson: Gson = Gson()
}
