package com.example.superttslauncher

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.superttslauncher.ui.theme.SuperTTSLauncherTheme

internal const val TARGET_URL = "https://yutajapan2026-tts-test.hf.space/"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuperTTSLauncherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WebViewScreen(
                        url = TARGET_URL,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }
}

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    modifier: Modifier = Modifier
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                CookieManager.getInstance().flush()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            CookieManager.getInstance().flush()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.let {
            if (it.canGoBack()) {
                it.goBack()
                canGoBack = it.canGoBack()
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                val context = context
                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                    try {
                        if (url.startsWith("data:")) {
                            // Data URI (e.g. base64 audio) handling via Share Intent
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = mimetype.ifEmpty { "audio/*" }
                                putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(url))
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "音声ファイルを共有"))
                        } else {
                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                setMimeType(mimetype)
                                val cookies = CookieManager.getInstance().getCookie(url)
                                addRequestHeader("cookie", cookies)
                                addRequestHeader("User-Agent", userAgent)
                                setDescription("音声ファイルをダウンロード中...")
                                setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setAllowedOverMetered(true)
                                setAllowedOverRoaming(true)
                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                            }
                            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            dm.enqueue(request)
                            android.widget.Toast.makeText(context, "ダウンロードを開始しました", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback: Open in browser/external intent
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            android.widget.Toast.makeText(context, "ダウンロードまたは共有に失敗しました", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        canGoBack = view?.canGoBack() == true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        canGoBack = view?.canGoBack() == true
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    allowFileAccess = true
                    allowContentAccess = true
                }
                loadUrl(url)
                webView = this
            }
        },
        update = {
            webView = it
        },
        modifier = modifier
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun WebViewScreenPreview() {
    SuperTTSLauncherTheme {
        Scaffold { innerPadding ->
            WebViewScreen(
                url = TARGET_URL,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}



