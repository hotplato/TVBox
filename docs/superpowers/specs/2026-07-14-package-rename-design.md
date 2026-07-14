# 包名迁移设计：com.hotplato.tvbox

**日期**：2026-07-14  
**状态**：已确认

## 目标

将应用包名与自有 Java 包统一为 `com.hotplato.tvbox`。

## 映射

| 原包名 | 新包名 |
|---|---|
| `com.github.tvbox.osc` | `com.hotplato.tvbox` |
| `com.github.catvod.crawler` | `com.hotplato.tvbox.crawler` |
| `com.github.tvbox.osc.player`（player namespace） | `com.hotplato.tvbox.player` |

第三方包（IJK / DKPlayer / 迅雷等）不改动。

## 约束

`JarLoader` 中反射加载外部爬虫 JAR 的字符串必须保留：

- `com.github.catvod.spider.*`
- `com.github.catvod.parser.*`

## 副作用

新 `applicationId` 与旧包视为不同应用，无法覆盖安装。

## 验证

`./gradlew assembleDebug`
