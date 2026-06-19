#include "engine/AAudioSource.h"
#include <android/log.h>
#include <sys/system_properties.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AAudioSource", __VA_ARGS__)

// Returns the device's runtime API level (fast, no JNI needed).
static int deviceApiLevel() {
    char buf[PROP_VALUE_MAX] = {};
    __system_property_get("ro.build.version.sdk", buf);
    return buf[0] ? atoi(buf) : 26;
}

namespace engine {
aaudio_data_callback_result_t AAudioSource::onAudioStatic(AAudioStream*, void* ud, void* audio, int32_t n) {
    auto* self = static_cast<AAudioSource*>(ud);
    self->sink_(static_cast<const float*>(audio), n);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
AAudioSource::AAudioSource(Sink s) : sink_(std::move(s)) {}
AAudioSource::~AAudioSource() { stop(); }

bool AAudioSource::start() {
    AAudioStreamBuilder* b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) { LOGE("createStreamBuilder failed"); return false; }
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setSampleRate(b, 48000);
    AAudioStreamBuilder_setChannelCount(b, 1);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
#if __ANDROID_API__ >= 28
    // AAUDIO_INPUT_PRESET_UNPROCESSED disables OS DSP (noise suppression, AGC, etc.)
    // so we receive raw mic samples. Requires API 28+; on older devices we accept the
    // default preset (VOICE_RECOGNITION) and note the difference in a log.
    if (deviceApiLevel() >= 28) {
        AAudioStreamBuilder_setInputPreset(b, AAUDIO_INPUT_PRESET_UNPROCESSED);
    } else {
        LOGE("API < 28: UNPROCESSED preset unavailable; using default (VOICE_RECOGNITION)");
    }
#endif
    AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setDataCallback(b, onAudioStatic, this);

    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &stream_);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK || stream_ == nullptr) { LOGE("openStream failed: %d", r); stream_ = nullptr; return false; }

    // The engine assumes 48kHz mono float. If the device gave us something else, fail fast.
    if (AAudioStream_getFormat(stream_) != AAUDIO_FORMAT_PCM_FLOAT ||
        AAudioStream_getChannelCount(stream_) != 1 ||
        AAudioStream_getSampleRate(stream_) != 48000) {
        LOGE("unexpected stream config: fmt=%d ch=%d rate=%d",
             AAudioStream_getFormat(stream_), AAudioStream_getChannelCount(stream_), AAudioStream_getSampleRate(stream_));
        AAudioStream_close(stream_); stream_ = nullptr; return false;
    }
    if (AAudioStream_requestStart(stream_) != AAUDIO_OK) { LOGE("requestStart failed"); AAudioStream_close(stream_); stream_ = nullptr; return false; }
    return true;
}
void AAudioSource::stop() {
    if (stream_) { AAudioStream_requestStop(stream_); AAudioStream_close(stream_); stream_ = nullptr; }
}
}
