package com.alibaba.mnnllm.android.archive.cloud

import android.content.Context
import com.alibaba.mnnllm.android.archive.ArchiveSettings
import com.alibaba.mnnllm.android.archive.export.Desensitizer
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Optional cloud enhancement (PRD module 7, V2.0). Default OFF — when off, never called,
 * so the app makes no network request. Enforces desensitization and NEVER uploads raw
 * images or audio — only desensitized text.
 */
class CloudEnhancer(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun isEnabled(): Boolean = ArchiveSettings.cloudEnabled(context)

    /**
     * Sends desensitized text to the configured cloud endpoint for advanced structuring
     * (e.g. long audio / complex contracts). Returns the raw response body, or throws.
     */
    fun enhance(rawText: String, instruction: String): String {
        check(isEnabled()) { "Cloud enhancement is disabled" }
        val endpoint = ArchiveSettings.cloudEndpoint(context)
        require(endpoint.isNotBlank()) { "Cloud endpoint not configured" }
        val apiKey = CloudKeyStore.apiKey(context) ?: error("API key not set")

        // Forced desensitization: mask phone/address/amount-like content before upload.
        val safeText = Desensitizer.mask(rawText)
        val payload = gson.toJson(mapOf("instruction" to instruction, "text" to safeText))
        val body = payload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("cloud http ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }
}
