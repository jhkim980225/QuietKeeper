#pragma once
#include <functional>
#include <aaudio/AAudio.h>
namespace engine {
// Owns an AAudio INPUT stream (UNPROCESSED, 48kHz, mono, float PCM, low-latency)
// and forwards each callback buffer to a sink. Real-time: the sink runs on the
// AAudio callback thread, so it must not allocate/block (AudioEngine::process satisfies this).
class AAudioSource {
public:
    using Sink = std::function<void(const float* data, int numFrames)>;
    explicit AAudioSource(Sink sink);
    ~AAudioSource();
    AAudioSource(const AAudioSource&) = delete;
    AAudioSource& operator=(const AAudioSource&) = delete;
    bool start();   // opens + starts; returns false on failure or unexpected format
    void stop();
    bool isRunning() const { return stream_ != nullptr; }
private:
    static aaudio_data_callback_result_t onAudioStatic(AAudioStream*, void* userData, void* audioData, int32_t numFrames);
    Sink sink_;
    AAudioStream* stream_ = nullptr;
};
}
