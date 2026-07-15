package com.hotplato.tvbox.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import com.hotplato.tvbox.ui.feature.home.HomeViewModel
import com.hotplato.tvbox.ui.navigation.TvBoxNavGraph
import com.hotplato.tvbox.ui.navigation.TvBoxRoutes
import com.hotplato.tvbox.ui.theme.TvTheme
import com.hotplato.tvbox.ui.util.hideSystemBars
import com.hotplato.tvbox.server.RemotePlaybackBridge
import com.hotplato.tvbox.data.WallpaperRepository
import com.google.gson.JsonObject
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        pendingRoute = intentToRoute(intent)
        if (intent.getBooleanExtra(EXTRA_REMOTE_CONFIG_CHANGED, false)) {
            homeViewModel.bootstrap(false)
        }
        setContent {
            val wallpaper by WallpaperRepository.state.collectAsStateWithLifecycle()
            TvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val wallpaperPath = wallpaper.cacheFile?.absolutePath
                    if (wallpaperPath != null) {
                        val wallpaperBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                            initialValue = null,
                            key1 = wallpaperPath,
                            key2 = wallpaper.version,
                        ) {
                            value = null
                            value = withContext(Dispatchers.IO) {
                                decodeWallpaper(wallpaperPath)
                            }
                        }
                        val bitmap = wallpaperBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Image(
                                painter = painterResource(com.hotplato.tvbox.R.drawable.app_bg),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(com.hotplato.tvbox.R.drawable.app_bg),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val navController = rememberNavController()
                            LaunchedEffect(pendingRoute) {
                                val route = pendingRoute ?: return@LaunchedEffect
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                                pendingRoute = null
                            }
                            TvBoxNavGraph(
                                navController = navController,
                                homeViewModel = homeViewModel,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun decodeWallpaper(path: String): androidx.compose.ui.graphics.ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val targetHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetWidth &&
            bounds.outHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intentToRoute(intent)
        if (intent.getBooleanExtra(EXTRA_REMOTE_CONFIG_CHANGED, false)) {
            homeViewModel.bootstrap(false)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        RemotePlaybackBridge.register(remoteKeyTarget)
    }

    override fun onPause() {
        RemotePlaybackBridge.unregister(remoteKeyTarget)
        super.onPause()
    }

    private val remoteKeyTarget = object : RemotePlaybackBridge.Target {
        override fun command(action: String, value: Double): String? {
            val keyCode = when (action) {
                "key_up" -> KeyEvent.KEYCODE_DPAD_UP
                "key_down" -> KeyEvent.KEYCODE_DPAD_DOWN
                "key_left" -> KeyEvent.KEYCODE_DPAD_LEFT
                "key_right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "key_enter" -> KeyEvent.KEYCODE_DPAD_CENTER
                "key_back" -> KeyEvent.KEYCODE_BACK
                else -> return "不支持的遥控按键"
            }
            runOnUiThread {
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            return null
        }

        override fun state(): JsonObject = JsonObject().apply { addProperty("available", false) }
    }

    private fun intentToRoute(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.getStringExtra(EXTRA_ROUTE)) {
            ROUTE_SEARCH -> TvBoxRoutes.search(intent.getStringExtra(EXTRA_QUERY))
            else -> null
        }
    }

    companion object {
        const val EXTRA_ROUTE = "compose_route"
        const val EXTRA_QUERY = "compose_query"
        const val ROUTE_SEARCH = "search"
        const val EXTRA_REMOTE_CONFIG_CHANGED = "remote_config_changed"
    }
}
