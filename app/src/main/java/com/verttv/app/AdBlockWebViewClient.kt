package com.verttv.app

import android.net.Uri
import android.webkit.*
import java.io.ByteArrayInputStream

open class AdBlockWebViewClient(private val filterAds: Boolean = true) : WebViewClient() {
    private val blocked = setOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com", "analytics.google.com", "ads.youtube.com", "adservice.google.com", "adnxs.com", "adsrvr.org", "scorecardresearch.com", "taboola.com", "outbrain.com")
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.scheme != "https"
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host?.lowercase() ?: return null
        if (request.url.scheme != "https" || (filterAds && blocked.any { host == it || host.endsWith(".$it") }))
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        return null
    }
    override fun onPageFinished(view: WebView, url: String) {
        if(filterAds) view.evaluateJavascript("""(function(){if(document.getElementById('vert-ad-filter'))return;const s=document.createElement('style');s.id='vert-ad-filter';s.textContent='.ytp-ad-overlay-container,.video-ads,.ad-container,[data-ad-slot],ins.adsbygoogle{display:none!important}';document.head.appendChild(s)})()""", null)
    }
}
