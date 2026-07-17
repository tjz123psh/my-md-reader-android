# MD Reader

一个极简的本地 Markdown 阅读器 Android 应用，专注于移动端阅读体验。

## 截图

| 浏览文件 | 阅读 | 目录大纲 |
|---------|------|---------|
| | | |

## 特点

- **纯本地** — 不需要网络，完全离线使用
- **SAF 文件浏览** — 通过系统文件选择器打开工作区，浏览 Markdown 文件
- **三种阅读主题**
  - 🌅 暖色亮 — 纸张质感，适合白天
  - 🌙 暖色暗 — 护眼暗色模式
  - 💻 GitHub — 简洁浅色风格
- **目录大纲** — 自动提取标题，快速跳转
- **捏合缩放** — 支持字体大小调整
- **文档内搜索** — 在正文中查找关键词
- **代码高亮** — 基于 highlight.js 的语法高亮
- **Obsidian 兼容** — 支持 `![[image]]` 嵌入图片语法
- **跟随系统主题** — 可自动切换明暗模式

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **渲染**: WebView + markdown-it + highlight.js
- **架构**: MVVM (ViewModel + StateFlow)
- **最低支持**: Android 14 (API 34)

## 下载

从 [GitHub Releases](https://github.com/tjz123psh/my-md-reader-android/releases) 下载最新 APK 并安装。

## 使用说明

1. 安装 APK 后打开应用
2. 点击 **"打开文件夹"**，选择一个包含 Markdown 文件的目录
3. 点击任意 `.md` 文件开始阅读
4. 在阅读界面可切换主题、调整字体、查看目录和搜索

> ⚠️ 目前使用 Debug 签名，安装时可能需要确认"未知来源应用"。

## 开发

```bash
# 克隆
git clone git@github.com:tjz123psh/my-md-reader-android.git

# 构建 debug APK
./gradlew assembleDebug

# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

需要 Android SDK 34+ 和 JDK 17+。

## License

MIT
