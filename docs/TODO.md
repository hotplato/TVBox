# TODO

跟踪已知问题与待办。完成后把条目移到「已完成」，并附提交/PR。

## 进行中 / 待办

### 启动慢：JAR 主线程装载

- **现象**：冷启动约十余秒才出内容；模拟器上尤其明显。日志里 `SpiderManager.onSuccess` → `JarLoader.load` / Spider `Init` 落在主线程（`Handler` 回调）。
- **根因假设**：`loadConfig` → `loadJar` 串行，且 `DexClassLoader` 装载在主线程，造成卡顿与「加载很慢」体感。
- **方向**：`JarLoader.load` / `Init` 挪到后台线程；成功后再切回主线程继续 `getSort` / UI。注意勿在主线程做网络 / JAR IO（见 `AGENTS.md`）。
- **状态**：待办
- **发现**：2026-07-14（Compose 封面排查时确认）

---

## 已完成

### Compose 封面无法加载

- **根因**：① Coil 未走豆瓣 Referer / `@Headers`；② 工程强制 OkHttp 3.12 + Okio 2.8，Coil 2.7 默认网络栈不可用。
- **修复**：`ImageHttpHeaders` 共用逻辑 + `TvImageFetcher`（OkHttp3）+ `vodImageModel`。
- **提交**：`07559c7`（2026-07-14）
