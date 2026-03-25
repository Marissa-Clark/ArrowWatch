package com.archery.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.analytics.AnalyticsCache
import com.archery.analytics.AnalyticsParser
import com.archery.analytics.DEFAULT_PROFILE
import com.archery.analytics.DetectionProfile
import com.archery.analytics.DetectionSettings
import com.archery.analytics.SensorSample
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ═══════════════ COLORS ═══════════════

private val BgPage        = Color(0xFFF8FAFC)
private val BgWhite       = Color.White
private val HeaderDarker  = Color(0xFF0F172A)
private val Cyan600       = Color(0xFF0891B2)
private val TextPrimary   = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF475569)
private val TextMuted     = Color(0xFF94A3B8)
private val BorderLight   = Color(0xFFE2E8F0)
private val Red400        = Color(0xFFF87171)
private val Amber600      = Color(0xFFD97706)
private val Amber100      = Color(0xFFFEF3C7)
private val Amber800      = Color(0xFF92400E)
private val Slate100      = Color(0xFFF1F5F9)
private val Violet400     = Color(0xFFA78BFA)
private val Violet600     = Color(0xFF7C3AED)

// ═══════════════ DATA ═══════════════

/** A single shot's sensor traces, aligned to shot start = t 0. */
data class ShotWindow(
    val gzD: List<Float>,
    val yaw: List<Float>,
    val pitch: List<Float>,
    val roll: List<Float>,
    val times: List<Float>,   // seconds from shot start
)

data class DetectionStats(
    val sessionsScanned: Int,
    val totalShots: Int,
    val groundTruthArrows: Int,   // non-DNS scored arrows across scanned sessions (0 if no scoring data)
    val holdP10: Float,
    val holdP50: Float,
    val holdP90: Float,
    val gzP10: Float,
    val gzP50: Float,
    val gzP90: Float,
    val shotWindows: List<ShotWindow>,   // up to 60 shots for the profile chart
)

sealed class StatsState {
    object Idle    : StatsState()
    object Loading : StatsState()
    data class Ready(val stats: DetectionStats) : StatsState()
    object Empty   : StatsState()
    object NoData  : StatsState()
}

// ═══════════════ VIEWMODEL ═══════════════

class DetectionSettingsViewModel(application: Application) : AndroidViewModel(application) {

    init { DetectionSettings.init(application) }

    private val _draft = MutableStateFlow(DetectionSettings.active.value)
    val draft: StateFlow<DetectionProfile> = _draft.asStateFlow()

    fun updateDraft(p: DetectionProfile) { _draft.value = p }
    fun resetDraft() { _draft.value = DEFAULT_PROFILE }

    fun apply() {
        DetectionSettings.update(_draft.value)
        AnalyticsCache.clear()
        loadStats()
    }

    private val _statsState = MutableStateFlow<StatsState>(StatsState.Idle)
    val statsState: StateFlow<StatsState> = _statsState.asStateFlow()

    fun loadStats() {
        viewModelScope.launch {
            _statsState.value = StatsState.Loading
            val result = withContext(Dispatchers.IO) { computeStats() }
            _statsState.value = result
        }
    }

    private fun computeStats(): StatsState {
        val app = getApplication<Application>()
        val archeryDir = File(app.getExternalFilesDir(null), "Archery")
        val csvFiles = archeryDir.listFiles { f -> f.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(10)
            ?: return StatsState.NoData
        if (csvFiles.isEmpty()) return StatsState.NoData

        val profile          = DetectionSettings.active.value
        val holds            = mutableListOf<Float>()
        val gzMeans          = mutableListOf<Float>()
        val windows          = mutableListOf<ShotWindow>()
        var sessions         = 0
        var groundTruthTotal = 0

        for (file in csvFiles) {
            try {
                val samples = parseSensorRows(file)
                if (samples.size < 3) continue
                val shots = AnalyticsParser.detectShots(samples, profile = profile).shots
                sessions++
                groundTruthTotal += countNonDnsArrows(file)
                for (shot in shots) {
                    holds.add(shot.holdSec)
                    gzMeans.add(shot.gzMean)
                    if (windows.size < 60 && shot.gzDWindow.isNotEmpty() && shot.sensorWindow.isNotEmpty()) {
                        val t0 = shot.sensorWindow.first().time
                        windows.add(ShotWindow(
                            gzD   = shot.gzDWindow,
                            yaw   = shot.sensorWindow.map { it.yaw },
                            pitch = shot.sensorWindow.map { it.pitch },
                            roll  = shot.sensorWindow.map { it.roll },
                            times = shot.sensorWindow.map { it.time - t0 },
                        ))
                    }
                }
            } catch (_: Exception) {}
        }

        if (holds.isEmpty()) return StatsState.Empty

        fun pct(sorted: List<Float>, p: Float): Float {
            val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
        val hs = holds.sorted()
        val gs = gzMeans.sorted()

        return StatsState.Ready(DetectionStats(
            sessionsScanned    = sessions,
            totalShots         = holds.size,
            groundTruthArrows  = groundTruthTotal,
            holdP10            = pct(hs, 0.1f),
            holdP50            = pct(hs, 0.5f),
            holdP90            = pct(hs, 0.9f),
            gzP10              = pct(gs, 0.1f),
            gzP50              = pct(gs, 0.5f),
            gzP90              = pct(gs, 0.9f),
            shotWindows        = windows,
        ))
    }

    /** Count non-DNS arrows scored in a session CSV (final_score + quick_score events, zone ≠ DNS). */
    private fun countNonDnsArrows(file: File): Int {
        var count = 0
        file.bufferedReader().useLines { lines ->
            for (line in lines.drop(1)) {
                val c = line.split(",")
                val event = c.getOrNull(1)?.trim() ?: continue
                if (event != "final_score" && event != "quick_score") continue
                val zone = c.getOrNull(7)?.trim() ?: continue
                if (zone.equals("DNS", ignoreCase = true)) continue
                count++
            }
        }
        return count
    }

    private fun parseSensorRows(file: File): List<SensorSample> {
        val out = mutableListOf<SensorSample>()
        file.bufferedReader().useLines { lines ->
            for (line in lines.drop(1)) {
                val c = line.split(",")
                if (c.size < 6 || c[1].trim() != "sensor") continue
                val t  = c[0].toFloatOrNull() ?: continue
                val gz = c[5].toFloatOrNull() ?: continue
                val yaw   = c.getOrNull(10)?.toFloatOrNull() ?: 0f
                val pitch = c.getOrNull(11)?.toFloatOrNull() ?: 0f
                val roll  = c.getOrNull(12)?.toFloatOrNull() ?: 0f
                out.add(SensorSample(t, gz, yaw, pitch, roll))
            }
        }
        return out
    }
}

// ═══════════════ SCREEN ═══════════════

@Composable
fun ManageProfilesScreen(
    onBack: () -> Unit,
    vm: DetectionSettingsViewModel = viewModel(),
) {
    val draft      by vm.draft.collectAsState()
    val statsState by vm.statsState.collectAsState()

    var ddtMin   by remember(draft) { mutableStateOf(draft.gzDdtMin.toString()) }
    var ddtMax   by remember(draft) { mutableStateOf(draft.gzDdtMax.toString()) }
    var holdMin  by remember(draft) { mutableStateOf(draft.holdMinSec.toString()) }
    var gzMin    by remember(draft) { mutableStateOf(draft.gzMinDetrended.toString()) }
    var yawMin   by remember(draft) { mutableStateOf(draft.yawMin?.toString()   ?: "") }
    var yawMax   by remember(draft) { mutableStateOf(draft.yawMax?.toString()   ?: "") }
    var pitchMin by remember(draft) { mutableStateOf(draft.pitchMin?.toString() ?: "") }
    var pitchMax by remember(draft) { mutableStateOf(draft.pitchMax?.toString() ?: "") }
    var rollMin  by remember(draft) { mutableStateOf(draft.rollMin?.toString()  ?: "") }
    var rollMax  by remember(draft) { mutableStateOf(draft.rollMax?.toString()  ?: "") }

    fun currentDraft() = DetectionProfile(
        gzDdtMin       = ddtMin.toFloatOrNull()   ?: draft.gzDdtMin,
        gzDdtMax       = ddtMax.toFloatOrNull()   ?: draft.gzDdtMax,
        holdMinSec     = holdMin.toFloatOrNull()  ?: draft.holdMinSec,
        gzMinDetrended = gzMin.toFloatOrNull()    ?: draft.gzMinDetrended,
        yawMin         = yawMin.toFloatOrNull(),
        yawMax         = yawMax.toFloatOrNull(),
        pitchMin       = pitchMin.toFloatOrNull(),
        pitchMax       = pitchMax.toFloatOrNull(),
        rollMin        = rollMin.toFloatOrNull(),
        rollMax        = rollMax.toFloatOrNull(),
    )

    Column(Modifier.fillMaxSize().background(BgPage)) {
        Box(
            Modifier.fillMaxWidth()
                .background(HeaderDarker)
                .padding(top = 52.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            Text("←", fontSize = 20.sp, color = Color.White,
                modifier = Modifier.align(Alignment.CenterStart)
                    .clickable(onClick = onBack).padding(4.dp))
            Text("Detection Settings", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Threshold editor ──────────────────────────────────────────────
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgWhite),
                shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Thresholds", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(ddtMin, { ddtMin = it }, label = { Text("gz_ddt min") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(ddtMax, { ddtMax = it }, label = { Text("gz_ddt max") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(holdMin, { holdMin = it }, label = { Text("hold min (s)") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(gzMin, { gzMin = it }, label = { Text("gz_d min") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Text("Optional angle filters — leave blank to disable",
                        fontSize = 11.sp, color = TextMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(yawMin, { yawMin = it }, label = { Text("yaw min") },
                            singleLine = true, placeholder = { Text("–") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(yawMax, { yawMax = it }, label = { Text("yaw max") },
                            singleLine = true, placeholder = { Text("–") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(pitchMin, { pitchMin = it }, label = { Text("pitch min") },
                            singleLine = true, placeholder = { Text("–") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(pitchMax, { pitchMax = it }, label = { Text("pitch max") },
                            singleLine = true, placeholder = { Text("–") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(rollMin, { rollMin = it }, label = { Text("roll min") },
                            singleLine = true, placeholder = { Text("–") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(rollMax, { rollMax = it }, label = { Text("roll max") },
                            singleLine = true, placeholder = { Text("–") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Text(
                        "gz_ddt: rate-of-change of baseline-removed gz (stable arm ≈ 0)\n" +
                        "hold min: minimum duration in seconds\n" +
                        "gz_d: baseline-removed gz (arm elevation above ~60s average)\n" +
                        "yaw/pitch/roll: raw angle values in degrees",
                        fontSize = 11.sp, color = TextMuted, lineHeight = 16.sp,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(Slate100)
                                .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                                .clickable {
                                    vm.resetDraft()
                                    ddtMin   = DEFAULT_PROFILE.gzDdtMin.toString()
                                    ddtMax   = DEFAULT_PROFILE.gzDdtMax.toString()
                                    holdMin  = DEFAULT_PROFILE.holdMinSec.toString()
                                    gzMin    = DEFAULT_PROFILE.gzMinDetrended.toString()
                                    yawMin   = ""; yawMax   = ""
                                    pitchMin = ""; pitchMax = ""
                                    rollMin  = ""; rollMax  = ""
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Reset", fontSize = 14.sp, color = TextSecondary) }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(Cyan600)
                                .clickable { vm.updateDraft(currentDraft()); vm.apply() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Apply", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Shot profiles + stats ──────────────────────────────────────────
            when (val s = statsState) {
                is StatsState.Idle -> {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Slate100)
                            .clickable { vm.loadStats() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Load shot profiles", fontSize = 14.sp, color = Cyan600,
                            fontWeight = FontWeight.Medium)
                    }
                }
                is StatsState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Scanning sessions…", fontSize = 14.sp, color = TextMuted)
                    }
                }
                is StatsState.Ready -> {
                    ShotProfilesCard(
                        stats  = s.stats,
                        draft  = draft,
                    )
                }
                is StatsState.Empty -> {
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BgWhite),
                        shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center) {
                            Text("No shots detected with current thresholds",
                                fontSize = 14.sp, color = TextMuted)
                        }
                    }
                }
                is StatsState.NoData -> {
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BgWhite),
                        shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center) {
                            Text("No session data found", fontSize = 14.sp, color = TextMuted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private data class ChannelDef(
    val name: String,
    val color: Color,
    val getData: (ShotWindow) -> List<Float>,
    val threshMin: Float?,
    val threshMax: Float?,
)

// ═══════════════ SHOT PROFILES CARD ═══════════════

@Composable
private fun ShotProfilesCard(
    stats: DetectionStats,
    draft: com.archery.analytics.DetectionProfile,
) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Shot Profiles", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary)
                Text("${stats.sessionsScanned} sessions", fontSize = 12.sp, color = TextMuted)
            }
            // Accuracy row — only show when ground-truth scoring data is available
            if (stats.groundTruthArrows > 0) {
                val pct = (stats.totalShots * 100f / stats.groundTruthArrows).coerceIn(0f, 999f)
                val (pctColor, pctBg) = when {
                    pct >= 90f -> Pair(Color(0xFF166534), Color(0xFFDCFCE7))  // green
                    pct >= 70f -> Pair(Amber800,          Amber100)            // amber
                    else       -> Pair(Color(0xFF991B1B), Color(0xFFFEE2E2))  // red
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pctBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Detection accuracy", fontSize = 12.sp, color = pctColor,
                        fontWeight = FontWeight.Medium)
                    Text(
                        "${stats.totalShots} / ${stats.groundTruthArrows} arrows  ·  ${"%.0f".format(pct)}%",
                        fontSize = 12.sp, color = pctColor, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (stats.shotWindows.isNotEmpty()) {
                val channels = listOf(
                    ChannelDef("gz_d",  Violet400, { w: ShotWindow -> w.gzD },   draft.gzMinDetrended, null),
                    ChannelDef("yaw",   Cyan600,   { w: ShotWindow -> w.yaw },   draft.yawMin,         draft.yawMax),
                    ChannelDef("pitch", Amber600,  { w: ShotWindow -> w.pitch }, draft.pitchMin,       draft.pitchMax),
                    ChannelDef("roll",  Red400,    { w: ShotWindow -> w.roll },  draft.rollMin,        draft.rollMax),
                )
                channels.forEach { ch ->
                    Text(ch.name, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = ch.color, modifier = Modifier.padding(top = 4.dp))
                    ShotProfileChart(
                        windows    = stats.shotWindows,
                        getData    = ch.getData,
                        lineColor  = ch.color,
                        holdMinSec = draft.holdMinSec,
                        threshMin  = ch.threshMin,
                        threshMax  = ch.threshMax,
                    )
                }
                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)) {
                    LegendDash(Red400, "hold min (${draft.holdMinSec}s)")
                    LegendDash(Amber600, "gz_d min (${draft.gzMinDetrended})")
                }
            }

            // Percentile table
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPctCol(Modifier.weight(1f), "Hold (s)", Amber600,
                    stats.holdP10, stats.holdP50, stats.holdP90, "%.1f")
                StatPctCol(Modifier.weight(1f), "Raw gz", Cyan600,
                    stats.gzP10, stats.gzP50, stats.gzP90, "%.1f")
            }
            Text("set hold min ≤ p10 to capture most shots · gz_d min should be well below p10 of raw gz",
                fontSize = 10.sp, color = TextMuted, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ShotProfileChart(
    windows: List<ShotWindow>,
    getData: (ShotWindow) -> List<Float>,
    lineColor: Color,
    holdMinSec: Float,
    threshMin: Float?,
    threshMax: Float?,
) {
    val maxTimeSec = windows.maxOfOrNull { it.times.lastOrNull() ?: 0f }
        ?.coerceAtMost(15f) ?: 8f
    val allVals = windows.flatMap { getData(it) }
    if (allVals.isEmpty()) return
    val rawMin = allVals.min(); val rawMax = allVals.max()
    val pad = ((rawMax - rawMin) * 0.1f).coerceAtLeast(0.5f)
    var yMin = rawMin - pad
    var yMax = rawMax + pad
    if (threshMin != null) { yMin = minOf(yMin, threshMin - pad) }
    if (threshMax != null) { yMax = maxOf(yMax, threshMax + pad) }

    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val pl = 36f; val pr = 8f; val pt = 6f; val pb = 22f
        val cw = size.width - pl - pr
        val ch = size.height - pt - pb

        fun tx(t: Float) = pl + (t / maxTimeSec).coerceIn(0f, 1f) * cw
        fun ty(v: Float) = pt + (1f - (v - yMin) / (yMax - yMin)).coerceIn(0f, 1f) * ch

        // Grid
        for (i in 0..3) {
            val v = yMin + (yMax - yMin) * i / 3f
            drawLine(Color(0xFFE2E8F0), Offset(pl, ty(v)), Offset(pl + cw, ty(v)), 1f)
        }

        // Shot traces
        for (win in windows) {
            val vals = getData(win)
            if (vals.size < 2) continue
            val path = Path(); var started = false
            vals.forEachIndexed { i, v ->
                val t = win.times.getOrElse(i) { i * 0.1f }
                if (t > maxTimeSec) return@forEachIndexed
                val x = tx(t); val y = ty(v)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, lineColor.copy(alpha = 0.22f), style = Stroke(1.5f))
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))

        // Threshold min line (horizontal, amber)
        if (threshMin != null) {
            drawLine(Amber600, Offset(pl, ty(threshMin)), Offset(pl + cw, ty(threshMin)),
                strokeWidth = 2f, pathEffect = dash)
        }
        // Threshold max line (horizontal, amber dashed — slightly different dash phase)
        if (threshMax != null) {
            drawLine(Amber600, Offset(pl, ty(threshMax)), Offset(pl + cw, ty(threshMax)),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 6f))
        }

        // Hold min line (vertical, red)
        val xHold = tx(holdMinSec).coerceIn(pl, pl + cw)
        drawLine(Red400, Offset(xHold, pt), Offset(xHold, pt + ch),
            strokeWidth = 2f, pathEffect = dash)

        // Y axis labels
        val yPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
            textSize = 18f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        for (i in 0..3) {
            val v = yMin + (yMax - yMin) * i / 3f
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(v), pl - 4f, ty(v) + 6f, yPaint)
        }

        // X axis labels (only on last chart — always draw for simplicity)
        val xPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
            textSize = 18f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        listOf(0f, maxTimeSec / 2f, maxTimeSec).forEach { t ->
            drawContext.canvas.nativeCanvas.drawText(
                "%.0fs".format(t), tx(t), size.height - 3f, xPaint)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(16.dp).height(2.dp).background(color))
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun LegendDash(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(16.dp).height(2.dp).background(color.copy(alpha = 0.8f)))
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun StatPctCol(
    modifier: Modifier,
    label: String,
    color: Color,
    p10: Float,
    p50: Float,
    p90: Float,
    fmt: String,
) {
    Column(modifier.border(1.dp, BorderLight, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            PctLabel("p10", fmt.format(p10), color)
            PctLabel("p50", fmt.format(p50), color)
            PctLabel("p90", fmt.format(p90), color)
        }
    }
}

@Composable
private fun PctLabel(pct: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(pct, fontSize = 9.sp, color = TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
