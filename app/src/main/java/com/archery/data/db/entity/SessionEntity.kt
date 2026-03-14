package com.archery.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["fileName"], unique = true)],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    /** Epoch millis of session start. */
    val dateMs: Long,
    val durationSec: Long,
    val isArchived: Boolean = false,
    /** User-supplied display name; null means use default "Practice Session". */
    val displayName: String? = null,
    /** Soft-delete flag: true means deleted but kept in DB so CSV re-import is suppressed. */
    val isDeleted: Boolean = false,
)
