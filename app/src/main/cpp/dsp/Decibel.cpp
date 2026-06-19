#include "dsp/Decibel.h"
#include <cmath>
namespace dsp {
    float rms(const float* s, size_t n) {
        if (n == 0) return 0.0f;
        double acc = 0.0;
        for (size_t i = 0; i < n; ++i) acc += static_cast<double>(s[i]) * s[i];
        return static_cast<float>(std::sqrt(acc / n));
    }
    float toDb(float v, float offset) {
        if (v <= 1e-12f) return -120.0f;
        return static_cast<float>(20.0 * std::log10(v)) + offset;
    }
}
