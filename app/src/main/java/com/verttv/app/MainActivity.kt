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
    @Deprecated("Android TV back navigation")
    override fun onBackPressed() {
        val form = findViewById<android.view.View>(R.id.activation_form)
        val welcome = findViewById<android.view.View>(R.id.welcome_copy)
        if (form?.visibility == android.view.View.VISIBLE && welcome != null) {
            form.visibility = android.view.View.GONE
            welcome.visibility = android.view.View.VISIBLE
            findViewById<Button>(R.id.open_activation).requestFocus()
        } else super.onBackPressed()
    }
    private fun activation(message: String? = null) {
        setContentView(R.layout.activity_main)
        val email = findViewById<EditText>(R.id.email)
        val activationCode = findViewById<EditText>(R.id.activation_code)
        email.setText(api.prefs.getString("email", ""))
        if (message != null) findViewById<TextView>(R.id.status).text = message
        findViewById<Button>(R.id.activate).setOnClickListener { button ->
            val value = email.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) { email.error = "Enter a valid email"; return@setOnClickListener }
            button.isEnabled = false
            lifecycleScope.launch {
                try {
                    val pending = value.equals(api.prefs.getString("email", ""), ignoreCase = true) && api.prefs.contains("token") && activationCode.visibility != android.view.View.VISIBLE
                    val result = if (pending) api.request("/api/activation/checkout", JSONObject()) else api.request("/api/activation/start", JSONObject().put("email", value).put("activationCode", activationCode.text.toString()))
                    if (result.optBoolean("requiresActivationCode")) {
                        activationCode.visibility = android.view.View.VISIBLE
                        activationCode.requestFocus()
                        findViewById<TextView>(R.id.status).text = "Enter your private code for payment-free access."
                        (button as Button).text = "Activate free access"
                        return@launch
                    }
                    if (!pending) api.prefs.edit().putString("email", value).putString("token", result.getString("token")).apply()
                    if (result.optBoolean("active")) verify()
                    else startActivity(Intent(this@MainActivity, PayfastActivity::class.java).putExtra("checkout", result.getString("checkoutUrl")))
                } catch (e: Exception) {
                    if(e is ApiException && e.status == 401) api.prefs.edit().remove("token").apply()
                    findViewById<TextView>(R.id.status).text = e.message
                }
                finally { button.isEnabled = true }
            }
        }
        val welcome = findViewById<android.view.View>(R.id.welcome_copy)
        val form = findViewById<android.view.View>(R.id.activation_form)
        fun showForm() {
            welcome.visibility = android.view.View.GONE
            form.visibility = android.view.View.VISIBLE
            email.requestFocus()
        }
        findViewById<Button>(R.id.open_activation).setOnClickListener { showForm() }
        if (message != null || email.text.isNotBlank()) showForm()
        else findViewById<Button>(R.id.open_activation).requestFocus()
    }
}
