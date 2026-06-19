#pragma once
#include <cstddef>
namespace dsp {
    float rms(const float* samples, size_t n);
    // dBFS + calibration offset. Inputs below 1e-6 linear (-120 dBFS) are clamped to the -120 floor.
    float toDb(float rmsValue, float calibrationOffset);
}
