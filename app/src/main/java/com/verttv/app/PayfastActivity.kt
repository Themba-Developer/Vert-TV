package com.verttv.app

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PayfastActivity : ViewerActivity() {
    override fun onDestroy(){AdBlockEngine.endPayment();super.onDestroy()}
    override fun onCreate(state: Bundle?) {
        AdBlockEngine.beginPayment()
        super.onCreate(state)
        if(!isWebReady())return
        val api = Api(this)
        web.webViewClient = object : AdBlockWebViewClient(false) {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.scheme != "https"
            }
        }
        val checkout = intent.getStringExtra("checkout") ?: return finish()
        val uri = android.net.Uri.parse(checkout)
        val base = android.net.Uri.parse(BuildConfig.API_BASE_URL)
        if(uri.scheme != "https" || uri.host != base.host) return finish()
        web.loadUrl(checkout)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var attempts = 0
                while(attempts++ < 240) {
                    try { if(api.request("/api/activation/status").optBoolean("active")) { finish(); break } }
                    catch(e: ApiException) { if(e.status == 401) { finish(); break } }
                    catch(_: Exception) { /* Retry temporary network failures. */ }
                    delay(5000)
                }
            }
        }
    }
}
