package com.verttv.app

import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

data class ContentItem(val title: String, val category: String, val url: String, val id: String = "", val media: org.json.JSONObject? = null, val loved: Boolean = false)
class DashboardFragment : BrowseSupportFragment() {
    override fun onResume() { super.onResume();if(adapter!=null)load() }
    private var lastSearch=""
    override fun onViewCreated(view: android.view.View,state: Bundle?) {
        super.onViewCreated(view,state)
        val root=view as? android.widget.FrameLayout ?: return
        val settings=android.widget.Button(requireContext()).apply {
            text="⚙ Settings";isAllCaps=false;textSize=16f;setTextColor(Color.WHITE);setBackgroundResource(R.drawable.focus_card)
            setOnClickListener { startActivity(Intent(requireContext(),SettingsActivity::class.java)) }
        }
        root.addView(settings,android.widget.FrameLayout.LayoutParams(-2,54).apply { gravity=android.view.Gravity.TOP or android.view.Gravity.END;topMargin=16;rightMargin=30 })
    }
    private fun love(item: ContentItem) {
        if(item.id.isBlank()||item.url.startsWith("vert:"))return
        android.app.AlertDialog.Builder(requireContext()).setTitle(item.title)
            .setItems(arrayOf(if(item.loved) "Remove from Loved" else "Add to Loved")) { _, _ ->
                lifecycleScope.launch {
                    try { Api(requireContext()).request("/api/library/activity",org.json.JSONObject().put("contentId",item.id).put("loved",!item.loved));load() }
                    catch(e: Exception) { android.widget.Toast.makeText(requireContext(),e.message,android.widget.Toast.LENGTH_LONG).show() }
                }
            }.show()
    }
    private fun search() {
        val input=android.widget.EditText(requireContext()).apply {
            hint="Movie or TV show name";setText(lastSearch);setSingleLine(true)
            filters=arrayOf(android.text.InputFilter.LengthFilter(120))
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        val preview=android.webkit.WebView(requireContext()).apply {
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture=true;settings.setSupportMultipleWindows(false)
            webViewClient=AdBlockWebViewClient();visibility=android.view.View.GONE;isFocusable=false;isFocusableInTouchMode=false
        }
        val container=android.widget.LinearLayout(requireContext()).apply { orientation=android.widget.LinearLayout.VERTICAL;addView(input);addView(preview,android.widget.LinearLayout.LayoutParams(-1,330)) }
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        var pending: Runnable?=null
        input.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                pending?.let { handler.removeCallbacks(it) };val query=s.toString().trim()
                preview.visibility=android.view.View.GONE;preview.stopLoading()
                if(query.length<2)return
                pending=Runnable { preview.visibility=android.view.View.VISIBLE;preview.loadUrl(android.net.Uri.Builder().scheme("https").authority("dulo.mov").appendPath("search").appendQueryParameter("q",query).build().toString()) }.also { handler.postDelayed(it,650) }
            }
        })
        val dialog=android.app.AlertDialog.Builder(requireContext()).setTitle("Search")
            .setView(container).setPositiveButton("Search",null).setNegativeButton("Cancel",null).setNeutralButton("Refresh library") { _, _ -> load() }.create()
        dialog.setOnShowListener {
            val submit=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            submit.setOnClickListener {
                val query=input.text.toString().trim()
                if(query.isBlank()) { input.error="Enter a movie or show name" }
                else {
                    lastSearch=query
                    val url=android.net.Uri.Builder().scheme("https").authority("dulo.mov").appendPath("search").appendQueryParameter("q",query).build().toString()
                    dialog.dismiss()
                    startActivity(Intent(requireContext(),ViewerActivity::class.java).putExtra("url",url))
                }
            }
            input.setOnEditorActionListener { _, action, _ ->
                if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { submit.performClick();true } else false
            }
            input.requestFocus()
        }
        dialog.setOnDismissListener { pending?.let { handler.removeCallbacks(it) };preview.stopLoading();preview.destroy() }
        dialog.show()
    }

    override fun onActivityCreated(state: Bundle?) {
        super.onActivityCreated(state)
        title = "VERT TV"
        headersState = HEADERS_ENABLED
        brandColor = Color.rgb(45,30,18)
        setOnSearchClickedListener { search() }
        setOnItemViewClickedListener { _, item, _, _ ->
            if(item is ContentItem && item.id.isNotBlank()&&!item.url.startsWith("vert:")) lifecycleScope.launch {
                try { Api(requireContext()).request("/api/library/activity",org.json.JSONObject().put("contentId",item.id).put("opened",true)) }catch(_: Exception) {}
            }
            if(item is ContentItem && item.url == "vert:search") { search()
            } else if(item is ContentItem && item.url == "vert:refresh") { load()
            } else if(item is ContentItem && item.url == "vert:settings") { startActivity(Intent(requireContext(),SettingsActivity::class.java))
            } else if(item is ContentItem && item.url == "vert:security") {
                startActivity(Intent(requireContext(),RecoveryActivity::class.java).putExtra("setup",true))
            } else if(item is ContentItem && item.url == "vert:logout") {
                lifecycleScope.launch {
                    val api = Api(requireContext())
                    try { api.request("/api/auth/logout", org.json.JSONObject()) } catch (_: Exception) {}
                    api.prefs.edit().clear().apply()
                    startActivity(Intent(requireContext(), MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
            } else if(item is ContentItem && item.media?.optString("provider")=="2embed" && item.media?.optString("type")=="tv") {
                startActivity(Intent(requireContext(), EpisodeActivity::class.java).putExtra("id",item.id).putExtra("title",item.title).putExtra("media",item.media.toString()))
            } else if(item is ContentItem) startActivity(Intent(requireContext(), ViewerActivity::class.java).putExtra("url", item.url))
        }
        load()
    }
    private fun load() { lifecycleScope.launch {
        try {
            val api=Api(requireContext())
            val items = api.request("/api/get_links.json").getJSONArray("items")
            val activity=try { api.request("/api/library/activity").getJSONArray("items") }catch(_: Exception){org.json.JSONArray()}
            val saved=(0 until activity.length()).associate { val row=activity.getJSONObject(it);row.getString("id") to row }
            val all=mutableListOf<ContentItem>()
            val groups = linkedMapOf<String, MutableList<ContentItem>>()
            for(i in 0 until items.length()) {
                val x = items.getJSONObject(i)
                val item = ContentItem(x.getString("title"), x.getString("category"), x.getString("url"), x.optString("id"), x.optJSONObject("media"), saved[x.optString("id")]?.optInt("loved")==1)
                all.add(item)
                groups.getOrPut(item.category) { mutableListOf() }.add(item)
            }
            val rows = ArrayObjectAdapter(ListRowPresenter())
            fun savedRow(name: String,index: Long,selected: List<ContentItem>) {
                if(selected.isEmpty())return
                val cards=ArrayObjectAdapter(CardPresenter { love(it) });selected.forEach { cards.add(it) };rows.add(ListRow(HeaderItem(index,name),cards))
            }
            savedRow("Recent",9000L,all.filter { (saved[it.id]?.optLong("lastOpened") ?: 0L)>0 }.sortedByDescending { saved[it.id]?.optLong("lastOpened") ?: 0L })
            savedRow("Loved",9001L,all.filter { it.loved })
            groups.entries.forEachIndexed { index, entry ->
                val cards = ArrayObjectAdapter(CardPresenter { love(it) })
                entry.value.forEach { cards.add(it) }
                rows.add(ListRow(HeaderItem(index.toLong(), entry.key), cards))
            }
            val browse=ArrayObjectAdapter(CardPresenter())
            browse.add(ContentItem("Search movies & TV shows", "Search", "vert:search"))
            browse.add(ContentItem("Refresh library", "Search", "vert:refresh"))
            browse.add(ContentItem("Settings", "Account", "vert:settings"))
            rows.add(ListRow(HeaderItem(9998L,"Search"),browse))
            val discovery=ArrayObjectAdapter(CardPresenter())
            discovery.add(ContentItem("Trending movies","Discover","https://dulo.mov/movies/?shelf=popular"))
            discovery.add(ContentItem("Trending series","Discover","https://dulo.mov/shows/?shelf=popular"))
            discovery.add(ContentItem("New movies","Discover","https://dulo.mov/movies/?shelf=in-theaters"))
            discovery.add(ContentItem("New series","Discover","https://dulo.mov/shows/?shelf=new-seasons"))
            rows.add(ListRow(HeaderItem(9999L,"Trending & New"),discovery))
            adapter = rows
            title = if(groups.isEmpty()) "VERT TV • Your library is coming soon" else "VERT TV"
        } catch(e: Exception) { title = "Connection failed • Select search, then Refresh library" }
    } }
}
class CardPresenter(private val onLove: ((ContentItem)->Unit)? = null) : Presenter() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val images = okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(15)).build()
    class CardHolder(val card: ImageCardView) : ViewHolder(card) { var imageJob: kotlinx.coroutines.Job? = null }
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card=ImageCardView(parent.context).apply {
            isFocusable=true; isFocusableInTouchMode=true
            setMainImageDimensions(280,220)
            mainImageView.scaleType=android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(27,23,32))
            setOnFocusChangeListener { _, focused -> setBackgroundColor(if(focused) Color.rgb(97,60,20) else Color.rgb(27,23,32)) }
        }
        return CardHolder(card)
    }
    override fun onBindViewHolder(holder: ViewHolder, value: Any) {
        val h=holder as CardHolder; val item=value as ContentItem
        h.imageJob?.cancel()
        h.card.setOnLongClickListener { if(onLove!=null&&item.id.isNotBlank()){onLove.invoke(item);true}else false }
        h.card.titleText=item.title
        h.card.contentText=listOf(item.media?.optString("year") ?: "",item.category,if(item.loved) "♥ Loved" else if(item.id.isNotBlank()) "Hold OK to love" else "").filter { it.isNotBlank() }.joinToString(" · ")
        h.card.mainImage=h.card.context.getDrawable(R.drawable.banner)
        val poster=item.media?.optString("poster")?.takeIf { it.startsWith("https://") } ?: return
        h.imageJob=scope.launch {
            try {
                val bitmap=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    images.newCall(okhttp3.Request.Builder().url(poster).build()).execute().use { response ->
                        if(!response.isSuccessful) null else response.body?.byteStream()?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    }
                }
                if(bitmap!=null) h.card.mainImage=android.graphics.drawable.BitmapDrawable(h.card.resources,bitmap)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { /* Keep the bundled fallback artwork. */ }
        }
    }
    override fun onUnbindViewHolder(holder: ViewHolder) { val h=holder as CardHolder; h.imageJob?.cancel(); h.card.mainImage=null }
}
