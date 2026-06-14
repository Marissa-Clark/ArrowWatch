package com.archery.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.archery.data.db.entity.ArrowEntity
import com.archery.data.db.entity.RoundEntity
import com.archery.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

// ── Relation helpers ─────────────────────────────────────────────────────────

data class RoundWithArrows(
    @Embedded val round: RoundEntity,
    @Relation(parentColumn = "id", entityColumn = "roundId")
    val arrows: List<ArrowEntity>,
)

data class SessionWithRounds(
    @Embedded val session: SessionEntity,
    @Relation(
        entity = RoundEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val rounds: List<RoundWithArrows>,
)

// ── DAO ──────────────────────────────────────────────────────────────────────

@Dao
interface SessionDao {

    @Transaction
    @Query("SELECT * FROM sessions WHERE isDeleted = 0 ORDER BY dateMs DESC")
    fun getAllSessions(): Flow<List<SessionWithRounds>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId AND isDeleted = 0")
    fun getSession(sessionId: Long): Flow<SessionWithRounds?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE fileName = :fileName LIMIT 1")
    suspend fun getSessionByFileName(fileName: String): SessionWithRounds?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRounds(rounds: List<RoundEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArrows(arrows: List<ArrowEntity>): List<Long>

    @Query("UPDATE sessions SET isDeleted = 1 WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long): Int

    @Query("UPDATE sessions SET isArchived = :archived WHERE id = :sessionId")
    suspend fun setArchived(sessionId: Long, archived: Boolean): Int

    @Query("UPDATE sessions SET displayName = :name WHERE id = :sessionId")
    suspend fun renameSession(sessionId: Long, name: String?): Int

    @Query("UPDATE sessions SET dateMs = :dateMs WHERE id = :sessionId")
    suspend fun updateSessionDate(sessionId: Long, dateMs: Long): Int

    @Query("UPDATE rounds SET confirmedScore = :score WHERE sessionId = :sessionId AND roundNumber = :roundNumber")
    suspend fun updateRoundScore(sessionId: Long, roundNumber: Int, score: Float): Int

    @Query("DELETE FROM rounds WHERE sessionId = :sessionId AND roundNumber = :roundNumber")
    suspend fun deleteRound(sessionId: Long, roundNumber: Int): Int

    @Query("""
        UPDATE arrows SET zone = :zone, score = :score, isFinal = 1
        WHERE roundId = (
            SELECT id FROM rounds WHERE sessionId = :sessionId AND roundNumber = :roundNumber
        ) AND shotNumber = :shotNumber
    """)
    suspend fun updateArrowScore(sessionId: Long, roundNumber: Int, shotNumber: Int, zone: String, score: Float): Int

    @Query("UPDATE rounds SET avgHoldMs = :holdMs WHERE sessionId = :sessionId AND roundNumber = :roundNumber")
    suspend fun updateRoundHoldMs(sessionId: Long, roundNumber: Int, holdMs: Long): Int

    @Query("UPDATE sessions SET analyticsLocked = :locked WHERE id = :sessionId")
    suspend fun setAnalyticsLocked(sessionId: Long, locked: Boolean): Int

    @Query("UPDATE sessions SET distanceM = :distanceM, targetSizeCm = :targetSizeCm WHERE id = :sessionId")
    suspend fun updateDistanceTarget(sessionId: Long, distanceM: Int?, targetSizeCm: Int?): Int

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE fileName = :fileName)")
    suspend fun sessionExists(fileName: String): Boolean

    // Hard-delete queries used to wipe corrupt sessions (0 rounds) so they can be re-imported.
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun hardDeleteSession(sessionId: Long)

    @Query("DELETE FROM rounds WHERE sessionId = :sessionId")
    suspend fun hardDeleteRounds(sessionId: Long)

    @Query("DELETE FROM arrows WHERE sessionId = :sessionId")
    suspend fun hardDeleteArrows(sessionId: Long)

    // ── Round insertion helpers ───────────────────────────────────────────────

    // Two-step shift to avoid SQLite unique-constraint violations.
    // Step 1: move affected rounds far out of range (+9000).
    @Query("UPDATE rounds SET roundNumber = roundNumber + 9000 WHERE sessionId = :sessionId AND roundNumber > :afterRound")
    suspend fun shiftRoundNumbersPhase1(sessionId: Long, afterRound: Int)

    // Step 2: move them back to (original + 1).
    @Query("UPDATE rounds SET roundNumber = roundNumber - 8999 WHERE sessionId = :sessionId AND roundNumber > 9000")
    suspend fun shiftRoundNumbersPhase2(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRound(round: RoundEntity): Long

    @Query("SELECT id FROM rounds WHERE sessionId = :sessionId AND roundNumber = :roundNumber LIMIT 1")
    suspend fun getRoundId(sessionId: Long, roundNumber: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArrow(arrow: ArrowEntity): Long
}
