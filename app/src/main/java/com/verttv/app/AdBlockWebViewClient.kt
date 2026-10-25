package com.verttv.app

import android.graphics.Bitmap
import android.webkit.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

open class AdBlockWebViewClient(private val filterAds: Boolean = true) : WebViewClient() {
    protected open val filteringEnabled: Boolean get()=filterAds
    @Volatile private var topURL="https://dulo.mov/"
    override fun onPageStarted(view: WebView,url: String,favicon: Bitmap?){topURL=url;if(filteringEnabled)AdBlockEngine.contentURL=url;super.onPageStarted(view,url,favicon)}
    override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean = request.url.scheme!="https" || (filteringEnabled&&AdBlockEngine.shouldBlock(request,topURL))
    override fun shouldInterceptRequest(view: WebView,request: WebResourceRequest): WebResourceResponse? {
        if(request.url.scheme!="https")return AdBlockEngine.emptyResponse()
        return if(filteringEnabled&&AdBlockEngine.shouldBlock(request,topURL))AdBlockEngine.emptyResponse() else null
    }
    override fun onPageCommitVisible(view: WebView,url: String){if(filteringEnabled)injectCosmetics(view,url)}
    override fun onPageFinished(view: WebView,url: String){
        if(!filteringEnabled)return
        injectCosmetics(view,url)
        for(delay in listOf(1500L,5000L))view.postDelayed({if(view.url==url)injectCosmetics(view,url)},delay)
    }
    private fun injectCosmetics(view: WebView,url: String){
        if(view.url!=url||!url.startsWith("https://"))return
        view.evaluateJavascript("""(()=>{const classes=new Set(),ids=new Set();for(const e of Array.from(document.querySelectorAll('[class],[id]')).slice(0,2000)){if(e.id&&ids.size<256)ids.add(e.id);for(const c of e.classList){if(classes.size<512)classes.add(c)}}return JSON.stringify({classes:[...classes],ids:[...ids]})})()"""){raw->
            try {
                if(view.url!=url)return@evaluateJavascript
                val text=JSONTokener(raw).nextValue() as? String ?: return@evaluateJavascript
                val data=JSONObject(text)
                val selectors=AdBlockEngine.selectors(url,data.optJSONArray("classes")?.toString() ?: "[]",data.optJSONArray("ids")?.toString() ?: "[]")
                val parsed=JSONArray(selectors);val css=(0 until parsed.length()).joinToString("\n"){parsed.getString(it)+"{display:none!important}"}
                view.evaluateJavascript("(()=>{let s=document.getElementById('vert-ad-filter');if(!s){s=document.createElement('style');s.id='vert-ad-filter';(document.head||document.documentElement).appendChild(s)}s.textContent="+JSONObject.quote(css)+"})()",null)
            }catch(_: Exception){ /* Keep network filtering if a page rejects cosmetic filtering. */ }
        }
    }
}
