# TODO

跟踪已知问题与待办。完成后把条目移到「已完成」，并附提交/PR。

## 进行中 / 待办

### 项目审计整改与 Java 迁移（2026-07-15）

- **验证基线**：`assembleDebug`、`assembleRelease` 通过；全局 `lintDebug` 失败（`app` 18 errors / 651 warnings 被配置忽略，`player` 3 errors / 20 warnings）；Release APK 约 16.4 MB、3 个 dex。
- **原则**：先修 Release 正确性、安全、并发和数据访问问题，再分层迁移；不做全仓机械 Java → Kotlin。

#### P0：Release 正确性与安全

- [ ] **恢复动态 Spider 宿主 ABI**：`app/proguard-rules.pro` 当前误保留 `com.hotplato.tvbox.crawler.*`，Release mapping 已将 `com.github.catvod.crawler.Spider` / `SpiderNull` 改名；改为保留真实 CatVod 宿主 API，并增加 Release APK 动态 JAR 烟测与 mapping 检查。
- [ ] **隔离配置/JAR 的可信网络通道**：配置、JAR、jsapi JAR 使用严格 TLS 与主机名校验；兼容旧媒体源的宽松 TLS 只能放在独立媒体客户端，不能覆盖配置、动态代码、DoH、Coil。
- [ ] **为动态 JAR 增加真实性校验**：使用应用内置公钥验证签名；远端配置中的 MD5/SHA-256 仅能校验一致性，不能防止配置与 JAR 被同时替换；禁止通过 HTTP 下载动态代码。
- [ ] **修复直播跨分组换台死循环**：`LivePlayActivityLegacy` 在只有一个分组或其余分组全部加密时可能无限循环；限制最多扫描 `groupCount` 次并提供无候选回退。
- [ ] **收紧播放嗅探 WebView/XWalk**：SSL 错误失败关闭；按场景关闭文件访问、file URL 跨域和不必要的混合内容，保留解析兼容性回归清单。

#### P1：并发、状态与数据层

- [ ] **恢复 Lint/CI 门禁**：修复现有 error 后启用 `checkReleaseBuilds`、`abortOnError`；处理 minSdk 23 调用 API 24、Media3 opt-in、`player` 网络权限 lint 和 Android TV touchscreen/leanback feature；CI 增加 PR 触发、SDK 35、Lint 与定向烟测。
- [ ] **重做 Spider 超时与容量控制**：`withTimeout` 不能中断阻塞 JAR/QuickJS；改为有界 executor、可取消 `Future/Call`、真实超时与每源实例总数限制，避免同源请求占满全局 permit。
- [ ] **修复 JS 取消与销毁**：解决 `Connect.client` 局部变量遮蔽、所有实例共用 `js_okhttp_tag`、QuickJS destroy 排在阻塞任务之后及 Timer 未清理问题。
- [ ] **为配置和爬虫运行时增加 generation**：配置加载、JAR 下载/切换、Spider reset、实例借还和页面请求都携带代次；旧任务不得覆盖新配置或归还到新实例池。
- [ ] **将 `SourceViewModel` 拆为 Kotlin `SourceRepository`**：以 `suspend`/Flow/typed result 替换公开 `MutableLiveData`、`observeForever` 和进程级 Bridge scope；结果携带 `RequestKey`，Home/Detail/Search 使用 latest-wins 与生命周期取消；Legacy 播放页暂留 Java/LiveData 门面。
- [ ] **将 `ApiConfig` 改为不可变配置快照**：先完整解析和校验 `ConfigSnapshot`，成功后原子发布并保留最后一次有效配置；并发加载采用 latest-wins；JarLoader 先验证临时 runtime，再原子切换和回收旧实例。
- [ ] **迁移 Room 数据层**：固定现有 schema 与备份兼容后改为 Kotlin suspend DAO/Flow；移除 `allowMainThreadQueries`，增加 `(sourceKey, vodId)` 唯一索引和事务 UPSERT，修复 restore 后复用已关闭数据库、全表扫描及历史无限增长。
- [ ] **修复播放/直播生命周期**：销毁时清理 Handler/Runnable；解析任务保存 `Future`、支持取消并校验 generation；远程播放状态只在主线程生成不可变快照，HTTP 线程不得直接访问播放器。
- [ ] **加强局域网控制服务**：配对接口增加失败限速、成功后轮换配对码、过期会话定期清理与会话上限；高风险配置/播放操作评估电视端确认。

#### P2：构建、体积与维护性

- [ ] **收紧 R8 规则并启用资源收缩**：先删除已不存在依赖的 Bugly/DK/IJK/旧 ExoPlayer 规则，将 AndroidX/OkHttp/Okio 整包 keep 缩小到真实反射/JNI 边界；动态 Spider、XWalk、Thunder、QuickJS 规则需逐项回归；稳定后启用 `shrinkResources true`。
- [ ] **治理依赖声明**：在现有“OkHttp 升级到 4.x”任务中统一 OkGo、DoH、Coil 和 Media3 网络栈；移除重复的 `fileTree`/本地 JAR/AAR 声明、无引用的 XXPermissions 和旧 `lifecycle-extensions`，缩小 `player` 的 `api` 暴露。
- [ ] **精简 Manifest 权限与组件暴露**：核实存储、定位、安装 APK、组播权限；`SearchReceiver` / `CustomWebReceiver` 改为不导出或删除无功能组件；为备份设置明确规则；保留动态 JAR 外部存储兼容测试。
- [ ] **修复播放器细节**：避免未准备好时清除播放进度；校正 Media3 16:9/4:3/原始比例映射、静音状态重建、网速显示和首帧 12 秒误判逻辑，并覆盖系统播放器与 Media3 双路径。
- [ ] **按测量优化启动与内存**：评估延迟加载 QuickJS/隐藏 API hook，按字节而非条目限制 Spider 缓存；通过 Macrobenchmark/Perfetto 和内存画像确认后再评估移除 `largeHeap`。
- [ ] **先删后迁**：确认并清理仅自引用的 Legacy 类/事件/Widget（静态审计约 785 行）及 `UA.java` 的 5,461 行整块注释；预计最多减少约 6,246 行源码噪声，主要收益是维护和索引成本。

#### Java 迁移顺序与边界

1. 建立配置解析、Room schema/备份、Release Spider ABI、源类型 0/1/3/4、系统/Media3 播放和 TV 焦点的最小特征验证。
2. 死代码清理 → 播放进度与 Room（约 599 行）→ `SourceRepository`（原 `SourceViewModel` 约 974 行）。
3. 简单 Bean 按需迁移，保留 Java getter/setter ABI、字段名和 Gson/XStream 行为；不要默认改为 `data class`。
4. `ApiConfig`（约 736 行）迁移为不可变状态核心，并保留 Java facade 与非主线程 JAR 装载约束。
5. 点播/直播 Legacy（约 4,045 行）按纵向功能重写，不做逐文件语法翻译；保留 `TvPlayerView` 的 `AndroidView` 边界，待旧 UI 完整退场后再删除 AppCompat/LoadSir/AutoSize/BRVAH 等依赖。
6. **暂不迁移**：`com.github.catvod.crawler` 外部 JAR ABI、JAR/QuickJS 反射运行时、Thunder/XWalk/JNI 适配层及稳定的 player 双后端；按具体缺陷修复。

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
