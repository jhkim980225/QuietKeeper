# QuietKeeper — Monitoring Web App

A Vite + React + TypeScript single-page app for monitoring a QuietKeeper noise
device. It is independent of the Android code in this repo.

It runs in two modes:

- **Demo / mock mode (default):** no configuration needed. An in-memory data
  source serves believable fake data (a live-ticking dB readout, sample noise
  events, etc.). A banner reads "데모 모드 (Firebase 미설정)".
- **Live mode:** when Firebase env vars are present, it transparently switches
  to a Firestore-backed data source. No code changes required.

## Quick start

```bash
cd web
npm install
npm run dev      # http://localhost:5173 (mock mode)
npm run build    # tsc + vite build -> dist/
```

## Views

1. **대시보드** — device online status, a big live dB(A) readout that polls,
   Leq / Lmax, and 측정 일시정지 / 재개 remote-control buttons (writes a
   `command` to the device doc).
2. **캘린더 / 노트** — a day picker plus that day's over-threshold events
   (time, dB(A), address, editable AI tag, editable note). Edits are written
   back. Each event has a local-audio player stub (the device's local web
   server isn't built yet, so it shows "기기 로컬 네트워크 연결 시 재생").
3. **레포트 신청** — pick a start/end date + a 음원 제공 동의 checkbox + a
   신청 button → stores a request in `reportRequests`. Shows a success toast.
   (No payment, no report rendering — just the request.)

## Connecting real Firebase

1. Create a Firebase project and a Cloud Firestore database.
2. In Project settings → Your apps, grab the web SDK config.
3. Copy the env template and fill it in:

   ```bash
   cp .env.example .env
   ```

   Set at least `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_PROJECT_ID`, and
   `VITE_FIREBASE_APP_ID`. The presence of these three flips the app from mock
   to Firestore (`src/data/index.ts` → `hasFirebaseConfig()`).
4. Restart `npm run dev`. The demo banner disappears and data comes from
   Firestore.

`.env` is gitignored; only `.env.example` is committed.

## Expected Firestore collections

| Path                  | Shape                                                                    |
| --------------------- | ------------------------------------------------------------------------ |
| `devices/{deviceId}`  | `{ online, measuring, currentDb, leq, lmax, command }` — watched live    |
| `events`              | `{ timestamp(ms), peakDb, leq, address, tag, note, moved, wavUrl }`      |
| `reportRequests`      | `{ start, end, consent, deviceId, createdAt }` — appended on submit      |

`deviceId` defaults to `device-001` (override with `VITE_QK_DEVICE_ID`).
Remote control writes `devices/{deviceId}.command = 'pause' | 'resume'`; the
device firmware is expected to act on it and clear it. The app signs in
anonymously so Firestore security rules can require an authenticated user.

## Data-source abstraction

All data access goes through the `DataSource` interface in
`src/data/types.ts`. Two implementations:

- `src/data/mock.ts` — `MockDataSource` (in-memory, no network).
- `src/data/firestore.ts` — `FirestoreDataSource` (firebase SDK).

`src/data/index.ts` picks one at startup:

```ts
export const data: DataSource = isMockMode
  ? new MockDataSource()
  : new FirestoreDataSource()
```

Views import only `data` and the types — they never know which backend is live.

## Not built yet

- The Firebase project itself (provision it, then fill `.env`).
- The device's **local web server** that streams captured WAV audio over the
  home LAN — the audio player is a labeled stub until then.
- Payment and report rendering (explicitly out of scope).
