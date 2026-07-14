# AGENTS.md

面向 AI 编码代理的项目说明。本文档描述 TVBox 的架构、约定与常用命令，帮助代理在仓库中安全、一致地修改代码。

## 项目概览

TVBox 是一款面向 Android TV / 机顶盒的影视聚合客户端（横屏 `landscape`）。应用通过远程 JSON 配置加载视频源，并支持动态 JAR 爬虫（CatVod CSP）与 QuickJS 的 JS 爬虫（`.js` / drpy），以及直播、点播播放与本地 Web 控制服务。

| 项 | 值 |
|---|---|
| 语言 | Kotlin（UI）+ Java 17（业务/播放/爬虫） |
| UI | Jetpack Compose + Compose for TV（`androidx.tv`） |
| 构建 | Gradle 8.9 + Android Gradle Plugin 8.7.2 + Kotlin 2.0.21 |
| `compileSdk` / `targetSdk` | 35 / 34 |
| `minSdk` | 23（Compose for TV 要求） |
| 包名 | `com.hotplato.tvbox` |
| ABI | `armeabi-v7a` + `arm64-v8a` |

## 模块结构

```
TVBox/
├── app/          # 主应用：UI、配置、爬虫加载、本地服务、缓存
└── player/       # 播放器库：Media3 / 系统 MediaPlayer（TvPlayerView）
```

### `app` 核心包

| 包路径 | 职责 |
|---|---|
| `com.hotplato.tvbox.base` | `App` 入口；遗留 `BaseActivity` / `BaseLazyFragment` |
| `com.hotplato.tvbox.api` | `ApiConfig`：远程配置解析、源/解析/直播管理（Java） |
| `com.hotplato.tvbox.crawler` | `SpiderManager`（双轨门面）、`JarLoader`、`JsLoader`（Java） |
| `com.hotplato.tvbox.ui` | Compose UI：`MainActivity`、`theme`、`feature/*`、`navigation` |
| `com.hotplato.tvbox.ui.play` / `ui.live` | 播放 Compose 外壳 + Legacy View 树（`AndroidView`/叠加） |
| `com.hotplato.tvbox.data` | Kotlin Repository / `SettingsRepository` / `EventBusBridge` |
| `com.hotplato.tvbox.player` | 点播/直播叠加控制器（绑定 `TvPlayerView`） |
| `com.hotplato.tvbox.server` | `ControlManager`、`RemoteServer`：局域网 Web 控制 |
| `com.hotplato.tvbox.cache` | Room 数据库与 DAO |
| `com.hotplato.tvbox.bean` | 数据模型（`SourceBean`、`VodInfo` 等） |
| `com.hotplato.tvbox.util` | 工具类；`HawkConfig` 为配置键常量 |
| `com.hotplato.tvbox.event` | EventBus 事件定义（Compose 经 `EventBusBridge` 接入） |

### 关键单例

- `App.getInstance()` — Application 上下文
- `ApiConfig.get()` — 视频源与配置中心
- `ControlManager.get()` — 本地 HTTP 服务
- `Hawk.get()` / `Hawk.put()` — 持久化用户设置（键定义在 `HawkConfig`）

## 构建与验证

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（CI 使用此命令）
./gradlew assembleRelease

# 清理
./gradlew clean
```

**环境要求**：Android SDK（`compileSdk 35`）、JDK 17。`local.properties` 需配置 `sdk.dir`，该文件已 gitignore，勿提交。

**验证清单**（修改后尽量执行）：

1. `./gradlew assembleDebug` 编译通过
2. 若改动 UI：确认横屏布局在 1280×720 设计稿下正常（Compose 用 `Dp`；遗留 XML 仍可能走 AutoSize）
3. 若改动 `ApiConfig` / 爬虫：确认不在主线程调用 `JarLoader.load()` / `SpiderManager` 的 JAR 装载
4. 若改动播放逻辑：分别验证系统播放器（`PLAY_TYPE=0`）与 Media3（`PLAY_TYPE=2`）路径
5. 若改动迅雷相关：确认 APK 内仍可包含 `lib/armeabi-v7a/libxl_*.so`（无 arm64 迅雷 so）

项目当前**无单元测试**；CI 仅做 release 构建（`.github/workflows/test.yml`，`workflow_dispatch` 触发）。

### 原生库与本地依赖

| 路径 | 说明 |
|---|---|
| `player/src/main/jniLibs/armeabi-v7a/` | 迅雷 `libxl_*.so`（32 位） |
| `app/libs/thunder.jar` | 迅雷 Java SDK |
| `app/libs/xwalk_shared_library-23.53.589.4.aar` | Crosswalk WebView（原 Maven 仓库已下线，本地引用） |

**迅雷 64 位**：`libxl_thunder_sdk.so` / `libxl_stat.so` 暂无 arm64 版；`Thunder.isSupported()` 在 64 位设备上返回 `false`，`thunder://` / magnet 解析自动降级。

## 代码风格与约定

### 遵循现有模式

- **新 UI 用 Kotlin + Jetpack Compose for TV**；业务层（`api` / `crawler` / `cache` / `server`）本阶段保持 Java
- 宿主：`MainActivity` + Navigation-Compose；播放/直播为独立 Activity（Compose 外壳 + 遗留播放表面）
- Screen 只消费 `UiState`；Hawk 经 `SettingsRepository`；EventBus 经 `EventBusBridge`，Compose 内不要直接 `@Subscribe`
- 遗留 XML 页仍可：`BaseActivity` / `BaseLazyFragment` / `BaseDialog` / BRVAH / LoadSir
- 网络请求：**OkGo**（`OkGoHelper` 初始化）或 **OkHttp**
- JSON 解析：**Gson**
- 图片（Compose）：**Coil**；遗留 Picasso 随 XML 退场
- 新建设置项：在 `HawkConfig` 添加常量，经 `SettingsRepository` / `Hawk` 读写
- Room 注解处理用 **KSP**

### 命名与结构

- 保持现有包划分，不要把业务逻辑堆进 `util`
- Bean 放 `bean/`，DAO 放 `cache/`，Compose 功能页放 `ui/feature/<name>/`
- 单例使用双重检查锁定，与 `ApiConfig`、`ControlManager` 一致

### UI 注意

- 所有 Activity 强制横屏（`AndroidManifest.xml`）
- Compose TV：`androidx.tv:tv-material` + Compose Foundation Lazy；焦点组件复用 `TvFocusButton` / `TvPosterCard`
- Compose 树不以 AutoSize 为准；设计宽 1280dp 横屏

## 架构要点

### 配置加载流程

1. 用户配置 API 地址（`HawkConfig.API_URL`）
2. `ApiConfig.load()` 拉取 JSON，解析 `sites`、`parses`、`lives` 等；重载时 `SpiderManager.reset()`
3. 若配置含 `spider` JAR，经 `SpiderManager.loadJar` → `JarLoader`（`DexClassLoader`）装载
4. 各页面通过 `ApiConfig.getCSP()` 取爬虫：`.js` 走 `JsLoader`，`csp_*` 走 `JarLoader`

### Spider 双轨

- 门面：`SpiderManager`（`ApiConfig` 委托），宿主 API 仍为 `com.github.catvod.crawler.Spider`
- JAR：配置级 `spider` + 站点 `api` 形如 `csp_Xxx`
- JS：站点 `api` 为 `.js` / `.js?`，可选站点级 `jar`（jsapi）；运行时为 `JsSpider` + `:quickjs`
- 失败：返回带 `reason` 的 `SpiderNull`，见 `SpiderFailReason`

### 播放栈

`player` 模块提供 `TvPlayerView`（薄壳）与 Media3 / 系统 `MediaPlayer` 后端；默认 Media3（`PLAY_TYPE=2`），可选系统播放器（`0`）。`app` 中 `PlayerHelper` 装配后端。修改播放相关代码时同时检查 `ui.play.PlayActivity`、`ui.live.LivePlayActivity`（及其 Legacy）、`com.hotplato.tvbox.tvplayer`、控制器包。

### 本地服务

`ControlManager` 启动 `RemoteServer`（NanoHTTPD），用于推送、搜索等局域网交互。改动端口或路由时检查 `InputRequestProcess` 与对应 Receiver。

## 提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)，**type 使用英文，描述使用中文**。

```
<type>(<scope>): <中文描述>

[可选正文，中文]
```

### 常用 type

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档变更 |
| `refactor` | 重构（不改变行为） |
| `style` | 代码格式（不影响逻辑） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建、依赖、工具链等 |
| `ci` | CI 配置 |

`scope` 可选，填模块名即可，如 `app`、`player`、`api`。

### 示例

```
docs: 添加 AGENTS.md 项目代理说明文档

为 AI 编码代理提供项目架构、构建命令、代码约定与边界约束。
```

```
fix(player): 修复 ExoPlayer 切换音轨后崩溃的问题
```

```
feat(app): 支持配置多个首页推荐源
```

## 边界与禁止事项

- **不要**擅自降级或升级 AGP / Gradle / `compileSdk`，除非用户明确要求（当前因 Compose for TV 已升至 AGP 8.7.2 / `compileSdk` 35 / `minSdk` 23）
- **不要**修改 `app/libs/thunder.jar` 或随意替换 `player/jniLibs` 下已验证的 `.so`，除非有明确的兼容性测试
- **不要**提交 `local.properties`、`.gradle/`、`build/`、`*.apk`
- **不要**在主线程执行 `JarLoader.load()`、网络 IO、数据库批量操作
- **不要**移除 `multiDex` 相关配置（方法数已接近上限）
- **不要**默认添加单元测试框架或大规模重构；保持最小改动范围
- **不要**未经请求创建 git commit 或 push
- 谨慎修改 `network_security_config.xml` 与 `usesCleartextTraffic`（影响所有 HTTP 源）

## 常见任务指引

### 新增设置项

1. `HawkConfig` 添加 key 常量
2. `ModelSettingFragment` 或对应设置 UI 增加交互
3. 读取处用 `Hawk.get(key, defaultValue)`

### 新增视频源类型 / 解析逻辑

1. 从 `ApiConfig` 的配置解析入手
2. 若涉及爬虫接口，对照 `Spider` 抽象类与 `SpiderManager` → `JarLoader` / `JsLoader` 调用链
3. 播放地址获取失败时检查 `ParseBean` 与 `vipParseFlags`

### 新增页面

1. 创建 Activity 继承 `BaseActivity`
2. 在 `AndroidManifest.xml` 注册，设置 `screenOrientation="landscape"`，含 intent-filter 的组件需声明 `android:exported`
3. 布局放 `res/layout/`，字符串放 `res/values/strings.xml`

## 依赖摘要

| 库 | 用途 |
|---|---|
| AndroidX (AppCompat, Room, RecyclerView) | 基础 UI 与数据库 |
| OkGo / OkHttp | HTTP |
| Gson | JSON |
| Hawk | KV 存储 |
| EventBus | 事件总线 |
| Picasso | 图片加载 |
| BRVAH | RecyclerView Adapter |
| LoadSir | 加载/空状态 |
| AutoSize | 屏幕适配 |
| XWalk | WebView 解析（本地 AAR） |
| NanoHTTPD | 本地 HTTP 服务 |
| Media3（ExoPlayer） | 播放（`player` 模块 `TvPlayerView`） |

## 相关文件速查

| 文件 | 说明 |
|---|---|
| `app/src/main/java/com/hotplato/tvbox/api/ApiConfig.java` | 配置与源管理核心 |
| `app/src/main/java/com/hotplato/tvbox/base/App.java` | Application 初始化 |
| `app/src/main/java/com/hotplato/tvbox/util/HawkConfig.java` | 设置键名 |
| `app/src/main/java/com/hotplato/tvbox/crawler/SpiderManager.java` | JAR/JS 爬虫统一门面 |
| `app/src/main/java/com/hotplato/tvbox/crawler/JarLoader.java` | 动态爬虫 JAR 加载 |
| `app/src/main/java/com/hotplato/tvbox/crawler/JsLoader.java` | JS 爬虫加载（QuickJS） |
| `app/src/main/AndroidManifest.xml` | 组件注册与权限 |
| `app/build.gradle` | 应用依赖与构建配置 |
| `player/build.gradle` | 播放器模块依赖 |
| `settings.gradle` | 插件版本与仓库管理 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 版本 |
