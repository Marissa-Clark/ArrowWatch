# ArrowWatch

A real-time sensor analytics system for archery, built on Wear OS + Android. The core problem is a signal-processing one: **automatically detecting the moment of shot release from a noisy wrist-worn IMU stream**, then surfacing per-session and longitudinal performance analytics on the phone.

> Built as a personal project to explore wearable sensor pipelines. The sport is archery; the data science is generalizable to any activity where you want to detect discrete events in a continuous motion signal.

---

## What it does

1. **Shot detection** — The watch samples the gyroscope at ~50 Hz during a session. A detrended-gz detector identifies *hold windows*: periods where the vertical rotation rate stays within a configurable band long enough to indicate a stable draw. Release is inferred from the window endpoint. Detected shots are matched to manual scores entered on the watch.

2. **Real-time streaming pipeline** — Sensor data, heart rate, and scored arrows flow from watch → phone over the Wear Data Layer (persistent BLE messaging). The phone app reconstructs session state in real time without polling.

3. **Post-session analytics** — Raw sensor CSV + HR time series are parsed and visualized: gz traces per round, hold-time distributions, HR at shot, near-miss events, walking intervals, and zone (score) distributions.

4. **Adaptive thresholds** — The app can suggest tighter detection parameters derived from the ratio of dismissed to kept shots, letting the user calibrate the detector to their own movement signature.

---

## Detection algorithm

Shot detection runs on the watch, then re-runs post-hoc on the phone for visualization and calibration.

```
raw gz stream (50 Hz)
    │
    ▼
detrend: gz_d = gz − 60-sec centred rolling mean          ← removes postural drift
    │
    ▼
band filter: gz_ddt ∈ [gzDdtMin, gzDdtMax]               ← rate-of-change gate
             gz_d ≥ gzMinDetrended                         ← magnitude gate
    │
    ▼
hold window: consecutive qualifying samples ≥ holdMinSec  ← duration threshold
    │
    ├──► DetectedShot  (hold time, gz stats, HR interpolated at midpoint)
    └──► NearMissShot  (qualifying window that was too short)
```

**Parameters** (`DetectionProfile`):

| Parameter | Default | Effect |
|---|---|---|
| `holdMinSec` | 4.0 s | Minimum stable draw duration |
| `gzMinDetrended` | 1.0 | Detrended gz floor (motion magnitude) |
| `gzDdtMin / Max` | −1 / +1 | Rate-of-change band (stability gate) |
| `yaw/pitch/rollMin/Max` | none | Optional orientation bounds |

---

## Sensor data

Each session produces a CSV with ~50 Hz rows:

```
time_sec, gz, yaw, pitch, roll, steps, gx, gy
```

Post-session analytics parse this into:
- `SensorSample` — raw IMU row
- `DetectedShot` — hold window with gz mean, gz stdev, HR at shot, full sensor window, detrended gz window, gz_ddt window
- `RoundAnalytics` — per-end aggregation: detected shots, walking intervals, avg HR, near-misses
- `SessionAnalytics` — session-level rollup

---

## Analytics surfaces

### Per-session (phone app)

- Score trend + hold-time trend: **line plots with ±1σ shaded band** across session history
- Zone distribution: proportional color bar (Gold / Red / Blue / Black / White / Miss)
- Running end summary: total score + avg per arrow during live session

### Post-session deep-dive

- gz trace with shot-window overlays and near-miss markers
- Hold time vs. score scatter
- HR time series with shot events overlaid
- Walking interval detection (step count derivative)
- Detection calibration UI: adjust thresholds and see which past shots are gained/lost

---

## Architecture

```
┌─────────────────────────────┐     Wear Data Layer (BLE)    ┌───────────────────┐
│         Wear OS             │ ──────────────────────────── │    Phone App      │
│                             │                              │                   │
│  Gyro/HR sensors            │  scored arrows (real-time)   │  LiveSession      │
│  Shot detector              │  sensor CSV (post-session)   │  Repository       │
│  ScoreScreen UI             │  HR time series              │                   │
│                             │                              │  AnalyticsParser  │
│  ~50 Hz gz stream           │                              │  SessionAnalytics │
│  Hold window detection      │                              │  AnalyticsScreen  │
└─────────────────────────────┘                              └───────────────────┘
```

- **Shared module** — data models, Wear Data Layer path constants, `ScoreZone` enum
- **Wear module** — sensor collection, detection, real-time scoring UI, data transmission
- **Phone module** — data layer receiver, Room persistence, analytics parsing + visualization

---

## Screenshots

<!-- Add screenshots here -->

---

## Tech

- Kotlin, Jetpack Compose, Wear Compose
- Wear Data Layer (Google Play Services Wearable API)
- Room (SQLite), Kotlin Coroutines + StateFlow
- Custom Canvas charts (line plots, gz traces, zone bars)
- Android Sensor API (gyroscope, gravity, step counter, heart rate)
