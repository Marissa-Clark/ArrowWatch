package com.archery.analytics

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

private const val DISMISSED_TAG = "DismissedShots"

// ═══════════════ DATA MODELS ═══════════════

data class SensorSample(
    val time: Float,
    val gz: Float,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val steps: Long = 0,
    val gx: Float = 0f,   // gravity X (col 14)
    val gy: Float = 0f,   // gravity Y (col 15)
)

data class HrPoint(val time: Float, val bpm: Float)

data class DetectedShot(
    val time: Float,      // mid-point of hold window
    val startSec: Float,
    val endSec: Float,
    val holdSec: Float,   // end - start
    val nSamples: Int,
    val gzMean: Float,
    /** Std deviation of gz during the hold window — lower = more stable arm position. */
    val gzStdev: Float = 0f,
    /** Heart rate interpolated at the shot mid-point; null if no HR data available. */
    val hrAtShot: Float? = null,
    /** Raw sensor samples captured during the hold window (startSec..endSec). */
    val sensorWindow: List<SensorSample> = emptyList(),
    /** Detrended gz (gz minus rolling mean) during the hold window. */
    val gzDWindow: List<Float> = emptyList(),
    /** Rate of change of detrended gz (central-difference derivative) during the hold window. */
    val gzDdtWindow: List<Float> = emptyList(),
)

/**
 * A window that passed the gz_ddt band + gzD threshold but was too short to qualify.
 * Represents a likely shot attempt that didn't meet [holdNeeded].
 */
data class NearMissShot(
    val startSec: Float,
    val endSec: Float,
    /** Actual duration — always < [holdNeeded]. */
    val holdSec: Float,
    /** Profile holdMinSec baked in so the UI can show the gap without re-passing the profile. */
    val holdNeeded: Float,
    val gzDMean: Float,
    val sensorWindow: List<SensorSample> = emptyList(),
    val gzDWindow: List<Float> = emptyList(),
)

/** Combined return value from [AnalyticsParser.detectShots]. */
data class DetectionResult(
    val shots: List<DetectedShot>,
    val nearMisses: List<NearMissShot>,
)

data class RoundAnalytics(
    val round: Int,
    val startSec: Float,
    val endSec: Float,
    val score: Float,
    val detectedShots: List<DetectedShot>,
    val sensorData: List<SensorSample>,
    val walkingIntervals: List<Pair<Float, Float>>,
    val avgHr: Float,
    val hrSamples: List<HrPoint>,
    val origCsvRound: Int = 0,
    val nearMisses: List<NearMissShot> = emptyList(),
)

data class SessionAnalytics(
    val durationSec: Float,
    val hrSamples: List<HrPoint>,
    val walkingIntervals: List<Pair<Float, Float>>,
    val roundAnalytics: List<RoundAnalytics>,
    val allShots: List<DetectedShot>,
    val arrowsPerRound: Int,
)

// ═══════════════ DISMISSED SHOTS PERSISTENCE ═══════════════

fun dismissedFilePath(sessionCsvPath: String): String =
    sessionCsvPath.removeSuffix(".csv") + ".dismissed.json"

fun loadDismissedShots(sessionCsvPath: String): MutableMap<Int, MutableSet<Int>> {
    val result = mutableMapOf<Int, MutableSet<Int>>()
    val path = dismissedFilePath(sessionCsvPath)
    try {
        val file = File(path)
        if (!file.exists()) {
            Log.d(DISMISSED_TAG, "No dismissed file at $path")
            return result
        }
        val json = JSONArray(file.readText())
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val round = obj.getInt("round")
            val shotIndex = obj.getInt("shotIndex")
            result.getOrPut(round) { mutableSetOf() }.add(shotIndex)
        }
        Log.d(DISMISSED_TAG, "Loaded ${result.values.sumOf { it.size }} dismissed shots from $path")
    } catch (e: Exception) {
        Log.e(DISMISSED_TAG, "Failed to load dismissed shots from $path", e)
    }
    return result
}

fun saveDismissedShots(sessionCsvPath: String, dismissed: Map<Int, Set<Int>>) {
    val path = dismissedFilePath(sessionCsvPath)
    try {
        val arr = JSONArray()
        dismissed.forEach { (round, indices) ->
            indices.sorted().forEach { idx ->
                arr.put(JSONObject().apply {
                    put("round", round)
                    put("shotIndex", idx)
                })
            }
        }
        val file = File(path)
        file.parentFile?.mkdirs()          // ensure directory exists
        file.writeText(arr.toString(2))
        Log.d(DISMISSED_TAG, "Saved ${arr.length()} dismissed shots to $path")
    } catch (e: Exception) {
        Log.e(DISMISSED_TAG, "Failed to save dismissed shots to $path", e)
    }
}

// ═══════════════ MANUAL SHOTS PERSISTENCE ═══════════════

private fun manualShotsFilePath(csvPath: String) =
    csvPath.removeSuffix(".csv") + ".manual.json"

fun loadManualShots(csvPath: String): MutableMap<Int, MutableList<Float>> {
    val result = mutableMapOf<Int, MutableList<Float>>()
    if (csvPath.isEmpty()) return result
    try {
        val file = File(manualShotsFilePath(csvPath))
        if (!file.exists()) return result
        val json = JSONArray(file.readText())
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val round = obj.getInt("round")
            val timeSec = obj.getDouble("timeSec").toFloat()
            result.getOrPut(round) { mutableListOf() }.add(timeSec)
        }
    } catch (e: Exception) {
        Log.e(DISMISSED_TAG, "Failed to load manual shots", e)
    }
    return result
}

fun saveManualShots(csvPath: String, manual: Map<Int, List<Float>>) {
    if (csvPath.isEmpty()) return
    try {
        val arr = JSONArray()
        manual.forEach { (round, times) ->
            times.sorted().forEach { t ->
                arr.put(JSONObject().apply { put("round", round); put("timeSec", t.toDouble()) })
            }
        }
        val file = File(manualShotsFilePath(csvPath))
        file.parentFile?.mkdirs()
        file.writeText(arr.toString(2))
    } catch (e: Exception) {
        Log.e(DISMISSED_TAG, "Failed to save manual shots", e)
    }
}

// ═══════════════ PARSER + SHOT DETECTION ═══════════════

private data class RoundWindow(
    val roundNum: Int,
    val start: Float,
    val end: Float,
    val score: Float,
    val origCsvRound: Int = 0,
)

object AnalyticsParser {

    // ── Threshold detector constants ──────────────────────────────────────────
    // Detection: gz_ddt (rate-of-change of detrended gz) within a band AND
    // gz_detrended >= GZ_MIN_DETRENDED for at least HOLD_MIN_SEC continuously.
    private const val DETREND_WIN_SEC   = 60f    // rolling-mean window for detrending
    private const val GZ_MIN_DETRENDED = 0f     // detrended gz must be >= this (arm raised)
    private const val GZ_DDT_MIN       = -5f    // gz_ddt lower bound (widen to disable)
    private const val GZ_DDT_MAX       =  5f    // gz_ddt upper bound (widen to disable)
    private const val HOLD_MIN_SEC     =  3.0f  // minimum hold duration (s)
    private const val HOLD_MAX_SEC     = 14.0f  // maximum hold duration (s)
    private const val MERGE_GAP_SEC    =  1.0f  // merge segments within this gap (s)
    private const val COOLDOWN_SEC     =  2.0f  // minimum gap between shots (s)

    // Used only by findShootingGap for round splitting
    private const val SPLIT_GZ_LOW = 5.0f

    fun parse(filePath: String, profile: DetectionProfile = DEFAULT_PROFILE): SessionAnalytics? {
        val file = File(filePath)
        if (!file.exists()) return null
        return parse(file, profile)
    }

    fun parse(file: File, profile: DetectionProfile = DEFAULT_PROFILE): SessionAnalytics? {
        if (!file.exists()) return null
        try {
            val lines = file.readLines()
            if (lines.size < 2) return null

            val sensorSamples   = mutableListOf<SensorSample>()
            val hrSamples       = mutableListOf<HrPoint>()
            val walkStarts      = mutableListOf<Float>()
            val walkStops       = mutableListOf<Float>()
            val roundScoreTimes = mutableMapOf<Int, Float>()
            val roundScores     = mutableMapOf<Int, Float>()
            val deletedRounds   = mutableSetOf<Int>()
            val arrowCounts     = mutableMapOf<Int, Int>()
            var durationSec     = 0f
            var arrowsPerRound  = 3

            for (line in lines.drop(1)) {
                val cols = line.split(",")
                if (cols.size < 2) continue
                val elapsed = cols[0].toFloatOrNull() ?: continue
                val event   = cols[1].trim()

                when (event) {
                    "sensor" -> {
                        val gz    = cols.getOrNull(5)?.toFloatOrNull()         ?: continue
                        val yaw   = cols.getOrNull(10)?.toFloatOrNull()        ?: 0f
                        val pitch = cols.getOrNull(11)?.toFloatOrNull()        ?: 0f
                        val roll  = cols.getOrNull(12)?.toFloatOrNull()        ?: 0f
                        val steps = cols.getOrNull(13)?.trim()?.toLongOrNull() ?: 0L
                        val gx    = cols.getOrNull(14)?.toFloatOrNull()        ?: 0f
                        val gy    = cols.getOrNull(15)?.toFloatOrNull()        ?: 0f
                        sensorSamples.add(SensorSample(elapsed, gz, yaw, pitch, roll, steps, gx, gy))
                    }
                    "heart_rate" -> {
                        val bpm = cols.getOrNull(9)?.toFloatOrNull() ?: 0f
                        if (bpm > 0f) hrSamples.add(HrPoint(elapsed, bpm))
                    }
                    "walking_start" -> walkStarts.add(elapsed)
                    "walking_stop"  -> walkStops.add(elapsed)
                    "actual_score"  -> {
                        val round = cols.getOrNull(2)?.toIntOrNull() ?: continue
                        val score = cols.getOrNull(8)?.toFloatOrNull() ?: 0f
                        roundScores[round] = score
                        roundScoreTimes[round] = elapsed
                    }
                    "approx_score" -> {
                        val round = cols.getOrNull(2)?.toIntOrNull() ?: continue
                        val score = cols.getOrNull(8)?.toFloatOrNull() ?: 0f
                        if (!roundScores.containsKey(round))     roundScores[round]     = score
                        if (!roundScoreTimes.containsKey(round)) roundScoreTimes[round] = elapsed
                    }
                    // "scoring" is logged when walking detection triggers the scoring screen.
                    // Use as round-end fallback if no approx_score follows (e.g. session without scoring).
                    "scoring" -> {
                        val round = cols.getOrNull(2)?.toIntOrNull() ?: continue
                        if (!roundScoreTimes.containsKey(round)) roundScoreTimes[round] = elapsed
                    }
                    "final_score", "quick_score" -> {
                        val round = cols.getOrNull(2)?.toIntOrNull() ?: continue
                        roundScoreTimes[round] = elapsed
                        arrowCounts[round] = (arrowCounts[round] ?: 0) + 1
                    }
                    "edit_round_total" -> {
                        val round = cols.getOrNull(2)?.toIntOrNull() ?: continue
                        val score = cols.getOrNull(8)?.toFloatOrNull() ?: 0f
                        roundScores[round] = score
                    }
                    "delete_round" -> {
                        val round = cols.getOrNull(2)?.toIntOrNull() ?: continue
                        deletedRounds.add(round)
                    }
                    "session_end" -> durationSec = elapsed
                }
            }

            if (durationSec == 0f) {
                durationSec = maxOf(
                    sensorSamples.lastOrNull()?.time ?: 0f,
                    hrSamples.lastOrNull()?.time ?: 0f,
                )
            }

            if (arrowCounts.isNotEmpty()) {
                arrowsPerRound = arrowCounts.values
                    .groupBy { it }
                    .maxByOrNull { it.value.size }
                    ?.key ?: 3
            }

            val hasSteps    = sensorSamples.any { it.steps > 0 }
            val cleanWalking = if (hasSteps) {
                deriveWalkingFromSteps(sensorSamples)
            } else {
                cleanWalkingIntervals(walkStarts, walkStops, durationSec)
            }

            val detection = detectShots(sensorSamples, hrSamples = hrSamples, profile = profile)
            val allShots  = detection.shots
            val allNearMisses = detection.nearMisses

            val sortedRounds = roundScoreTimes.keys
                .filter { it !in deletedRounds }
                .sorted()

            val rawWindows: List<RoundWindow> = if (sortedRounds.isNotEmpty()) {
                sortedRounds.mapIndexed { i, r ->
                    val start = if (i > 0) (roundScoreTimes[sortedRounds[i - 1]] ?: 0f) + 1f else 0f
                    val end   = roundScoreTimes[r] ?: durationSec
                    RoundWindow(r, start, end, roundScores[r] ?: 0f, origCsvRound = r)
                }
            } else {
                deriveRoundsFromWalking(cleanWalking, durationSec).mapIndexed { i, (s, e) ->
                    RoundWindow(i + 1, s, e, 0f, origCsvRound = i + 1)
                }
            }

            val roundWindows = splitLongRounds(rawWindows, sensorSamples)

            val roundAnalytics = roundWindows.map { rw ->
                val roundSensor  = sensorSamples.filter { it.time in rw.start..rw.end }
                val roundHr      = hrSamples.filter { it.time in rw.start..rw.end }
                val roundWalking = cleanWalking
                    .filter { (ws, we) -> we >= rw.start && ws <= rw.end }
                    .map { (ws, we) -> maxOf(ws, rw.start) to minOf(we, rw.end) }
                val roundShots     = allShots.filter { it.time in rw.start..rw.end }
                val roundNearMisses = allNearMisses.filter { it.startSec >= rw.start && it.endSec <= rw.end }

                // HR: average only at detected shot times, not the whole round
                val avgHr = roundShots
                    .mapNotNull { it.hrAtShot }
                    .takeIf { it.isNotEmpty() }
                    ?.average()?.toFloat()
                    ?: 0f

                RoundAnalytics(
                    round            = rw.roundNum,
                    startSec         = rw.start,
                    endSec           = rw.end,
                    score            = rw.score,
                    detectedShots    = roundShots,
                    sensorData       = roundSensor,
                    walkingIntervals = roundWalking,
                    avgHr            = avgHr,
                    hrSamples        = roundHr,
                    origCsvRound     = rw.origCsvRound,
                    nearMisses       = roundNearMisses,
                )
            }

            return SessionAnalytics(
                durationSec      = durationSec,
                hrSamples        = hrSamples,
                walkingIntervals = cleanWalking,
                roundAnalytics   = roundAnalytics,
                allShots         = allShots,
                arrowsPerRound   = arrowsPerRound,
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun cleanWalkingIntervals(
        starts: List<Float>,
        stops: List<Float>,
        duration: Float,
    ): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()
        val remainingStops = stops.toMutableList()
        for (start in starts.sorted()) {
            val stop = remainingStops.filter { it > start }.minOrNull()
            if (stop != null) {
                remainingStops.remove(stop)
                result.add(start to minOf(stop, start + 90f))
            } else {
                result.add(start to minOf(start + 30f, duration))
            }
        }
        return result
    }

    private fun deriveWalkingFromSteps(sensor: List<SensorSample>): List<Pair<Float, Float>> {
        if (sensor.size < 2) return emptyList()
        val intervals = mutableListOf<Pair<Float, Float>>()
        var walkStart: Float? = null
        for (i in sensor.indices) {
            val t = sensor[i].time
            val lookback = sensor.filter { it.time in (t - 5f)..t }
            val stepsInWindow = if (lookback.size >= 2)
                lookback.last().steps - lookback.first().steps else 0L
            val isWalking = stepsInWindow > 0
            if (isWalking && walkStart == null) {
                walkStart = t
            } else if (!isWalking && walkStart != null) {
                intervals.add(walkStart to minOf(t, walkStart + 90f))
                walkStart = null
            }
        }
        if (walkStart != null) {
            val end = sensor.last().time
            intervals.add(walkStart to minOf(end, walkStart + 90f))
        }
        return intervals
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shot detection — gz_ddt band filter on detrended gz
    // ─────────────────────────────────────────────────────────────────────────

    fun detectShots(
        sensor: List<SensorSample>,
        walkingIntervals: List<Pair<Float, Float>> = emptyList(),
        hrSamples: List<HrPoint> = emptyList(),
        profile: DetectionProfile = DEFAULT_PROFILE,
    ): DetectionResult {
        if (sensor.size < 3) return DetectionResult(emptyList(), emptyList())

        val n = sensor.size
        val halfDetrend = DETREND_WIN_SEC / 2f

        // ── 1. Detrend gz via centred rolling mean (prefix sum, O(n)) ────────
        val gzD = FloatArray(n)
        val gzPrefix = DoubleArray(n + 1)
        for (i in 0 until n) gzPrefix[i + 1] = gzPrefix[i] + sensor[i].gz
        var lo = 0; var hi = 0
        for (i in 0 until n) {
            val t = sensor[i].time
            while (lo < n && sensor[lo].time < t - halfDetrend) lo++
            while (hi < n - 1 && sensor[hi + 1].time <= t + halfDetrend) hi++
            val winSize = (hi - lo + 1).coerceAtLeast(1)
            val mean = ((gzPrefix[hi + 1] - gzPrefix[lo]) / winSize).toFloat()
            gzD[i] = sensor[i].gz - mean
        }

        // ── 2. gz_ddt via central difference (DSTEP = 3 samples) ─────────────
        val DSTEP = 3
        val gzDdt = FloatArray(n)
        for (i in DSTEP until n - DSTEP) {
            val dt = sensor[i + DSTEP].time - sensor[i - DSTEP].time
            if (dt > 0.001f) gzDdt[i] = (gzD[i + DSTEP] - gzD[i - DSTEP]) / dt
        }
        for (i in 0 until DSTEP) gzDdt[i] = gzDdt[DSTEP]
        for (i in n - DSTEP until n) gzDdt[i] = gzDdt[n - DSTEP - 1]

        // ── 3. Boolean mask: gz_ddt in band AND gzD >= min AND optional yaw/pitch/roll bounds ──
        val mask = BooleanArray(n) { i ->
            val s = sensor[i]
            gzDdt[i] >= profile.gzDdtMin        &&
            gzDdt[i] <= profile.gzDdtMax        &&
            gzD[i]   >= profile.gzMinDetrended  &&
            (profile.yawMin   == null || s.yaw   >= profile.yawMin)   &&
            (profile.yawMax   == null || s.yaw   <= profile.yawMax)   &&
            (profile.pitchMin == null || s.pitch >= profile.pitchMin) &&
            (profile.pitchMax == null || s.pitch <= profile.pitchMax) &&
            (profile.rollMin  == null || s.roll  >= profile.rollMin)  &&
            (profile.rollMax  == null || s.roll  <= profile.rollMax)
        }

        // ── 4. Find contiguous True segments ─────────────────────────────────
        data class Seg(val s: Int, var e: Int) {
            val tStart get() = sensor[s].time
            val tEnd   get() = sensor[e].time
            val hold   get() = tEnd - tStart
        }

        val segs = mutableListOf<Seg>()
        var segStart: Int? = null
        for (i in 0 until n) {
            if (mask[i] && segStart == null) segStart = i
            else if (!mask[i] && segStart != null) {
                segs.add(Seg(segStart, i - 1)); segStart = null
            }
        }
        if (segStart != null) segs.add(Seg(segStart, n - 1))
        if (segs.isEmpty()) return DetectionResult(emptyList(), emptyList())

        // ── 5. Merge segments within MERGE_GAP_SEC ────────────────────────────
        val merged = mutableListOf(segs[0])
        for (seg in segs.drop(1)) {
            if (seg.tStart - merged.last().tEnd <= MERGE_GAP_SEC) {
                merged.last().e = seg.e
            } else {
                merged.add(seg)
            }
        }

        // ── 6. Filter by duration + cooldown, build DetectedShot list ─────────
        val shots     = mutableListOf<DetectedShot>()
        val nearMisses = mutableListOf<NearMissShot>()
        var lastExit  = -999f
        for (seg in merged) {
            if (walkingIntervals.any { (ws, we) -> seg.tStart < we && seg.tEnd > ws }) continue

            if (seg.hold < profile.holdMinSec || seg.hold > HOLD_MAX_SEC) {
                // Near-miss detection disabled for now (hold duration not the main issue)
                // if (seg.hold >= 1.0f && seg.hold < profile.holdMinSec) { ... }
                continue
            }
            if (seg.tStart - lastExit < COOLDOWN_SEC) continue

            val gzVals  = (seg.s..seg.e).map { sensor[it].gz }
            val gzMean  = gzVals.average().toFloat()
            val gzStdev = if (gzVals.size > 1) {
                val mean = gzMean.toDouble()
                sqrt(gzVals.map { (it - mean) * (it - mean) }.average()).toFloat()
            } else 0f

            val midTime  = seg.tStart + seg.hold / 2f
            val hrAtShot = interpolateHr(hrSamples, midTime)
            val window   = sensor.subList(seg.s, seg.e + 1)

            shots.add(DetectedShot(
                time         = midTime,
                startSec     = seg.tStart,
                endSec       = seg.tEnd,
                holdSec      = seg.hold,
                nSamples     = seg.e - seg.s + 1,
                gzMean       = gzMean,
                gzStdev      = gzStdev,
                hrAtShot     = hrAtShot,
                sensorWindow = window,
                gzDWindow    = gzD.slice(seg.s..seg.e),
                gzDdtWindow  = gzDdt.slice(seg.s..seg.e),
            ))
            lastExit = seg.tEnd
        }
        return DetectionResult(shots, nearMisses)
    }

    private fun interpolateHr(hrSamples: List<HrPoint>, t: Float): Float? {
        if (hrSamples.isEmpty()) return null
        val before = hrSamples.filter { it.time <= t }.maxByOrNull { it.time }
        val after  = hrSamples.filter { it.time >  t }.minByOrNull { it.time }
        return when {
            before != null && after != null -> {
                val frac = (t - before.time) / (after.time - before.time)
                before.bpm + frac * (after.bpm - before.bpm)
            }
            before != null -> before.bpm
            after  != null -> after.bpm
            else           -> null
        }
    }

    private fun mergeWalkingTransitions(
        walking: List<Pair<Float, Float>>,
        gapThreshold: Float = 60f,
    ): List<Pair<Float, Float>> {
        if (walking.isEmpty()) return emptyList()
        val sorted = walking.sortedBy { it.first }
        val merged = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            val (ws, we) = sorted[i]
            val (prevS, prevE) = merged.last()
            if (ws - prevE <= gapThreshold) {
                merged[merged.lastIndex] = prevS to maxOf(prevE, we)
            } else {
                merged.add(ws to we)
            }
        }
        return merged
    }

    private fun deriveRoundsFromWalking(
        walking: List<Pair<Float, Float>>,
        duration: Float,
        minShootingSec: Float = 30f,
    ): List<Pair<Float, Float>> {
        val transitions = mergeWalkingTransitions(walking)
        val rounds = mutableListOf<Pair<Float, Float>>()
        if (transitions.isEmpty()) return listOf(0f to duration)
        val firstStart = transitions[0].first
        if (firstStart > minShootingSec) rounds.add(0f to firstStart)
        for (i in 0 until transitions.size - 1) {
            val gapStart = transitions[i].second
            val gapEnd   = transitions[i + 1].first
            if (gapEnd - gapStart >= minShootingSec) rounds.add(gapStart to gapEnd)
        }
        val lastEnd = transitions.last().second
        if (duration - lastEnd >= minShootingSec) rounds.add(lastEnd to duration)
        return rounds
    }

    private fun splitLongRounds(
        windows: List<RoundWindow>,
        sensor: List<SensorSample>,
    ): List<RoundWindow> {
        if (windows.size < 3) return windows
        val durations = windows.map { it.end - it.start }
        val sorted    = durations.sorted()
        val median    = sorted[sorted.size / 2]
        val threshold = median * 1.5f
        val result    = mutableListOf<RoundWindow>()
        for (w in windows) {
            val dur = w.end - w.start
            if (dur <= threshold) { result.add(w); continue }
            val splitPoint = findShootingGap(sensor, w.start, w.end)
            if (splitPoint != null) {
                result.add(RoundWindow(0, w.start, splitPoint, 0f, w.origCsvRound))
                result.add(RoundWindow(0, splitPoint, w.end, w.score, w.origCsvRound))
            } else {
                result.add(w)
            }
        }
        return result.mapIndexed { i, rw -> rw.copy(roundNum = i + 1) }
    }

    fun findShootingGap(sensor: List<SensorSample>, start: Float, end: Float): Float? {
        val roundSensor = sensor.filter { it.time in start..end }
        if (roundSensor.size < 10) return null

        data class Gap(val start: Float, val end: Float) {
            val duration get() = end - start
            val midpoint get() = start + duration / 2
        }

        val gaps      = mutableListOf<Gap>()
        var gapStart: Float? = null
        for (s in roundSensor) {
            if (s.gz < SPLIT_GZ_LOW) {
                if (gapStart == null) gapStart = s.time
            } else {
                if (gapStart != null) {
                    if (s.time - gapStart >= 30f) gaps.add(Gap(gapStart, s.time))
                    gapStart = null
                }
            }
        }
        if (gapStart != null) {
            val lastTime = roundSensor.last().time
            if (lastTime - gapStart >= 30f) gaps.add(Gap(gapStart, lastTime))
        }
        if (gaps.isEmpty()) return null
        return gaps
            .filter { it.midpoint > start + 60f && it.midpoint < end - 60f }
            .maxByOrNull { it.duration }
            ?.midpoint
    }
}
