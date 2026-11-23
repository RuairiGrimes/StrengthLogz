package com.strengthlogz.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.SavedSummaryEntry
import com.strengthlogz.mobile.model.SavedSummaryLiftItem
import com.strengthlogz.mobile.model.SavedSummaryMuscleItem
import com.strengthlogz.mobile.model.TrainingMode
import com.strengthlogz.mobile.model.TrainingModePolicy
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutProgram
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import com.strengthlogz.mobile.util.SummaryEngine
import kotlin.math.roundToInt

private enum class SummaryModalTab { LIVE, LOG }

@Composable
fun FourteenDaySummaryDialog(
    workouts: List<Workout>,
    sets: List<WorkoutSet>,
    exercises: List<Exercise>,
    bodyWeights: List<BodyWeightEntry>,
    goals: List<ExerciseGoal>,
    activeProgram: WorkoutProgram?,
    isMale: Boolean,
    trainingMode: TrainingMode = TrainingMode.MAINGAIN,
    onOpenModePicker: () -> Unit = {},
    savedSummaries: List<SavedSummaryEntry>,
    onSaveSummary: (SavedSummaryEntry) -> Unit,
    onDeleteSummary: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val summary = remember(workouts, sets, exercises, bodyWeights, goals, activeProgram, isMale, trainingMode) {
        SummaryEngine.build14DaySummary(
            workouts = workouts,
            sets = sets,
            exercises = exercises,
            bodyWeights = bodyWeights,
            goals = goals,
            activeProgram = activeProgram,
            isMale = isMale,
            trainingMode = trainingMode
        )
    }

    var activeTab by remember { mutableStateOf(SummaryModalTab.LIVE) }
    var entryTitle by remember { mutableStateOf("") }
    var entryNotes by remember { mutableStateOf("") }
    var showNotesField by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = BgDark,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = SurfaceDark,
                    border = BorderStroke(0.5.dp, BorderDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "14-Day Training Summary",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${summary.startDate} – ${summary.endDate}",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TrainingModePill(
                                    mode = trainingMode,
                                    compact = true,
                                    onClick = onOpenModePicker
                                )
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Clean 2-tab switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgDark)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SummaryTabButton(
                                label = "Live Summary",
                                selected = activeTab == SummaryModalTab.LIVE,
                                onClick = { activeTab = SummaryModalTab.LIVE },
                                modifier = Modifier.weight(1f)
                            )
                            SummaryTabButton(
                                label = "Saved Log (${savedSummaries.size})",
                                selected = activeTab == SummaryModalTab.LOG,
                                onClick = { activeTab = SummaryModalTab.LOG },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeTab == SummaryModalTab.LIVE) {
                        // Save Summary Snapshot Bar
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceCard,
                            border = BorderStroke(0.8.dp, BorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = entryTitle,
                                        onValueChange = {
                                            entryTitle = it
                                            justSaved = false
                                        },
                                        placeholder = {
                                            Text(
                                                text = "Label (e.g. Week 4 Block)",
                                                fontSize = 12.sp,
                                                color = TextMuted
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedBorderColor = AccentOrange,
                                            unfocusedBorderColor = BorderDark,
                                            focusedContainerColor = BgDark,
                                            unfocusedContainerColor = BgDark
                                        ),
                                        shape = RoundedCornerShape(9.dp)
                                    )

                                    Button(
                                        onClick = {
                                            val entry = summary.toSavedEntry(
                                                customTitle = entryTitle,
                                                customNotes = entryNotes
                                            )
                                            onSaveSummary(entry)
                                            entryTitle = ""
                                            entryNotes = ""
                                            showNotesField = false
                                            justSaved = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (justSaved) IncreaseGreen else AccentOrange,
                                            contentColor = Color(0xFF140D07)
                                        ),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (justSaved) Icons.Default.Check else Icons.Default.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = if (justSaved) "Saved" else "Save",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showNotesField) "Hide note" else "+ Add block note",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.clickable { showNotesField = !showNotesField }
                                    )
                                    if (justSaved) {
                                        Text(
                                            text = "Added to Saved Log",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = IncreaseGreen,
                                            modifier = Modifier.clickable { activeTab = SummaryModalTab.LOG }
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showNotesField) {
                                    OutlinedTextField(
                                        value = entryNotes,
                                        onValueChange = { entryNotes = it },
                                        placeholder = {
                                            Text(
                                                text = "Optional training context, sleep, deload notes...",
                                                fontSize = 12.sp,
                                                color = TextMuted
                                            )
                                        },
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedBorderColor = AccentOrange,
                                            unfocusedBorderColor = BorderDark,
                                            focusedContainerColor = BgDark,
                                            unfocusedContainerColor = BgDark
                                        ),
                                        shape = RoundedCornerShape(9.dp)
                                    )
                                }
                            }
                        }

                        // Score Hero Card
                        ScoreBreakdownCard(
                            overallScore = summary.overallScore,
                            tierLabel = summary.tierLabel,
                            volumeScore = summary.volumeScore,
                            strengthScore = summary.strengthScore,
                            consistencyScore = summary.consistencyScore,
                            balanceScore = summary.balanceScore,
                            trainingMode = summary.trainingMode
                        )

                        // 2x2 Telemetry Strip
                        val volStr = formatTonnage(summary.totalVolumeKg)
                        val volSub = summary.volumeDeltaPct?.let { pct ->
                            val sign = if (pct > 0) "+" else ""
                            "$sign${formatWeightValue(pct)}% vs prior 14d"
                        } ?: if (summary.priorVolumeKg > 0) "Prior: ${formatTonnage(summary.priorVolumeKg)}" else "Baseline block"

                        val sessSub = summary.programAdherencePct?.let {
                            "$it% of ${summary.programName ?: "program"}"
                        } ?: "${summary.sessionsPerWeek}/wk • ${summary.activeDaysCount} active days"

                        val setsSub = if (summary.failedSetsCount > 0) {
                            "${summary.avgSetsPerWorkout}/sess • ${summary.failedSetsCount} failed excl."
                        } else {
                            "${summary.avgSetsPerWorkout}/sess • ${summary.avgRepsPerSet} reps/set"
                        }

                        val bwVal = if (summary.hasBodyWeightData && summary.endBodyWeightKg != null) {
                            "${formatWeightValue(summary.endBodyWeightKg)} kg"
                        } else "—"
                        val bwSub = buildString {
                            if (summary.bodyWeightDeltaKg != null) {
                                val sign = if (summary.bodyWeightDeltaKg > 0) "+" else ""
                                append("$sign${formatWeightValue(summary.bodyWeightDeltaKg)} kg")
                            } else if (summary.hasBodyWeightData) {
                                append("Steady")
                            } else {
                                append("No readings")
                            }
                            if (!summary.bwPaceAssessment.isNullOrBlank()) {
                                append(" • ${summary.bwPaceAssessment}")
                            } else if (summary.dotsScore != null) {
                                append(" • DOTS ${formatWeightValue(summary.dotsScore)}")
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TelemetryStatCard("Sessions", "${summary.workoutsCount}", sessSub, Modifier.weight(1f))
                                TelemetryStatCard("External volume", volStr, volSub, Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TelemetryStatCard("Working sets", "${summary.workingSetsCount}", setsSub, Modifier.weight(1f))
                                TelemetryStatCard("Body weight", bwVal, bwSub, Modifier.weight(1f))
                            }
                        }

                        // Muscle Volume & Balance Card
                        MuscleAndBalanceCard(
                            muscleItems = summary.muscleItems,
                            pushToPullRatio = summary.pushToPullRatio,
                            upperSharePct = summary.upperSharePct,
                            heavySets = summary.heavySets,
                            strengthHypSets = summary.strengthHypSets,
                            hypertrophySets = summary.hypertrophySets,
                            enduranceSets = summary.enduranceSets,
                            totalWorkingSets = summary.workingSetsCount
                        )

                        // Movement Progression Card
                        if (summary.lifts.isNotEmpty()) {
                            LiftsProgressionCard(
                                lifts = summary.lifts,
                                prsCount = summary.prsCount,
                                relPrsCount = summary.relPrsCount,
                                retainedCount = summary.retainedCount,
                                progressingCount = summary.progressingCount,
                                holdingCount = summary.holdingCount,
                                regressingCount = summary.regressingCount,
                                trainingMode = summary.trainingMode
                            )
                        }

                        // Active Goals Alignment
                        if (summary.goalStatuses.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SurfaceCard,
                                border = BorderStroke(0.8.dp, BorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Goal Alignment",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    summary.goalStatuses.forEach { g ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = g.exerciseName,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "Target: ${g.targetSummary}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                            Text(
                                                text = if (g.achieved) "Achieved" else if (g.trainedIn14d) "${g.progressPct}% • Active" else "${g.progressPct}% • Untrained",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = when {
                                                    g.achieved -> GoldPR
                                                    g.trainedIn14d -> IncreaseGreen
                                                    else -> TextMuted
                                                },
                                                style = TextStyle(fontFeatureSettings = "tnum")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Key Findings & Next-Block Focus
                        InsightsCard(
                            title = "Key Findings",
                            items = summary.highlights,
                            dotColor = IncreaseGreen
                        )
                        InsightsCard(
                            title = "Next-Block Focus",
                            items = summary.recommendations,
                            dotColor = AccentOrange
                        )
                    } else {
                        // Saved Log Tab
                        SavedSummariesListSection(
                            savedSummaries = savedSummaries,
                            onDeleteSummary = onDeleteSummary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) SurfaceCardElevated else Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScoreBreakdownCard(
    overallScore: Int,
    tierLabel: String,
    volumeScore: Int,
    strengthScore: Int,
    consistencyScore: Int,
    balanceScore: Int,
    trainingMode: TrainingMode = TrainingMode.MAINGAIN
) {
    val scoreColor = scoreColorFor(overallScore)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(0.8.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Training score",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = scoreColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = tierLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$overallScore",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        lineHeight = 36.sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = "/100",
                        fontSize = 13.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 5.dp, start = 2.dp),
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BgDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((overallScore / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(scoreColor)
                )
            }

            val strengthBarLabel = when (trainingMode) {
                TrainingMode.CUT -> "Retention & Rel."
                TrainingMode.BULK -> "Strength Gains"
                TrainingMode.MAINGAIN -> "Strength"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubScoreBarItem("Volume", volumeScore, Modifier.weight(1f))
                SubScoreBarItem(strengthBarLabel, strengthScore, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubScoreBarItem("Cadence", consistencyScore, Modifier.weight(1f))
                SubScoreBarItem("Balance", balanceScore, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SubScoreBarItem(label: String, score: Int, modifier: Modifier = Modifier) {
    val color = scoreColorFor(score)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgDark)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 11.5.sp, color = TextSecondary)
            Text(
                text = "$score",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SurfaceHighlight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((score / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun TelemetryStatCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(0.8.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = label, fontSize = 11.5.sp, color = TextSecondary)
            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Text(
                text = sub,
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun MuscleAndBalanceCard(
    muscleItems: List<SavedSummaryMuscleItem>,
    pushToPullRatio: Double?,
    upperSharePct: Int,
    heavySets: Int,
    strengthHypSets: Int,
    hypertrophySets: Int,
    enduranceSets: Int,
    totalWorkingSets: Int
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(0.8.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Muscle Volume (14d vs Target)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (pushToPullRatio != null) {
                    Text(
                        text = "Push:Pull ${formatWeightValue(pushToPullRatio)}:1",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }

            muscleItems.forEach { m ->
                val ratio = (m.workingSets14d.toFloat() / m.target14dSets.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                val barColor = when {
                    m.workingSets14d >= m.target14dSets * 0.85 -> IncreaseGreen
                    m.workingSets14d >= m.target14dSets * 0.45 -> AccentOrange
                    else -> TextMuted
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = m.category, fontSize = 12.sp, color = TextPrimary)
                        Text(
                            text = "${m.workingSets14d}/${m.target14dSets} sets",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(BgDark)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor)
                        )
                    }
                }
            }

            if (totalWorkingSets > 0) {
                HorizontalDivider(color = BorderDark, thickness = 0.6.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Upper/Lower: $upperSharePct% / ${100 - upperSharePct}%",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = "Rep zones: $heavySets heavy • $strengthHypSets str-hyp • $hypertrophySets hyp • $enduranceSets end",
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}

@Composable
private fun LiftsProgressionCard(
    lifts: List<SavedSummaryLiftItem>,
    prsCount: Int,
    relPrsCount: Int = 0,
    retainedCount: Int = 0,
    progressingCount: Int,
    holdingCount: Int,
    regressingCount: Int,
    trainingMode: TrainingMode = TrainingMode.MAINGAIN
) {
    var showAll by remember { mutableStateOf(false) }
    val visibleLifts = if (showAll) lifts else lifts.take(8)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(0.8.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (trainingMode == TrainingMode.CUT) "Retention & Strength" else "Strength Progression",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = if (trainingMode == TrainingMode.CUT) {
                        "$prsCount PR • $relPrsCount rel PR • ${holdingCount + retainedCount} held"
                    } else {
                        "$prsCount PR • $progressingCount up • $holdingCount steady • $regressingCount down"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }

            visibleLifts.forEach { item ->
                val delta = item.e1rmDeltaKg ?: item.withinPeriodE1rmDeltaKg
                val retPct = item.retentionPct
                val isCutHeldDip = trainingMode == TrainingMode.CUT &&
                    delta != null && delta < -0.5 &&
                    (retPct == null || retPct >= TrainingModePolicy.CUT_RETENTION_FLOOR_PCT)

                val deltaLabel = when {
                    item.isAllTimePr && delta != null && delta > 0 -> "PR +${formatWeightValue(delta)} kg"
                    item.isAllTimePr -> "All-time PR"
                    item.isAllTimeRelPr -> "REL PR"
                    isCutHeldDip && retPct != null -> "${retPct.roundToInt()}% held"
                    delta != null -> "${if (delta > 0) "+" else ""}${formatWeightValue(delta)} kg"
                    else -> "${item.workingSets14d} sets"
                }
                val deltaColor = when {
                    item.isAllTimePr || item.isAllTimeRelPr -> GoldPR
                    delta != null && delta > 0.5 -> IncreaseGreen
                    isCutHeldDip -> BlueAccent
                    delta != null && delta < -0.5 -> DecreaseRed
                    else -> TextSecondary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgDark)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.exerciseName,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val relStr = item.relativeStrengthBw?.let { rel ->
                            val relDeltaStr = item.relBwDelta?.takeIf { kotlin.math.abs(it) >= 0.01 }?.let { rd ->
                                " (${if (rd > 0) "+" else ""}${"%.2f".format(rd)}×)"
                            } ?: ""
                            " • ${"%.2f".format(rel)}× BW$relDeltaStr"
                        } ?: ""
                        Text(
                            text = "${item.bestSet14d} • E1RM ${formatWeightValue(item.bestE1rm14dKg)} kg$relStr",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = deltaLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = deltaColor,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }

            if (lifts.size > 8) {
                Text(
                    text = if (showAll) "Show top 8" else "Show all ${lifts.size} movements",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentOrange,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { showAll = !showAll }
                )
            }
        }
    }
}

@Composable
private fun InsightsCard(
    title: String,
    items: List<String>,
    dotColor: Color
) {
    if (items.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(0.8.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            items.forEach { line ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = line,
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedSummariesListSection(
    savedSummaries: List<SavedSummaryEntry>,
    onDeleteSummary: (String) -> Unit
) {
    if (savedSummaries.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(0.8.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "No saved summaries yet",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Save a 14-day summary snapshot from the Live Summary tab to track block-over-block progress.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        return
    }

    // Progression delta strip when 2+ saved entries exist
    if (savedSummaries.size >= 2) {
        val newest = savedSummaries.first()
        val oldest = savedSummaries.last()
        val scoreDiff = newest.overallScore - oldest.overallScore
        val volDiff = newest.totalVolumeKg - oldest.totalVolumeKg
        val bwDiff = if (newest.endBodyWeightKg != null && oldest.endBodyWeightKg != null) {
            ((newest.endBodyWeightKg - oldest.endBodyWeightKg) * 10.0).roundToInt() / 10.0
        } else null

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = BorderStroke(0.8.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SavedTrendStat("Score trend", "${if (scoreDiff > 0) "+" else ""}$scoreDiff pts", if (scoreDiff >= 0) IncreaseGreen else DecreaseRed)
                val volTrendColor = when {
                    volDiff >= 0 -> IncreaseGreen
                    newest.safeTrainingMode == TrainingMode.CUT -> BlueAccent
                    else -> DecreaseRed
                }
                SavedTrendStat("Volume trend", "${if (volDiff > 0) "+" else ""}${formatTonnage(volDiff)}", volTrendColor)
                SavedTrendStat(
                    "Weight trend",
                    bwDiff?.let { "${if (it > 0) "+" else ""}${formatWeightValue(it)} kg" } ?: "—",
                    TextPrimary
                )
            }
        }
    }

    var expandedId by remember { mutableStateOf<String?>(null) }

    savedSummaries.forEachIndexed { idx, entry ->
        val prev = savedSummaries.getOrNull(idx + 1)
        val scoreDelta = prev?.let { entry.overallScore - it.overallScore }
        val isExpanded = expandedId == entry.id
        val scoreColor = scoreColorFor(entry.overallScore)

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(0.8.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedId = if (isExpanded) null else entry.id }
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title.ifBlank { "${entry.startDate} – ${entry.endDate}" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entry.startDate} – ${entry.endDate} • Saved ${entry.savedAt}",
                            fontSize = 11.sp,
                            color = TextMuted,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrainingModePill(
                            mode = entry.safeTrainingMode,
                            compact = true
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${entry.overallScore}/100",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                            if (scoreDelta != null && scoreDelta != 0) {
                                Text(
                                    text = "${if (scoreDelta > 0) "+" else ""}$scoreDelta vs prev",
                                    fontSize = 10.5.sp,
                                    color = if (scoreDelta > 0) IncreaseGreen else DecreaseRed,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Compact summary strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${entry.workoutsCount} sessions • ${entry.workingSetsCount} sets • ${formatTonnage(entry.totalVolumeKg)}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = entry.endBodyWeightKg?.let { "${formatWeightValue(it)} kg" } ?: "${entry.prsCount} PRs",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }

                if (entry.notes.isNotBlank()) {
                    Text(
                        text = entry.notes,
                        fontSize = 12.sp,
                        color = AccentPeach
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = BorderDark, thickness = 0.6.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vol ${entry.volumeScore}", fontSize = 11.5.sp, color = TextSecondary, style = TextStyle(fontFeatureSettings = "tnum"))
                            Text("Str ${entry.strengthScore}", fontSize = 11.5.sp, color = TextSecondary, style = TextStyle(fontFeatureSettings = "tnum"))
                            Text("Cad ${entry.consistencyScore}", fontSize = 11.5.sp, color = TextSecondary, style = TextStyle(fontFeatureSettings = "tnum"))
                            Text("Bal ${entry.balanceScore}", fontSize = 11.5.sp, color = TextSecondary, style = TextStyle(fontFeatureSettings = "tnum"))
                        }

                        if (entry.safeTopLifts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                entry.safeTopLifts.take(5).forEach { lift ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = lift.exerciseName,
                                            fontSize = 11.5.sp,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${lift.bestSet14d} (${formatWeightValue(lift.bestE1rm14dKg)} kg)",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary,
                                            style = TextStyle(fontFeatureSettings = "tnum")
                                        )
                                    }
                                }
                            }
                        }

                        entry.safeHighlights.forEach { h ->
                            Text(text = "• $h", fontSize = 11.5.sp, color = TextSecondary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onDeleteSummary(entry.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = DecreaseRed),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete Entry", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedTrendStat(label: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

private fun scoreColorFor(score: Int): Color = when {
    score >= 80 -> IncreaseGreen
    score >= 60 -> GoldPR
    score >= 40 -> AccentOrange
    else -> DecreaseRed
}

private fun formatTonnage(kg: Double): String {
    val absKg = kotlin.math.abs(kg)
    return if (absKg >= 1000.0) "${"%.1f".format(kg / 1000.0)}t" else "${formatWeightValue(kg)} kg"
}
