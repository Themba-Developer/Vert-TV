package com.verttv.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class Api(context: Context) {
    val prefs = context.getSharedPreferences("activation", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()
    suspend fun request(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path)
        prefs.getString("token", null)?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use {
            val json = JSONObject(it.body?.string() ?: "{}")
            if (!it.isSuccessful) throw ApiException(it.code, json.optString("error", "Request failed"))
            json
        }
    }
}
class ApiException(val status: Int, message: String): Exception(message)
