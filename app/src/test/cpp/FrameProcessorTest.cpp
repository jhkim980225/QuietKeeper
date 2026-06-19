#include <gtest/gtest.h>
#include <vector>
#include <cmath>
#include "dsp/FrameProcessor.h"

static constexpr double kPi = 3.14159265358979323846;
static std::vector<float> sine(double f, size_t n) {
    std::vector<float> s(n);
    for (size_t i = 0; i < n; ++i) s[i] = 0.5f*std::sin(2.0*kPi*f*i/48000.0);
    return s;
}
TEST(FrameProcessor, ReturnsFrameDbForOneFrame) {
    dsp::FrameProcessor fp(48000, 6000, /*offset*/94.0f);
    auto s = sine(1000, 6000);
    dsp::FrameResult r = fp.pushFrame(s.data(), 6000);
    EXPECT_GT(r.db, 80.0f);
    EXPECT_NEAR(r.lmax, r.db, 0.01f);
}
TEST(FrameProcessor, LmaxTracksLoudestFrame) {
    dsp::FrameProcessor fp(48000, 6000, 94.0f);
    fp.pushFrame(sine(1000,6000).data(), 6000);
    auto quiet = sine(1000,6000); for (auto& x: quiet) x*=0.01f;
    dsp::FrameResult r = fp.pushFrame(quiet.data(), 6000);
    EXPECT_LT(r.db, r.lmax);
}
TEST(FrameProcessor, LeqBetweenQuietAndLoud) {
    dsp::FrameProcessor fp(48000, 6000, 94.0f);
    float loud = fp.pushFrame(sine(1000,6000).data(),6000).db;
    auto quiet = sine(1000,6000); for (auto& x: quiet) x*=0.05f;
    float q = fp.pushFrame(quiet.data(),6000).db;
    float leq = fp.leq();
    EXPECT_GT(leq, q);
    EXPECT_LT(leq, loud);
}
