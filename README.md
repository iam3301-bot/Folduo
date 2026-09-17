# 折叠流光 · Folduo 中文玻璃版

面向 **三星 Galaxy Z Fold8 Ultra（SM-F9760）／One UI 9.0** 的 Folduo 分支。默认全中文，以 Android 原生界面呈现液态玻璃的通透、高光和悬浮层次。

**开发中，暂缓交付。** 中文界面已在 SM-F9760／Android 17／One UI 9.0 安装运行，双屏保持、应用跨屏移动与画面捕获已通过组件检查。保留现有壁纸时仍缺少连续精细角度，真实开合动画尚未通过验证。此前预览包已转为发布草稿，不作为可用成品。

[安装与构建说明](docs/安装指南.md) · [验证记录](docs/验证记录.md) · [原项目](https://github.com/bunkaich/Folduo)

## 本版本的变化

- 设置、桌面、预览、通知、恢复提示及错误信息均已中文化。首次启动默认简体中文；仍可手动选择英语或日语。
- 主界面分为“流光／外观／设置”，统一使用玻璃面板和悬浮导航；支持玻璃浓度调节及减少按压动态效果。
- 玻璃材质通过原生 RuntimeShader 实现，对同一程序生成背景进行边缘位移采样和柔化，再叠加可调浓度与高光。不是调用 iOS 的私有组件。
- 新增 SM-F9760 型号入口；启用时继续检查三星并行屏幕状态。独立包名、独立签名，保留原 MIT 许可与第三方声明。
- 兼容独立包名及 One UI 9.0 的壁纸角度日志格式，提供只读设备检查工具。
- 修复 One UI 9.0 状态栏初始化时序问题，以及内屏悬浮导航未跟随应用语言的问题。

## 界面预览

下图为 Android 17 模拟器原生界面截图；分辨率按 SM-F9760 内外屏设置，不能代替三星真实开合测试。

<img src="docs/images/cover.png" width="260" alt="中文流光主界面"> <img src="docs/images/appearance.png" width="260" alt="玻璃浓度与动态效果设置">

## 兼容性范围

| 项目 | 范围 |
| --- | --- |
| 安装系统 | Android 13 起；编译 SDK 37，target SDK 36 |
| 目标机型 | Galaxy Z Fold8 Ultra / SM-F9760 / One UI 9.0 |
| 原项目的已测机型 | Fold7 SM-F966Z / Android 16 / One UI 8.5 |
| 折叠动画依赖 | Shizuku、悬浮显示、三星双屏并行状态，以及持续的精细角度来源 |
| 真机验证情况 | 以[验证记录](docs/验证记录.md)为准，不将模拟器通过当作三星双屏验证 |

本应用保持两块屏幕同时点亮，并在开合时显示临时静止画面。画面只保存在内存中，不保存、不上传。桌面和效果预览可以在没有 Shizuku 的情况下使用。

## 构建

准备 JDK 17 或兼容版本与 Android SDK：

```sh
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
python3 tools/check-chinese.py
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。正式签名与 release 构建方式见[安装指南](docs/安装指南.md#源码与构建)。

## 来源与许可

原项目：[bunkaich/Folduo](https://github.com/bunkaich/Folduo)。原始代码采用 [MIT](LICENSE)，依赖声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。保留[上游英文说明](README.en.md)和[上游日文说明](README.ja.md)以便对照原版行为。

设计参考 [Apple iOS 27](https://www.apple.com/os/ios/) 的 Liquid Glass 材质与可读性。界面素材由代码绘制，不分发苹果或三星的专有图标、壁纸或视频。
