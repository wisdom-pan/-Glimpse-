//
//  ocr_jni.cpp
//  JNI bridge for the PP-OCRv6 OCR engine.
//
#include <jni.h>
#include <string>
#include <memory>

#include "ocr_engine.h"

#ifdef __ANDROID__
#include <android/log.h>
#define OCRJNI_LOG(...) __android_log_print(ANDROID_LOG_INFO, "ArchiveOCRJni", __VA_ARGS__)
#else
#define OCRJNI_LOG(...)
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_alibaba_mnnllm_android_archive_ocr_OcrEngine_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jstring detPath, jstring recPath, jstring keysPath) {
    const char* det = env->GetStringUTFChars(detPath, nullptr);
    const char* rec = env->GetStringUTFChars(recPath, nullptr);
    const char* keys = env->GetStringUTFChars(keysPath, nullptr);
    archive::OcrEngine* engine = archive::OcrEngine::create(det, rec, keys);
    env->ReleaseStringUTFChars(detPath, det);
    env->ReleaseStringUTFChars(recPath, rec);
    env->ReleaseStringUTFChars(keysPath, keys);
    if (engine == nullptr) {
        jclass ex = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(ex, "OCR engine init failed: check model/keys paths");
        return 0;
    }
    return reinterpret_cast<jlong>(engine);
}

// Runs OCR and returns the recognized text. If detailed==true, returns a JSON-ish
// multi-line string with per-box coords/score; otherwise returns plain joined text.
JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_archive_ocr_OcrEngine_nativeRun(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring imagePath, jboolean detailed) {
    auto* engine = reinterpret_cast<archive::OcrEngine*>(handle);
    if (engine == nullptr) {
        return env->NewStringUTF("");
    }
    const char* img = env->GetStringUTFChars(imagePath, nullptr);
    std::string out;
    try {
        archive::OcrResult r = engine->run(img);
        if (detailed) {
            std::string json = "[";
            for (size_t i = 0; i < r.lines.size(); ++i) {
                const auto& ln = r.lines[i];
                if (i) json += ",";
                // escape quotes/backslashes in text
                std::string esc;
                for (char c : ln.text) {
                    if (c == '"' || c == '\\') esc += '\\';
                    esc += c;
                }
                json += "{\"text\":\"" + esc + "\",\"score\":" + std::to_string(ln.score) + "}";
            }
            json += "]";
            out = json;
        } else {
            out = r.fullText;
        }
    } catch (const std::exception& e) {
        OCRJNI_LOG("ocr run error: %s", e.what());
        env->ReleaseStringUTFChars(imagePath, img);
        jclass ex = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(ex, e.what());
        return nullptr;
    }
    env->ReleaseStringUTFChars(imagePath, img);
    return env->NewStringUTF(out.c_str());
}

// Runs OCR on a decoded RGB byte buffer (width*height*3). Returns recognized full text.
JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_archive_ocr_OcrEngine_nativeRunRgb(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jbyteArray rgb, jint width, jint height) {
    auto* engine = reinterpret_cast<archive::OcrEngine*>(handle);
    if (engine == nullptr) return env->NewStringUTF("");
    jbyte* buf = env->GetByteArrayElements(rgb, nullptr);
    std::string out;
    try {
        archive::OcrResult r = engine->runRgb(reinterpret_cast<const uint8_t*>(buf), width, height);
        out = r.fullText;
    } catch (const std::exception& e) {
        OCRJNI_LOG("ocr runRgb error: %s", e.what());
        env->ReleaseByteArrayElements(rgb, buf, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(ex, e.what());
        return nullptr;
    }
    env->ReleaseByteArrayElements(rgb, buf, JNI_ABORT);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_archive_ocr_OcrEngine_nativeRelease(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* engine = reinterpret_cast<archive::OcrEngine*>(handle);
    delete engine;
}

} // extern "C"
