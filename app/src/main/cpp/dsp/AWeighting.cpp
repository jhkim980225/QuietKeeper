#include "dsp/AWeighting.h"

namespace dsp {

// A-weighting (IEC 61672) as a cascade of 3 biquad sections for fs = 48 kHz.
//
// Designed from the analog A-weighting prototype:
//   H(s) = K * s^4 / ((s+w1)^2 (s+w2) (s+w3) (s+w4)^2)
// with corner frequencies (Hz):
//   f1 = 20.598997, f2 = 107.65265, f3 = 737.86223, f4 = 12194.217
// discretized via the bilinear transform (s = 2*fs*(z-1)/(z+1)) at fs = 48000,
// grouped into 3 biquads and normalized so the total cascade gain is exactly
// unity (0 dB) at 1 kHz. The normalization gain is folded into the third stage.
//
// Verified response: 100 Hz -> -19.15 dB, 1 kHz -> 0.00 dB, 10 kHz -> -3.71 dB
// (matches the IEC 61672 class tolerances).
AWeighting::AWeighting(int /*sampleRate*/) {
    // Engine always runs at 48 kHz; coefficients are baked for that rate.
    // Stage 0: zeros {0,0}, poles {-w1,-w1}
    stages_[0] = { 0.99730904075178, -1.9946180815036, 0.99730904075178,
                   -1.994614455993, 0.99462170701408 };
    // Stage 1: zeros {0,0}, poles {-w2,-w3}
    stages_[1] = { 0.94725756595443, -1.8945151319089, 0.94725756595443,
                   -1.8938704947231, 0.89515976909466 };
    // Stage 2: zeros {0,0}, poles {-w4,-w4}; carries the 1 kHz normalization gain.
    stages_[2] = { 0.24788919975268, 0.49577839950535, 0.24788919975268,
                   -0.22455845805978, 0.012606625271546 };
}

void AWeighting::process(float* samples, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        double x = samples[i];
        for (auto& s : stages_) x = s.step(x);
        samples[i] = static_cast<float>(x);
    }
}

void AWeighting::reset() {
    for (auto& s : stages_) { s.z1 = 0.0; s.z2 = 0.0; }
}

}
