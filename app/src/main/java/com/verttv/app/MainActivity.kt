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
    private var openingLibrary=false
    override fun onCreate(state: Bundle?) { super.onCreate(state); api = Api(this) }
    override fun onResume() { super.onResume(); verify() }
    private fun verify() {
        if (api.prefs.getString("token", null) == null) { activation(); return }
        lifecycleScope.launch {
            try {
                if (api.request("/api/activation/status").optBoolean("active")) {
                    if(!openingLibrary){openingLibrary=true;startActivity(Intent(this@MainActivity,LibraryActivity::class.java));finish()}
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
        val confirmation = findViewById<EditText>(R.id.confirm_email)
        val security = findViewById<LinearLayout>(R.id.signup_security)
        val answers=RecoveryActivity.addQuestions(this,security)
        RecoveryActivity.passwordToggle(password,findViewById(R.id.password_visibility))
        val forgot=findViewById<Button>(R.id.forgot_password)
        forgot.setOnClickListener { startActivity(Intent(this,RecoveryActivity::class.java).putExtra("email",email.text.toString())) }
        val submit = findViewById<Button>(R.id.activate)
        val toggle = findViewById<Button>(R.id.switch_auth)
        val status = findViewById<TextView>(R.id.status)
        val welcome = findViewById<android.view.View>(R.id.welcome_copy)
        val form = findViewById<android.view.View>(R.id.activation_form)
        if(api.prefs.getString("token",null)!=null) {
            findViewById<LinearLayout>(R.id.activation_form).addView(Button(this).apply {
                text="Account settings";isAllCaps=false;setBackgroundResource(R.drawable.focus_card)
                setTextColor(android.graphics.Color.WHITE)
                setOnClickListener { startActivity(Intent(this@MainActivity,SettingsActivity::class.java)) }
            })
        }
        var signup = true
        email.setText(api.prefs.getString("email", ""))
        fun showForm(create: Boolean) {
            signup = create
            welcome.visibility = android.view.View.GONE
            form.visibility = android.view.View.VISIBLE
            findViewById<TextView>(R.id.auth_heading).text = if (signup) "Create your account." else "Welcome back."
            submit.text = if (signup) "Create account" else "Sign in"
            toggle.text = if (signup) "Already a member? Sign in" else "New here? Create an account"
            status.text = message ?: if (signup) "Enter your email and a password of 12+ characters." else "Use your Vert TV email and password."
            confirmation.visibility=if(signup) android.view.View.VISIBLE else android.view.View.GONE
            security.visibility=confirmation.visibility
            forgot.visibility=if(signup) android.view.View.GONE else android.view.View.VISIBLE
            password.transformationMethod=android.text.method.PasswordTransformationMethod.getInstance()
            findViewById<Button>(R.id.password_visibility).text="Show password 👁"
            email.requestFocus()
        }
        toggle.setOnClickListener { showForm(!signup) }
        findViewById<Button>(R.id.open_activation).setOnClickListener { showForm(true) }
        findViewById<Button>(R.id.open_login).setOnClickListener { showForm(false) }
        submit.setOnClickListener {
            val value = email.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) { email.error = "Enter a valid email"; return@setOnClickListener }
            if (password.text.length !in 12..128) { password.error = "Use 12–128 characters"; return@setOnClickListener }
            if(signup && !value.equals(confirmation.text.toString().trim(),ignoreCase=true)) { confirmation.error="Email addresses must match";return@setOnClickListener }
            if(signup && answers.any { it.text.toString().trim().length !in 4..128 }) { status.text="Answer all three security questions using 4–128 characters each.";return@setOnClickListener }
            submit.isEnabled = false; toggle.isEnabled = false
            lifecycleScope.launch {
                try {
                    val body = JSONObject().put("email", value).put("password", password.text.toString()).put("client", "tv")
                    if(signup) body.put("confirmEmail",confirmation.text.toString()).put("answers",org.json.JSONArray(answers.map { it.text.toString() }))
                    val result = api.request(if (signup) "/api/auth/signup" else "/api/auth/login", body)
                    api.prefs.edit().putString("email", value).putString("token", result.getString("token")).apply()
                    password.text.clear();answers.forEach { it.text.clear() }
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
