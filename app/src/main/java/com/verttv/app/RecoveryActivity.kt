package com.verttv.app

import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

class RecoveryActivity : FragmentActivity() {
    companion object {
        private val questions=listOf("What was the name of your first pet?","What is a memorable place from your childhood?","What was your childhood nickname?")
        fun addQuestions(context: android.content.Context,root: LinearLayout): List<EditText> {
            root.addView(TextView(context).apply { text="Security questions · Use private answers that are hard to guess. All three are required for recovery.";setTextColor(Color.LTGRAY);textSize=16f })
            return questions.map { question ->
                root.addView(TextView(context).apply { text=question;setTextColor(Color.WHITE);textSize=18f;setPadding(0,18,0,4) })
                EditText(context).apply {
                    inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    transformationMethod=PasswordTransformationMethod.getInstance();isSingleLine=true
                    hint="Your private answer (4–128 characters)";setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)
                    filters=arrayOf(android.text.InputFilter.LengthFilter(128));setBackgroundResource(R.drawable.focus_card)
                    importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO
                    root.addView(this,LinearLayout.LayoutParams(-1,64))
                }
            }
        }
        fun passwordToggle(input: EditText,button: Button) {
            button.setOnClickListener {
                val show=input.transformationMethod is PasswordTransformationMethod
                input.transformationMethod=if(show) null else PasswordTransformationMethod.getInstance()
                button.text=if(show) "Hide password 👁" else "Show password 👁"
                input.setSelection(input.text.length)
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val api=Api(this);val setup=intent.getBooleanExtra("setup",false)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(64,40,64,40);setBackgroundColor(Color.rgb(9,8,13)) }
        fun text(value: String,size: Float=18f) { root.addView(TextView(this).apply { text=value;textSize=size;setTextColor(Color.WHITE);setPadding(0,12,0,12) }) }
        fun field(hintText: String,secret: Boolean): EditText {
            return EditText(this).apply { hint=hintText;isSingleLine=true;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setBackgroundResource(R.drawable.focus_card)
                inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                filters=arrayOf(android.text.InputFilter.LengthFilter(if(secret)128 else 254))
                root.addView(this,LinearLayout.LayoutParams(-1,64).apply { topMargin=12 })
            }
        }
        fun button(value: String)=Button(this).apply { text=value;isAllCaps=false;setBackgroundResource(R.drawable.focus_card);setTextColor(Color.WHITE);root.addView(this,LinearLayout.LayoutParams(-1,60).apply { topMargin=12 }) }
        text(if(setup) "Set security questions" else "Reset password",30f)
        text(if(setup) "Confirm your current password to save or update your answers." else "Use your email and all three saved answers. Capital letters and extra spaces are ignored. No email is sent.")
        val email=if(!setup) field("Email address",false).apply { setText(intent.getStringExtra("email") ?: api.prefs.getString("email","")) } else null
        val current=if(setup) field("Current password",true) else null
        current?.let { passwordToggle(it,button("Show password 👁")) }
        val answers=addQuestions(this,root)
        val next=if(!setup) field("New password (12–128 characters)",true) else null
        next?.let { passwordToggle(it,button("Show password 👁")) }
        val submit=button(if(setup) "Save security questions" else "Reset password")
        val status=TextView(this).apply { setTextColor(Color.LTGRAY);textSize=18f;root.addView(this) }
        button("Back").setOnClickListener { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
        submit.setOnClickListener {
            if(answers.any { it.text.toString().trim().length !in 4..128 }) { status.text="Answer all three questions using 4–128 characters each.";return@setOnClickListener }
            val password=if(setup)current!!.text.toString() else next!!.text.toString()
            if(password.length !in 12..128) { status.text="Use a password of 12–128 characters.";return@setOnClickListener }
            submit.isEnabled=false
            lifecycleScope.launch {
                try {
                    val body=JSONObject().put("answers",JSONArray(answers.map { it.text.toString() }))
                    if(setup)body.put("currentPassword",password) else body.put("email",email!!.text.toString()).put("newPassword",password)
                    api.request("/api/auth/recovery/"+(if(setup) "setup" else "reset"),body)
                    answers.forEach { it.text.clear() };current?.text?.clear();next?.text?.clear()
                    if(setup) { status.text="Security questions saved." }
                    else {
                        api.prefs.edit().remove("token").apply()
                        Toast.makeText(this@RecoveryActivity,"Password reset. Sign in with your new password.",Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@RecoveryActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { status.text=e.message ?: "Unable to save changes." }
                finally { submit.isEnabled=true }
            }
        }
    }
}
