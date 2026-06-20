# QuietKeeper

층간소음을 **객관적으로 측정·기록·입증**하는 안드로이드 앱 + 모니터링 웹. 외장 마이크로 보정 없는 Raw Audio를 수집해 국제 표준(IEC 61672) A-가중 dB(A)로 소음을 계측하고, 기준 초과 순간의 원음을 자동 저장하며, 위치·AI 태그·무결성 해시까지 함께 남깁니다.

> **상태:** Phase 1(측정 코어) + Phase 2(앱 전 기능 + 모니터링 웹) 구현 완료.
> 외부 계정/하드웨어가 필요한 부분(지도 키, 결제 상품, 광고 계정, Firebase 프로젝트, 실 AI 모델)은 **테스트값·인터페이스로 교체 대기** — 아래 "프로덕션 연동" 참고.

## 구성

| 구성요소 | 위치 | 설명 |
|---|---|---|
| **안드로이드 앱** | `app/` | Kotlin + NDK. 측정·저장·UI 전체 |
| **모니터링 웹** | `web/` | Vite + React + TS. 실시간 상태·캘린더·레포트 신청 |

## 기능

### 측정 코어 (NDK, C++)
- AAudio `UNPROCESSED` 입력(48kHz·mono·float)으로 OS 보정 배제된 Raw Audio 수집
- 0.125초 프레임 A-weighting → RMS → **dB(A)**, **Leq**·**Lmax** 집계 (IEC 61672)
- 30초 순환 버퍼 → 임계 초과 시 전후 5초(총 10초) 원음 WAV 자동 저장
- 오디오 콜백 스레드 무할당·무 I/O (이벤트 저장은 워커 스레드 오프로드)
- 가속도/자이로 기기 이동 감지 플래그

### 앱 (Kotlin / Jetpack Compose)
- **전체 화면 플로우:** 언어 선택 → 준비(상태 점검) → 측정(원형 게이지) → 저장(세션 요약) → 이벤트 목록 → 이벤트 상세 → 결제
- **다국어:** 진입 시 언어 선택 + 런타임 로케일 전환 (한·영 번역, 그 외 진행 중)
- **위치:** GPS 캡처 + 역지오코딩 주소, 지도 미리보기, 이벤트에 위치 기록
- **이벤트 상세:** 원음 재생(MediaPlayer), 태그·노트 편집, 내보내기, 삭제
- **수익화:** 무료(재생 5회/일·광고) / 프로(무제한 재생·내보내기·광고 제거), Play Billing + AdMob
- **AI:** 녹음 완료 후 더미 분류기로 자동 태깅 (실 모델 교체 시임)
- **무결성:** 타임스탬프+기기ID 조합 SHA-256 해시, CloudSync 인터페이스(Firebase 교체 대기)
- 상시 측정용 Foreground Service, 로컬 저장(Room + WAV)

### 모니터링 웹 (React)
- 실시간 상태/제어(측정 일시정지·재개), 소음 캘린더·노트 편집, 단순 레포트 신청 폼
- **목 데이터 모드**로 백엔드 없이 동작 + Firebase 설정 시 자동으로 Firestore 연결

## 빌드 & 테스트

```bash
# 앱 빌드 / 단위테스트
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest          # AI 분류기 · 무결성 해시 등

# 네이티브 DSP 단위 테스트(NDK→에뮬레이터): docs/MEASUREMENT_CORE_DEVICE_TEST.md 참고 (23 tests)

# 모니터링 웹
cd web && npm install && npm run build     # 또는 npm run dev (http://localhost:5173)
```

## 프로덕션 연동 (외부 계정/하드웨어 — 출시 전 교체)

코드는 전부 동작·빌드되며, 아래만 실제 값으로 교체하면 라이브가 됩니다:

| 항목 | 현재(개발) | 교체 위치 |
|---|---|---|
| **외장마이크 캘리브레이션** | 임시 `94.0f` | `MeasurementService.CALIBRATION_OFFSET` |
| **Google Maps 키** | `${MAPS_API_KEY}` 플레이스홀더 | `local.properties`의 `MAPS_API_KEY`, manifest meta-data |
| **Play Billing 상품** | `quietkeeper_pro_monthly` 미설정 | Play Console에 구독 상품 생성 |
| **AdMob** | 테스트 App/Unit ID | manifest meta-data, `AdBanner.kt` |
| **Firebase** | 더미 `LocalCloudSync` | `google-services.json` + `FirebaseCloudSync` 구현 → `Cloud.sync` 한 줄 교체 (`Cloud.kt` 주석 참고) |
| **AI 모델** | `DummyNoiseClassifier` | `Ai.classifier` 한 줄 교체 |
| **웹 Firebase** | 목 데이터 | `web/.env` (`.env.example` 복사) |
| **디바이스 로컬 웹서버** | 미구현(스트리밍 스텁) | 음원 LAN 스트리밍 서버 |

> 릴리스 전 체크: 페이월의 `BuildConfig.DEBUG` 디버그-프로 토글 제거 확인, 결제 서버측 영수증 검증 도입 권장.

## 프로젝트 구조

```
app/src/main/cpp/                 # 네이티브 오디오 코어 (dsp/buffer/io/engine/jni)
app/src/main/java/com/quietkeeper/app/
  audio/   AudioEngine, MeasurementService, Metrics
  data/    Room: NoiseEvent, DAO, AppDatabase (v3)
  sensor/  MovementDetector
  location/ LocationProvider
  billing/ ProStatus, BillingManager, PlayQuota
  ai/      NoiseClassifier, DummyNoiseClassifier, Ai
  cloud/   CloudSync, IntegrityHash, LocalCloudSync, Cloud
  ui/      전 화면 + theme/(glass 디자인 시스템)
web/                              # 모니터링 웹 (Vite+React+TS)
docs/                             # 설계 문서, 구현 계획, UI 목업, 실기기 테스트 절차
```

## 라이선스

Proprietary. © 2026 QuietKeeper.
