package com.verttv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity

open class ViewerActivity : FragmentActivity() {
    protected lateinit var web: WebView
    private var custom: android.view.View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        web = try { WebView(this) }catch(_: Exception){
            setContentView(android.widget.TextView(this).apply { text="Android System WebView is unavailable. Enable or update it in your TV settings, then reopen Vert TV.";textSize=24f;setTextColor(android.graphics.Color.WHITE);setPadding(40,40,40,40) });return
        }
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(this@ViewerActivity !is PayfastActivity)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = AdBlockWebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView,isDialog: Boolean,isUserGesture: Boolean,resultMsg: android.os.Message): Boolean = false
            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                if(custom != null) { callback.onCustomViewHidden(); return }
                custom = view; customCallback = callback
                addContentView(view, FrameLayout.LayoutParams(-1,-1)); web.visibility = android.view.View.GONE
            }
            override fun onHideCustomView() { hideFullscreen() }
        }
        intent.getStringExtra("url")?.takeIf { android.net.Uri.parse(it).scheme == "https" }?.let { url -> lifecycleScope.launch { AdBlockEngine.awaitReady(this@ViewerActivity);if(!isFinishing&&!isDestroyed)web.loadUrl(url) } }
        web.requestFocus()
    }
    protected fun isWebReady()=::web.isInitialized
    private fun hideFullscreen() {
        custom?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        custom = null; customCallback?.onCustomViewHidden(); customCallback = null
        web.visibility = android.view.View.VISIBLE
    }
    @Deprecated("Android TV back navigation")
    override fun onBackPressed() { if(custom != null) hideFullscreen() else if(isWebReady()&&web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onPause() { if(isWebReady())web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); if(isWebReady())web.onResume() }
    override fun onDestroy() { if(isWebReady()){web.stopLoading();web.destroy()};super.onDestroy() }
}
