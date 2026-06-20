# Measurement Core — Real-Device End-to-End Checklist

The emulator has no usable microphone, so the full audio-capture and WAV-on-threshold
pipeline must be validated on a physical Android device (API 26+; API 28+ preferred for
the UNPROCESSED audio preset).

## Setup

1. Connect an external microphone to the device (USB-C adapter or 3.5 mm jack).
2. Enable Developer Options and USB debugging; connect device to workstation.
3. Install the debug APK:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   # or:
   ./gradlew installDebug
   ```
4. Grant runtime permissions (the UI prompts automatically on first launch; confirm both):
   - `android.permission.RECORD_AUDIO`
   - `android.permission.POST_NOTIFICATIONS`
   Alternatively grant via adb:
   ```
   adb shell pm grant com.quietkeeper.app android.permission.RECORD_AUDIO
   adb shell pm grant com.quietkeeper.app android.permission.POST_NOTIFICATIONS
   ```

## A — Real-time level display

1. Launch the app; the measure screen shows `-120 dB(A)` (service not yet started).
2. Tap **측정 시작**.
3. Clap loudly or make a sharp sound near the mic.
4. **Pass:** The large dB(A) readout rises immediately (within ~125 ms polling interval)
   and falls back after the sound stops.

## B — WAV event capture

1. With the service running (step A), make a continuous sound above **55 dB(A)** for at
   least **5 seconds**.
2. Stop making the sound.
3. Pull the events directory from the device:
   ```
   adb pull /sdcard/Android/data/com.quietkeeper.app/files/events/ ./pulled_events/
   ```
4. **Pass:**
   - At least one `event_*.wav` file is present.
   - The file is approximately **10 seconds** long (5 s pre-event + 5 s post-event).
   - Plays back clearly via any audio player.

## C — Movement flag (shake detection)

1. Start a new recording session; trigger a sound event (see B).
2. **While the event is being recorded**, shake the device with moderate force for 1–2 s.
3. Tap **측정 정지**, then tap **이벤트 보기**.
4. **Pass:** The event row displays **📍이동됨** in the supporting text.

## D — Stability / memory (long-duration run)

1. Start the service and leave it running continuously for **30+ minutes** with ambient
   sound present.
2. Monitor in Android Studio Profiler (Memory tab):
   - Attach to `com.quietkeeper.app`.
   - Watch Heap (Java + Native) over time.
3. **Pass:**
   - No `FATAL EXCEPTION` or `ANR` in `adb logcat`.
   - Memory usage is stable (no unbounded linear growth after warm-up).
   - CPU stays below ~10 % on a mid-range device.

## Notes

- If the UNPROCESSED audio preset is unavailable on the device, the engine falls back
  gracefully; dB readings may be less accurate but the app must not crash.
- The calibration offset constant (`CALIBRATION_OFFSET = 94.0f`) in `MeasurementService`
  is a placeholder; real external-mic calibration requires a reference sound source at a
  known SPL level and adjustment of the constant accordingly.
- WAV files accumulate indefinitely; add a housekeeping step (manual `adb shell rm` or
  a future in-app cleanup screen) to avoid filling device storage during extended testing.
