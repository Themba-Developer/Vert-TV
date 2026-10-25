package com.verttv.app

import android.os.Bundle
import android.content.Intent
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : FragmentActivity() {
    private lateinit var api: Api
    override fun onCreate(state: Bundle?) { super.onCreate(state); api = Api(this) }
    override fun onResume() { super.onResume(); verify() }
    private fun verify() {
        if (api.prefs.getString("token", null) == null) { activation(); return }
        lifecycleScope.launch {
            try {
                if (api.request("/api/activation/status").optBoolean("active")) {
                    supportFragmentManager.beginTransaction().replace(android.R.id.content, DashboardFragment()).commit()
                } else activation()
            } catch (e: Exception) {
                if(e is ApiException && e.status == 401) api.prefs.edit().remove("token").apply()
                activation(e.message ?: "Unable to connect. Try again.")
            }
        }
    }
    private fun activation(message: String? = null) {
        setContentView(R.layout.activity_main)
        val email = findViewById<EditText>(R.id.email)
        email.setText(api.prefs.getString("email", ""))
        if (message != null) findViewById<TextView>(R.id.status).text = message
        findViewById<Button>(R.id.activate).setOnClickListener { button ->
            val value = email.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) { email.error = "Enter a valid email"; return@setOnClickListener }
            button.isEnabled = false
            lifecycleScope.launch {
                try {
                    val pending = value.equals(api.prefs.getString("email", ""), ignoreCase = true) && api.prefs.contains("token")
                    val result = if (pending) api.request("/api/activation/checkout", JSONObject()) else api.request("/api/activation/start", JSONObject().put("email", value))
                    if (!pending) api.prefs.edit().putString("email", value).putString("token", result.getString("token")).apply()
                    startActivity(Intent(this@MainActivity, PayfastActivity::class.java).putExtra("checkout", result.getString("checkoutUrl")))
                } catch (e: Exception) {
                    if(e is ApiException && e.status == 401) api.prefs.edit().remove("token").apply()
                    findViewById<TextView>(R.id.status).text = e.message
                }
                finally { button.isEnabled = true }
            }
        }
        email.requestFocus()
    }
}
