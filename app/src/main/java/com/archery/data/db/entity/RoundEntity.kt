package com.archery.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "roundNumber"], unique = true)],
)
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val roundNumber: Int,
    val detectedScore: Float,
    val confirmedScore: Float? = null,
    val avgHeartRate: Float? = null,
    val avgHoldMs: Long? = null,
)
