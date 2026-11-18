# VideoRotator 视频旋转助手

> 将横屏视频旋转 90° 转为竖屏 —— 纯元数据旋转、无裁切、无重编码、秒级完成。
> Rotate landscape videos to portrait — metadata-only, no cropping, no re-encoding.

[English Readme](./README.en.md) · [功能与开发文档 / Docs](https://qianyuan1437.github.io/VideoRotator/) · [Release 下载](https://github.com/QianYuan1437/VideoRotator/releases)

## 功能特性

- **横屏转竖屏**：通过 `MediaExtractor + MediaMuxer + setOrientationHint` 修改视频方向元数据，不重新编码，画质无损、速度极快。
- **Bilibili 风格播放器**：倍速（0.5x–2.0x）、左侧滑动调亮度、右侧滑动调音量、上下滑动拖动进度、视角锁定、全屏切换、双击播放/暂停、控制栏自动隐藏。
- **视频列表**：MediaStore 一次查询完成扫描；已转换的原视频 / 转换产物在卡片上带标识，筛选栏支持「全部 / 未转换 / 已转换」、去重显示与「源文件 / 转换产物」筛选；原视频与转换产物准确对应（之前版本转换的产物按文件名与时长自动识别），「源文件」筛选下可一键删除全部已转换完毕的原视频释放空间，多选时可同时转换 / 删除源文件；卡片标注竖屏 / 横屏，列表带「回到顶部」「定位上次浏览」悬浮按钮。
- **转换列表**：任务在后台继续执行（2 路并行，前台服务保活、锁屏不中断，通知栏显示进度），可取消，进程退出后记录不丢失；任务可按「进行中 / 已完成 / 失败或取消」筛选并批量重试、删除；「已转视频」汇总所有保存位置的产物（含之前版本转换的），可按「源文件仍在 / 源文件已删除」筛选，显示每个产物的源文件，支持多选批量删除源文件或产物、播放、反向旋转、排序；各确认弹窗以要点列出删除对象、可释放空间与注意事项。
- **转换配置**：旋转方向（顺/逆时针 90°）、默认保存位置（应用私有目录 / Movies / DCIM / Download / 自定义路径），设置持久化并同时用于单个与批量转换；原视频始终保留。
- **主题定制**：8 套主题色（两行色块点选，浅紫 / 樱花粉 / 天空蓝 / 薄荷绿 / 活力橙 / 玫瑰红 / 柠檬黄 / 石墨灰），背景模式（浅色 / 深色 / 跟随系统），设置持久化保存。
- **多语言**：界面支持中文 / English / 跟随系统，切换即时生效（Android 13+ 同时出现在系统「应用语言」设置中）。
- **统一圆角设计**：卡片与按钮 20dp、选择框 16dp、标签 12dp；底部导航栏底部 28dp 圆角并适配圆角屏幕底边。

## 技术架构

| 模块 | 技术选型 |
|------|----------|
| 语言 | Kotlin 1.9.22 |
| UI | Jetpack Compose + Material 3 |
| 播放器 | Media3 ExoPlayer 1.2.1 |
| 视频旋转 | MediaExtractor + MediaMuxer（元数据旋转） |
| 图片加载 | Coil |
| 构建 | Gradle 7.6.3 / AGP 7.4.2 / JDK 17 |

```
app/src/main/java/com/videorotator/
├── MainActivity.kt              # 三 Tab 导航骨架（视频列表 / 转换配置 / 设置）
├── ui/
│   ├── components/              # VideoPlayer、ThemeSelector 等复用组件
│   ├── screens/                 # 各功能页面
│   └── theme/                   # 主题色 / 背景模式
├── viewmodel/                   # FileBrowser / Convert / Player 状态管理
└── utils/                       # 视频扫描、旋转转换、权限
```

## 本地构建

```powershell
# 需要 JDK 17 + Android SDK（local.properties 中配置 sdk.dir）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 自动化（GitHub Actions）

- **Build & Release**：推送 `main` 分支自动构建 APK 并更新 `latest` 预发布；推送 `v*` 标签自动创建正式 Release 并附带安装包。
- **Deploy Docs**：`docs/` 目录变更自动部署中英文说明网页到 GitHub Pages。

## License

[MIT](./LICENSE)
