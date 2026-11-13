package com.strengthlogz.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.SetUtils
import kotlinx.coroutines.launch

@Immutable
private data class HistoryCardModel(
    val workout: Workout,
    val displayDate: String,
    val displayDateWithDay: String,
    val sessionVolume: Double,
    val sessionVolumeFormatted: String,
    val workingSetsCount: Int,
    val liftCount: Int,
    val topExerciseLines: List<Pair<String, String>>,
    val extraLiftCount: Int,
    val exerciseNamesLower: List<String>
)

@Composable
private fun SyncSpinIcon(isSyncing: Boolean, modifier: Modifier = Modifier) {
    if (isSyncing) {
        val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
        val spinAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing)
            ),
            label = "spin"
        )
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Sync",
            tint = AccentOrange,
            modifier = modifier.rotate(spinAngle)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Sync",
            tint = AccentOrange,
            modifier = modifier
        )
    }
}

@Composable
fun HistoryScreen(
    workouts: List<Workout>,
    getSetsForWorkout: (String) -> List<WorkoutSet>,
    onDeleteWorkout: (String) -> Unit,
    onQuickSync: suspend () -> Result<String>,
    onSelectWorkout: (Workout) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var syncToast by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val cardModels = remember(workouts) {
        workouts.map { w ->
            val sets = getSetsForWorkout(w.id)
            val grouped = sets.groupBy { it.exercise }
            val volume = sets.filter { (it.reps ?: 0) > 0 }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
            val workingCount = sets.count { (it.reps ?: 0) > 0 }
            val topLines = grouped.entries.take(4).map { (exName, exSets) ->
                exName to SetUtils.formatSetsSummary(exSets.sortedBy { it.setNo })
            }
            HistoryCardModel(
                workout = w,
                displayDate = DateUtils.toDdMonYyyy(w.date),
                displayDateWithDay = DateUtils.formatDisplayWithDay(w.date),
                sessionVolume = volume,
                sessionVolumeFormatted = if (volume > 0) "${SetUtils.formatWeightValue(volume)} kg" else "",
                workingSetsCount = workingCount,
                liftCount = grouped.size,
                topExerciseLines = topLines,
                extraLiftCount = (grouped.size - 4).coerceAtLeast(0),
                exerciseNamesLower = grouped.keys.map { it.lowercase() }
            )
        }
    }

    val filteredModels = remember(cardModels, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            cardModels
        } else {
            val qLower = q.lowercase()
            cardModels.filter { m ->
                m.workout.title.contains(q, ignoreCase = true) ||
                    m.workout.notes.contains(q, ignoreCase = true) ||
                    m.displayDate.contains(q, ignoreCase = true) ||
                    m.displayDateWithDay.contains(q, ignoreCase = true) ||
                    m.workout.date.contains(q, ignoreCase = true) ||
                    m.exerciseNamesLower.any { it.contains(qLower) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        ModernScreenHeader(
            title = "History",
            badgeText = "${workouts.size} sessions",
            action = {
                OutlinedButton(
                    onClick = {
                        if (!isSyncing) {
                            scope.launch {
                                isSyncing = true
                                syncToast = "Syncing..."
                                val res = onQuickSync()
                                isSyncing = false
                                syncToast = if (res.isSuccess) {
                                    res.getOrNull() ?: "Synced!"
                                } else {
                                    "Sync failed: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    },
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.8.dp, BorderDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    SyncSpinIcon(isSyncing = isSyncing, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSyncing) "Syncing" else "Sync", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        )

        AnimatedVisibility(visible = syncToast.isNotBlank()) {
            Surface(
                color = if (syncToast.startsWith("Sync failed")) DecreaseRed.copy(alpha = 0.15f) else IncreaseGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (syncToast.startsWith("Sync failed")) DecreaseRed.copy(alpha = 0.4f) else IncreaseGreen.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = syncToast,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (syncToast.startsWith("Sync failed")) DecreaseRed else IncreaseGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        ModernSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search date, split, or exercise..."
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            items(filteredModels, key = { it.workout.id }) { model ->
                val workout = model.workout

                StrengthCard(
                    shape = RoundedCornerShape(16.dp),
                    onClick = { onSelectWorkout(workout) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = workout.title.ifBlank { "Workout Session" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = model.displayDateWithDay,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentPeach,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (model.sessionVolume > 0) {
                                Text(
                                    text = model.sessionVolumeFormatted,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "${model.workingSetsCount} sets • ${model.liftCount} lifts",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (workout.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "“${workout.notes}”",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = BorderDark.copy(alpha = 0.6f), thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        model.topExerciseLines.forEach { (exerciseName, setsSummary) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = exerciseName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.42f)
                                )
                                Text(
                                    text = setsSummary,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.End,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(0.58f)
                                        .padding(start = 10.dp)
                                )
                            }
                        }

                        if (model.extraLiftCount > 0) {
                            Text(
                                text = "+ ${model.extraLiftCount} more",
                                fontSize = 11.5.sp,
                                color = AccentPeach,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
