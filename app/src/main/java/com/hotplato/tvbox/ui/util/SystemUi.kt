package com.hotplato.tvbox.ui.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager

fun Activity.hideSystemBars() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        val decor = window.decorView
        var uiOptions = decor.systemUiVisibility
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decor.systemUiVisibility = uiOptions
    }
}
