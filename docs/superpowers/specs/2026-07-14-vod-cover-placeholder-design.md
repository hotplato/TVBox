# Compose 封面 Loading / 默认图

**日期**：2026-07-14  
**状态**：已确认

## 目标

Compose 封面在加载中、加载失败、URL 为空时不再留白，统一显示现有默认占位图，避免海报区域空白。

## 已确认决策

| 项 | 选择 |
|---|---|
| 视觉风格 | A：静态占位图（与旧版 Picasso 一致） |
| 实现方式 | 共享 `VodCoverImage` 组件 |
| 占位资源 | 复用 `R.drawable.img_loading_placeholder` |
| 加载中 / 失败 / 无图 | 同一 drawable |

## 行为

| 状态 | 表现 |
|---|---|
| URL 有效，加载中 | 显示 `img_loading_placeholder` |
| 加载失败 / 解码失败 | 显示 `img_loading_placeholder` |
| `pic` 为空、空白串，或 `vodImageModel` 为 null | 直接显示 `img_loading_placeholder`，不发起网络请求 |
| 加载成功 | 显示真实封面（`ContentScale.Crop`） |

## 组件设计

新建 [`app/src/main/java/com/hotplato/tvbox/ui/component/VodCoverImage.kt`](../../../app/src/main/java/com/hotplato/tvbox/ui/component/VodCoverImage.kt)：

```kotlin
@Composable
fun VodCoverImage(
    pic: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
)
```

- `model = vodImageModel(pic)`，经 Coil `ImageRequest` 加载
- `placeholder` / `error` 用 `ImageRequest` 的 drawable res（`img_loading_placeholder`）；**不用** `painterResource`（该资源为 layer-list，Compose 不支持）
- model 为 null 时 `data` 直接设为同一 drawable res，保证区域始终有图

## 接入点

| 文件 | 改动 |
|---|---|
| `TvPosterCard.kt` | 内部 `AsyncImage` → `VodCoverImage` |
| `DetailScreen.kt` | 详情大封面 `AsyncImage` → `VodCoverImage` |

经 `TvPosterCard` 自动覆盖：首页、搜索、历史、收藏。

## 不做

- 不改 Picasso / 遗留 XML 路径（已有占位）
- 不新增转圈、shimmer 骨架
- 不新增 drawable 资源
- 不改 `vodImageModel` / `TvImageFetcher` 网络逻辑

## 验证

1. 首页 / 搜索 / 历史 / 收藏：列表滚动时封面先显示占位图，成功后替换为真实图
2. 人为错误 URL 或无封面数据：显示占位图，无空白
3. 详情页大封面同样不出现空白
4. `./gradlew assembleDebug` 编译通过
