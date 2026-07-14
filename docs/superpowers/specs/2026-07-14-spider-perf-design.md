# Spider 宿主侧性能优化设计

**日期**：2026-07-14  
**状态**：已确认

## 目标

提升整体吞吐与起播延迟：Kotlin Coroutine 调度、HTTP 连接池、JAR MD5 旁路、Spider 结果缓存、播放地址直出、JS 有限多实例。兼容现有双轨门面，不改 `com.github.catvod.crawler.Spider`，不升 OkHttp 4.x。

## 架构

```
UI / SourceViewModel
      → SpiderGateway（suspend / Java 回调桥）
          → SpiderResultCache（LRU + TTL）
          → SpiderDispatcher（全局 Semaphore + per-key Mutex）
          → SpiderInstancePool（JAR 单实例 / JS 最多 2）
              → SpiderManager → JarLoader / JsLoader
PlayUrlFastPath：规范化 playerContent JSON，可直链则 parse=0
OkGoHelper：ConnectionPool + Dispatcher（仍 OkHttp 3.12）
JarMd5Index：csp.jar.md5 / jsapi 旁路，跳过全文件 MD5
```

## 调度约定

| 项 | 值 |
|---|---|
| 全局并发 | `Semaphore(8)` |
| 同源 | 每 `sourceKey` 一把 `Mutex`，串行 |
| JAR 实例 | 每 key 单实例（复用 JarLoader 缓存） |
| JS 实例 | 同 key 最多 2，借还；多余 `destroy` |
| 超时 | 单次 Spider 调用 `withTimeout(15s)` |

## 结果缓存

| 操作 | TTL | Key 组成 |
|---|---|---|
| home / category | 60s | sourceKey + op + tid + pg + filter |
| search | 30s | sourceKey + wd + quick |
| detail | 120s | sourceKey + id |
| play | 90s | sourceKey + flag + url |

空串与 `SpiderNull` 结果不缓存。`SpiderManager.reset()` 清空缓存与池。

## 播放直出

1. play 缓存命中 → 直接回放 JSON  
2. `parse!=1 && jx!=1`，或 url path 匹配 `.m3u8|.mp4|.flv|.mkv|.webm|.ts` → 规范 `parse=0`，跳过 `initParse`  
3. 否则原解析路径

## Jar MD5 旁路

- 配置级：`filesDir/csp.jar` + `csp.jar.md5`  
- 旁路内容与配置 `;md5;` 一致 → 跳过 `MD5.getFileMd5`，直接 load  
- 下载成功后写入旁路；站点 jsapi 同步 `{key}.jar.md5`

## JSON

进程内 `GsonHolder` 单例；播放小 JSON 仍用 `org.json`。

## 非目标

- 不改远端 CSP JAR / QuickJS 内核  
- 不为 JAR 多实例并行  
- 不引入 Fastjson / kotlinx.serialization  
- 不升 OkHttp 4.x（见 `docs/TODO.md`）

## 验证

- `./gradlew assembleDebug`  
- `csp_*` 与 `.js` 各测：首页、列表、跨源搜索、详情、直链/需解析起播  
- 配置重载后缓存不串；JAR 二次启动旁路命中可观测  
