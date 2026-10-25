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

data class ContentItem(val title: String, val category: String, val url: String, val id: String = "", val media: org.json.JSONObject? = null)
class DashboardFragment : BrowseSupportFragment() {
    override fun onActivityCreated(state: Bundle?) {
        super.onActivityCreated(state)
        title = "VERT TV"
        headersState = HEADERS_ENABLED
        brandColor = Color.rgb(16,31,48)
        setOnSearchClickedListener { load() }
        setOnItemViewClickedListener { _, item, _, _ ->
            if(item is ContentItem && item.url == "vert:security") {
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
            val items = Api(requireContext()).request("/api/get_links.json").getJSONArray("items")
            val groups = linkedMapOf<String, MutableList<ContentItem>>()
            for(i in 0 until items.length()) {
                val x = items.getJSONObject(i)
                val item = ContentItem(x.getString("title"), x.getString("category"), x.getString("url"), x.optString("id"), x.optJSONObject("media"))
                groups.getOrPut(item.category) { mutableListOf() }.add(item)
            }
            val rows = ArrayObjectAdapter(ListRowPresenter())
            groups.entries.forEachIndexed { index, entry ->
                val cards = ArrayObjectAdapter(CardPresenter())
                entry.value.forEach { cards.add(it) }
                rows.add(ListRow(HeaderItem(index.toLong(), entry.key), cards))
            }
            val account = ArrayObjectAdapter(CardPresenter())
            account.add(ContentItem("Security questions", "Account", "vert:security"))
            account.add(ContentItem("Sign out", "Account", "vert:logout"))
            rows.add(ListRow(HeaderItem(9999L, "Account"), account))
            adapter = rows
            title = if(groups.isEmpty()) "VERT TV • Your library is coming soon" else "VERT TV"
        } catch(e: Exception) { title = "Connection failed • Select search to retry" }
    } }
}
class CardPresenter : Presenter() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val images = okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(15)).build()
    class CardHolder(val card: ImageCardView) : ViewHolder(card) { var imageJob: kotlinx.coroutines.Job? = null }
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card=ImageCardView(parent.context).apply {
            isFocusable=true; isFocusableInTouchMode=true
            setMainImageDimensions(280,220)
            mainImageView.scaleType=android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(25,38,56))
            setOnFocusChangeListener { _, focused -> setBackgroundColor(if(focused) Color.rgb(36,92,80) else Color.rgb(25,38,56)) }
        }
        return CardHolder(card)
    }
    override fun onBindViewHolder(holder: ViewHolder, value: Any) {
        val h=holder as CardHolder; val item=value as ContentItem
        h.imageJob?.cancel()
        h.card.titleText=item.title
        h.card.contentText=listOf(item.media?.optString("year") ?: "",item.category).filter { it.isNotBlank() }.joinToString(" · ")
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
