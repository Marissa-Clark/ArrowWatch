package com.archery.shared

/**
 * Wear Data Layer path and message key constants shared by watch and phone.
 */
object WearPaths {

    // ── Data Layer asset (watch → phone, session end) ────────────────────────
    const val DATA_SESSION_CSV = "/archery2/session/csv"
    const val KEY_CSV_FILE_NAME = "fileName"
    const val KEY_CSV_ASSET = "csvAsset"

    // ── Live messages (watch → phone) ────────────────────────────────────────
    const val MSG_SESSION_START = "/archery2/live/session_start"
    const val MSG_SHOT = "/archery2/live/shot"
    const val MSG_PHASE_CHANGE = "/archery2/live/phase_change"
    const val MSG_ARROW_SCORED = "/archery2/live/arrow_scored"
    const val MSG_ROUND_TOTAL = "/archery2/live/round_total"
    const val MSG_ROUND_COMPLETE = "/archery2/live/round_complete"
    const val MSG_SESSION_END = "/archery2/live/session_end"

    // ── Commands (phone → watch) ─────────────────────────────────────────────
    const val CMD_START_SESSION = "/archery2/cmd/start_session"
    const val CMD_END_SESSION = "/archery2/cmd/end_session"
    const val CMD_ENTER_SCORING = "/archery2/cmd/enter_scoring"
    const val CMD_SCORE_ARROW = "/archery2/cmd/score_arrow"
    const val CMD_SET_ROUND_TOTAL = "/archery2/cmd/set_round_total"
    const val CMD_FINISH_SCORING = "/archery2/cmd/finish_scoring"

    // ── Message payload keys ─────────────────────────────────────────────────
    const val KEY_ARROWS_PER_ROUND = "arrowsPerRound"
    const val KEY_ROUND = "round"
    const val KEY_SHOT_NUMBER = "shotNumber"
    const val KEY_HOLD_MS = "holdMs"
    const val KEY_HEART_RATE = "heartRate"
    const val KEY_ZONE = "zone"
    const val KEY_SCORE = "score"
    const val KEY_PHASE = "phase"
    const val KEY_TOTAL = "total"
    const val KEY_CONFIRMED = "confirmed"
    const val KEY_APPROX_SCORE = "approxScore"
    const val KEY_ACTUAL_SCORE = "actualScore"
    const val KEY_SHOT_INDEX = "shotIndex"
    const val KEY_EXACT_SCORE = "exactScore"
    const val KEY_SESSION_START_MS = "sessionStartMs"
}
