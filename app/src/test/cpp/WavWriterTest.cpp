#include <gtest/gtest.h>
#include <vector>
#include <cstdio>
#include <cstdint>
#include <string>
#include "io/WavWriter.h"

static uint32_t le32(const unsigned char* p){ return p[0] | (p[1]<<8) | (p[2]<<16) | ((uint32_t)p[3]<<24); }

TEST(WavWriter, WritesValidHeader) {
    std::vector<float> pcm(48000, 0.5f); // 1 second
    std::string path = "/data/local/tmp/wavtest.wav";
    ASSERT_TRUE(io::writeWav(path, pcm.data(), pcm.size(), 48000));

    FILE* f = fopen(path.c_str(), "rb");
    ASSERT_NE(f, nullptr);
    unsigned char h[44];
    ASSERT_EQ(fread(h,1,44,f), 44u);
    fclose(f);
    EXPECT_EQ(std::string(h,h+4), "RIFF");
    EXPECT_EQ(std::string(h+8,h+12), "WAVE");
    EXPECT_EQ(le32(h+24), 48000u);        // sample rate
    EXPECT_EQ(h[34], 16);                 // bits per sample
    EXPECT_EQ(le32(h+40), 48000u*2u);     // data bytes = samples * 2

    FILE* fz = fopen(path.c_str(), "rb"); ASSERT_NE(fz, nullptr);
    fseek(fz, 0, SEEK_END);
    EXPECT_EQ(ftell(fz), 44 + (long)(48000 * 2));
    fclose(fz);
}

TEST(WavWriter, ClampsAndScalesSample) {
    std::vector<float> pcm = { 1.0f, -1.0f, 2.0f, 0.0f }; // 2.0 must clamp to +full-scale
    std::string path = "/data/local/tmp/wavtest2.wav";
    ASSERT_TRUE(io::writeWav(path, pcm.data(), pcm.size(), 48000));
    FILE* f = fopen(path.c_str(), "rb"); ASSERT_NE(f, nullptr);
    unsigned char buf[44 + 8]; ASSERT_EQ(fread(buf,1,sizeof(buf),f), sizeof(buf)); fclose(f);
    int16_t s0 = (int16_t)(buf[44] | (buf[45]<<8));   // 1.0 -> 32767
    int16_t s2 = (int16_t)(buf[48] | (buf[49]<<8));   // 2.0 clamped -> 32767
    EXPECT_EQ(s0, 32767);
    EXPECT_EQ(s2, 32767);
    int16_t s1 = (int16_t)(buf[46] | (buf[47]<<8));   // -1.0 -> -32767
    EXPECT_EQ(s1, -32767);
}

TEST(WavWriter, ReturnsFalseOnBadPath) {
    std::vector<float> pcm(10, 0.0f);
    EXPECT_FALSE(io::writeWav("/no/such/dir/x.wav", pcm.data(), pcm.size(), 48000));
}
