package com.archery.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "arrows",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["roundId", "shotNumber"], unique = true)],
)
data class ArrowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val sessionId: Long,
    val shotNumber: Int,
    /** ScoreZone.name string. */
    val zone: String,
    val score: Float,
    /** True = final_score or edit_score; false = quick_score only. */
    val isFinal: Boolean,
    val holdMs: Long? = null,
    val heartRate: Float? = null,
)
