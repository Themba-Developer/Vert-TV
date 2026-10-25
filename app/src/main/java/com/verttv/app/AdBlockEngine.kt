package com.verttv.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object AdBlockEngine {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val started=AtomicBoolean(false)
    private val initialized=CompletableDeferred<Boolean>()
    private val updates=Mutex()
    private val payments=AtomicInteger(0)
    private val blocked=AtomicLong(0)
    private val lists=listOf("easylist","easyprivacy")
    private const val MAX_LIST=5*1024*1024
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(java.time.Duration.ofSeconds(25)).build()
    @Volatile var ready=false;private set
    @Volatile var contentURL="https://dulo.mov/"
    private val fallback=setOf("doubleclick.net","googlesyndication.com","googleadservices.com","google-analytics.com","analytics.google.com","ads.youtube.com","adservice.google.com","adnxs.com","adsrvr.org","scorecardresearch.com","taboola.com","outbrain.com")
    fun initialize(context: Context) {
        if(!started.compareAndSet(false,true))return
        val app=context.applicationContext
        Handler(Looper.getMainLooper()).post {
            try { ServiceWorkerController.getInstance().setServiceWorkerClient(object: ServiceWorkerClient(){
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    if(payments.get()>0)return null
                    return if(shouldBlock(request,contentURL))emptyResponse() else null
                }
            }) }catch(_: Exception){ /* Some TV images have no usable WebView provider. */ }
        }
        scope.launch {
            try {
                if(NativeAdblock.available)ready=NativeAdblock.initialize(readRules(app))
            }catch(_: Exception){ ready=false }
            finally { initialized.complete(ready) }
            if(ready)refresh(app,false)
        }
    }
    suspend fun awaitReady(context: Context): Boolean { initialize(context);return initialized.await() }
    fun beginPayment(){payments.incrementAndGet()}
    fun endPayment(){payments.updateAndGet { maxOf(0,it-1) }}
    private fun cache(context: Context,name: String)=AtomicFile(File(File(context.noBackupFilesDir,"adblock").apply { mkdirs() },"$name.txt"))
    private fun valid(text: String)=text.startsWith("[Adblock Plus")&&text.length<=MAX_LIST&&text.lineSequence().take(1001).count()>1000
    private fun readRules(context: Context): String {
        val sources=lists.map { name ->
            val saved=try { cache(context,name).readFully().toString(Charsets.UTF_8).takeIf { valid(it) } }catch(_: Exception){null}
            saved ?: context.assets.open("adblock/$name.txt").bufferedReader().use { it.readText() }
        }
        return (sources+context.assets.open("adblock/vert-rules.txt").bufferedReader().use { it.readText() }).joinToString("\n")
    }
    suspend fun refresh(context: Context,force: Boolean=true): Boolean=withContext(Dispatchers.IO){ updates.withLock {
        if(!ready)return@withLock false
        val prefs=context.getSharedPreferences("adblock",Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        if(!force&&now-prefs.getLong("lastAttempt",0)<24*3600000L)return@withLock false
        prefs.edit().putLong("lastAttempt",now).apply()
        try {
            val texts=lists.map { name ->
                client.newCall(Request.Builder().url("https://easylist.to/easylist/$name.txt").build()).execute().use { response ->
                    check(response.isSuccessful){"Filter update failed"}
                    val body=response.body ?: error("No filter body")
                    val bytes=body.byteStream().use { input ->
                        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val count=input.read(buffer);if(count<0)break;check(output.size()+count<=MAX_LIST){"Filter list too large"};output.write(buffer,0,count)}
                        output.toByteArray()
                    }
                    check(bytes.size<=MAX_LIST){"Filter list too large"}
                    bytes.toString(Charsets.UTF_8).also { check(valid(it)){"Invalid filter list"} }
                }
            }
            val local=context.assets.open("adblock/vert-rules.txt").bufferedReader().use { it.readText() }
            if(!NativeAdblock.initialize((texts+local).joinToString("\n")))return@withLock false
            lists.zip(texts).forEach { (name,text) ->
                val file=cache(context,name);val output=file.startWrite()
                try { output.write(text.toByteArray());file.finishWrite(output) }catch(error: Exception){file.failWrite(output);throw error}
            }
            prefs.edit().putLong("lastUpdated",now).apply();true
        }catch(_: Exception){false}
    } }
    private fun trustedPayment(url: String): Boolean {
        val host=android.net.Uri.parse(url).host?.lowercase() ?: return false
        return host=="payfast.co.za"||host.endsWith(".payfast.co.za")||host=="payfast.io"||host.endsWith(".payfast.io")
    }
    private fun header(request: WebResourceRequest,name: String)=request.requestHeaders.entries.firstOrNull { it.key.equals(name,true) }?.value.orEmpty()
    internal fun resourceType(request: WebResourceRequest): String {
        if(request.isForMainFrame)return "document"
        return when(header(request,"Sec-Fetch-Dest").lowercase()){
            "script","worker","sharedworker","serviceworker"->"script"
            "style"->"stylesheet";"image"->"image";"font"->"font";"audio","video","track"->"media";"iframe","frame"->"subdocument"
            else->{val path=request.url.path.orEmpty().lowercase();val accept=header(request,"Accept").lowercase()
                when { path.endsWith(".js")||path.endsWith(".mjs")->"script";path.endsWith(".css")->"stylesheet";accept.startsWith("image/")->"image";path.endsWith(".woff2")||path.endsWith(".woff")->"font";listOf(".m3u8",".mpd",".mp4",".webm",".m4s",".ts",".mp3").any { path.endsWith(it) }->"media";accept.contains("json")->"xmlhttprequest";else->"other"}
            }
        }
    }
    fun shouldBlock(request: WebResourceRequest,topURL: String): Boolean {
        val url=request.url.toString()
        if(trustedPayment(url)||payments.get()>0)return false
        val source=header(request,"Referer").ifBlank { topURL }
        val result=if(ready)try { NativeAdblock.shouldBlock(url,source,resourceType(request),request.method.lowercase()) }catch(_: Exception){false}
        else request.url.host?.lowercase()?.let { host ->fallback.any { host==it||host.endsWith(".$it") } } ?: false
        if(result)blocked.incrementAndGet()
        return result
    }
    fun selectors(url: String,classes: String,ids: String): String=if(ready)try { NativeAdblock.cosmeticRules(url,classes,ids) ?: "[]" }catch(_: Exception){"[]"} else "[\".ytp-ad-overlay-container\",\".video-ads\",\".ad-container\",\"[data-ad-slot]\",\"ins.adsbygoogle\"]"
    fun emptyResponse()=WebResourceResponse("text/plain","utf-8",204,"No Content",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(ByteArray(0)))
    fun status(context: Context): String {
        val engine=if(ready)"adblock-rust 0.13.3 · active" else "Basic blocking · native engine unavailable"
        val updated=context.getSharedPreferences("adblock",Context.MODE_PRIVATE).getLong("lastUpdated",0)
        val date=if(updated>0)java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(updated)) else "Bundled offline lists"
        return "$engine\nEasyList + EasyPrivacy\n$date\n${blocked.get()} requests blocked this session"
    }
}
