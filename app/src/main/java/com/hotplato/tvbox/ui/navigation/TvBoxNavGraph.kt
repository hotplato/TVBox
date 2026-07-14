package com.hotplato.tvbox.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hotplato.tvbox.ui.feature.collect.CollectScreen
import com.hotplato.tvbox.ui.feature.detail.DetailScreen
import com.hotplato.tvbox.ui.feature.history.HistoryScreen
import com.hotplato.tvbox.ui.feature.home.HomeScreen
import com.hotplato.tvbox.ui.feature.home.HomeViewModel
import com.hotplato.tvbox.ui.feature.mine.MineScreen
import com.hotplato.tvbox.ui.feature.push.PushScreen
import com.hotplato.tvbox.ui.feature.search.SearchScreen
import com.hotplato.tvbox.ui.feature.settings.SettingsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun TvBoxNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var restoreMineFocus by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = TvBoxRoutes.HOME,
        modifier = modifier,
    ) {
        composable(TvBoxRoutes.HOME) {
            HomeScreen(
                onOpenDetail = { sourceKey, vodId ->
                    navController.navigate(TvBoxRoutes.detail(sourceKey, vodId))
                },
                onOpenSearch = { query ->
                    navController.navigate(TvBoxRoutes.search(query))
                },
                onOpenMine = { navController.navigate(TvBoxRoutes.MINE) },
                restoreMineFocus = restoreMineFocus,
                onMineFocusConsumed = { restoreMineFocus = false },
                viewModel = homeViewModel,
            )
        }
        composable(TvBoxRoutes.MINE) {
            val leaveMine: () -> Unit = {
                restoreMineFocus = true
                navController.popBackStack()
            }
            BackHandler(onBack = leaveMine)
            MineScreen(
                onBack = leaveMine,
                onOpenHistory = { navController.navigate(TvBoxRoutes.HISTORY) },
                onOpenCollect = { navController.navigate(TvBoxRoutes.COLLECT) },
                onOpenPush = { navController.navigate(TvBoxRoutes.PUSH) },
                onOpenSettings = { navController.navigate(TvBoxRoutes.SETTINGS) },
            )
        }
        composable(
            route = "search?q={q}",
            arguments = listOf(
                navArgument("q") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val q = entry.arguments?.getString("q")
            SearchScreen(
                initialQuery = q?.let { decode(it) },
                onOpenDetail = { sourceKey, vodId ->
                    navController.navigate(TvBoxRoutes.detail(sourceKey, vodId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(TvBoxRoutes.SEARCH) {
            SearchScreen(
                initialQuery = null,
                onOpenDetail = { sourceKey, vodId ->
                    navController.navigate(TvBoxRoutes.detail(sourceKey, vodId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(TvBoxRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRequestHomeReload = {
                    homeViewModel.bootstrap(false)
                    navController.popBackStack(TvBoxRoutes.HOME, inclusive = false)
                },
            )
        }
        composable(TvBoxRoutes.HISTORY) {
            HistoryScreen(
                onOpenDetail = { sourceKey, vodId ->
                    navController.navigate(TvBoxRoutes.detail(sourceKey, vodId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(TvBoxRoutes.COLLECT) {
            CollectScreen(
                onOpenDetail = { sourceKey, vodId ->
                    navController.navigate(TvBoxRoutes.detail(sourceKey, vodId))
                },
                onOpenSearch = { query ->
                    navController.navigate(TvBoxRoutes.search(query))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(TvBoxRoutes.PUSH) {
            PushScreen(
                onOpenDetail = { sourceKey, vodId ->
                    navController.navigate(TvBoxRoutes.detail(sourceKey, vodId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = TvBoxRoutes.DETAIL,
            arguments = listOf(
                navArgument("sourceKey") { type = NavType.StringType },
                navArgument("vodId") { type = NavType.StringType },
            ),
        ) { entry ->
            val sourceKey = decode(entry.arguments?.getString("sourceKey").orEmpty())
            val vodId = decode(entry.arguments?.getString("vodId").orEmpty())
            DetailScreen(
                sourceKey = sourceKey,
                vodId = vodId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
