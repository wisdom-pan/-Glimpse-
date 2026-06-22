package com.alibaba.mnnllm.android.archive

import android.content.Context

/** Persistent settings for the archive assistant. */
object ArchiveSettings {

    private const val PREF = "archive_settings"
    private const val KEY_STRUCT_MODEL = "structuring_model_id"
    private const val KEY_AUDIO_MODEL = "audio_model_id"
    private const val KEY_CLOUD_ENABLED = "cloud_enabled"
    private const val KEY_CLOUD_ENDPOINT = "cloud_endpoint"

    // PRD default structuring model (exists in model_market.json). ModelScope source by default.
    const val DEFAULT_STRUCT_MODEL = "ModelScope/MNN/Qwen3.5-2B-MNN"
    const val DEFAULT_AUDIO_MODEL = "ModelScope/MNN/gemma-4-E2B-it-MNN"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun structuringModelId(c: Context): String =
        prefs(c).getString(KEY_STRUCT_MODEL, DEFAULT_STRUCT_MODEL) ?: DEFAULT_STRUCT_MODEL

    fun setStructuringModelId(c: Context, id: String) =
        prefs(c).edit().putString(KEY_STRUCT_MODEL, id).apply()

    fun audioModelId(c: Context): String =
        prefs(c).getString(KEY_AUDIO_MODEL, DEFAULT_AUDIO_MODEL) ?: DEFAULT_AUDIO_MODEL

    fun setAudioModelId(c: Context, id: String) =
        prefs(c).edit().putString(KEY_AUDIO_MODEL, id).apply()

    fun cloudEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_CLOUD_ENABLED, false)
    fun setCloudEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_CLOUD_ENABLED, v).apply()

    fun cloudEndpoint(c: Context): String = prefs(c).getString(KEY_CLOUD_ENDPOINT, "") ?: ""
    fun setCloudEndpoint(c: Context, v: String) = prefs(c).edit().putString(KEY_CLOUD_ENDPOINT, v).apply()
}
