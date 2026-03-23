"""
CSV parser and shot detection for ArrowWatch session files.
Ported from Kotlin AnalyticsParser.kt + SessionParser.kt.
"""

import json
import os
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd


# ═══════════════ DATA MODELS ═══════════════

@dataclass
class DetectedShot:
    time: float           # midpoint of hold window
    start_sec: float
    end_sec: float
    hold_sec: float       # end - start
    n_samples: int
    gz_mean: float
    gz_stdev: float = 0.0
    hr_at_shot: float | None = None


@dataclass
class RoundAnalytics:
    round: int
    start_sec: float
    end_sec: float
    score: float
    detected_shots: list[DetectedShot] = field(default_factory=list)
    walking_intervals: list[tuple[float, float]] = field(default_factory=list)
    avg_hr: float = 0.0
    orig_csv_round: int = 0


@dataclass
class ArrowScore:
    shot_number: int
    zone: str
    score: float
    is_final: bool = False


@dataclass
class RoundSummary:
    number: int
    arrows: list[ArrowScore] = field(default_factory=list)
    detected_score: float = 0.0
    confirmed_score: float | None = None
    avg_heart_rate: float | None = None
    avg_hold_ms: int | None = None

    @property
    def display_score(self) -> float:
        if self.confirmed_score is not None:
            return self.confirmed_score
        if self.arrows:
            return sum(a.score for a in self.arrows if a.zone != "DNS")
        return self.detected_score


@dataclass
class SessionAnalytics:
    duration_sec: float
    sensor: pd.DataFrame                     # time, gz, yaw, pitch, roll, steps, gx, gy
    hr_samples: list[tuple[float, float]]    # (time, bpm)
    walking_intervals: list[tuple[float, float]]
    round_analytics: list[RoundAnalytics] = field(default_factory=list)
    all_shots: list[DetectedShot] = field(default_factory=list)
    arrows_per_round: int = 3
    rounds: list[RoundSummary] = field(default_factory=list)
    # Intermediate signal arrays for visualization
    gz_detrended: np.ndarray | None = None
    roll_detrended: np.ndarray | None = None
    pitch_detrended: np.ndarray | None = None
    yaw_detrended: np.ndarray | None = None
    gz_stdev_arr: np.ndarray | None = None


# ═══════════════ DEFAULT DETECTION PARAMS ═══════════════

DEFAULT_PARAMS = {
    "detrend_win_sec": 60.0,
    "gz_min_detrended": 2.0,
    "gz_stdev_max": 1.0,
    "roll_max_detrended": -0.5,
    "stdev_win_sec": 2.0,
    "hold_min_sec": 3.0,
    "hold_max_sec": 14.0,
    "merge_gap_sec": 1.0,
    "cooldown_sec": 2.0,
}

SPLIT_GZ_LOW = 5.0


# ═══════════════ CSV PARSING ═══════════════

def parse_csv(filepath: str) -> SessionAnalytics | None:
    """Parse a session CSV into sensor data, HR, walking, rounds, and scores."""
    path = Path(filepath)
    if not path.exists():
        return None

    sensor_rows = []
    hr_samples = []
    walk_starts = []
    walk_stops = []
    round_score_times: dict[int, float] = {}
    round_scores: dict[int, float] = {}
    deleted_rounds: set[int] = set()
    arrow_counts: dict[int, int] = {}
    duration_sec = 0.0

    # For session summary parsing
    round_arrows: dict[int, dict[int, ArrowScore]] = {}  # round -> {shot# -> ArrowScore}
    round_hr_values: dict[int, list[float]] = {}
    round_hold_values: dict[int, list[int]] = {}

    with open(path, "r") as f:
        header = f.readline()
        for line in f:
            cols = line.strip().split(",")
            if len(cols) < 2:
                continue
            try:
                elapsed = float(cols[0])
            except ValueError:
                continue
            event = cols[1].strip()

            if event == "sensor":
                gz = _float(cols, 5)
                if gz is None:
                    continue
                sensor_rows.append({
                    "time": elapsed,
                    "gz": gz,
                    "yaw": _float(cols, 10, 0.0),
                    "pitch": _float(cols, 11, 0.0),
                    "roll": _float(cols, 12, 0.0),
                    "steps": _int(cols, 13, 0),
                    "gx": _float(cols, 14, 0.0),
                    "gy": _float(cols, 15, 0.0),
                })

            elif event == "heart_rate":
                bpm = _float(cols, 9, 0.0)
                if bpm > 0:
                    hr_samples.append((elapsed, bpm))
                    # Assign HR to highest round seen
                    if round_score_times:
                        max_round = max(round_score_times.keys())
                        round_hr_values.setdefault(max_round, []).append(bpm)

            elif event == "walking_start":
                walk_starts.append(elapsed)
            elif event == "walking_stop":
                walk_stops.append(elapsed)

            elif event == "actual_score":
                rnd = _int(cols, 2)
                if rnd is None:
                    continue
                score = _float(cols, 8, 0.0)
                round_scores[rnd] = score
                round_score_times[rnd] = elapsed

            elif event == "approx_score":
                rnd = _int(cols, 2)
                if rnd is None:
                    continue
                score = _float(cols, 8, 0.0)
                round_scores.setdefault(rnd, score)
                round_score_times.setdefault(rnd, elapsed)

            elif event in ("final_score", "quick_score"):
                rnd = _int(cols, 2)
                if rnd is None:
                    continue
                round_score_times[rnd] = elapsed
                arrow_counts[rnd] = arrow_counts.get(rnd, 0) + 1
                shot_num = _int(cols, 3)
                zone = (cols[7].strip() if len(cols) > 7 else "") or "MISS"
                score = _float(cols, 8, 0.0)
                if shot_num is not None:
                    arrows = round_arrows.setdefault(rnd, {})
                    is_final = event == "final_score"
                    if shot_num not in arrows or is_final:
                        arrows[shot_num] = ArrowScore(shot_num, zone, score, is_final)

            elif event in ("scoring", "manual_shot", "auto_shot"):
                rnd = _int(cols, 2)
                hold_ms = _int(cols, 4)
                hr = _float(cols, 9)
                if rnd is not None:
                    if hold_ms is not None and hold_ms > 0:
                        round_hold_values.setdefault(rnd, []).append(hold_ms)
                    if hr is not None and hr > 0:
                        round_hr_values.setdefault(rnd, []).append(hr)

            elif event == "edit_score":
                rnd = _int(cols, 2)
                shot_num = _int(cols, 3)
                zone = (cols[7].strip() if len(cols) > 7 else "") or "MISS"
                score = _float(cols, 8, 0.0)
                if rnd is not None and shot_num is not None:
                    round_arrows.setdefault(rnd, {})[shot_num] = ArrowScore(shot_num, zone, score, True)

            elif event == "edit_round_total":
                rnd = _int(cols, 2)
                if rnd is None:
                    continue
                score = _float(cols, 8, 0.0)
                round_scores[rnd] = score

            elif event == "delete_round":
                rnd = _int(cols, 2)
                if rnd is not None:
                    deleted_rounds.add(rnd)

            elif event == "session_end":
                duration_sec = elapsed

    if not sensor_rows:
        return None

    sensor = pd.DataFrame(sensor_rows)

    if duration_sec == 0:
        duration_sec = max(
            sensor["time"].iloc[-1] if len(sensor) > 0 else 0,
            hr_samples[-1][0] if hr_samples else 0,
        )

    # Arrows per round (mode)
    arrows_per_round = 3
    if arrow_counts:
        from collections import Counter
        counts = Counter(arrow_counts.values())
        arrows_per_round = counts.most_common(1)[0][0]

    # Walking intervals
    has_steps = (sensor["steps"] > 0).any()
    if has_steps:
        walking = _derive_walking_from_steps(sensor)
    else:
        walking = _clean_walking_intervals(walk_starts, walk_stops, duration_sec)

    # Build round summaries
    sorted_rounds = sorted(r for r in round_score_times if r not in deleted_rounds)
    rounds = []
    for rnd in sorted_rounds:
        arrows = sorted(round_arrows.get(rnd, {}).values(), key=lambda a: a.shot_number)
        avg_hr = None
        hr_vals = round_hr_values.get(rnd, [])
        if hr_vals:
            avg_hr = sum(hr_vals) / len(hr_vals)
        avg_hold = None
        hold_vals = round_hold_values.get(rnd, [])
        if hold_vals:
            avg_hold = int(sum(hold_vals) / len(hold_vals))
        rounds.append(RoundSummary(
            number=rnd,
            arrows=arrows,
            detected_score=round_scores.get(rnd, 0.0) if rnd not in round_scores or round_scores[rnd] == round_scores.get(rnd) else 0.0,
            confirmed_score=round_scores.get(rnd),
            avg_heart_rate=avg_hr,
            avg_hold_ms=avg_hold,
        ))

    return SessionAnalytics(
        duration_sec=duration_sec,
        sensor=sensor,
        hr_samples=hr_samples,
        walking_intervals=walking,
        arrows_per_round=arrows_per_round,
        rounds=rounds,
    )


# ═══════════════ SHOT DETECTION ═══════════════

def detect_shots(session: SessionAnalytics, params: dict | None = None) -> SessionAnalytics:
    """
    Run shot detection on parsed session data. Mutates session in place
    (sets all_shots, round_analytics, and intermediate signal arrays).
    Uses vectorized pandas rolling operations for speed.
    """
    p = {**DEFAULT_PARAMS, **(params or {})}
    sensor = session.sensor

    if len(sensor) < 3:
        return session

    n = len(sensor)
    times = sensor["time"].values.astype(np.float64)
    gz_raw = sensor["gz"].values.astype(np.float64)
    roll_raw = sensor["roll"].values.astype(np.float64)

    # Estimate sample rate for converting time-based windows to sample counts
    duration = max(times[-1] - times[0], 1.0)
    samples_per_sec = n / duration
    detrend_win = max(int(p["detrend_win_sec"] * samples_per_sec), 3) | 1  # odd
    stdev_win = max(int(p["stdev_win_sec"] * samples_per_sec), 3) | 1      # odd

    pitch_raw = sensor["pitch"].values.astype(np.float64)
    yaw_raw = sensor["yaw"].values.astype(np.float64)

    # ── 1. Detrend all rotation signals via vectorized rolling median ──
    def _rolling_detrend(raw, win):
        med = pd.Series(raw).rolling(win, center=True, min_periods=1).median().values
        return raw - med

    gz_d = _rolling_detrend(gz_raw, detrend_win)
    roll_d = _rolling_detrend(roll_raw, detrend_win)
    pitch_d = _rolling_detrend(pitch_raw, detrend_win)
    yaw_d = _rolling_detrend(yaw_raw, detrend_win)

    # ── 2. Rolling stdev of detrended gz (vectorized) ──
    gz_stdev_arr = pd.Series(gz_d).rolling(stdev_win, center=True, min_periods=2).std(ddof=0).fillna(0).values

    # ── 3. Boolean mask ──
    mask = (
        (gz_d >= p["gz_min_detrended"]) &
        (gz_stdev_arr <= p["gz_stdev_max"]) &
        (roll_d <= p["roll_max_detrended"])
    )

    # ── 4. Find contiguous True segments ──
    segments = []
    seg_start = None
    for i in range(n):
        if mask[i] and seg_start is None:
            seg_start = i
        elif not mask[i] and seg_start is not None:
            segments.append((seg_start, i - 1))
            seg_start = None
    if seg_start is not None:
        segments.append((seg_start, n - 1))

    if not segments:
        session.gz_detrended = gz_d
        session.roll_detrended = roll_d
        session.pitch_detrended = pitch_d
        session.yaw_detrended = yaw_d
        session.gz_stdev_arr = gz_stdev_arr
        session.all_shots = []
        session.round_analytics = _build_round_analytics(session, [], p)
        return session

    # ── 5. Merge segments within merge_gap_sec ──
    merged = [list(segments[0])]
    for s, e in segments[1:]:
        if times[s] - times[merged[-1][1]] <= p["merge_gap_sec"]:
            merged[-1][1] = e
        else:
            merged.append([s, e])

    # ── 6. Filter by duration + cooldown, build DetectedShot list ──
    shots = []
    last_exit = -999.0
    for s, e in merged:
        hold = times[e] - times[s]
        if hold < p["hold_min_sec"] or hold > p["hold_max_sec"]:
            continue
        if times[s] - last_exit < p["cooldown_sec"]:
            continue

        gz_window = gz_raw[s:e + 1]
        gz_mean = float(np.mean(gz_window))
        gz_stdev = float(np.std(gz_window)) if len(gz_window) > 1 else 0.0

        mid_time = times[s] + hold / 2.0
        hr = _interpolate_hr(session.hr_samples, mid_time)

        shots.append(DetectedShot(
            time=float(mid_time),
            start_sec=float(times[s]),
            end_sec=float(times[e]),
            hold_sec=float(hold),
            n_samples=e - s + 1,
            gz_mean=gz_mean,
            gz_stdev=gz_stdev,
            hr_at_shot=hr,
        ))
        last_exit = float(times[e])

    session.gz_detrended = gz_d
    session.roll_detrended = roll_d
    session.pitch_detrended = pitch_d
    session.yaw_detrended = yaw_d
    session.gz_stdev_arr = gz_stdev_arr
    session.all_shots = shots
    session.round_analytics = _build_round_analytics(session, shots, p)
    return session


# ═══════════════ ROUND WINDOWS ═══════════════

def _build_round_analytics(
    session: SessionAnalytics,
    shots: list[DetectedShot],
    params: dict,
) -> list[RoundAnalytics]:
    """Assign shots and sensor data to round time windows."""
    sensor = session.sensor
    times = sensor["time"].values
    duration = session.duration_sec

    # Build round windows from score times
    round_score_times: dict[int, float] = {}
    round_scores: dict[int, float] = {}
    for rs in session.rounds:
        # Use the last sensor time before round as proxy
        round_scores[rs.number] = rs.display_score

    # If we have rounds from CSV, use them
    if session.rounds:
        sorted_rounds = sorted(session.rounds, key=lambda r: r.number)
        windows = []
        for i, rs in enumerate(sorted_rounds):
            # Estimate round boundaries: evenly divide session by round count
            # Better: use the original CSV round score times
            start = (i / len(sorted_rounds)) * duration
            end = ((i + 1) / len(sorted_rounds)) * duration
            windows.append((rs.number, start, end, rs.display_score, rs.number))
    else:
        # Derive from walking intervals
        round_pairs = _derive_rounds_from_walking(session.walking_intervals, duration)
        windows = [
            (i + 1, s, e, 0.0, i + 1) for i, (s, e) in enumerate(round_pairs)
        ]

    # Try to split long rounds
    windows = _split_long_rounds(windows, sensor)

    result = []
    for rnd_num, start, end, score, orig_rnd in windows:
        round_walking = [
            (max(ws, start), min(we, end))
            for ws, we in session.walking_intervals
            if we >= start and ws <= end
        ]
        round_shots = [s for s in shots if start <= s.time <= end]

        # HR: prefer at-shot times
        avg_hr = 0.0
        shot_hrs = [s.hr_at_shot for s in round_shots if s.hr_at_shot is not None]
        if shot_hrs:
            avg_hr = sum(shot_hrs) / len(shot_hrs)

        result.append(RoundAnalytics(
            round=rnd_num,
            start_sec=start,
            end_sec=end,
            score=score,
            detected_shots=round_shots,
            walking_intervals=round_walking,
            avg_hr=avg_hr,
            orig_csv_round=orig_rnd,
        ))

    return result


def _split_long_rounds(windows, sensor):
    """Split rounds whose duration exceeds 1.5x median."""
    if len(windows) < 3:
        return windows
    durations = [e - s for _, s, e, _, _ in windows]
    median_dur = float(np.median(durations))
    threshold = median_dur * 1.5

    result = []
    for rnd_num, start, end, score, orig_rnd in windows:
        dur = end - start
        if dur <= threshold:
            result.append((0, start, end, score, orig_rnd))
            continue
        split_pt = _find_shooting_gap(sensor, start, end)
        if split_pt is not None:
            result.append((0, start, split_pt, 0.0, orig_rnd))
            result.append((0, split_pt, end, score, orig_rnd))
        else:
            result.append((0, start, end, score, orig_rnd))

    # Renumber
    return [(i + 1, s, e, sc, o) for i, (_, s, e, sc, o) in enumerate(result)]


def _find_shooting_gap(sensor, start, end):
    """Find a 30s+ gap in gz < SPLIT_GZ_LOW for splitting a long round."""
    mask = (sensor["time"] >= start) & (sensor["time"] <= end)
    rs = sensor[mask]
    if len(rs) < 10:
        return None

    times_arr = rs["time"].values
    gz_arr = rs["gz"].values

    gap_start = None
    gaps = []
    for i in range(len(rs)):
        if gz_arr[i] < SPLIT_GZ_LOW:
            if gap_start is None:
                gap_start = times_arr[i]
        else:
            if gap_start is not None:
                if times_arr[i] - gap_start >= 30.0:
                    gaps.append((gap_start, times_arr[i]))
                gap_start = None
    if gap_start is not None and times_arr[-1] - gap_start >= 30.0:
        gaps.append((gap_start, times_arr[-1]))

    if not gaps:
        return None

    valid = [(s, e) for s, e in gaps
             if (s + e) / 2 > start + 60 and (s + e) / 2 < end - 60]
    if not valid:
        return None
    best = max(valid, key=lambda g: g[1] - g[0])
    return (best[0] + best[1]) / 2


# ═══════════════ HELPERS ═══════════════

def _float(cols, idx, default=None):
    if idx >= len(cols):
        return default
    v = cols[idx].strip()
    if not v:
        return default
    try:
        return float(v)
    except ValueError:
        return default


def _int(cols, idx, default=None):
    if idx >= len(cols):
        return default
    v = cols[idx].strip()
    if not v:
        return default
    try:
        return int(v)
    except ValueError:
        return default


def _interpolate_hr(hr_samples, t):
    if not hr_samples:
        return None
    before = [(time, bpm) for time, bpm in hr_samples if time <= t]
    after = [(time, bpm) for time, bpm in hr_samples if time > t]
    b = before[-1] if before else None
    a = after[0] if after else None
    if b and a:
        frac = (t - b[0]) / (a[0] - b[0]) if a[0] != b[0] else 0
        return b[1] + frac * (a[1] - b[1])
    if b:
        return b[1]
    if a:
        return a[1]
    return None


def _clean_walking_intervals(starts, stops, duration):
    result = []
    remaining = sorted(stops)
    for start in sorted(starts):
        matching = [s for s in remaining if s > start]
        if matching:
            stop = min(matching)
            remaining.remove(stop)
            result.append((start, min(stop, start + 90)))
        else:
            result.append((start, min(start + 30, duration)))
    return result


def _derive_walking_from_steps(sensor):
    if len(sensor) < 2:
        return []
    times_arr = sensor["time"].values
    steps_arr = sensor["steps"].values
    intervals = []
    walk_start = None

    for i in range(len(sensor)):
        t = times_arr[i]
        # 5-second lookback
        lookback_mask = (times_arr >= t - 5) & (times_arr <= t)
        lb_steps = steps_arr[lookback_mask]
        steps_in_window = lb_steps[-1] - lb_steps[0] if len(lb_steps) >= 2 else 0
        is_walking = steps_in_window > 0

        if is_walking and walk_start is None:
            walk_start = t
        elif not is_walking and walk_start is not None:
            intervals.append((walk_start, min(t, walk_start + 90)))
            walk_start = None

    if walk_start is not None:
        intervals.append((walk_start, min(times_arr[-1], walk_start + 90)))
    return intervals


def _derive_rounds_from_walking(walking, duration, min_shooting_sec=30.0):
    """Derive round boundaries from gaps between walking intervals."""
    if not walking:
        return [(0.0, duration)]

    # Merge close walking intervals
    merged = _merge_walking_transitions(walking)
    rounds = []

    first_start = merged[0][0]
    if first_start > min_shooting_sec:
        rounds.append((0.0, first_start))

    for i in range(len(merged) - 1):
        gap_start = merged[i][1]
        gap_end = merged[i + 1][0]
        if gap_end - gap_start >= min_shooting_sec:
            rounds.append((gap_start, gap_end))

    last_end = merged[-1][1]
    if duration - last_end >= min_shooting_sec:
        rounds.append((last_end, duration))

    return rounds


def _merge_walking_transitions(walking, gap_threshold=60.0):
    if not walking:
        return []
    sorted_w = sorted(walking, key=lambda w: w[0])
    merged = [list(sorted_w[0])]
    for ws, we in sorted_w[1:]:
        if ws - merged[-1][1] <= gap_threshold:
            merged[-1][1] = max(merged[-1][1], we)
        else:
            merged.append([ws, we])
    return [tuple(m) for m in merged]


# ═══════════════ PROFILE SAVE/LOAD ═══════════════

PROFILES_DIR = Path(__file__).parent / "profiles"


def save_profile(name: str, params: dict):
    PROFILES_DIR.mkdir(exist_ok=True)
    path = PROFILES_DIR / f"{name}.json"
    with open(path, "w") as f:
        json.dump(params, f, indent=2)


def load_profile(name: str) -> dict:
    path = PROFILES_DIR / f"{name}.json"
    if path.exists():
        with open(path) as f:
            return json.load(f)
    return dict(DEFAULT_PARAMS)


def list_profiles() -> list[str]:
    if not PROFILES_DIR.exists():
        return []
    return [p.stem for p in PROFILES_DIR.glob("*.json")]


# ═══════════════ CALIBRATION ═══════════════

@dataclass
class CalibrationResult:
    """Stats computed from user-marked shot locations."""
    shots: list[dict]           # per-shot stats
    suggested_params: dict      # threshold values that would capture all marked shots


def calibrate_from_clicks(session: SessionAnalytics, click_times: list[float],
                          search_radius: float = 7.0) -> CalibrationResult:
    """
    Given a list of times the user clicked (approximate shot centers),
    find the actual hold boundaries and compute stats for each.
    Returns suggested detection parameters.
    """
    if session.gz_detrended is None:
        return CalibrationResult(shots=[], suggested_params=dict(DEFAULT_PARAMS))

    times = session.sensor["time"].values
    gz_raw = session.sensor["gz"].values
    gz_d = session.gz_detrended
    roll_d = session.roll_detrended
    pitch_d = session.pitch_detrended if session.pitch_detrended is not None else np.zeros(len(times))
    yaw_d = session.yaw_detrended if session.yaw_detrended is not None else np.zeros(len(times))
    gz_stdev = session.gz_stdev_arr

    shot_stats = []

    for click_t in click_times:
        # Find the nearest sample index
        center_idx = int(np.argmin(np.abs(times - click_t)))
        center_time = times[center_idx]

        # Search window: ±search_radius seconds
        radius_samples = int(search_radius * len(times) / max(times[-1] - times[0], 1))
        lo = max(0, center_idx - radius_samples)
        hi = min(len(times) - 1, center_idx + radius_samples)

        # Find the peak gz_detrended in the window (the hold region)
        window_gz_d = gz_d[lo:hi + 1]
        peak_idx = lo + int(np.argmax(window_gz_d))

        # Expand outward from peak while gz_d stays elevated (> 50% of peak)
        peak_val = gz_d[peak_idx]
        threshold = max(peak_val * 0.3, 0.5)  # at least 0.5

        # Expand left
        start_idx = peak_idx
        while start_idx > 0 and gz_d[start_idx - 1] >= threshold:
            start_idx -= 1

        # Expand right
        end_idx = peak_idx
        while end_idx < len(times) - 1 and gz_d[end_idx + 1] >= threshold:
            end_idx += 1

        start_sec = float(times[start_idx])
        end_sec = float(times[end_idx])
        hold_sec = end_sec - start_sec

        # Compute stats for this hold window
        win = slice(start_idx, end_idx + 1)
        gz_d_mean = float(np.mean(gz_d[win]))
        gz_d_min = float(np.min(gz_d[win]))
        gz_raw_mean = float(np.mean(gz_raw[win]))
        gz_stdev_mean = float(np.mean(gz_stdev[win]))
        gz_stdev_max = float(np.max(gz_stdev[win]))
        roll_d_mean = float(np.mean(roll_d[win]))
        roll_d_max = float(np.max(roll_d[win]))
        pitch_d_mean = float(np.mean(pitch_d[win]))
        yaw_d_mean = float(np.mean(yaw_d[win]))

        mid_time = start_sec + hold_sec / 2
        hr = _interpolate_hr(session.hr_samples, mid_time)

        shot_stats.append({
            "click_time": float(click_t),
            "start_sec": start_sec,
            "end_sec": end_sec,
            "hold_sec": hold_sec,
            "gz_d_mean": gz_d_mean,
            "gz_d_min": gz_d_min,
            "gz_raw_mean": gz_raw_mean,
            "gz_stdev_mean": gz_stdev_mean,
            "gz_stdev_max": gz_stdev_max,
            "roll_d_mean": roll_d_mean,
            "roll_d_max": roll_d_max,
            "pitch_d_mean": pitch_d_mean,
            "yaw_d_mean": yaw_d_mean,
            "hr_at_shot": hr,
        })

    # Compute suggested parameters from the marked shots
    if not shot_stats:
        return CalibrationResult(shots=[], suggested_params=dict(DEFAULT_PARAMS))

    margin = 0.3  # buffer factor

    gz_d_mins = [s["gz_d_min"] for s in shot_stats]
    gz_stdev_maxes = [s["gz_stdev_max"] for s in shot_stats]
    roll_d_maxes = [s["roll_d_max"] for s in shot_stats]
    hold_secs = [s["hold_sec"] for s in shot_stats]

    suggested = dict(DEFAULT_PARAMS)
    suggested["gz_min_detrended"] = round(min(gz_d_mins) - margin, 2)
    suggested["gz_stdev_max"] = round(max(gz_stdev_maxes) + margin, 2)
    suggested["roll_max_detrended"] = round(max(roll_d_maxes) + margin, 2)
    suggested["hold_min_sec"] = round(max(min(hold_secs) - 1.0, 1.0), 1)
    suggested["hold_max_sec"] = round(max(hold_secs) + 2.0, 1)

    return CalibrationResult(shots=shot_stats, suggested_params=suggested)
