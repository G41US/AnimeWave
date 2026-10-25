package com.animewave.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * Full-screen in-app source player. This embeds the selected provider page without browser
 * address bars; playback is still handled by the provider's own page/player.
 */
class EmbeddedPlayerActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.statusBarColor = Color.rgb(10, 8, 20)
        window.navigationBarColor = Color.rgb(10, 8, 20)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 8, 20))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 14, 20, 14)
            setBackgroundColor(Color.rgb(23, 18, 39))
        }
        val back = TextView(this).apply {
            text = "‹   BACK"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 8, 20, 8)
            isFocusable = true
            setOnClickListener { finish() }
        }
        status = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "ANIMEWAVE PLAYER" }
            textSize = 15f
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        top.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        top.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        webView = WebView(this)
        webView.setBackgroundColor(Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString = userAgentString
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                // Keep normal web navigation in the embedded screen, but block non-web schemes
                // from launching arbitrary external apps.
                return scheme != "https" && scheme != "http"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                status.text = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "ANIMEWAVE PLAYER" }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                root.removeView(webView)
                root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
                top.visibility = View.GONE
            }

            override fun onHideCustomView() {
                hideFullscreenView(root, top)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
            finish()
            return
        }
        webView.loadUrl(url)
        back.requestFocus()
    }

    private fun hideFullscreenView(root: LinearLayout, top: View) {
        val view = customView ?: return
        root.removeView(view)
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        top.visibility = View.VISIBLE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    override fun onBackPressed() {
        if (customView != null) {
            val root = webView.parent as? LinearLayout
            val top = root?.getChildAt(0)
            if (root != null && top != null) hideFullscreenView(root, top)
        } else if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "com.animewave.app.EXTRA_URL"
        const val EXTRA_TITLE = "com.animewave.app.EXTRA_TITLE"
    }
}
