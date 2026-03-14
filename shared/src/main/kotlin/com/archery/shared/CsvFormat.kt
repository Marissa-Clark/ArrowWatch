package com.archery.shared

/**
 * CSV column indices and event type string constants.
 * The CSV written by the watch has a fixed column layout; these indices
 * are used by the phone-side parser.
 */
object CsvFormat {

    const val HEADER = "elapsed_sec,event_type,round,shot_number,hold_ms," +
            "gravity_z,roll_swing,zone,score,heart_rate,yaw,pitch,roll,steps"

    // ── Column indices (0-based) ─────────────────────────────────────────────
    const val COL_ELAPSED_SEC = 0
    const val COL_EVENT_TYPE = 1
    const val COL_ROUND = 2
    const val COL_SHOT_NUMBER = 3
    const val COL_HOLD_MS = 4       // doubles as splitTimeSec for split_round events
    const val COL_GRAVITY_Z = 5
    const val COL_ROLL_SWING = 6    // legacy, always 0
    const val COL_ZONE = 7
    const val COL_SCORE = 8
    const val COL_HEART_RATE = 9
    const val COL_YAW = 10
    const val COL_PITCH = 11
    const val COL_ROLL = 12
    const val COL_STEPS = 13

    // ── Event types ──────────────────────────────────────────────────────────
    const val EVENT_SENSOR = "sensor"
    const val EVENT_MANUAL_SHOT = "manual_shot"
    const val EVENT_AUTO_SHOT   = "auto_shot"
    const val EVENT_QUICK_SCORE = "quick_score"
    const val EVENT_FINAL_SCORE = "final_score"
    const val EVENT_APPROX_SCORE = "approx_score"
    const val EVENT_ACTUAL_SCORE = "actual_score"
    const val EVENT_WALKING_START = "walking_start"
    const val EVENT_WALKING_STOP = "walking_stop"
    const val EVENT_HEART_RATE = "heart_rate"
    const val EVENT_SESSION_END = "session_end"
    const val EVENT_EDIT_SCORE = "edit_score"
    const val EVENT_EDIT_ROUND_TOTAL = "edit_round_total"
    const val EVENT_DELETE_ROUND = "delete_round"
    const val EVENT_SPLIT_ROUND = "split_round"
    const val EVENT_UNSPLIT_ROUND = "unsplit_round"
}
