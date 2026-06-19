#include "dsp/Decibel.h"
#include <cmath>
namespace dsp {
    float rms(const float* samples, size_t n) {
        if (n == 0) return 0.0f;
        double acc = 0.0;
        for (size_t i = 0; i < n; ++i) acc += static_cast<double>(samples[i]) * samples[i];
        return static_cast<float>(std::sqrt(acc / n));
    }
    float toDb(float v, float offset) {
        // -120 dBFS floor: full-scale = 1.0, so 1e-6 linear == -120 dBFS.
        float dbfs = (v <= 1e-6f) ? -120.0f
                                  : static_cast<float>(20.0 * std::log10(static_cast<double>(v)));
        return dbfs + offset;
    }
}
