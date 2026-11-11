package com.strengthlogz.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.BigLift
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.StrengthAnalytics
import java.time.LocalDate
import java.util.Locale

private enum class BwRange(val label: String, val days: Int) {
    M1("1M", 30), M3("3M", 90), M6("6M", 182), Y1("1Y", 365), ALL("All", 0)
}

private enum class StrengthMode(val label: String) { LIFT("Lift ×BW"), DOTS("DOTS Score") }

private const val LB_PER_KG = 2.20462

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

/**
 * Body Weight & Relative Strength dashboard: weight trend (with 7-day moving average),
 * relative strength (E1RM / body weight) for any lift, DOTS score and the full reading log.
 */
@Composable
fun BodyWeightScreen(
    bodyWeights: List<BodyWeightEntry>,
    workouts: List<Workout>,
    allSets: List<WorkoutSet>,
    distinctExercises: List<String>,
    getSetsForExercise: (String) -> List<WorkoutSet>,
    isMale: Boolean,
    onSetMale: (Boolean) -> Unit,
    useLbs: Boolean,
    onSetUseLbs: (Boolean) -> Unit,
    healthConnectAvailable: Boolean,
    onSyncHealthConnect: suspend () -> Result<String>,
    onAddManualWeight: (String, Double) -> Unit,
    onDeleteWeight: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var range by remember { mutableStateOf(BwRange.M3) }
    var showAvg by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(StrengthMode.LIFT) }
    var liftName by remember { mutableStateOf<String?>(null) }
    var showAllHistory by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BodyWeightEntry?>(null) }

    val unit = if (useLbs) "lb" else "kg"
    fun disp(kg: Double) = if (useLbs) kg * LB_PER_KG else kg

    val todayDay = remember { LocalDate.now().toEpochDay() }
    fun inRange(epochDay: Long) = range.days == 0 || todayDay - epochDay <= range.days

    val series = remember(bodyWeights) { StrengthAnalytics.buildBodyWeightSeries(bodyWeights) }
    val visible = remember(series, range) { series.filter { inRange(it.epochDay) } }
    var selectedIdx by remember(visible) { mutableIntStateOf((visible.size - 1).coerceAtLeast(0)) }

    val workoutMap = remember(workouts) { workouts.associateBy { it.id } }

    val dotsAll = remember(workouts, allSets, bodyWeights, isMale) {
        StrengthAnalytics.buildDotsTimeline(workouts, allSets, bodyWeights, isMale)
    }
    val dotsVisible = remember(dotsAll, range) { dotsAll.filter { inRange(StrengthAnalytics.epochDay(it.isoDate)) } }
    var dotsSel by remember(dotsVisible) { mutableIntStateOf((dotsVisible.size - 1).coerceAtLeast(0)) }

    val liftChoices = remember(distinctExercises) {
        val big = distinctExercises.filter { StrengthAnalytics.classifyLift(it) != null }
        (big + distinctExercises.filter { it !in big }).take(16)
    }
    val activeLift = liftName ?: liftChoices.firstOrNull()
    val relAll = remember(activeLift, bodyWeights, workouts, allSets) {
        if (activeLift == null) emptyList()
        else StrengthAnalytics.buildRelativeStrength(
            getSetsForExercise(activeLift), workoutMap, bodyWeights,
            BodyWeightUtils.isBodyweightExercise(activeLift)
        )
    }
    val relVisible = remember(relAll, range) { relAll.filter { inRange(StrengthAnalytics.epochDay(it.first)) } }
    var relSel by remember(relVisible, activeLift) { mutableIntStateOf((relVisible.size - 1).coerceAtLeast(0)) }

    val history = remember(series) { series.reversed() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .background(SurfaceCard, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(19.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Body Weight & Strength", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Trends, relative strength & DOTS", fontSize = 12.sp, color = TextSecondary)
                }
                SegChips(listOf("kg", "lb"), if (useLbs) 1 else 0) { onSetUseLbs(it == 1) }
            }
        }

        // KPI strip
        item {
            val last = series.lastOrNull()
            val first = visible.firstOrNull()
            val lastVis = visible.lastOrNull()
            val delta = if (first != null && lastVis != null && visible.size >= 2) lastVis.weightKg - first.weightKg else null
            val spanDays = if (first != null && lastVis != null) lastVis.epochDay - first.epochDay else 0L
            val rate = if (delta != null && spanDays >= 7) delta / (spanDays / 7.0) else null
            val peakDots = dotsAll.maxByOrNull { it.dots }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactKpiCard(
                    label = "CURRENT",
                    value = last?.let { "${f1(disp(it.weightKg))} $unit" } ?: "–",
                    caption = last?.let { "7d avg ${f1(disp(it.avg7Kg))}" } ?: "No readings",
                    valueColor = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
                CompactKpiCard(
                    label = "CHANGE (${range.label})",
                    value = delta?.let { (if (it > 0) "+" else "") + f1(disp(it)) + " $unit" } ?: "–",
                    caption = rate?.let { (if (it > 0) "+" else "") + f2(disp(it)) + " $unit/wk" } ?: "Need 7+ days",
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                CompactKpiCard(
                    label = if (peakDots != null) "PEAK DOTS" else "DOTS",
                    value = peakDots?.let { f1(it.dots) } ?: "–",
                    caption = peakDots?.let { "Total ${f1(disp(it.totalKg))} $unit" } ?: "Needs S+B+D",
                    valueColor = GoldPR,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Range chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BwRange.entries.toList()) { r ->
                    FilterPill(r.label, range == r) { range = r }
                }
            }
        }

        // Body weight chart
        item {
            StrengthCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Body Weight", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        FilterPill("7d avg", showAvg) { showAvg = !showAvg }
                    }
                    if (visible.isEmpty()) {
                        EmptyNote("No body weight readings in this range. Log one below or sync Health Connect.")
                    } else {
                        val safe = selectedIdx.coerceIn(0, visible.size - 1)
                        val p = visible[safe]
                        Text(
                            "${p.displayDate} • ${f1(disp(p.weightKg))} $unit • 7d avg ${f1(disp(p.avg7Kg))} • ${p.source}",
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AccentPeach
                        )
                        TrendLineChart(
                            labels = visible.map { it.displayDate },
                            primary = visible.map { disp(it.weightKg).toFloat() },
                            secondary = if (showAvg && visible.size >= 2) visible.map { disp(it.avg7Kg).toFloat() } else null,
                            selectedIndex = safe,
                            onSelectIndex = { selectedIdx = it },
                            yLabel = { v -> f1(v.toDouble()) }
                        )
                    }
                }
            }
        }

        // Strength card (Lift ×BW / DOTS)
        item {
            StrengthCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Relative Strength", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        SegChips(StrengthMode.entries.map { it.label }, mode.ordinal) { mode = StrengthMode.entries[it] }
                    }

                    if (mode == StrengthMode.LIFT) {
                        if (series.isEmpty()) {
                            EmptyNote("Log your body weight to see strength relative to body weight.")
                        } else if (liftChoices.isEmpty()) {
                            EmptyNote("Log some workouts to see relative strength.")
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(liftChoices) { name ->
                                    FilterPill(name, name == activeLift) { liftName = name }
                                }
                            }
                            if (relVisible.isEmpty()) {
                                EmptyNote("No sessions for ${activeLift ?: "this lift"} in this range.")
                            } else {
                                val safe = relSel.coerceIn(0, relVisible.size - 1)
                                val (date, rel, e1rm) = relVisible[safe]
                                val bw = if (rel > 0) e1rm / rel else 0.0
                                Text(
                                    "$date • E1RM ${f1(disp(e1rm))} $unit ÷ BW ${f1(disp(bw))} $unit = ${f2(rel)}× BW",
                                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AccentPeach
                                )
                                TrendLineChart(
                                    labels = relVisible.map { it.first },
                                    primary = relVisible.map { it.second.toFloat() },
                                    selectedIndex = safe,
                                    onSelectIndex = { relSel = it },
                                    yLabel = { v -> f2(v.toDouble()) + "×" }
                                )
                            }
                        }
                    } else {
                        SegChips(listOf("Male", "Female"), if (isMale) 0 else 1) { onSetMale(it == 0) }
                        if (dotsVisible.isEmpty()) {
                            EmptyNote(
                                if (series.isEmpty()) "Log your body weight to calculate DOTS."
                                else "Log ${BigLift.entries.joinToString(", ") { it.label }} within the last 90 days of a session to calculate DOTS."
                            )
                        } else {
                            val safe = dotsSel.coerceIn(0, dotsVisible.size - 1)
                            val d = dotsVisible[safe]
                            Text(
                                "${d.displayDate} • DOTS ${f1(d.dots)} • BW ${f1(disp(d.bodyWeightKg))} $unit",
                                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AccentPeach
                            )
                            Text(
                                "Squat ${f1(disp(d.squat))} + Bench ${f1(disp(d.bench))} + Deadlift ${f1(disp(d.deadlift))} = ${f1(disp(d.totalKg))} $unit (est. 1RMs, best of last 90 days)",
                                fontSize = 11.5.sp, color = TextSecondary
                            )
                            TrendLineChart(
                                labels = dotsVisible.map { it.displayDate },
                                primary = dotsVisible.map { it.dots.toFloat() },
                                selectedIndex = safe,
                                onSelectIndex = { dotsSel = it },
                                yLabel = { v -> f1(v.toDouble()) },
                                primaryColor = GoldPR
                            )
                        }
                    }
                }
            }
        }

        // Log & sync
        item {
            BodyWeightCard(
                bodyWeights = bodyWeights,
                healthConnectAvailable = healthConnectAvailable,
                onSyncHealthConnect = onSyncHealthConnect,
                onAddManualWeight = onAddManualWeight
            )
        }

        // History
        item {
            Text("Readings (${history.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        items(history.take(if (showAllHistory) history.size else 20), key = { it.isoDate }) { p ->
            StrengthCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.displayDate, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AccentPeach)
                        Text(p.source, fontSize = 11.sp, color = TextSecondary)
                    }
                    Text("${f1(disp(p.weightKg))} $unit", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    IconButton(
                        onClick = { pendingDelete = bodyWeights.firstOrNull { e -> com.strengthlogz.mobile.util.DateUtils.toSortableIso(e.date) == p.isoDate } },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete reading", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (history.size > 20 && !showAllHistory) {
            item {
                TextButton(onClick = { showAllHistory = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show all ${history.size} readings", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SurfaceDark,
            title = { Text("Delete reading?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("${entry.date} • ${f1(disp(entry.weightKg))} $unit will be removed from this device and Google Sheets.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { onDeleteWeight(entry.date); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = DecreaseRed, contentColor = Color.White)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AccentOrange else SurfaceCard,
        border = if (selected) null else BorderStroke(0.8.dp, BorderDark),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Color(0xFF140D07) else TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SegChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .background(BgDark, RoundedCornerShape(9.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .background(if (active) AccentOrange else Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color(0xFF140D07) else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
