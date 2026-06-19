#include <gtest/gtest.h>
#include <vector>
#include <cmath>
#include "dsp/AWeighting.h"
#include "dsp/Decibel.h"

static constexpr double kPi = 3.14159265358979323846;
static std::vector<float> sine(double f, size_t n) {
    std::vector<float> s(n);
    for (size_t i = 0; i < n; ++i) s[i] = std::sin(2.0 * kPi * f * i / 48000.0);
    return s;
}
// RMS dB of the steady-state second half after filtering in-place
static float steadyDb(dsp::AWeighting& w, std::vector<float> s) {
    w.process(s.data(), s.size());
    return dsp::toDb(dsp::rms(s.data() + s.size()/2, s.size()/2), 0.0f);
}

TEST(AWeighting, UnityAt1kHz) {
    dsp::AWeighting w(48000);
    auto in = sine(1000, 48000);
    float inDb = dsp::toDb(dsp::rms(in.data(), in.size()), 0.0f);
    float outDb = steadyDb(w, sine(1000, 48000));
    EXPECT_NEAR(outDb - inDb, 0.0f, 0.7f);   // 1 kHz ~ 0 dB
}
TEST(AWeighting, Attenuates100Hz) {
    dsp::AWeighting w(48000);
    auto in = sine(100, 48000);
    float inDb = dsp::toDb(dsp::rms(in.data(), in.size()), 0.0f);
    float outDb = steadyDb(w, sine(100, 48000));
    EXPECT_LT(outDb - inDb, -15.0f);          // 100 Hz strongly attenuated
}
