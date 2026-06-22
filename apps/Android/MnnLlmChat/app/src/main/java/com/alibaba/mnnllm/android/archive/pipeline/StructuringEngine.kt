package com.alibaba.mnnllm.android.archive.pipeline

import android.util.Log
import com.alibaba.mnnllm.android.archive.data.ArchiveRecord
import com.alibaba.mnnllm.android.llm.ChatService
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession

/**
 * Runs structured extraction using a real downloaded Qwen LLM via the existing LlmSession/JNI.
 * Not a mock: it loads the model, runs response(), collects streamed text, parses JSON.
 */
class StructuringEngine(
    private val modelId: String,
    private val configPath: String
) {
    private var session: LlmSession? = null

    @Synchronized
    fun ensureLoaded() {
        if (session != null) return
        val s = ChatService.provide().createLlmSession(
            modelId = modelId,
            modelDir = configPath,
            sessionIdParam = "archive_struct_${System.currentTimeMillis()}",
            chatDataItemList = null,
            supportOmni = false,
            backendType = null,
            useCustomConfig = false
        )
        s.load()
        if (!s.isModelLoaded()) {
            throw IllegalStateException("Structuring model failed to load: $modelId")
        }
        // Disable thinking so the model emits JSON directly (no long reasoning) -> faster + parseable.
        try { s.updateThinking(false) } catch (e: Exception) { Log.w(TAG, "updateThinking", e) }
        // Short structured output; cap tokens to keep latency low.
        try { s.updateMaxNewTokens(256) } catch (e: Exception) { Log.w(TAG, "maxNewTokens", e) }
        session = s
    }

    /** Blocking call: returns the structured record extracted from rawText. */
    fun structure(rawText: String, sourceType: String): ArchiveRecord {
        ensureLoaded()
        val s = session ?: throw IllegalStateException("not loaded")
        // Append /no_think (Qwen3 switch) as a hard guard against reasoning output.
        val prompt = StructuringPrompt.buildPrompt(rawText, sourceType) + " /no_think"
        val sb = StringBuilder()
        s.reset()
        s.setKeepHistory(false)
        val listener = object : GenerateProgressListener {
            override fun onProgress(progress: String?): Boolean {
                if (progress != null) sb.append(progress)
                return false // never cancel
            }
        }
        s.generate(prompt, emptyMap(), listener)
        val output = sb.toString()
        Log.d(TAG, "structuring output: $output")
        return StructuringPrompt.parse(output, rawText, sourceType)
    }

    fun release() {
        try { session?.release() } catch (e: Exception) { Log.w(TAG, "release", e) }
        session = null
    }

    companion object {
        private const val TAG = "StructuringEngine"
    }
}
