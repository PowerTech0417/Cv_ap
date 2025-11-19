# Links App - WebView 应用

这是一个将网站打包成 Android APK 的 WebView 应用。

## 功能特性

- 📱 将网站转换为原生 Android 应用
- 🔄 自动处理页面导航
- 📊 显示加载进度条
- 🔙 支持返回键导航
- 🚀 自动构建和发布

## 自动构建

此项目使用 GitHub Actions 自动构建 APK：
- 每次推送到 main 分支时自动构建
- 生成 Debug 和 Release 版本的 APK
- 自动创建 GitHub Release

## 本地构建

```bash
./gradlew assembleDebug
./gradlew assembleRelease
