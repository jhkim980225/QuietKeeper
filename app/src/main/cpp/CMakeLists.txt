cmake_minimum_required(VERSION 3.22.1)
project(audiocore)
add_library(audiocore SHARED
    dsp/Decibel.cpp dsp/AWeighting.cpp dsp/FrameProcessor.cpp
    buffer/RingBuffer.cpp io/WavWriter.cpp engine/AudioEngine.cpp engine/AAudioSource.cpp jni/native-lib.cpp)
find_library(log-lib log)
find_library(aaudio-lib aaudio)
target_link_libraries(audiocore ${log-lib} ${aaudio-lib})
target_include_directories(audiocore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
# Allow calling API-28+ functions (e.g. AAudioStreamBuilder_setInputPreset) from a minSdk-26
# build using __builtin_available runtime guards.  Without this flag the NDK uses the 'strict'
# availability attribute which makes Clang error even inside __builtin_available blocks.
target_compile_definitions(audiocore PRIVATE __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__)
