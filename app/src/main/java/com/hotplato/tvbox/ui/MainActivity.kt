package com.hotplato.tvbox.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.hotplato.tvbox.ui.feature.home.HomeViewModel
import com.hotplato.tvbox.ui.navigation.TvBoxNavGraph
import com.hotplato.tvbox.ui.navigation.TvBoxRoutes
import com.hotplato.tvbox.ui.theme.TvTheme
import com.hotplato.tvbox.ui.util.hideSystemBars

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
            TvTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
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
