package com.verttv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

open class ViewerActivity : FragmentActivity() {
    protected lateinit var web: WebView
    private var custom: android.view.View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = AdBlockWebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                if(custom != null) { callback.onCustomViewHidden(); return }
                custom = view; customCallback = callback
                addContentView(view, FrameLayout.LayoutParams(-1,-1)); web.visibility = android.view.View.GONE
            }
            override fun onHideCustomView() { hideFullscreen() }
        }
        intent.getStringExtra("url")?.takeIf { android.net.Uri.parse(it).scheme == "https" }?.let { web.loadUrl(it) }
        web.requestFocus()
    }
    private fun hideFullscreen() {
        custom?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        custom = null; customCallback?.onCustomViewHidden(); customCallback = null
        web.visibility = android.view.View.VISIBLE
    }
    @Deprecated("Android TV back navigation")
    override fun onBackPressed() { if(custom != null) hideFullscreen() else if(web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { web.stopLoading(); web.destroy(); super.onDestroy() }
}
