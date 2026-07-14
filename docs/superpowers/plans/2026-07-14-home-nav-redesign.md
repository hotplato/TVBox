# 首页导航重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Compose 首页从左栏大杂烩改为顶部分类 Tab + 右侧快捷入口，并新增简洁「我的」列表页。

**Architecture:** 首页壳拆为 `HomeTopBar`（源名 / `TvCategoryTabRow` / 搜索·直播·我的）+ 满宽 `LazyVerticalGrid`；「我的」为独立路由 `MineScreen` 仅做跳转；切源用全屏轻量列表覆盖层，对齐遗留 `showSiteSwitch` 行为。`HomeViewModel` 保持现有 sort/list 数据流，仅补 `switchHomeSource`。

**Tech Stack:** Kotlin、Jetpack Compose for TV（`androidx.tv.material3`）、Navigation-Compose、现有 `TvFocusButton` / `TvPosterCard` / `HomeViewModel`

**验证约定：** 仓库当前无单元测试框架（见 AGENTS.md），不新增测试框架；每任务以编译检查 + 列出手工焦点验收点代替 TDD。Git commit 仅在用户明确要求时执行（勿自动 commit）。

**规格：** [docs/superpowers/specs/2026-07-14-home-nav-redesign-design.md](../specs/2026-07-14-home-nav-redesign-design.md)

---

## File Structure

| 文件 | 职责 |
|---|---|
| Create: `app/src/main/java/com/hotplato/tvbox/ui/component/TvCategoryTabRow.kt` | 横向可滚动分类 Tab，选中/焦点态 |
| Create: `app/src/main/java/com/hotplato/tvbox/ui/feature/home/HomeTopBar.kt` | 首页顶栏组合（源名 + Tab + 快捷） |
| Create: `app/src/main/java/com/hotplato/tvbox/ui/feature/mine/MineScreen.kt` | 「我的」简洁列表 |
| Modify: `app/src/main/java/com/hotplato/tvbox/ui/feature/home/HomeScreen.kt` | 去掉左栏；顶栏 + 网格；切源覆盖层 |
| Modify: `app/src/main/java/com/hotplato/tvbox/ui/feature/home/HomeViewModel.kt` | 增加 `switchHomeSource` |
| Modify: `app/src/main/java/com/hotplato/tvbox/ui/navigation/TvBoxRoutes.kt` | 增加 `MINE` |
| Modify: `app/src/main/java/com/hotplato/tvbox/ui/navigation/TvBoxNavGraph.kt` | 注册 Mine；首页 `onOpenMine` |

---

### Task 1: TvCategoryTabRow

**Files:**
- Create: `app/src/main/java/com/hotplato/tvbox/ui/component/TvCategoryTabRow.kt`

- [ ] **Step 1: 新增横向分类 Tab 组件**

```kotlin
package com.hotplato.tvbox.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TvCategoryTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in labels.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier,
    ) {
        itemsIndexed(labels) { index, label ->
            TvFocusButton(
                text = label,
                onClick = { onSelect(index) },
                selected = index == selectedIndex,
            )
        }
    }
}
```

- [ ] **Step 2: 编译确认模块可解析（先不要求整 APK）**

Run: `./gradlew :app:compileDebugKotlin`

Expected: 成功，或仅有与本文件无关的既有警告/错误时记下；本文件本身无 unresolved reference。

---

### Task 2: HomeTopBar

**Files:**
- Create: `app/src/main/java/com/hotplato/tvbox/ui/feature/home/HomeTopBar.kt`

- [ ] **Step 1: 创建顶栏组合**

```kotlin
package com.hotplato.tvbox.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.hotplato.tvbox.ui.component.TvCategoryTabRow
import com.hotplato.tvbox.ui.component.TvFocusButton

@Composable
fun HomeTopBar(
    homeName: String,
    sortLabels: List<String>,
    selectedSortIndex: Int,
    onSelectSort: (Int) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenMine: () -> Unit,
    mineFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvFocusButton(
            text = homeName.ifBlank { "TVBox" },
            onClick = onOpenSourcePicker,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        TvCategoryTabRow(
            labels = sortLabels,
            selectedIndex = selectedSortIndex,
            onSelect = onSelectSort,
            modifier = Modifier.weight(1f),
        )
        TvFocusButton(text = "搜索", onClick = onOpenSearch)
        TvFocusButton(text = "直播", onClick = onOpenLive)
        TvFocusButton(
            text = "我的",
            onClick = onOpenMine,
            modifier = Modifier.focusRequester(mineFocusRequester),
        )
    }
}
```

- [ ] **Step 2: 再次 compileDebugKotlin**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

---

### Task 3: HomeViewModel 切源

**Files:**
- Modify: `app/src/main/java/com/hotplato/tvbox/ui/feature/home/HomeViewModel.kt`

- [ ] **Step 1: 增加主源切换并重载分类**

在 `selectStore` 附近添加（对齐遗留 `HomeActivity.showSiteSwitch` → `ApiConfig.setSourceBean` + 重新 `getSort`）：

```kotlin
fun switchHomeSource(source: com.hotplato.tvbox.bean.SourceBean) {
    ApiConfig.get().setSourceBean(source)
    _uiState.update {
        it.copy(
            homeName = source.name ?: "",
            loading = true,
            error = null,
            sorts = emptyList(),
            videos = emptyList(),
            selectedSortIndex = 0,
        )
    }
    val key = source.key
    sourceViewModel.getSort(key)
}
```

使用已有 import；若缺 `SourceBean` 则补上。

- [ ] **Step 2: compileDebugKotlin**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

---

### Task 4: 重写 HomeScreen

**Files:**
- Modify: `app/src/main/java/com/hotplato/tvbox/ui/feature/home/HomeScreen.kt`

- [ ] **Step 1: 替换整页布局**

签名增加 `onOpenMine`；去掉左栏；用 `HomeTopBar` + 网格；切源用本地 `var showSourcePicker`。

要点代码结构（替换 `HomeScreen` 函数体主体，保留 `storePrompt` 分支与海报点击逻辑）：

```kotlin
@Composable
fun HomeScreen(
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onOpenSearch: (query: String?) -> Unit,
    onOpenMine: () -> Unit,
    onOpenSettings: () -> Unit, // 可删除若顶栏不再直达设置；保留给兼容亦可但本规格顶栏不进设置
    onOpenHistory: () -> Unit,
    onOpenCollect: () -> Unit,
    onOpenPush: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    // 规格：设置/历史/收藏/推送只从「我的」进。
    // 从签名移除 onOpenSettings/History/Collect/Push，仅保留 onOpenMine + onOpenSearch + onOpenDetail。
}
```

**最终签名（本任务落实）：**

```kotlin
@Composable
fun HomeScreen(
    onOpenDetail: (sourceKey: String, vodId: String) -> Unit,
    onOpenSearch: (query: String?) -> Unit,
    onOpenMine: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
)
```

**主内容骨架：**

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
val context = LocalContext.current
var showSourcePicker by remember { mutableStateOf(false) }
val mineFocusRequester = remember { FocusRequester() }
val navEntry = androidx.navigation.compose.currentBackStackEntryAsState() // 勿强依赖；改用：
// 从 Mine 返回后请求焦点：LaunchedEffect(Unit) 不够；
// 在 NavGraph pop 回 HOME 时由父级不强制；HomeScreen 用：
val lifecycleOwner = LocalLifecycleOwner.current
// 简化：首次组合不强求；Mine 返回时 Home 重组，用：
DisposableEffect(Unit) {
    onDispose { }
}
// 实际采用：rememberSaveable + 父级传入 restoreMineFocus: Boolean
```

为降低耦合，**焦点回「我的」**采用：

```kotlin
// HomeScreen 参数
restoreMineFocus: Boolean = false,
onMineFocusConsumed: () -> Unit = {},
```

```kotlin
LaunchedEffect(restoreMineFocus) {
    if (restoreMineFocus) {
        mineFocusRequester.requestFocus()
        onMineFocusConsumed()
    }
}
```

`NavGraph` 在离开 `MINE` pop 回 `HOME` 时设一个 `var restoreMineFocus`（见 Task 6）。

切源覆盖层（与 store 提示类似）：

```kotlin
if (showSourcePicker) {
    val sites = remember { ApiConfig.get().sourceBeanList }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("选择主源", style = MaterialTheme.typography.headlineMedium)
        TvFocusButton(text = "取消", onClick = { showSourcePicker = false })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sites.size) { i ->
                val site = sites[i]
                TvFocusButton(
                    text = site.name ?: site.key,
                    selected = site.key == ApiConfig.get().homeSourceBean?.key,
                    onClick = {
                        viewModel.switchHomeSource(site)
                        showSourcePicker = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    return
}
```

注意：`ApiConfig.get().sourceBeanList` 在 Kotlin 为属性访问 `sourceBeanList`（Java getter）。

正常态：

```kotlin
Column(Modifier.fillMaxSize()) {
    HomeTopBar(
        homeName = state.homeName,
        sortLabels = state.sorts.map { it.name ?: it.id },
        selectedSortIndex = state.selectedSortIndex,
        onSelectSort = viewModel::selectSort,
        onOpenSourcePicker = { showSourcePicker = true },
        onOpenSearch = { onOpenSearch(null) },
        onOpenLive = {
            context.startActivity(Intent(context, LivePlayActivity::class.java))
        },
        onOpenMine = onOpenMine,
        mineFocusRequester = mineFocusRequester,
    )
    // when loading/error/empty/grid — 与现逻辑相同，去掉左侧 Row
}
```

删除对 `onOpenSettings` / `onOpenHistory` / `onOpenCollect` / `onOpenPush` 的依赖。

- [ ] **Step 2: compileDebugKotlin**（此时 NavGraph 仍传旧参数，会失败属预期；进入 Task 5–6 修好）

若希望本任务单独可编译，可暂时保留旧参数为 unused `@Suppress` 或立即进入 Task 5。

---

### Task 5: MineScreen

**Files:**
- Create: `app/src/main/java/com/hotplato/tvbox/ui/feature/mine/MineScreen.kt`

- [ ] **Step 1: 实现简洁列表页**

```kotlin
package com.hotplato.tvbox.ui.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hotplato.tvbox.ui.component.TvFocusButton

@Composable
fun MineScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCollect: () -> Unit,
    onOpenPush: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            TvFocusButton(text = "返回", onClick = onBack)
            Text(text = "我的", style = MaterialTheme.typography.headlineMedium)
        }
        TvFocusButton(text = "历史记录", onClick = onOpenHistory, modifier = Modifier.fillMaxWidth())
        TvFocusButton(text = "我的收藏", onClick = onOpenCollect, modifier = Modifier.fillMaxWidth())
        TvFocusButton(text = "推送", onClick = onOpenPush, modifier = Modifier.fillMaxWidth())
        TvFocusButton(text = "设置", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
    }
}
```

- [ ] **Step 2: 确认无 ViewModel（纯导航壳）**

无需额外类。

---

### Task 6: 路由与导航图

**Files:**
- Modify: `app/src/main/java/com/hotplato/tvbox/ui/navigation/TvBoxRoutes.kt`
- Modify: `app/src/main/java/com/hotplato/tvbox/ui/navigation/TvBoxNavGraph.kt`
- Modify: `app/src/main/java/com/hotplato/tvbox/ui/MainActivity.kt`（仅当需持有 `restoreMineFocus` 状态时）

- [ ] **Step 1: 增加路由常量**

在 `TvBoxRoutes`：

```kotlin
const val MINE = "mine"
```

- [ ] **Step 2: 更新 NavGraph**

在 `TvBoxNavGraph`：

```kotlin
var restoreMineFocus by remember { mutableStateOf(false) }

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
    MineScreen(
        onBack = {
            restoreMineFocus = true
            navController.popBackStack()
        },
        onOpenHistory = { navController.navigate(TvBoxRoutes.HISTORY) },
        onOpenCollect = { navController.navigate(TvBoxRoutes.COLLECT) },
        onOpenPush = { navController.navigate(TvBoxRoutes.PUSH) },
        onOpenSettings = { navController.navigate(TvBoxRoutes.SETTINGS) },
    )
}
```

`settings` 的 `onRequestHomeReload` 逻辑保持不变。从「我的」进设置再返回时，默认 `pop` 回「我的」即可（Navigation 默认栈）。

补齐 `remember` / `mutableStateOf` / `MineScreen` import。

- [ ] **Step 3: 全量 Debug 编译**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`，产物 `app/build/outputs/apk/debug/`

---

### Task 7: 手工验收清单

**Files:** 无代码变更（或仅修焦点小问题）

- [ ] **Step 1: 真机 / 模拟器横屏走查**

1. 首页无左栏；顶部分类可左右焦点移动并切换内容；海报表多行可下移  
2. 顶栏：搜索进搜索页；直播进直播 Activity；我的进列表  
3. 「我的」四项分别进历史 / 收藏 / 推送 / 设置；返回键与「返回」按钮均可回退  
4. 从「我的」返回首页后，焦点落在「我的」按钮（`restoreMineFocus`）  
5. 点击源名可切主源，切后分类与列表刷新  
6. 仓库选择提示（多仓）仍可用  

- [ ] **Step 2: 对照规格勾验收项**

规格验收 1–4 全部满足。

---

## Spec Coverage (self-review)

| 规格要求 | 任务 |
|---|---|
| 去左栏 + 顶部分类 Tab | Task 1, 2, 4 |
| 满宽多行网格 | Task 4 |
| 搜索/直播/我的顶栏入口 | Task 2, 4 |
| 「我的」简洁列表四项 | Task 5, 6 |
| 切源 | Task 3, 4 |
| 焦点两层 + 回「我的」 | Task 2, 4, 6 |
| 不改子页内部 | 未改 History/Collect/Settings/Push/Search 逻辑 |
| assembleDebug | Task 6 Step 3 |

无 TBD/占位步骤。`HomeScreen` 最终签名与 Task 6 导航一致：`onOpenMine` + `restoreMineFocus` / `onMineFocusConsumed`。
