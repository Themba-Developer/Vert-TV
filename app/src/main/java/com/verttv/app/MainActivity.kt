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
        val password = findViewById<EditText>(R.id.password)
        val code = findViewById<EditText>(R.id.activation_code)
        val submit = findViewById<Button>(R.id.activate)
        val toggle = findViewById<Button>(R.id.switch_auth)
        val status = findViewById<TextView>(R.id.status)
        val welcome = findViewById<android.view.View>(R.id.welcome_copy)
        val form = findViewById<android.view.View>(R.id.activation_form)
        var signup = true
        email.setText(api.prefs.getString("email", ""))
        fun updateSetupField() {
            val reserved = email.text.toString().trim().lowercase() in setOf("vertcorpgroup@gmail.com", "mahlangu843@gmail.com")
            code.visibility = if (signup && reserved) android.view.View.VISIBLE else android.view.View.GONE
        }
        fun showForm(create: Boolean) {
            signup = create
            welcome.visibility = android.view.View.GONE
            form.visibility = android.view.View.VISIBLE
            findViewById<TextView>(R.id.auth_heading).text = if (signup) "Create your account." else "Welcome back."
            submit.text = if (signup) "Create account" else "Sign in"
            toggle.text = if (signup) "Already a member? Sign in" else "New here? Create an account"
            status.text = message ?: if (signup) "Enter your email and a password of 12+ characters." else "Use your Vert TV email and password."
            updateSetupField()
            email.requestFocus()
        }
        email.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSetupField() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        toggle.setOnClickListener { showForm(!signup) }
        findViewById<Button>(R.id.open_activation).setOnClickListener { showForm(true) }
        findViewById<Button>(R.id.open_login).setOnClickListener { showForm(false) }
        submit.setOnClickListener {
            val value = email.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) { email.error = "Enter a valid email"; return@setOnClickListener }
            if (password.text.length !in 12..128) { password.error = "Use 12–128 characters"; return@setOnClickListener }
            submit.isEnabled = false; toggle.isEnabled = false
            lifecycleScope.launch {
                try {
                    val body = JSONObject().put("email", value).put("password", password.text.toString()).put("setupCode", code.text.toString()).put("client", "tv")
                    val result = api.request(if (signup) "/api/auth/signup" else "/api/auth/login", body)
                    api.prefs.edit().putString("email", value).putString("token", result.getString("token")).apply()
                    password.text.clear(); code.text.clear()
                    if (result.getJSONObject("user").optBoolean("active")) verify()
                    else {
                        val checkout = api.request("/api/billing/checkout", JSONObject())
                        if (checkout.optBoolean("active")) verify()
                        else startActivity(Intent(this@MainActivity, PayfastActivity::class.java).putExtra("checkout", checkout.getString("checkoutUrl")))
                    }
                } catch (e: Exception) {
                    if(e is ApiException && e.status == 401) api.prefs.edit().remove("token").apply()
                    status.text = e.message
                } finally { submit.isEnabled = true; toggle.isEnabled = true }
            }
        }
        if (message != null || email.text.isNotBlank()) showForm(false)
        else findViewById<Button>(R.id.open_activation).requestFocus()
    }
}
