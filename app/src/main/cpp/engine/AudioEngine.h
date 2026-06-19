#pragma once
#include <array>
#include <atomic>
#include <functional>
#include <memory>
#include <string>
#include <thread>
#include <vector>
#include "dsp/FrameProcessor.h"
#include "buffer/RingBuffer.h"

namespace engine {
struct Metrics { float db = -120.f, leq = -120.f, lmax = -120.f; };

class AudioEngine {
public:
    using EventCallback = std::function<void(const std::string& wavPath, float peakDb, float leq)>;
    AudioEngine(std::string outDir, float calibrationOffset, float thresholdDb, EventCallback cb);
    ~AudioEngine();
    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    // Feed PCM (any block size). Audio-thread safe: no alloc, no I/O.
    void process(const float* data, int numFrames);
    Metrics latest() const;
    void setThreshold(float db) { threshold_.store(db); }

    // Test helper: block (with timeout) until the save worker has drained all READY/PROCESSING slots.
    void drainForTest(int timeoutMs = 3000);

    static constexpr int kSampleRate = 48000;
    static constexpr int kFrame = 6000;                 // 0.125 s
    static constexpr int kRing = kSampleRate * 30;      // 30 s
    static constexpr int kPre = kSampleRate * 5;        // 5 s
    static constexpr int kPost = kSampleRate * 5;       // 5 s

private:
    struct Slot {
        std::vector<float> buf;          // pre-sized to kPre+kPost
        std::atomic<int> state{0};       // 0 FREE, 1 READY, 2 PROCESSING
        size_t count = 0;
        float peak = -120.f, leq = -120.f;
        long seq = 0;
    };
    static constexpr int kPoolSize = 3;

    void workerLoop();
    void triggerIfNeeded(float frameDb);
    void enqueueSave();

    std::string outDir_;
    float offset_;
    std::atomic<float> threshold_;
    EventCallback cb_;
    std::unique_ptr<dsp::FrameProcessor> proc_;
    std::unique_ptr<buffer::RingBuffer> ring_;
    std::vector<float> frameAccum_;      // grows to kFrame then clears (allocated once, reused)

    std::atomic<float> mDb_{-120.f}, mLeq_{-120.f}, mLmax_{-120.f};

    // event capture state (audio thread only)
    bool capturingPost_ = false;
    int postRemaining_ = 0;
    float eventPeak_ = -120.f;
    double eventEnergyAcc_ = 0.0;   // per-event Leq accumulator
    long eventFrames_ = 0;
    long eventSeq_ = 0;
    std::atomic<long> droppedEvents_{0};

    std::array<Slot, kPoolSize> pool_;
    std::thread worker_;
    std::atomic<bool> running_{false};
};
}
