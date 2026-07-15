package com.hotplato.tvbox.ui.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.api.ApiConfig
import com.hotplato.tvbox.data.SettingsRepository
import com.hotplato.tvbox.data.WallpaperRepository
import com.hotplato.tvbox.ui.component.TvFocusButton
import com.hotplato.tvbox.ui.theme.TvMuted
import com.hotplato.tvbox.util.OkGoHelper
import com.hotplato.tvbox.util.PlayerHelper

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onRequestHomeReload: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wallpaper by WallpaperRepository.state.collectAsStateWithLifecycle()
    val wallpaperMessage by viewModel.wallpaperMessage.collectAsStateWithLifecycle()
    var showWallpaperOptions by remember { mutableStateOf(false) }
    var showApiEditor by remember { mutableStateOf(false) }
    var apiDraft by remember(state.apiUrl) { mutableStateOf(state.apiUrl) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp),
        ) {
            Text(text = "设置", style = MaterialTheme.typography.headlineMedium)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingRow("配置地址", state.apiUrl.ifBlank { "未设置" }) {
                showApiEditor = true
                apiDraft = state.apiUrl
            }
            SettingRow("调试", if (state.debugOpen) "已打开" else "已关闭", viewModel::toggleDebug)
            SettingRow("运行日志", "查看本次启动的诊断信息", onOpenDiagnostics)
            SettingRow(
                "解析 WebView",
                if (state.parseWebView) "系统自带" else "XWalkView",
                viewModel::toggleParseWebView,
            )
            SettingRow("播放器", PlayerHelper.getPlayerName(state.playType)) {
                viewModel.cyclePlayType()
            }
            SettingRow("渲染", PlayerHelper.getRenderName(state.playRender)) {
                viewModel.cyclePlayRender()
            }
            SettingRow("缩放", PlayerHelper.getScaleName(state.playScale)) {
                viewModel.cyclePlayScale()
            }
            val dnsName = OkGoHelper.dnsHttpsList.getOrNull(state.dohUrl) ?: "关闭"
            SettingRow("DoH", dnsName) { viewModel.cycleDoh() }
            SettingRow(
                "首页推荐",
                when (state.homeRec) {
                    1 -> "数据源推荐"
                    2 -> "历史"
                    else -> "豆瓣热播"
                },
            ) { viewModel.cycleHomeRec() }
            SettingRow(
                "搜索展示",
                if (state.searchView == 1) "缩略图" else "列表",
            ) { viewModel.cycleSearchView() }
            SettingRow(
                "壁纸",
                when (wallpaper.source) {
                    WallpaperRepository.SOURCE_WALLHAVEN -> "Wallhaven 安全随机"
                    WallpaperRepository.SOURCE_BING -> "Bing 每日壁纸"
                    WallpaperRepository.SOURCE_UPLOAD -> "自定义上传"
                    else -> "应用默认"
                },
            ) { showWallpaperOptions = true }
            if (wallpaperMessage.isNotBlank()) {
                Text(wallpaperMessage, color = TvMuted, style = MaterialTheme.typography.bodyMedium)
            }
            SettingRow("应用设置并重载首页", "点击后重新拉取配置") {
                SettingsRepository.refresh()
                onRequestHomeReload()
            }
            val stores = ApiConfig.get().storeBeanList
            if (stores.isNotEmpty()) {
                Text(
                    text = "仓库: ${state.storeName.ifBlank { state.storeApi.ifBlank { "未选择" } }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                stores.forEach { store ->
                    TvFocusButton(
                        text = store.name ?: store.url,
                        onClick = {
                            viewModel.selectStore(store.url, store.name ?: "")
                            onRequestHomeReload()
                        },
                        selected = store.url == state.storeApi,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showApiEditor) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showApiEditor = false }) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("输入配置地址", style = MaterialTheme.typography.titleLarge)
                androidx.compose.foundation.text.BasicTextField(
                    value = apiDraft,
                    onValueChange = { apiDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvFocusButton(text = "取消", onClick = { showApiEditor = false })
                    TvFocusButton(
                        text = "保存",
                        onClick = {
                            viewModel.saveApiUrl(apiDraft.trim())
                            showApiEditor = false
                            onRequestHomeReload()
                        },
                    )
                }
            }
        }
    }
    if (showWallpaperOptions) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showWallpaperOptions = false }) {
            Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("壁纸设置", style = MaterialTheme.typography.titleLarge)
                TvFocusButton(text = "Wallhaven 安全随机", onClick = { viewModel.updateWallpaper(WallpaperRepository.SOURCE_WALLHAVEN); showWallpaperOptions = false })
                TvFocusButton(text = "Bing 每日壁纸", onClick = { viewModel.updateWallpaper(WallpaperRepository.SOURCE_BING); showWallpaperOptions = false })
                TvFocusButton(text = "更新当前服务", onClick = { viewModel.refreshWallpaper(); showWallpaperOptions = false })
                TvFocusButton(text = "恢复默认壁纸", onClick = { viewModel.resetWallpaper(); showWallpaperOptions = false })
                TvFocusButton(text = "取消", onClick = { showWallpaperOptions = false })
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TvMuted)
        }
        TvFocusButton(text = "更改", onClick = onClick)
    }
}
