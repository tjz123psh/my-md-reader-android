package com.pang.mdreader.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pang.mdreader.model.OutlineItem
import com.pang.mdreader.model.ReaderTheme
import org.json.JSONArray
import org.json.JSONObject

/**
 * Callback interface for communication from WebView JS to Compose.
 */
class JsBridge(
    val onDocumentLoaded: () -> Unit = {},
    val onSelectionChanged: (text: String, startLine: Int, endLine: Int, headingId: String, headingTitle: String) -> Unit = { _, _, _, _, _ -> },
    val onActiveHeadingChanged: (headingId: String) -> Unit = {},
    val onZoomChanged: (percent: Int) -> Unit = {},
) {
    @JavascriptInterface
    fun onDocumentLoaded(json: String) {
        onDocumentLoaded()
    }

    @JavascriptInterface
    fun onSelectionChanged(json: String) {
        if (json.isBlank()) {
            onSelectionChanged("", 0, 0, "", "")
            return
        }
        try {
            val obj = JSONObject(json)
            val heading = obj.optJSONObject("heading")
            onSelectionChanged.invoke(
                obj.optString("text", ""),
                obj.optInt("startLine", 0),
                obj.optInt("endLine", 0),
                heading?.optString("id", "") ?: "",
                heading?.optString("title", "") ?: "",
            )
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onActiveHeadingChanged(json: String) {
        try {
            val obj = JSONObject(json)
            onActiveHeadingChanged(obj.optString("id", ""))
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onZoomChanged(json: String) {
        try {
            val obj = JSONObject(json)
            onZoomChanged(obj.optInt("percent", 100))
        } catch (_: Exception) {}
    }
}

/**
 * Compose wrapper around WebView for rendering Markdown.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownView(
    markdownContent: String,
    theme: ReaderTheme = ReaderTheme.WARM_LIGHT,
    zoom: Int = 100,
    activeHeadingId: String? = null,
    onDocumentLoaded: () -> Unit = {},
    onSelectionChanged: (text: String, startLine: Int, endLine: Int, headingId: String, headingTitle: String) -> Unit = { _, _, _, _, _ -> },
    onActiveHeadingChanged: (headingId: String) -> Unit = {},
    onZoomChanged: (percent: Int) -> Unit = {},
    onHeadingsReady: (List<OutlineItem>) -> Unit = {},
    onScrollToHeading: String? = null,
    onScrollToLine: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bridge = remember {
        JsBridge(
            onDocumentLoaded = onDocumentLoaded,
            onSelectionChanged = onSelectionChanged,
            onActiveHeadingChanged = onActiveHeadingChanged,
            onZoomChanged = onZoomChanged,
        )
    }

    // Track if content was already set
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Build the HTML
    val htmlContent = remember(markdownContent, theme) {
        buildReaderHtml(context, markdownContent, theme.id)
    }

    AndroidView(
        modifier = modifier,
        onRelease = {
            // C2: Destroy WebView when composable leaves the tree
            it.destroy()
        },
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    textZoom = 100
                    cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                }

                addJavascriptInterface(bridge, "MdReaderAndroid")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Set initial zoom after page load
                        view?.evaluateJavascript(
                            "mdReader.setZoom($zoom);",
                            null
                        )
                        // Trigger heading extraction after page loads
                        view?.evaluateJavascript(
                            """mdReader.getHeadings();""",
                            android.webkit.ValueCallback { result ->
                                android.util.Log.d("MDReader-JS", "onPageFinished headings: ${result?.take(200)}")
                                // Try parsing on first load
                                if (result != null && result != "null" && result.length > 2) {
                                    try {
                                        val arr = JSONArray(result)
                                        val items = mutableListOf<OutlineItem>()
                                        for (i in 0 until arr.length()) {
                                            val obj = arr.getJSONObject(i)
                                            items.add(
                                                OutlineItem(
                                                    level = obj.optInt("level", 1),
                                                    title = obj.optString("title", ""),
                                                    id = obj.optString("id", ""),
                                                )
                                            )
                                        }
                                        if (items.isNotEmpty()) {
                                            onHeadingsReady(items)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MDReader-JS", "onPageFinished JSON error: ${e.message}")
                                    }
                                }
                            }
                        )
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        // Handle external links
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                            ctx.startActivity(intent)
                            return true
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): android.webkit.WebResourceResponse? {
                        val url = request?.url ?: return null
                        // Intercept content:// URIs (images resolved by resolveImageUris)
                        // Android 10+ WebView doesn't auto-load content:// URIs, so we
                        // bridge them via the app's content resolver (which has SAF tree permission).
                        if (url.scheme == "content") {
                            try {
                                val inputStream = ctx.contentResolver.openInputStream(url)
                                if (inputStream != null) {
                                    val mimeType = ctx.contentResolver.getType(url) ?: "image/*"
                                    android.util.Log.d("MDReader-IMG", "serve: $url ($mimeType)")
                                    return android.webkit.WebResourceResponse(mimeType, null, inputStream)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MDReader-IMG", "shouldInterceptRequest: ${e.message} | url=$url")
                            }
                        }
                        return null
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("MDReader-JS", "${msg?.message()} (${msg?.sourceId()}:${msg?.lineNumber()})")
                        return true
                    }
                }

                webView = this
            }
        },
        update = { view ->
            webView = view
        },
    )

    // C1: Reload WebView when content or theme changes (triggers on first load too)
    LaunchedEffect(htmlContent) {
        // Wait for WebView to be available (may not be ready on first composition)
        var attempts = 0
        while (webView == null && attempts < 20) {
            delay(50)
            attempts++
        }
        webView?.loadDataWithBaseURL(
            "file:///android_asset/reader/",
            htmlContent,
            "text/html",
            "utf-8",
            null
        )
    }

    // React to zoom changes from outside
    DisposableEffect(zoom) {
        webView?.evaluateJavascript("mdReader.setZoom($zoom);", null)
        onDispose {}
    }

    // React to theme changes — inject JS to switch CSS data-color-scheme immediately
    DisposableEffect(theme) {
        webView?.evaluateJavascript(
            "document.body.setAttribute('data-color-scheme', '${theme.id}');",
            null
        )
        onDispose {}
    }

    // React to heading scroll requests
    DisposableEffect(onScrollToHeading) {
        if (onScrollToHeading != null) {
            webView?.evaluateJavascript(
                "mdReader.scrollToHeading('${onScrollToHeading.replace("'", "\\'")}');",
                null
            )
        }
        onDispose {}
    }

    // React to line scroll requests
    DisposableEffect(onScrollToLine) {
        if (onScrollToLine != null) {
            webView?.evaluateJavascript(
                "mdReader.scrollToSource($onScrollToLine);",
                null
            )
        }
        onDispose {}
    }

    // Extract headings after load — poll until found or 5s timeout
    DisposableEffect(Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var pollingActive = true
        var attempts = 0
        val maxAttempts = 25  // 25 * 200ms = 5s total

        fun pollHeadings() {
            attempts++
            // NOTE: evaluateJavascript already JSON-encodes native arrays/objects.
            // Do NOT use JSON.stringify() — that would double-encode (string in a string)
            // and break JSONArray parsing. Just return the native array.
            webView?.evaluateJavascript(
                "mdReader.getHeadings();",
            ) { result ->
                if (!pollingActive) return@evaluateJavascript
                android.util.Log.d("MDReader-JS", "pollHeadings attempt=$attempts result.length=${result?.length} result=${result?.take(200)}")
                if (result != null && result != "null" && result.length > 2) {
                    try {
                        val arr = JSONArray(result)
                        val items = mutableListOf<OutlineItem>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            items.add(
                                OutlineItem(
                                    level = obj.optInt("level", 1),
                                    title = obj.optString("title", ""),
                                    id = obj.optString("id", ""),
                                )
                            )
                        }
                        if (items.isNotEmpty()) {
                            android.util.Log.d("MDReader-JS", "Heading extraction succeeded: ${items.size} headings")
                            onHeadingsReady(items)
                            pollingActive = false
                            handler.removeCallbacksAndMessages(null)
                            return@evaluateJavascript
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MDReader-JS", "Heading JSON parse error: ${e.message} | result=${result?.take(200)}")
                    }
                }
                // Retry while still active and under max attempts
                if (pollingActive && attempts < maxAttempts) {
                    handler.postDelayed({ pollHeadings() }, 200)
                }
            }
        }
        handler.postDelayed({ pollHeadings() }, 500)
        onDispose { pollingActive = false; handler.removeCallbacksAndMessages(null) }
    }
}

/**
 * Build the complete reader HTML with embedded CSS and JS.
 */
private fun buildReaderHtml(context: Context, markdownContent: String, themeId: String): String {
    val assetManager = context.assets
    val css = assetManager.open("reader/reader.css").bufferedReader().readText()
    val markdownItJs = assetManager.open("reader/markdown-it.min.js").bufferedReader().readText()
    val highlightJs = assetManager.open("reader/highlight.min.js").bufferedReader().readText()
    val bridgeJs = assetManager.open("reader/bridge.js").bufferedReader().readText()

    val encodedContent = Base64.encodeToString(
        markdownContent.toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP
    )

    return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src data: file: content:;">
  <style>$css</style>
</head>
<body data-color-scheme="$themeId">
  <main id="content"></main>
  <script>$markdownItJs</script>
  <script>$highlightJs</script>
  <script>
    try {
      var MD_READER_CONTENT = decodeURIComponent(escape(atob("$encodedContent")));
    } catch(e) {
      var MD_READER_CONTENT = "";
    }
  </script>
  <script>$bridgeJs</script>
</body>
</html>
""".trimIndent()
}
