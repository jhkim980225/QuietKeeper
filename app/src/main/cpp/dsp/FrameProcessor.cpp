#include "dsp/FrameProcessor.h"
#include "dsp/Decibel.h"
#include <cmath>
namespace dsp {
FrameProcessor::FrameProcessor(int, int frameSize, float offset)
    : weight_(48000), offset_(offset), scratch_(frameSize) {}

FrameResult FrameProcessor::pushFrame(const float* in, size_t n) {
    if (scratch_.size() < n) scratch_.resize(n);
    for (size_t i = 0; i < n; ++i) scratch_[i] = in[i];
    weight_.process(scratch_.data(), n);
    float r = rms(scratch_.data(), n);
    float db = toDb(r, offset_);
    energyAcc_ += std::pow(10.0, db / 10.0);
    frameCount_ += 1;
    if (db > lmax_) lmax_ = db;
    return { db, leq(), lmax_ };
}
float FrameProcessor::leq() const {
    if (frameCount_ == 0) return -120.0f;
    return static_cast<float>(10.0 * std::log10(energyAcc_ / frameCount_));
}
void FrameProcessor::reset() { weight_.reset(); energyAcc_ = 0; frameCount_ = 0; lmax_ = -120.0f; }
}
