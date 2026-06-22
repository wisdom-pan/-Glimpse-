package com.alibaba.mnnllm.android.archive.pipeline

import android.util.Log
import com.alibaba.mnnllm.android.llm.ChatService
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession

/**
 * Transcribes an audio file to text using a real Gemma audio-understanding LLM via MNN.
 * The audio is passed through the multimodal `<audio>path</audio>` prompt format that the
 * MNN LLM engine consumes natively (PRD V1.1, Gemma 4 E2B).
 */
class AudioTranscriber(
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
            sessionIdParam = "archive_asr_${System.currentTimeMillis()}",
            chatDataItemList = null,
            supportOmni = true,
            backendType = null,
            useCustomConfig = false
        )
        s.supportOmni = true
        s.load()
        if (!s.isModelLoaded()) throw IllegalStateException("Audio model failed to load: $modelId")
        session = s
    }

    /** Returns the transcribed text from the audio at [audioPath]. Blocking. */
    fun transcribe(audioPath: String): String {
        ensureLoaded()
        val s = session ?: throw IllegalStateException("not loaded")
        // Multimodal prompt: audio tag + instruction. Engine decodes the audio file.
        val prompt = "<audio>$audioPath</audio>请将这段音频逐字转写为文字，只输出转写文本。"
        val sb = StringBuilder()
        s.reset()
        s.setKeepHistory(false)
        s.generate(prompt, emptyMap(), object : GenerateProgressListener {
            override fun onProgress(progress: String?): Boolean {
                if (progress != null) sb.append(progress)
                return false
            }
        })
        val text = sb.toString().trim()
        Log.d(TAG, "transcribed: $text")
        return text
    }

    fun release() {
        try { session?.release() } catch (e: Exception) { Log.w(TAG, "release", e) }
        session = null
    }

    companion object { private const val TAG = "AudioTranscriber" }
}
