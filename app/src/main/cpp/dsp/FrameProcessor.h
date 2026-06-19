#pragma once
#include <cstddef>
#include <vector>
#include "dsp/AWeighting.h"
namespace dsp {
struct FrameResult { float db; float leq; float lmax; };
class FrameProcessor {
public:
    FrameProcessor(int sampleRate, int frameSize, float calibrationOffset);
    FrameResult pushFrame(const float* samples, size_t n); // A-weight -> RMS -> dB(A); updates Leq/Lmax
    float leq() const;
    float lmax() const { return lmax_; }
    void reset();
private:
    AWeighting weight_;
    float offset_;
    double energyAcc_ = 0.0;   // sum of linear energies (10^(db/10))
    long frameCount_ = 0;
    float lmax_ = -120.0f;
    std::vector<float> scratch_;
};
}
