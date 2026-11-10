package com.strengthlogz.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util. ExerciseGoalReport
import com.strengthlogz.mobile.util.GoalEngine
import com.strengthlogz.mobile.util.GoalEvaluation
import com.strengthlogz.mobile.util.GoalStatus
import com.strengthlogz.mobile.util.LiveSessionGoalAnalysis
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

private enum class GoalFilterTab(val label: String) {
    ALL("All Goals"),
    ACTIVE("In Progress"),
    ACHIEVED("Achieved 🏆")
}

@Composable
fun GoalsScreen(
    goals: List<ExerciseGoal>,
    workouts: List<Workout>,
    allSets: List<WorkoutSet>,
    availableExercises: List<Exercise>,
    bodyWeights: List<BodyWeightEntry>,
    getSetsForExercise: (String) -> List<WorkoutSet>,
    initialExerciseForGoal: String? = null,
    onConsumeInitialExercise: () -> Unit = {},
    onSaveGoal: (ExerciseGoal) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onToggleGoalEnabled: (String) -> Unit,
    onStartGoalSessionInLogger: (SuggestedWorkoutPlan) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var filterTab by remember { mutableStateOf(GoalFilterTab.ALL) }
    var showCreateEditDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<ExerciseGoal?>(null) }
    var preselectedExerciseName by remember { mutableStateOf<String?>(null) }

    var reportGoalTarget by remember { mutableStateOf<ExerciseGoal?>(null) }
    var showAllGoalsMasterReport by remember { mutableStateOf(false) }
    var expandedGoalIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // If navigated from a specific exercise on ProgressScreen, open create/inspect for that exercise
    LaunchedEffect(initialExerciseForGoal) {
        if (!initialExerciseForGoal.isNullOrBlank()) {
            val existing = goals.firstOrNull { it.exerciseName.equals(initialExerciseForGoal, ignoreCase = true) }
            if (existing != null) {
                expandedGoalIds = expandedGoalIds + existing.id
            } else {
                preselectedExerciseName = initialExerciseForGoal
                editingGoal = null
                showCreateEditDialog = true
            }
            onConsumeInitialExercise()
        }
    }

    val evaluations = remember(goals, workouts, allSets, availableExercises, bodyWeights) {
        goals.map { g ->
            GoalEngine.evaluateGoal(
                goal = g,
                workouts = workouts,
                exerciseSets = getSetsForExercise(g.exerciseName),
                allSets = allSets,
                exercisesCatalog = availableExercises,
                bodyWeights = bodyWeights
            )
        }
    }

    val filteredEvaluations = remember(evaluations, filterTab) {
        when (filterTab) {
            GoalFilterTab.ALL -> evaluations
            GoalFilterTab.ACTIVE -> evaluations.filter { !it.isAchieved && it.goal.enabled }
            GoalFilterTab.ACHIEVED -> evaluations.filter { it.isAchieved }
        }
    }

    val latestBwKg = remember(bodyWeights) {
        if (bodyWeights.isNotEmpty()) BodyWeightUtils.weightOn(DateUtils.todayDdMonYyyy(), bodyWeights)
        else BodyWeightUtils.latestKg()
    }

    val achievedCount = remember(evaluations) { evaluations.count { it.isAchieved } }
    val avgCompletion = remember(evaluations) {
        if (evaluations.isNotEmpty()) {
            (evaluations.map { it.progressPct }.average() * 10.0).roundToInt() / 10.0
        } else 0.0
    }

    // Single unified scrollable screen
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Clean, uncluttered header with action buttons below title
        item(key = "goals_header") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(34.dp)
                            .background(SurfaceCard, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "Strength Goals",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (goals.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clickable { showAllGoalsMasterReport = true }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = "Reports",
                                    tint = GoldPR,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "All Reports",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentOrange,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable {
                                editingGoal = null
                                preselectedExerciseName = null
                                showCreateEditDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Goal",
                                tint = Color(0xFF140D07),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "New Goal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF140D07)
                            )
                        }
                    }
                }
            }
        }

        // 2. Filter Tabs
        item(key = "goals_filter_tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GoalFilterTab.entries.forEach { tab ->
                    val selected = filterTab == tab
                    val label = when (tab) {
                        GoalFilterTab.ALL -> "All (${evaluations.size})"
                        GoalFilterTab.ACTIVE -> "In Progress (${evaluations.count { !it.isAchieved && it.goal.enabled }})"
                        GoalFilterTab.ACHIEVED -> "Achieved (${evaluations.count { it.isAchieved }})"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AccentOrange else Color.Transparent)
                            .clickable { filterTab = tab }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (selected) Color(0xFF140D07) else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 4. Goals Content (Empty State or Goal Cards — all part of the same single scrollable page)
        if (filteredEvaluations.isEmpty()) {
            item(key = "goals_empty_card") {
                StrengthCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentOrange.copy(alpha = 0.16f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = GoldPR,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Text(
                            text = if (goals.isEmpty()) "Set Your First Strength Goal" else "No Goals in This Filter",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Select any exercise and choose a 1RM target or a Sets × Reps target. StrengthLogz will analyze your current strength, body weight (${formatWeightValue(latestBwKg)}kg), and session history to build a custom progression roadmap.",
                            fontSize = 12.5.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(2.dp))
                        Button(
                            onClick = {
                                editingGoal = null
                                preselectedExerciseName = null
                                showCreateEditDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentOrange,
                                contentColor = Color(0xFF140D07)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Exercise Goal", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            item(key = "quick_start_header") {
                Text(
                    text = "QUICK-START GOALS FROM YOUR HISTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            val topExercises = listOf("Bench Press", "Squat", "Deadlift", "Overhead Press", "Pull Ups", "Dips")
            items(topExercises, key = { "quickstart_$it" }) { exName ->
                val exSets = getSetsForExercise(exName)
                val isBw = BodyWeightUtils.isBodyweightExercise(exName)
                val pts = buildSessionPoints(exSets, workouts.associateBy { it.id }, isBw)
                val bestE1rm = pts.maxOfOrNull { it.e1rm } ?: 0.0
                val suggestedTarget = if (bestE1rm > 0.0) GoalEngine.roundToGymLoad(bestE1rm * 1.075, exName) else 100.0

                StrengthCard(
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        preselectedExerciseName = exName
                        editingGoal = null
                        showCreateEditDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = exName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (bestE1rm > 0.0)
                                    "Current Best E1RM: ${formatWeightValue(bestE1rm)}kg • Target: ${formatWeightValue(suggestedTarget)}kg"
                                else "Tap to configure 1RM or Sets × Reps target",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentOrange.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, AccentOrange)
                        ) {
                            Text(
                                text = "+ Set Goal",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredEvaluations, key = { it.goal.id }) { eval ->
                val isExpanded = eval.goal.id in expandedGoalIds
                GoalProgrammingCard(
                    evaluation = eval,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedGoalIds = if (isExpanded) expandedGoalIds - eval.goal.id else expandedGoalIds + eval.goal.id
                    },
                    onOpenReport = { reportGoalTarget = eval.goal },
                    onEditGoal = {
                        editingGoal = eval.goal
                        preselectedExerciseName = eval.goal.exerciseName
                        showCreateEditDialog = true
                    },
                    onDeleteGoal = { onDeleteGoal(eval.goal.id) },
                    onToggleEnabled = { onToggleGoalEnabled(eval.goal.id) },
                    onTrainGoalNow = {
                        val plan = SuggestedWorkoutPlan(
                            split = WorkoutSplitType.FULL_BODY,
                            title = "${eval.goal.exerciseName} Goal Session",
                            notes = "Goal Target: ${
                                if (eval.goal.isSetsRepsGoal) "${eval.goal.targetSets}×${eval.goal.targetReps} @ ${formatWeightValue(eval.goal.targetWeightKg)}kg"
                                else "${formatWeightValue(eval.effectiveTargetE1rm)}kg Est. 1RM"
                            } • ${eval.methodologyTitle}",
                            exercises = listOf(
                                SuggestedExerciseItem(
                                    slotTitle = "Primary Goal Movement",
                                    exerciseName = eval.goal.exerciseName,
                                    category = eval.category,
                                    lastSessionDate = eval.sessionHistory.lastOrNull()?.displayDate ?: "First time",
                                    lastSummary = eval.bestSetSummary,
                                    peakE1rm = eval.currentBestE1rm,
                                    currentE1rm = eval.recentBestE1rm,
                                    reasonTag = eval.methodologyTitle,
                                    targetSummary = eval.nextSessionSummary,
                                    prescribedSets = eval.nextSessionSets.map { ps ->
                                        PrescribedSet(
                                            weight = formatWeightValue(ps.weightKg),
                                            reps = ps.reps.toString(),
                                            notes = "${ps.role} (${ps.rpe})"
                                        )
                                    }
                                )
                            )
                        )
                        onStartGoalSessionInLogger(plan)
                    }
                )
            }
        }
    }

    // Create / Edit Goal Dialog
    if (showCreateEditDialog) {
        CreateOrEditGoalDialog(
            existingGoal = editingGoal,
            initialExerciseName = preselectedExerciseName,
            availableExercises = availableExercises,
            workouts = workouts,
            getSetsForExercise = getSetsForExercise,
            currentBodyWeightKg = latestBwKg,
            onDismiss = { showCreateEditDialog = false },
            onSave = { newGoal ->
                onSaveGoal(newGoal)
                expandedGoalIds = expandedGoalIds + newGoal.id
                showCreateEditDialog = false
            }
        )
    }

    // Single-Exercise Goal Report Dialog
    reportGoalTarget?.let { targetGoal ->
        val report = remember(targetGoal, workouts, allSets, availableExercises, bodyWeights) {
            GoalEngine.generateExerciseReport(
                goal = targetGoal,
                workouts = workouts,
                exerciseSets = getSetsForExercise(targetGoal.exerciseName),
                allSets = allSets,
                exercisesCatalog = availableExercises,
                bodyWeights = bodyWeights
            )
        }
        ExerciseGoalReportDialog(
            report = report,
            onDismiss = { reportGoalTarget = null }
        )
    }

    // Master All-Goals Report Dialog
    if (showAllGoalsMasterReport) {
        val masterReport = remember(goals, workouts, allSets, availableExercises, bodyWeights) {
            GoalEngine.generateAllGoalsReport(
                goals = goals,
                workouts = workouts,
                allSets = allSets,
                exercisesCatalog = availableExercises,
                bodyWeights = bodyWeights
            )
        }
        AllGoalsReportDialog(
            masterReport = masterReport,
            onInspectExerciseReport = { exGoal ->
                showAllGoalsMasterReport = false
                reportGoalTarget = exGoal
            },
            onDismiss = { showAllGoalsMasterReport = false }
        )
    }
}

@Composable
private fun GoalSummaryMiniCard(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(0.8.dp, BorderDark.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GoalProgrammingCard(
    evaluation: GoalEvaluation,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenReport: () -> Unit,
    onEditGoal: () -> Unit,
    onDeleteGoal: () -> Unit,
    onToggleEnabled: () -> Unit,
    onTrainGoalNow: () -> Unit
) {
    val goal = evaluation.goal

    val statusColor = when (evaluation.status) {
        GoalStatus.ACHIEVED -> GoldPR
        GoalStatus.ON_TRACK -> IncreaseGreen
        GoalStatus.PLATEAU_ALERT -> DecreaseRed
        GoalStatus.NEEDS_FREQUENCY -> AccentPeach
        GoalStatus.BUILDING -> BlueAccent
    }

    val targetHeadline = if (goal.isSetsRepsGoal) {
        val wStr = if (evaluation.isBodyweight) {
            if (goal.targetWeightKg <= 0.0) "BW" else "BW+${formatWeightValue(goal.targetWeightKg)}kg"
        } else "${formatWeightValue(goal.targetWeightKg)}kg"
        "${goal.targetSets} × ${goal.targetReps} @ $wStr"
    } else {
        "${formatWeightValue(evaluation.effectiveTargetE1rm)} kg 1RM"
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (evaluation.progressPct.toFloat() / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "goalProgressAnim"
    )

    StrengthCard(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (evaluation.isAchieved) 1.2.dp else 1.dp,
            color = if (evaluation.isAchieved) GoldPR.copy(alpha = 0.6f)
            else if (!goal.enabled) BorderDark.copy(alpha = 0.4f)
            else BorderDark
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1: Exercise name + Category pill on left, Target headline on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = goal.exerciseName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (goal.enabled) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CategoryPill(category = evaluation.category)
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = targetHeadline,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (evaluation.isAchieved) GoldPR else AccentOrange,
                    maxLines = 1
                )
            }

            // Row 2: Status pill on left, Current best & progress % on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = statusColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.7.dp, statusColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = evaluation.status.label,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (!goal.enabled) {
                        Text(
                            text = "Paused",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = "Best: ${formatWeightValue(evaluation.currentBestE1rm)}kg • ${formatWeightValue(evaluation.progressPct)}%",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }

            // Row 3: Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BgDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (evaluation.isAchieved) GoldGradient else OrangeGradient
                        )
                )
            }

            // Row 4: Next Session Target + Train Button + Expand toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (evaluation.isAchieved) "Goal Reached! 🏆" else "Next: ${evaluation.nextSessionSummary}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (evaluation.isAchieved) GoldPR else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!evaluation.isAchieved) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = AccentOrange,
                            modifier = Modifier.clickable { onTrainGoalNow() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Train",
                                    tint = Color(0xFF140D07),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Train",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF140D07)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // EXPANDED CONTENT: Clean, concise & actionable (no clutter)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = BorderDark, thickness = 0.6.dp)

                    // 1. Next Workout Prescribed Sets
                    if (evaluation.nextSessionSets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "NEXT SESSION SETS",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            evaluation.nextSessionSets.forEach { ps ->
                                PrescribedSetRow(set = ps, isBodyweight = evaluation.isBodyweight, isWarmup = false)
                            }
                            if (evaluation.nextSessionFocusCue.isNotBlank()) {
                                Text(
                                    text = "💡 ${evaluation.nextSessionFocusCue}",
                                    fontSize = 10.5.sp,
                                    color = AccentPeach,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // 2. 4-Phase Periodization Milestones
                    if (evaluation.roadmapPhases.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "MILESTONES",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            evaluation.roadmapPhases.forEach { phase ->
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = BgDark,
                                    border = BorderStroke(0.6.dp, BorderDark)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = phase.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = phase.prescriptionSummary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentOrange
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Goal Actions Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenReport,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, BorderDark),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        ) {
                            Text("Report", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onEditGoal,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, BorderDark),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        ) {
                            Text("Edit", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onToggleEnabled,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, BorderDark),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (goal.enabled) TextSecondary else IncreaseGreen),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        ) {
                            Text(if (goal.enabled) "Pause" else "Resume", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onDeleteGoal,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = DecreaseRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammingMetricBadge(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = BgDark,
        border = BorderStroke(0.6.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PrescribedSetRow(
    set: com.strengthlogz.mobile.util.PrescribedGoalSet,
    isBodyweight: Boolean,
    isWarmup: Boolean
) {
    val wStr = if (isBodyweight) {
        if (set.weightKg <= 0.0) "BW" else "BW+${formatWeightValue(set.weightKg)} kg"
    } else "${formatWeightValue(set.weightKg)} kg"

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (isWarmup) SurfaceDark.copy(alpha = 0.6f) else BgDark,
        border = BorderStroke(0.7.dp, if (isWarmup) BorderDark.copy(alpha = 0.5f) else AccentOrange.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isWarmup) BorderDark else AccentOrange.copy(alpha = 0.2f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isWarmup) "W" else "${set.setNo}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isWarmup) TextSecondary else AccentOrange
                        )
                    }
                }
                Column {
                    Text(
                        text = "${set.role} • ${set.rpe}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWarmup) TextSecondary else TextPrimary
                    )
                    if (set.notes.isNotBlank()) {
                        Text(
                            text = set.notes,
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Text(
                text = "$wStr × ${set.reps}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isWarmup) TextSecondary else GoldPR
            )
        }
    }
}

/**
 * Modal dialog to Create or Edit an [ExerciseGoal] (supports both 1RM and Sets x Reps goals).
 */
@Composable
fun CreateOrEditGoalDialog(
    existingGoal: ExerciseGoal?,
    initialExerciseName: String?,
    availableExercises: List<Exercise>,
    workouts: List<Workout>,
    getSetsForExercise: (String) -> List<WorkoutSet>,
    currentBodyWeightKg: Double,
    onDismiss: () -> Unit,
    onSave: (ExerciseGoal) -> Unit
) {
    val defaultEx = existingGoal?.exerciseName
        ?: initialExerciseName
        ?: availableExercises.firstOrNull { it.name.equals("Bench Press", true) }?.name
        ?: availableExercises.firstOrNull()?.name
        ?: "Bench Press"

    var selectedExercise by remember { mutableStateOf(defaultEx) }
    var showExerciseSelector by remember { mutableStateOf(false) }
    var exerciseQuery by remember { mutableStateOf("") }

    var goalType by remember { mutableStateOf(existingGoal?.safeGoalType ?: "E1RM") }

    // Compute RECENT current stats for selected exercise (last 4 sessions, never 12-month-old peak)
    val workoutMap = remember(workouts) { workouts.associateBy { it.id } }
    val isBw = remember(selectedExercise, availableExercises) {
        availableExercises.find { it.name.equals(selectedExercise, true) }?.bodyweight == true ||
            BodyWeightUtils.isBodyweightExercise(selectedExercise)
    }
    val exSessions = remember(selectedExercise, workoutMap, isBw) {
        buildSessionPoints(getSetsForExercise(selectedExercise), workoutMap, isBw)
    }
    val latestSession = remember(exSessions) { exSessions.lastOrNull() }
    val recentSessions = remember(exSessions) { exSessions.takeLast(4) }
    val recentBestE1rm = remember(recentSessions) { recentSessions.maxOfOrNull { it.e1rm } ?: 0.0 }
    val recentTopWeight = remember(latestSession, recentSessions) {
        latestSession?.topWeight ?: recentSessions.maxOfOrNull { it.topWeight } ?: 0.0
    }
    val recentTopReps = remember(latestSession) { latestSession?.topWeightReps ?: 8 }

    var startWeightText by remember(selectedExercise) {
        val initW = if (existingGoal != null && existingGoal.exerciseName.equals(selectedExercise, true) && existingGoal.baselineWeightKg > 0.0) {
            existingGoal.baselineWeightKg
        } else {
            recentTopWeight
        }
        mutableStateOf(if (initW > 0.0) formatWeightValue(initW) else "")
    }
    var startRepsText by remember(selectedExercise) {
        val initR = if (existingGoal != null && existingGoal.exerciseName.equals(selectedExercise, true) && existingGoal.baselineReps > 0) {
            existingGoal.baselineReps
        } else if (recentTopWeight > 0.0) {
            recentTopReps
        } else 5
        mutableStateOf(initR.toString())
    }

    val manualStartW = startWeightText.trim().toDoubleOrNull() ?: 0.0
    val manualStartR = (startRepsText.trim().toIntOrNull() ?: 1).coerceIn(1, 50)
    val effectiveCurrentE1rm = remember(manualStartW, manualStartR, recentBestE1rm, isBw, currentBodyWeightKg) {
        if (manualStartW > 0.0) {
            val totalLoad = BodyWeightUtils.totalLoad(isBw, manualStartW, currentBodyWeightKg)
            ((computeEpley(totalLoad, manualStartR)) * 2.0).roundToInt() / 2.0
        } else if (isBw && startWeightText.trim() == "0") {
            ((computeEpley(currentBodyWeightKg, manualStartR)) * 2.0).roundToInt() / 2.0
        } else if (existingGoal != null && existingGoal.exerciseName.equals(selectedExercise, true) && existingGoal.baselineE1rmKg > 0.0) {
            existingGoal.baselineE1rmKg
        } else {
            recentBestE1rm
        }
    }
    val currentRelBw = remember(effectiveCurrentE1rm, currentBodyWeightKg) {
        BodyWeightUtils.relativeStrength(effectiveCurrentE1rm, currentBodyWeightKg)
    }

    var targetE1rmText by remember(selectedExercise) {
        val baseE = if (effectiveCurrentE1rm > 0.0) effectiveCurrentE1rm else recentBestE1rm
        val initVal = if (existingGoal != null && existingGoal.exerciseName.equals(selectedExercise, true) && existingGoal.targetE1rmKg > 0.0) {
            existingGoal.targetE1rmKg
        } else if (baseE > 0.0) {
            GoalEngine.roundToGymLoad(baseE * 1.075, selectedExercise)
        } else 100.0
        mutableStateOf(formatWeightValue(initVal))
    }

    var targetWeightText by remember(selectedExercise) {
        val baseW = if (manualStartW > 0.0) manualStartW else recentTopWeight
        val initW = if (existingGoal != null && existingGoal.exerciseName.equals(selectedExercise, true) && existingGoal.targetWeightKg > 0.0) {
            existingGoal.targetWeightKg
        } else if (baseW > 0.0) {
            GoalEngine.roundToGymLoad(baseW + 2.5, selectedExercise)
        } else 80.0
        mutableStateOf(formatWeightValue(initW))
    }

    var targetSetsText by remember(selectedExercise) {
        mutableStateOf((existingGoal?.targetSets ?: 3).toString())
    }
    var targetRepsText by remember(selectedExercise) {
        mutableStateOf((existingGoal?.targetReps ?: 8).toString())
    }
    var notesText by remember { mutableStateOf(existingGoal?.safeNotes ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existingGoal == null) "Set Exercise Goal" else "Edit Exercise Goal",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // 1. Exercise Selector
                Text("1. SELECT MOVEMENT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgDark,
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExerciseSelector = !showExerciseSelector }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(selectedExercise, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            Text(
                                text = if (latestSession != null)
                                    "Latest (${latestSession.displayDate}): ${formatWeightValue(recentTopWeight)}kg × $recentTopReps (${formatWeightValue(recentBestE1rm)}kg recent E1RM)"
                                else "No prior sessions logged • Enter your starting weight below",
                                fontSize = 11.5.sp,
                                color = AccentPeach
                            )
                        }
                        Text(
                            text = if (showExerciseSelector) "Close ▲" else "Change ▼",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }

                if (showExerciseSelector) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            ModernSearchBar(
                                query = exerciseQuery,
                                onQueryChange = { exerciseQuery = it },
                                placeholder = "Search exercise..."
                            )
                            Spacer(Modifier.height(6.dp))
                            val filteredEx = remember(availableExercises, exerciseQuery) {
                                val q = exerciseQuery.trim()
                                availableExercises.filter { q.isEmpty() || it.name.contains(q, true) || it.category.contains(q, true) }
                            }
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(filteredEx, key = { it.name }) { ex ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedExercise = ex.name
                                                showExerciseSelector = false
                                                exerciseQuery = ""
                                            }
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(ex.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        CategoryPill(category = ex.category)
                                    }
                                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                // 2. Current / Starting Strength (Auto from recent sessions or manual override)
                Text("2. CURRENT STRENGTH (STARTING BASELINE)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startWeightText,
                        onValueChange = { startWeightText = it },
                        label = { Text(if (isBw) "Curr Added kg" else "Curr Weight kg") },
                        placeholder = { Text("e.g. 80") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = BorderDark
                        )
                    )
                    OutlinedTextField(
                        value = startRepsText,
                        onValueChange = { startRepsText = it },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = BorderDark
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDark,
                        border = BorderStroke(0.8.dp, BorderDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("CURR E1RM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Text(
                                text = if (effectiveCurrentE1rm > 0.0) "${formatWeightValue(effectiveCurrentE1rm)}kg" else "--",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentPeach
                            )
                        }
                    }
                }

                // 3. Goal Type Selector: 1RM vs Sets / Reps
                Text("3. GOAL TYPE", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgDark, RoundedCornerShape(11.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("E1RM" to "🎯 1-Rep Max (1RM)", "SETS_REPS" to "🏋️ Sets × Reps").forEach { (typeKey, label) ->
                        val active = goalType == typeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) AccentOrange else Color.Transparent)
                                .clickable { goalType = typeKey }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (active) Color(0xFF140D07) else TextSecondary
                            )
                        }
                    }
                }

                // 4. Target Configuration & Smart Presets
                if (goalType == "E1RM") {
                    Text("4. TARGET 1-REP MAX (KG)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

                    if (effectiveCurrentE1rm > 0.0) {
                        val presets = listOf(
                            "+2.5kg" to (effectiveCurrentE1rm + 2.5),
                            "+5% Block" to GoalEngine.roundToGymLoad(effectiveCurrentE1rm * 1.05, selectedExercise),
                            "+10% Peak" to GoalEngine.roundToGymLoad(effectiveCurrentE1rm * 1.10, selectedExercise),
                            "1.25× BW" to GoalEngine.roundToGymLoad(currentBodyWeightKg * 1.25, selectedExercise),
                            "1.5× BW" to GoalEngine.roundToGymLoad(currentBodyWeightKg * 1.50, selectedExercise)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(presets) { (lbl, valKg) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgDark,
                                    border = BorderStroke(0.8.dp, AccentPeach.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable { targetE1rmText = formatWeightValue(valKg) }
                                ) {
                                    Text(
                                        text = "$lbl (${formatWeightValue(valKg)}kg)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPeach,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetE1rmText,
                        onValueChange = { targetE1rmText = it },
                        label = { Text(if (isBw) "Target Total-Load 1RM (kg)" else "Target 1RM (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = BorderDark
                        )
                    )
                } else {
                    Text("4. TARGET SETS × REPS @ WEIGHT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

                    val baseW = when {
                        manualStartW > 0.0 -> manualStartW
                        recentTopWeight > 0.0 -> recentTopWeight
                        else -> 60.0
                    }
                    val srPresets = listOf(
                        Triple("3 × 8 Overload", 3, 8 to GoalEngine.roundToGymLoad(baseW + 2.5, selectedExercise)),
                        Triple("5 × 5 Strength", 5, 5 to GoalEngine.roundToGymLoad(baseW + 5.0, selectedExercise)),
                        Triple("3 × 10 Volume", 3, 10 to baseW),
                        Triple("4 × 6 Power", 4, 6 to GoalEngine.roundToGymLoad(baseW + 2.5, selectedExercise))
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(srPresets) { (lbl, sCnt, repW) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgDark,
                                border = BorderStroke(0.8.dp, AccentPeach.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    targetSetsText = sCnt.toString()
                                    targetRepsText = repW.first.toString()
                                    targetWeightText = formatWeightValue(repW.second)
                                }
                            ) {
                                Text(
                                    text = "$lbl (@ ${formatWeightValue(repW.second)}kg)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPeach,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = targetSetsText,
                            onValueChange = { targetSetsText = it },
                            label = { Text("Sets") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                        OutlinedTextField(
                            value = targetRepsText,
                            onValueChange = { targetRepsText = it },
                            label = { Text("Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                        OutlinedTextField(
                            value = targetWeightText,
                            onValueChange = { targetWeightText = it },
                            label = { Text(if (isBw) "Added kg" else "Weight kg") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Focus Cue / Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderDark
                    )
                )

                // Live Preview Calculation Box
                val previewGoal = remember(selectedExercise, goalType, targetE1rmText, targetWeightText, targetSetsText, targetRepsText, effectiveCurrentE1rm, manualStartW, manualStartR, notesText) {
                    ExerciseGoal(
                        id = existingGoal?.id ?: "goal-${selectedExercise.lowercase().replace(" ", "-")}",
                        exerciseName = selectedExercise,
                        goalType = goalType,
                        targetE1rmKg = targetE1rmText.trim().toDoubleOrNull() ?: 100.0,
                        targetWeightKg = targetWeightText.trim().toDoubleOrNull() ?: 0.0,
                        targetSets = targetSetsText.trim().toIntOrNull()?.coerceIn(1, 20) ?: 3,
                        targetReps = targetRepsText.trim().toIntOrNull()?.coerceIn(1, 50) ?: 8,
                        baselineE1rmKg = effectiveCurrentE1rm,
                        baselineWeightKg = manualStartW,
                        baselineReps = manualStartR,
                        baselineBodyWeightKg = existingGoal?.baselineBodyWeightKg?.takeIf { it > 0.0 } ?: currentBodyWeightKg,
                        createdDate = existingGoal?.safeCreatedDate?.ifBlank { DateUtils.todayDdMonYyyy() } ?: DateUtils.todayDdMonYyyy(),
                        notes = notesText
                    )
                }
                val previewTargetE1rm = GoalEngine.computeEffectiveTargetE1rm(previewGoal, isBw, currentBodyWeightKg)
                val deltaNeeded = (previewTargetE1rm - effectiveCurrentE1rm).coerceAtLeast(0.0)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgDark,
                    border = BorderStroke(0.8.dp, GoldPR.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "📊 IN-APP GOAL PROJECTION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldPR
                        )
                        Text(
                            text = "Equivalent Target E1RM: ${formatWeightValue(previewTargetE1rm)} kg (${"%.2f".format(BodyWeightUtils.relativeStrength(previewTargetE1rm, currentBodyWeightKg))}× BW)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (effectiveCurrentE1rm > 0.0)
                                "Requires +${formatWeightValue(deltaNeeded)} kg E1RM gain over your current ${formatWeightValue(effectiveCurrentE1rm)} kg strength."
                            else "Enter your current lift above or log a workout to calibrate your starting point.",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            onSave(previewGoal.copy(notes = notesText.trim(), enabled = true))
                        },
                        modifier = Modifier.weight(1.3f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = Color(0xFF140D07)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Goal & Plan", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * Single-Exercise Goal Report Dialog — accessible from GoalsScreen AND LogWorkoutScreen.
 */
@Composable
fun ExerciseGoalReportDialog(
    report: ExerciseGoalReport,
    onDismiss: () -> Unit
) {
    val eval = report.evaluation
    val goal = eval.goal

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = BgDark,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${goal.exerciseName} Report",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            CategoryPill(category = eval.category)
                        }
                        Text(
                            text = "Generated ${report.generatedDate} • Adherence Score: ${report.adherenceScore}/100",
                            fontSize = 11.5.sp,
                            color = AccentPeach,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(SurfaceCard, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Executive Status Card
                    item {
                        StrengthCard(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (eval.isAchieved) GoldPR else AccentOrange.copy(alpha = 0.6f))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = report.executiveStatus,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (eval.isAchieved) GoldPR else AccentOrange
                                )
                                Text(
                                    text = report.headlineSummary,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    // 4-KPI Strip
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GoalSummaryMiniCard(
                                label = "COMPLETION",
                                value = "${formatWeightValue(eval.progressPct)}%",
                                sub = "${formatWeightValue(eval.remainingKg)}kg to target",
                                valueColor = if (eval.isAchieved) GoldPR else AccentOrange,
                                modifier = Modifier.weight(1f)
                            )
                            GoalSummaryMiniCard(
                                label = "GAIN SINCE START",
                                value = "${if (report.strengthGainSinceGoalStartKg >= 0) "+" else ""}${formatWeightValue(report.strengthGainSinceGoalStartKg)}kg",
                                sub = "${if (report.strengthGainPct >= 0) "+" else ""}${formatWeightValue(report.strengthGainPct)}% E1RM",
                                valueColor = IncreaseGreen,
                                modifier = Modifier.weight(1f)
                            )
                            GoalSummaryMiniCard(
                                label = "REL. STRENGTH",
                                value = "${"%.2f".format(eval.currentRelBw)}× BW",
                                sub = "Target ${"%.2f".format(eval.targetRelBw)}× BW",
                                valueColor = GoldPR,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Strengths & Bottlenecks Analysis
                    item {
                        StrengthCard(shape = RoundedCornerShape(14.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "📈 PERFORMANCE & BODY WEIGHT AUDIT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IncreaseGreen,
                                    letterSpacing = 0.6.sp
                                )
                                report.keyStrengths.forEach { pt ->
                                    Text("• $pt", fontSize = 12.sp, color = TextPrimary)
                                }
                                Text(
                                    text = "📦 Volume Trend: ${report.volumeTrendSummary}",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "⚖️ Body Weight Context: ${report.bodyWeightImpactSummary}",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )

                                HorizontalDivider(color = BorderDark, thickness = 0.6.dp)

                                Text(
                                    text = "🎯 ACTIONABLE PROGRAMMING ADJUSTMENTS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentOrange,
                                    letterSpacing = 0.6.sp
                                )
                                report.bottlenecksAndFixes.forEach { fix ->
                                    Text("• $fix", fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    // Session-by-Session History Table
                    item {
                        Text(
                            text = "SESSION-BY-SESSION GOAL LOG (${report.sessionEntries.size} SESSIONS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextSecondary,
                            letterSpacing = 0.6.sp
                        )
                    }

                    items(report.sessionEntries.take(25)) { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceCard,
                            border = BorderStroke(
                                0.8.dp,
                                if (entry.hitMilestone) GoldPR.copy(alpha = 0.6f) else BorderDark
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${entry.date} • ${entry.workoutTitle}",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${formatWeightValue(entry.sessionBestE1rm)}kg E1RM (${formatWeightValue(entry.progressPctAtSession)}%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (entry.hitMilestone) GoldPR else AccentOrange
                                    )
                                }
                                Text(
                                    text = "Sets: ${entry.setsSummary} • Vol: ${formatWeightValue(entry.totalVolumeKg)}kg",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = entry.coachNote,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (entry.deltaVsPrevE1rm >= 0.0) IncreaseGreen else AccentPeach
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Close Report", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * Executive All-Goals Master Report Dialog across all exercises that have goals configured.
 */
@Composable
fun AllGoalsReportDialog(
    masterReport: com.strengthlogz.mobile.util.AllGoalsMasterReport,
    onInspectExerciseReport: (ExerciseGoal) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = BgDark,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "All Exercise Goals Report",
                            fontSize = 18.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Generated ${masterReport.generatedDate} • ${masterReport.totalGoalsCount} Goal Lifts (@ ${formatWeightValue(masterReport.currentBodyWeightKg)}kg BW)",
                            fontSize = 11.5.sp,
                            color = GoldPR,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(SurfaceCard, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Portfolio Summary Strip
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GoalSummaryMiniCard(
                                label = "OVERALL PROGRESS",
                                value = "${formatWeightValue(masterReport.overallPortfolioCompletionPct)}%",
                                sub = "Adherence ${masterReport.averageAdherenceScore}/100",
                                valueColor = AccentOrange,
                                modifier = Modifier.weight(1f)
                            )
                            GoalSummaryMiniCard(
                                label = "ON TRACK / HIT",
                                value = "${masterReport.onTrackCount + masterReport.achievedGoalsCount}/${masterReport.totalGoalsCount}",
                                sub = "${masterReport.achievedGoalsCount} Achieved 🏆",
                                valueColor = IncreaseGreen,
                                modifier = Modifier.weight(1f)
                            )
                            GoalSummaryMiniCard(
                                label = "NEEDS FOCUS",
                                value = "${masterReport.plateauedCount}",
                                sub = "Plateau / Frequency",
                                valueColor = if (masterReport.plateauedCount > 0) DecreaseRed else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Executive Takeaways
                    item {
                        StrengthCard(shape = RoundedCornerShape(14.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "🏆 EXECUTIVE COACHING SUMMARY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldPR,
                                    letterSpacing = 0.6.sp
                                )
                                masterReport.executiveTakeaways.forEach { bullet ->
                                    Text("• $bullet", fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "PER-EXERCISE GOAL STATUS & PRESCRIPTIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextSecondary,
                            letterSpacing = 0.6.sp
                        )
                    }

                    items(masterReport.exerciseReports, key = { it.evaluation.goal.id }) { exRep ->
                        val ev = exRep.evaluation
                        StrengthCard(
                            shape = RoundedCornerShape(14.dp),
                            onClick = { onInspectExerciseReport(ev.goal) }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = ev.goal.exerciseName,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                        CategoryPill(category = ev.category)
                                    }
                                    Text(
                                        text = "${formatWeightValue(ev.progressPct)}% • ${ev.status.label}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (ev.isAchieved) GoldPR else AccentOrange
                                    )
                                }

                                Text(
                                    text = exRep.headlineSummary,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )

                                Text(
                                    text = "Next Session Target: ${ev.nextSessionSummary} (${ev.methodologyTitle})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentPeach
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Freq: ${ev.sessionsPerWeek}/wk • Adherence: ${exRep.adherenceScore}/100",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Inspect Exercise Report →",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPR
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Close Master Report", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * In-Logger "Goal Progress" Modal Popup shown when clicking 'Goal Progress' on an exercise card in LogWorkoutScreen.
 * Shows sets/reps to aim for today, live session goal evaluation, and full Exercise Goal Report!
 */
@Composable
fun GoalProgressSessionDialog(
    liveAnalysis: LiveSessionGoalAnalysis,
    exerciseReport: ExerciseGoalReport,
    onApplyPrescribedSetsToCard: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Today's Target & Live Progress, 1 = Full Exercise Report
    val eval = liveAnalysis.evaluation
    val goal = eval.goal

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = BgDark,
            border = BorderStroke(1.2.dp, if (liveAnalysis.achievedGoalToday) GoldPR else AccentOrange)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "🎯 ${goal.exerciseName} Goal Progress",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (goal.isSetsRepsGoal)
                                "Target: ${goal.targetSets} × ${goal.targetReps} @ ${formatWeightValue(goal.targetWeightKg)}kg (≈${formatWeightValue(eval.effectiveTargetE1rm)}kg E1RM)"
                            else "Target: ${formatWeightValue(eval.effectiveTargetE1rm)}kg 1RM (${"%.2f".format(eval.targetRelBw)}× BW)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldPR,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(SurfaceCard, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(17.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 2 Tabs: Today's Target & Live Coach | Exercise Goal Report
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0 to "🎯 Session Target", 1 to "📋 Exercise Report").forEach { (idx, title) ->
                        val selected = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AccentOrange else Color.Transparent)
                                .clickable { activeTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (selected) Color(0xFF140D07) else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (activeTab == 0) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        // Live Session Status Card
                        item {
                            StrengthCard(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (liveAnalysis.achievedGoalToday) GoldPR
                                    else if (liveAnalysis.hitSessionTargetToday) IncreaseGreen
                                    else AccentOrange.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (liveAnalysis.achievedGoalToday) GoldPR.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = liveAnalysis.sessionGrade,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (liveAnalysis.achievedGoalToday) GoldPR else AccentOrange,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "Goal Progress: ${formatWeightValue(liveAnalysis.updatedProgressPct)}%",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GoldPR
                                        )
                                    }

                                    Text(
                                        text = liveAnalysis.liveCoachingHeadline,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    // Next Set Immediate Callout
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BgDark,
                                        border = BorderStroke(0.8.dp, AccentPeach.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "👉 ${liveAnalysis.nextSetTargetText}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentPeach,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }

                                    // Live Session Logged Stats
                                    if (liveAnalysis.loggedWorkingSetsCount > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ProgrammingMetricBadge(
                                                title = "TODAY'S E1RM",
                                                value = "${formatWeightValue(liveAnalysis.sessionBestE1rm)} kg",
                                                modifier = Modifier.weight(1f)
                                            )
                                            ProgrammingMetricBadge(
                                                title = "TOP SET",
                                                value = "${formatWeightValue(liveAnalysis.sessionBestWeight)}kg × ${liveAnalysis.sessionBestReps}",
                                                modifier = Modifier.weight(1f)
                                            )
                                            ProgrammingMetricBadge(
                                                title = "VS RECENT",
                                                value = "${if (liveAnalysis.deltaVsPreviousBestKg >= 0) "+" else ""}${formatWeightValue(liveAnalysis.deltaVsPreviousBestKg)} kg",
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Today's Prescribed Sets to Aim For
                        item {
                            StrengthCard(shape = RoundedCornerShape(14.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "SETS & REPS TO AIM FOR TODAY",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = AccentOrange,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = eval.methodologyTitle,
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AccentOrange,
                                            modifier = Modifier.clickable {
                                                onApplyPrescribedSetsToCard()
                                            }
                                        ) {
                                            Text(
                                                text = "⚡ Auto-Fill Sets",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF140D07),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    eval.nextSessionSets.forEach { ps ->
                                        PrescribedSetRow(set = ps, isBodyweight = eval.isBodyweight, isWarmup = false)
                                    }

                                    liveAnalysis.liveCoachingBullets.forEach { b ->
                                        Text("• $b", fontSize = 11.5.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        // Rep Equivalence Reference
                        item {
                            StrengthCard(shape = RoundedCornerShape(14.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "WEIGHT × REPS NEEDED TO HIT YOUR GOAL TODAY",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldPR,
                                        letterSpacing = 0.5.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        eval.repEquivalenceTable.forEach { (r, w) ->
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                color = BgDark,
                                                border = BorderStroke(0.7.dp, BorderDark)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 3.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("${r} reps", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = if (eval.isBodyweight && w <= 0.0) "BW"
                                                        else if (eval.isBodyweight) "+${formatWeightValue(w)}k"
                                                        else "${formatWeightValue(w)}k",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = TextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Tab 1: Embedded Full Exercise Report
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        item {
                            StrengthCard(shape = RoundedCornerShape(14.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = exerciseReport.executiveStatus,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldPR
                                    )
                                    Text(exerciseReport.headlineSummary, fontSize = 12.sp, color = TextPrimary)
                                    HorizontalDivider(color = BorderDark, thickness = 0.6.dp)
                                    exerciseReport.keyStrengths.forEach { s ->
                                        Text("✓ $s", fontSize = 11.5.sp, color = IncreaseGreen)
                                    }
                                    exerciseReport.bottlenecksAndFixes.forEach { f ->
                                        Text("→ $f", fontSize = 11.5.sp, color = AccentPeach)
                                    }
                                }
                            }
                        }

                        items(exerciseReport.sessionEntries.take(15)) { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceCard,
                                border = BorderStroke(0.7.dp, BorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${entry.date} • ${entry.workoutTitle}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("${formatWeightValue(entry.sessionBestE1rm)}kg E1RM (${formatWeightValue(entry.progressPctAtSession)}%)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                                    }
                                    Text("Sets: ${entry.setsSummary}", fontSize = 11.sp, color = TextSecondary)
                                    Text(entry.coachNote, fontSize = 11.sp, color = IncreaseGreen)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Back to Workout Logger", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
