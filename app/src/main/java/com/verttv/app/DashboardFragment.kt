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

data class ContentItem(val title: String, val category: String, val url: String)
class DashboardFragment : BrowseSupportFragment() {
    override fun onActivityCreated(state: Bundle?) {
        super.onActivityCreated(state)
        title = "VERT TV"
        headersState = HEADERS_ENABLED
        brandColor = Color.rgb(16,31,48)
        setOnSearchClickedListener { load() }
        setOnItemViewClickedListener { _, item, _, _ ->
            if(item is ContentItem && item.url == "vert:logout") {
                lifecycleScope.launch {
                    val api = Api(requireContext())
                    try { api.request("/api/auth/logout", org.json.JSONObject()) } catch (_: Exception) {}
                    api.prefs.edit().clear().apply()
                    startActivity(Intent(requireContext(), MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
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
                val item = ContentItem(x.getString("title"), x.getString("category"), x.getString("url"))
                groups.getOrPut(item.category) { mutableListOf() }.add(item)
            }
            val rows = ArrayObjectAdapter(ListRowPresenter())
            groups.entries.forEachIndexed { index, entry ->
                val cards = ArrayObjectAdapter(CardPresenter())
                entry.value.forEach { cards.add(it) }
                rows.add(ListRow(HeaderItem(index.toLong(), entry.key), cards))
            }
            val account = ArrayObjectAdapter(CardPresenter())
            account.add(ContentItem("Sign out", "Account", "vert:logout"))
            rows.add(ListRow(HeaderItem(9999L, "Account"), account))
            adapter = rows
            title = if(groups.isEmpty()) "VERT TV • Your library is coming soon" else "VERT TV"
        } catch(e: Exception) { title = "Connection failed • Select search to retry" }
    } }
}
class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(340, 190)
            textSize = 24f; setTextColor(Color.WHITE); gravity = android.view.Gravity.CENTER
            isFocusable = true; isFocusableInTouchMode = true
            setBackgroundResource(R.drawable.focus_card)
        }
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, item: Any) { (holder.view as TextView).text = (item as ContentItem).title }
    override fun onUnbindViewHolder(holder: ViewHolder) {}
}
