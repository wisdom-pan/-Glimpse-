//
//  ocr_engine.cpp
//  Local Archive Mate - PP-OCRv6 detection (DBNet) + recognition (CRNN+CTC).
//  Parameters calibrated against the validated host PyMNN reference pipeline.
//
#include "ocr_engine.h"

#include <algorithm>
#include <cmath>
#include <fstream>
#include <sstream>
#include <stdexcept>

#include <MNN/expr/ExprCreator.hpp>
#include <cv/cv.hpp>

#ifdef __ANDROID__
#include <android/log.h>
#define OCR_LOG(...) __android_log_print(ANDROID_LOG_INFO, "ArchiveOCR", __VA_ARGS__)
#else
#include <cstdio>
#define OCR_LOG(...) do { printf(__VA_ARGS__); printf("\n"); } while (0)
#endif

using namespace MNN;
using namespace MNN::Express;
using namespace MNN::CV;

namespace archive {

// ---- Hyper-parameters (match ocr_validate.py) ----
static const int   kDetLimit    = 960;   // max long side for detection
static const float kDetThresh   = 0.3f;  // probability map binarization
static const float kBoxThresh   = 0.6f;  // min box score
static const float kUnclipRatio = 1.5f;  // DB unclip
static const int   kMinSize     = 3;     // min box short side
static const int   kRecHeight   = 48;    // recognition input height

OcrEngine::~OcrEngine() = default;

OcrEngine* OcrEngine::create(const std::string& detPath,
                             const std::string& recPath,
                             const std::string& keysPath) {
    auto* engine = new OcrEngine();
    if (!engine->load(detPath, recPath, keysPath)) {
        delete engine;
        return nullptr;
    }
    return engine;
}

bool OcrEngine::load(const std::string& detPath, const std::string& recPath, const std::string& keysPath) {
    // PP-OCR dictionary convention: index 0 = CTC blank, chars from 1, trailing space class.
    mKeys.clear();
    mKeys.emplace_back("");  // [blank]
    std::ifstream fin(keysPath);
    if (!fin.is_open()) {
        OCR_LOG("failed to open keys: %s", keysPath.c_str());
        return false;
    }
    std::string line;
    while (std::getline(fin, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        mKeys.push_back(line);
    }
    mKeys.emplace_back(" ");  // trailing space class
    OCR_LOG("keys loaded: %zu", mKeys.size());

    ScheduleConfig sconfig;
    sconfig.type = MNN_FORWARD_CPU;
    sconfig.numThread = 4;
    BackendConfig bnConfig;
    bnConfig.precision = BackendConfig::Precision_High;
    sconfig.backendConfig = &bnConfig;
    mRuntime.reset(Executor::RuntimeManager::createRuntimeManager(sconfig));
    if (mRuntime == nullptr) {
        OCR_LOG("create runtime failed");
        return false;
    }

    Module::Config mconfig;
    mconfig.shapeMutable = true;  // det/rec take dynamic input sizes
    mDet.reset(Module::load({"x"}, {"fetch_name_0"}, detPath.c_str(), mRuntime, &mconfig));
    mRec.reset(Module::load({"x"}, {"fetch_name_0"}, recPath.c_str(), mRuntime, &mconfig));
    if (mDet == nullptr || mRec == nullptr) {
        OCR_LOG("load module failed det=%p rec=%p", (void*)mDet.get(), (void*)mRec.get());
        return false;
    }
    return true;
}

// NHWC uint8 [H,W,C] -> normalized NCHW float [1,C,H,W]; mean/norm applied as (p-mean)*norm.
static VARP toNchw(VARP nhwcU8, const std::vector<float>& mean, const std::vector<float>& norm) {
    auto f = _Cast<float>(nhwcU8);          // [H,W,C]
    f = _Unsqueeze(f, {0});                  // [1,H,W,C]
    auto meanV = _Const(mean.data(), {1, 1, 1, (int)mean.size()}, NHWC);
    auto normV = _Const(norm.data(), {1, 1, 1, (int)norm.size()}, NHWC);
    f = (f - meanV) * normV;                 // NHWC, broadcast over channel
    f = _Convert(f, NC4HW4);
    f = _Convert(f, NCHW);                    // [1,C,H,W]
    return f;
}

struct OrderedBox {
    float pts[8];      // tl,tr,br,bl (x,y)
    float score;
    float minY, minX;  // for sorting
};

static void orderQuad(const float* in /*4x2*/, float* out /*tl,tr,br,bl*/) {
    int tl = 0, br = 0, tr = 0, bl = 0;
    float minSum = 1e18f, maxSum = -1e18f, minDiff = 1e18f, maxDiff = -1e18f;
    for (int i = 0; i < 4; ++i) {
        float x = in[i * 2], y = in[i * 2 + 1];
        float s = x + y, d = y - x;
        if (s < minSum) { minSum = s; tl = i; }
        if (s > maxSum) { maxSum = s; br = i; }
        if (d < minDiff) { minDiff = d; tr = i; }
        if (d > maxDiff) { maxDiff = d; bl = i; }
    }
    int idx[4] = {tl, tr, br, bl};
    for (int i = 0; i < 4; ++i) { out[i * 2] = in[idx[i] * 2]; out[i * 2 + 1] = in[idx[i] * 2 + 1]; }
}

OcrResult OcrEngine::run(const std::string& imagePath) {
    VARP bgr = imread(imagePath);  // NHWC uint8 BGR [H,W,3]
    if (bgr == nullptr) {
        throw std::runtime_error("OCR: cannot read image " + imagePath);
    }
    return runOnImage(bgr);
}

OcrResult OcrEngine::runRgb(const uint8_t* rgb, int width, int height) {
    if (rgb == nullptr || width <= 0 || height <= 0) {
        throw std::runtime_error("OCR: invalid rgb buffer");
    }
    // Build a BGR NHWC uint8 VARP from the RGB bytes (swap R/B to match imread's BGR).
    std::vector<uint8_t> bgrData((size_t)width * height * 3);
    const size_t n = (size_t)width * height;
    for (size_t i = 0; i < n; ++i) {
        bgrData[i * 3 + 0] = rgb[i * 3 + 2];
        bgrData[i * 3 + 1] = rgb[i * 3 + 1];
        bgrData[i * 3 + 2] = rgb[i * 3 + 0];
    }
    VARP bgr = _Const(bgrData.data(), {height, width, 3}, NHWC, halide_type_of<uint8_t>());
    return runOnImage(bgr);
}

OcrResult OcrEngine::runOnImage(VARP bgr) {
    OcrResult result;
    int oh = getVARPHeight(bgr), ow = getVARPWidth(bgr);
    if (oh <= 0 || ow <= 0) throw std::runtime_error("OCR: invalid image dims");

    // ---- Detection preprocess: ratio resize, 32-aligned ----
    float scale = 1.0f;
    int maxSide = std::max(oh, ow);
    if (maxSide > kDetLimit) scale = (float)kDetLimit / maxSide;
    int nh = std::max(32, (int)std::lround(oh * scale + 0.0f));
    int nw = std::max(32, (int)std::lround(ow * scale + 0.0f));
    nh = std::max(32, (nh + 31) / 32 * 32);
    nw = std::max(32, (nw + 31) / 32 * 32);

    // resize with BGR->RGB; mean/norm applied in toNchw to keep uint8 resize fast.
    VARP detResized = resize(bgr, Size(nw, nh), 0, 0, INTER_LINEAR, COLOR_BGR2RGB);  // NHWC uint8 RGB
    if (detResized == nullptr) throw std::runtime_error("OCR: det resize failed");
    std::vector<float> detMean = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};
    std::vector<float> detNorm = {1.f / (0.229f * 255.f), 1.f / (0.224f * 255.f), 1.f / (0.225f * 255.f)};
    VARP detIn = toNchw(detResized, detMean, detNorm);
    if (detIn == nullptr) throw std::runtime_error("OCR: det preprocess failed");
    OCR_LOG("det input %dx%d ready", nw, nh);

    auto detOutputs = mDet->onForward({detIn});
    if (detOutputs.empty() || detOutputs[0] == nullptr) {
        throw std::runtime_error("OCR: detection forward returned empty");
    }
    VARP detOut = _Convert(detOutputs[0], NCHW);  // [1,1,nh,nw]
    const float* prob = detOut->readMap<float>();
    auto detInfo = detOut->getInfo();
    if (prob == nullptr || detInfo == nullptr || detInfo->size < (size_t)nh * nw) {
        throw std::runtime_error("OCR: detection output unexpected size");
    }
    OCR_LOG("det forward done, prob size %zu", detInfo->size);

    // ---- Binarize -> findContours (input must be uint8 [H,W,1]) ----
    std::vector<uint8_t> binData((size_t)nh * nw);
    for (size_t i = 0; i < binData.size(); ++i) binData[i] = prob[i] > kDetThresh ? 255 : 0;
    VARP binImg = _Const(binData.data(), {nh, nw, 1}, NHWC, halide_type_of<uint8_t>());
    auto contours = findContours(binImg, RETR_LIST, CHAIN_APPROX_SIMPLE);

    float rx = (float)ow / nw, ry = (float)oh / nh;
    std::vector<OrderedBox> boxes;
    for (auto& cnt : contours) {
        auto cInfo = cnt->getInfo();
        if (cInfo == nullptr || cInfo->size < 4 * 2) continue;  // need >=4 points
        RotatedRect rr = minAreaRect(cnt);
        float shortSide = std::min(rr.size.width, rr.size.height);
        if (shortSide < kMinSize) continue;

        VARP quad = boxPoints(rr);  // float [4,2] in detection coords
        const float* qp = quad->readMap<float>();

        // box score: mean prob over axis-aligned bbox of the quad
        float minx = qp[0], maxx = qp[0], miny = qp[1], maxy = qp[1];
        for (int i = 1; i < 4; ++i) {
            minx = std::min(minx, qp[i * 2]); maxx = std::max(maxx, qp[i * 2]);
            miny = std::min(miny, qp[i * 2 + 1]); maxy = std::max(maxy, qp[i * 2 + 1]);
        }
        int x0 = std::max(0, (int)minx), x1 = std::min(nw - 1, (int)maxx);
        int y0 = std::max(0, (int)miny), y1 = std::min(nh - 1, (int)maxy);
        if (x1 <= x0 || y1 <= y0) continue;
        double sum = 0; int cnt2 = 0;
        for (int y = y0; y <= y1; ++y)
            for (int x = x0; x <= x1; ++x) { sum += prob[(size_t)y * nw + x]; ++cnt2; }
        float boxScore = cnt2 > 0 ? (float)(sum / cnt2) : 0.f;
        if (boxScore < kBoxThresh) continue;

        // ---- DB unclip: expand the rotated rect by 2*dist on each side ----
        float area = rr.size.width * rr.size.height;
        float peri = 2.f * (rr.size.width + rr.size.height);
        if (peri <= 0) continue;
        float dist = area * kUnclipRatio / peri;
        RotatedRect expanded = rr;
        expanded.size.width  += 2.f * dist;
        expanded.size.height += 2.f * dist;
        VARP equad = boxPoints(expanded);
        const float* ep = equad->readMap<float>();

        float raw[8];
        for (int i = 0; i < 4; ++i) {
            raw[i * 2]     = std::min(std::max(ep[i * 2] * rx, 0.f), (float)(ow - 1));
            raw[i * 2 + 1] = std::min(std::max(ep[i * 2 + 1] * ry, 0.f), (float)(oh - 1));
        }
        OrderedBox ob;
        orderQuad(raw, ob.pts);
        ob.score = boxScore;
        ob.minY = std::min(std::min(ob.pts[1], ob.pts[3]), std::min(ob.pts[5], ob.pts[7]));
        ob.minX = std::min(std::min(ob.pts[0], ob.pts[2]), std::min(ob.pts[4], ob.pts[6]));
        boxes.push_back(ob);
    }

    // sort top->bottom, then left->right (line reading order)
    std::sort(boxes.begin(), boxes.end(), [](const OrderedBox& a, const OrderedBox& b) {
        if (std::abs(a.minY - b.minY) > 10) return a.minY < b.minY;
        return a.minX < b.minX;
    });

    // ---- Recognition per box ----
    std::vector<float> recMean = {127.5f, 127.5f, 127.5f};
    std::vector<float> recNorm = {1.f / 127.5f, 1.f / 127.5f, 1.f / 127.5f};
    std::ostringstream full;
    for (auto& ob : boxes) {
        // perspective-crop the quad to an axis-aligned patch
        float wTop = std::hypot(ob.pts[0] - ob.pts[2], ob.pts[1] - ob.pts[3]);
        float wBot = std::hypot(ob.pts[4] - ob.pts[6], ob.pts[5] - ob.pts[7]);
        float hLeft = std::hypot(ob.pts[0] - ob.pts[6], ob.pts[1] - ob.pts[7]);
        float hRight = std::hypot(ob.pts[2] - ob.pts[4], ob.pts[3] - ob.pts[5]);
        int cw = (int)std::lround(std::max(wTop, wBot));
        int ch = (int)std::lround(std::max(hLeft, hRight));
        if (cw < 2 || ch < 2) continue;

        Point src[4] = { {ob.pts[0], ob.pts[1]}, {ob.pts[2], ob.pts[3]},
                         {ob.pts[4], ob.pts[5]}, {ob.pts[6], ob.pts[7]} };
        Point dst[4] = { {0, 0}, {(float)cw, 0}, {(float)cw, (float)ch}, {0, (float)ch} };
        Matrix M = getPerspectiveTransform(src, dst);
        VARP crop = warpPerspective(bgr, M, Size(cw, ch), INTER_LINEAR);  // NHWC uint8 BGR

        // vertical text -> rotate 90 deg clockwise
        if ((float)ch >= 1.5f * cw) {
            crop = _Transpose(crop, {1, 0, 2});      // swap H,W
            crop = _Reverse(crop, _Scalar<int>(1));  // flip along new width -> 90 CW
            std::swap(cw, ch);
        }

        int rh = kRecHeight;
        int rw = std::max(1, (int)std::lround((float)kRecHeight * cw / ch));
        rw = std::max(32, (rw + 31) / 32 * 32);
        VARP recResized = resize(crop, Size(rw, rh), 0, 0, INTER_LINEAR, COLOR_BGR2RGB);
        VARP recIn = toNchw(recResized, recMean, recNorm);

        VARP recOut = mRec->onForward({recIn})[0];  // [1,T,C]
        recOut = _Convert(recOut, NCHW);
        auto rInfo = recOut->getInfo();
        if (rInfo == nullptr || rInfo->dim.size() < 3) continue;
        int T = rInfo->dim[1];
        int C = rInfo->dim[2];
        if ((size_t)C != mKeys.size()) {
            throw std::runtime_error("OCR: rec class dim " + std::to_string(C) +
                                     " != keys " + std::to_string(mKeys.size()));
        }
        const float* logits = recOut->readMap<float>();

        // CTC greedy decode: argmax per frame, skip blank(0) and repeats.
        std::string text;
        double confSum = 0; int confN = 0; int prev = -1;
        for (int t = 0; t < T; ++t) {
            const float* row = logits + (size_t)t * C;
            int best = 0; float bestv = row[0];
            for (int c = 1; c < C; ++c) if (row[c] > bestv) { bestv = row[c]; best = c; }
            if (best != 0 && best != prev) {
                text += mKeys[best];
                confSum += bestv; ++confN;
            }
            prev = best;
        }
        if (text.empty()) continue;
        OcrLine ln;
        ln.text = text;
        ln.score = confN > 0 ? (float)(confSum / confN) : 0.f;
        ln.boxScore = ob.score;
        for (int i = 0; i < 8; ++i) ln.points[i] = ob.pts[i];
        result.lines.push_back(ln);
        if (full.tellp() > 0) full << "\n";
        full << text;
    }
    result.fullText = full.str();
    OCR_LOG("ocr done: %zu lines", result.lines.size());
    return result;
}

} // namespace archive
