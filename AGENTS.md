# AGENTS.md

面向 AI 编码代理的项目说明。本文档描述 TVBox 的架构、约定与常用命令，帮助代理在仓库中安全、一致地修改代码。

## 项目概览

TVBox 是一款面向 Android TV / 机顶盒的影视聚合客户端（横屏 `landscape`）。应用通过远程 JSON 配置加载视频源，并支持动态 JAR 爬虫（CatVod CSP）、直播、点播播放与本地 Web 控制服务。

| 项 | 值 |
|---|---|
| 语言 | Java 17 |
| 构建 | Gradle 8.9 + Android Gradle Plugin 8.5.2 |
| `compileSdk` / `targetSdk` | 34 / 34 |
| `minSdk` | 21 |
| 包名 | `com.hotplato.tvbox` |
| ABI | `armeabi-v7a` + `arm64-v8a` |

## 模块结构

```
TVBox/
├── app/          # 主应用：UI、配置、爬虫加载、本地服务、缓存
└── player/       # 播放器库：IJK / ExoPlayer / DKPlayer 封装
```

### `app` 核心包

| 包路径 | 职责 |
|---|---|
| `com.hotplato.tvbox.base` | `App` 入口、`BaseActivity`、`BaseLazyFragment` |
| `com.hotplato.tvbox.api` | `ApiConfig`：远程配置解析、源/解析/直播管理 |
| `com.hotplato.tvbox.crawler` | `JarLoader`、`Spider`：动态 JAR 爬虫加载 |
| `com.hotplato.tvbox.ui` | Activity、Fragment、Dialog、Adapter |
| `com.hotplato.tvbox.player` | 播放控制器、渲染、第三方播放器 |
| `com.hotplato.tvbox.server` | `ControlManager`、`RemoteServer`：局域网 Web 控制 |
| `com.hotplato.tvbox.cache` | Room 数据库与 DAO |
| `com.hotplato.tvbox.bean` | 数据模型（`SourceBean`、`VodInfo` 等） |
| `com.hotplato.tvbox.util` | 工具类；`HawkConfig` 为配置键常量 |
| `com.hotplato.tvbox.event` | EventBus 事件定义 |

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

**环境要求**：Android SDK（`compileSdk 34`）、JDK 17、Android NDK（编译 IJK 原生库时）。`local.properties` 需配置 `sdk.dir`，该文件已 gitignore，勿提交。

**验证清单**（修改后尽量执行）：

1. `./gradlew assembleDebug` 编译通过
2. 若改动 UI：确认横屏布局在 1280×720 设计稿下正常（`AutoSize` + `design_width_in_dp=1280`）
3. 若改动 `ApiConfig` / 爬虫：确认不在主线程调用 `JarLoader.load()`
4. 若改动播放逻辑：分别验证 IJK（`PLAY_TYPE=1`）与 Exo（`PLAY_TYPE=2`）路径
5. 若改动 native 库：确认 APK 内包含 `lib/arm64-v8a/libplayer.so` 与 `lib/armeabi-v7a/libplayer.so`

项目当前**无单元测试**；CI 仅做 release 构建（`.github/workflows/test.yml`，`workflow_dispatch` 触发）。

### 原生库与本地依赖

| 路径 | 说明 |
|---|---|
| `player/src/main/jniLibs/armeabi-v7a/` | IJK `libplayer.so`、迅雷 `libxl_*.so`（32 位） |
| `player/src/main/jniLibs/arm64-v8a/` | IJK `libplayer.so`（64 位） |
| `app/libs/thunder.jar` | 迅雷 Java SDK |
| `app/libs/xwalk_shared_library-23.53.589.4.aar` | Crosswalk WebView（原 Maven 仓库已下线，本地引用） |

**IJK 重编译**：从 [bilibili/ijkplayer](https://github.com/bilibili/ijkplayer) 编译 armv7a / arm64，产物重命名为 `libplayer.so` 放入对应 `jniLibs` 目录。

**迅雷 64 位**：`libxl_thunder_sdk.so` / `libxl_stat.so` 暂无 arm64 版；`Thunder.isSupported()` 在 64 位设备上返回 `false`，`thunder://` / magnet 解析自动降级。

## 代码风格与约定

### 遵循现有模式

- **Java only**，不使用 Kotlin
- Activity 继承 `BaseActivity`，Fragment 继承 `BaseLazyFragment`
- Dialog 继承 `BaseDialog`
- RecyclerView Adapter 使用 `BaseQuickAdapter`（BRVAH）
- 页面状态用 LoadSir（`EmptyCallback`、`LoadingCallback`）
- 跨组件通信优先 **EventBus**（`com.hotplato.tvbox.event`）
- 网络请求：**OkGo**（`OkGoHelper` 初始化）或 **OkHttp**
- JSON 解析：**Gson**
- 新建设置项：在 `HawkConfig` 添加常量，通过 `Hawk` 读写

### 命名与结构

- 保持现有包划分，不要把业务逻辑堆进 `util`
- Bean 放 `bean/`，DAO 放 `cache/`，UI 放 `ui/` 对应子包
- 单例使用双重检查锁定，与 `ApiConfig`、`ControlManager` 一致

### UI 注意

- 所有 Activity 强制横屏（`AndroidManifest.xml`）
- TV 遥控器焦点导航：修改列表/按钮时保留焦点态 drawable（如 `*_focus.xml`）
- 尺寸适配走 `me.jessyan.autosize`，不要硬编码 dp 替代已有适配方案

## 架构要点

### 配置加载流程

1. 用户配置 API 地址（`HawkConfig.API_URL`）
2. `ApiConfig.load()` 拉取 JSON，解析 `sites`、`parses`、`lives` 等
3. 若配置含 `spider` JAR，通过 `JarLoader` 用 `DexClassLoader` 动态加载
4. 各页面通过 `ApiConfig.get()` 获取当前源与解析规则

### 播放栈

`player` 模块封装 IJK、ExoPlayer、DKPlayer。`app` 中 `PlayerHelper` 根据 `HawkConfig.PLAY_TYPE` 选择播放器。修改播放相关代码时同时检查 `PlayActivity`、`LivePlayActivity` 与 `player/` 模块。

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

- **不要**擅自降级或升级 AGP / Gradle / `compileSdk`，除非用户明确要求
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
2. 若涉及爬虫接口，对照 `Spider` 抽象类与 `JarLoader` 调用链
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
| ExoPlayer / DKPlayer / IJK | 播放（`player` 模块） |

## 相关文件速查

| 文件 | 说明 |
|---|---|
| `app/src/main/java/com/hotplato/tvbox/api/ApiConfig.java` | 配置与源管理核心 |
| `app/src/main/java/com/hotplato/tvbox/base/App.java` | Application 初始化 |
| `app/src/main/java/com/hotplato/tvbox/util/HawkConfig.java` | 设置键名 |
| `app/src/main/java/com/hotplato/tvbox/crawler/JarLoader.java` | 动态爬虫 JAR 加载 |
| `app/src/main/AndroidManifest.xml` | 组件注册与权限 |
| `app/build.gradle` | 应用依赖与构建配置 |
| `player/build.gradle` | 播放器模块依赖 |
| `settings.gradle` | 插件版本与仓库管理 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 版本 |
