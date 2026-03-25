package com.archery.analytics

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetectionProfile(
    val name: String = "",
    val gzDdtMin: Float = -1f,
    val gzDdtMax: Float = 1f,
    val holdMinSec: Float = 4f,
    val gzMinDetrended: Float = 1f,
    // Optional yaw / pitch / roll bounds — null means "no filter"
    val yawMin: Float? = null,
    val yawMax: Float? = null,
    val pitchMin: Float? = null,
    val pitchMax: Float? = null,
    val rollMin: Float? = null,
    val rollMax: Float? = null,
)

val DEFAULT_PROFILE = DetectionProfile(
    gzDdtMin       = -1f,
    gzDdtMax       = 1f,
    holdMinSec     = 4f,
    gzMinDetrended = 1f,
)

/**
 * Suggested tighter thresholds derived from dismissed vs. kept shots.
 * [holdConflicts] / [gzConflicts] count kept shots that would also be excluded —
 * helps the user judge the trade-off.
 */
data class ProfileSuggestion(
    val suggested: DetectionProfile,
    val dismissedCount: Int,
    val holdChanged: Boolean,
    val gzChanged: Boolean,
    val holdConflicts: Int,
    val gzConflicts: Int,
)

/** Single global detection threshold set, persisted across app restarts. */
object DetectionSettings {

    private var prefs: SharedPreferences? = null

    private val _active = MutableStateFlow(DEFAULT_PROFILE)
    val active: StateFlow<DetectionProfile> = _active.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences("detection_settings", Context.MODE_PRIVATE)
        _active.value = load()
    }

    private fun getFloatOrNull(key: String): Float? =
        if (prefs!!.contains(key)) prefs!!.getFloat(key, 0f) else null

    private fun load(): DetectionProfile {
        val p = prefs ?: return DEFAULT_PROFILE
        return DetectionProfile(
            gzDdtMin       = p.getFloat("gzDdtMin",       DEFAULT_PROFILE.gzDdtMin),
            gzDdtMax       = p.getFloat("gzDdtMax",       DEFAULT_PROFILE.gzDdtMax),
            holdMinSec     = p.getFloat("holdMinSec",     DEFAULT_PROFILE.holdMinSec),
            gzMinDetrended = p.getFloat("gzMinDetrended", DEFAULT_PROFILE.gzMinDetrended),
            yawMin         = getFloatOrNull("yawMin"),
            yawMax         = getFloatOrNull("yawMax"),
            pitchMin       = getFloatOrNull("pitchMin"),
            pitchMax       = getFloatOrNull("pitchMax"),
            rollMin        = getFloatOrNull("rollMin"),
            rollMax        = getFloatOrNull("rollMax"),
        )
    }

    private fun SharedPreferences.Editor.putFloatOrNull(key: String, value: Float?): SharedPreferences.Editor {
        if (value == null) remove(key) else putFloat(key, value)
        return this
    }

    fun update(profile: DetectionProfile) {
        _active.value = profile
        prefs?.edit()
            ?.putFloat("gzDdtMin",       profile.gzDdtMin)
            ?.putFloat("gzDdtMax",       profile.gzDdtMax)
            ?.putFloat("holdMinSec",     profile.holdMinSec)
            ?.putFloat("gzMinDetrended", profile.gzMinDetrended)
            ?.putFloatOrNull("yawMin",   profile.yawMin)
            ?.putFloatOrNull("yawMax",   profile.yawMax)
            ?.putFloatOrNull("pitchMin", profile.pitchMin)
            ?.putFloatOrNull("pitchMax", profile.pitchMax)
            ?.putFloatOrNull("rollMin",  profile.rollMin)
            ?.putFloatOrNull("rollMax",  profile.rollMax)
            ?.apply()
    }

    fun reset() = update(DEFAULT_PROFILE)
}
