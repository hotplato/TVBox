# TODO

跟踪已知问题与待办。完成后把条目移到「已完成」，并附提交/PR。

## 进行中 / 待办

### OkHttp 升级到 4.x

- **背景**：工程强制 `okhttp:3.12.13` + `okio:2.8.0`（OkGo / DoH / Coil `TvImageFetcher` 均依赖），与 Media3 `media3-datasource-okhttp` 及 Coil 默认网络栈不兼容。
- **范围**：去掉 `force` 升级到 OkHttp 4.x；适配 `OkGoHelper`、DoH、图片加载；评估是否改用 Media3 OkHttp DataSource。
- **说明**：不并入「Media3 + 去 DK 壳」播放器改造；播放期仍用 Media3 `DefaultHttpDataSource`。

---

## 已完成

### 启动慢：JAR 主线程装载

- **根因**：`SpiderManager.loadJar` 缓存命中或 OkGo `onSuccess` 时在主线程执行 `JarLoader.load`（DexClassLoader + Init / sleep）。
- **修复**：`jarLoadExecutor` 后台执行 MD5 校验与 `JarLoader.load`；`success`/`error` 经主线程 Handler 回传，上层流水线不变。
- **提交**：`af72f5f`（2026-07-14）

### Compose 封面无法加载

- **根因**：① Coil 未走豆瓣 Referer / `@Headers`；② 工程强制 OkHttp 3.12 + Okio 2.8，Coil 2.7 默认网络栈不可用。
- **修复**：`ImageHttpHeaders` 共用逻辑 + `TvImageFetcher`（OkHttp3）+ `vodImageModel`。
- **提交**：`07559c7`（2026-07-14）
