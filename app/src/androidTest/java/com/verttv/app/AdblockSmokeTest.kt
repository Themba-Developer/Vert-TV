package com.verttv.app

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdblockSmokeTest {
    @Test fun nativeEngineLoadsAndFiltersWithoutASeparateBrowser()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val tvIntent=android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.packageName)
        assertNotNull("App must be visible to the TV home launcher",context.packageManager.resolveActivity(tvIntent,0))
        val cookieReady=java.util.concurrent.CountDownLatch(1)
        var cookie=""
        instrumentation.runOnMainSync {
            android.webkit.CookieManager.getInstance().setCookie("https://vert-cookie-test.invalid","__Host-vert_session=fixture; Secure; HttpOnly; Path=/; SameSite=Lax"){
                cookie=android.webkit.CookieManager.getInstance().getCookie("https://vert-cookie-test.invalid").orEmpty();cookieReady.countDown()
            }
        }
        assertTrue("Native login cookie must be accepted",cookieReady.await(10,java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("Native session synchronization must retain HttpOnly cookies",cookie.contains("__Host-vert_session=fixture"))
        assertTrue("Native ABI library must load",NativeAdblock.available)
        assertTrue("Bundled lists must initialize offline",AdBlockEngine.awaitReady(context))
        assertTrue(NativeAdblock.shouldBlock("https://googleads.g.doubleclick.net/pagead/id","https://dulo.mov","xmlhttprequest","get"))
        assertFalse(NativeAdblock.shouldBlock("https://dulo.mov/movies/","https://dulo.mov","document","get"))
        val request=object: WebResourceRequest {
            override fun getUrl()=Uri.parse("https://googleads.g.doubleclick.net/pagead/id")
            override fun isForMainFrame()=false
            override fun isRedirect()=false
            override fun hasGesture()=false
            override fun getMethod()="GET"
            override fun getRequestHeaders()=mapOf("Accept" to "application/json","Referer" to "https://dulo.mov/")
        }
        lateinit var web: WebView
        instrumentation.runOnMainSync { web=WebView(context) }
        try {
            assertNotNull("Content viewer intercepts ad requests",AdBlockWebViewClient().shouldInterceptRequest(web,request))
            assertNull("Payment viewer does not filter requests",AdBlockWebViewClient(false).shouldInterceptRequest(web,request))
            AdBlockEngine.beginPayment()
            try { assertNull("Global payment scope also bypasses filtering",AdBlockWebViewClient().shouldInterceptRequest(web,request)) } finally { AdBlockEngine.endPayment() }
        } finally { instrumentation.runOnMainSync { web.destroy() } }
    }
}
