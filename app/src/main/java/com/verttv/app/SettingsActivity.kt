package com.verttv.app

import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class SettingsActivity : FragmentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val api=Api(this)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(64,40,64,40);setBackgroundColor(Color.rgb(7,12,20)) }
        fun text(value: String,size: Float=18f) { root.addView(TextView(this).apply { text=value;textSize=size;setTextColor(Color.WHITE);setPadding(0,14,0,10) }) }
        fun button(value: String)=Button(this).apply { text=value;isAllCaps=false;setBackgroundResource(R.drawable.focus_card);setTextColor(Color.WHITE);root.addView(this,LinearLayout.LayoutParams(-1,60).apply { topMargin=12 }) }
        fun password(label: String): EditText {
            text(label)
            val field=EditText(this).apply { isSingleLine=true;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;setTextColor(Color.WHITE);setBackgroundResource(R.drawable.focus_card);filters=arrayOf(android.text.InputFilter.LengthFilter(128));root.addView(this,LinearLayout.LayoutParams(-1,64)) }
            RecoveryActivity.passwordToggle(field,button("Show password 👁"));return field
        }
        text("Account settings",32f);text(api.prefs.getString("email","") ?: "")
        button("Update security questions").setOnClickListener { startActivity(Intent(this,RecoveryActivity::class.java).putExtra("setup",true)) }
        text("Change password",24f)
        val current=password("Current password")
        val next=password("New password")
        val confirm=password("Re-type new password")
        val submit=button("Update password")
        val status=TextView(this).apply { setTextColor(Color.LTGRAY);textSize=18f;root.addView(this) }
        fun returnToLogin(){api.prefs.edit().remove("token").apply();startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
        submit.setOnClickListener {
            if(current.text.length !in 12..128||next.text.length !in 12..128) { status.text="Use passwords of 12–128 characters.";return@setOnClickListener }
            if(next.text.toString()!=confirm.text.toString()) { status.text="New passwords must match.";return@setOnClickListener }
            submit.isEnabled=false
            lifecycleScope.launch {
                try {
                    api.request("/api/auth/password/change",JSONObject().put("currentPassword",current.text.toString()).put("newPassword",next.text.toString()).put("confirmPassword",confirm.text.toString()))
                    current.text.clear();next.text.clear();confirm.text.clear()
                    Toast.makeText(this@SettingsActivity,"Password updated. Sign in again.",Toast.LENGTH_LONG).show();returnToLogin()
                }catch(e: kotlinx.coroutines.CancellationException){throw e}
                catch(e: Exception){status.text=e.message}
                finally{submit.isEnabled=true}
            }
        }
        button("Sign out").setOnClickListener { lifecycleScope.launch { try { api.request("/api/auth/logout",JSONObject());returnToLogin() }catch(e: Exception){status.text=e.message} } }
        button("Back to library").setOnClickListener { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
