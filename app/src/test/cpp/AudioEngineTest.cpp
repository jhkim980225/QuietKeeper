#include <gtest/gtest.h>
#include <vector>
#include <cmath>
#include <cstdio>
#include <string>
#include <atomic>
#include "engine/AudioEngine.h"

static constexpr double kPi = 3.14159265358979323846;
static void fillSine(std::vector<float>& v, double f, double amp) {
    for (size_t i = 0; i < v.size(); ++i) v[i] = (float)(amp*std::sin(2.0*kPi*f*i/48000.0));
}

TEST(AudioEngine, LoudInputTriggersSavedWavAndCallback) {
    std::atomic<int> events{0};
    std::string gotPath; float gotPeak = 0;
    engine::AudioEngine eng("/data/local/tmp", /*offset*/94.0f, /*threshold*/55.0f,
        [&](const std::string& p, float peak, float leq){ events++; gotPath = p; gotPeak = peak; });

    // Feed enough loud audio: at least one frame to trigger + kPost samples to complete the window.
    const int total = engine::AudioEngine::kPre + engine::AudioEngine::kPost + engine::AudioEngine::kFrame;
    std::vector<float> block(4800); // 0.1s blocks
    fillSine(block, 1000.0, 0.5); // ~85 dB(A) with +94 offset >> 55 threshold
    int fed = 0;
    while (fed < total) { eng.process(block.data(), (int)block.size()); fed += (int)block.size(); }

    eng.drainForTest();
    EXPECT_GE(events.load(), 1);
    ASSERT_FALSE(gotPath.empty());
    FILE* f = fopen(gotPath.c_str(), "rb"); ASSERT_NE(f, nullptr);
    fseek(f, 0, SEEK_END); long sz = ftell(f); fclose(f);
    EXPECT_GT(sz, 44 + 100000*2);     // a multi-second clip was written
    EXPECT_GT(gotPeak, 70.0f);         // event peak is loud
}

TEST(AudioEngine, QuietInputProducesNoEvent) {
    std::atomic<int> events{0};
    engine::AudioEngine eng("/data/local/tmp", 94.0f, 55.0f,
        [&](const std::string&, float, float){ events++; });
    std::vector<float> block(48000);
    fillSine(block, 1000.0, 0.0005); // ~28 dB(A) << 55
    for (int k = 0; k < 12; ++k) eng.process(block.data(), (int)block.size());
    eng.drainForTest(500);
    EXPECT_EQ(events.load(), 0);
}

TEST(AudioEngine, LatestReflectsLevel) {
    engine::AudioEngine eng("/data/local/tmp", 94.0f, 200.0f /*never trigger*/,
        [](const std::string&, float, float){});
    std::vector<float> block(6000);
    fillSine(block, 1000.0, 0.5);
    eng.process(block.data(), (int)block.size());
    EXPECT_GT(eng.latest().db, 70.0f);
}
