# Spider 双轨统一门面设计

**日期**：2026-07-14  
**状态**：已确认

## 目标

兼容优先：保留 JAR（`csp_*`）与 JS（`.js`）双轨；新增 `SpiderManager` 统一托管分流、生命周期与失败可观测。不改 `com.github.catvod.crawler.Spider` 宿主 API，不做彻底 JS 迁移。

## 架构

```
配置 JSON → ApiConfig → SpiderManager
                           ├─ .js api → JsLoader → JsSpider
                           └─ csp_*  → JarLoader → JAR DexClassLoader Spider
UI（SourceViewModel / PlayActivity）仍只调用 ApiConfig.getCSP()
```

## 分流规则

| 条件 | 路径 |
|---|---|
| `api` 以 `.js` 结尾或含 `.js?` | JS（可附带站点级 `jar` jsapi） |
| `JarLoader.isJarSpiderApi(api)` | JAR |
| 其他 | `SpiderNull`（`API_UNSUPPORTED`） |

## 生命周期

- 配置重载（`parseJson`）：`SpiderManager.reset()` — 清空 JS 缓存与 JAR spider 实例，并重置失败日志去重。
- JAR 本体：仍由 `loadJar` / `JarLoader.load` 下载并装载。
- 进程退出：保留 `JsLoader.destroy()`（`App` 侧）。

## 失败原因

| 常量 | 含义 |
|---|---|
| `JAR_NOT_LOADED` | DexClassLoader 尚未就绪 |
| `JAR_CLASS_INVALID` | 类加载 / 实例化失败 |
| `JS_LOAD_FAILED` | JsSpider 初始化失败 |
| `API_UNSUPPORTED` | api 既不是 JAR 也不是 JS |

`SpiderNull` 携带 `reason` / `detail`；方法仍返回空串，不破坏外部契约。每个 `sourceKey` 首次落入 `SpiderNull` 时打一条结构化日志。

## 非目标

- 弃用 JAR / 强制迁 Spider.js
- 重写 QuickJS / `JsSpider` 运行时
- 修改远端爬虫 JAR 内部逻辑

## 验证

- `./gradlew assembleDebug`
- 各测一条 `csp_*` 与一条 `.js` 源
- 故意错误 api → 日志含 reason，无 ClassNotFound 刷屏
