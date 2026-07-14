# Spider 宿主侧性能优化 Implementation Plan

> **For agentic workers:** Use subagent-driven-development or executing-plans. Steps use checkbox syntax.

**Goal:** 宿主侧 Coroutine 调度、连接池、JAR MD5 旁路、结果缓存、播放直出、JS 有限多实例。

**Architecture:** `SpiderGateway` → `Dispatcher` + `ResultCache` + `InstancePool` → 现有 `SpiderManager`；OkHttp 3.12 调池；不改宿主 Spider API。

**Tech Stack:** Kotlin Coroutines、OkHttp 3.12、Gson、`org.json`

**Spec:** `docs/superpowers/specs/2026-07-14-spider-perf-design.md`

---

见 Cursor plan：Spider Coroutine Opt（P0→P1→P2→验证）。

## 验证

- [x] `./gradlew assembleDebug` 通过（2026-07-14）
- [ ] 设备：`csp_*` / `.js` 首页、列表、跨源搜索、详情、直链/解析起播
- [ ] 配置重载后结果不串；JAR 二次启动日志含 `jar md5 sidecar hit`
