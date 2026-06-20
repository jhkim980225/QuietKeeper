# QuietKeeper

층간소음을 **객관적으로 측정·기록·입증**하는 안드로이드 앱. 외장 마이크로 보정 없는 Raw Audio를 수집해 국제 표준(IEC 61672) A-가중 dB(A)로 소음을 계측하고, 기준 초과 순간의 원음을 자동 저장합니다.

> **상태:** Phase 1 (측정 코어) 완료 — 네이티브 측정 엔진 + 최소 UI가 동작하며 검증됨.

## 핵심 기능 (Phase 1)

- **NDK 오디오 코어 (C++):** AAudio `UNPROCESSED` 입력(48kHz·mono·float)으로 OS 보정(AGC·노이즈 억제)이 배제된 Raw Audio 수집
- **A-가중 dB(A) 계측:** 0.125초 프레임마다 A-weighting IIR 필터 → RMS → dB(A), **Leq**(등가소음도)·**Lmax**(최고소음도) 집계
- **순환 버퍼 자동 녹음:** 30초 링버퍼로 상시 버퍼링, 임계 초과 시 **전후 5초(총 10초)** 원음을 WAV로 로컬 저장
- **리얼타임 안전 설계:** 오디오 콜백 스레드에서 힙 할당·파일 I/O 없음 — 이벤트 저장은 워커 스레드로 오프로드
- **센서 이동 감지:** 가속도/자이로로 기기 이동을 감지해 부정 측정 플래그 표시
- **로컬 저장:** Room(SQLite)에 이벤트 메타데이터, 외부저장소에 원음 WAV
- **상시 측정:** Foreground Service로 장시간 안정 측정

## 아키텍처

```
[Jetpack Compose UI]  ── 0.125초 폴링 ──┐
        │ StateFlow                      │
[MeasurementService (Foreground)]        │ JNI poll: [db, leq, lmax]
        │ JNI                            │
[native-lib.cpp (JNI 브릿지)] ───────────┘
        │                         ▲ 이벤트 콜백(워커 스레드)
[engine::AudioEngine]  ── 워커 스레드 ──→ WAV 저장 + Room insert
        │  process() (오디오 콜백 스레드: 무할당·무 I/O)
        ├─ dsp::FrameProcessor (A-weighting → RMS → dB(A), Leq/Lmax)
        ├─ buffer::RingBuffer (30s, alloc-free extractInto)
        └─ io::WavWriter (무결성 검사)
[engine::AAudioSource]  ── UNPROCESSED 48kHz mono float 입력
```

## 기술 스택

| 영역 | 기술 |
|---|---|
| 오디오 코어 | C++17, Android NDK, AAudio |
| 앱 | Kotlin, Jetpack Compose, Room, Foreground Service |
| 테스트 | GoogleTest (네이티브 DSP), AndroidX instrumented (Room) |
| 빌드 | Gradle (AGP 8.7.3), CMake, KSP |

minSdk 26 · compileSdk 35

## 프로젝트 구조

```
app/src/main/cpp/          # 네이티브 오디오 코어 (C++)
  dsp/      Decibel, AWeighting, FrameProcessor
  buffer/   RingBuffer
  io/       WavWriter
  engine/   AudioEngine, AAudioSource
  jni/      native-lib (JNI 브릿지)
app/src/main/java/com/quietkeeper/app/
  audio/    AudioEngine(Kotlin), MeasurementService
  data/     Room: NoiseEvent, DAO, AppDatabase
  sensor/   MovementDetector
  ui/       MeasureScreen, EventListScreen
app/src/test/cpp/          # GoogleTest 네이티브 단위 테스트
docs/                      # 설계 문서, 구현 계획, UI 목업, 실기기 테스트 절차
```

## 빌드 & 테스트

```bash
# APK 빌드
./gradlew :app:assembleDebug

# Room 계측 테스트 (에뮬레이터/기기 필요)
./gradlew :app:connectedDebugAndroidTest
```

**네이티브 DSP 단위 테스트** (호스트 컴파일러 없이 NDK로 android-x86_64 빌드 → 에뮬레이터 실행):

```bash
cmake -S app/src/test/cpp -B build-tests -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=<NDK>/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=x86_64 -DANDROID_PLATFORM=android-26
cmake --build build-tests
adb push build-tests/audiocore_tests /data/local/tmp/ && adb shell /data/local/tmp/audiocore_tests
# → 23 tests pass (Decibel, AWeighting, FrameProcessor, RingBuffer, WavWriter, AudioEngine)
```

실제 마이크 기반 엔드투엔드 검증 절차는 [`docs/MEASUREMENT_CORE_DEVICE_TEST.md`](docs/MEASUREMENT_CORE_DEVICE_TEST.md) 참고.

## 로드맵 (Phase 2+)

- **다국어(i18n)** — 한·영·일·중·스페인·동남아 등 진입 시 언어 선택
- **지도/GPS** — 측정 위치 저장 (Google Maps + 역지오코딩)
- **수익화** — 무료(재생 5회/일·광고) / 프로(무제한 재생·내보내기·광고 제거)
- **Firebase** — 클라우드 백업·다기기 조회, 무결성 해시, 캘리브레이션 동기화
- **모니터링 웹** — 실시간 상태·원격 제어, 캘린더·노트, 레포트 신청
- **AI** — 층간소음 판별·소음 종류 분류 (현재 더미 인터페이스 자리)

설계 상세는 [`docs/superpowers/specs/`](docs/superpowers/specs/), 구현 계획은 [`docs/superpowers/plans/`](docs/superpowers/plans/) 참고.

## 라이선스

Proprietary. © 2026 QuietKeeper.
