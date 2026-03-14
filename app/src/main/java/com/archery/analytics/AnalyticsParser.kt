package com.archery.analytics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

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
    val time: Float,      // median of qualifying cluster
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
    try {
        val file = File(dismissedFilePath(sessionCsvPath))
        if (!file.exists()) return result
        val json = JSONArray(file.readText())
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val round = obj.getInt("round")
            val shotIndex = obj.getInt("shotIndex")
            result.getOrPut(round) { mutableSetOf() }.add(shotIndex)
        }
    } catch (_: Exception) {}
    return result
}

fun saveDismissedShots(sessionCsvPath: String, dismissed: Map<Int, Set<Int>>) {
    try {
        val arr = JSONArray()
        dismissed.forEach { (round, indices) ->
            indices.forEach { idx ->
                arr.put(JSONObject().apply {
                    put("round", round)
                    put("shotIndex", idx)
                })
            }
        }
        File(dismissedFilePath(sessionCsvPath)).writeText(arr.toString(2))
    } catch (_: Exception) {}
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

    // Shoot mask: arm raised and holding at full draw
    private const val SHOOT_GZ_MIN  = 5.0f
    private const val SHOOT_GZ_MAX  = 7.5f
    private const val SHOOT_YAW_MIN = -4.0f
    private const val SHOOT_YAW_MAX = 3.5f
    // Prep mask: arm in ready position before raising
    private const val PREP_GZ_MIN    = -6.0f
    private const val PREP_GZ_MAX    =  2.0f
    private const val PREP_YAW_MIN   = -5.0f
    private const val PREP_YAW_MAX   =  3.0f
    private const val PREP_PITCH_MIN =  0.5f
    private const val PREP_PITCH_MAX =  2.0f
    private const val PREP_ROLL_MIN  =  1.0f
    private const val PREP_ROLL_MAX  =  3.0f
    // Clustering & filtering
    private const val CLUSTER_GAP_SEC    = 3.0f
    private const val HOLD_MIN_SEC       = 3.0f
    private const val HOLD_MAX_SEC       = 12.0f
    private const val PREP_LOOKBACK_SEC  = 10.0f
    private const val GZ_LOW_LOOKBACK_SEC = 5.0f
    private const val GZ_LOW_THRESH      = 3.0f

    fun parse(filePath: String): SessionAnalytics? {
        val file = File(filePath)
        if (!file.exists()) return null
        return parse(file)
    }

    fun parse(file: File): SessionAnalytics? {
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

            val allShots = detectShots(sensorSamples, cleanWalking, hrSamples)

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
                val roundShots   = allShots.filter { it.time in rw.start..rw.end }

                RoundAnalytics(
                    round            = rw.roundNum,
                    startSec         = rw.start,
                    endSec           = rw.end,
                    score            = rw.score,
                    detectedShots    = roundShots,
                    sensorData       = roundSensor,
                    walkingIntervals = roundWalking,
                    avgHr            = if (roundHr.isNotEmpty())
                        roundHr.map { it.bpm }.average().toFloat() else 0f,
                    hrSamples        = roundHr,
                    origCsvRound     = rw.origCsvRound,
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

    fun detectShots(
        sensor: List<SensorSample>,
        walkingIntervals: List<Pair<Float, Float>>,
        hrSamples: List<HrPoint> = emptyList(),
    ): List<DetectedShot> {
        if (sensor.size < 3) return emptyList()

        data class MaskedSample(val time: Float, val gz: Float, val isShoot: Boolean, val isPrep: Boolean)

        val masked = sensor.map { s ->
            val isWalking = walkingIntervals.any { (ws, we) -> s.time in ws..we }
            val isShoot = !isWalking &&
                s.gz in SHOOT_GZ_MIN..SHOOT_GZ_MAX &&
                s.yaw in SHOOT_YAW_MIN..SHOOT_YAW_MAX
            val isPrep = !isWalking &&
                s.gz in PREP_GZ_MIN..PREP_GZ_MAX &&
                s.yaw in PREP_YAW_MIN..PREP_YAW_MAX &&
                s.pitch in PREP_PITCH_MIN..PREP_PITCH_MAX &&
                s.roll in PREP_ROLL_MIN..PREP_ROLL_MAX
            MaskedSample(s.time, s.gz, isShoot, isPrep)
        }

        val shootSamples = masked.filter { it.isShoot }
        if (shootSamples.isEmpty()) return emptyList()

        data class Cluster(val samples: MutableList<MaskedSample> = mutableListOf()) {
            val startSec get() = samples.first().time
            val endSec   get() = samples.last().time
            val holdSec  get() = endSec - startSec
            val midTime  get() = startSec + holdSec / 2
            val gzMean   get() = samples.map { it.gz }.average().toFloat()
        }

        val clusters = mutableListOf<Cluster>()
        var current = Cluster()
        current.samples.add(shootSamples[0])
        for (i in 1 until shootSamples.size) {
            if (shootSamples[i].time - shootSamples[i - 1].time > CLUSTER_GAP_SEC) {
                clusters.add(current)
                current = Cluster()
            }
            current.samples.add(shootSamples[i])
        }
        clusters.add(current)

        val durationFiltered = clusters.filter { it.holdSec in HOLD_MIN_SEC..HOLD_MAX_SEC }

        return durationFiltered.mapNotNull { cluster ->
            val hasPrep = masked.any { m ->
                m.isPrep && m.time in (cluster.startSec - PREP_LOOKBACK_SEC)..cluster.startSec
            }
            val hasGzLow = sensor.any { s ->
                s.time in (cluster.startSec - GZ_LOW_LOOKBACK_SEC)..cluster.startSec &&
                    s.gz < GZ_LOW_THRESH
            }
            if (hasPrep || hasGzLow) {
                val gzVals = cluster.samples.map { it.gz }
                val gzStdev = if (gzVals.size > 1) {
                    val mean = cluster.gzMean.toDouble()
                    sqrt(gzVals.map { (it - mean) * (it - mean) }.average()).toFloat()
                } else 0f

                val hrAtShot: Float? = if (hrSamples.isEmpty()) null else {
                    val mid = cluster.midTime
                    val before = hrSamples.filter { it.time <= mid }.maxByOrNull { it.time }
                    val after  = hrSamples.filter { it.time > mid }.minByOrNull { it.time }
                    when {
                        before != null && after != null -> {
                            val t = (mid - before.time) / (after.time - before.time)
                            before.bpm + t * (after.bpm - before.bpm)
                        }
                        before != null -> before.bpm
                        after  != null -> after.bpm
                        else           -> null
                    }
                }

                val window = sensor.filter { it.time in cluster.startSec..cluster.endSec }

                DetectedShot(
                    time         = cluster.midTime,
                    startSec     = cluster.startSec,
                    endSec       = cluster.endSec,
                    holdSec      = cluster.holdSec,
                    nSamples     = cluster.samples.size,
                    gzMean       = cluster.gzMean,
                    gzStdev      = gzStdev,
                    hrAtShot     = hrAtShot,
                    sensorWindow = window,
                )
            } else null
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
            if (s.gz < SHOOT_GZ_MIN) {
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
