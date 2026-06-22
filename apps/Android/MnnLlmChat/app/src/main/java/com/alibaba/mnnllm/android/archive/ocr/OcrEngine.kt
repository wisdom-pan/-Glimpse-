package com.alibaba.mnnllm.android.archive.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * Kotlin wrapper over the native PP-OCRv6 detection + recognition pipeline.
 * Image decoding is done with Android's BitmapFactory (reliable for JPEG/HEIC/PNG and Exif
 * orientation), then raw RGB is handed to native — avoiding MNN's image decoder entirely.
 */
class OcrEngine private constructor(private var handle: Long) {

    /** Returns the recognized full text (lines joined by newline). */
    fun recognize(imagePath: String): String {
        check(handle != 0L) { "OcrEngine already released" }
        val bmp = decodeOriented(imagePath) ?: return ""
        try {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val rgb = ByteArray(w * h * 3)
            var j = 0
            for (p in pixels) {
                rgb[j++] = ((p shr 16) and 0xFF).toByte() // R
                rgb[j++] = ((p shr 8) and 0xFF).toByte()  // G
                rgb[j++] = (p and 0xFF).toByte()          // B
            }
            return nativeRunRgb(handle, rgb, w, h) ?: ""
        } finally {
            bmp.recycle()
        }
    }

    private fun decodeOriented(path: String): Bitmap? {
        if (!File(path).exists()) return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        // apply Exif rotation
        val orientation = try {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }

    fun release() {
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0L
        }
    }

    private external fun nativeRunRgb(handle: Long, rgb: ByteArray, width: Int, height: Int): String?
    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("mnnllmapp")
        }

        @JvmStatic
        private external fun nativeInit(detPath: String, recPath: String, keysPath: String): Long

        /**
         * Creates an engine from a directory containing det.mnn / rec.mnn / keys.txt.
         * Throws IllegalStateException on failure (native side).
         */
        fun create(modelDir: String): OcrEngine {
            val det = "$modelDir/det.mnn"
            val rec = "$modelDir/rec.mnn"
            val keys = "$modelDir/keys.txt"
            val h = nativeInit(det, rec, keys)
            check(h != 0L) { "Failed to init OCR engine at $modelDir" }
            return OcrEngine(h)
        }
    }
}
