package com.strengthlogz.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.strengthlogz.mobile.data.WorkoutRepository
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.ui.BodyWeightScreen
import com.strengthlogz.mobile.ui.ExercisesScreen
import com.strengthlogz.mobile.ui.GoalsScreen
import com.strengthlogz.mobile.ui.HistoryScreen
import com.strengthlogz.mobile.data.HealthConnectWeightSource
import com.strengthlogz.mobile.ui.LogWorkoutScreen
import com.strengthlogz.mobile.ui.ProgramScreen
import com.strengthlogz.mobile.ui.ProgressScreen
import com.strengthlogz.mobile.ui.SettingsScreen
import com.strengthlogz.mobile.ui.SuggestWorkoutScreen
import com.strengthlogz.mobile.ui.SuggestedWorkoutPlan
import com.strengthlogz.mobile.ui.WorkoutDetailScreen
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.DateUtils
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    LOG("Log", Icons.Default.AddCircle),
    PROGRAM("Program", Icons.Default.CalendarMonth),
    SUGGEST("Suggest", Icons.Default.AutoAwesome),
    HISTORY("History", Icons.Default.History),
    PROGRESS("Progress", Icons.Default.BarChart),
    GOALS("Goals", Icons.Default.EmojiEvents),
    EXERCISES("Exercises", Icons.Default.FitnessCenter),
    SETTINGS("Settings", Icons.Default.Settings),
    BODY_WEIGHT("Body Weight", Icons.Default.MonitorWeight)
}

private val PRIMARY_TABS = listOf(Screen.LOG, Screen.PROGRAM, Screen.SUGGEST, Screen.HISTORY, Screen.PROGRESS)

class MainActivity : ComponentActivity() {
    private lateinit var repository: WorkoutRepository

    /** Bumped after Health Connect weights are imported so the UI refreshes. */
    private var healthTick by androidx.compose.runtime.mutableIntStateOf(0)

    private val healthPermissionLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (HealthConnectWeightSource.weightPermission in granted) {
            lifecycleScope.launch { importHealthConnectWeights() }
        }
    }

    private suspend fun importHealthConnectWeights(): Result<String> {
        return try {
            val entries = HealthConnectWeightSource.readWeights(applicationContext)
            repository.upsertBodyWeights(entries)
            healthTick++
            Result.success(
                if (entries.isEmpty()) "Connected, but no weight records found in Health Connect since May 2026"
                else "Imported ${entries.size} weight reading${if (entries.size == 1) "" else "s"} from Health Connect"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun syncHealthConnectWeights(): Result<String> {
        if (!HealthConnectWeightSource.isAvailable(applicationContext)) {
            return Result.failure(Exception("Health Connect is not available on this device"))
        }
        if (!HealthConnectWeightSource.hasPermission(applicationContext)) {
            healthPermissionLauncher.launch(HealthConnectWeightSource.permissions)
            return Result.success("Grant weight access in the Health Connect dialog - weights import automatically")
        }
        return importHealthConnectWeights()
    }

    override fun onStop() {
        super.onStop()
        // Push local edits (goals, programs, body weight) to Google Sheets when leaving the app.
        if (::repository.isInitialized && repository.isCloudConfigured()) {
            lifecycleScope.launch { repository.syncWithGoogleSheets() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WorkoutRepository(applicationContext)

        setContent {
            MaterialTheme(colorScheme = StrengthDarkColorScheme) {
                var currentScreen by remember { mutableStateOf(Screen.LOG) }
                var lastPrimaryScreen by remember { mutableStateOf(Screen.LOG) }
                // Trigger recomposition on database updates
                var dataVersion by remember { mutableIntStateOf(0) }
                var pendingSuggestedPlan by remember { mutableStateOf<SuggestedWorkoutPlan?>(null) }
                var selectedWorkoutForDetail by remember { mutableStateOf<Workout?>(null) }
                var selectedProgressExercise by remember { mutableStateOf<String?>(null) }
                var selectedGoalExercise by remember { mutableStateOf<String?>(null) }
                var bwReturnScreen by remember { mutableStateOf(Screen.SETTINGS) }
                var dotsIsMale by remember { mutableStateOf(repository.getDotsIsMale()) }
                var bwUseLbs by remember { mutableStateOf(repository.getBwUseLbs()) }
                var postWorkoutUpdatedGoal by remember { mutableStateOf<com.strengthlogz.mobile.model.ExerciseGoal?>(null) }

                BackHandler(enabled = selectedWorkoutForDetail == null && currentScreen !in PRIMARY_TABS) {
                    currentScreen = lastPrimaryScreen
                }

                // Check if active draft exists to display draft badge
                val activeDraft = remember(dataVersion) { repository.getActiveDraft() }
                val hasActiveDraft = remember(activeDraft) {
                    activeDraft != null && (activeDraft.exercises.isNotEmpty() || activeDraft.title.isNotBlank())
                }
                val isHealthConnectAvailable = remember {
                    HealthConnectWeightSource.isAvailable(applicationContext)
                }

                // Refresh UI after Health Connect import
                LaunchedEffect(healthTick) { if (healthTick > 0) dataVersion++ }

                // Silent Health Connect weight import on startup when permission was already granted
                LaunchedEffect(Unit) {
                    try {
                        if (HealthConnectWeightSource.hasPermission(applicationContext)) importHealthConnectWeights()
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // Automatically sync in background on startup if online
                LaunchedEffect(Unit) {
                    val res = repository.syncWithGoogleSheets()
                    if (res.isSuccess) {
                        dataVersion++
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark),
                    containerColor = BgDark,
                    topBar = {
                        if (selectedWorkoutForDetail == null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding(),
                                color = BgDark
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AccentOrange.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.FitnessCenter,
                                                contentDescription = null,
                                                tint = AccentOrange,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = "STRENGTHLOGZ",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isExercises = currentScreen == Screen.EXERCISES
                                        Surface(
                                            color = if (isExercises) AccentOrange.copy(alpha = 0.18f) else SurfaceDark,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isExercises) AccentOrange else BorderDark.copy(alpha = 0.7f)
                                            ),
                                            modifier = Modifier.clickable {
                                                currentScreen = if (isExercises) lastPrimaryScreen else Screen.EXERCISES
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.FitnessCenter,
                                                    contentDescription = "Exercises Catalog",
                                                    tint = if (isExercises) AccentOrange else TextSecondary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    "Catalog",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isExercises) AccentOrange else TextSecondary
                                                )
                                            }
                                        }

                                        val isSettings = currentScreen == Screen.SETTINGS
                                        Surface(
                                            color = if (isSettings) AccentOrange.copy(alpha = 0.18f) else SurfaceDark,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSettings) AccentOrange else BorderDark.copy(alpha = 0.7f)
                                            ),
                                            modifier = Modifier.clickable {
                                                currentScreen = if (isSettings) lastPrimaryScreen else Screen.SETTINGS
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Settings,
                                                    contentDescription = "Settings & Body Weight",
                                                    tint = if (isSettings) AccentOrange else TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    bottomBar = {
                        // Streamlined 5-tab Primary Bottom Navigation Bar with centered icons & top clearance
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PRIMARY_TABS.forEach { screen ->
                                    val isSelected = currentScreen == screen && selectedWorkoutForDetail == null
                                    val isLogScreen = screen == Screen.LOG

                                    ModernNavItem(
                                        screen = screen,
                                        isSelected = isSelected,
                                        hasBadge = isLogScreen && hasActiveDraft && !isSelected,
                                        onClick = {
                                            currentScreen = screen
                                            lastPrimaryScreen = screen
                                            selectedWorkoutForDetail = null
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(BgDark)
                    ) {
                        key(dataVersion) {
                            AnimatedContent(
                                targetState = selectedWorkoutForDetail to currentScreen,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) +
                                        scaleIn(initialScale = 0.985f, animationSpec = tween(200)) togetherWith
                                        fadeOut(animationSpec = tween(150))
                                },
                                label = "screenTransition"
                            ) { (workoutDetail, screen) ->
                                if (workoutDetail != null) {
                                    WorkoutDetailScreen(
                                        workout = workoutDetail,
                                        allWorkouts = repository.appData.workouts,
                                        getSetsForWorkout = { id -> repository.getSetsForWorkout(id) },
                                        getSetsForExercise = { exName -> repository.getSetsForExercise(exName) },
                                        availableExercises = repository.appData.exercises,
                                        onBack = { selectedWorkoutForDetail = null },
                                        onNavigateToProgress = { exName ->
                                            selectedProgressExercise = exName
                                            selectedWorkoutForDetail = null
                                            currentScreen = Screen.PROGRESS
                                            lastPrimaryScreen = Screen.PROGRESS
                                        },
                                        onDeleteWorkout = { id ->
                                            repository.deleteWorkout(id)
                                            dataVersion++
                                            selectedWorkoutForDetail = null
                                            lifecycleScope.launch {
                                                val res = repository.syncWithGoogleSheets()
                                                if (res.isSuccess) dataVersion++
                                            }
                                        }
                                    )
                                } else {
                                    when (screen) {
                                        Screen.LOG -> LogWorkoutScreen(
                                            availableExercises = repository.appData.exercises,
                                            getPreviousSets = { exName ->
                                                repository.getPreviousSetsForExercise(exName)
                                            },
                                            getAllSetsForExercise = { exName -> repository.getSetsForExercise(exName) },
                                            getRecentExercises = {
                                                repository.getRecentExercises()
                                            },
                                            workouts = repository.appData.workouts,
                                            allSets = repository.appData.sets,
                                            bodyWeights = repository.getBodyWeights(),
                                            getActiveGoalForExercise = { exName ->
                                                repository.getActiveGoalForExercise(exName)
                                            },
                                            onAddCustomExercise = { newEx ->
                                                repository.addExercise(newEx)
                                                dataVersion++
                                            },
                                            onSaveWorkout = { workout, sets ->
                                                val updatedGoals = repository.saveWorkout(workout, sets)
                                                repository.clearActiveDraft()
                                                dataVersion++
                                                if (updatedGoals.isNotEmpty()) {
                                                    postWorkoutUpdatedGoal = updatedGoals.first()
                                                }
                                                lifecycleScope.launch {
                                                    val res = repository.syncWithGoogleSheets()
                                                    if (res.isSuccess) dataVersion++
                                                }
                                            },
                                            onNavigateHistory = {
                                                currentScreen = Screen.HISTORY
                                                lastPrimaryScreen = Screen.HISTORY
                                            },
                                            onNavigateSuggest = {
                                                currentScreen = Screen.SUGGEST
                                                lastPrimaryScreen = Screen.SUGGEST
                                            },
                                            onNavigateProgram = {
                                                currentScreen = Screen.PROGRAM
                                                lastPrimaryScreen = Screen.PROGRAM
                                            },
                                            onNavigateExercises = {
                                                currentScreen = Screen.EXERCISES
                                            },
                                            onNavigateGoals = {
                                                currentScreen = Screen.GOALS
                                            },
                                            pendingSuggestedPlan = pendingSuggestedPlan,
                                            onConsumeSuggestedPlan = {
                                                pendingSuggestedPlan = null
                                            },
                                            initialDraft = activeDraft,
                                            onDraftChanged = { draft ->
                                                repository.saveActiveDraft(draft)
                                            },
                                            onDiscardDraft = {
                                                repository.clearActiveDraft()
                                                dataVersion++
                                            }
                                        )
                                        Screen.PROGRAM -> ProgramScreen(
                                            programs = repository.getPrograms(),
                                            activeProgramId = repository.getActiveProgram()?.id,
                                            availableExercises = repository.appData.exercises,
                                            workouts = repository.appData.workouts,
                                            allSets = repository.appData.sets,
                                            getPreviousSets = { exName ->
                                                repository.getPreviousSetsForExercise(exName)
                                            },
                                            onSaveProgram = { updatedProg ->
                                                repository.saveProgram(updatedProg)
                                                dataVersion++
                                            },
                                            onCreateProgram = { name ->
                                                repository.createProgram(name)
                                                dataVersion++
                                            },
                                            onDeleteProgram = { id ->
                                                repository.deleteProgram(id)
                                                dataVersion++
                                            },
                                            onSelectProgram = { id ->
                                                repository.setActiveProgramId(id)
                                                dataVersion++
                                            },
                                            onDuplicateProgram = { id ->
                                                repository.duplicateProgram(id)
                                                dataVersion++
                                            },
                                            onAddCustomExercise = { newEx ->
                                                repository.addExercise(newEx)
                                                dataVersion++
                                            },
                                            onLoadProgramDayToLogger = { plan ->
                                                pendingSuggestedPlan = plan
                                                currentScreen = Screen.LOG
                                                lastPrimaryScreen = Screen.LOG
                                            }
                                        )
                                        Screen.SUGGEST -> SuggestWorkoutScreen(
                                            workouts = repository.appData.workouts,
                                            sets = repository.appData.sets,
                                            exercises = repository.appData.exercises,
                                            onStartSuggestedWorkout = { plan ->
                                                pendingSuggestedPlan = plan
                                                currentScreen = Screen.LOG
                                                lastPrimaryScreen = Screen.LOG
                                            }
                                        )
                                        Screen.EXERCISES -> ExercisesScreen(
                                            exercises = repository.appData.exercises,
                                            allSets = repository.appData.sets,
                                            onCreateExercise = { newEx ->
                                                repository.addExercise(newEx)
                                                dataVersion++
                                            },
                                            onUpdateExercise = { oldName, updated ->
                                                val res = repository.updateExercise(oldName, updated)
                                                if (res.isSuccess) dataVersion++
                                                res
                                            },
                                            onBackupExercises = { oldName, newName ->
                                                val res = repository.backupExercisesToGoogleSheets(oldName, newName)
                                                if (res.isSuccess) dataVersion++
                                                res
                                            },
                                            onBack = { currentScreen = lastPrimaryScreen }
                                        )
                                        Screen.HISTORY -> HistoryScreen(
                                            workouts = repository.getWorkoutsSorted(),
                                            getSetsForWorkout = { id -> repository.getSetsForWorkout(id) },
                                            onDeleteWorkout = { id ->
                                                repository.deleteWorkout(id)
                                                dataVersion++
                                                lifecycleScope.launch {
                                                    val res = repository.syncWithGoogleSheets()
                                                    if (res.isSuccess) dataVersion++
                                                }
                                            },
                                            onQuickSync = {
                                                val res = repository.syncWithGoogleSheets()
                                                if (res.isSuccess) dataVersion++
                                                res
                                            },
                                            onSelectWorkout = { w ->
                                                selectedWorkoutForDetail = w
                                            }
                                        )
                                        Screen.PROGRESS -> ProgressScreen(
                                            distinctExercises = repository.getDistinctExercisesByFrequency(),
                                            availableExercises = repository.appData.exercises,
                                            workouts = repository.appData.workouts,
                                            getSetsForExercise = { exName -> repository.getSetsForExercise(exName) },
                                            goals = repository.getGoals(),
                                            onNavigateGoals = { exName ->
                                                selectedGoalExercise = exName
                                                lastPrimaryScreen = Screen.PROGRESS
                                                currentScreen = Screen.GOALS
                                            },
                                            onNavigateBodyWeight = {
                                                bwReturnScreen = Screen.PROGRESS
                                                currentScreen = Screen.BODY_WEIGHT
                                            },
                                            initialExercise = selectedProgressExercise,
                                            onConsumeInitialExercise = { selectedProgressExercise = null }
                                        )
                                        Screen.GOALS -> GoalsScreen(
                                            goals = repository.getGoals(),
                                            workouts = repository.appData.workouts,
                                            allSets = repository.appData.sets,
                                            availableExercises = repository.appData.exercises,
                                            bodyWeights = repository.getBodyWeights(),
                                            getSetsForExercise = { exName -> repository.getSetsForExercise(exName) },
                                            initialExerciseForGoal = selectedGoalExercise,
                                            onConsumeInitialExercise = { selectedGoalExercise = null },
                                            onSaveGoal = { goal ->
                                                repository.saveGoal(goal)
                                                dataVersion++
                                            },
                                            onDeleteGoal = { goalId ->
                                                repository.deleteGoal(goalId)
                                                dataVersion++
                                            },
                                            onToggleGoalEnabled = { goalId ->
                                                repository.toggleGoalEnabled(goalId)
                                                dataVersion++
                                            },
                                            onStartGoalSessionInLogger = { plan ->
                                                pendingSuggestedPlan = plan
                                                currentScreen = Screen.LOG
                                                lastPrimaryScreen = Screen.LOG
                                            },
                                            onBack = {
                                                currentScreen = Screen.PROGRESS
                                                lastPrimaryScreen = Screen.PROGRESS
                                            }
                                        )
                                        Screen.SETTINGS -> SettingsScreen(
                                            currentSpreadsheetId = repository.getSpreadsheetId(),
                                            currentServiceAccountJson = repository.getServiceAccountJson(),
                                            lastSyncTime = repository.getLastSyncTime(),
                                            onSaveSpreadsheetId = { repository.setSpreadsheetId(it) },
                                            onSaveServiceAccountJson = { repository.setServiceAccountJson(it) },
                                            onClearCloudCredentials = {
                                                repository.clearCloudCredentials()
                                                dataVersion++
                                            },
                                            onTriggerSync = {
                                                val res = repository.syncWithGoogleSheets()
                                                if (res.isSuccess) dataVersion++
                                                res
                                            },
                                            workoutCount = repository.appData.workouts.size,
                                            setCount = repository.appData.sets.size,
                                            exerciseCount = repository.appData.exercises.size,
                                            bodyWeights = repository.getBodyWeights(),
                                            healthConnectAvailable = isHealthConnectAvailable,
                                            onSyncHealthConnect = { syncHealthConnectWeights() },
                                            onAddManualWeight = { date, kg ->
                                                repository.addManualBodyWeight(date, kg)
                                                dataVersion++
                                            },
                                            onNavigateExercises = { currentScreen = Screen.EXERCISES },
                                            onOpenBodyWeightDashboard = {
                                                bwReturnScreen = Screen.SETTINGS
                                                currentScreen = Screen.BODY_WEIGHT
                                            }
                                        )
                                        Screen.BODY_WEIGHT -> BodyWeightScreen(
                                            bodyWeights = repository.getBodyWeights(),
                                            workouts = repository.appData.workouts,
                                            allSets = repository.appData.sets,
                                            distinctExercises = repository.getDistinctExercisesByFrequency(),
                                            getSetsForExercise = { exName -> repository.getSetsForExercise(exName) },
                                            isMale = dotsIsMale,
                                            onSetMale = {
                                                dotsIsMale = it
                                                repository.setDotsIsMale(it)
                                            },
                                            useLbs = bwUseLbs,
                                            onSetUseLbs = {
                                                bwUseLbs = it
                                                repository.setBwUseLbs(it)
                                            },
                                            healthConnectAvailable = isHealthConnectAvailable,
                                            onSyncHealthConnect = { syncHealthConnectWeights() },
                                            onAddManualWeight = { date, kg ->
                                                repository.addManualBodyWeight(date, kg)
                                                dataVersion++
                                            },
                                            onDeleteWeight = { date ->
                                                repository.deleteBodyWeight(date)
                                                dataVersion++
                                                lifecycleScope.launch {
                                                    val res = repository.syncWithGoogleSheets()
                                                    if (res.isSuccess) dataVersion++
                                                }
                                            },
                                            onBack = {
                                                currentScreen = bwReturnScreen
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Post-workout Goal Report popup when a session containing a goal exercise is logged
                        postWorkoutUpdatedGoal?.let { updatedGoal ->
                            val report = remember(updatedGoal, dataVersion) {
                                com.strengthlogz.mobile.util.GoalEngine.generateExerciseReport(
                                    goal = updatedGoal,
                                    workouts = repository.appData.workouts,
                                    exerciseSets = repository.getSetsForExercise(updatedGoal.exerciseName),
                                    allSets = repository.appData.sets,
                                    exercisesCatalog = repository.appData.exercises,
                                    bodyWeights = repository.getBodyWeights()
                                )
                            }
                            com.strengthlogz.mobile.ui.ExerciseGoalReportDialog(
                                report = report,
                                onDismiss = { postWorkoutUpdatedGoal = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern responsive navigation item with smooth animated scale and micro-capsule.
 */
@Composable
private fun ModernNavItem(
    screen: Screen,
    isSelected: Boolean,
    hasBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navIconScale"
    )

    val contentColor = if (isSelected) AccentOrange else TextSecondary

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) AccentOrange.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = screen.icon,
                contentDescription = screen.title,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .scale(iconScale)
            )

            // Active draft badge dot
            if (hasBadge) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .clip(CircleShape)
                        .background(AccentOrange)
                )
            }
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = screen.title,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
