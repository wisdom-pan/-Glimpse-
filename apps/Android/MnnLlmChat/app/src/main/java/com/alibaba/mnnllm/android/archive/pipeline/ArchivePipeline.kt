package com.alibaba.mnnllm.android.archive.pipeline

import android.content.Context
import android.net.Uri
import android.util.Log
import com.alibaba.mnnllm.android.archive.data.ArchiveRecord
import com.alibaba.mnnllm.android.archive.ocr.OcrEngine
import com.alibaba.mnnllm.android.archive.ocr.OcrModelInstaller
import java.io.File

/**
 * Orchestrates the real V1.0 pipeline: image -> PP-OCRv6 -> Qwen structuring -> ArchiveRecord.
 * Material files are copied into the app private dir so they survive and are deletable on demand.
 */
class ArchivePipeline(private val context: Context) {

    @Volatile private var ocr: OcrEngine? = null

    private fun ocrEngine(): OcrEngine {
        ocr?.let { return it }
        synchronized(this) {
            ocr?.let { return it }
            val dir = OcrModelInstaller.ensureInstalled(context)
            val e = OcrEngine.create(dir)
            ocr = e
            return e
        }
    }

    /** Copies a picked image uri into private materials dir; returns the saved file path. */
    fun persistMaterial(uri: Uri, ext: String): String {
        val dir = File(context.filesDir, "materials").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}_${(0..9999).random()}.$ext")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }

    /** Runs OCR only (fast path / preview). */
    fun runOcr(imagePath: String): String {
        return ocrEngine().recognize(imagePath)
    }

    /**
     * Full pipeline for a single image. [structuringEngine] must be loaded (Qwen).
     * sourceType: photo / chat / invoice / card
     */
    fun process(
        imagePath: String,
        sourceType: String,
        structuringEngine: StructuringEngine
    ): ArchiveRecord {
        val text = ocrEngine().recognize(imagePath)
        Log.d(TAG, "OCR text: $text")
        val record = if (text.isBlank()) {
            ArchiveRecord(
                sourceType = sourceType,
                rawText = "",
                summary = "未识别到文字",
                lowConfidenceFields = listOf("summary", "action_items"),
                createdAt = System.currentTimeMillis()
            )
        } else {
            structuringEngine.structure(text, sourceType)
        }
        record.materialPath = imagePath
        return record
    }

    /** Structure already-transcribed text (used by audio path V1.1). */
    fun processText(
        rawText: String,
        sourceType: String,
        structuringEngine: StructuringEngine,
        materialPath: String?
    ): ArchiveRecord {
        val record = if (rawText.isBlank()) {
            ArchiveRecord(sourceType = sourceType, summary = "未识别到内容",
                lowConfidenceFields = listOf("summary"), createdAt = System.currentTimeMillis())
        } else {
            structuringEngine.structure(rawText, sourceType)
        }
        record.materialPath = materialPath
        return record
    }

    fun release() {
        ocr?.release()
        ocr = null
    }

    companion object { private const val TAG = "ArchivePipeline" }
}
