package com.alibaba.mnnllm.android.archive.ocr

import android.content.Context
import java.io.File

/**
 * Copies the bundled PP-OCRv6 models from APK assets (assets/ocr/) into the app's
 * private files dir on first launch. MNN's Module::load needs a real filesystem path.
 */
object OcrModelInstaller {

    private const val ASSET_DIR = "ocr"
    private const val MODELS_SUBDIR = "models/ocr"
    private val FILES = listOf("det.mnn", "rec.mnn", "keys.txt")

    /** Returns the absolute directory containing det.mnn/rec.mnn/keys.txt, installing if needed. */
    fun ensureInstalled(context: Context): String {
        val dir = File(context.filesDir, MODELS_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        for (name in FILES) {
            val target = File(dir, name)
            if (target.exists() && target.length() > 0) continue
            context.assets.open("$ASSET_DIR/$name").use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 1 shl 16)
                }
            }
        }
        return dir.absolutePath
    }

    fun isInstalled(context: Context): Boolean {
        val dir = File(context.filesDir, MODELS_SUBDIR)
        return FILES.all { File(dir, it).let { f -> f.exists() && f.length() > 0 } }
    }
}
