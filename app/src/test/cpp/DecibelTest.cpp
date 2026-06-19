#include <gtest/gtest.h>
#include <vector>
#include <cmath>
#include "dsp/Decibel.h"

constexpr double kPi = 3.14159265358979323846;

TEST(Decibel, RmsOfConstant) {
    std::vector<float> s(100, 0.5f);
    EXPECT_NEAR(dsp::rms(s.data(), s.size()), 0.5f, 1e-6);
}
TEST(Decibel, FullScaleSineIsAboutMinus3dBFS) {
    std::vector<float> s(4800);
    for (size_t i = 0; i < s.size(); ++i)
        s[i] = std::sin(2.0 * kPi * 1000.0 * i / 48000.0);
    float db = dsp::toDb(dsp::rms(s.data(), s.size()), 0.0f);
    EXPECT_NEAR(db, -3.01f, 0.1f);
}
TEST(Decibel, CalibrationOffsetAdds) {
    float base = dsp::toDb(0.1f, 0.0f);
    EXPECT_NEAR(dsp::toDb(0.1f, 20.0f), base + 20.0f, 1e-4);
}
TEST(Decibel, SilenceClampsToFloor) {
    EXPECT_LE(dsp::toDb(0.0f, 0.0f), -120.0f);
}
TEST(Decibel, NearSilenceClampsToFloor) {
    EXPECT_FLOAT_EQ(dsp::toDb(1e-8f, 0.0f), -120.0f);
}
