package com.archery.ui.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.shared.RoundSummary
import com.archery.shared.ScoreZone
import androidx.compose.ui.platform.LocalConfiguration
import com.archery.ui.theme.*
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ═══════════════ COLORS ═══════════════

private val ABgWhite       = Color.White
private val ARed500        = Color(0xFFEF4444)
private val ARed700        = Color(0xFFB91C1C)
private val ARed800        = Color(0xFF991B1B)
private val ACyan50        = Color(0xFFECFEFF)
private val ARed50         = Color(0xFFFEF2F2)
private val ARed100        = Color(0xFFFEE2E2)
private val AAmber50       = Color(0xFFFFFBEB)
private val ASlate700      = Color(0xFF334155)
private val ASlate600      = Color(0xFF475569)
private val ACyanBorder    = Color(0xFFA5F3FC).copy(alpha = 0.6f)
private val AAmberBorder   = Color(0xFFFDE68A).copy(alpha = 0.6f)
private val ARedBorder     = Color(0xFFFECACA).copy(alpha = 0.6f)

private fun zoneForScore(score: Float): ScoreZone = when {
    score >= 9f -> ScoreZone.GOLD
    score >= 7f -> ScoreZone.RED
    score >= 5f -> ScoreZone.BLUE
    score >= 3f -> ScoreZone.BLACK
    score >= 1f -> ScoreZone.WHITE
    else        -> ScoreZone.MISS
}

private fun fmtScore(v: Float): String =
    if (v == v.toLong().toFloat()) "%.0f".format(v) else "%.1f".format(v)

// ═══════════════ SCREEN ═══════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onAnalyticsClick: () -> Unit = {},
    vm: SessionDetailViewModel = viewModel(),
) {
    LaunchedEffect(sessionId) { vm.load(sessionId) }

    val session by vm.session.collectAsState()
    var showDeleteDialog       by remember { mutableStateOf(false) }
    var showRenameDialog       by remember { mutableStateOf(false) }
    var showDatePicker         by remember { mutableStateOf(false) }
    var showDistanceDialog     by remember { mutableStateOf(false) }
    var renameText by remember(session?.displayName) { mutableStateOf(session?.displayName ?: "") }
    var editDistanceMText    by remember(session?.distanceM)    { mutableStateOf(session?.distanceM?.toString() ?: "") }
    var editTargetSizeCmText by remember(session?.targetSizeCm) { mutableStateOf(session?.targetSizeCm?.toString() ?: "") }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")

    // Date picker — fresh state each time the dialog opens so it always reflects the session date.
    val currentDateMs = remember(session?.date) {
        session?.date?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: System.currentTimeMillis()
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = currentDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMs ->
                        val picked = Instant.ofEpochMilli(utcMs).atZone(ZoneId.of("UTC")).toLocalDate()
                        val epochMs = picked.atTime(LocalTime.NOON)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        vm.updateDate(epochMs)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDistanceDialog) {
        AlertDialog(
            onDismissRequest = { showDistanceDialog = false },
            title   = { Text("Distance & Target") },
            text    = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = editDistanceMText,
                        onValueChange = { editDistanceMText = it.filter { c -> c.isDigit() } },
                        label         = { Text("Distance (m)") },
                        placeholder   = { Text("e.g. 18") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value         = editTargetSizeCmText,
                        onValueChange = { editTargetSizeCmText = it.filter { c -> c.isDigit() } },
                        label         = { Text("Target face (cm)") },
                        placeholder   = { Text("e.g. 80") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateDistanceTarget(editDistanceMText.toIntOrNull(), editTargetSizeCmText.toIntOrNull())
                    showDistanceDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDistanceDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Analytics are loaded + cached by the ViewModel (AnalyticsCache) — instant on revisit.
    val sessionAnalytics by vm.analytics.collectAsState()
    val isAnalyticsLoading by vm.isAnalyticsLoading.collectAsState()
    val hasAnalytics = sessionAnalytics != null
    // Map roundNumber → avg hold time in seconds (from detected shots, falls back to 0)
    val analyticsHoldPerRound: Map<Int, Float> = remember(sessionAnalytics) {
        sessionAnalytics?.roundAnalytics?.associate { ra ->
            ra.origCsvRound to if (ra.detectedShots.isNotEmpty())
                ra.detectedShots.map { it.holdSec }.average().toFloat()
            else 0f
        }?.filterValues { it > 0f } ?: emptyMap()
    }

    Column(
        Modifier.fillMaxSize().background(AppBgPage).verticalScroll(rememberScrollState())
    ) {
        // ── Dark gradient header ──
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(AppHeaderDark, AppHeaderDarker)))
                .padding(start = 20.dp, end = 20.dp, top = statusBarTop + 12.dp, bottom = 24.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Back", fontSize = 14.sp, color = AppTextSlate300,
                        modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        when {
                            hasAnalytics -> Text(
                                "Analytics", fontSize = 14.sp, color = AppCyan400,
                                modifier = Modifier.clickable { onAnalyticsClick() }
                                    .padding(vertical = 4.dp),
                            )
                            isAnalyticsLoading -> Text(
                                "Analyzing…", fontSize = 14.sp,
                                color = AppTextMuted.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        session?.let { s ->
                            Text(
                                if (s.isArchived) "Unarchive" else "Archive",
                                fontSize = 14.sp, color = AppTextSlate300,
                                modifier = Modifier.clickable { vm.archive(!s.isArchived) }
                                    .padding(vertical = 4.dp))
                        }
                        Text("Delete", fontSize = 14.sp, color = ARed500,
                            modifier = Modifier.clickable { showDeleteDialog = true }
                                .padding(vertical = 4.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        session?.displayName ?: "Practice Session",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                    Text("✏", fontSize = 16.sp, color = AppTextSlate300,
                        modifier = Modifier
                            .clickable {
                                renameText = session?.displayName ?: ""
                                showRenameDialog = true
                            }
                            .padding(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { showDatePicker = true },
                ) {
                    Text(session?.date?.format(formatter) ?: "", fontSize = 14.sp, color = AppTextSlate300)
                    Text("✏", fontSize = 12.sp, color = AppTextSlate300.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable {
                        editDistanceMText    = session?.distanceM?.toString() ?: ""
                        editTargetSizeCmText = session?.targetSizeCm?.toString() ?: ""
                        showDistanceDialog = true
                    },
                ) {
                    val distStr   = session?.distanceM?.let { "${it}m" }
                    val targetStr = session?.targetSizeCm?.let { "${it}cm target" }
                    val label     = listOfNotNull(distStr, targetStr).joinToString("  ·  ")
                        .ifBlank { "Set distance & target" }
                    Text(label, fontSize = 13.sp, color = AppTextSlate300.copy(alpha = 0.75f))
                    Text("✏", fontSize = 11.sp, color = AppTextSlate300.copy(alpha = 0.45f))
                }
            }
        }

        val s = session
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Loading…", color = AppTextMuted)
            }
            return@Column
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {

            // ── Session Overview (2×2 stat cards) ──
            val totalScore = s.displayScore
            val totalArrows = s.totalArrows
            val avgArrow = s.avgPerArrow
            val avgHr = s.avgHeartRate

            Text("Session Overview", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = AppHeaderDark)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    bgStart = ACyan50, bgEnd = AppCyan100.copy(0.5f), borderColor = ACyanBorder,
                    iconBg = AppCyan600, iconText = "R",
                    label = "Rounds", labelColor = AppCyan800,
                    value = "${s.rounds.size}",
                    subtitle = "$totalArrows arrows total", subtitleColor = AppCyan800.copy(0.8f),
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    bgStart = AAmber50, bgEnd = AppAmber100.copy(0.5f), borderColor = AAmberBorder,
                    iconBg = AppAmber600, iconText = "S",
                    label = "Total Score", labelColor = AppAmber800,
                    value = if (totalScore > 0) fmtScore(totalScore) else "—",
                    subtitle = "points earned", subtitleColor = AppAmber700,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    bgStart = AppSlate100.copy(0.5f), bgEnd = AppSlate100, borderColor = AppBorderLight,
                    iconBg = ASlate700, iconText = "A",
                    label = "Avg Arrow", labelColor = ASlate700,
                    value = if (avgArrow > 0) "%.1f".format(avgArrow) else "—",
                    subtitle = "points per arrow", subtitleColor = ASlate600,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    bgStart = ARed50, bgEnd = ARed100.copy(0.5f), borderColor = ARedBorder,
                    iconBg = ARed500, iconText = "H",
                    label = "Avg HR", labelColor = ARed800,
                    value = if ((avgHr ?: 0f) > 0) "%.0f".format(avgHr) else "—",
                    subtitle = "beats per min", subtitleColor = ARed700,
                )
            }
            Spacer(Modifier.height(24.dp))

            // ── Round Details (shown FIRST, before zone distribution) ──
            val hasArrows = s.rounds.any { it.arrows.isNotEmpty() }
            val maxArrows = s.rounds.maxOfOrNull { it.arrows.size } ?: 0
            var showInsertDialog by remember { mutableStateOf(false) }
            var editingRound by remember { mutableStateOf<Int?>(null) }

            Text("Round Details", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = AppHeaderDark)
            Spacer(Modifier.height(12.dp))

            RoundDetailsTable(
                rounds = s.rounds,
                maxArrows = maxArrows,
                hasArrows = hasArrows,
                editingRound = editingRound,
                analyticsHoldPerRound = analyticsHoldPerRound,
                onRoundClick = { rn -> editingRound = if (editingRound == rn) null else rn },
                onScoreArrow = { roundNumber, shotNumber, zone, score ->
                    vm.updateArrowScore(roundNumber, shotNumber, zone, score)
                },
                onSetTotal = { roundNumber, total -> vm.updateRoundTotal(roundNumber, total) },
                onDeleteRound = { roundNumber ->
                    editingRound = null
                    vm.deleteRound(roundNumber)
                },
            )
            Spacer(Modifier.height(8.dp))
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(AppCyan600)
                        .clickable { showInsertDialog = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("+ Round", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White)
                }
            }
            Spacer(Modifier.height(20.dp))

            // ── Zone Distribution (shown AFTER round details) ──
            if (s.zoneCounts.isNotEmpty()) {
                ZoneDistributionCard(s.zoneCounts)
                Spacer(Modifier.height(16.dp))
            }

            // Insert round dialog
            if (showInsertDialog) {
                AlertDialog(
                    onDismissRequest = { showInsertDialog = false },
                    title = { Text("Insert Round") },
                    text = {
                        Column {
                            Text("Choose where to insert a new round:",
                                fontSize = 14.sp, color = AppTextSecondary)
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                    .background(AppSlate100)
                                    .clickable { vm.insertRoundAfter(0); showInsertDialog = false }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                            ) { Text("Before Round 1", fontSize = 14.sp, color = AppHeaderDark) }
                            s.rounds.forEach { round ->
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                        .background(AppSlate100)
                                        .clickable {
                                            vm.insertRoundAfter(round.number)
                                            showInsertDialog = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 12.dp)
                                ) {
                                    Text("After Round ${round.number}", fontSize = 14.sp,
                                        color = AppHeaderDark)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showInsertDialog = false }) { Text("Cancel") }
                    },
                )
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = { Text("Practice Session") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppCyan600,
                        unfocusedBorderColor = AppBorderLight,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(renameText)
                    showRenameDialog = false
                }) { Text("Save", color = AppCyan600) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session") },
            text = { Text("This session will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.delete(onDeleted = onBack)
                }) { Text("Delete", color = ARed500) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ═══════════════ STAT CARD ═══════════════

@Composable
private fun StatCard(
    modifier: Modifier,
    bgStart: Color, bgEnd: Color, borderColor: Color,
    iconBg: Color, iconText: String,
    label: String, labelColor: Color,
    value: String,
    subtitle: String, subtitleColor: Color,
) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(bgStart, bgEnd)))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(22.dp).background(iconBg, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(iconText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color.White)
                }
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = labelColor)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppHeaderDark)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = subtitleColor)
        }
    }
}

// ═══════════════ ZONE DISTRIBUTION ═══════════════

private val zoneUiColors = mapOf(
    ScoreZone.GOLD  to Color(0xFFF59E0B),
    ScoreZone.RED   to Color(0xFFEF4444),
    ScoreZone.BLUE  to Color(0xFF0EA5E9),
    ScoreZone.BLACK to Color(0xFF64748B),
    ScoreZone.WHITE to Color(0xFFCBD5E1),
    ScoreZone.MISS  to Color(0xFF94A3B8),
)

@Composable
private fun ZoneDistributionCard(zoneCounts: Map<ScoreZone, Int>) {
    val displayZones = listOf(
        ScoreZone.GOLD, ScoreZone.RED, ScoreZone.BLUE,
        ScoreZone.BLACK, ScoreZone.WHITE, ScoreZone.MISS,
    )
    val activeZones = displayZones.filter { (zoneCounts[it] ?: 0) > 0 }
    val total = zoneCounts.values.sum().coerceAtLeast(1)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ABgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Zone Distribution", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = AppHeaderDark)
            Spacer(Modifier.height(8.dp))
            // Count numbers above bar
            Row(Modifier.fillMaxWidth()) {
                activeZones.forEach { zone ->
                    val count = zoneCounts[zone] ?: 0
                    Text(
                        text = "$count",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = zoneUiColors[zone] ?: AppTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(count.toFloat() / total),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            // Stacked color bar
            Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp))) {
                activeZones.forEach { zone ->
                    val count = zoneCounts[zone] ?: 0
                    Box(Modifier.weight(count.toFloat() / total).fillMaxSize()
                        .background(zoneUiColors[zone] ?: AppTextMuted))
                }
            }
        }
    }
}

// ═══════════════ ROUND DETAILS TABLE ═══════════════

@Composable
private fun RoundDetailsTable(
    rounds: List<RoundSummary>,
    maxArrows: Int,
    hasArrows: Boolean,
    editingRound: Int?,
    analyticsHoldPerRound: Map<Int, Float> = emptyMap(),
    onRoundClick: (Int) -> Unit,
    onScoreArrow: (roundNumber: Int, shotNumber: Int, ScoreZone, Float) -> Unit,
    onSetTotal: (roundNumber: Int, Float) -> Unit,
    onDeleteRound: (roundNumber: Int) -> Unit,
) {
    // Fixed column widths for horizontal scroll
    val rnW   = 44.dp   // Round number
    val arW   = 30.dp   // Arrow score cells
    val totW  = 52.dp   // Total
    val hrW   = 44.dp   // Heart rate
    val holdW = 50.dp   // Hold time
    val avgW  = 50.dp   // Average

    // Compute minimum table width so it always fills screen width (even with few/no arrows)
    val arrowColumnsWidth = if (hasArrows && maxArrows > 0) arW * maxArrows.toFloat() else 0.dp
    val contentWidth = rnW + arrowColumnsWidth + totW + hrW + holdW + avgW + 24.dp  // 24 = 12*2 padding
    val screenMinWidth = LocalConfiguration.current.screenWidthDp.dp - 32.dp // 16dp padding each side
    val tableWidth = maxOf(contentWidth, screenMinWidth)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ABgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column {
            // ── Scrollable header + data rows ──
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(tableWidth)) {
                    // Header row
                    Row(
                        Modifier.fillMaxWidth()
                            .background(AppBorderLight.copy(0.5f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Round", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = AppTextSecondary, modifier = Modifier.width(rnW))
                        if (hasArrows && maxArrows > 0) {
                            for (a in 1..maxArrows) {
                                Text("A$a", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    color = AppTextSecondary, textAlign = TextAlign.Center,
                                    modifier = Modifier.width(arW))
                            }
                        }
                        Text("Total", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = AppTextSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.width(totW))
                        Text("HR", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = AppTextSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.width(hrW))
                        Text("Hold", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = AppTextSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.width(holdW))
                        Text("Avg.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = AppTextSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.width(avgW))
                        Spacer(Modifier.weight(1f))
                    }

                    // Data rows
                    rounds.forEachIndexed { idx, round ->
                        val roundTotal = round.displayScore
                        val isEditing = editingRound == round.number
                        val showDivider = idx != rounds.size - 1 || isEditing

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (isEditing) Modifier.background(AppCyan100.copy(0.3f)) else Modifier)
                                .then(if (showDivider) Modifier.border(
                                    0.5.dp,
                                    if (isEditing) AppCyan600 else AppBorderLight.copy(0.5f),
                                    RoundedCornerShape(0.dp)
                                ) else Modifier)
                                .clickable { onRoundClick(round.number) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Round number circle
                            Box(Modifier.width(rnW)) {
                                Box(
                                    Modifier.size(28.dp).background(
                                        if (isEditing) AppCyan600 else AppHeaderDark, CircleShape
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${round.number}", fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            // Arrow score cells
                            if (hasArrows && maxArrows > 0) {
                                for (a in 1..maxArrows) {
                                    val arrow = round.arrows.find { it.shotNumber == a }
                                    val cellText = when {
                                        arrow == null -> "—"
                                        arrow.zone == ScoreZone.DNS -> "—"
                                        else -> fmtScore(arrow.score)
                                    }
                                    Text(
                                        text = cellText,
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (arrow != null && arrow.zone != ScoreZone.DNS)
                                            AppTextSecondary else AppTextMuted,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(arW),
                                    )
                                }
                            }

                            // Total (cyan pill)
                            Box(Modifier.width(totW), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth().background(AppCyan100, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (roundTotal > 0) fmtScore(roundTotal) else "—",
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = AppCyan800)
                                }
                            }

                            // HR
                            val hr = round.avgHeartRate ?: 0f
                            Box(Modifier.width(hrW), contentAlignment = Alignment.Center) {
                                Text(
                                    if (hr > 0f) "%.0f".format(hr) else "—",
                                    fontSize = 11.sp,
                                    color = if (hr > 0f) AppHrPink else AppTextMuted,
                                    fontWeight = if (hr > 0f) FontWeight.Medium else FontWeight.Normal,
                                )
                            }

                            // Hold — prefer analytics detected shots, fall back to CSV avg
                            val holdSec = analyticsHoldPerRound[round.number]?.takeIf { it > 0f }
                                ?: (round.avgHoldMs ?: 0L).takeIf { it > 0 }?.let { it / 1000f }
                            Box(Modifier.width(holdW), contentAlignment = Alignment.Center) {
                                Text(
                                    if (holdSec != null) "%.1fs".format(holdSec) else "—",
                                    fontSize = 11.sp,
                                    color = if (holdSec != null) AppAmber700 else AppTextMuted,
                                    fontWeight = if (holdSec != null) FontWeight.Medium else FontWeight.Normal,
                                )
                            }

                            // Avg (amber pill) — exclude DNS from denominator
                            val scored = round.arrows.count { it.zone != ScoreZone.DNS }
                            val avg = if (scored > 0) roundTotal / scored else 0f
                            Box(Modifier.width(avgW), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth().background(AppAmber100, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (avg > 0) "%.1f".format(avg) else "—",
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = AppAmber800)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Inline editors (full width, outside scroll) ──
            rounds.forEach { round ->
                if (editingRound == round.number) {
                    TableRoundEditor(
                        round = round,
                        onScoreArrow = { shotNumber, zone, score ->
                            onScoreArrow(round.number, shotNumber, zone, score)
                        },
                        onSetTotal = { total -> onSetTotal(round.number, total) },
                        onDelete = { onDeleteRound(round.number) },
                        onCancel = { onRoundClick(round.number) },
                    )
                }
            }
        }
    }
}

// ═══════════════ TABLE ROUND EDITOR ═══════════════

@Composable
private fun TableRoundEditor(
    round: RoundSummary,
    onScoreArrow: (Int, ScoreZone, Float) -> Unit,
    onSetTotal: (Float) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    // Always show one empty slot past the last arrow so the user can keep adding
    val arrowCount = round.arrows.size + 1
    var selectedArrow by remember(round.number) { mutableStateOf<Int?>(null) }
    // Tracks live with arrow edits — reset whenever displayScore changes (DB re-emits)
    var totalVal by remember(round.number, round.displayScore) { mutableStateOf(round.displayScore) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scoreVals = listOf(10f, 9f, 8f, 7f, 6f, 5f, 4f, 3f, 2f, 1f, 0f)
    val arrowsPerRow = 6

    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFFF0FDFA))
            .border(0.5.dp, AppCyan600.copy(0.3f))
            .padding(12.dp)
    ) {
        Text("Edit Round ${round.number}", fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = AppCyan800)
        Spacer(Modifier.height(8.dp))
        Text("Tap arrow to select, then tap score below:", fontSize = 11.sp, color = AppTextSecondary)
        Spacer(Modifier.height(6.dp))

        for (rowStart in 1..arrowCount step arrowsPerRow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (a in rowStart..minOf(rowStart + arrowsPerRow - 1, arrowCount)) {
                    val existing = round.arrows.find { it.shotNumber == a }
                    val isSelected = selectedArrow == a
                    val isDns = existing?.zone == ScoreZone.DNS
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(when {
                                isSelected -> AppCyan600
                                existing != null -> AppCyan100
                                else -> AppSlate100
                            })
                            .border(1.dp, if (isSelected) AppCyan800 else AppBorderLight,
                                RoundedCornerShape(6.dp))
                            .clickable { selectedArrow = if (isSelected) null else a }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("A$a", fontSize = 9.sp,
                                color = if (isSelected) Color.White else AppTextMuted)
                            Text(
                                when {
                                    existing == null -> "—"
                                    isDns -> "DNS"
                                    else -> fmtScore(existing.score)
                                },
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White
                                       else if (existing != null) AppHeaderDark else AppTextMuted,
                            )
                        }
                    }
                }
                val rem = arrowsPerRow - (minOf(rowStart + arrowsPerRow - 1, arrowCount) - rowStart + 1)
                repeat(rem) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }

        AnimatedVisibility(visible = selectedArrow != null) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text("Score for Arrow $selectedArrow:", fontSize = 11.sp, color = AppTextSecondary)
                Spacer(Modifier.height(4.dp))
                // Numeric score buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    scoreVals.forEach { sc ->
                        val label = if (sc == 0f) "M" else "%.0f".format(sc)
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                                .background(AppSlate100)
                                .clickable {
                                    val zone = zoneForScore(sc)
                                    onScoreArrow(selectedArrow!!, zone, sc)
                                    val next = selectedArrow!! + 1
                                    selectedArrow = if (next <= arrowCount) next else null
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = AppHeaderDark)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // DNS button (full width, distinct style)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        .background(AppTextMuted.copy(alpha = 0.15f))
                        .border(1.dp, AppTextMuted.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            onScoreArrow(selectedArrow!!, ScoreZone.DNS, 0f)
                            val next = selectedArrow!! + 1
                            selectedArrow = if (next <= arrowCount) next else null
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("DNS  (Did Not Shoot)", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = AppTextMuted)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Round Total", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppTextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(AppSlate100)
                .clickable { totalVal = (totalVal - 1f).coerceAtLeast(0f); onSetTotal(totalVal) },
                contentAlignment = Alignment.Center) {
                Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppHeaderDark)
            }
            Text("%.0f".format(totalVal), fontSize = 36.sp, fontWeight = FontWeight.Bold,
                color = AppAmber600, modifier = Modifier.padding(horizontal = 20.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).background(AppSlate100)
                .clickable { totalVal += 1f; onSetTotal(totalVal) },
                contentAlignment = Alignment.Center) {
                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppHeaderDark)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            if (!showDeleteConfirm) {
                Text("Delete Round", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = ARed500,
                    modifier = Modifier.clickable { showDeleteConfirm = true }.padding(8.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Delete?", fontSize = 12.sp, color = ARed500, fontWeight = FontWeight.Bold)
                    Text("Yes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ARed500,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(ARed100).clickable { onDelete() }
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                    Text("No", fontSize = 12.sp, color = AppTextSecondary,
                        modifier = Modifier.clickable { showDeleteConfirm = false }
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AppCyan600)
                    .clickable { onCancel() }
                    .padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

// ── Nullable stepper for detail dialogs ───────────────────────────────────────

@Composable
private fun DetailNullableStepper(
    label: String,
    value: Int?,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Text(label, fontSize = 12.sp, color = ASlate600)
        Spacer(Modifier.height(4.dp))
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
                modifier = Modifier.clickable { onDecrement() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        modifier = Modifier.clickable { onClear() }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                Text(
                    "+", fontSize = 20.sp, color = AppTextSecondary,
                    modifier = Modifier.clickable { onIncrement() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
