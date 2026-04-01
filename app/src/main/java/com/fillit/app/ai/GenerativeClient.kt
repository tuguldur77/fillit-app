package com.fillit.app.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Updated Gemini REST helper.
 * Compatible with models/gemini-2.5-flash and models/gemini-2.5-pro.
 */
class GenerativeClient(private val apiKey: String) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** List available models (for debugging). */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw Exception("ListModels failed: ${resp.code} - $body")

            val json = JSONObject(body)
            val list = mutableListOf<String>()
            val models = json.optJSONArray("models")
            if (models != null) {
                for (i in 0 until models.length()) {
                    val m = models.optJSONObject(i)?.optString("name")
                    if (!m.isNullOrEmpty()) list.add(m)
                }
            }
            list
        }
    }

    /** Generate content with Gemini (fixed endpoint). */
    suspend fun generate(modelName: String, prompt: String): String = withContext(Dispatchers.IO) {
        val cleanName = modelName.removePrefix("models/")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanName:generateContent?key=$apiKey"

        // ✅ Proper JSON — no nested string encoding
        val contentsArray = org.json.JSONArray().apply {
            put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt))))
        }

        val payload = JSONObject().put("contents", contentsArray).toString()

        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            Log.d("GenerativeClient", "Gemini request status=${resp.code} len=${body.length}")
            if (!resp.isSuccessful) {
                Log.e("GenerativeClient", "Generate failed: ${resp.code} - $body")
                return@use ""
            }

            val json = JSONObject(body)
            json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                ?: ""
        }
    }
}
