package com.archery.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.shared.ScoreZone
import com.archery.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Color palette ─────────────────────────────────────────────────────────────

// Per-zone button colors matching live scoring screen
private val ZONE_BTN_BG = mapOf(
    ScoreZone.GOLD  to Color(0xFFD97706),
    ScoreZone.RED   to Color(0xFFDC2626),
    ScoreZone.BLUE  to Color(0xFF0891B2),
    ScoreZone.BLACK to Color(0xFF334155),
    ScoreZone.WHITE to Color(0xFFE2E8F0),
    ScoreZone.MISS  to Color(0xFFCBD5E1),
    ScoreZone.DNS   to Color(0xFF555555),
)
private val ZONE_BTN_TEXT = mapOf(
    ScoreZone.WHITE to Color(0xFF1E293B),
    ScoreZone.MISS  to Color(0xFF1E293B),
)

// Arrow chip display: zone → fill color (lighter)
private val ZONE_CHIP_BG = mapOf(
    ScoreZone.GOLD  to Color(0xFFFEF3C7),
    ScoreZone.RED   to Color(0xFFFEE2E2),
    ScoreZone.BLUE  to Color(0xFFCFFAFE),
    ScoreZone.BLACK to Color(0xFFF1F5F9),
    ScoreZone.WHITE to Color(0xFFF8FAFC),
    ScoreZone.MISS  to Color(0xFFF1F5F9),
    ScoreZone.DNS   to Color(0xFFF1F5F9),
)
private val ZONE_CHIP_FG = mapOf(
    ScoreZone.GOLD  to Color(0xFF92400E),
    ScoreZone.RED   to Color(0xFF991B1B),
    ScoreZone.BLUE  to Color(0xFF155E75),
    ScoreZone.BLACK to Color(0xFF334155),
    ScoreZone.WHITE to Color(0xFF475569),
    ScoreZone.MISS  to Color(0xFF64748B),
    ScoreZone.DNS   to Color(0xFF475569),
)

// ── Score data ────────────────────────────────────────────────────────────────

private data class ScoreEntry(val label: String, val zone: ScoreZone, val score: Float)

// Phone/calculator numpad:
//   [DNS] [M ] [✕]   ← special top row
//   [ 7 ] [ 8] [ 9]
//   [ 4 ] [ 5] [ 6]
//   [ 1 ] [ 2] [ 3]
//   [   ] [10] [  ]  ← 10 centered at bottom like 0 on a numpad

// Rows 2-4: standard calculator 1–9
private val NUMPAD_DIGIT_ROWS: List<List<ScoreEntry>> = listOf(
    listOf(ScoreEntry("7", ScoreZone.RED,   7f), ScoreEntry("8", ScoreZone.RED,   8f), ScoreEntry("9", ScoreZone.GOLD,  9f)),
    listOf(ScoreEntry("4", ScoreZone.BLACK, 4f), ScoreEntry("5", ScoreZone.BLUE,  5f), ScoreEntry("6", ScoreZone.BLUE,  6f)),
    listOf(ScoreEntry("1", ScoreZone.WHITE, 1f), ScoreEntry("2", ScoreZone.WHITE, 2f), ScoreEntry("3", ScoreZone.BLACK, 3f)),
)
private val ENTRY_10  = ScoreEntry("10", ScoreZone.GOLD, 10f)
private val ENTRY_M   = ScoreEntry("M",  ScoreZone.MISS,  0f)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSessionScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    vm: LogSessionViewModel = viewModel(),
) {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today = remember { LocalDate.now() }

    var dateMs     by remember { mutableStateOf(
        today.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )}
    var dateLabel  by remember { mutableStateOf(today.format(fmt)) }
    var nameText   by remember { mutableStateOf("") }
    var roundCount     by remember { mutableIntStateOf(6) }
    var arrowsPerRound by remember { mutableIntStateOf(3) }
    var distanceMText    by remember { mutableStateOf("") }
    var targetSizeCmText by remember { mutableStateOf("") }

    // Date picker dialog state
    var showDatePicker by remember { mutableStateOf(false) }

    // Arrow scores: (roundIdx, arrowIdx) → (zone, score)
    val arrowScores = remember { mutableStateMapOf<Pair<Int, Int>, Pair<ScoreZone, Float>>() }

    // Currently selected position
    var selRound by remember { mutableIntStateOf(0) }
    var selArrow by remember { mutableIntStateOf(0) }

    // Clamp selection when dimensions shrink
    LaunchedEffect(roundCount, arrowsPerRound) {
        if (selRound >= roundCount) selRound = (roundCount - 1).coerceAtLeast(0)
        if (selArrow >= arrowsPerRound) selArrow = (arrowsPerRound - 1).coerceAtLeast(0)
    }

    fun getScore(r: Int, a: Int)                           = arrowScores[r to a]
    fun setScore(r: Int, a: Int, v: Pair<ScoreZone, Float>?) {
        if (v == null) arrowScores.remove(r to a) else arrowScores[r to a] = v
    }
    fun roundTotal(r: Int)   = (0 until arrowsPerRound).sumOf { a -> getScore(r, a)?.second?.toDouble() ?: 0.0 }.toFloat()
    fun roundEntered(r: Int) = (0 until arrowsPerRound).count  { a -> getScore(r, a) != null }

    fun applyScore(entry: ScoreEntry) {
        setScore(selRound, selArrow, entry.zone to entry.score)
        // Auto-advance
        when {
            selArrow < arrowsPerRound - 1 -> selArrow++
            selRound < roundCount - 1     -> { selRound++; selArrow = 0 }
            // last arrow of last round — stay, user can tap Save
        }
    }

    // ── Date picker dialog ─────────────────────────────────────────────────────
    // State is created fresh each time the dialog opens so it always reflects dateMs.
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMs ->
                        val picked = Instant.ofEpochMilli(utcMs).atZone(ZoneId.of("UTC")).toLocalDate()
                        dateLabel = picked.format(fmt)
                        dateMs = picked.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Scaffold(
        bottomBar = {
            ScoreKeyboard(
                selRound       = selRound,
                selArrow       = selArrow,
                roundCount     = roundCount,
                arrowsPerRound = arrowsPerRound,
                onEntry        = { applyScore(it) },
                onClear        = { setScore(selRound, selArrow, null) },
                onSave         = {
                    val data = (0 until roundCount).map { r ->
                        (0 until arrowsPerRound).map { a -> getScore(r, a) }
                    }
                    vm.save(dateMs, nameText.ifBlank { null }, arrowsPerRound, data,
                        distanceMText.toIntOrNull(), targetSizeCmText.toIntOrNull()) { id ->
                        onSaved(id)
                    }
                },
                canSave        = dateMs > 0L,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AppBgPage)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(AppHeaderDarker, AppHeaderDark)))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "← Back", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.clickable { onBack() }.padding(4.dp),
                    )
                    Text(
                        "Log Past Session", fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White,
                    )
                    Spacer(Modifier.width(56.dp))
                }
            }

            // ── Setup row ─────────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Date chip — taps open date picker
                    Column(Modifier.weight(1f)) {
                        Text("Date", fontSize = 11.sp, color = AppTextMuted)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppBgPage)
                                .border(1.dp, AppBorderLight, RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                        ) {
                            Text(dateLabel, fontSize = 15.sp, color = AppHeaderDark, fontWeight = FontWeight.Medium)
                        }
                    }
                    // Name field
                    Column(Modifier.weight(1f)) {
                        Text("Name (optional)", fontSize = 11.sp, color = AppTextMuted)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppBgPage)
                                .border(1.dp, AppBorderLight, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = nameText,
                                onValueChange = { nameText = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp, color = AppHeaderDark,
                                ),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (nameText.isEmpty()) {
                                        Text("Session name", fontSize = 15.sp, color = AppTextMuted)
                                    }
                                    inner()
                                },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepperField(
                        label       = "Rounds",
                        value       = roundCount,
                        onDecrement = { if (roundCount > 1) roundCount-- },
                        onIncrement = { if (roundCount < 30) roundCount++ },
                        modifier    = Modifier.weight(1f),
                    )
                    StepperField(
                        label       = "Arrows / round",
                        value       = arrowsPerRound,
                        onDecrement = { if (arrowsPerRound > 1) arrowsPerRound-- },
                        onIncrement = { if (arrowsPerRound < 20) arrowsPerRound++ },
                        modifier    = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value         = distanceMText,
                        onValueChange = { distanceMText = it.filter { c -> c.isDigit() } },
                        label         = { Text("Distance (m)", fontSize = 11.sp) },
                        placeholder   = { Text("e.g. 18", fontSize = 13.sp, color = AppTextMuted) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AppCyan600,
                            unfocusedBorderColor = AppBorderLight,
                            focusedLabelColor    = AppCyan600,
                            unfocusedLabelColor  = AppTextMuted,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value         = targetSizeCmText,
                        onValueChange = { targetSizeCmText = it.filter { c -> c.isDigit() } },
                        label         = { Text("Target (cm)", fontSize = 11.sp) },
                        placeholder   = { Text("e.g. 80", fontSize = 13.sp, color = AppTextMuted) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AppCyan600,
                            unfocusedBorderColor = AppBorderLight,
                            focusedLabelColor    = AppCyan600,
                            unfocusedLabelColor  = AppTextMuted,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(AppBorderLight))

            // ── Round cards ───────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(roundCount) { rIdx ->
                    RoundCard(
                        roundIndex     = rIdx,
                        arrowsPerRound = arrowsPerRound,
                        getScore       = { a -> getScore(rIdx, a) },
                        selArrow       = if (selRound == rIdx) selArrow else -1,
                        total          = roundTotal(rIdx),
                        entered        = roundEntered(rIdx),
                        onArrowTap     = { a -> selRound = rIdx; selArrow = a },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ── Fixed bottom keyboard ─────────────────────────────────────────────────────

@Composable
private fun ScoreKeyboard(
    selRound: Int,
    selArrow: Int,
    roundCount: Int,
    arrowsPerRound: Int,
    onEntry: (ScoreEntry) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppSlate100)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── Selection indicator + Save ────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Round ${selRound + 1}  ·  Arrow ${selArrow + 1} of $arrowsPerRound",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppTextSecondary,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (canSave) Brush.horizontalGradient(listOf(AppCyan600, AppCyan800))
                        else Brush.horizontalGradient(listOf(AppTextMuted, AppTextMuted)),
                    )
                    .clickable(enabled = canSave) { onSave() }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("Save Session", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        // ── Top row: DNS · M · Clear ─────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // DNS (circle-slash)
            Box(
                Modifier
                    .weight(1f).height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ZONE_BTN_BG[ScoreZone.DNS] ?: Color(0xFF555555))
                    .clickable { onEntry(ScoreEntry("DNS", ScoreZone.DNS, 0f)) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).drawBehind {
                    val r = size.minDimension / 2f - 2f
                    drawCircle(Color.White, radius = r,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                    drawLine(Color.White,
                        androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.78f),
                        androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.22f),
                        strokeWidth = 2.5f)
                })
            }
            // Miss
            KeyButton(ENTRY_M, Modifier.weight(1f), onEntry)
            // Clear
            Box(
                Modifier
                    .weight(1f).height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF94A3B8))
                    .clickable { onClear() },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // ── Middle rows: 7–9, 4–6, 1–3 ──────────────────────────────────────
        NUMPAD_DIGIT_ROWS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { entry -> KeyButton(entry, Modifier.weight(1f), onEntry) }
            }
        }

        // ── Bottom row: [spacer] [10] [spacer] ───────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.weight(1f))
            KeyButton(ENTRY_10, Modifier.weight(1f), onEntry)
            Spacer(Modifier.weight(1f))
        }
    }
}

// ── Score key button ──────────────────────────────────────────────────────────

@Composable
private fun KeyButton(entry: ScoreEntry, modifier: Modifier, onEntry: (ScoreEntry) -> Unit) {
    val bg   = ZONE_BTN_BG[entry.zone]   ?: Color(0xFF555555)
    val text = ZONE_BTN_TEXT[entry.zone] ?: Color.White
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onEntry(entry) },
        contentAlignment = Alignment.Center,
    ) {
        Text(entry.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = text)
    }
}

// ── Stepper ───────────────────────────────────────────────────────────────────

@Composable
private fun StepperField(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = AppTextMuted)
        Spacer(Modifier.height(3.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, AppBorderLight, RoundedCornerShape(8.dp))
                .background(AppBgPage),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "−", fontSize = 20.sp, color = AppTextSecondary,
                modifier = Modifier
                    .clickable { onDecrement() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(value.toString(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppHeaderDark)
            Text(
                "+", fontSize = 20.sp, color = AppTextSecondary,
                modifier = Modifier
                    .clickable { onIncrement() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

// ── Nullable stepper (for optional Int? fields) ───────────────────────────────

@Composable
private fun NullableStepperField(
    label: String,
    value: Int?,
    step: Int?,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = AppTextMuted)
        Spacer(Modifier.height(3.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, AppBorderLight, RoundedCornerShape(8.dp))
                .background(AppBgPage),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "−", fontSize = 20.sp, color = AppTextSecondary,
                modifier = Modifier
                    .clickable { onDecrement() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Text(
                value?.toString() ?: "—",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = if (value != null) AppHeaderDark else AppTextMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(
                        "✕", fontSize = 13.sp, color = AppTextMuted,
                        modifier = Modifier
                            .clickable { onClear() }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                    )
                }
                Text(
                    "+", fontSize = 20.sp, color = AppTextSecondary,
                    modifier = Modifier
                        .clickable { onIncrement() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ── Per-round card ────────────────────────────────────────────────────────────

@Composable
private fun RoundCard(
    roundIndex: Int,
    arrowsPerRound: Int,
    getScore: (Int) -> Pair<ScoreZone, Float>?,
    selArrow: Int,
    total: Float,
    entered: Int,
    onArrowTap: (Int) -> Unit,
) {
    val isActiveRound = selArrow >= 0

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(
                width = if (isActiveRound) 1.5.dp else 1.dp,
                color = if (isActiveRound) AppCyan600.copy(alpha = 0.5f) else AppBorderLight,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Round label
        Column(Modifier.width(54.dp)) {
            Text("Round", fontSize = 10.sp, color = AppTextMuted)
            Text(
                "${roundIndex + 1}",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppHeaderDark,
            )
        }

        // Arrow chips
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(arrowsPerRound) { aIdx ->
                val scored = getScore(aIdx)
                val isSel  = aIdx == selArrow
                val zone   = scored?.first
                val bg     = if (zone != null) (ZONE_CHIP_BG[zone]   ?: Color.White)   else Color(0xFFF1F5F9)
                val fg     = if (zone != null) (ZONE_CHIP_FG[zone]   ?: AppHeaderDark) else AppTextMuted
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) AppCyan600.copy(alpha = 0.12f) else bg)
                        .border(
                            width = if (isSel) 2.dp else 1.dp,
                            color = if (isSel) AppCyan600
                                    else if (zone != null) fg.copy(alpha = 0.5f)
                                    else AppBorderLight,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onArrowTap(aIdx) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (scored != null) {
                        val display = scored.second.let { s ->
                            if (s == 0f) "M" else "%.0f".format(s)
                        }
                        Text(display, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                    } else {
                        Text("·", fontSize = 18.sp, color = AppTextMuted)
                    }
                }
            }
        }

        // Total
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(42.dp)) {
            Text("total", fontSize = 10.sp, color = AppTextMuted)
            if (entered > 0) {
                Text(
                    "%.0f".format(total),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppAmber600,
                )
            } else {
                Text("—", fontSize = 16.sp, color = AppTextMuted)
            }
        }
    }
}
