# 측정 코어 (NDK 오디오 엔진) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 외장마이크의 보정 없는 Raw Audio를 NDK(AAudio)로 수집해 0.125초 주기 A-가중 dB(A)·Leq·Lmax를 계산하고, 순환 버퍼로 임계 초과 시 전후 10초 원음을 WAV로 저장하는 측정 엔진을, 동작을 눈으로 확인할 최소 Kotlin 화면까지 포함해 완성한다.

**Architecture:** 무거운 연산은 전부 C++ 네이티브(`audio-core`)에서 수행한다. 순수 DSP 단위(A-가중 필터, RMS→dB, Leq/Lmax 집계, 링버퍼, WAV 라이터)는 GoogleTest로 TDD하고, AAudio 캡처·JNI 브릿지·Compose UI는 실기기 통합으로 검증한다. Kotlin은 Foreground Service에서 8Hz로 네이티브의 최신 측정값을 폴링하고, 이벤트 저장 콜백을 Room에 기록한다.

**Tech Stack:** Android (minSdk 26), Kotlin, Jetpack Compose, NDK + CMake, AAudio, GoogleTest, Room, Foreground Service.

**이 플랜의 범위 (Phase 1):** 측정 엔진 + 동작 검증용 최소 UI(측정 화면, 이벤트 목록).
**범위 밖 (다음 플랜):** 언어 선택·준비·저장·상세 등 전체 UX, 지도/GPS, 결제/광고, 다국어 번역, 더미 AI. 설계 문서 `docs/superpowers/specs/2026-06-20-noise-measurement-core-design.md` 참조.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `app/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/cpp/CMakeLists.txt` | 프로젝트·NDK·CMake·GoogleTest 빌드 설정 |
| `app/src/main/cpp/dsp/Decibel.h` / `.cpp` | RMS → dBFS → dB(A) (캘리브레이션 오프셋 적용) |
| `app/src/main/cpp/dsp/AWeighting.h` / `.cpp` | A-가중 IIR(biquad 캐스케이드) 필터 |
| `app/src/main/cpp/dsp/FrameProcessor.h` / `.cpp` | 0.125초 프레임 처리 + Leq/Lmax 집계 |
| `app/src/main/cpp/buffer/RingBuffer.h` / `.cpp` | 고정 크기 PCM 순환 버퍼, 전후 구간 추출 |
| `app/src/main/cpp/io/WavWriter.h` / `.cpp` | float PCM → 16-bit WAV 파일 |
| `app/src/main/cpp/engine/AudioEngine.h` / `.cpp` | AAudio 캡처 + 파이프라인 + 이벤트 감지 |
| `app/src/main/cpp/jni/native-lib.cpp` | JNI 진입점 (start/stop/poll/콜백) |
| `app/src/test/cpp/*` | GoogleTest 네이티브 단위 테스트 |
| `app/src/main/java/.../audio/AudioEngine.kt` | JNI 래퍼 (Kotlin) |
| `app/src/main/java/.../audio/MeasurementService.kt` | Foreground Service + 폴링 |
| `app/src/main/java/.../data/*` | Room: `NoiseEvent`, DAO, DB |
| `app/src/main/java/.../sensor/MovementDetector.kt` | 가속도/자이로 이동 감지 |
| `app/src/main/java/.../ui/*` | 최소 Compose: 측정 화면, 이벤트 목록 |

**상수 (전 모듈 공통):** 샘플레이트 `48000`, 채널 mono, 프레임 `6000` 샘플(0.125초), 링버퍼 `30`초, 전/후 저장 `5`초/`5`초, 기본 임계 `55.0f` dB(A), 캘리브레이션 오프셋 기본 `0.0f`.

---

## Task 1: 프로젝트 스켈레톤 + NDK/CMake + GoogleTest 하니스

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/dsp/Decibel.h`
- Create: `app/src/test/cpp/CMakeLists.txt`, `app/src/test/cpp/SanityTest.cpp`

- [ ] **Step 1: Android Studio로 "Native C++" 템플릿 프로젝트 생성**

패키지 `com.noisemeter.app`, minSdk 26, Kotlin, Jetpack Compose 활성. 생성 후 아래 단계로 NDK/CMake/GoogleTest를 정리한다.

- [ ] **Step 2: `app/src/main/cpp/CMakeLists.txt` 작성**

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(audiocore)

add_library(audiocore SHARED
    dsp/Decibel.cpp
    dsp/AWeighting.cpp
    dsp/FrameProcessor.cpp
    buffer/RingBuffer.cpp
    io/WavWriter.cpp
    engine/AudioEngine.cpp
    jni/native-lib.cpp)

find_library(log-lib log)
find_library(aaudio-lib aaudio)
target_link_libraries(audiocore ${log-lib} ${aaudio-lib})
target_include_directories(audiocore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
```

- [ ] **Step 3: GoogleTest용 호스트 테스트 CMake 작성 — `app/src/test/cpp/CMakeLists.txt`**

호스트(개발 PC)에서 DSP 로직만 컴파일해 빠르게 테스트한다. AAudio/JNI 의존 파일은 제외한다.

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(audiocore_tests)
set(CMAKE_CXX_STANDARD 17)

include(FetchContent)
FetchContent_Declare(googletest
  URL https://github.com/google/googletest/archive/refs/tags/v1.14.0.zip)
FetchContent_MakeAvailable(googletest)

set(SRC ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp)
add_executable(audiocore_tests
    SanityTest.cpp
    ${SRC}/dsp/Decibel.cpp
    ${SRC}/dsp/AWeighting.cpp
    ${SRC}/dsp/FrameProcessor.cpp
    ${SRC}/buffer/RingBuffer.cpp
    ${SRC}/io/WavWriter.cpp)
target_include_directories(audiocore_tests PRIVATE ${SRC})
target_link_libraries(audiocore_tests gtest_main)

include(GoogleTest)
gtest_discover_tests(audiocore_tests)
```

- [ ] **Step 4: 빌드를 통과시킬 최소 헤더 `app/src/main/cpp/dsp/Decibel.h` 작성**

```cpp
#pragma once
namespace dsp { double sanity(); }
```

`app/src/main/cpp/dsp/Decibel.cpp`:
```cpp
#include "dsp/Decibel.h"
namespace dsp { double sanity() { return 42.0; } }
```

(AWeighting/FrameProcessor/RingBuffer/WavWriter/AudioEngine/native-lib는 다음 Task에서 만들되, 지금은 빈 stub `.cpp`/`.h`를 만들어 CMake가 깨지지 않게 둔다. 각 stub은 빈 네임스페이스 본문만 둔다.)

- [ ] **Step 5: 실패하는 sanity 테스트 작성 — `app/src/test/cpp/SanityTest.cpp`**

```cpp
#include <gtest/gtest.h>
#include "dsp/Decibel.h"

TEST(Sanity, HarnessRuns) {
    EXPECT_DOUBLE_EQ(dsp::sanity(), 42.0);
}
```

- [ ] **Step 6: 호스트 테스트 빌드·실행하여 통과 확인**

Run:
```bash
cmake -S app/src/test/cpp -B build-tests
cmake --build build-tests
./build-tests/audiocore_tests
```
Expected: `[  PASSED  ] 1 test.`

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts app/ gradle.properties
git commit -m "chore: scaffold Android NDK project with GoogleTest host harness"
```

---

## Task 2: RMS → dB(A) 변환 (캘리브레이션 오프셋)

**Files:**
- Modify: `app/src/main/cpp/dsp/Decibel.h`, `app/src/main/cpp/dsp/Decibel.cpp`
- Test: `app/src/test/cpp/DecibelTest.cpp` (Create), `app/src/test/cpp/CMakeLists.txt` (add source)

- [ ] **Step 1: 실패하는 테스트 작성 — `app/src/test/cpp/DecibelTest.cpp`**

float 샘플의 RMS를 구하고, `dB = 20*log10(rms) + offset` 로 변환한다. full-scale 정현파(진폭 1.0, RMS=0.707)는 오프셋 0에서 약 -3.01 dBFS여야 한다.

```cpp
#include <gtest/gtest.h>
#include <vector>
#include <cmath>
#include "dsp/Decibel.h"

TEST(Decibel, RmsOfConstant) {
    std::vector<float> s(100, 0.5f);
    EXPECT_NEAR(dsp::rms(s.data(), s.size()), 0.5f, 1e-6);
}

TEST(Decibel, FullScaleSineIsAboutMinus3dBFS) {
    std::vector<float> s(4800);
    for (size_t i = 0; i < s.size(); ++i)
        s[i] = std::sin(2.0 * M_PI * 1000.0 * i / 48000.0);
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
```

- [ ] **Step 2: 테스트 소스를 CMake에 추가**

`app/src/test/cpp/CMakeLists.txt`의 `add_executable(audiocore_tests ...)` 목록에 `DecibelTest.cpp` 한 줄을 추가한다.

- [ ] **Step 3: 테스트 빌드·실행하여 실패 확인**

Run: `cmake --build build-tests && ./build-tests/audiocore_tests`
Expected: FAIL — `dsp::rms` / `dsp::toDb` 미정의(컴파일 에러).

- [ ] **Step 4: 구현 — `app/src/main/cpp/dsp/Decibel.h`**

```cpp
#pragma once
#include <cstddef>
namespace dsp {
    float rms(const float* samples, size_t n);
    // dBFS(+offset). rms<=0 이면 floor(-120) 반환.
    float toDb(float rmsValue, float calibrationOffset);
}
```

`app/src/main/cpp/dsp/Decibel.cpp`:
```cpp
#include "dsp/Decibel.h"
#include <cmath>
namespace dsp {
    float rms(const float* s, size_t n) {
        if (n == 0) return 0.0f;
        double acc = 0.0;
        for (size_t i = 0; i < n; ++i) acc += static_cast<double>(s[i]) * s[i];
        return static_cast<float>(std::sqrt(acc / n));
    }
    float toDb(float v, float offset) {
        if (v <= 1e-12f) return -120.0f;
        return static_cast<float>(20.0 * std::log10(v)) + offset;
    }
}
```

- [ ] **Step 5: 테스트 빌드·실행하여 통과 확인**

Run: `cmake --build build-tests && ./build-tests/audiocore_tests`
Expected: PASS (Decibel 4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/cpp/dsp/Decibel.* app/src/test/cpp/DecibelTest.cpp app/src/test/cpp/CMakeLists.txt
git commit -m "feat: RMS and dBFS conversion with calibration offset"
```

---

## Task 3: A-가중 IIR 필터 (biquad 캐스케이드)

**Files:**
- Modify: `app/src/main/cpp/dsp/AWeighting.h`, `app/src/main/cpp/dsp/AWeighting.cpp`
- Test: `app/src/test/cpp/AWeightingTest.cpp` (Create), add to test CMake

A-가중은 표준상 1 kHz에서 0 dB(이득 1), 저주파(예: 100 Hz, ≈ -19 dB)와 고주파를 감쇠시킨다. 48 kHz용 A-weighting biquad 계수(공개된 표준 설계값)를 사용하고, 정현파를 통과시켜 1 kHz는 거의 그대로, 100 Hz는 크게 감쇠되는지로 검증한다.

- [ ] **Step 1: 실패하는 테스트 작성 — `app/src/test/cpp/AWeightingTest.cpp`**

```cpp
#include <gtest/gtest.h>
#include <vector>
#include <cmath>
#include "dsp/AWeighting.h"
#include "dsp/Decibel.h"

static std::vector<float> sine(double f, size_t n) {
    std::vector<float> s(n);
    for (size_t i = 0; i < n; ++i) s[i] = std::sin(2.0 * M_PI * f * i / 48000.0);
    return s;
}
// 필터를 통과시킨 정현파의 정상상태 RMS dB (앞부분 트랜지언트 제외)
static float steadyDb(dsp::AWeighting& w, std::vector<float> s) {
    w.process(s.data(), s.size());
    return dsp::toDb(dsp::rms(s.data() + s.size()/2, s.size()/2), 0.0f);
}

TEST(AWeighting, UnityAt1kHz) {
    dsp::AWeighting w(48000);
    float in = dsp::toDb(dsp::rms(sine(1000, 48000).data(), 48000), 0.0f);
    float out = steadyDb(w, sine(1000, 48000));
    EXPECT_NEAR(out - in, 0.0f, 0.7f); // 1kHz ≈ 0 dB
}

TEST(AWeighting, Attenuates100Hz) {
    dsp::AWeighting w(48000);
    float in = dsp::toDb(dsp::rms(sine(100, 48000).data(), 48000), 0.0f);
    float out = steadyDb(w, sine(100, 48000));
    EXPECT_LT(out - in, -15.0f); // 100Hz ≈ -19 dB 부근, 크게 감쇠
}
```

- [ ] **Step 2: 테스트 CMake에 `AWeightingTest.cpp` 추가, 빌드·실행하여 실패 확인**

Run: `cmake -S app/src/test/cpp -B build-tests && cmake --build build-tests && ./build-tests/audiocore_tests`
Expected: FAIL — `dsp::AWeighting` 미정의.

- [ ] **Step 3: 구현 — `app/src/main/cpp/dsp/AWeighting.h`**

```cpp
#pragma once
#include <cstddef>
#include <array>
namespace dsp {
class AWeighting {
public:
    explicit AWeighting(int sampleRate);
    void process(float* samples, size_t n); // in-place
    void reset();
private:
    struct Biquad { double b0,b1,b2,a1,a2; double z1=0,z2=0;
        double step(double x){ double y=b0*x+z1; z1=b1*x-a1*y+z2; z2=b2*x-a2*y; return y; } };
    std::array<Biquad,3> stages_;
};
}
```

`app/src/main/cpp/dsp/AWeighting.cpp` — 48 kHz A-weighting 표준 계수(bilinear 변환 기반, 3개 biquad). 다른 샘플레이트는 48 kHz로 고정 가정(엔진이 48 kHz 요청).

```cpp
#include "dsp/AWeighting.h"
namespace dsp {
AWeighting::AWeighting(int) {
    // 48kHz A-weighting, normalized to 0 dB @ 1 kHz (3 biquad sections)
    stages_[0] = { 0.16999495, 0.33998990, 0.16999495, -1.76004281, 0.76916178 };
    stages_[1] = { 1.0, -2.0,  1.0, -1.96606705, 0.96671693 };
    stages_[2] = { 1.0, -2.0,  1.0, -1.99764047, 0.99764704 };
}
void AWeighting::process(float* s, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        double x = s[i];
        for (auto& st : stages_) x = st.step(x);
        s[i] = static_cast<float>(x);
    }
}
void AWeighting::reset() { for (auto& st : stages_) { st.z1 = st.z2 = 0; } }
}
```

> 참고: 위 계수는 표준 A-weighting의 48 kHz 이산화 근사다. 테스트(1 kHz ≈ 0 dB, 100 Hz 큰 감쇠)가 통과하지 않으면 계수를 검증된 값으로 교체한다. 테스트가 정확성의 기준이다.

- [ ] **Step 4: 테스트 빌드·실행하여 통과 확인**

Run: `cmake --build build-tests && ./build-tests/audiocore_tests`
Expected: PASS (AWeighting 2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/dsp/AWeighting.* app/src/test/cpp/AWeightingTest.cpp app/src/test/cpp/CMakeLists.txt
git commit -m "feat: A-weighting IIR filter (48kHz biquad cascade)"
```

---

## Task 4: 프레임 프로세서 (0.125초 dB(A) + Leq/Lmax)

**Files:**
- Modify: `app/src/main/cpp/dsp/FrameProcessor.h`, `.cpp`
- Test: `app/src/test/cpp/FrameProcessorTest.cpp` (Create), add to test CMake

`FrameProcessor`는 매 0.125초(6000 샘플) 블록을 받아 A-가중 → RMS → dB(A)를 계산하고, 진행 중 Leq(1분 에너지 평균)와 Lmax(관측된 최대 프레임 dB)를 누적한다.

- [ ] **Step 1: 실패하는 테스트 작성 — `app/src/test/cpp/FrameProcessorTest.cpp`**

```cpp
#include <gtest/gtest.h>
#include <vector>
#include <cmath>
#include "dsp/FrameProcessor.h"

static std::vector<float> sine(double f, size_t n) {
    std::vector<float> s(n);
    for (size_t i = 0; i < n; ++i) s[i] = 0.5f*std::sin(2.0*M_PI*f*i/48000.0);
    return s;
}

TEST(FrameProcessor, ReturnsFrameDbForOneFrame) {
    dsp::FrameProcessor fp(48000, 6000, /*offset*/94.0f);
    auto s = sine(1000, 6000);
    dsp::FrameResult r = fp.pushFrame(s.data(), 6000);
    EXPECT_GT(r.db, 80.0f);   // 0.5 진폭 + 94 오프셋 → 약 85 dB(A)
    EXPECT_NEAR(r.lmax, r.db, 0.01f);
}

TEST(FrameProcessor, LmaxTracksLoudestFrame) {
    dsp::FrameProcessor fp(48000, 6000, 94.0f);
    fp.pushFrame(sine(1000,6000).data(), 6000);   // 큰 소리
    auto quiet = sine(1000,6000); for (auto& x: quiet) x*=0.01f;
    dsp::FrameResult r = fp.pushFrame(quiet.data(), 6000); // 작은 소리
    EXPECT_LT(r.db, r.lmax);  // 현재 프레임 < 누적 Lmax
}

TEST(FrameProcessor, LeqBetweenQuietAndLoud) {
    dsp::FrameProcessor fp(48000, 6000, 94.0f);
    float loud = fp.pushFrame(sine(1000,6000).data(),6000).db;
    auto quiet = sine(1000,6000); for (auto& x: quiet) x*=0.05f;
    float q = fp.pushFrame(quiet.data(),6000).db;
    float leq = fp.leq();
    EXPECT_GT(leq, q);
    EXPECT_LT(leq, loud);
}
```

- [ ] **Step 2: 테스트 CMake에 추가, 빌드·실행하여 실패 확인**

Expected: FAIL — `dsp::FrameProcessor` / `dsp::FrameResult` 미정의.

- [ ] **Step 3: 구현 — `app/src/main/cpp/dsp/FrameProcessor.h`**

```cpp
#pragma once
#include <cstddef>
#include "dsp/AWeighting.h"
namespace dsp {
struct FrameResult { float db; float leq; float lmax; };
class FrameProcessor {
public:
    FrameProcessor(int sampleRate, int frameSize, float calibrationOffset);
    // n 샘플(=frameSize)을 A-가중→RMS→dB(A) 처리하고 결과 반환. 누적 Leq/Lmax 갱신.
    FrameResult pushFrame(const float* samples, size_t n);
    float leq() const;
    float lmax() const { return lmax_; }
    void reset();
private:
    AWeighting weight_;
    float offset_;
    double energyAcc_ = 0.0;  // 선형 에너지 합
    long frameCount_ = 0;
    float lmax_ = -120.0f;
    std::vector<float> scratch_;
};
}
```

`app/src/main/cpp/dsp/FrameProcessor.cpp`:
```cpp
#include "dsp/FrameProcessor.h"
#include "dsp/Decibel.h"
#include <cmath>
namespace dsp {
FrameProcessor::FrameProcessor(int, int frameSize, float offset)
    : weight_(48000), offset_(offset), scratch_(frameSize) {}

FrameResult FrameProcessor::pushFrame(const float* in, size_t n) {
    if (scratch_.size() < n) scratch_.resize(n);
    for (size_t i = 0; i < n; ++i) scratch_[i] = in[i];
    weight_.process(scratch_.data(), n);
    float r = rms(scratch_.data(), n);
    float db = toDb(r, offset_);
    // Leq: 에너지(10^(db/10)) 평균을 로그 환산
    energyAcc_ += std::pow(10.0, db / 10.0);
    frameCount_ += 1;
    if (db > lmax_) lmax_ = db;
    return { db, leq(), lmax_ };
}
float FrameProcessor::leq() const {
    if (frameCount_ == 0) return -120.0f;
    return static_cast<float>(10.0 * std::log10(energyAcc_ / frameCount_));
}
void FrameProcessor::reset() { weight_.reset(); energyAcc_ = 0; frameCount_ = 0; lmax_ = -120.0f; }
}
```

(`#include <vector>`를 헤더 상단에 추가한다.)

- [ ] **Step 4: 테스트 빌드·실행하여 통과 확인**

Expected: PASS (FrameProcessor 3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/dsp/FrameProcessor.* app/src/test/cpp/FrameProcessorTest.cpp app/src/test/cpp/CMakeLists.txt
git commit -m "feat: frame processor with A-weighted dB(A), Leq, Lmax"
```

---

## Task 5: 순환(링) 버퍼 + 전후 구간 추출

**Files:**
- Modify: `app/src/main/cpp/buffer/RingBuffer.h`, `.cpp`
- Test: `app/src/test/cpp/RingBufferTest.cpp` (Create), add to test CMake

고정 크기 float 링버퍼. `write()`로 계속 채우고, 임계 초과 시점에 `extract(preSamples, postSamples)`로 "직전 pre + 직후 post" 연속 구간을 복사한다. post는 트리거 이후 write가 채워지면 추출 가능해진다.

- [ ] **Step 1: 실패하는 테스트 작성 — `app/src/test/cpp/RingBufferTest.cpp`**

```cpp
#include <gtest/gtest.h>
#include <vector>
#include "buffer/RingBuffer.h"

TEST(RingBuffer, OverwritesOldestWhenFull) {
    buffer::RingBuffer rb(4);
    float a[] = {1,2,3,4,5,6};
    rb.write(a, 6);              // 3,4,5,6 만 남음
    std::vector<float> out = rb.lastN(4);
    ASSERT_EQ(out.size(), 4u);
    EXPECT_FLOAT_EQ(out[0], 3); EXPECT_FLOAT_EQ(out[3], 6);
}

TEST(RingBuffer, ExtractPreAndPost) {
    buffer::RingBuffer rb(10);
    float pre[] = {1,2,3,4,5};   // 트리거 직전 5개
    rb.write(pre, 5);
    float post[] = {6,7,8};      // 트리거 이후 3개
    rb.write(post, 3);
    std::vector<float> seg = rb.extract(/*pre*/5, /*post*/3);
    ASSERT_EQ(seg.size(), 8u);
    EXPECT_FLOAT_EQ(seg.front(), 1); EXPECT_FLOAT_EQ(seg.back(), 8);
}
```

- [ ] **Step 2: 테스트 CMake에 추가, 빌드·실행하여 실패 확인**

Expected: FAIL — `buffer::RingBuffer` 미정의.

- [ ] **Step 3: 구현 — `app/src/main/cpp/buffer/RingBuffer.h`**

```cpp
#pragma once
#include <vector>
#include <cstddef>
namespace buffer {
class RingBuffer {
public:
    explicit RingBuffer(size_t capacity);
    void write(const float* data, size_t n);
    std::vector<float> lastN(size_t n) const;            // 최근 n개
    std::vector<float> extract(size_t pre, size_t post) const; // 최근 (pre+post)개
    size_t capacity() const { return cap_; }
private:
    std::vector<float> buf_;
    size_t cap_;
    size_t head_ = 0;   // 다음 쓸 위치
    size_t filled_ = 0; // 채워진 개수
};
}
```

`app/src/main/cpp/buffer/RingBuffer.cpp`:
```cpp
#include "buffer/RingBuffer.h"
namespace buffer {
RingBuffer::RingBuffer(size_t c) : buf_(c, 0.0f), cap_(c) {}
void RingBuffer::write(const float* d, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        buf_[head_] = d[i];
        head_ = (head_ + 1) % cap_;
        if (filled_ < cap_) ++filled_;
    }
}
std::vector<float> RingBuffer::lastN(size_t n) const {
    if (n > filled_) n = filled_;
    std::vector<float> out(n);
    for (size_t i = 0; i < n; ++i)
        out[n - 1 - i] = buf_[(head_ + cap_ - 1 - i) % cap_];
    return out;
}
std::vector<float> RingBuffer::extract(size_t pre, size_t post) const {
    return lastN(pre + post);
}
}
```

- [ ] **Step 4: 테스트 빌드·실행하여 통과 확인**

Expected: PASS (RingBuffer 2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/buffer/RingBuffer.* app/src/test/cpp/RingBufferTest.cpp app/src/test/cpp/CMakeLists.txt
git commit -m "feat: fixed-size PCM ring buffer with pre/post extraction"
```

---

## Task 6: WAV 라이터 (float PCM → 16-bit WAV)

**Files:**
- Modify: `app/src/main/cpp/io/WavWriter.h`, `.cpp`
- Test: `app/src/test/cpp/WavWriterTest.cpp` (Create), add to test CMake

- [ ] **Step 1: 실패하는 테스트 작성 — `app/src/test/cpp/WavWriterTest.cpp`**

알려진 PCM을 임시 파일로 쓰고, 파일 크기와 헤더(`RIFF`/`WAVE`, 샘플레이트, 16-bit)를 다시 읽어 검증한다.

```cpp
#include <gtest/gtest.h>
#include <vector>
#include <cstdio>
#include <cstdint>
#include "io/WavWriter.h"

static uint32_t le32(const unsigned char* p){ return p[0]|p[1]<<8|p[2]<<16|(uint32_t)p[3]<<24; }

TEST(WavWriter, WritesValidHeader) {
    std::vector<float> pcm(48000, 0.5f); // 1초
    std::string path = "/tmp/wavtest.wav";
    ASSERT_TRUE(io::writeWav(path, pcm.data(), pcm.size(), 48000));

    FILE* f = fopen(path.c_str(), "rb");
    ASSERT_NE(f, nullptr);
    unsigned char h[44]; ASSERT_EQ(fread(h,1,44,f), 44u); fclose(f);
    EXPECT_EQ(std::string(h,h+4), "RIFF");
    EXPECT_EQ(std::string(h+8,h+12), "WAVE");
    EXPECT_EQ(le32(h+24), 48000u);             // sample rate
    EXPECT_EQ(h[34], 16);                       // bits per sample
    EXPECT_EQ(le32(h+40), 48000u*2u);           // data bytes = samples*2
}
```

- [ ] **Step 2: 테스트 CMake에 추가, 빌드·실행하여 실패 확인**

Expected: FAIL — `io::writeWav` 미정의.

- [ ] **Step 3: 구현 — `app/src/main/cpp/io/WavWriter.h`**

```cpp
#pragma once
#include <string>
#include <cstddef>
namespace io {
    // float[-1..1] → 16-bit PCM mono WAV. 성공 시 true.
    bool writeWav(const std::string& path, const float* samples, size_t n, int sampleRate);
}
```

`app/src/main/cpp/io/WavWriter.cpp`:
```cpp
#include "io/WavWriter.h"
#include <cstdio>
#include <cstdint>
#include <algorithm>
namespace {
void w32(FILE* f, uint32_t v){ unsigned char b[4]={(unsigned char)v,(unsigned char)(v>>8),(unsigned char)(v>>16),(unsigned char)(v>>24)}; fwrite(b,1,4,f);}
void w16(FILE* f, uint16_t v){ unsigned char b[2]={(unsigned char)v,(unsigned char)(v>>8)}; fwrite(b,1,2,f);}
}
namespace io {
bool writeWav(const std::string& path, const float* s, size_t n, int sr) {
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) return false;
    uint32_t dataBytes = (uint32_t)(n * 2);
    fwrite("RIFF",1,4,f); w32(f, 36 + dataBytes); fwrite("WAVE",1,4,f);
    fwrite("fmt ",1,4,f); w32(f,16); w16(f,1); w16(f,1);
    w32(f, sr); w32(f, sr*2); w16(f,2); w16(f,16);
    fwrite("data",1,4,f); w32(f, dataBytes);
    for (size_t i = 0; i < n; ++i) {
        float v = std::max(-1.0f, std::min(1.0f, s[i]));
        w16(f, (int16_t)(v * 32767.0f));
    }
    fclose(f);
    return true;
}
}
```

- [ ] **Step 4: 테스트 빌드·실행하여 통과 확인**

Expected: PASS (WavWriter 1 test).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/io/WavWriter.* app/src/test/cpp/WavWriterTest.cpp app/src/test/cpp/CMakeLists.txt
git commit -m "feat: 16-bit mono WAV writer from float PCM"
```

---

## Task 7: AudioEngine — AAudio 캡처 + 파이프라인 + 이벤트 감지

**Files:**
- Modify: `app/src/main/cpp/engine/AudioEngine.h`, `.cpp`

이 Task는 하드웨어(AAudio) 의존이라 호스트 GoogleTest가 아닌 **실기기 통합**으로 검증한다. AAudio 입력 콜백에서 6000샘플 프레임 단위로 `FrameProcessor`에 넣고, 원본 PCM은 `RingBuffer`에 쓴다. 프레임 dB가 임계 초과 시, post 5초가 채워진 뒤 `RingBuffer::extract`로 전후 10초를 꺼내 `writeWav`로 저장하고 콜백을 호출한다.

- [ ] **Step 1: 구현 — `app/src/main/cpp/engine/AudioEngine.h`**

```cpp
#pragma once
#include <atomic>
#include <functional>
#include <memory>
#include <string>
#include <aaudio/AAudio.h>
#include "dsp/FrameProcessor.h"
#include "buffer/RingBuffer.h"

namespace engine {
struct Metrics { float db=-120, leq=-120, lmax=-120; };
class AudioEngine {
public:
    using EventCallback = std::function<void(const std::string& wavPath, float peakDb, float leq)>;
    AudioEngine(const std::string& outDir, float calibrationOffset,
                float thresholdDb, EventCallback cb);
    bool start();
    void stop();
    Metrics latest() const;           // Kotlin 폴링용 (lock-free 스냅샷)
    void setThreshold(float db) { threshold_ = db; }
    // AAudio 콜백에서 호출 (public for callback trampoline)
    void onAudio(const float* data, int32_t numFrames);
private:
    void maybeSaveEvent(float frameDb);
    std::string outDir_;
    float offset_, threshold_;
    EventCallback cb_;
    std::unique_ptr<dsp::FrameProcessor> proc_;
    std::unique_ptr<buffer::RingBuffer> ring_;
    std::vector<float> frameAccum_;     // 6000 모일 때까지 누적
    std::atomic<float> db_{-120}, leq_{-120}, lmax_{-120};
    AAudioStream* stream_ = nullptr;
    // 이벤트 저장 상태
    bool capturingPost_ = false;
    int postRemaining_ = 0;
    int eventSeq_ = 0;
    static constexpr int kSampleRate = 48000;
    static constexpr int kFrame = 6000;       // 0.125s
    static constexpr int kPre = kSampleRate*5; // 5s
    static constexpr int kPost = kSampleRate*5;
};
}
```

- [ ] **Step 2: 구현 — `app/src/main/cpp/engine/AudioEngine.cpp`**

```cpp
#include "engine/AudioEngine.h"
#include "io/WavWriter.h"

namespace engine {
static aaudio_data_callback_result_t cbTramp(AAudioStream*, void* ud, void* audio, int32_t n) {
    static_cast<AudioEngine*>(ud)->onAudio(static_cast<const float*>(audio), n);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
AudioEngine::AudioEngine(const std::string& dir, float offset, float thr, EventCallback cb)
    : outDir_(dir), offset_(offset), threshold_(thr), cb_(std::move(cb)) {
    proc_ = std::make_unique<dsp::FrameProcessor>(kSampleRate, kFrame, offset_);
    ring_ = std::make_unique<buffer::RingBuffer>(kSampleRate * 30); // 30s
    frameAccum_.reserve(kFrame);
}
bool AudioEngine::start() {
    AAudioStreamBuilder* b; if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return false;
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setSampleRate(b, kSampleRate);
    AAudioStreamBuilder_setChannelCount(b, 1);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setInputPreset(b, AAUDIO_INPUT_PRESET_UNPROCESSED); // OS 보정 OFF
    AAudioStreamBuilder_setDataCallback(b, cbTramp, this);
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &stream_);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK) return false;
    return AAudioStream_requestStart(stream_) == AAUDIO_OK;
}
void AudioEngine::stop() {
    if (stream_) { AAudioStream_requestStop(stream_); AAudioStream_close(stream_); stream_ = nullptr; }
}
Metrics AudioEngine::latest() const { return { db_.load(), leq_.load(), lmax_.load() }; }

void AudioEngine::onAudio(const float* data, int32_t n) {
    ring_->write(data, n);                       // 원음 항상 버퍼링
    for (int32_t i = 0; i < n; ++i) {
        frameAccum_.push_back(data[i]);
        if ((int)frameAccum_.size() == kFrame) {
            auto res = proc_->pushFrame(frameAccum_.data(), kFrame);
            db_.store(res.db); leq_.store(res.leq); lmax_.store(res.lmax);
            maybeSaveEvent(res.db);
            frameAccum_.clear();
        }
    }
    if (capturingPost_) {
        postRemaining_ -= n;
        if (postRemaining_ <= 0) {
            auto seg = ring_->extract(kPre, kPost);
            std::string path = outDir_ + "/event_" + std::to_string(eventSeq_++) + ".wav";
            if (io::writeWav(path, seg.data(), seg.size(), kSampleRate))
                cb_(path, lmax_.load(), leq_.load());
            capturingPost_ = false;
        }
    }
}
void AudioEngine::maybeSaveEvent(float frameDb) {
    if (!capturingPost_ && frameDb >= threshold_) {
        capturingPost_ = true; postRemaining_ = kPost; // 이후 5초 더 모은 뒤 저장
    }
}
}
```

- [ ] **Step 3: 앱 빌드 (네이티브 라이브러리 컴파일 확인)**

Run: Android Studio에서 `assembleDebug` 또는 `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — `libaudiocore.so` 생성, 링크 에러 없음.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/cpp/engine/AudioEngine.*
git commit -m "feat: AAudio capture engine with pipeline and event detection"
```

---

## Task 8: JNI 브릿지

**Files:**
- Modify: `app/src/main/cpp/jni/native-lib.cpp`
- Create: `app/src/main/java/com/noisemeter/app/audio/AudioEngine.kt`

JNI로 start/stop/poll과 이벤트 콜백을 노출한다. 이벤트 콜백은 네이티브 콜백 스레드에서 발생하므로, JNI에서 미리 잡아둔 전역 `JavaVM`으로 스레드를 attach해 Kotlin 메서드를 호출한다.

- [ ] **Step 1: 구현 — `app/src/main/cpp/jni/native-lib.cpp`**

```cpp
#include <jni.h>
#include <string>
#include <memory>
#include "engine/AudioEngine.h"

static JavaVM* gVm = nullptr;
static jobject gListener = nullptr;       // global ref to Kotlin listener
static std::unique_ptr<engine::AudioEngine> gEngine;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) { gVm = vm; return JNI_VERSION_1_6; }

static void emitEvent(const std::string& path, float peak, float leq) {
    if (!gListener) return;
    JNIEnv* env; bool attached = false;
    if (gVm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) { gVm->AttachCurrentThread(&env, nullptr); attached = true; }
    jclass cls = env->GetObjectClass(gListener);
    jmethodID m = env->GetMethodID(cls, "onEvent", "(Ljava/lang/String;FF)V");
    jstring jp = env->NewStringUTF(path.c_str());
    env->CallVoidMethod(gListener, m, jp, peak, leq);
    env->DeleteLocalRef(jp);
    if (attached) gVm->DetachCurrentThread();
}

extern "C" JNIEXPORT void JNICALL
Java_com_noisemeter_app_audio_AudioEngine_nativeStart(JNIEnv* env, jobject,
        jstring outDir, jfloat offset, jfloat threshold, jobject listener) {
    gListener = env->NewGlobalRef(listener);
    const char* dir = env->GetStringUTFChars(outDir, nullptr);
    gEngine = std::make_unique<engine::AudioEngine>(std::string(dir), offset, threshold, &emitEvent);
    env->ReleaseStringUTFChars(outDir, dir);
    gEngine->start();
}
extern "C" JNIEXPORT void JNICALL
Java_com_noisemeter_app_audio_AudioEngine_nativeStop(JNIEnv* env, jobject) {
    if (gEngine) { gEngine->stop(); gEngine.reset(); }
    if (gListener) { env->DeleteGlobalRef(gListener); gListener = nullptr; }
}
// [db, leq, lmax]
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_noisemeter_app_audio_AudioEngine_nativePoll(JNIEnv* env, jobject) {
    engine::Metrics m = gEngine ? gEngine->latest() : engine::Metrics{};
    jfloatArray arr = env->NewFloatArray(3);
    float vals[3] = { m.db, m.leq, m.lmax };
    env->SetFloatArrayRegion(arr, 0, 3, vals);
    return arr;
}
```

- [ ] **Step 2: 구현 — `app/src/main/java/com/noisemeter/app/audio/AudioEngine.kt`**

```kotlin
package com.noisemeter.app.audio

class AudioEngine(private val listener: Listener) {
    interface Listener { fun onEvent(wavPath: String, peakDb: Float, leq: Float) }

    companion object { init { System.loadLibrary("audiocore") } }

    fun start(outDir: String, calibrationOffset: Float, thresholdDb: Float) =
        nativeStart(outDir, calibrationOffset, thresholdDb, listener)
    fun stop() = nativeStop()
    /** @return [db, leq, lmax] */
    fun poll(): FloatArray = nativePoll()

    private external fun nativeStart(outDir: String, offset: Float, threshold: Float, listener: Listener)
    private external fun nativeStop()
    private external fun nativePoll(): FloatArray
}
```

- [ ] **Step 3: 빌드하여 JNI 시그니처 일치 확인**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (런타임 검증은 Task 12에서 실기기로.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/cpp/jni/native-lib.cpp app/src/main/java/com/noisemeter/app/audio/AudioEngine.kt
git commit -m "feat: JNI bridge for start/stop/poll and event callback"
```

---

## Task 9: Room — NoiseEvent 저장

**Files:**
- Create: `app/src/main/java/com/noisemeter/app/data/NoiseEvent.kt`, `NoiseEventDao.kt`, `AppDatabase.kt`
- Test: `app/src/androidTest/java/com/noisemeter/app/data/NoiseEventDaoTest.kt`
- Modify: `app/build.gradle.kts` (Room 의존성 + KSP)

- [ ] **Step 1: Room 의존성 추가 — `app/build.gradle.kts`**

```kotlin
plugins { id("com.google.devtools.ksp") }
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
```

- [ ] **Step 2: 실패하는 계측 테스트 작성 — `NoiseEventDaoTest.kt`**

```kotlin
package com.noisemeter.app.data
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals

class NoiseEventDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: NoiseEventDao
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.noiseEventDao()
    }
    @After fun close() = db.close()

    @Test fun insertAndReadBack() = runBlocking {
        dao.insert(NoiseEvent(timestamp = 1000L, peakDb = 71f, leq = 63f,
            wavPath = "/p/e.wav", moved = false, tag = null, note = null))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(71f, all[0].peakDb)
    }
}
```

- [ ] **Step 3: 테스트 실행하여 실패 확인**

Run: `./gradlew connectedDebugAndroidTest` (실기기/에뮬 필요)
Expected: FAIL — `NoiseEvent`/`AppDatabase`/`NoiseEventDao` 미정의(컴파일 실패).

- [ ] **Step 4: 구현 — 엔티티/DAO/DB**

`NoiseEvent.kt`:
```kotlin
package com.noisemeter.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "noise_events")
data class NoiseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val peakDb: Float,
    val leq: Float,
    val wavPath: String,
    val moved: Boolean,
    val tag: String?,
    val note: String?,
)
```

`NoiseEventDao.kt`:
```kotlin
package com.noisemeter.app.data
import androidx.room.*

@Dao
interface NoiseEventDao {
    @Insert suspend fun insert(e: NoiseEvent): Long
    @Query("SELECT * FROM noise_events ORDER BY timestamp DESC") suspend fun getAll(): List<NoiseEvent>
    @Update suspend fun update(e: NoiseEvent)
}
```

`AppDatabase.kt`:
```kotlin
package com.noisemeter.app.data
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NoiseEvent::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noiseEventDao(): NoiseEventDao
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew connectedDebugAndroidTest`
Expected: PASS (`insertAndReadBack`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/noisemeter/app/data/ app/src/androidTest/ app/build.gradle.kts
git commit -m "feat: Room storage for noise events"
```

---

## Task 10: Foreground Service — 엔진 구동 + 폴링

**Files:**
- Create: `app/src/main/java/com/noisemeter/app/audio/MeasurementService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (권한 + 서비스 등록)

- [ ] **Step 1: 매니페스트에 권한·서비스 추가 — `AndroidManifest.xml`**

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<!-- <application> 안에 -->
<service android:name=".audio.MeasurementService"
    android:foregroundServiceType="microphone" android:exported="false"/>
```

- [ ] **Step 2: 구현 — `MeasurementService.kt`**

엔진을 시작하고, 0.125초마다 `poll()`한 값을 `StateFlow`로 노출한다. 이벤트 콜백은 Room에 기록한다.

```kotlin
package com.noisemeter.app.audio
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.noisemeter.app.data.AppDatabase
import com.noisemeter.app.data.NoiseEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.room.Room

class MeasurementService : Service(), AudioEngine.Listener {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engine = AudioEngine(this)
    private lateinit var db: AppDatabase

    companion object {
        val metrics = MutableStateFlow(floatArrayOf(-120f, -120f, -120f)) // [db,leq,lmax]
        const val CH = "measure"
    }

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "noise.db").build()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startForeground(1, notification())
        val outDir = getExternalFilesDir("events")!!.absolutePath
        engine.start(outDir, calibrationOffset = 94.0f, thresholdDb = 55.0f)
        scope.launch {
            while (isActive) { metrics.value = engine.poll(); delay(125) }
        }
        return START_STICKY
    }

    override fun onEvent(wavPath: String, peakDb: Float, leq: Float) {
        scope.launch {
            db.noiseEventDao().insert(NoiseEvent(
                timestamp = System.currentTimeMillis(), peakDb = peakDb, leq = leq,
                wavPath = wavPath, moved = MovementDetector.movedFlag, tag = null, note = null))
        }
    }

    override fun onDestroy() { engine.stop(); scope.cancel(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null

    private fun notification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(CH, "측정", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("소음 측정 중").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
    }
}
```

(`calibrationOffset = 94.0f`는 임시값 — 실제 외장마이크 보정값으로 교체. `MovementDetector.movedFlag`는 Task 11에서 정의.)

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (단, `MovementDetector` 미정의면 Task 11 먼저 — 순서상 Task 11을 이 직전에 둬도 됨).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/noisemeter/app/audio/MeasurementService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: foreground measurement service with polling loop"
```

---

## Task 11: 센서 이동 감지

**Files:**
- Create: `app/src/main/java/com/noisemeter/app/sensor/MovementDetector.kt`

가속도 벡터 크기가 중력(≈9.8)에서 임계 이상 벗어나거나 자이로 각속도가 크면 "이동됨" 플래그를 세운다. 측정은 중단하지 않는다.

- [ ] **Step 1: 구현 — `MovementDetector.kt`**

```kotlin
package com.noisemeter.app.sensor
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

object MovementDetector : SensorEventListener {
    @Volatile var movedFlag = false; private set
    private const val ACCEL_THRESHOLD = 1.5f   // m/s^2, 중력 대비 편차
    private const val GYRO_THRESHOLD = 0.8f     // rad/s

    fun start(ctx: Context) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    fun reset() { movedFlag = false }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val mag = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2])
                if (abs(mag - SensorManager.GRAVITY_EARTH) > ACCEL_THRESHOLD) movedFlag = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                val rot = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2])
                if (rot > GYRO_THRESHOLD) movedFlag = true
            }
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
```

- [ ] **Step 2: 서비스에서 센서 시작 연결 — `MeasurementService.onStartCommand` 내 `engine.start(...)` 직후 추가**

```kotlin
MovementDetector.reset()
MovementDetector.start(this)
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/noisemeter/app/sensor/MovementDetector.kt app/src/main/java/com/noisemeter/app/audio/MeasurementService.kt
git commit -m "feat: accelerometer/gyroscope movement detection flag"
```

---

## Task 12: 최소 Compose UI + 엔드투엔드 실기기 검증

**Files:**
- Create: `app/src/main/java/com/noisemeter/app/ui/MeasureScreen.kt`, `EventListScreen.kt`
- Modify: `app/src/main/java/com/noisemeter/app/MainActivity.kt`

- [ ] **Step 1: 측정 화면 — `MeasureScreen.kt`**

`MeasurementService.metrics`를 구독해 실시간 dB(A)/Leq/Lmax를 표시하고, 시작/정지로 서비스를 제어한다.

```kotlin
package com.noisemeter.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noisemeter.app.audio.MeasurementService
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MeasureScreen(onStart: () -> Unit, onStop: () -> Unit) {
    val m by MeasurementService.metrics.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("${m[0].toInt()}", fontSize = 72.sp)
        Text("dB(A)")
        Spacer(Modifier.height(16.dp))
        Row { Text("Leq ${m[1].toInt()}   "); Text("Lmax ${m[2].toInt()}") }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart) { Text("측정 시작") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStop) { Text("측정 정지") }
    }
}
```

- [ ] **Step 2: 이벤트 목록 화면 — `EventListScreen.kt`**

```kotlin
package com.noisemeter.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noisemeter.app.data.NoiseEvent

@Composable
fun EventListScreen(events: List<NoiseEvent>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(events) { e ->
            ListItem(
                headlineContent = { Text("${e.peakDb.toInt()} dB") },
                supportingContent = { Text("${if (e.moved) "📍이동 · " else ""}${e.wavPath.substringAfterLast('/')}") })
            Divider()
        }
    }
}
```

- [ ] **Step 3: MainActivity에서 권한 요청 + 화면 연결 — `MainActivity.kt`**

`RECORD_AUDIO`, `POST_NOTIFICATIONS` 런타임 권한을 요청하고, 시작/정지 버튼이 `MeasurementService`를 `startForegroundService`/`stopService`로 제어하게 한다. (표준 `ActivityResultContracts.RequestMultiplePermissions` 사용. 권한 승인 후 `MeasureScreen` 표시.)

```kotlin
package com.noisemeter.app
import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.noisemeter.app.audio.MeasurementService
import com.noisemeter.app.ui.MeasureScreen

class MainActivity : ComponentActivity() {
    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {}
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        setContent {
            MeasureScreen(
                onStart = { startForegroundService(Intent(this, MeasurementService::class.java)) },
                onStop = { stopService(Intent(this, MeasurementService::class.java)) })
        }
    }
}
```

- [ ] **Step 4: 실기기 엔드투엔드 검증 (수동)**

1. 외장마이크를 USB-C/잭으로 연결한 실기기에 설치: `./gradlew installDebug`
2. 앱 실행 → 권한 허용 → "측정 시작".
3. **검증 A:** 조용할 때 낮은 dB, 손뼉/큰 소리에 dB(A) 값이 즉시 상승하는지 (실시간 폴링 동작).
4. **검증 B:** 임계(55dB)를 넘는 소리를 5초 이상 낸 뒤, `getExternalFilesDir("events")`에 `event_*.wav`가 생기고 약 10초 길이로 재생되는지 (`adb pull`로 받아 확인).
5. **검증 C:** 측정 중 기기를 흔든 뒤 발생한 이벤트의 `moved`가 true로 기록되는지 (DB Inspector 또는 로그).
6. **검증 D:** 30분 이상 연속 측정 시 크래시·메모리 증가 없는지 (Android Studio Profiler).

Expected: A~D 모두 통과. 실패 시 해당 Task로 돌아가 원인 수정.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/noisemeter/app/ui/ app/src/main/java/com/noisemeter/app/MainActivity.kt
git commit -m "feat: minimal Compose UI and end-to-end measurement verification"
```

---

## Self-Review 메모 (작성자 확인용)

- **Spec 커버리지:** Raw Audio 수집(T7) · 0.125초 dB(A)(T4) · A-가중(T3) · Leq/Lmax(T4) · 순환버퍼 전후 N초 저장(T5,T7) · WAV(T6) · 캘리브레이션 오프셋(T2,T10) · 센서 이동감지(T11) · 로컬 DB(T9) · Foreground 안정성(T10) · 최소 UI(T12). **범위 밖(다음 플랜):** 지도/GPS, 다국어, 결제/광고, 상세/저장/언어 화면, 더미 AI — 설계 문서 11~12장.
- **임시값:** `calibrationOffset=94.0f`는 플레이스홀더 — 실제 외장마이크 캘리브레이션 데이터로 교체 필요(클라이언트 제공 예정).
- **A-weighting 계수:** Task 3 테스트가 정확성 기준. 계수가 테스트를 통과 못 하면 검증된 표준 계수로 교체.
