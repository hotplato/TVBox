# Compose 封面占位 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose 封面在加载中、失败、无图时显示 `img_loading_placeholder`，避免空白。

**Architecture:** 新增共享 `VodCoverImage`，`TvPosterCard` 与 `DetailScreen` 改用它；复用现有 drawable 与 `vodImageModel`。

**Tech Stack:** Kotlin Compose、Coil 2 `AsyncImage`、`painterResource`

**Spec:** `docs/superpowers/specs/2026-07-14-vod-cover-placeholder-design.md`

---

### Task 1: 新增 VodCoverImage

**Files:**
- Create: `app/src/main/java/com/hotplato/tvbox/ui/component/VodCoverImage.kt`

- [x] **Step 1: 实现组件**

```kotlin
@Composable
fun VodCoverImage(
    pic: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = painterResource(R.drawable.img_loading_placeholder)
    val model = vodImageModel(pic)
    if (model == null) {
        Image(
            painter = placeholder,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = placeholder,
            error = placeholder,
        )
    }
}
```

- [x] **Step 2: 接入 TvPosterCard / DetailScreen**

将两处 `AsyncImage` + `vodImageModel` 替换为 `VodCoverImage(pic = ...)`。

- [x] **Step 3: 编译验证**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL
