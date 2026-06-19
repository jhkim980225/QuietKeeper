# 층간소음 측정 코어 설계 (Android Kotlin + NDK)

- 작성일: 2026-06-20
- 범위: **측정 앱 코어 MVP** (전체 서비스 4개 시스템 중 1번째)
- 스택: Android Kotlin + NDK(C++), Jetpack Compose, Room

---

## 1. 배경 및 목표

기존 Flutter 프로토타입은 휴대폰 자체 오디오 프로세싱(AGC 등)과 Flutter의
GC·부동소수점 딜레이로 측정 신뢰도 이슈가 있었다. 이를 해결하기 위해 **순수
Kotlin 네이티브 + NDK** 로 재구성한다. UI는 Kotlin/Compose, 오디오 수집과
DSP 연산은 전부 C++ 네이티브에서 처리해 OS 보정이 없는 Raw Audio를 안정적으로
확보하는 것이 핵심이다.

측정 단위는 **국제 표준(IEC 61672)** 인 A-가중 dB(A) 기반으로 하여, 한국뿐
아니라 타 국가로도 동일 로직으로 확장 가능하게 한다.

## 2. 이번 범위 (Scope)

### 포함
- NDK(AAudio) 보정 없는 Raw Audio 수집 (UNPROCESSED)
- 0.125초 주기 A-가중 dB(A) 계산 + Leq(1분 등가소음도) / Lmax(최고소음도) 집계
- 순환(링) 버퍼 → 기준 초과 시 전후 N초 원음 로컬 WAV 저장
- 캘리브레이션 오프셋 상수 1개 (외장마이크 보정)
- 가속도/자이로 센서 기반 기기 이동감지 (부정 측정 플래그)
- 저장 이벤트 목록 + 태그/노트 (로컬 DB)
- 최소 Compose UI + 상시 측정용 Foreground Service

### 제외 (다음 단계)
- Firebase 업로드 / SHA-256 무결성 해시 / 시리얼별 캘리브레이션 파일 동기화
- 로컬 음원 재생(플레이어), 로컬 웹서버 스트리밍
- 모니터링 웹, 더미 AI 연동
- 원격 제어, 레포트 신청 폼

## 3. 아키텍처

3계층 구조. 무거운 연산은 전부 네이티브에서 처리한다.

```
[Kotlin / Jetpack Compose UI]
      │  ① 8Hz 폴링: 최신 dB(A)/Leq/Lmax 값 읽기
      │  ② 이벤트 저장 시 1회 콜백
   [ JNI 브릿지 ]
      │
[C++ 오디오 코어]
   - AAudio 입력 콜백 스레드
   - A-가중 IIR 필터 → RMS → dB(A)
   - Leq/Lmax 집계
   - 30초 raw PCM 링버퍼
   - 임계 초과 시 WAV 저장
      │
[AAudio: 외장마이크 UNPROCESSED 입력]
```

**통신 방식:** Kotlin이 0.125초 주기로 네이티브의 "최신 측정값"을 폴링한다
(네이티브→Kotlin 콜백 방식보다 단순하고 스레드 안전). 이벤트 WAV 저장이
발생한 순간에만 네이티브가 Kotlin으로 콜백 1회를 보낸다.

## 4. 오디오 코어 (DSP 파이프라인)

- **입력:** AAudio, 48kHz / mono / float(PCM_FLOAT) / UNPROCESSED
  (AGC·노이즈제거·자동게인 OFF, OS 보정 배제)
- **프레임:** 0.125초 = 6000 샘플 단위 처리
- **연산 순서:** A-가중 IIR 필터(biquad 캐스케이드) → 프레임 RMS →
  `dB(A) = 20·log10(RMS) + calibrationOffset`
- **집계:**
  - Leq: 1분 등가소음도 (0.125초 레벨들의 에너지 평균을 로그 환산)
  - Lmax: 구간 내 최고 0.125초 레벨
- **캘리브레이션:** 외장마이크 기준 오프셋 상수 1개 (BuildConfig/설정값).
  시리얼별 파일 동기화는 다음 단계로 미룸.

## 5. 순환 버퍼 & 이벤트 저장

- 네이티브에 **30초 분량 raw PCM 링버퍼**를 상시 유지 (고정 크기, 메모리 누수
  방지)
- dB(A)가 임계값 초과 → **전 5초 + 후 5초 = 10초** 구간을 WAV로 저장
  - 저장 위치: 앱 전용 외부저장소 (`getExternalFilesDir`)
  - N초(전/후), 임계값(dB)은 설정 가능한 파라미터 (기본 5s/5s)
- 저장 직후 Kotlin으로 콜백 → Room에 이벤트 메타데이터 기록

## 6. 센서 이동감지

- Kotlin `SensorManager`로 `TYPE_ACCELEROMETER` + `TYPE_GYROSCOPE` 구독
- 임계 이상 움직임 감지 시 현재 측정 세션/이벤트에 **"이동됨" 플래그** 표시
- 측정 자체는 중단하지 않음 (부정 측정 여부 판단 근거만 남김)

## 7. 데이터 저장 (전부 로컬)

| 저장소 | 내용 | 형식 |
|---|---|---|
| Room (SQLite) | 이벤트 메타데이터: 발생시각, dB값, Leq, Lmax, WAV경로, 이동플래그, 태그, 노트 | DB 테이블 |
| 앱 전용 외부저장소 | 실제 원음 | WAV 파일 |

Room의 `wavPath` 컬럼이 메타데이터와 실제 파일을 연결한다. 이 단계에서는
모든 데이터가 기기 내부에만 존재하며, 클라우드 동기화는 Firebase 단계에서
추가한다.

### Room 엔티티 (초안)

```
NoiseEvent
  id: Long (PK)
  timestamp: Long          // epoch millis
  peakDb: Float            // 이벤트 Lmax
  leq: Float               // 구간 Leq
  wavPath: String
  moved: Boolean           // 센서 이동 플래그
  tag: String?             // 사용자 태그
  note: String?            // 사용자 노트
```

## 8. UI (Jetpack Compose)

- **측정 화면:** 실시간 dB(A) 표시, Leq/Lmax, 시작/정지 버튼, 측정 상태
- **이벤트 목록 화면:** 저장된 이벤트 리스트, 항목별 태그·노트 편집
  (음원 재생은 다음 단계)
- **Foreground Service:** 장시간 상시 측정 시 OS에 의한 종료 방지, 메모리
  안정성 확보 (비기능 요구사항)

## 9. 모듈 경계

| 모듈 | 책임 | 의존 |
|---|---|---|
| `audio-core` (C++) | AAudio 입력, A-가중 DSP, dB(A)/Leq/Lmax, 링버퍼, WAV 저장 | AAudio, NDK |
| `jni-bridge` | 네이티브 ↔ Kotlin 함수/콜백 | audio-core |
| `measurement` (Kotlin) | Foreground Service, 폴링 루프, 센서 구독, 이벤트 수신 | jni-bridge, SensorManager |
| `data` (Kotlin) | Room DB, 이벤트 CRUD, 파일 경로 관리 | Room |
| `ui` (Compose) | 측정/목록 화면 | measurement, data |

## 10. 비기능 요구사항

- 장시간 측정 시 메모리 누수·크래시 방지: 고정 크기 링버퍼, 네이티브 버퍼
  재사용, Foreground Service
- 기기별 마이크 편차 최소화: 외장마이크 + 캘리브레이션 오프셋

## 11. 다음 단계 (이번 범위 밖, 로드맵)

1. **Firebase 연동:** 메타데이터 → Firestore, WAV → Storage, SHA-256
   무결성 해시, 시리얼별 캘리브레이션 파일 동기화, Auth
2. **모니터링 웹:** 실시간 폴링/원격 제어, 캘린더·노트, 로컬 음원 스트리밍,
   레포트 신청 폼
3. **더미 AI:** 녹음 완료 후 호출 인터페이스 + 더미 모델
