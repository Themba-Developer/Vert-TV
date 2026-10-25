package com.verttv.app

import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class EpisodeActivity : FragmentActivity() {
    private var request: Job? = null
    private var episodes = JSONArray()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val contentId = intent.getStringExtra("id") ?: return finish()
        val details = try { JSONObject(intent.getStringExtra("media") ?: "{}") } catch (_: Exception) { JSONObject() }
        val seasons = details.optJSONArray("seasons")?.takeIf { it.length() > 0 }
            ?: JSONArray().put(JSONObject().put("number", 1).put("name", "Season 1"))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64,40,64,40); setBackgroundColor(Color.rgb(9,8,13)) }
        val title = TextView(this).apply { text = intent.getStringExtra("title") ?: "Choose an episode"; textSize = 30f; setTextColor(Color.WHITE) }
        val seasonSpinner = Spinner(this)
        val episodeSpinner = Spinner(this)
        val status = TextView(this).apply { textSize = 18f; setTextColor(Color.LTGRAY) }
        val watch = Button(this).apply { text = "Watch episode"; isEnabled = false; setBackgroundResource(R.drawable.primary_button); setTextColor(Color.rgb(35,18,4)) }
        fun add(view: View, height: Int = 64) { root.addView(view, LinearLayout.LayoutParams(-1,height).apply { topMargin = 18 }) }
        add(title,90)
        add(TextView(this).apply { text="Season"; textSize=18f; setTextColor(Color.WHITE) },32)
        add(seasonSpinner)
        add(TextView(this).apply { text="Episode"; textSize=18f; setTextColor(Color.WHITE) },32)
        add(episodeSpinner)
        add(status,70); add(watch,72)
        setContentView(ScrollView(this).apply { addView(root) })
        seasonSpinner.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,(0 until seasons.length()).map { seasons.getJSONObject(it).optString("name", "Season ${it+1}") })
        seasonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                request?.cancel(); watch.isEnabled=false; status.text="Loading episodes…"
                val season = seasons.getJSONObject(position).getInt("number")
                request = lifecycleScope.launch {
                    try {
                        val result = Api(this@EpisodeActivity).request("/api/content/${android.net.Uri.encode(contentId)}/episodes?season=$season")
                        episodes = result.getJSONArray("episodes")
                        episodeSpinner.adapter = ArrayAdapter(this@EpisodeActivity,android.R.layout.simple_spinner_dropdown_item,(0 until episodes.length()).map { val e=episodes.getJSONObject(it); "E${e.getInt("number")} · ${e.getString("name")}" })
                        watch.isEnabled=episodes.length()>0
                        status.text=if(episodes.length()>0) "${episodes.length()} episodes available" else "No episodes returned for this season."
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { status.text=e.message ?: "Unable to load episodes. Select a season to retry." }
                }
            }
        }
        val preferred=(0 until seasons.length()).firstOrNull { seasons.getJSONObject(it).getInt("number")==1 } ?: 0
        seasonSpinner.setSelection(preferred)
        watch.setOnClickListener {
            val index=episodeSpinner.selectedItemPosition
            if(index>=0 && index<episodes.length()) startActivity(Intent(this,ViewerActivity::class.java).putExtra("url",episodes.getJSONObject(index).getString("url")))
        }
        seasonSpinner.requestFocus()
    }
}
