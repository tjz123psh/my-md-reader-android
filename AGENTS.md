# MD Reader — AI 交接文档

## 项目概况

极简本地 Markdown 阅读器 Android App。Kotlin + Jetpack Compose + Material 3，WebView + markdown-it 渲染。

- GitHub: https://github.com/tjz123psh/my-md-reader-android
- 下载: Releases 页面有 debug + release APK
- CI: 手动构建上传（无自动流水线）

## 当前版本: v1.3.4

Build: v8, Tag: `v1.3.4`
之前是一个国内程序员写的，两个用户，这是传给第二个AI做的。我是第三个AI。

## 验证通过的修复

1. **子目录排序** — `FileTree.kt` 的 `buildVisibleList` 传入 `files.sortedBy { it.relativePath }`
2. **大纲面板空白** — `MarkdownView.kt` 的 `pollHeadings` 中 `evaluateJavascript` 调用 `JSON.stringify(mdReader.getHeadings())` 会双编码，应直接用 `mdReader.getHeadings()`（Android WebView 自动 JSON 编码返回值）
3. **WebView 不刷新** — C1: `LaunchedEffect(htmlContent)` 延迟等待 WebView 就绪后重新 `loadDataWithBaseURL`
4. **WebView 内存泄漏** — C2: `onRelease = { it.destroy() }`
5. **APK 下载流泄漏** — C3: `HttpURLConnection` 等改用 `.use {}`
6. **更新下载为 release 版** — 之前下载 debug APK（签名不匹配无法覆盖安装）
7. **SAF 子目录查询** — 用 `getDocumentId` + `rootTreeUri` 代替 `getTreeDocumentId`
8. **更新检查双通道** — GitHub API 受阻时 fallback 到 `releases/latest` 重定向
9. **标题注入** — `bridge.js` 中用正则后处理为 headings 注入 id（`heading_open` renderer rule 不可靠）

## 核心文件索引

| 文件 | 职责 |
|------|------|
| `data/FileRepository.kt` | SAF 文件读写、目录查询、图片 URI 解析 |
| `viewmodel/ReaderViewModel.kt` | 阅读器状态、文件加载、搜索 |
| `viewmodel/BrowserViewModel.kt` | 文件浏览器状态、目录展开/折叠 |
| `ui/component/MarkdownView.kt` | WebView 封装、JS bridge、标题轮询、shouldOverrideUrlLoading |
| `ui/component/FileTree.kt` | 文件树展开/折叠/排序 |
| `ui/component/OutlinePanel.kt` | 大纲面板 |
| `ui/screen/ReaderScreen.kt` | 阅读器页面组合 |
| `ui/screen/BrowserScreen.kt` | 文件浏览页面 |
| `assets/reader/bridge.js` | JS bridge — markdown 渲染、标题提取、缩放、选择跟踪 |
| `assets/reader/reader.css` | WebView 主题样式 |
| `assets/reader/markdown-it.min.js` | markdown-it 14.3.0 |
| `data/UpdateChecker.kt` | 版本检查（GitHub API + 重定向） |

## 架构关键

### SAF（Storage Access Framework）
- 用户通过系统文件夹选择器选择根目录 → 获得 `treeUri`
- 所有文件操作基于 `treeUri` 构建 document URIs
- 子目录查询需要原始 `treeUri` 作为 base URI
- 越权访问 `content://` URI 会抛出 `SecurityException`

### WebView 渲染
- `buildReaderHtml()` 将所有 CSS/JS 内联到单个 HTML 字符串
- `loadDataWithBaseURL("file:///android_asset/reader/", ...)` 加载
- `bridge.js` 在页面加载时自动渲染 `MD_READER_CONTENT`（base64 编码）
- JS ↔ Kotlin 通信通过 `@JavascriptInterface`（`JsBridge` 类）和 `evaluateJavascript`

### 标题提取 / 大纲
- `bridge.js` 中 `renderMarkdown()` 后处理 headings 注入 id
- `MarkdownView.kt` 轮询 `mdReader.getHeadings()`（最多 25×200ms）
- `evaluateJavascript` 返回值：
  - 返回 JS 数组时 → Android 自动 JSON 编码，回调得 `"[{...}]"`，`JSONArray()` 可解析
  - 返回 `JSON.stringify(array)` 时 → Android 再编码一遍成 `"\"[{...}]\""`，`JSONArray()` 失败
  - 正确写法：**不**用 `JSON.stringify()`，let `evaluateJavascript` 自动编码

## 已解决问题：Obsidian 图片不显示

**原现象**：`![[Pasted image.png]]` 在阅读器中不显示图片内容，只显示 alt 文字或不显示。

**v1.3.4 最终策略**（已真机验证）：

1. 在 markdown 文本中匹配 `![...](...)` 和 `![[...]]` 两种语法
2. 解码 URL 路径，并先尝试笔记相对路径和 vault 根相对路径
3. 未命中时，从 SAF tree 根目录按文件提供器返回的真实 document ID 广度优先搜索，支持任意名称和任意层级的自定义附件目录
4. 找到后将 Markdown 改写为带 tree 权限的 `content://` URI
5. `MarkdownView.shouldInterceptRequest` 通过 ContentResolver 流式返回图片，并按扩展名补偿错误的 MIME 类型
6. 最近文件入口使用其自身保存的 workspace URI；旧版本写坏的 workspace URI 会自动恢复为规范 tree URI

真机验证日志标签：`MDReader-IMG`。正常加载会出现 `tree match`（需要树搜索时）和 `serve`。

## 构建与发布

```bash
# 本地构建
export ANDROID_HOME="$HOME/android-sdk"
./gradlew assembleDebug assembleRelease -x lintVitalAnalyzeRelease

# 发布
gh release create v1.3.4 --title "v1.3.4" --notes "..." \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release.apk
```

- Release 必须删除重建（`gh release delete` + `git tag -d` + `git push origin --delete`）
- 版本在 `app/build.gradle.kts`（`versionCode` + `versionName`）和 `UpdateChecker.kt`（`CURRENT_VERSION`）两处维护
- 验证更新：`curl https://api.github.com/repos/tjz123psh/my-md-reader-android/releases/latest`

## 工具链

- Gradle 9.4.1（在 `/tmp/gradle-9.4.1/`）
- Android SDK 在 `~/android-sdk/`
- Release keystore: `release.keystore`（项目根目录）
- gh CLI 在 `/tmp/gh_2.96.0_linux_amd64/bin/gh`
- JDK 17+

## 发布记录

| 版本 | 代码 | 说明 |
|------|------|------|
| v1.3.0 | 6 | 初始发布（第一个 AI 做的） |
| v1.3.1 | 7 | 微调（跳过，未发布） |
| v1.3.2 | 7 | 更新检查 + 小修复（跳过） |
| v1.3.3 | 7 | 子目录排序、大纲面板、图片修复、SAF 子目录查询、WebView 生命周期 |
| v1.3.4 | 8 | 真机验证的 Obsidian 图片修复、全 SAF 树附件搜索、工作区 URI 修复 |
