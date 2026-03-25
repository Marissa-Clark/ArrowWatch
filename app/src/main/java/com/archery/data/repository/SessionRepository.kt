package com.archery.data.repository

import com.archery.data.db.ArcheryDatabase
import com.archery.data.db.RoundWithArrows
import com.archery.data.db.SessionWithRounds
import com.archery.data.db.entity.ArrowEntity
import com.archery.data.db.entity.RoundEntity
import com.archery.data.db.entity.SessionEntity
import com.archery.parser.SessionParser
import com.archery.shared.ArrowScore
import com.archery.shared.RoundSummary
import com.archery.shared.ScoreZone
import com.archery.shared.SessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SessionRepository(db: ArcheryDatabase) {

    private val dao = db.sessionDao()

    // ── Read ─────────────────────────────────────────────────────────────────

    fun getAllSessions(): Flow<List<SessionSummary>> =
        dao.getAllSessions().map { list -> list.map { it.toSummary() } }

    fun getSession(sessionId: Long): Flow<SessionSummary?> =
        dao.getSession(sessionId).map { it?.toSummary() }

    // ── Import ───────────────────────────────────────────────────────────────

    /**
     * Parse a CSV file and store it in the DB.
     *
     * Behaviour:
     * - Already imported correctly (has rounds) → no-op.
     * - Soft-deleted by the user → no-op (suppresses re-import as intended).
     * - Corrupt import (0 rounds, not deleted) → hard-delete the stale record and re-import.
     * - Never seen before → import fresh.
     *
     * Returns the new session ID, or null on no-op / failure.
     */
    suspend fun importCsv(csvFile: File, dateMs: Long): Long? {
        val existing = dao.getSessionByFileName(csvFile.name)
        if (existing != null) {
            when {
                // User intentionally deleted this session — leave it gone.
                existing.session.isDeleted -> return null
                // Already imported and has round data — nothing to do.
                existing.rounds.isNotEmpty() -> return null
                // Corrupt: session record exists but 0 rounds were stored.
                // Hard-delete so we can re-import correctly below.
                else -> {
                    dao.hardDeleteArrows(existing.session.id)
                    dao.hardDeleteRounds(existing.session.id)
                    dao.hardDeleteSession(existing.session.id)
                }
            }
        }
        val summary = SessionParser.parse(csvFile, dateMs) ?: return null
        return storeSummary(summary, csvFile.absolutePath, dateMs)
    }

    private suspend fun storeSummary(summary: SessionSummary, filePath: String, dateMs: Long): Long {
        val sessionId = dao.insertSession(
            SessionEntity(
                fileName = summary.fileName,
                filePath = filePath,
                dateMs = dateMs,
                durationSec = summary.durationSec,
            )
        )
        val roundIds = dao.insertRounds(
            summary.rounds.map { r ->
                RoundEntity(
                    sessionId = sessionId,
                    roundNumber = r.number,
                    detectedScore = r.detectedScore,
                    confirmedScore = r.confirmedScore,
                    avgHeartRate = r.avgHeartRate,
                    avgHoldMs = r.avgHoldMs,
                )
            }
        )
        val arrows = summary.rounds.flatMapIndexed { idx, round ->
            val roundId = roundIds[idx]
            round.arrows.map { a ->
                ArrowEntity(
                    roundId = roundId,
                    sessionId = sessionId,
                    shotNumber = a.shotNumber,
                    zone = a.zone.name,
                    score = a.score,
                    isFinal = a.isFinal,
                )
            }
        }
        dao.insertArrows(arrows)
        return sessionId
    }

    // ── Edit ─────────────────────────────────────────────────────────────────

    suspend fun setArchived(sessionId: Long, archived: Boolean) =
        dao.setArchived(sessionId, archived)

    suspend fun renameSession(sessionId: Long, name: String?) =
        dao.renameSession(sessionId, name)

    suspend fun deleteSession(sessionId: Long) =
        dao.deleteSession(sessionId)

    suspend fun updateArrowScore(sessionId: Long, roundNumber: Int, shotNumber: Int, zone: ScoreZone, score: Float) {
        val updated = dao.updateArrowScore(sessionId, roundNumber, shotNumber, zone.name, score)
        if (updated == 0) {
            // Arrow doesn't exist yet — insert it (new arrow added to existing round)
            val roundId = dao.getRoundId(sessionId, roundNumber) ?: return
            dao.insertArrow(
                ArrowEntity(
                    roundId = roundId,
                    sessionId = sessionId,
                    shotNumber = shotNumber,
                    zone = zone.name,
                    score = score,
                    isFinal = true,
                )
            )
        }
    }

    suspend fun updateRoundTotal(sessionId: Long, roundNumber: Int, score: Float) =
        dao.updateRoundScore(sessionId, roundNumber, score)

    suspend fun deleteRound(sessionId: Long, roundNumber: Int) =
        dao.deleteRound(sessionId, roundNumber)

    suspend fun lockAnalytics(sessionId: Long, locked: Boolean) =
        dao.setAnalyticsLocked(sessionId, locked)

    suspend fun updateRoundHoldMs(sessionId: Long, roundNumber: Int, holdMs: Long) =
        dao.updateRoundHoldMs(sessionId, roundNumber, holdMs)

    suspend fun insertRoundAfter(sessionId: Long, afterRound: Int) {
        dao.shiftRoundNumbersPhase1(sessionId, afterRound)
        dao.shiftRoundNumbersPhase2(sessionId)
        dao.insertRound(
            RoundEntity(
                sessionId = sessionId,
                roundNumber = afterRound + 1,
                detectedScore = 0f,
            )
        )
    }

    /**
     * Creates a manual (retrospective) session with [roundCount] empty rounds.
     * [roundScores] may be shorter than [roundCount]; missing slots default to 0.
     * Returns the new session ID.
     */
    suspend fun createManualSession(
        dateMs: Long,
        displayName: String?,
        roundCount: Int,
        arrowsPerRound: Int,
        roundScores: List<Float>,
    ): Long {
        val sessionId = dao.insertSession(
            SessionEntity(
                fileName    = "manual_$dateMs",
                filePath    = "",
                dateMs      = dateMs,
                durationSec = 0L,
                displayName = displayName?.takeIf { it.isNotBlank() },
            )
        )
        repeat(roundCount) { idx ->
            val roundNum = idx + 1
            val score = roundScores.getOrNull(idx)?.takeIf { it > 0f }
            dao.insertRound(
                RoundEntity(
                    sessionId      = sessionId,
                    roundNumber    = roundNum,
                    detectedScore  = score ?: 0f,
                    confirmedScore = score,
                )
            )
        }
        return sessionId
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun SessionWithRounds.toSummary(): SessionSummary {
        val rounds = rounds
            .sortedBy { it.round.roundNumber }
            .map { it.toRoundSummary() }
        val allArrows = rounds.flatMap { it.arrows }
        val zoneCounts = allArrows.groupingBy { it.zone }.eachCount()
        val allHr = rounds.mapNotNull { it.avgHeartRate }
        val allHold = rounds.mapNotNull { it.avgHoldMs }
        return SessionSummary(
            id = session.id,
            fileName = session.fileName,
            filePath = session.filePath,
            date = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(session.dateMs), ZoneId.systemDefault()
            ),
            durationSec = session.durationSec,
            rounds = rounds,
            zoneCounts = zoneCounts,
            avgHeartRate = allHr.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            avgHoldMs = allHold.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            isArchived = session.isArchived,
            displayName = session.displayName,
            analyticsLocked = session.analyticsLocked,
        )
    }

    private fun RoundWithArrows.toRoundSummary(): RoundSummary {
        val arrows = arrows.sortedBy { it.shotNumber }.map { it.toArrowScore() }
        return RoundSummary(
            number = round.roundNumber,
            arrows = arrows,
            detectedScore = round.detectedScore,
            confirmedScore = round.confirmedScore,
            avgHeartRate = round.avgHeartRate,
            avgHoldMs = round.avgHoldMs,
        )
    }

    private fun ArrowEntity.toArrowScore() = ArrowScore(
        shotNumber = shotNumber,
        zone = ScoreZone.valueOf(zone),
        score = score,
        isFinal = isFinal,
    )
}
