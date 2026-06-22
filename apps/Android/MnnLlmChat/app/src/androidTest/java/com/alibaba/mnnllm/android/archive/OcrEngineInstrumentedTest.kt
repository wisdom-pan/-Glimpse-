package com.alibaba.mnnllm.android.archive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alibaba.mnnllm.android.archive.ocr.OcrEngine
import com.alibaba.mnnllm.android.archive.ocr.OcrModelInstaller
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device end-to-end test of the real PP-OCRv6 native pipeline (no mock).
 * Installs the bundled models, runs detection+recognition on a Chinese test image,
 * and asserts that real (non-empty, contains-Chinese) text is recognized.
 */
@RunWith(AndroidJUnit4::class)
class OcrEngineInstrumentedTest {

    @Test
    fun recognizesChineseText() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        // copy test image (from androidTest assets) to a real file path
        val img = File(ctx.cacheDir, "ocr_test.png")
        testCtx.assets.open("ocr_test.png").use { input ->
            img.outputStream().use { input.copyTo(it) }
        }

        val modelDir = OcrModelInstaller.ensureInstalled(ctx)
        val engine = OcrEngine.create(modelDir)
        val text = engine.recognize(img.absolutePath)
        engine.release()

        android.util.Log.i("OcrEngineTest", "recognized=[$text]")
        assertTrue("OCR returned empty text", text.isNotBlank())
        // expect at least one CJK character
        val hasCjk = text.any { it.code in 0x4E00..0x9FFF }
        assertTrue("OCR returned no Chinese characters: $text", hasCjk)
    }
}
