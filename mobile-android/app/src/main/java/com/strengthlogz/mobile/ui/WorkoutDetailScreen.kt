package com.strengthlogz.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.SetUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutDetailScreen(
    workout: Workout,
    allWorkouts: List<Workout>,
    getSetsForWorkout: (String) -> List<WorkoutSet>,
    getSetsForExercise: (String) -> List<WorkoutSet>,
    availableExercises: List<Exercise>,
    onBack: () -> Unit,
    onNavigateToProgress: (String) -> Unit = {},
    onDeleteWorkout: (String) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val expandedExercises = remember { mutableStateMapOf<String, Boolean>() }

    val workoutSets = remember(workout.id) { getSetsForWorkout(workout.id) }
    val groupedByExercise = remember(workoutSets) { workoutSets.groupBy { it.exercise } }
    val workoutMap = remember(allWorkouts) { allWorkouts.associateBy { it.id } }
    val exMap = remember(availableExercises) { availableExercises.associateBy { it.name.lowercase() } }

    val totalVolume = workoutSets.filter { (it.reps ?: 0) > 0 }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
    val totalWorkingSets = workoutSets.count { (it.reps ?: 0) > 0 }
    val totalReps = workoutSets.filter { (it.reps ?: 0) > 0 }.sumOf { it.reps ?: 0 }

    val prExercises = remember(groupedByExercise, workout.id) {
        groupedByExercise.keys.filter { exName ->
            val allExSets = getSetsForExercise(exName)
            val pts = buildSessionPoints(allExSets, workoutMap, BodyWeightUtils.isBodyweightExercise(exName))
            val thisPt = pts.find { it.workoutId == workout.id }
            thisPt != null && (thisPt.isE1rmPr || thisPt.isWeightPr)
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = workout.title.ifBlank { "Workout Session" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = DateUtils.formatDisplayWithDay(workout.date),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Workout",
                            tint = DecreaseRed.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            if (workout.notes.isNotBlank()) {
                item {
                    StrengthCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = workout.notes,
                            fontSize = 12.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Sleek 3-Column Summary Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrengthCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Volume", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${SetUtils.formatWeightValue(totalVolume)} kg",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = TextPrimary
                        )
                        Text(
                            text = "${groupedByExercise.size} movements",
                            fontSize = 11.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = TextSecondary
                        )
                    }

                    StrengthCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sets & reps", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$totalWorkingSets sets",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = TextPrimary
                        )
                        Text(
                            text = "$totalReps reps",
                            fontSize = 11.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = TextSecondary
                        )
                    }

                    StrengthCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Records", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (prExercises.isNotEmpty()) "${prExercises.size} PR${if (prExercises.size > 1) "s" else ""}" else "–",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = if (prExercises.isNotEmpty()) GoldPR else TextSecondary
                        )
                        Text(
                            text = if (prExercises.isNotEmpty()) prExercises.first() else "Steady work",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Collapsible Per-Exercise Cards
            items(groupedByExercise.entries.toList(), key = { it.key }) { (exerciseName, exSets) ->
                val exInfo = exMap[exerciseName.lowercase()]
                val isBw = exInfo?.bodyweight == true || exInfo?.category.equals("Bodyweight", ignoreCase = true)
                val isExpanded = expandedExercises[exerciseName] == true

                val allExSets = remember(exerciseName) { getSetsForExercise(exerciseName) }
                val sessions = remember(allExSets) {
                    buildSessionPoints(allExSets, workoutMap, BodyWeightUtils.isBodyweightExercise(exerciseName))
                }
                val currentSessIdx = sessions.indexOfFirst { it.workoutId == workout.id }
                val currentSess = if (currentSessIdx >= 0) sessions[currentSessIdx] else null
                val prevSess = if (currentSessIdx > 0) sessions[currentSessIdx - 1] else null
                val isPr = currentSess != null && (currentSess.isE1rmPr || currentSess.isWeightPr)
                val e1rmDelta = if (currentSess != null && prevSess != null && prevSess.e1rm > 0) {
                    ((currentSess.e1rm - prevSess.e1rm) * 10.0).roundToInt() / 10.0
                } else null

                val setsSummary = SetUtils.formatSetsSummary(exSets, isBw)

                StrengthCard(
                    shape = RoundedCornerShape(12.dp),
                    onClick = { expandedExercises[exerciseName] = !isExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = exerciseName,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    CategoryPill(category = exInfo?.category ?: "Other")
                                    if (isPr) {
                                        PrTrophyBadge(text = "PR", isNewPr = true)
                                    }
                                }
                                Text(
                                    text = setsSummary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentSess != null && currentSess.e1rm > 0) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${SetUtils.formatWeightValue(currentSess.e1rm)} kg",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = TextPrimary
                                        )
                                        if (e1rmDelta != null && e1rmDelta != 0.0) {
                                            val sign = if (e1rmDelta > 0) "+" else ""
                                            Text(
                                                text = "$sign${SetUtils.formatWeightValue(e1rmDelta)}kg",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                style = TextStyle(fontFeatureSettings = "tnum"),
                                                color = if (e1rmDelta > 0) IncreaseGreen else DecreaseRed
                                            )
                                        } else {
                                            Text("Est. 1RM", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isExpanded) {
                            HorizontalDivider(color = BorderDark, thickness = 1.dp)

                            val exNote = exSets.firstOrNull { it.notes.isNotBlank() }?.notes?.trim().orEmpty()
                            if (exNote.isNotBlank()) {
                                Surface(
                                    color = BgDark,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Note: $exNote",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            if (prevSess != null) {
                                Text(
                                    text = "Previous (${prevSess.displayDate}): ${SetUtils.formatSetsSummary(prevSess.sets, isBw)}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = TextSecondary
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Set", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary, modifier = Modifier.width(36.dp))
                                    Text("Weight", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary, modifier = Modifier.weight(1f))
                                    Text("Reps", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary, modifier = Modifier.weight(0.9f))
                                    Text("Est. 1RM", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary, modifier = Modifier.width(68.dp))
                                }

                                exSets.sortedBy { it.setNo }.forEachIndexed { idx, s ->
                                    val wVal = s.weight ?: 0.0
                                    val rVal = s.reps ?: 0
                                    val totalLoadKg = BodyWeightUtils.totalLoad(
                                        BodyWeightUtils.isBodyweightExercise(exerciseName),
                                        wVal,
                                        BodyWeightUtils.weightOn(s.date)
                                    )
                                    val e1rm = if (totalLoadKg > 0 && rVal > 0) computeEpley(totalLoadKg, rVal) else 0.0

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = TextSecondary,
                                            modifier = Modifier.width(36.dp)
                                        )
                                        Text(
                                            text = if (s.weight != null && s.weight > 0) "${SetUtils.formatWeightValue(s.weight)} kg" else "BW",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (rVal > 0) "$rVal" else "0 (fail)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = if (rVal > 0) TextPrimary else DecreaseRed,
                                            modifier = Modifier.weight(0.9f)
                                        )
                                        Text(
                                            text = if (e1rm > 0) "${SetUtils.formatWeightValue(e1rm)} kg" else "–",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = TextSecondary,
                                            modifier = Modifier.width(68.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { onNavigateToProgress(exerciseName) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("View Progress Chart", fontSize = 12.sp, color = AccentOrange, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Delete Workout?", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = {
                Text("Delete workout on ${DateUtils.toDdMonYyyy(workout.date)}? This cannot be undone.", color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWorkout(workout.id)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DecreaseRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
