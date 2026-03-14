package com.archery.parser

import com.archery.shared.ArrowScore
import com.archery.shared.CsvFormat
import com.archery.shared.RoundSummary
import com.archery.shared.ScoreZone
import com.archery.shared.SessionSummary
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Parses a session CSV file into a [SessionSummary].
 *
 * Edit events (edit_score, edit_round_total, delete_round) are applied after
 * all original events are collected — last value wins per shot/round.
 * Split/unsplit handling is deferred to a later implementation.
 */
object SessionParser {

    fun parse(file: File, dateMs: Long): SessionSummary? {
        if (!file.exists()) return null

        val lines = file.readLines()
            .drop(1)                          // skip header
            .filter { it.isNotBlank() }
            .map { it.split(",") }

        // ── Pass 1: collect all events ───────────────────────────────────────

        // round → (shotNumber → ArrowScore), mutable so edits overwrite
        val arrowsByRound = mutableMapOf<Int, MutableMap<Int, ArrowScore>>()
        // round → detectedScore (approx_score event)
        val detectedScores = mutableMapOf<Int, Float>()
        // round → confirmedScore (actual_score or edit_round_total event)
        val confirmedScores = mutableMapOf<Int, Float>()
        // deleted round numbers
        val deletedRounds = mutableSetOf<Int>()

        // round → list of (elapsed, holdMs) for hold stats
        val holdMsByRound = mutableMapOf<Int, MutableList<Long>>()
        // round → list of heart rate readings during that round
        val hrByRound = mutableMapOf<Int, MutableList<Float>>()

        var sessionDurationSec = 0L
        var lastElapsed = 0f
        var sessionStartElapsed: Float? = null

        for (cols in lines) {
            if (cols.size <= CsvFormat.COL_EVENT_TYPE) continue
            val elapsed = cols.getOrNull(CsvFormat.COL_ELAPSED_SEC)?.toFloatOrNull() ?: continue
            val event = cols.getOrNull(CsvFormat.COL_EVENT_TYPE) ?: continue
            lastElapsed = elapsed
            if (sessionStartElapsed == null) sessionStartElapsed = elapsed

            val round = cols.getOrNull(CsvFormat.COL_ROUND)?.toIntOrNull()
            val shot = cols.getOrNull(CsvFormat.COL_SHOT_NUMBER)?.toIntOrNull()
            val holdMs = cols.getOrNull(CsvFormat.COL_HOLD_MS)?.toLongOrNull()
            val zone = cols.getOrNull(CsvFormat.COL_ZONE)?.takeIf { it.isNotEmpty() }
            val score = cols.getOrNull(CsvFormat.COL_SCORE)?.toFloatOrNull()
            val hr = cols.getOrNull(CsvFormat.COL_HEART_RATE)?.toFloatOrNull()

            when (event) {
                CsvFormat.EVENT_QUICK_SCORE -> {
                    if (round != null && shot != null && zone != null && score != null) {
                        arrowsByRound
                            .getOrPut(round) { mutableMapOf() }
                            .putIfAbsent(
                                shot,
                                ArrowScore(shot, ScoreZone.fromLabel(zone), score, isFinal = false),
                            )
                    }
                }
                CsvFormat.EVENT_FINAL_SCORE -> {
                    if (round != null && shot != null && zone != null && score != null) {
                        arrowsByRound
                            .getOrPut(round) { mutableMapOf() }[shot] =
                            ArrowScore(shot, ScoreZone.fromLabel(zone), score, isFinal = true)
                    }
                }
                CsvFormat.EVENT_APPROX_SCORE -> {
                    if (round != null && score != null) detectedScores[round] = score
                }
                CsvFormat.EVENT_ACTUAL_SCORE -> {
                    if (round != null && score != null) confirmedScores[round] = score
                }
                CsvFormat.EVENT_EDIT_SCORE -> {
                    if (round != null && shot != null && zone != null && score != null) {
                        arrowsByRound
                            .getOrPut(round) { mutableMapOf() }[shot] =
                            ArrowScore(shot, ScoreZone.fromLabel(zone), score, isFinal = true)
                    }
                }
                CsvFormat.EVENT_EDIT_ROUND_TOTAL -> {
                    if (round != null && score != null) confirmedScores[round] = score
                }
                CsvFormat.EVENT_DELETE_ROUND -> {
                    if (round != null) deletedRounds.add(round)
                }
                CsvFormat.EVENT_MANUAL_SHOT,
                CsvFormat.EVENT_AUTO_SHOT -> {
                    if (round != null && holdMs != null && holdMs > 0) {
                        holdMsByRound.getOrPut(round) { mutableListOf() }.add(holdMs)
                    }
                    if (round != null && hr != null && hr > 0) {
                        hrByRound.getOrPut(round) { mutableListOf() }.add(hr)
                    }
                }
                CsvFormat.EVENT_HEART_RATE -> {
                    // Assign HR readings to rounds based on time — simplified:
                    // attribute to the highest round number seen so far
                    val currentRound = arrowsByRound.keys.maxOrNull() ?: 1
                    if (hr != null && hr > 0) {
                        hrByRound.getOrPut(currentRound) { mutableListOf() }.add(hr)
                    }
                }
                CsvFormat.EVENT_SESSION_END -> {
                    sessionDurationSec = elapsed.toLong()
                }
            }
        }

        if (sessionDurationSec == 0L) sessionDurationSec = lastElapsed.toLong()

        // ── Pass 2: build rounds ─────────────────────────────────────────────

        val allRoundNumbers = (arrowsByRound.keys + detectedScores.keys + confirmedScores.keys)
            .toSortedSet()
            .filter { it !in deletedRounds }

        if (allRoundNumbers.isEmpty()) return null

        val rounds = allRoundNumbers.mapIndexed { idx, origRound ->
            val arrows = arrowsByRound[origRound]
                ?.values
                ?.sortedBy { it.shotNumber }
                ?: emptyList()
            val arrowSum = arrows.sumOf { it.score.toDouble() }.toFloat()
            val detected = detectedScores[origRound] ?: arrowSum
            val confirmed = confirmedScores[origRound]
            val holds = holdMsByRound[origRound]
            val hrs = hrByRound[origRound]

            RoundSummary(
                number = idx + 1,
                arrows = arrows,
                detectedScore = detected,
                confirmedScore = confirmed,
                avgHeartRate = hrs?.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                avgHoldMs = holds?.takeIf { it.isNotEmpty() }?.map { it.toLong() }?.average()?.toLong(),
            )
        }

        // ── Aggregate stats ──────────────────────────────────────────────────

        val allArrows = rounds.flatMap { it.arrows }
        val zoneCounts = allArrows
            .groupingBy { it.zone }
            .eachCount()

        val allHr = hrByRound.values.flatten()
        val avgHr = allHr.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        val allHolds = holdMsByRound.values.flatten()
        val avgHold = allHolds.takeIf { it.isNotEmpty() }?.average()?.toLong()

        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateMs), ZoneId.systemDefault())

        return SessionSummary(
            fileName = file.name,
            filePath = file.absolutePath,
            date = date,
            durationSec = sessionDurationSec,
            rounds = rounds,
            zoneCounts = zoneCounts,
            avgHeartRate = avgHr,
            avgHoldMs = avgHold,
        )
    }
}
