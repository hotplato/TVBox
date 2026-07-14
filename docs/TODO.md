# TODO

跟踪已知问题与待办。完成后把条目移到「已完成」，并附提交/PR。

## 进行中 / 待办

### OkHttp 升级到 4.x

- **背景**：工程强制 `okhttp:3.12.13` + `okio:2.8.0`（OkGo / DoH / Coil `TvImageFetcher` 均依赖），与 Media3 `media3-datasource-okhttp` 及 Coil 默认网络栈不兼容。
- **范围**：去掉 `force` 升级到 OkHttp 4.x；适配 `OkGoHelper`、DoH、图片加载；评估是否改用 Media3 OkHttp DataSource。
- **说明**：不并入「Media3 + 去 DK 壳」播放器改造；播放期仍用 Media3 `DefaultHttpDataSource`。

### 首页豆瓣热播点进走搜索中转

- **现象**：点击首页推荐先到同名搜索，再选手动进详情；搜索结果封面也失败。
- **根因**：`HomeViewModel.parseDoubanHots` 只填 `name/note/pic`，无 `id`/`sourceKey`，`HomeScreen` 回退 `onOpenSearch(name)`。
- **方向**：补齐可播映射，或明确「搜同名」交互并优化体验。

### 直播秒退：频道列表为空

- **现象**：进 `LivePlayActivity` 后 Toast「频道列表为空」并 `finish()`。
- **说明**：更像当前配置无可用 `lives`；需确认配置解析与空态提示/引导。

### EventBus 事件在 Compose 侧无订阅

- **现象**：log 出现 `ServerEvent` / `RefreshEvent` 无订阅方。
- **方向**：经 `EventBusBridge` 接入 Compose，或清理不再需要的投递。

### JAR 相对路径 `./xxx` 装载失败

- **现象**：`JarLoader.load` 尝试 `/storage/emulated/0/TVBox/./ogthread.json` 报 `FileNotFoundException`。
- **方向**：规范化站点级 jar / 资源相对路径解析。

---

## 已完成

### 详情简介 HTML 实体未解码

- **根因**：遗留 `DetailActivity` 用 `Html.fromHtml`，Compose `DetailScreen` 直接 `Text` 未解码。
- **修复**：`htmlToPlainText`（`HtmlCompat`）剥离标签并解码实体后再展示。

### 详情页选集不可见

- **根因**：`DetailScreen` 外层 `Column` 不可滚，底部 `LazyVerticalGrid` 用 `fillMaxSize()` 与上方海报/线路争高，选集被裁切。
- **修复**：网格改为 `weight(1f).fillMaxWidth()`，占剩余视口高度并可纵向滚动。

### 源站封面加载失败（分类 / 搜索 / 详情）

- **根因**：① 荐片 JAR 仍输出已 NXDOMAIN 的图床 `static.ztcuc.com`；② fake-ip 环境下 AAAA=`fc00::` 优先导致连接失败。
- **修复**：`ImageHostRewrite` 按荐片官方链路刷新 `imgDomain` 并改写失效 host；`PreferIpv4Dns` 优先 IPv4 并丢弃 ULA IPv6；接入 `ImageHttpHeaders`（Coil / Picasso 共用）。
- **验证**：`emulator-5554` 电影分类封面恢复。

### 启动慢：JAR 主线程装载

- **根因**：`SpiderManager.loadJar` 缓存命中或 OkGo `onSuccess` 时在主线程执行 `JarLoader.load`（DexClassLoader + Init / sleep）。
- **修复**：`jarLoadExecutor` 后台执行 MD5 校验与 `JarLoader.load`；`success`/`error` 经主线程 Handler 回传，上层流水线不变。
- **提交**：`af72f5f`（2026-07-14）

### Compose 封面无法加载

- **根因**：① Coil 未走豆瓣 Referer / `@Headers`；② 工程强制 OkHttp 3.12 + Okio 2.8，Coil 2.7 默认网络栈不可用。
- **修复**：`ImageHttpHeaders` 共用逻辑 + `TvImageFetcher`（OkHttp3）+ `vodImageModel`。
- **提交**：`07559c7`（2026-07-14）
