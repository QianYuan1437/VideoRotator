# VideoRotator

> Rotate landscape videos to portrait by 90° — metadata-only, no cropping, no re-encoding.
> 将横屏视频旋转 90° 转为竖屏 —— 纯元数据旋转，无裁切、无重编码。

[中文 Readme](./README.md) · [Features & Dev Docs](https://qianyuan1437.github.io/VideoRotator/) · [Releases](https://github.com/QianYuan1437/VideoRotator/releases)

## Features

- **Landscape → Portrait rotation**: video orientation is changed via `MediaExtractor + MediaMuxer + setOrientationHint` — no re-encoding, lossless quality, completes in seconds.
- **Bilibili-style player**: playback speed (0.5x–2.0x), left-edge swipe for brightness, right-edge swipe for volume, vertical swipe for seeking, orientation lock, fullscreen, double-tap play/pause, auto-hiding controls.
- **Conversion config**: rotation direction (CW/CCW 90°), selectable save path (app-private / Movies / DCIM / Download / custom), keep-original switches.
- **Theme customization**: 8 theme colors (two rows of swatches — Light Purple / Sakura Pink / Sky Blue / Mint Green / Vibrant Orange / Rose Red / Lemon Yellow / Graphite Gray), background mode (Light / Dark / Follow system), settings persisted.
- **Unified rounded design**: cards & buttons 20dp, selection boxes 16dp, chips 12dp; bottom navigation bar with 28dp bottom corners adapted to rounded screen edges.

## Tech Stack

| Module | Choice |
|--------|--------|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose + Material 3 |
| Player | Media3 ExoPlayer 1.2.1 |
| Rotation | MediaExtractor + MediaMuxer (metadata) |
| Thumbnails | Coil |
| Build | Gradle 7.6.3 / AGP 7.4.2 / JDK 17 |

## Build Locally

```bash
# Requires JDK 17 + Android SDK (sdk.dir in local.properties)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## CI/CD (GitHub Actions)

- **Build & Release**: every push to `main` builds an APK and updates the `latest` pre-release; pushing a `v*` tag creates a formal Release with the APK attached.
- **Deploy Docs**: changes to `docs/` deploy the bilingual documentation site to GitHub Pages.

## License

[MIT](./LICENSE)
