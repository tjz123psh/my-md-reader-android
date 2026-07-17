# Keep JavaScript bridge methods
-keepclassmembers class com.pang.mdreader.ui.component.MarkdownWebView {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView JavaScript interface
-keepclassmembers class com.pang.mdreader.ui.component.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
