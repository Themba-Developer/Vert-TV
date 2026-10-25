package com.verttv.app

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Native, filtered viewer for the shared real catalogue; no Chrome or Brave installation required. */
class LibraryActivity : ViewerActivity() {
    private var payment=false
    private val base get()=BuildConfig.API_BASE_URL.trimEnd('/')
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if(!isWebReady())return
        val api=Api(this)
        val token=api.prefs.getString("token",null) ?: return finish()
        web.webViewClient=object: AdBlockWebViewClient(true){
            override val filteringEnabled: Boolean get()=!payment
            override fun shouldOverrideUrlLoading(view: WebView,request: android.webkit.WebResourceRequest): Boolean {
                val uri=request.url
                if(request.isForMainFrame&&uri.scheme=="https"&&uri.host==android.net.Uri.parse(base).host&&uri.path in listOf("/app","/account","/login","/signup")&&uri.getQueryParameter("native")!="1"){
                    view.loadUrl(uri.buildUpon().appendQueryParameter("native","1").build().toString());return true
                }
                if(request.isForMainFrame&&uri.scheme=="https"&&uri.host==android.net.Uri.parse(base).host&&uri.path=="/native/settings"){
                    syncSession();startActivity(android.content.Intent(this@LibraryActivity,SettingsActivity::class.java));return true
                }
                return super.shouldOverrideUrlLoading(view,request)
            }
            override fun onPageStarted(view: WebView,url: String,favicon: android.graphics.Bitmap?){
                val uri=android.net.Uri.parse(url)
                val host=uri.host.orEmpty()
                val entering=host=="payfast.co.za"||host.endsWith(".payfast.co.za")||host=="payfast.io"||host.endsWith(".payfast.io")||(host==android.net.Uri.parse(base).host&&uri.path=="/checkout")
                if(entering&&!payment){payment=true;AdBlockEngine.beginPayment()}
                if(payment&&host==android.net.Uri.parse(base).host&&uri.path in listOf("/app","/account","/login","/signup")){payment=false;AdBlockEngine.endPayment()}
                super.onPageStarted(view,url,favicon)
            }
        }
        CookieManager.getInstance().setCookie(base,"__Host-vert_session=$token; Path=/; Secure; HttpOnly; SameSite=Lax"){
            lifecycleScope.launch {
                AdBlockEngine.awaitReady(this@LibraryActivity)
                if(!isFinishing&&!isDestroyed)web.loadUrl("$base/app?native=1")
            }
        }
    }
    private fun syncSession(){
        if(!isWebReady())return
        val cookie=CookieManager.getInstance().getCookie(base).orEmpty().split(';').map { it.trim() }.firstOrNull { it.startsWith("__Host-vert_session=") }?.substringAfter('=')
        val edit=Api(this).prefs.edit()
        if(cookie.isNullOrBlank())edit.remove("token") else edit.putString("token",cookie)
        edit.apply();CookieManager.getInstance().flush()
    }
    override fun onPause(){syncSession();super.onPause()}
    override fun onDestroy(){if(payment){payment=false;AdBlockEngine.endPayment()};super.onDestroy()}
}
