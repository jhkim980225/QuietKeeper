#include <jni.h>
#include <string>
#include <memory>
#include "engine/AudioEngine.h"
#include "engine/AAudioSource.h"

static JavaVM* gVm = nullptr;
static jobject gListener = nullptr;                  // global ref to Kotlin Listener
static std::unique_ptr<engine::AudioEngine> gEngine;
static std::unique_ptr<engine::AAudioSource> gSource;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) { gVm = vm; return JNI_VERSION_1_6; }

// Called from the engine worker thread → attach to the JVM, call Kotlin Listener.onEvent.
static void emitEvent(const std::string& path, float peak, float leq) {
    if (!gListener || !gVm) return;
    JNIEnv* env = nullptr; bool attached = false;
    if (gVm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (gVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jclass cls = env->GetObjectClass(gListener);
    jmethodID m = env->GetMethodID(cls, "onEvent", "(Ljava/lang/String;FF)V");
    if (m) {
        jstring jp = env->NewStringUTF(path.c_str());
        env->CallVoidMethod(gListener, m, jp, peak, leq);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(jp);
    }
    env->DeleteLocalRef(cls);
    if (attached) gVm->DetachCurrentThread();
}

extern "C" JNIEXPORT void JNICALL
Java_com_quietkeeper_app_audio_AudioEngine_nativeStart(JNIEnv* env, jobject /*thiz*/,
        jstring outDir, jfloat offset, jfloat threshold, jobject listener) {
    // Defensive: if start is called again without stop, tear down the previous session first.
    if (gSource) { gSource->stop(); gSource.reset(); }
    if (gEngine) { gEngine.reset(); }
    if (gListener) { env->DeleteGlobalRef(gListener); gListener = nullptr; }
    gListener = env->NewGlobalRef(listener);
    const char* dir = env->GetStringUTFChars(outDir, nullptr);
    gEngine = std::make_unique<engine::AudioEngine>(std::string(dir), (float)offset, (float)threshold, &emitEvent);
    env->ReleaseStringUTFChars(outDir, dir);
    gSource = std::make_unique<engine::AAudioSource>(
        [](const float* d, int n){ if (gEngine) gEngine->process(d, n); });
    gSource->start();   // if false, mic unavailable; engine still constructed (poll returns floor)
}

extern "C" JNIEXPORT void JNICALL
Java_com_quietkeeper_app_audio_AudioEngine_nativeStop(JNIEnv* env, jobject /*thiz*/) {
    if (gSource) { gSource->stop(); gSource.reset(); }   // stop callbacks BEFORE destroying engine
    if (gEngine) { gEngine.reset(); }                    // destructor joins worker
    if (gListener) { env->DeleteGlobalRef(gListener); gListener = nullptr; }
}

// returns float[3] = {db, leq, lmax}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_quietkeeper_app_audio_AudioEngine_nativePoll(JNIEnv* env, jobject /*thiz*/) {
    engine::Metrics m = gEngine ? gEngine->latest() : engine::Metrics{};
    jfloatArray arr = env->NewFloatArray(3);
    float vals[3] = { m.db, m.leq, m.lmax };
    env->SetFloatArrayRegion(arr, 0, 3, vals);
    return arr;
}
