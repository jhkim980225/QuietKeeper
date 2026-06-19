#pragma once
#include <string>
#include <cstddef>
namespace io {
    // float[-1..1] -> 16-bit PCM mono WAV. Returns true on success.
    bool writeWav(const std::string& path, const float* samples, size_t n, int sampleRate);
}
