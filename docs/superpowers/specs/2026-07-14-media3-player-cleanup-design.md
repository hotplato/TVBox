# Media3 为主 + 去掉 DK 壳

## 目标

- 去掉 DK `VideoView` / IJK / MX / Reex
- Media3 为默认内置内核；保留系统 MediaPlayer（`PLAY_TYPE=0`）
- 点播/直播仍走 Legacy Activity + 叠加控制器

## 架构

- `TvPlayerView`：统一壳（`com.hotplato.tvbox.tvplayer`）
- `Media3Backend` / `SystemMediaPlayerBackend`
- `PlayerHelper`：`0`→系统，`2`→Media3；旧值 `1/10/11` 启动迁移为 `2`
- HTTP：Media3 `DefaultHttpDataSource`（不升 OkHttp 4.x，见 `docs/TODO.md`）

## 非目标

- 不整页 Compose 化播放页
- 不升级全局 OkHttp
- 保留迅雷 so/jar
