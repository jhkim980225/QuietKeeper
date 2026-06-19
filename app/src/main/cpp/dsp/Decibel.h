#pragma once
#include <cstddef>
namespace dsp {
    float rms(const float* samples, size_t n);
    // dBFS(+offset). rms<=0 (or ~0) returns floor -120.
    float toDb(float rmsValue, float calibrationOffset);
}
