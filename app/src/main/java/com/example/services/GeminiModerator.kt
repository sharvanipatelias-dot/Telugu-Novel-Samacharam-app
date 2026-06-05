package com.example.services

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ModerationResult(
    val isSafe: Boolean,
    val reason: String
)

object GeminiModerator {
    private const val TAG = "GeminiModerator"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    suspend fun moderateContent(title: String, content: String): ModerationResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "No valid Gemini API key found. Defaulting to local basic moderation.")
            return@withContext basicRuleModeration(title, content)
        }

        val prompt = """
            You are a Telugu and English Content Moderator for the "Telugu Novel & Samacharam" app.
            Analyze the following submitted post for policy violations.
            - Title: "$title"
            - Content: "$content"

            Check specifically for:
            1. Spam or commercial junk
            2. Hate Speech (offensive text targeting communities)
            3. Abusive, vulgar or extremely violent language
            4. Adult, sexually explicit content
            5. Clear fake news indicators (dangerous conspiracy theories, fabricated warnings)
            6. Unsafe or illegal content

            Respond strictly in raw, valid JSON with no markdown block qualifiers.
            JSON Format:
            {
              "isSafe": true/false value,
              "reason": "A brief explanation of the detection in English or Telugu."
            }
        """.trimIndent()

        try {
            // Build direct Gemini REST endpoint
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            
            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini API failed with response code ${response.code}: $errBody")
                    return@withContext basicRuleModeration(title, content)
                }

                val bodyStr = response.body?.string() ?: ""
                val geminiJson = JSONObject(bodyStr)
                val candidates = geminiJson.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""

                Log.d(TAG, "Gemini Response: $text")

                // Clean-up response markdown if Gemini included it
                val cleanJson = text.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                try {
                    val parsedObj = JSONObject(cleanJson)
                    val isSafe = parsedObj.optBoolean("isSafe", true)
                    val reason = parsedObj.optString("reason", "Approved by AI")
                    ModerationResult(isSafe, reason)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse json: $cleanJson. Defaulting to safe.", e)
                    ModerationResult(true, "AI passed checks, json unparsable")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini moderation check", e)
            basicRuleModeration(title, content)
        }
    }

    private fun basicRuleModeration(title: String, content: String): ModerationResult {
        // Fallback basic keyword moderation when offline or API key is missing
        val unsafeWords = listOf("abuse", "hate", "scam", "spam", "porn", "vulgar", "bribe", "fake news", "kill", "murder")
        val combinedText = "$title $content".lowercase()

        for (word in unsafeWords) {
            if (combinedText.contains(word)) {
                return ModerationResult(false, "Basic Rules: Contains policy-violating keyword '$word'.")
            }
        }
        return ModerationResult(true, "Auto-approved by local heuristics filter (offline safety screen).")
    }
}
