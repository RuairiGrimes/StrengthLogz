package com.strengthlogz.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.SetUtils
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import kotlin.math.roundToInt

enum class ChartMetricMode(val label: String) {
    E1RM("Est. 1RM"),
    SET_REPS("Top Weight"),
    RELATIVE("Rel. ×BW")
}

internal fun SessionProgressPoint.metricValue(mode: ChartMetricMode): Double = when (mode) {
    ChartMetricMode.E1RM -> e1rm
    ChartMetricMode.SET_REPS -> topWeight
    ChartMetricMode.RELATIVE -> relativeStrength
}

internal fun SessionProgressPoint.isPrFor(mode: ChartMetricMode): Boolean = when (mode) {
    ChartMetricMode.E1RM -> isE1rmPr
    ChartMetricMode.SET_REPS -> isWeightPr
    ChartMetricMode.RELATIVE -> false
}

internal fun ChartMetricMode.formatValue(v: Double): String =
    if (this == ChartMetricMode.RELATIVE) "${"%.2f".format(v)}×" else "${formatWeightValue(v)}kg"

private enum class ProgressDetailTab(val label: String) {
    OVERVIEW("Overview"),
    HISTORY("History"),
    PRS("PRs")
}

@Immutable
data class SessionProgressPoint(
    val workoutId: String,
    val displayDate: String,
    val isoDate: String,
    val workoutTitle: String,
    val sets: List<WorkoutSet>,
    val bestWeightSet: WorkoutSet?,
    val bestE1rmSet: WorkoutSet?,
    val topWeight: Double,
    val topWeightReps: Int,
    val e1rm: Double,
    val e1rmWeight: Double,
    val e1rmReps: Int,
    val totalVolume: Double,
    val totalReps: Int,
    val isE1rmPr: Boolean = false,
    val isWeightPr: Boolean = false,
    /** Body weight on this session date (kg); 0 when the exercise is not bodyweight-based. */
    val bodyWeightKg: Double = 0.0,
    /** E1RM (total load) divided by body weight; 0 when not applicable. */
    val relativeStrength: Double = 0.0
)

@Immutable
private data class ExerciseDirectorySummary(
    val sessionCount: Int,
    val lastDisplayDate: String?,
    val bestE1rm: Double,
    val bestRelStrength: Double,
    val latestTopReps: Int
)

private fun roundHalf(v: Double): Double = (v * 2.0).roundToInt() / 2.0
private fun roundTenth(v: Double): Double = (v * 10.0).roundToInt() / 10.0
private fun roundHundredth(v: Double): Double = (v * 100.0).roundToInt() / 100.0

fun computeEpley(weight: Double, reps: Int): Double {
    if (reps <= 0 || weight <= 0.0) return 0.0
    if (reps == 1) return weight
    return roundHalf(weight * (1.0 + reps / 30.0))
}

fun buildSessionPoints(
    sets: List<WorkoutSet>,
    workoutMap: Map<String, Workout>,
    isBodyweight: Boolean = false
): List<SessionProgressPoint> {
    if (sets.isEmpty()) return emptyList()

    val grouped = sets.groupBy { it.workoutId.ifBlank { it.date } }

    val rawPoints = grouped.mapNotNull { (wId, rawSets) ->
        val sortedSets = rawSets.sortedBy { it.setNo }
        val firstSet = sortedSets.first()
        val workout = workoutMap[wId]
        val rawDate = workout?.date?.ifBlank { firstSet.date } ?: firstSet.date
        val displayDate = DateUtils.toDdMonYyyy(rawDate)
        val isoDate = DateUtils.toSortableIso(rawDate)
        val title = workout?.title ?: ""
        val bwKg = if (isBodyweight || BodyWeightUtils.hasEntries()) BodyWeightUtils.weightOn(rawDate) else 0.0

        var bestWeightSet: WorkoutSet? = null
        var bestE1rmSet: WorkoutSet? = null
        var maxWeight = 0.0
        var maxWeightReps = 0
        var maxE1rm = 0.0
        var e1rmSourceWeight = 0.0
        var e1rmSourceReps = 0
        var volume = 0.0
        var totalReps = 0

        for (s in sortedSets) {
            val w = s.weight ?: 0.0
            val r = s.reps ?: 0
            // Strictly ignore failed sets (reps <= 0) for PRs, topWeight, and E1RM
            if (r > 0) {
                totalReps += r
                volume += w * r

                if (w > maxWeight || (w == maxWeight && w > 0.0 && r > maxWeightReps)) {
                    maxWeight = w
                    maxWeightReps = r
                    bestWeightSet = s
                }

                // Bodyweight lifts: E1RM uses total load (body weight + added load)
                val load = BodyWeightUtils.totalLoad(isBodyweight, w, bwKg)
                if (load > 0.0) {
                    val e = computeEpley(load, r)
                    if (e > maxE1rm) {
                        maxE1rm = e
                        e1rmSourceWeight = load
                        e1rmSourceReps = r
                        bestE1rmSet = s
                    }
                }
            }
        }

        if (bestWeightSet == null) {
            bestWeightSet = sortedSets.filter { (it.reps ?: 0) > 0 }.maxByOrNull { it.reps ?: 0 }
            maxWeightReps = bestWeightSet?.reps ?: 0
        }
        if (bestE1rmSet == null) {
            bestE1rmSet = bestWeightSet
        }

        if (totalReps <= 0 && maxWeight <= 0.0 && maxE1rm <= 0.0) {
            return@mapNotNull null
        }

        SessionProgressPoint(
            workoutId = wId,
            displayDate = displayDate,
            isoDate = isoDate,
            workoutTitle = title,
            sets = sortedSets,
            bestWeightSet = bestWeightSet,
            bestE1rmSet = bestE1rmSet,
            topWeight = maxWeight,
            topWeightReps = maxWeightReps,
            e1rm = maxE1rm,
            e1rmWeight = e1rmSourceWeight,
            e1rmReps = e1rmSourceReps,
            totalVolume = roundTenth(volume),
            totalReps = totalReps,
            bodyWeightKg = bwKg,
            relativeStrength = if (bwKg > 0.0) roundHundredth(BodyWeightUtils.relativeStrength(maxE1rm, bwKg)) else 0.0
        )
    }.sortedWith(compareBy<SessionProgressPoint> { it.isoDate }.thenBy { it.workoutId })

    var runningBestE1rm = 0.0
    var runningBestWeight = 0.0
    var runningBestWeightReps = 0

    return rawPoints.map { pt ->
        var e1rmPr = false
        var weightPr = false
        if (pt.e1rm > runningBestE1rm && pt.e1rm > 0.0) {
            e1rmPr = true
            runningBestE1rm = pt.e1rm
        }
        if (pt.topWeight > runningBestWeight && pt.topWeight > 0.0 && pt.topWeightReps > 0) {
            weightPr = true
            runningBestWeight = pt.topWeight
            runningBestWeightReps = pt.topWeightReps
        } else if (pt.topWeight == runningBestWeight && pt.topWeight > 0.0 && pt.topWeightReps > runningBestWeightReps) {
            weightPr = true
            runningBestWeightReps = pt.topWeightReps
        }
        if (e1rmPr || weightPr) pt.copy(isE1rmPr = e1rmPr, isWeightPr = weightPr) else pt
    }
}

/**
 * Two-Step Progress Architecture:
 * Step 1: Clean Exercise Directory (Search, Muscle Filter, Headline PR & E1RM preview).
 * Step 2: Dedicated Exercise Progress Detail Screen with 3 focused sub-tabs (Overview | History | PRs).
 */
@Composable
fun ProgressScreen(
    distinctExercises: List<String>,
    availableExercises: List<Exercise> = emptyList(),
    workouts: List<Workout> = emptyList(),
    getSetsForExercise: (String) -> List<WorkoutSet>,
    goals: List<ExerciseGoal> = emptyList(),
    onNavigateGoals: (String?) -> Unit = {},
    onNavigateBodyWeight: () -> Unit = {},
    initialExercise: String? = null,
    onConsumeInitialExercise: () -> Unit = {}
) {
    var activeExercise by remember {
        mutableStateOf<String?>(initialExercise?.takeIf { it.isNotBlank() })
    }

    LaunchedEffect(initialExercise) {
        if (!initialExercise.isNullOrBlank()) {
            activeExercise = initialExercise
            onConsumeInitialExercise()
        }
    }

    val workoutMap = remember(workouts) { workouts.associateBy { it.id } }
    val exerciseCategoryMap = remember(availableExercises) {
        availableExercises.associate { it.name.lowercase() to it.category }
    }
    val goalByExerciseLower = remember(goals) {
        goals.associateBy { it.exerciseName.trim().lowercase() }
    }

    if (activeExercise == null) {
        ProgressDirectoryView(
            distinctExercises = distinctExercises,
            exerciseCategoryMap = exerciseCategoryMap,
            workoutMap = workoutMap,
            getSetsForExercise = getSetsForExercise,
            goalByExerciseLower = goalByExerciseLower,
            onNavigateGoals = { onNavigateGoals(null) },
            onNavigateBodyWeight = onNavigateBodyWeight,
            onSelectExercise = { exName -> activeExercise = exName }
        )
    } else {
        BackHandler { activeExercise = null }
        ExerciseProgressDetailView(
            exerciseName = activeExercise!!,
            category = exerciseCategoryMap[activeExercise!!.lowercase()] ?: "Other",
            workoutMap = workoutMap,
            getSetsForExercise = getSetsForExercise,
            activeGoal = goalByExerciseLower[activeExercise!!.trim().lowercase()],
            onNavigateGoalsForExercise = { onNavigateGoals(activeExercise) },
            onBack = { activeExercise = null }
        )
    }
}

@Composable
private fun ProgressDirectoryView(
    distinctExercises: List<String>,
    exerciseCategoryMap: Map<String, String>,
    workoutMap: Map<String, Workout>,
    getSetsForExercise: (String) -> List<WorkoutSet>,
    goalByExerciseLower: Map<String, ExerciseGoal> = emptyMap(),
    onNavigateGoals: () -> Unit = {},
    onNavigateBodyWeight: () -> Unit = {},
    onSelectExercise: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core")

    val directorySummaries = remember(distinctExercises, workoutMap) {
        distinctExercises.associateWith { exName ->
            val isBw = BodyWeightUtils.isBodyweightExercise(exName)
            val sets = getSetsForExercise(exName)
            val pts = buildSessionPoints(sets, workoutMap, isBw)
            val latest = pts.lastOrNull()
            ExerciseDirectorySummary(
                sessionCount = pts.size,
                lastDisplayDate = latest?.displayDate,
                bestE1rm = pts.maxOfOrNull { it.e1rm } ?: 0.0,
                bestRelStrength = if (isBw) (pts.maxOfOrNull { it.relativeStrength } ?: 0.0) else 0.0,
                latestTopReps = latest?.topWeightReps ?: 0
            )
        }
    }

    val filteredExercises = remember(distinctExercises, searchQuery, selectedCategory, exerciseCategoryMap) {
        val q = searchQuery.trim()
        distinctExercises.filter { exName ->
            val cat = exerciseCategoryMap[exName.lowercase()] ?: "Other"
            val matchesCat = selectedCategory == "All" || cat.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = q.isEmpty() || exName.contains(q, ignoreCase = true) || cat.contains(q, ignoreCase = true)
            matchesCat && matchesQuery
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
            title = "Progress",
            subtitle = "Select a movement to inspect charts, PRs & session history",
            badgeText = "${distinctExercises.size} lifts",
            action = {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, AccentOrange),
                    modifier = Modifier.clickable { onNavigateBodyWeight() }
                ) {
                    Text(
                        text = "⚖ BW",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentOrange,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = AccentOrange,
                    modifier = Modifier.clickable { onNavigateGoals() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Goals",
                            tint = Color(0xFF140D07),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (goalByExerciseLower.isNotEmpty()) "Goals (${goalByExerciseLower.size})" else "Goals",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF140D07)
                        )
                    }
                }
              }
            }
        )

        Spacer(Modifier.height(14.dp))

        ModernSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search exercises..."
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentOrange else SurfaceCard,
                    border = if (isSelected) null else BorderStroke(0.8.dp, BorderDark),
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(
                        text = cat,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF140D07) else TextSecondary,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            items(filteredExercises, key = { it }) { exName ->
                val isBw = BodyWeightUtils.isBodyweightExercise(exName)
                val cat = exerciseCategoryMap[exName.lowercase()] ?: "Other"
                val summary = directorySummaries[exName]
                val bestE1rm = summary?.bestE1rm ?: 0.0
                val bestRel = summary?.bestRelStrength ?: 0.0
                val sessCount = summary?.sessionCount ?: 0
                val lastDate = summary?.lastDisplayDate
                val latestReps = summary?.latestTopReps ?: 0

                StrengthCard(
                    shape = RoundedCornerShape(14.dp),
                    onClick = { onSelectExercise(exName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = exName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                CategoryPill(category = cat)
                                val exGoal = goalByExerciseLower[exName.trim().lowercase()]
                                if (exGoal != null && exGoal.enabled) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GoldPR.copy(alpha = 0.16f),
                                        border = BorderStroke(0.7.dp, GoldPR.copy(alpha = 0.55f))
                                    ) {
                                        Text(
                                            text = "🎯 GOAL",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GoldPR,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "$sessCount sessions" + (lastDate?.let { " • Last $it" } ?: ""),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                if (bestE1rm > 0.0) {
                                    Text(
                                        text = "${formatWeightValue(bestE1rm)} kg",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldPR
                                    )
                                    Text(
                                        text = if (isBw && bestRel > 0.0) "${"%.2f".format(bestRel)}× BW PR" else "Best E1RM",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                } else {
                                    Text(
                                        text = "$latestReps reps",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseProgressDetailView(
    exerciseName: String,
    category: String,
    workoutMap: Map<String, Workout>,
    getSetsForExercise: (String) -> List<WorkoutSet>,
    activeGoal: ExerciseGoal? = null,
    onNavigateGoalsForExercise: () -> Unit = {},
    onBack: () -> Unit
) {
    val isBwExercise = BodyWeightUtils.isBodyweightExercise(exerciseName)
    val rawSets = remember(exerciseName, workoutMap) { getSetsForExercise(exerciseName) }
    val sessions = remember(rawSets, workoutMap, isBwExercise) {
        buildSessionPoints(rawSets, workoutMap, isBwExercise)
    }

    var activeTab by remember { mutableStateOf(ProgressDetailTab.OVERVIEW) }
    var metricMode by remember { mutableStateOf(ChartMetricMode.E1RM) }
    var showFullChartModal by remember { mutableStateOf(false) }

    var selectedPointIndex by remember(exerciseName, sessions.size) {
        mutableIntStateOf((sessions.size - 1).coerceAtLeast(0))
    }

    val peakE1rmSession = remember(sessions) { sessions.maxByOrNull { it.e1rm } }
    val latestSession = sessions.lastOrNull()
    val prevSession = if (sessions.size >= 2) sessions[sessions.size - 2] else null
    val firstSession = sessions.firstOrNull()
    val reversedSessions = remember(sessions) { sessions.reversed() }
    val hasRelData = remember(sessions) { sessions.any { it.relativeStrength > 0.0 } }
    val prSessions = remember(reversedSessions) {
        reversedSessions.filter { it.isE1rmPr || it.isWeightPr }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // Top Detail Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .background(SurfaceCard, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Exercises",
                        tint = TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exerciseName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CategoryPill(category = category)
                        Text(
                            text = "${sessions.size} sessions",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        if (isBwExercise && (latestSession?.relativeStrength ?: 0.0) > 0.0) {
                            Text(
                                text = "• ${"%.2f".format(latestSession!!.relativeStrength)}× BW",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentPeach
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (activeGoal != null) GoldPR.copy(alpha = 0.18f) else SurfaceCard,
                    border = BorderStroke(1.dp, if (activeGoal != null) GoldPR else AccentOrange),
                    modifier = Modifier.clickable { onNavigateGoalsForExercise() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Exercise Goal",
                            tint = if (activeGoal != null) GoldPR else AccentOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (activeGoal != null) "Goal" else "+ Goal",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (activeGoal != null) GoldPR else AccentOrange
                        )
                    }
                }

                if (sessions.isNotEmpty()) {
                    IconButton(
                        onClick = { showFullChartModal = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(SurfaceCard, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Full-Screen Chart",
                            tint = AccentOrange,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 3 Sub-Tabs: Overview | History | PRs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ProgressDetailTab.entries.forEach { tab ->
                val active = activeTab == tab
                val label = when (tab) {
                    ProgressDetailTab.OVERVIEW -> "Overview"
                    ProgressDetailTab.HISTORY -> "History (${sessions.size})"
                    ProgressDetailTab.PRS -> "PRs (${prSessions.size})"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) AccentOrange else Color.Transparent)
                        .clickable { activeTab = tab }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) Color(0xFF140D07) else TextSecondary,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when (activeTab) {
            ProgressDetailTab.OVERVIEW -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    // Clean 3-Stat KPI Strip
                    item {
                        val bestE1rmVal = peakE1rmSession?.e1rm ?: 0.0
                        val bestSetStr = peakE1rmSession?.bestE1rmSet?.let {
                            if (isBwExercise) "BW+${formatWeightValue(it.weight ?: 0.0)}kg × ${it.reps ?: 0}"
                            else "${formatWeightValue(it.weight)}kg × ${it.reps ?: 0}"
                        } ?: "–"

                        val latestValStr = latestSession?.let {
                            when (metricMode) {
                                ChartMetricMode.E1RM -> "${formatWeightValue(it.e1rm)} kg"
                                ChartMetricMode.RELATIVE -> metricMode.formatValue(it.relativeStrength)
                                ChartMetricMode.SET_REPS -> "${formatWeightValue(it.topWeight)}kg × ${it.topWeightReps}"
                            }
                        } ?: "–"

                        val prevDeltaStr = if (latestSession != null && prevSession != null) {
                            val curr = latestSession.metricValue(metricMode)
                            val prev = prevSession.metricValue(metricMode)
                            val d = if (metricMode == ChartMetricMode.RELATIVE) roundHundredth(curr - prev) else roundTenth(curr - prev)
                            val sign = if (d > 0) "+" else ""
                            if (metricMode == ChartMetricMode.RELATIVE) "$sign${"%.2f".format(d)}× vs prev"
                            else "$sign${formatWeightValue(d)}kg vs prev"
                        } else "Latest session"

                        val gainMode = if (metricMode == ChartMetricMode.RELATIVE) ChartMetricMode.E1RM else metricMode
                        val firstVal = firstSession?.metricValue(gainMode) ?: 0.0
                        val latestVal = latestSession?.metricValue(gainMode) ?: 0.0
                        val totalGain = roundTenth(latestVal - firstVal)
                        val gainSign = if (totalGain >= 0) "+" else ""
                        val bestRel = sessions.maxOfOrNull { it.relativeStrength } ?: 0.0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactKpiCard(
                                label = "ALL-TIME PR",
                                value = if (bestE1rmVal > 0) "${formatWeightValue(bestE1rmVal)} kg" else "–",
                                caption = bestSetStr,
                                valueColor = GoldPR,
                                modifier = Modifier.weight(1f)
                            )
                            CompactKpiCard(
                                label = "LATEST",
                                value = latestValStr,
                                caption = prevDeltaStr,
                                valueColor = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            CompactKpiCard(
                                label = if (isBwExercise && bestRel > 0.0) "REL. STRENGTH" else "TOTAL GAIN",
                                value = if (isBwExercise && bestRel > 0.0) "${"%.2f".format(bestRel)}× BW" else "$gainSign${formatWeightValue(totalGain)} kg",
                                caption = if (isBwExercise && latestSession != null) "BW ${formatWeightValue(latestSession.bodyWeightKg)}kg"
                                else (firstSession?.displayDate?.let { "Since $it" } ?: "–"),
                                valueColor = if (totalGain >= 0) IncreaseGreen else DecreaseRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Main Chart Card with Metric Switcher & Compact Point Header
                    item {
                        StrengthCard(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Progression Curve",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BgDark, RoundedCornerShape(9.dp))
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        ChartMetricMode.entries.filter { it != ChartMetricMode.RELATIVE || hasRelData }.forEach { mode ->
                                            val active = metricMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(if (active) AccentOrange else Color.Transparent)
                                                    .clickable { metricMode = mode }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = mode.label,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (active) Color(0xFF140D07) else TextSecondary,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                if (sessions.isNotEmpty()) {
                                    val safeIdx = selectedPointIndex.coerceIn(0, sessions.size - 1)
                                    val inspected = sessions[safeIdx]

                                    InspectedPointBanner(
                                        point = inspected,
                                        pointIndex = safeIdx,
                                        totalPoints = sessions.size,
                                        metricMode = metricMode,
                                        onPrev = { if (safeIdx > 0) selectedPointIndex = safeIdx - 1 },
                                        onNext = { if (safeIdx < sessions.size - 1) selectedPointIndex = safeIdx + 1 },
                                        onJumpPr = {
                                            val prIdx = if (metricMode == ChartMetricMode.E1RM) {
                                                sessions.indices.maxByOrNull { sessions[it].e1rm } ?: safeIdx
                                            } else if (metricMode == ChartMetricMode.RELATIVE) {
                                                sessions.indices.maxByOrNull { sessions[it].relativeStrength } ?: safeIdx
                                            } else {
                                                sessions.indices.maxWithOrNull(
                                                    compareBy<Int> { sessions[it].topWeight }.thenBy { sessions[it].topWeightReps }
                                                ) ?: safeIdx
                                            }
                                            selectedPointIndex = prIdx
                                        }
                                    )

                                    InteractiveProgressChart(
                                        sessions = sessions,
                                        metricMode = metricMode,
                                        selectedIndex = safeIdx,
                                        onSelectIndex = { selectedPointIndex = it },
                                        chartHeight = 240.dp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No sessions logged yet", color = TextSecondary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ProgressDetailTab.HISTORY -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    itemsIndexed(reversedSessions, key = { _, s -> s.workoutId }) { revIdx, session ->
                        val chronologicalIdx = sessions.size - 1 - revIdx
                        val olderSession = reversedSessions.getOrNull(revIdx + 1)
                        val isSelectedOnChart = chronologicalIdx == selectedPointIndex

                        SessionHistoryRowCard(
                            session = session,
                            olderSession = olderSession,
                            metricMode = metricMode,
                            isHighlighted = isSelectedOnChart,
                            onClick = {
                                selectedPointIndex = chronologicalIdx
                                activeTab = ProgressDetailTab.OVERVIEW
                            }
                        )
                    }
                }
            }

            ProgressDetailTab.PRS -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    if (prSessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No PR milestones recorded yet", color = TextSecondary, fontSize = 13.5.sp)
                            }
                        }
                    } else {
                        items(prSessions, key = { it.workoutId }) { session ->
                            val chronologicalIdx = sessions.indexOfFirst { it.workoutId == session.workoutId }
                            SessionHistoryRowCard(
                                session = session,
                                olderSession = null,
                                metricMode = metricMode,
                                isHighlighted = chronologicalIdx == selectedPointIndex,
                                onClick = {
                                    if (chronologicalIdx >= 0) selectedPointIndex = chronologicalIdx
                                    activeTab = ProgressDetailTab.OVERVIEW
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFullChartModal && sessions.isNotEmpty()) {
        FullScreenChartInspectorDialog(
            exerciseName = exerciseName,
            allSessions = sessions,
            initialMetricMode = metricMode,
            initialSelectedIndex = selectedPointIndex.coerceIn(0, sessions.size - 1),
            onDismiss = { showFullChartModal = false }
        )
    }
}

@Composable
internal fun CompactKpiCard(
    label: String,
    value: String,
    caption: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    StrengthCard(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = caption,
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun InspectedPointBanner(
    point: SessionProgressPoint,
    pointIndex: Int,
    totalPoints: Int,
    metricMode: ChartMetricMode,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpPr: () -> Unit
) {
    val isPr = point.isPrFor(metricMode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgDark)
            .border(
                width = 1.dp,
                color = if (isPr) GoldPR.copy(alpha = 0.55f) else BorderDark,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = point.displayDate,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentPeach
                )
                if (isPr) {
                    PrTrophyBadge(text = "PR", isNewPr = true)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPrev, enabled = pointIndex > 0, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Older", tint = if (pointIndex > 0) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                }
                TextButton(
                    onClick = onJumpPr,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldPR, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Peak", fontSize = 11.sp, color = GoldPR, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onNext, enabled = pointIndex < totalPoints - 1, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Newer", tint = if (pointIndex < totalPoints - 1) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (point.e1rm > 0) "E1RM ${formatWeightValue(point.e1rm)} kg (${formatWeightValue(point.e1rmWeight)}×${point.e1rmReps})"
                else "Top: ${point.topWeightReps} reps",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = SetUtils.formatSetsSummary(point.sets),
                fontSize = 11.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun InteractiveProgressChart(
    sessions: List<SessionProgressPoint>,
    metricMode: ChartMetricMode,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    chartHeight: Dp = 230.dp
) {
    val values = remember(sessions, metricMode) {
        sessions.map { it.metricValue(metricMode).toFloat() }
    }

    val prFlags = remember(sessions, metricMode) {
        sessions.map { it.isPrFor(metricMode) }
    }

    val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val minVal = (values.minOrNull() ?: 0f).coerceAtLeast(0f)
    val span = (maxVal - minVal).coerceAtLeast(2f)

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#949EB2")
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val prLabelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#FFD166")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .pointerInput(sessions.size) {
                detectTapGestures { offset ->
                    val leftPad = 80f
                    val rightPad = 24f
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    if (sessions.size <= 1) {
                        onSelectIndex(0)
                    } else {
                        val stepX = plotW / (sessions.size - 1)
                        val rawIdx = ((offset.x - leftPad) / stepX).roundToInt()
                        onSelectIndex(rawIdx.coerceIn(0, sessions.size - 1))
                    }
                }
            }
            .pointerInput(sessions.size) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val leftPad = 80f
                    val rightPad = 24f
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    if (sessions.size <= 1) {
                        onSelectIndex(0)
                    } else {
                        val stepX = plotW / (sessions.size - 1)
                        val rawIdx = ((change.position.x - leftPad) / stepX).roundToInt()
                        onSelectIndex(rawIdx.coerceIn(0, sessions.size - 1))
                    }
                }
            }
    ) {
        val leftPad = 80f
        val rightPad = 24f
        val topPad = 28f
        val bottomPad = 44f

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val gridSteps = 3
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps
            val y = topPad + plotH * (1f - frac)
            val labelVal = minVal + span * frac

            drawLine(
                color = BorderDark.copy(alpha = 0.7f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.2f
            )

            drawContext.canvas.nativeCanvas.drawText(
                if (metricMode == ChartMetricMode.RELATIVE) "${"%.2f".format(labelVal)}×" else "${formatWeightValue(roundHalf(labelVal.toDouble()))}kg",
                6f,
                y + 8f,
                textPaint
            )
        }

        val stepX = if (sessions.size > 1) plotW / (sessions.size - 1) else 0f
        val points = values.mapIndexed { idx, v ->
            val x = if (sessions.size > 1) leftPad + idx * stepX else leftPad + plotW / 2f
            val norm = ((v - minVal) / span).coerceIn(0f, 1f)
            val y = topPad + plotH * (1f - norm)
            Offset(x, y)
        }

        val peakY = topPad
        drawLine(
            color = GoldPR.copy(alpha = 0.4f),
            start = Offset(leftPad, peakY),
            end = Offset(leftPad + plotW, peakY),
            strokeWidth = 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )

        if (points.size >= 2) {
            val fillPath = Path().apply {
                moveTo(points.first().x, topPad + plotH)
                points.forEach { p -> lineTo(p.x, p.y) }
                lineTo(points.last().x, topPad + plotH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AccentOrange.copy(alpha = 0.28f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val v0 = values[i]
                val v1 = values[i + 1]
                val segColor = when {
                    v1 > v0 -> IncreaseGreen
                    v1 < v0 -> DecreaseRed
                    else -> AccentOrange
                }
                drawLine(
                    color = segColor,
                    start = p0,
                    end = p1,
                    strokeWidth = 4.2f,
                    cap = StrokeCap.Round
                )
            }
        }

        if (sessions.isNotEmpty()) {
            val firstDate = sessions.first().displayDate
            drawContext.canvas.nativeCanvas.drawText(
                firstDate,
                leftPad,
                size.height - 8f,
                textPaint
            )

            if (sessions.size >= 3) {
                val midIdx = sessions.size / 2
                val midDate = sessions[midIdx].displayDate
                val midW = textPaint.measureText(midDate)
                drawContext.canvas.nativeCanvas.drawText(
                    midDate,
                    points[midIdx].x - midW / 2f,
                    size.height - 8f,
                    textPaint
                )
            }

            if (sessions.size >= 2) {
                val lastDate = sessions.last().displayDate
                val lastW = textPaint.measureText(lastDate)
                drawContext.canvas.nativeCanvas.drawText(
                    lastDate,
                    (leftPad + plotW - lastW).coerceAtLeast(leftPad),
                    size.height - 8f,
                    textPaint
                )
            }
        }

        val safeSelected = selectedIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0))
        if (points.isNotEmpty()) {
            val selPt = points[safeSelected]
            drawLine(
                color = AccentOrange.copy(alpha = 0.85f),
                start = Offset(selPt.x, topPad),
                end = Offset(selPt.x, topPad + plotH),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }

        points.forEachIndexed { idx, pt ->
            val isPr = prFlags[idx]
            val isSelected = idx == safeSelected

            if (isSelected) {
                drawCircle(
                    color = (if (isPr) GoldPR else AccentOrange).copy(alpha = 0.28f),
                    radius = 16f,
                    center = pt
                )
            }

            if (isPr) {
                drawCircle(color = SurfaceDark, radius = 9f, center = pt)
                drawCircle(color = GoldPR, radius = 7f, center = pt)
            } else {
                drawCircle(color = SurfaceDark, radius = 6.5f, center = pt)
                drawCircle(color = if (isSelected) AccentOrange else TextPrimary, radius = 4.5f, center = pt)
            }

            if (isSelected) {
                val sess = sessions[idx]
                val callout = if (metricMode == ChartMetricMode.RELATIVE) {
                    metricMode.formatValue(sess.relativeStrength)
                } else if (metricMode == ChartMetricMode.E1RM) {
                    "${formatWeightValue(sess.e1rm)}kg"
                } else {
                    "${formatWeightValue(sess.topWeight)}kg × ${sess.topWeightReps}"
                }
                val labelW = prLabelPaint.measureText(callout)
                val labelX = (pt.x - labelW / 2f).coerceIn(leftPad, (leftPad + plotW - labelW).coerceAtLeast(leftPad))
                val labelY = (pt.y - 18f).coerceAtLeast(22f)
                drawContext.canvas.nativeCanvas.drawText(callout, labelX, labelY, prLabelPaint)
            }
        }
    }
}

@Composable
private fun SessionHistoryRowCard(
    session: SessionProgressPoint,
    olderSession: SessionProgressPoint?,
    metricMode: ChartMetricMode,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val currVal = session.metricValue(metricMode)
    val prevVal = olderSession?.metricValue(metricMode)
    val delta = if (prevVal != null && prevVal > 0) {
        if (metricMode == ChartMetricMode.RELATIVE) roundHundredth(currVal - prevVal) else roundTenth(currVal - prevVal)
    } else null

    val setNotes = remember(session) {
        session.sets.mapNotNull { it.notes.takeIf { n -> n.isNotBlank() } }.distinct().joinToString("; ")
    }

    StrengthCard(
        shape = RoundedCornerShape(14.dp),
        border = if (isHighlighted) BorderStroke(1.5.dp, AccentOrange) else BorderStroke(0.8.dp, BorderDark.copy(alpha = 0.65f)),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = session.displayDate,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPeach
                    )
                    if (session.workoutTitle.isNotBlank()) {
                        Text(
                            text = session.workoutTitle,
                            fontSize = 12.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (session.isE1rmPr || session.isWeightPr) {
                        PrTrophyBadge(
                            text = if (session.isE1rmPr) "E1RM PR" else "WT PR",
                            isNewPr = true
                        )
                    }
                    if (delta != null && delta != 0.0) {
                        val sign = if (delta > 0) "+" else ""
                        Text(
                            text = if (metricMode == ChartMetricMode.RELATIVE) "$sign${"%.2f".format(delta)}×" else "$sign${formatWeightValue(delta)}kg",
                            color = if (delta > 0) IncreaseGreen else DecreaseRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SetUtils.formatSetsSummary(session.sets),
                    fontSize = 12.5.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (session.e1rm > 0) {
                    Text(
                        text = "E1RM ${formatWeightValue(session.e1rm)}kg",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (setNotes.isNotBlank()) {
                Text(
                    text = "“$setNotes”",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun FullScreenChartInspectorDialog(
    exerciseName: String,
    allSessions: List<SessionProgressPoint>,
    initialMetricMode: ChartMetricMode,
    initialSelectedIndex: Int,
    onDismiss: () -> Unit
) {
    var metricMode by remember { mutableStateOf(initialMetricMode) }
    var prOnlyFilter by remember { mutableStateOf(false) }

    val displayedSessions = remember(allSessions, prOnlyFilter, metricMode) {
        if (!prOnlyFilter) allSessions
        else allSessions.filter { it.isPrFor(metricMode) }.ifEmpty { allSessions }
    }

    var selectedIdx by remember(displayedSessions) {
        mutableIntStateOf((displayedSessions.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        if (!prOnlyFilter && initialSelectedIndex in displayedSessions.indices) {
            selectedIdx = initialSelectedIndex
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BgDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exerciseName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${displayedSessions.size} sessions",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(SurfaceDark, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dialogHasRel = allSessions.any { it.relativeStrength > 0.0 }
                    ChartMetricMode.entries.filter { it != ChartMetricMode.RELATIVE || dialogHasRel }.forEach { mode ->
                        val active = metricMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) AccentOrange else SurfaceCard,
                            border = if (active) null else BorderStroke(1.dp, BorderDark),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { metricMode = mode }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (active) Color(0xFF140D07) else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (prOnlyFilter) GoldPR else SurfaceCard,
                        border = if (prOnlyFilter) null else BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.clickable { prOnlyFilter = !prOnlyFilter }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PRs Only",
                                color = if (prOnlyFilter) Color(0xFF140D07) else GoldPR,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }

                val safeIdx = selectedIdx.coerceIn(0, (displayedSessions.size - 1).coerceAtLeast(0))
                val currentPoint = displayedSessions[safeIdx]

                InspectedPointBanner(
                    point = currentPoint,
                    pointIndex = safeIdx,
                    totalPoints = displayedSessions.size,
                    metricMode = metricMode,
                    onPrev = { if (safeIdx > 0) selectedIdx = safeIdx - 1 },
                    onNext = { if (safeIdx < displayedSessions.size - 1) selectedIdx = safeIdx + 1 },
                    onJumpPr = {
                        val prIdx = if (metricMode == ChartMetricMode.E1RM) {
                            displayedSessions.indices.maxByOrNull { displayedSessions[it].e1rm } ?: safeIdx
                        } else if (metricMode == ChartMetricMode.RELATIVE) {
                            displayedSessions.indices.maxByOrNull { displayedSessions[it].relativeStrength } ?: safeIdx
                        } else {
                            displayedSessions.indices.maxWithOrNull(
                                compareBy<Int> { displayedSessions[it].topWeight }.thenBy { displayedSessions[it].topWeightReps }
                            ) ?: safeIdx
                        }
                        selectedIdx = prIdx
                    }
                )

                StrengthCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(6.dp)) {
                        InteractiveProgressChart(
                            sessions = displayedSessions,
                            metricMode = metricMode,
                            selectedIndex = safeIdx,
                            onSelectIndex = { selectedIdx = it },
                            chartHeight = 290.dp
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val revList = displayedSessions.reversed()
                    itemsIndexed(revList, key = { _, p -> p.workoutId }) { rIdx, pt ->
                        val origIdx = displayedSessions.size - 1 - rIdx
                        val older = revList.getOrNull(rIdx + 1)
                        SessionHistoryRowCard(
                            session = pt,
                            olderSession = older,
                            metricMode = metricMode,
                            isHighlighted = origIdx == safeIdx,
                            onClick = { selectedIdx = origIdx }
                        )
                    }
                }
            }
        }
    }
}
