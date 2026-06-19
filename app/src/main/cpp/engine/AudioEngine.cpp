#include "engine/AudioEngine.h"

#include <chrono>
#include <cmath>
#include <string>

#include "io/WavWriter.h"

namespace engine {

AudioEngine::AudioEngine(std::string outDir, float calibrationOffset, float thresholdDb,
                         EventCallback cb)
    : outDir_(std::move(outDir)),
      offset_(calibrationOffset),
      threshold_(thresholdDb),
      cb_(std::move(cb)) {
    proc_ = std::make_unique<dsp::FrameProcessor>(kSampleRate, kFrame, offset_);
    ring_ = std::make_unique<buffer::RingBuffer>(kRing);
    frameAccum_.reserve(kFrame);
    for (auto& slot : pool_) {
        slot.buf.assign(kPre + kPost, 0.f);
    }
    running_.store(true);
    worker_ = std::thread(&AudioEngine::workerLoop, this);
}

AudioEngine::~AudioEngine() {
    running_.store(false);
    if (worker_.joinable()) worker_.join();
}

void AudioEngine::process(const float* data, int numFrames) {
    // Audio-thread path: no heap allocation, no I/O.
    ring_->write(data, static_cast<size_t>(numFrames));

    for (int i = 0; i < numFrames; ++i) {
        frameAccum_.push_back(data[i]);
        if (frameAccum_.size() == static_cast<size_t>(kFrame)) {
            dsp::FrameResult r = proc_->pushFrame(frameAccum_.data(), kFrame);
            mDb_.store(r.db);
            mLeq_.store(r.leq);
            mLmax_.store(r.lmax);
            if (capturingPost_) {
                if (r.db > eventPeak_) eventPeak_ = r.db;
                eventEnergyAcc_ += std::pow(10.0, r.db / 10.0);
                eventFrames_++;
            }
            triggerIfNeeded(r.db);
            frameAccum_.clear();
        }
    }

    if (capturingPost_) {
        postRemaining_ -= numFrames;
        if (postRemaining_ <= 0) {
            enqueueSave();
            capturingPost_ = false;
        }
    }
}

void AudioEngine::triggerIfNeeded(float frameDb) {
    if (!capturingPost_ && frameDb >= threshold_.load()) {
        capturingPost_ = true;
        postRemaining_ = kPost;
        eventPeak_ = frameDb;
        eventEnergyAcc_ = std::pow(10.0, frameDb / 10.0);
        eventFrames_ = 1;
    }
}

void AudioEngine::enqueueSave() {
    for (auto& slot : pool_) {
        if (slot.state.load() == 0) {  // FREE
            size_t cnt = ring_->extractInto(kPre, kPost, slot.buf.data());
            slot.count = cnt;
            slot.peak = eventPeak_;
            slot.leq = eventFrames_ > 0
                           ? static_cast<float>(10.0 * std::log10(eventEnergyAcc_ / eventFrames_))
                           : eventPeak_;
            slot.seq = eventSeq_++;
            slot.state.store(1);  // READY — set state LAST (release)
            return;
        }
    }
    // No free slot: drop the event rather than block the audio thread.
    droppedEvents_++;
}

void AudioEngine::workerLoop() {
    auto anyBusy = [this]() {
        for (auto& slot : pool_) {
            if (slot.state.load() != 0) return true;
        }
        return false;
    };

    while (running_.load() || anyBusy()) {
        for (auto& slot : pool_) {
            if (slot.state.load() == 1) {  // READY
                slot.state.store(2);       // PROCESSING
                std::string path = outDir_ + "/event_" + std::to_string(slot.seq) + ".wav";
                bool ok = io::writeWav(path, slot.buf.data(), slot.count, kSampleRate);
                if (ok && cb_) cb_(path, slot.peak, slot.leq);
                slot.state.store(0);  // FREE
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

Metrics AudioEngine::latest() const {
    return {mDb_.load(), mLeq_.load(), mLmax_.load()};
}

void AudioEngine::drainForTest(int timeoutMs) {
    int iters = timeoutMs / 5;
    for (int i = 0; i < iters; ++i) {
        bool allFree = true;
        for (auto& slot : pool_) {
            if (slot.state.load() != 0) {
                allFree = false;
                break;
            }
        }
        if (allFree) return;
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
}

}  // namespace engine
