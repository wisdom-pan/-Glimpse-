//
//  ocr_engine.h
//  Local Archive Mate - PP-OCRv6 detection + recognition pipeline (real, no mock)
//
#ifndef ARCHIVE_OCR_ENGINE_H
#define ARCHIVE_OCR_ENGINE_H

#include <string>
#include <vector>
#include <memory>
#include <MNN/expr/Module.hpp>
#include <MNN/expr/Executor.hpp>

namespace archive {

struct OcrLine {
    std::string text;
    float score = 0.f;            // recognition confidence (mean over decoded chars)
    float boxScore = 0.f;         // detection box score
    float points[8] = {0};        // 4 corner points in original image coords: x0,y0,...x3,y3
};

struct OcrResult {
    std::vector<OcrLine> lines;
    std::string fullText;         // lines joined by '\n'
};

class OcrEngine {
public:
    // Loads det/rec MNN models + character dictionary. Returns nullptr on failure.
    static OcrEngine* create(const std::string& detPath,
                             const std::string& recPath,
                             const std::string& keysPath);
    ~OcrEngine();

    // Runs the full pipeline on an image file. Throws std::runtime_error on hard failure.
    OcrResult run(const std::string& imagePath);

    // Runs the pipeline on a decoded RGB buffer (row-major, 3 channels, no padding).
    // Used on Android where BitmapFactory decodes the image reliably (incl. HEIC/Exif).
    OcrResult runRgb(const uint8_t* rgb, int width, int height);

private:
    OcrEngine() = default;
    bool load(const std::string& detPath, const std::string& recPath, const std::string& keysPath);
    OcrResult runOnImage(MNN::Express::VARP bgr);  // shared core

    std::shared_ptr<MNN::Express::Executor::RuntimeManager> mRuntime;
    std::shared_ptr<MNN::Express::Module> mDet;
    std::shared_ptr<MNN::Express::Module> mRec;
    std::vector<std::string> mKeys;   // [blank] + chars + " "
};

} // namespace archive

#endif // ARCHIVE_OCR_ENGINE_H
