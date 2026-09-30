"""
ArrowWatch — Interactive session analytics web app.
Run: python app.py
Open: http://localhost:8050
"""

import os
from pathlib import Path

import dash
from dash import dcc, html, Input, Output, State, callback, no_update, ctx
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import numpy as np

from parser import (
    parse_csv, detect_shots, DEFAULT_PARAMS,
    save_profile, load_profile, list_profiles,
    calibrate_from_clicks,
)

# ═══════════════ APP SETUP ═══════════════

app = dash.Dash(
    __name__,
    title="ArrowWatch",
    suppress_callback_exceptions=True,
)

# Color palette (matching phone app)
COLORS = {
    "bg": "#0F172A",
    "sidebar": "#1E293B",
    "card": "#1E293B",
    "card_border": "#334155",
    "text": "#F8FAFC",
    "text_secondary": "#94A3B8",
    "text_muted": "#64748B",
    "cyan": "#22D3EE",
    "cyan_dark": "#0891B2",
    "amber": "#F59E0B",
    "amber_dark": "#D97706",
    "red": "#EF4444",
    "pink": "#EC4899",
    "green": "#22C55E",
    "blue": "#3B82F6",
    "gold": "#F59E0B",
}

ZONE_COLORS = {
    "GOLD": "#F59E0B", "YELLOW": "#F59E0B",
    "RED": "#EF4444",
    "BLUE": "#0EA5E9",
    "BLACK": "#64748B",
    "WHITE": "#CBD5E1",
    "MISS": "#94A3B8",
    "DNS": "#475569",
}

SHOT_COLORS = [
    "#22D3EE", "#F59E0B", "#EC4899", "#22C55E",
    "#A78BFA", "#FB923C", "#2DD4BF", "#F472B6",
]

# Default CSV directory — detect WSL vs Windows
_project_root = Path(__file__).parent.parent
_wsl_path = Path("/mnt/c/Users/maris/AndroidStudioProjects/ArcheryWatchAndPhoneApp/pulled_csvs")
DEFAULT_CSV_DIR = str(_wsl_path if _wsl_path.exists() else _project_root / "pulled_csvs")

# ═══════════════ SERVER-SIDE SESSION CACHE ═══════════════
# Keeps parsed + detected session data in Python memory.
# The browser only holds a tiny key string via dcc.Store.
_session_cache: dict[str, dict] = {}  # key -> serialized session dict


def _cache_put(key: str, data: dict):
    _session_cache.clear()       # single-session app — free old memory
    _session_cache[key] = data


def _cache_get(key: str) -> dict | None:
    return _session_cache.get(key)


# ═══════════════ LAYOUT ═══════════════

def make_param_input(param_id, label, default_val, step=0.1, min_val=0):
    return html.Div([
        html.Label(label, style={"fontSize": "12px", "color": COLORS["text_secondary"]}),
        dcc.Input(
            id=param_id, type="number", value=default_val,
            step=step, min=min_val,
            style={
                "width": "100%", "background": COLORS["bg"],
                "color": COLORS["text"], "border": f"1px solid {COLORS['card_border']}",
                "borderRadius": "4px", "padding": "6px 8px", "fontSize": "13px",
            },
        ),
    ], style={"marginBottom": "8px"})


app.layout = html.Div([
    # Stores
    dcc.Store(id="session-store"),
    dcc.Store(id="csv-dir-store", data=DEFAULT_CSV_DIR),
    dcc.Store(id="calibration-clicks", data=[]),      # list of clicked times
    dcc.Store(id="calibration-results", data=None),   # computed stats from clicks

    html.Div([
        # ── SIDEBAR ──
        html.Div([
            html.H2("ArrowWatch", style={
                "color": COLORS["cyan"], "margin": "0 0 4px 0", "fontSize": "20px",
            }),
            html.P("Session Analytics", style={
                "color": COLORS["text_muted"], "margin": "0 0 20px 0", "fontSize": "12px",
            }),

            # CSV folder
            html.Label("CSV Folder", style={"fontSize": "12px", "color": COLORS["text_secondary"]}),
            dcc.Input(
                id="csv-dir-input", type="text", value=DEFAULT_CSV_DIR,
                style={
                    "width": "100%", "background": COLORS["bg"],
                    "color": COLORS["text"], "border": f"1px solid {COLORS['card_border']}",
                    "borderRadius": "4px", "padding": "6px 8px", "fontSize": "12px",
                    "marginBottom": "8px",
                },
            ),
            html.Button("Scan", id="scan-btn", n_clicks=0, style={
                "width": "100%", "background": COLORS["cyan_dark"], "color": "white",
                "border": "none", "borderRadius": "4px", "padding": "8px",
                "cursor": "pointer", "marginBottom": "12px", "fontSize": "13px",
            }),

            # Session selector
            html.Label("Session", style={"fontSize": "12px", "color": COLORS["text_secondary"]}),
            dcc.Dropdown(
                id="session-dropdown", options=[], placeholder="Select a session...",
                style={"marginBottom": "16px", "fontSize": "13px"},
            ),

            html.Hr(style={"borderColor": COLORS["card_border"], "margin": "12px 0"}),

            # Profile
            html.Label("Profile", style={"fontSize": "12px", "color": COLORS["text_secondary"]}),
            html.Div([
                dcc.Dropdown(
                    id="profile-dropdown", options=[], placeholder="Default",
                    style={"flex": "1", "fontSize": "13px"},
                ),
            ], style={"marginBottom": "8px"}),
            html.Div([
                dcc.Input(
                    id="profile-name-input", type="text", placeholder="Profile name",
                    style={
                        "flex": "1", "background": COLORS["bg"],
                        "color": COLORS["text"], "border": f"1px solid {COLORS['card_border']}",
                        "borderRadius": "4px", "padding": "6px 8px", "fontSize": "12px",
                    },
                ),
                html.Button("Save", id="save-profile-btn", n_clicks=0, style={
                    "background": COLORS["amber_dark"], "color": "white",
                    "border": "none", "borderRadius": "4px", "padding": "6px 10px",
                    "cursor": "pointer", "marginLeft": "4px", "fontSize": "12px",
                }),
            ], style={"display": "flex", "marginBottom": "16px"}),

            html.Hr(style={"borderColor": COLORS["card_border"], "margin": "12px 0"}),

            # Tuning parameters
            html.Div([
                html.Span("Detection Parameters", style={
                    "fontSize": "13px", "fontWeight": "600", "color": COLORS["text"],
                }),
            ], style={"marginBottom": "10px"}),

            make_param_input("param-gz-min", "gz_min_detrended", DEFAULT_PARAMS["gz_min_detrended"]),
            make_param_input("param-gz-stdev", "gz_stdev_max", DEFAULT_PARAMS["gz_stdev_max"]),
            make_param_input("param-roll-max", "roll_max_detrended", DEFAULT_PARAMS["roll_max_detrended"], step=0.1, min_val=-10),
            make_param_input("param-hold-min", "hold_min_sec", DEFAULT_PARAMS["hold_min_sec"], step=0.5),
            make_param_input("param-hold-max", "hold_max_sec", DEFAULT_PARAMS["hold_max_sec"], step=0.5),
            make_param_input("param-merge-gap", "merge_gap_sec", DEFAULT_PARAMS["merge_gap_sec"]),
            make_param_input("param-cooldown", "cooldown_sec", DEFAULT_PARAMS["cooldown_sec"]),
            make_param_input("param-detrend-win", "detrend_win_sec", DEFAULT_PARAMS["detrend_win_sec"], step=5),
            make_param_input("param-stdev-win", "stdev_win_sec", DEFAULT_PARAMS["stdev_win_sec"]),

            html.Button("Re-detect Shots", id="redetect-btn", n_clicks=0, style={
                "width": "100%", "background": COLORS["cyan_dark"], "color": "white",
                "border": "none", "borderRadius": "6px", "padding": "10px",
                "cursor": "pointer", "marginTop": "12px", "fontWeight": "600",
                "fontSize": "14px",
            }),

            html.Hr(style={"borderColor": COLORS["card_border"], "margin": "16px 0"}),

            # Calibration mode
            html.Div([
                html.Span("Calibration", style={
                    "fontSize": "13px", "fontWeight": "600", "color": COLORS["text"],
                }),
            ], style={"marginBottom": "8px"}),
            html.P("Click on shot centers in the chart to mark them. Stats are computed "
                   "from your selections to suggest thresholds.",
                   style={"fontSize": "11px", "color": COLORS["text_muted"], "margin": "0 0 8px 0"}),
            html.Button("Enable Calibration", id="calibrate-toggle", n_clicks=0, style={
                "width": "100%", "background": COLORS["card_border"], "color": COLORS["text"],
                "border": "none", "borderRadius": "4px", "padding": "8px",
                "cursor": "pointer", "fontSize": "12px", "marginBottom": "6px",
            }),
            html.Button("Clear Marks", id="clear-marks-btn", n_clicks=0, style={
                "width": "100%", "background": "transparent",
                "color": COLORS["text_muted"],
                "border": f"1px solid {COLORS['card_border']}", "borderRadius": "4px",
                "padding": "6px", "cursor": "pointer", "fontSize": "11px",
                "marginBottom": "6px",
            }),
            html.Button("Apply Suggested Params", id="apply-calibration-btn", n_clicks=0, style={
                "width": "100%", "background": COLORS["amber_dark"], "color": "white",
                "border": "none", "borderRadius": "4px", "padding": "8px",
                "cursor": "pointer", "fontSize": "12px", "marginBottom": "8px",
            }),
            html.Div(id="calibration-stats", style={"fontSize": "11px"}),
        ], style={
            "width": "260px", "minWidth": "260px",
            "background": COLORS["sidebar"], "padding": "16px",
            "overflowY": "auto", "height": "100vh",
            "borderRight": f"1px solid {COLORS['card_border']}",
        }),

        # ── MAIN PANEL ──
        html.Div([
            # Summary cards
            html.Div(id="summary-cards", style={"marginBottom": "16px"}),

            # Round filter
            html.Div([
                html.Label("Round:", style={
                    "fontSize": "13px", "color": COLORS["text_secondary"], "marginRight": "8px",
                }),
                dcc.Dropdown(
                    id="round-dropdown",
                    options=[{"label": "All Rounds", "value": "all"}],
                    value="all",
                    style={"width": "180px", "fontSize": "13px"},
                    clearable=False,
                ),
                html.Div([
                    dcc.Checklist(
                        id="show-detrended",
                        options=[{"label": " Show detrended signals", "value": "detrended"}],
                        value=[],
                        style={"fontSize": "13px", "color": COLORS["text_secondary"]},
                    ),
                ], style={"marginLeft": "24px"}),
            ], style={
                "display": "flex", "alignItems": "center", "marginBottom": "12px",
            }),

            # Sensor charts
            dcc.Loading(
                dcc.Graph(id="sensor-chart", style={"height": "700px"}),
                type="circle", color=COLORS["cyan"],
            ),

            # Round details table + zone distribution
            html.Div([
                html.Div(id="round-table", style={"flex": "1", "marginRight": "12px"}),
                html.Div(id="zone-chart-container", style={"width": "340px"}),
            ], style={"display": "flex", "marginTop": "16px"}),

        ], style={
            "flex": "1", "padding": "16px", "overflowY": "auto", "height": "100vh",
            "background": COLORS["bg"],
        }),

    ], style={"display": "flex", "height": "100vh"}),

], style={
    "fontFamily": "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
    "background": COLORS["bg"], "color": COLORS["text"],
})


# ═══════════════ CALLBACKS ═══════════════

# ── Scan CSV directory ──
@callback(
    Output("session-dropdown", "options"),
    Input("scan-btn", "n_clicks"),
    State("csv-dir-input", "value"),
    prevent_initial_call=False,
)
def scan_csvs(n_clicks, csv_dir):
    if not csv_dir or not os.path.isdir(csv_dir):
        return []
    csvs = []
    for root, dirs, files in os.walk(csv_dir):
        for f in sorted(files):
            if f.endswith(".csv") and f.startswith("session_"):
                full = os.path.join(root, f)
                rel = os.path.relpath(full, csv_dir)
                # Extract date from filename
                label = f.replace("session_", "").replace(".csv", "").replace("_", " ")
                csvs.append({"label": f"{label}  ({rel})", "value": full})
    return sorted(csvs, key=lambda x: x["label"], reverse=True)


# ── Load & parse session ──
@callback(
    Output("session-store", "data"),
    Output("round-dropdown", "options"),
    Output("round-dropdown", "value"),
    Input("session-dropdown", "value"),
    Input("redetect-btn", "n_clicks"),
    State("param-gz-min", "value"),
    State("param-gz-stdev", "value"),
    State("param-roll-max", "value"),
    State("param-hold-min", "value"),
    State("param-hold-max", "value"),
    State("param-merge-gap", "value"),
    State("param-cooldown", "value"),
    State("param-detrend-win", "value"),
    State("param-stdev-win", "value"),
    prevent_initial_call=True,
)
def load_session(csv_path, n_redetect, gz_min, gz_stdev, roll_max,
                 hold_min, hold_max, merge_gap, cooldown, detrend_win, stdev_win):
    if not csv_path:
        return no_update, no_update, no_update

    params = {
        "detrend_win_sec": detrend_win or 60.0,
        "gz_min_detrended": gz_min or 2.0,
        "gz_stdev_max": gz_stdev or 1.0,
        "roll_max_detrended": roll_max or -0.5,
        "stdev_win_sec": stdev_win or 2.0,
        "hold_min_sec": hold_min or 3.0,
        "hold_max_sec": hold_max or 14.0,
        "merge_gap_sec": merge_gap or 1.0,
        "cooldown_sec": cooldown or 2.0,
    }

    session = parse_csv(csv_path)
    if session is None:
        return no_update, no_update, no_update

    session = detect_shots(session, params)

    # Cache full data server-side; only send a key to the browser
    data = _serialize_session(session, csv_path)
    import hashlib, time as _time
    cache_key = hashlib.md5(f"{csv_path}:{_time.time()}".encode()).hexdigest()[:12]
    _cache_put(cache_key, data)

    # Round dropdown options
    round_opts = [{"label": "All Rounds", "value": "all"}]
    for ra in session.round_analytics:
        n_shots = len(ra.detected_shots)
        round_opts.append({
            "label": f"Round {ra.round} ({n_shots} shots, {ra.end_sec - ra.start_sec:.0f}s)",
            "value": str(ra.round),
        })

    return cache_key, round_opts, "all"


def _serialize_session(session, csv_path):
    """Convert SessionAnalytics to a JSON-serializable dict for dcc.Store."""
    sensor = session.sensor
    return {
        "csv_path": csv_path,
        "duration_sec": session.duration_sec,
        "arrows_per_round": session.arrows_per_round,
        "sensor_time": sensor["time"].tolist(),
        "sensor_gz": sensor["gz"].tolist(),
        "sensor_roll": sensor["roll"].tolist(),
        "sensor_yaw": sensor["yaw"].tolist(),
        "sensor_pitch": sensor["pitch"].tolist(),
        "sensor_steps": sensor["steps"].tolist(),
        "sensor_gx": sensor["gx"].tolist(),
        "sensor_gy": sensor["gy"].tolist(),
        "gz_detrended": session.gz_detrended.tolist() if session.gz_detrended is not None else [],
        "roll_detrended": session.roll_detrended.tolist() if session.roll_detrended is not None else [],
        "pitch_detrended": session.pitch_detrended.tolist() if session.pitch_detrended is not None else [],
        "yaw_detrended": session.yaw_detrended.tolist() if session.yaw_detrended is not None else [],
        "gz_stdev_arr": session.gz_stdev_arr.tolist() if session.gz_stdev_arr is not None else [],
        "hr_samples": [{"time": t, "bpm": b} for t, b in session.hr_samples],
        "walking_intervals": [{"start": s, "end": e} for s, e in session.walking_intervals],
        "all_shots": [
            {
                "time": s.time, "start_sec": s.start_sec, "end_sec": s.end_sec,
                "hold_sec": s.hold_sec, "gz_mean": s.gz_mean, "gz_stdev": s.gz_stdev,
                "hr_at_shot": s.hr_at_shot,
            } for s in session.all_shots
        ],
        "round_analytics": [
            {
                "round": ra.round, "start_sec": ra.start_sec, "end_sec": ra.end_sec,
                "score": ra.score, "avg_hr": ra.avg_hr, "orig_csv_round": ra.orig_csv_round,
                "detected_shots": [
                    {
                        "time": s.time, "start_sec": s.start_sec, "end_sec": s.end_sec,
                        "hold_sec": s.hold_sec, "gz_mean": s.gz_mean, "gz_stdev": s.gz_stdev,
                        "hr_at_shot": s.hr_at_shot,
                    } for s in ra.detected_shots
                ],
                "walking_intervals": [{"start": s, "end": e} for s, e in ra.walking_intervals],
            } for ra in session.round_analytics
        ],
        "rounds": [
            {
                "number": r.number,
                "display_score": r.display_score,
                "confirmed_score": r.confirmed_score,
                "avg_heart_rate": r.avg_heart_rate,
                "avg_hold_ms": r.avg_hold_ms,
                "arrows": [
                    {"shot_number": a.shot_number, "zone": a.zone, "score": a.score}
                    for a in r.arrows
                ],
            } for r in session.rounds
        ],
    }


# ── Summary cards ──
@callback(
    Output("summary-cards", "children"),
    Input("session-store", "data"),
)
def update_summary(cache_key):
    data = _cache_get(cache_key) if cache_key else None
    if not data:
        return html.Div("Load a session to begin.", style={
            "color": COLORS["text_muted"], "padding": "40px", "textAlign": "center",
        })

    n_rounds = len(data["round_analytics"])
    n_shots = len(data["all_shots"])
    total_score = sum(r["display_score"] for r in data["rounds"])
    total_arrows = sum(len(r["arrows"]) for r in data["rounds"])
    avg_arrow = total_score / max(total_arrows, 1)
    duration_min = data["duration_sec"] / 60.0

    # Avg hold time
    hold_times = [s["hold_sec"] for s in data["all_shots"]]
    avg_hold = sum(hold_times) / len(hold_times) if hold_times else 0

    # Avg HR at shots
    hr_vals = [s["hr_at_shot"] for s in data["all_shots"] if s["hr_at_shot"]]
    avg_hr = sum(hr_vals) / len(hr_vals) if hr_vals else 0

    filename = os.path.basename(data["csv_path"])

    def stat_card(label, value, color=COLORS["cyan"]):
        return html.Div([
            html.Div(value, style={
                "fontSize": "22px", "fontWeight": "700", "color": color,
            }),
            html.Div(label, style={
                "fontSize": "11px", "color": COLORS["text_secondary"],
            }),
        ], style={
            "background": COLORS["card"], "border": f"1px solid {COLORS['card_border']}",
            "borderRadius": "8px", "padding": "12px 16px", "flex": "1",
            "minWidth": "120px",
        })

    return html.Div([
        html.Div([
            html.Span(filename.replace(".csv", ""), style={
                "fontSize": "16px", "fontWeight": "600", "color": COLORS["text"],
            }),
            html.Span(f"  {duration_min:.1f} min", style={
                "fontSize": "13px", "color": COLORS["text_muted"], "marginLeft": "12px",
            }),
        ], style={"marginBottom": "10px"}),
        html.Div([
            stat_card("Rounds", str(n_rounds)),
            stat_card("Detected Shots", str(n_shots)),
            stat_card("Total Score", f"{total_score:.0f}" if total_score > 0 else "—", COLORS["amber"]),
            stat_card("Avg/Arrow", f"{avg_arrow:.1f}" if avg_arrow > 0 else "—", COLORS["amber"]),
            stat_card("Avg Hold", f"{avg_hold:.1f}s" if avg_hold > 0 else "—", COLORS["green"]),
            stat_card("Avg HR", f"{avg_hr:.0f}" if avg_hr > 0 else "—", COLORS["pink"]),
        ], style={"display": "flex", "gap": "8px", "flexWrap": "wrap"}),
    ])


# ── Sensor charts ──
@callback(
    Output("sensor-chart", "figure"),
    Input("session-store", "data"),
    Input("round-dropdown", "value"),
    Input("show-detrended", "value"),
    Input("calibration-clicks", "data"),
    Input("calibration-results", "data"),
)
def update_charts(cache_key, round_filter, show_detrended, cal_clicks, cal_results):
    data = _cache_get(cache_key) if cache_key else None
    if not data:
        return go.Figure().update_layout(
            paper_bgcolor=COLORS["bg"], plot_bgcolor=COLORS["bg"],
            font_color=COLORS["text_muted"],
            annotations=[{"text": "No session loaded", "showarrow": False,
                          "font": {"size": 16}}],
        )

    show_det = "detrended" in (show_detrended or [])

    times_full = np.array(data["sensor_time"])
    gz_full = np.array(data["sensor_gz"])
    roll_full = np.array(data["sensor_roll"])
    pitch_full = np.array(data["sensor_pitch"])
    yaw_full = np.array(data["sensor_yaw"])

    # Downsample for display — keep max 2000 points via LTTB-like decimation
    MAX_POINTS = 2000
    if len(times_full) > MAX_POINTS:
        idx = _downsample_indices(times_full, gz_full, MAX_POINTS)
        times = times_full[idx]
        gz = gz_full[idx]
        roll_arr = roll_full[idx]
        pitch_arr = pitch_full[idx]
        yaw_arr = yaw_full[idx]
    else:
        idx = None
        times = times_full
        gz = gz_full
        roll_arr = roll_full
        pitch_arr = pitch_full
        yaw_arr = yaw_full

    def _maybe_ds(arr_data):
        if not arr_data:
            return None
        a = np.array(arr_data)
        return a[idx] if idx is not None else a

    gz_d = _maybe_ds(data["gz_detrended"])
    roll_d = _maybe_ds(data["roll_detrended"])
    pitch_d = _maybe_ds(data.get("pitch_detrended"))
    yaw_d = _maybe_ds(data.get("yaw_detrended"))
    gz_stdev = _maybe_ds(data["gz_stdev_arr"])

    hr_times = [h["time"] for h in data["hr_samples"]]
    hr_bpm = [h["bpm"] for h in data["hr_samples"]]

    # Determine time range
    t_min, t_max = times[0], times[-1]
    round_analytics = data["round_analytics"]

    if round_filter and round_filter != "all":
        rnd_num = int(round_filter)
        for ra in round_analytics:
            if ra["round"] == rnd_num:
                t_min = ra["start_sec"] - 5
                t_max = ra["end_sec"] + 5
                break

    # Subplot layout: Gravity Z | Pitch | Roll | Yaw | HR
    # In detrended mode, add gz_detrended+stdev row after gz
    if show_det:
        n_rows = 6
        row_titles = ["Gravity Z", "GZ Detrended + Stdev", "Pitch", "Roll", "Yaw", "Heart Rate"]
        heights = [0.18, 0.18, 0.14, 0.18, 0.14, 0.18]
    else:
        n_rows = 5
        row_titles = ["Gravity Z", "Pitch", "Roll", "Yaw", "Heart Rate"]
        heights = [0.22, 0.18, 0.22, 0.18, 0.20]

    fig = make_subplots(
        rows=n_rows, cols=1,
        shared_xaxes=True,
        row_heights=heights,
        subplot_titles=row_titles,
        vertical_spacing=0.03,
    )

    layout_kwargs = dict(
        paper_bgcolor=COLORS["bg"],
        plot_bgcolor="#0B1120",
        font=dict(color=COLORS["text_secondary"], size=11),
        margin=dict(l=50, r=20, t=40, b=30),
        showlegend=False,
        hovermode="x unified",
    )

    # Row index mapping
    if show_det:
        ROW_GZ, ROW_GZ_D, ROW_PITCH, ROW_ROLL, ROW_YAW, ROW_HR = 1, 2, 3, 4, 5, 6
    else:
        ROW_GZ, ROW_GZ_D, ROW_PITCH, ROW_ROLL, ROW_YAW, ROW_HR = 1, None, 2, 3, 4, 5

    # ── Gravity Z (raw) ──
    fig.add_trace(go.Scattergl(
        x=times, y=gz, name="gz",
        line=dict(color=COLORS["cyan"], width=1),
        hovertemplate="%{y:.2f}",
    ), row=ROW_GZ, col=1)

    # ── Gravity Z detrended + stdev ──
    if show_det and ROW_GZ_D and gz_d is not None:
        fig.add_trace(go.Scattergl(
            x=times, y=gz_d, name="gz_detrended",
            line=dict(color=COLORS["cyan"], width=1),
            hovertemplate="%{y:.2f}",
        ), row=ROW_GZ_D, col=1)
        if gz_stdev is not None:
            fig.add_trace(go.Scattergl(
                x=times, y=gz_stdev, name="gz_stdev",
                line=dict(color=COLORS["amber"], width=1, dash="dot"),
                hovertemplate="%{y:.2f}",
            ), row=ROW_GZ_D, col=1)

    # ── Pitch ──
    pitch_data = pitch_d if (show_det and pitch_d is not None) else pitch_arr
    pitch_name = "pitch_d" if show_det else "pitch"
    fig.add_trace(go.Scattergl(
        x=times, y=pitch_data, name=pitch_name,
        line=dict(color=COLORS["green"], width=1),
        hovertemplate="%{y:.2f}",
    ), row=ROW_PITCH, col=1)

    # ── Roll ──
    roll_data = roll_d if (show_det and roll_d is not None) else roll_arr
    roll_name = "roll_d" if show_det else "roll"
    fig.add_trace(go.Scattergl(
        x=times, y=roll_data, name=roll_name,
        line=dict(color="#A78BFA", width=1),
        hovertemplate="%{y:.2f}",
    ), row=ROW_ROLL, col=1)

    # ── Yaw ──
    yaw_data = yaw_d if (show_det and yaw_d is not None) else yaw_arr
    yaw_name = "yaw_d" if show_det else "yaw"
    fig.add_trace(go.Scattergl(
        x=times, y=yaw_data, name=yaw_name,
        line=dict(color=COLORS["amber"], width=1),
        hovertemplate="%{y:.2f}",
    ), row=ROW_YAW, col=1)

    # ── Heart Rate ──
    if hr_times:
        fig.add_trace(go.Scattergl(
            x=hr_times, y=hr_bpm, name="HR",
            mode="lines+markers",
            line=dict(color=COLORS["pink"], width=1.5),
            marker=dict(size=3),
            hovertemplate="%{y:.0f} bpm",
        ), row=ROW_HR, col=1)

    all_rows = list(range(1, n_rows + 1))

    # ── Walking intervals (grey shading on all subplots) ──
    for wi in data["walking_intervals"]:
        for row in all_rows:
            fig.add_vrect(
                x0=wi["start"], x1=wi["end"],
                fillcolor="rgba(100, 116, 139, 0.15)",
                line_width=0, row=row, col=1,
            )

    # ── Round boundaries ──
    for ra in round_analytics:
        for row in all_rows:
            fig.add_vline(
                x=ra["start_sec"],
                line=dict(color="rgba(148, 163, 184, 0.3)", width=1, dash="dash"),
                row=row, col=1,
            )

    # ── Detected shot markers ──
    shots = data["all_shots"]
    for i, shot in enumerate(shots):
        color = SHOT_COLORS[i % len(SHOT_COLORS)]
        for row in all_rows:
            fig.add_vrect(
                x0=shot["start_sec"], x1=shot["end_sec"],
                fillcolor=f"rgba({_hex_to_rgb(color)}, 0.12)",
                line_width=0, row=row, col=1,
            )
            fig.add_vline(
                x=shot["time"],
                line=dict(color=color, width=1.5, dash="dot"),
                annotation=dict(
                    text=f"S{i+1}", font=dict(size=9, color=color), showarrow=False,
                ) if row == 1 else None,
                row=row, col=1,
            )

    # ── Calibration marks (green dashed lines + shaded hold regions) ──
    if cal_results and cal_results.get("shots"):
        for j, cs in enumerate(cal_results["shots"]):
            for row in all_rows:
                fig.add_vrect(
                    x0=cs["start_sec"], x1=cs["end_sec"],
                    fillcolor="rgba(34, 197, 94, 0.18)",
                    line=dict(color=COLORS["green"], width=1),
                    row=row, col=1,
                )
                fig.add_vline(
                    x=cs["click_time"],
                    line=dict(color=COLORS["green"], width=2, dash="dash"),
                    annotation=dict(
                        text=f"C{j+1}", font=dict(size=10, color=COLORS["green"]),
                        showarrow=False,
                    ) if row == 1 else None,
                    row=row, col=1,
                )
    elif cal_clicks:
        # Show pending click markers (before calibration is computed)
        for j, ct in enumerate(cal_clicks):
            for row in all_rows:
                fig.add_vline(
                    x=ct,
                    line=dict(color=COLORS["green"], width=2, dash="dash"),
                    annotation=dict(
                        text=f"C{j+1}", font=dict(size=10, color=COLORS["green"]),
                        showarrow=False,
                    ) if row == 1 else None,
                    row=row, col=1,
                )

    # ── Axis styling ──
    fig.update_xaxes(gridcolor="rgba(51, 65, 85, 0.5)", range=[t_min, t_max])
    fig.update_yaxes(gridcolor="rgba(51, 65, 85, 0.5)")
    fig.update_xaxes(title_text="Time (seconds)", row=n_rows, col=1)
    fig.update_layout(**layout_kwargs, height=max(700, n_rows * 140))

    return fig


# ── Round details table ──
@callback(
    Output("round-table", "children"),
    Input("session-store", "data"),
)
def update_round_table(cache_key):
    data = _cache_get(cache_key) if cache_key else None
    if not data:
        return ""

    ra_list = data["round_analytics"]
    rounds = data["rounds"]

    # Build a lookup from round number to round summary
    round_lookup = {r["number"]: r for r in rounds}

    header = html.Tr([
        html.Th("Round", style=_th_style()),
        html.Th("Shots", style=_th_style()),
        html.Th("Score", style=_th_style()),
        html.Th("Hold (avg)", style=_th_style()),
        html.Th("HR", style=_th_style()),
        html.Th("Duration", style=_th_style()),
    ])

    rows = []
    for ra in ra_list:
        n_shots = len(ra["detected_shots"])
        holds = [s["hold_sec"] for s in ra["detected_shots"]]
        avg_hold = sum(holds) / len(holds) if holds else 0
        hrs = [s["hr_at_shot"] for s in ra["detected_shots"] if s["hr_at_shot"]]
        avg_hr = sum(hrs) / len(hrs) if hrs else 0
        dur = ra["end_sec"] - ra["start_sec"]

        # Score from round summary
        rs = round_lookup.get(ra["orig_csv_round"])
        score = rs["display_score"] if rs else ra["score"]

        rows.append(html.Tr([
            html.Td(f"R{ra['round']}", style=_td_style(COLORS["cyan"])),
            html.Td(str(n_shots), style=_td_style()),
            html.Td(f"{score:.0f}" if score > 0 else "—", style=_td_style(COLORS["amber"])),
            html.Td(f"{avg_hold:.1f}s" if avg_hold > 0 else "—", style=_td_style(COLORS["green"])),
            html.Td(f"{avg_hr:.0f}" if avg_hr > 0 else "—", style=_td_style(COLORS["pink"])),
            html.Td(f"{dur:.0f}s", style=_td_style()),
        ]))

    return html.Div([
        html.H3("Round Details", style={
            "fontSize": "15px", "fontWeight": "600", "color": COLORS["text"],
            "marginBottom": "8px",
        }),
        html.Table([html.Thead(header), html.Tbody(rows)], style={
            "width": "100%", "borderCollapse": "collapse",
        }),
    ], style={
        "background": COLORS["card"],
        "border": f"1px solid {COLORS['card_border']}",
        "borderRadius": "8px", "padding": "14px",
    })


# ── Zone distribution chart ──
@callback(
    Output("zone-chart-container", "children"),
    Input("session-store", "data"),
)
def update_zone_chart(cache_key):
    data = _cache_get(cache_key) if cache_key else None
    if not data or not data["rounds"]:
        return ""

    zone_counts: dict[str, int] = {}
    for r in data["rounds"]:
        for a in r["arrows"]:
            z = a["zone"]
            if z == "DNS":
                continue
            zone_counts[z] = zone_counts.get(z, 0) + 1

    if not zone_counts:
        return ""

    display_order = ["GOLD", "YELLOW", "RED", "BLUE", "BLACK", "WHITE", "MISS"]
    zones = [z for z in display_order if z in zone_counts]
    counts = [zone_counts[z] for z in zones]
    colors = [ZONE_COLORS.get(z, "#94A3B8") for z in zones]

    fig = go.Figure(go.Bar(
        x=zones, y=counts,
        marker_color=colors,
        text=counts, textposition="auto",
        textfont=dict(size=12, color="white"),
    ))
    fig.update_layout(
        paper_bgcolor=COLORS["card"],
        plot_bgcolor=COLORS["card"],
        font=dict(color=COLORS["text_secondary"], size=11),
        margin=dict(l=30, r=10, t=30, b=30),
        height=250,
        title=dict(text="Zone Distribution", font=dict(size=14, color=COLORS["text"])),
        xaxis=dict(gridcolor="rgba(51,65,85,0.3)"),
        yaxis=dict(gridcolor="rgba(51,65,85,0.3)"),
    )

    return html.Div([
        dcc.Graph(figure=fig, config={"displayModeBar": False}),
    ], style={
        "background": COLORS["card"],
        "border": f"1px solid {COLORS['card_border']}",
        "borderRadius": "8px", "padding": "8px",
    })


# ── Profile management ──
@callback(
    Output("profile-dropdown", "options"),
    Input("save-profile-btn", "n_clicks"),
    Input("scan-btn", "n_clicks"),
)
def refresh_profiles(*_):
    profiles = list_profiles()
    return [{"label": p, "value": p} for p in profiles]


@callback(
    Output("profile-name-input", "value"),
    Input("save-profile-btn", "n_clicks"),
    State("profile-name-input", "value"),
    State("param-gz-min", "value"),
    State("param-gz-stdev", "value"),
    State("param-roll-max", "value"),
    State("param-hold-min", "value"),
    State("param-hold-max", "value"),
    State("param-merge-gap", "value"),
    State("param-cooldown", "value"),
    State("param-detrend-win", "value"),
    State("param-stdev-win", "value"),
    prevent_initial_call=True,
)
def save_profile_cb(n_clicks, name, gz_min, gz_stdev, roll_max,
                    hold_min, hold_max, merge_gap, cooldown, detrend_win, stdev_win):
    if not name:
        return no_update
    params = {
        "gz_min_detrended": gz_min,
        "gz_stdev_max": gz_stdev,
        "roll_max_detrended": roll_max,
        "hold_min_sec": hold_min,
        "hold_max_sec": hold_max,
        "merge_gap_sec": merge_gap,
        "cooldown_sec": cooldown,
        "detrend_win_sec": detrend_win,
        "stdev_win_sec": stdev_win,
    }
    save_profile(name, params)
    return ""


@callback(
    Output("param-gz-min", "value"),
    Output("param-gz-stdev", "value"),
    Output("param-roll-max", "value"),
    Output("param-hold-min", "value"),
    Output("param-hold-max", "value"),
    Output("param-merge-gap", "value"),
    Output("param-cooldown", "value"),
    Output("param-detrend-win", "value"),
    Output("param-stdev-win", "value"),
    Input("profile-dropdown", "value"),
    Input("apply-calibration-btn", "n_clicks"),
    State("calibration-results", "data"),
    prevent_initial_call=True,
)
def load_params_cb(profile_name, apply_clicks, cal_results):
    trigger = ctx.triggered_id
    if trigger == "apply-calibration-btn" and cal_results and cal_results.get("suggested"):
        p = cal_results["suggested"]
    elif trigger == "profile-dropdown" and profile_name:
        p = load_profile(profile_name)
    else:
        return (no_update,) * 9
    return (
        p.get("gz_min_detrended", 2.0),
        p.get("gz_stdev_max", 1.0),
        p.get("roll_max_detrended", -0.5),
        p.get("hold_min_sec", 3.0),
        p.get("hold_max_sec", 14.0),
        p.get("merge_gap_sec", 1.0),
        p.get("cooldown_sec", 2.0),
        p.get("detrend_win_sec", 60.0),
        p.get("stdev_win_sec", 2.0),
    )


# ── Calibration: toggle button style ──
@callback(
    Output("calibrate-toggle", "style"),
    Output("calibrate-toggle", "children"),
    Input("calibrate-toggle", "n_clicks"),
)
def toggle_calibrate_style(n_clicks):
    active = (n_clicks or 0) % 2 == 1
    base = {
        "width": "100%", "border": "none", "borderRadius": "4px",
        "padding": "8px", "cursor": "pointer", "fontSize": "12px",
        "marginBottom": "6px",
    }
    if active:
        base["background"] = COLORS["green"]
        base["color"] = "white"
        return base, "Calibrating... (click chart)"
    else:
        base["background"] = COLORS["card_border"]
        base["color"] = COLORS["text"]
        return base, "Enable Calibration"


# ── Calibration: capture clicks on chart ──
@callback(
    Output("calibration-clicks", "data"),
    Output("calibration-results", "data"),
    Output("calibration-stats", "children"),
    Input("sensor-chart", "clickData"),
    Input("clear-marks-btn", "n_clicks"),
    Input("session-dropdown", "value"),
    State("calibrate-toggle", "n_clicks"),
    State("calibration-clicks", "data"),
    State("session-store", "data"),
    prevent_initial_call=True,
)
def handle_calibration(click_data, clear_clicks, session_val,
                       cal_toggle_clicks, current_clicks, cache_key):
    session_data = _cache_get(cache_key) if cache_key else None
    trigger = ctx.triggered_id
    clicks = list(current_clicks or [])

    # Clear marks
    if trigger in ("clear-marks-btn", "session-dropdown"):
        return [], None, ""

    # Only process clicks if calibration is active
    is_active = (cal_toggle_clicks or 0) % 2 == 1
    if trigger == "sensor-chart" and is_active and click_data and session_data:
        point = click_data["points"][0]
        click_time = point["x"]
        clicks.append(float(click_time))

    if not clicks or not session_data:
        return clicks, None, ""

    # Run calibration
    session = _deserialize_for_calibration(session_data)
    if session is None:
        return clicks, None, ""

    result = calibrate_from_clicks(session, clicks)

    # Build stats display
    stats_children = []
    stats_children.append(html.Div(
        f"{len(result.shots)} marked shot(s)",
        style={"fontWeight": "600", "color": COLORS["green"], "marginBottom": "6px"},
    ))

    if result.shots:
        # Per-shot stats table
        header = html.Tr([
            html.Th("#", style={"padding": "2px 4px"}),
            html.Th("Hold", style={"padding": "2px 4px"}),
            html.Th("gz_d", style={"padding": "2px 4px"}),
            html.Th("stdev", style={"padding": "2px 4px"}),
            html.Th("roll_d", style={"padding": "2px 4px"}),
            html.Th("pitch_d", style={"padding": "2px 4px"}),
            html.Th("HR", style={"padding": "2px 4px"}),
        ])
        rows = []
        for i, s in enumerate(result.shots):
            rows.append(html.Tr([
                html.Td(f"C{i+1}", style={"padding": "2px 4px", "color": COLORS["green"]}),
                html.Td(f"{s['hold_sec']:.1f}s", style={"padding": "2px 4px"}),
                html.Td(f"{s['gz_d_mean']:.1f}", style={"padding": "2px 4px"}),
                html.Td(f"{s['gz_stdev_max']:.2f}", style={"padding": "2px 4px"}),
                html.Td(f"{s['roll_d_mean']:.2f}", style={"padding": "2px 4px"}),
                html.Td(f"{s['pitch_d_mean']:.2f}", style={"padding": "2px 4px"}),
                html.Td(f"{s['hr_at_shot']:.0f}" if s['hr_at_shot'] else "—",
                         style={"padding": "2px 4px"}),
            ]))
        stats_children.append(html.Table(
            [html.Thead(header), html.Tbody(rows)],
            style={"width": "100%", "borderCollapse": "collapse",
                   "color": COLORS["text_secondary"]},
        ))

        # Suggested params summary
        sp = result.suggested_params
        stats_children.append(html.Div([
            html.Div("Suggested:", style={
                "fontWeight": "600", "color": COLORS["amber"], "marginTop": "8px",
                "marginBottom": "4px",
            }),
            html.Div(f"gz_min: {sp['gz_min_detrended']:.2f}", style={"color": COLORS["text_muted"]}),
            html.Div(f"gz_stdev_max: {sp['gz_stdev_max']:.2f}", style={"color": COLORS["text_muted"]}),
            html.Div(f"roll_max: {sp['roll_max_detrended']:.2f}", style={"color": COLORS["text_muted"]}),
            html.Div(f"hold: {sp['hold_min_sec']:.1f}–{sp['hold_max_sec']:.1f}s",
                     style={"color": COLORS["text_muted"]}),
        ]))

    cal_data = {
        "shots": result.shots,
        "suggested": result.suggested_params,
    }
    return clicks, cal_data, stats_children


def _deserialize_for_calibration(data):
    """Reconstruct enough of SessionAnalytics for calibration."""
    import pandas as pd
    from parser import SessionAnalytics

    sensor = pd.DataFrame({
        "time": data["sensor_time"],
        "gz": data["sensor_gz"],
        "roll": data["sensor_roll"],
        "pitch": data["sensor_pitch"],
        "yaw": data["sensor_yaw"],
        "steps": data.get("sensor_steps", [0] * len(data["sensor_time"])),
        "gx": data.get("sensor_gx", [0] * len(data["sensor_time"])),
        "gy": data.get("sensor_gy", [0] * len(data["sensor_time"])),
    })

    session = SessionAnalytics(
        duration_sec=data["duration_sec"],
        sensor=sensor,
        hr_samples=[(h["time"], h["bpm"]) for h in data["hr_samples"]],
        walking_intervals=[(w["start"], w["end"]) for w in data["walking_intervals"]],
    )

    session.gz_detrended = np.array(data["gz_detrended"]) if data.get("gz_detrended") else None
    session.roll_detrended = np.array(data["roll_detrended"]) if data.get("roll_detrended") else None
    session.pitch_detrended = np.array(data["pitch_detrended"]) if data.get("pitch_detrended") else None
    session.yaw_detrended = np.array(data["yaw_detrended"]) if data.get("yaw_detrended") else None
    session.gz_stdev_arr = np.array(data["gz_stdev_arr"]) if data.get("gz_stdev_arr") else None

    return session


# ═══════════════ HELPERS ═══════════════

def _downsample_indices(times, values, max_points):
    """
    Min-max decimation: split data into buckets, keep the index of the
    min and max value in each bucket. Preserves peaks/valleys for visual
    fidelity while cutting point count roughly in half of max_points.
    """
    n = len(times)
    bucket_size = max(n // (max_points // 2), 1)
    indices = [0]  # always keep first
    for b_start in range(0, n, bucket_size):
        b_end = min(b_start + bucket_size, n)
        chunk = values[b_start:b_end]
        i_min = b_start + int(np.argmin(chunk))
        i_max = b_start + int(np.argmax(chunk))
        # Add in time order
        lo, hi = min(i_min, i_max), max(i_min, i_max)
        if lo != indices[-1]:
            indices.append(lo)
        if hi != lo:
            indices.append(hi)
    if indices[-1] != n - 1:
        indices.append(n - 1)  # always keep last
    return np.array(indices)


def _hex_to_rgb(hex_color):
    """Convert '#RRGGBB' to 'R, G, B' string."""
    h = hex_color.lstrip("#")
    return f"{int(h[0:2], 16)}, {int(h[2:4], 16)}, {int(h[4:6], 16)}"


def _th_style():
    return {
        "fontSize": "11px", "fontWeight": "600", "color": COLORS["text_secondary"],
        "padding": "8px 10px", "textAlign": "left",
        "borderBottom": f"1px solid {COLORS['card_border']}",
    }


def _td_style(color=None):
    return {
        "fontSize": "13px", "color": color or COLORS["text"],
        "padding": "8px 10px",
        "borderBottom": f"1px solid {COLORS['card_border']}",
    }


# ═══════════════ RUN ═══════════════

if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0", port=8050)
