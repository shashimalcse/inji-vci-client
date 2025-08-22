package com.example.vciclient.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
@SuppressLint("SetJavaScriptEnabled")
fun AuthWebViewScreen(
    authUrl: String,
    onAuthCodeReceived: (String) -> Unit,
    onClose: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                WebView.setWebContentsDebuggingEnabled(true)

                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith("io.mosip.residentapp.inji://oauthredirect")) {
                            val code = request.url.getQueryParameter("code")
                            code?.let { onAuthCodeReceived(it) }
                            onClose()
                            return true
                        }
                        return false // Allow all internal redirects
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // Optional: Log for debug
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Optional: Log for debug
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        // Optional: Log for error tracking
                    }
                }

                loadUrl(authUrl)
            }
        },
        update = { webView = it },
        modifier = Modifier
    )
}
