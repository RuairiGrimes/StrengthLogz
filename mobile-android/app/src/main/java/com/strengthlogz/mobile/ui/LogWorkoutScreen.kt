package com.strengthlogz.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.model.ActiveWorkoutDraft
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.DraftExerciseLog
import com.strengthlogz.mobile.model.DraftSetInput
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.GoalEngine
import com.strengthlogz.mobile.util.SetUtils
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Stable
class SetInputState(
    initialWeight: String = "",
    initialReps: String = "",
    initialNotes: String = "",
    initialCompleted: Boolean = false
) {
    var weight by mutableStateOf(initialWeight)
    var reps by mutableStateOf(initialReps)
    var notes by mutableStateOf(initialNotes)
    var isCompleted by mutableStateOf(initialCompleted)
}

/** Which set field was tapped, so the picker dialog can edit it. */
@Stable
class SetPickerTarget(
    val exLog: ExerciseLogState,
    val set: SetInputState,
    val setIdx: Int,
    val focusWeight: Boolean
)

@Stable
class ExerciseLogState(
    val exerciseName: String,
    val category: String = "Other",
    val previousSets: List<WorkoutSet> = emptyList(),
    val allTimeSets: List<WorkoutSet> = emptyList(),
    initialSets: List<SetInputState> = listOf(SetInputState()),
    /** Bodyweight-flagged lifts log ADDED load; E1RM uses body weight + added load. */
    val isBodyweight: Boolean = BodyWeightUtils.isBodyweightExercise(exerciseName)
) {
    val sets = mutableStateListOf<SetInputState>().apply {
        addAll(initialSets)
    }

    val previousSummary: String by lazy {
        if (previousSets.isNotEmpty()) SetUtils.formatSetsSummary(previousSets) else ""
    }

    val previousDate: String by lazy {
        if (previousSets.isNotEmpty()) DateUtils.toDdMonYyyy(previousSets.first().date) else ""
    }

    // Historical peak E1RM for PR Radar
    val peakE1rm: Double by lazy {
        allTimeSets.filter { (it.reps ?: 0) > 0 }
            .map { s ->
                val load = BodyWeightUtils.totalLoad(isBodyweight, s.weight, if (isBodyweight) BodyWeightUtils.weightOn(s.date) else 0.0)
                if (load > 0.0) computeEpley(load, s.reps!!) else 0.0
            }
            .maxOrNull() ?: 0.0
    }

    // Historical top weight moved
    val peakWeight: Double by lazy {
        allTimeSets.filter { (it.reps ?: 0) > 0 && (it.weight ?: 0.0) > 0.0 }
            .map { it.weight!! }
            .maxOrNull() ?: 0.0
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun LogWorkoutScreen(
    availableExercises: List<Exercise>,
    getPreviousSets: (String) -> List<WorkoutSet>,
    getAllSetsForExercise: ((String) -> List<WorkoutSet>)? = null,
    getRecentExercises: (() -> List<String>)? = null,
    workouts: List<Workout> = emptyList(),
    allSets: List<WorkoutSet> = emptyList(),
    bodyWeights: List<BodyWeightEntry> = emptyList(),
    getActiveGoalForExercise: (String) -> ExerciseGoal? = { null },
    onAddCustomExercise: (Exercise) -> Unit,
    onSaveWorkout: (Workout, List<WorkoutSet>) -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateSuggest: () -> Unit = {},
    onNavigateProgram: () -> Unit = {},
    onNavigateExercises: () -> Unit = {},
    onNavigateGoals: () -> Unit = {},
    pendingSuggestedPlan: SuggestedWorkoutPlan? = null,
    onConsumeSuggestedPlan: () -> Unit = {},
    initialDraft: ActiveWorkoutDraft? = null,
    onDraftChanged: (ActiveWorkoutDraft) -> Unit = {},
    onDiscardDraft: () -> Unit = {}
) {
    var workoutDate by remember {
        mutableStateOf(initialDraft?.date?.ifBlank { DateUtils.todayDdMonYyyy() } ?: DateUtils.todayDdMonYyyy())
    }
    var workoutTitle by remember { mutableStateOf(initialDraft?.title ?: "") }
    var workoutNotes by remember { mutableStateOf(initialDraft?.notes ?: "") }

    val loggedExercises = remember {
        mutableStateListOf<ExerciseLogState>().apply {
            if (initialDraft != null && initialDraft.exercises.isNotEmpty()) {
                initialDraft.exercises.forEach { draftEx ->
                    val prevSets = getPreviousSets(draftEx.exerciseName)
                    val allSets = getAllSetsForExercise?.invoke(draftEx.exerciseName) ?: prevSets
                    val cat = availableExercises.find { it.name.equals(draftEx.exerciseName, ignoreCase = true) }?.category
                        ?: draftEx.category.ifBlank { "Other" }
                    val restoredSets = draftEx.sets.map { ds ->
                        SetInputState(
                            initialWeight = ds.weight,
                            initialReps = ds.reps,
                            initialNotes = ds.notes,
                            initialCompleted = ds.isCompleted
                        )
                    }.ifEmpty { listOf(SetInputState()) }

                    add(
                        ExerciseLogState(
                            exerciseName = draftEx.exerciseName,
                            category = cat,
                            previousSets = prevSets,
                            allTimeSets = allSets,
                            initialSets = restoredSets
                        )
                    )
                }
            }
        }
    }
    var showExercisePicker by remember { mutableStateOf(false) }
    var exerciseSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // Build active draft snapshot
    fun buildCurrentDraft(): ActiveWorkoutDraft {
        val draftExercises = loggedExercises.map { exLog ->
            DraftExerciseLog(
                exerciseName = exLog.exerciseName,
                category = exLog.category,
                sets = exLog.sets.map { s ->
                    DraftSetInput(
                        weight = s.weight,
                        reps = s.reps,
                        notes = s.notes,
                        isCompleted = s.isCompleted
                    )
                }
            )
        }
        return ActiveWorkoutDraft(
            date = workoutDate,
            title = workoutTitle,
            notes = workoutNotes,
            exercises = draftExercises,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }

    // Auto-persist draft to repository whenever any input or set changes (debounced)
    LaunchedEffect(Unit) {
        snapshotFlow {
            val exData = loggedExercises.map { ex ->
                ex.exerciseName to ex.sets.map { s ->
                    listOf(s.weight, s.reps, s.notes, s.isCompleted)
                }
            }
            Triple(workoutDate, workoutTitle to workoutNotes, exData)
        }
            .distinctUntilChanged()
            .debounce(300L)
            .collect {
                val draft = buildCurrentDraft()
                if (draft.title.isNotBlank() || draft.notes.isNotBlank() || draft.exercises.isNotEmpty()) {
                    onDraftChanged(draft)
                }
            }
    }

    // Date Switcher Dialog
    var showDateDialog by remember { mutableStateOf(false) }

    // Weight/reps picker (wheel by default, keyboard optional)
    var pickerTarget by remember { mutableStateOf<SetPickerTarget?>(null) }

    // Plate Calculator State
    var plateCalcTargetSet by remember { mutableStateOf<SetInputState?>(null) }
    var showPlateCalculator by remember { mutableStateOf(false) }

    // Goal Progress Popup State (for active exercise with a goal)
    var goalProgressTargetExLog by remember { mutableStateOf<ExerciseLogState?>(null) }
    var postWorkoutGoalReportExName by remember { mutableStateOf<String?>(null) }

    // Helper: Add Exercise to active session
    fun addExerciseToSession(exerciseName: String) {
        val cat = availableExercises.find { it.name.equals(exerciseName, ignoreCase = true) }?.category ?: "Other"
        val prevSets = getPreviousSets(exerciseName)
        val allSets = getAllSetsForExercise?.invoke(exerciseName) ?: prevSets

        val completedPrevSets = prevSets.filter { (it.reps ?: 0) > 0 }
        val initialSets = if (completedPrevSets.isNotEmpty()) {
            completedPrevSets.map {
                SetInputState(
                    initialWeight = formatWeightValue(it.weight),
                    initialReps = it.reps?.toString() ?: ""
                )
            }
        } else {
            listOf(SetInputState())
        }

        loggedExercises.add(
            ExerciseLogState(
                exerciseName = exerciseName,
                category = cat,
                previousSets = prevSets,
                allTimeSets = allSets,
                initialSets = initialSets
            )
        )
    }

    // Load suggested workout plan if passed from Suggest tab
    LaunchedEffect(pendingSuggestedPlan) {
        if (pendingSuggestedPlan != null) {
            workoutDate = DateUtils.todayDdMonYyyy()
            workoutTitle = pendingSuggestedPlan.title
            workoutNotes = pendingSuggestedPlan.notes
            loggedExercises.clear()
            pendingSuggestedPlan.exercises.forEach { item ->
                val prevSets = getPreviousSets(item.exerciseName)
                val allSets = getAllSetsForExercise?.invoke(item.exerciseName) ?: prevSets
                val cat = availableExercises.find { it.name.equals(item.exerciseName, ignoreCase = true) }?.category ?: "Other"

                val sets = item.prescribedSets.map { ps ->
                    SetInputState(
                        initialWeight = ps.weight,
                        initialReps = ps.reps,
                        initialNotes = ps.notes
                    )
                }.ifEmpty { listOf(SetInputState()) }

                loggedExercises.add(
                    ExerciseLogState(
                        exerciseName = item.exerciseName,
                        category = cat,
                        previousSets = prevSets,
                        allTimeSets = allSets,
                        initialSets = sets
                    )
                )
            }
            onConsumeSuggestedPlan()
        }
    }

    // Live session stats
    val totalLoggedSets = loggedExercises.sumOf { ex ->
        ex.sets.count { (it.reps.trim().toIntOrNull() ?: 0) > 0 }
    }
    val totalSessionTonnage = loggedExercises.sumOf { ex ->
        ex.sets.sumOf { s ->
            val w = s.weight.trim().toDoubleOrNull() ?: 0.0
            val r = s.reps.trim().toIntOrNull() ?: 0
            if (r > 0) w * r else 0.0
        }
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            Surface(
                color = SurfaceDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showExercisePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Add Exercise",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (loggedExercises.isNotEmpty()) {
                                val nowMs = System.currentTimeMillis()
                                val workoutId = "w-${nowMs.toString(36)}"
                                val formattedDate = DateUtils.toDdMonYyyy(
                                    workoutDate.trim().ifEmpty { DateUtils.todayDdMonYyyy() }
                                )
                                val newWorkout = Workout(
                                    id = workoutId,
                                    date = formattedDate,
                                    title = workoutTitle.trim(),
                                    notes = workoutNotes.trim()
                                )

                                val setList = mutableListOf<WorkoutSet>()
                                var globalIdx = 0
                                loggedExercises.forEach { exLog ->
                                    var setNum = 1
                                    exLog.sets.forEach { s ->
                                        val w = s.weight.trim().toDoubleOrNull()
                                        val r = s.reps.trim().toIntOrNull()
                                        if (w != null || r != null || s.notes.isNotBlank()) {
                                            setList.add(
                                                WorkoutSet(
                                                    id = "s-${nowMs.toString(36)}-${globalIdx++}",
                                                    workoutId = workoutId,
                                                    date = newWorkout.date,
                                                    exercise = exLog.exerciseName,
                                                    setNo = setNum++,
                                                    weight = w,
                                                    reps = r,
                                                    notes = s.notes.trim()
                                                )
                                            )
                                        }
                                    }
                                }

                                if (setList.isNotEmpty()) {
                                    onSaveWorkout(newWorkout, setList)
                                    onDiscardDraft()
                                    loggedExercises.clear()
                                    workoutTitle = ""
                                    workoutNotes = ""
                                    onNavigateHistory()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(50.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = Color(0xFF140D07),
                            disabledContainerColor = SurfaceDark,
                            disabledContentColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = loggedExercises.isNotEmpty()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Finish Workout",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            if (totalLoggedSets > 0) {
                                Text(
                                    text = "$totalLoggedSets sets • ${formatWeightValue(totalSessionTonnage)}kg",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF140D07).copy(alpha = 0.85f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        var showNotesInput by remember { mutableStateOf(workoutNotes.isNotBlank()) }
        var sessionExpandedWhenActive by remember { mutableStateOf(false) }

        val headerBlock: @Composable () -> Unit = {
            ModernScreenHeader(
                title = "Log Workout",
                badgeText = if (loggedExercises.isNotEmpty()) "${loggedExercises.size} lifts" else null,
                action = {
                    if (loggedExercises.isNotEmpty() || workoutTitle.isNotBlank()) {
                        IconButton(
                            onClick = { showDiscardConfirm = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Discard Draft",
                                tint = DecreaseRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            )
        }

        val sessionEditorFields: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDark,
                        border = BorderStroke(0.8.dp, BorderDark),
                        modifier = Modifier
                            .clickable { showDateDialog = true }
                            .height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Date",
                                tint = AccentPeach,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = workoutDate,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }

                    TextButton(
                        onClick = { showNotesInput = !showNotesInput },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (showNotesInput || workoutNotes.isNotBlank()) "Hide note" else "+ Add note",
                            fontSize = 12.sp,
                            color = AccentPeach
                        )
                    }
                }

                // Workout Title Input
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgDark,
                    border = BorderStroke(0.8.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (workoutTitle.isEmpty()) {
                            Text(
                                text = "Workout Title (e.g. Push, Pull, Full Body)",
                                color = TextMuted,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = workoutTitle,
                            onValueChange = { workoutTitle = it },
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(AccentOrange),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Quick Split Preset Pills
                val splitPresets = listOf("Push", "Pull", "Legs", "Full Body", "Upper", "Lower")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(splitPresets) { split ->
                        val isSelected = workoutTitle.contains(split, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AccentOrange.copy(alpha = 0.2f) else BgDark,
                            border = BorderStroke(0.8.dp, if (isSelected) AccentOrange else BorderDark),
                            modifier = Modifier.clickable {
                                workoutTitle = if (workoutTitle.isBlank()) split else "$split Session"
                            }
                        ) {
                            Text(
                                text = split,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AccentOrange else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Collapsible Session Notes
                if (showNotesInput || workoutNotes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDark,
                        border = BorderStroke(0.8.dp, BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (workoutNotes.isEmpty()) {
                                Text(
                                    text = "Session notes / energy level...",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            BasicTextField(
                                value = workoutNotes,
                                onValueChange = { workoutNotes = it },
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(AccentOrange),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        val sessionBlock: @Composable () -> Unit = {
            if (loggedExercises.isEmpty()) {
                StrengthCard(shape = RoundedCornerShape(16.dp)) {
                    sessionEditorFields()
                }
            } else {
                StrengthCard(
                    shape = RoundedCornerShape(14.dp),
                    onClick = { sessionExpandedWhenActive = !sessionExpandedWhenActive }
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
                                text = workoutTitle.ifBlank { "Untitled Session" },
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "• $workoutDate",
                                fontSize = 12.5.sp,
                                color = AccentPeach,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = if (sessionExpandedWhenActive) "Done" else "Edit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                    if (sessionExpandedWhenActive) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                        Spacer(Modifier.height(10.dp))
                        sessionEditorFields()
                    }
                }
            }
        }
        if (loggedExercises.isEmpty()) {
            // READY STATE: header, session card and "Ready to Lift" card fill the whole screen (no gaps)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                headerBlock()
                sessionBlock()

                StrengthCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AccentOrange.copy(alpha = 0.2f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Ready to Lift",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Choose a movement to begin logging sets",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { showExercisePicker = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentOrange,
                                    contentColor = Color(0xFF140D07)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Lift", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateProgram,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AccentPeach, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Load Program", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = onNavigateSuggest,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPR, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("AI Suggest", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        // Quick add recent exercises, laid out as an evenly spaced 2-column grid
                        val recentLifts = remember(getRecentExercises) {
                            getRecentExercises?.invoke()?.ifEmpty { null }
                                ?: listOf("Bench Press", "Squat", "Deadlift", "Overhead Press", "Pull Ups", "Barbell Row")
                        }
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Quick Add Recent Exercises",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                            recentLifts.take(6).chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { liftName ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = BgDark,
                                            border = BorderStroke(1.dp, BorderDark),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clickable { addExerciseToSession(liftName) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = AccentPeach, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = liftName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(6.dp)) }

            item { headerBlock() }

            // DRAFT AUTO-SAVED RESTORED BANNER
            if (initialDraft != null && (initialDraft.exercises.isNotEmpty() || initialDraft.title.isNotBlank())) {
                item {
                    Surface(
                        color = AccentPeach.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentPeach.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("💾 Active Session Restored", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentPeach)
                                Text("• ${loggedExercises.size} exercise${if (loggedExercises.size == 1) "" else "s"}", fontSize = 11.5.sp, color = TextSecondary)
                            }
                            TextButton(
                                onClick = { showDiscardConfirm = true },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Discard", fontSize = 11.5.sp, color = DecreaseRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item { sessionBlock() }

            // EXERCISE CARDS WITH SYMMETRICAL INPUTS, PR RADAR & ERGONOMIC BUTTONS
            itemsIndexed(
                items = loggedExercises,
                key = { _, exLog -> "${exLog.exerciseName}-${exLog.hashCode()}" }
            ) { exIndex, exLog ->
                StrengthCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        )
                ) {
                    Column {
                        // EXERCISE CARD HEADER
                        var showExMenu by remember { mutableStateOf(false) }
                        val activeExGoal = getActiveGoalForExercise(exLog.exerciseName)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = exLog.exerciseName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    CategoryPill(category = exLog.category)
                                }

                                if (exLog.previousSets.isNotEmpty()) {
                                    Text(
                                        text = "Last (${exLog.previousDate}): ${exLog.previousSummary}",
                                        fontSize = 11.5.sp,
                                        color = AccentPeach,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clickable {
                                                val compSets = exLog.previousSets.filter { (it.reps ?: 0) > 0 }
                                                if (compSets.isNotEmpty()) {
                                                    exLog.sets.clear()
                                                    compSets.forEach { s ->
                                                        exLog.sets.add(
                                                            SetInputState(
                                                                initialWeight = formatWeightValue(s.weight),
                                                                initialReps = s.reps?.toString() ?: ""
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (activeExGoal != null) {
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = GoldPR.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, GoldPR.copy(alpha = 0.7f)),
                                        modifier = Modifier.clickable {
                                            goalProgressTargetExLog = exLog
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EmojiEvents,
                                                contentDescription = "Goal Progress",
                                                tint = GoldPR,
                                                modifier = Modifier.size(13.5.dp)
                                            )
                                            Text(
                                                text = "Goal Progress",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = GoldPR,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { showExMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Exercise Options",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showExMenu,
                                        onDismissRequest = { showExMenu = false },
                                        modifier = Modifier.background(SurfaceDark)
                                    ) {
                                        if (activeExGoal != null) {
                                            DropdownMenuItem(
                                                text = { Text("Goal Progress & Report", color = GoldPR, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldPR, modifier = Modifier.size(17.dp))
                                                },
                                                onClick = {
                                                    showExMenu = false
                                                    goalProgressTargetExLog = exLog
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Plate Calculator", color = TextPrimary, fontSize = 13.5.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(17.dp))
                                            },
                                            onClick = {
                                                showExMenu = false
                                                plateCalcTargetSet = exLog.sets.firstOrNull()
                                                showPlateCalculator = true
                                            }
                                        )
                                        if (exLog.previousSets.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Copy Last Session", color = TextPrimary, fontSize = 13.5.sp) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentPeach, modifier = Modifier.size(17.dp))
                                                },
                                                onClick = {
                                                    showExMenu = false
                                                    val compSets = exLog.previousSets.filter { (it.reps ?: 0) > 0 }
                                                    if (compSets.isNotEmpty()) {
                                                        exLog.sets.clear()
                                                        compSets.forEach { s ->
                                                            exLog.sets.add(
                                                                SetInputState(
                                                                    initialWeight = formatWeightValue(s.weight),
                                                                    initialReps = s.reps?.toString() ?: ""
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Remove Exercise", color = DecreaseRed, fontSize = 13.5.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = DecreaseRed, modifier = Modifier.size(17.dp))
                                            },
                                            onClick = {
                                                showExMenu = false
                                                loggedExercises.removeAt(exIndex)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // SET TABLE HEADER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SET",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(30.dp)
                            )
                            Spacer(Modifier.width(6.dp))

                            Text(
                                text = "PREV",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(64.dp)
                            )
                            Spacer(Modifier.width(6.dp))

                            Text(
                                text = "WEIGHT (kg)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))

                            Text(
                                text = "REPS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))

                            Text(
                                text = "DONE",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(36.dp)
                            )

                            Spacer(Modifier.width(26.dp)) // Delete spacer
                        }

                        Spacer(Modifier.height(4.dp))

                        // SET ROWS
                        val cardBwKg = remember(workoutDate, exLog.isBodyweight) {
                            if (exLog.isBodyweight) BodyWeightUtils.weightOn(workoutDate) else 0.0
                        }
                        exLog.sets.forEachIndexed { setIdx, sState ->
                            val prevSetMatch = exLog.previousSets.getOrNull(setIdx)
                            val prevString = if (prevSetMatch != null && (prevSetMatch.reps ?: 0) > 0) {
                                "${formatWeightValue(prevSetMatch.weight)}×${prevSetMatch.reps}"
                            } else "–"

                            val wVal = sState.weight.trim().toDoubleOrNull() ?: 0.0
                            val rVal = sState.reps.trim().toIntOrNull() ?: 0
                            val isFailedAttempt = sState.reps.isNotBlank() && rVal <= 0
                            val liveLoad = BodyWeightUtils.totalLoad(exLog.isBodyweight, wVal, cardBwKg)
                            val liveE1rm = if (liveLoad > 0.0 && rVal > 0) computeEpley(liveLoad, rVal) else 0.0

                            // PR Radar Detection (strictly invalidated if rVal <= 0)
                            val isE1rmPr = rVal > 0 && exLog.peakE1rm > 0.0 && liveE1rm > (exLog.peakE1rm + 0.1)
                            val isWeightPr = rVal > 0 && exLog.peakWeight > 0.0 && wVal > exLog.peakWeight

                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Set Number
                                    Text(
                                        text = "${setIdx + 1}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (sState.isCompleted) IncreaseGreen else AccentPeach,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(30.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))

                                    // Previous Ghost Column (Tap to auto-fill)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BgDark,
                                        border = BorderStroke(0.6.dp, BorderDark),
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(38.dp)
                                            .clickable {
                                                if (prevSetMatch != null) {
                                                    sState.weight = formatWeightValue(prevSetMatch.weight)
                                                    sState.reps = prevSetMatch.reps?.toString() ?: ""
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = prevString,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (prevString != "–") TextSecondary else BorderDark,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))

                                    // WEIGHT TEXT BOX
                                    TapValueBox(
                                        value = sState.weight,
                                        placeholder = "kg",
                                        onClick = { pickerTarget = SetPickerTarget(exLog, sState, setIdx, focusWeight = true) },
                                        isHighlightedPr = isE1rmPr || isWeightPr,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(Modifier.width(6.dp))

                                    // REPS TEXT BOX
                                    TapValueBox(
                                        value = sState.reps,
                                        placeholder = "reps",
                                        onClick = { pickerTarget = SetPickerTarget(exLog, sState, setIdx, focusWeight = false) },
                                        isHighlightedPr = isE1rmPr,
                                        isFailedAttempt = isFailedAttempt,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(Modifier.width(6.dp))

                                    // Animated Bouncy Completion Checkmark
                                    SetCompletionCheckButton(
                                        isCompleted = sState.isCompleted,
                                        onToggle = { sState.isCompleted = !sState.isCompleted }
                                    )

                                    // Delete Set Button
                                    IconButton(
                                        onClick = {
                                            exLog.sets.removeAt(setIdx)
                                            if (exLog.sets.isEmpty()) {
                                                loggedExercises.removeAt(exIndex)
                                            }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Delete Set",
                                            tint = DecreaseRed.copy(alpha = 0.7f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // LIVE PR RADAR OR FAILED SET NOTICE (only shown when triggered; normal E1RM lives in card footer)
                                if (isFailedAttempt) {
                                    Surface(
                                        color = DecreaseRed.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, DecreaseRed.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .padding(start = 36.dp, top = 2.dp)
                                            .fillMaxWidth(0.85f)
                                    ) {
                                        Text(
                                            text = "✕ FAILED ATTEMPT (0 reps) — excluded from PRs & volume",
                                            color = DecreaseRed,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (isE1rmPr || isWeightPr) {
                                    val prText = if (isE1rmPr) {
                                        "🏆 NEW ALL-TIME PR! ${formatWeightValue(liveE1rm)}kg Est. 1RM (+${formatWeightValue(liveE1rm - exLog.peakE1rm)}kg)"
                                    } else {
                                        "🏆 NEW TOP WEIGHT! ${formatWeightValue(wVal)}kg"
                                    }
                                    Surface(
                                        color = GoldPR.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, GoldPR.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .padding(start = 36.dp, top = 2.dp)
                                            .fillMaxWidth(0.85f)
                                    ) {
                                        Text(
                                            text = prText,
                                            color = GoldPR,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Single concise card-level Live E1RM summary
                        val bestCardE1rm = exLog.sets.maxOfOrNull { s ->
                            val w = s.weight.trim().toDoubleOrNull() ?: 0.0
                            val r = s.reps.trim().toIntOrNull() ?: 0
                            val load = BodyWeightUtils.totalLoad(exLog.isBodyweight, w, cardBwKg)
                            if (load > 0.0 && r > 0) computeEpley(load, r) else 0.0
                        } ?: 0.0
                        if (bestCardE1rm > 0.0) {
                            Text(
                                text = if (exLog.isBodyweight)
                                    "Best Est. 1RM: ${formatWeightValue(bestCardE1rm)} kg • ${"%.2f".format(BodyWeightUtils.relativeStrength(bestCardE1rm, cardBwKg))}× BW (BW ${formatWeightValue(cardBwKg)}kg)"
                                else "Best Est. 1RM: ${formatWeightValue(bestCardE1rm)} kg",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        // LIVE GOAL PROGRESS BANNER WHEN SETS ARE LOGGED FOR AN EXERCISE WITH AN ACTIVE GOAL
                        if (activeExGoal != null) {
                            val liveEval = remember(activeExGoal, workouts, exLog.allTimeSets, availableExercises, bodyWeights) {
                                GoalEngine.evaluateGoal(
                                    goal = activeExGoal,
                                    workouts = workouts,
                                    exerciseSets = exLog.allTimeSets,
                                    allSets = allSets.ifEmpty { exLog.allTimeSets },
                                    exercisesCatalog = availableExercises,
                                    bodyWeights = bodyWeights
                                )
                            }
                            val liveSetTriples = exLog.sets.map { s ->
                                Triple(
                                    s.weight.trim().toDoubleOrNull(),
                                    s.reps.trim().toIntOrNull(),
                                    s.isCompleted
                                )
                            }
                            val liveSessionAnalysis = GoalEngine.evaluateLiveSession(
                                evaluation = liveEval,
                                liveSets = liveSetTriples,
                                sessionDate = workoutDate
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (liveSessionAnalysis.achievedGoalToday) GoldPR.copy(alpha = 0.16f) else BgDark,
                                border = BorderStroke(
                                    0.8.dp,
                                    if (liveSessionAnalysis.achievedGoalToday) GoldPR else AccentOrange.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clickable { goalProgressTargetExLog = exLog }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "🎯 ${liveSessionAnalysis.sessionGrade} • ${formatWeightValue(liveSessionAnalysis.updatedProgressPct)}% of Goal",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (liveSessionAnalysis.achievedGoalToday) GoldPR else AccentOrange
                                        )
                                        Text(
                                            text = liveSessionAnalysis.nextSetTargetText,
                                            fontSize = 10.5.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Report →",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPR,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // ERGONOMIC BUTTONS: ADD SET & TACTILE STEPPERS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "+ Add Set" pill button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgDark,
                                border = BorderStroke(1.dp, AccentPeach.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable {
                                        val lastSet = exLog.sets.lastOrNull()
                                        exLog.sets.add(
                                            SetInputState(
                                                initialWeight = lastSet?.weight ?: "",
                                                initialReps = lastSet?.reps ?: ""
                                            )
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = AccentPeach,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Add Set",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPeach
                                    )
                                }
                            }

                            // Tactile Increment Chips (+1.25kg, +2.5kg, +5kg, +1 rep)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("+1.25" to 1.25, "+2.5" to 2.5, "+5" to 5.0).forEach { (lbl, inc) ->
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = BgDark,
                                        border = BorderStroke(0.6.dp, BorderDark),
                                        modifier = Modifier.clickable {
                                            // Apply to every open (not yet completed) set of this exercise
                                            exLog.sets.filter { !it.isCompleted }.forEach { it.weight = SetUtils.addToWeight(it.weight, inc) }
                                        }
                                    ) {
                                        Text(
                                            text = "${lbl}k",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = BgDark,
                                    border = BorderStroke(0.6.dp, BorderDark),
                                    modifier = Modifier.clickable {
                                        exLog.sets.filter { !it.isCompleted }.forEach { it.reps = SetUtils.addToReps(it.reps, 1) }
                                    }
                                ) {
                                    Text(
                                        text = "+1r",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
        }
    }

    // WEIGHT / REPS WHEEL PICKER (keyboard is the default only for the weight of a brand-new exercise)
    pickerTarget?.let { target ->
        val ex = target.exLog
        val prev = ex.previousSets.filter { (it.reps ?: 0) > 0 }
        val hintSet = prev.getOrNull(target.setIdx) ?: prev.lastOrNull()
        val isNewExercise = ex.previousSets.isEmpty() && ex.allTimeSets.isEmpty()
        key(target) {
            SetValuePickerDialog(
                initialWeight = target.set.weight,
                initialReps = target.set.reps,
                hintWeight = hintSet?.weight,
                hintReps = hintSet?.reps,
                focusWeightFirst = target.focusWeight,
                startInKeyboard = target.focusWeight && isNewExercise,
                onConfirm = { w, r ->
                    target.set.weight = w
                    target.set.reps = r
                    pickerTarget = null
                },
                onDismiss = { pickerTarget = null }
            )
        }
    }

    // BARBELL & ADJUSTABLE DUMBBELL PLATE CALCULATOR MODAL
    if (showPlateCalculator) {
        val targetWeight = plateCalcTargetSet?.weight?.toDoubleOrNull() ?: 60.0
        PlateCalculatorDialog(
            initialWeight = targetWeight,
            onDismiss = { showPlateCalculator = false },
            onApplyWeight = { newWeight ->
                plateCalcTargetSet?.weight = formatWeightValue(newWeight)
            }
        )
    }

    // GOAL PROGRESS & SESSION PRESCRIPTION POPUP FOR ACTIVE EXERCISE
    goalProgressTargetExLog?.let { targetExLog ->
        val exGoal = getActiveGoalForExercise(targetExLog.exerciseName)
        if (exGoal != null) {
            // Merge any completed/valid sets from the current logger card into a synthetic preview workout so the report reflects today's logged sets too!
            val liveWorkSets = targetExLog.sets.mapIndexedNotNull { idx, s ->
                val w = s.weight.trim().toDoubleOrNull()
                val r = s.reps.trim().toIntOrNull()
                if ((r ?: 0) > 0) {
                    WorkoutSet(
                        id = "live-s-$idx",
                        workoutId = "live-session",
                        date = workoutDate,
                        exercise = targetExLog.exerciseName,
                        setNo = idx + 1,
                        weight = w,
                        reps = r,
                        notes = s.notes
                    )
                } else null
            }
            val previewWorkouts = if (liveWorkSets.isNotEmpty()) {
                listOf(Workout(id = "live-session", date = workoutDate, title = workoutTitle.ifBlank { "Today's Session (Live)" })) + workouts
            } else workouts
            val previewExSets = targetExLog.allTimeSets + liveWorkSets

            val baseEval = GoalEngine.evaluateGoal(
                goal = exGoal,
                workouts = workouts,
                exerciseSets = targetExLog.allTimeSets,
                allSets = allSets.ifEmpty { targetExLog.allTimeSets },
                exercisesCatalog = availableExercises,
                bodyWeights = bodyWeights
            )
            val liveAnalysis = GoalEngine.evaluateLiveSession(
                evaluation = baseEval,
                liveSets = targetExLog.sets.map {
                    Triple(it.weight.trim().toDoubleOrNull(), it.reps.trim().toIntOrNull(), it.isCompleted)
                },
                sessionDate = workoutDate
            )
            val updatedReport = GoalEngine.generateExerciseReport(
                goal = exGoal,
                workouts = previewWorkouts,
                exerciseSets = previewExSets,
                allSets = allSets + liveWorkSets,
                exercisesCatalog = availableExercises,
                bodyWeights = bodyWeights
            )

            GoalProgressSessionDialog(
                liveAnalysis = liveAnalysis,
                exerciseReport = updatedReport,
                onApplyPrescribedSetsToCard = {
                    val prescribed = GoalEngine.prescribedSetsForLogger(baseEval)
                    if (prescribed.isNotEmpty()) {
                        targetExLog.sets.clear()
                        prescribed.forEach { (wStr, rStr, noteStr) ->
                            targetExLog.sets.add(
                                SetInputState(
                                    initialWeight = wStr,
                                    initialReps = rStr,
                                    initialNotes = noteStr
                                )
                            )
                        }
                    }
                },
                onDismiss = { goalProgressTargetExLog = null }
            )
        } else {
            goalProgressTargetExLog = null
        }
    }

    // POST-WORKOUT UPDATED GOAL REPORT MODAL (shown when sets are logged & saved for a goal exercise)
    postWorkoutGoalReportExName?.let { savedExName ->
        val updatedGoal = getActiveGoalForExercise(savedExName)
        if (updatedGoal != null) {
            val savedExSets = getAllSetsForExercise?.invoke(savedExName) ?: emptyList()
            val report = GoalEngine.generateExerciseReport(
                goal = updatedGoal,
                workouts = workouts,
                exerciseSets = savedExSets,
                allSets = allSets.ifEmpty { savedExSets },
                exercisesCatalog = availableExercises,
                bodyWeights = bodyWeights
            )
            ExerciseGoalReportDialog(
                report = report,
                onDismiss = {
                    postWorkoutGoalReportExName = null
                    onNavigateHistory()
                }
            )
        } else {
            postWorkoutGoalReportExName = null
            onNavigateHistory()
        }
    }

    // DATE SWITCHER DIALOG
    if (showDateDialog) {
        AlertDialog(
            onDismissRequest = { showDateDialog = false },
            title = {
                Text("Select Workout Date", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val today = DateUtils.todayDdMonYyyy()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (workoutDate == today) AccentOrange.copy(alpha = 0.2f) else BgDark,
                        border = BorderStroke(1.dp, if (workoutDate == today) AccentOrange else BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                workoutDate = today
                                showDateDialog = false
                            }
                    ) {
                        Text(
                            text = "Today ($today)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (workoutDate == today) AccentOrange else TextPrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    OutlinedTextField(
                        value = workoutDate,
                        onValueChange = { workoutDate = it },
                        label = { Text("Custom Date (DD-MON-YYYY)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = BorderDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDateDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07))
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // DISCARD DRAFT CONFIRMATION DIALOG
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = {
                Text("Discard Active Workout?", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "This will erase the active draft and all entered sets. You cannot undo this action.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirm = false
                        loggedExercises.clear()
                        workoutTitle = ""
                        workoutNotes = ""
                        workoutDate = DateUtils.todayDdMonYyyy()
                        onDiscardDraft()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DecreaseRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Discard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // EXERCISE PICKER DIALOG
    if (showExercisePicker) {
        val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Custom")

        AlertDialog(
            onDismissRequest = {
                showExercisePicker = false
                exerciseSearchQuery = ""
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showExercisePicker = false
                    exerciseSearchQuery = ""
                }) { Text("Cancel", color = TextSecondary) }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Exercise", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    TextButton(onClick = {
                        showExercisePicker = false
                        onNavigateExercises()
                    }) {
                        Text("Manage →", color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.height(440.dp)) {
                    ModernSearchBar(
                        query = exerciseSearchQuery,
                        onQueryChange = { exerciseSearchQuery = it },
                        placeholder = "Search or type new movement..."
                    )

                    Spacer(Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategoryFilter == cat
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AccentOrange else SurfaceCard,
                                border = BorderStroke(1.dp, if (isSelected) AccentOrange else BorderDark),
                                modifier = Modifier.clickable { selectedCategoryFilter = cat }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF140D07) else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val trimmedQuery = exerciseSearchQuery.trim()
                    val exactMatch = remember(availableExercises, trimmedQuery) {
                        trimmedQuery.isNotEmpty() && availableExercises.any { it.name.equals(trimmedQuery, ignoreCase = true) }
                    }
                    if (trimmedQuery.isNotEmpty() && !exactMatch) {
                        Button(
                            onClick = {
                                val newEx = Exercise(name = trimmedQuery, category = "Custom")
                                onAddCustomExercise(newEx)
                                addExerciseToSession(trimmedQuery)
                                showExercisePicker = false
                                exerciseSearchQuery = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add \"$trimmedQuery\" as custom exercise")
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    val filtered = remember(availableExercises, selectedCategoryFilter, trimmedQuery) {
                        availableExercises.filter { ex ->
                            val matchesCat = selectedCategoryFilter == "All" || ex.category.equals(selectedCategoryFilter, ignoreCase = true)
                            val matchesSearch = trimmedQuery.isEmpty() || ex.name.contains(trimmedQuery, ignoreCase = true) || ex.category.contains(trimmedQuery, ignoreCase = true)
                            matchesCat && matchesSearch
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(filtered, key = { idx, exercise -> "${exercise.name}_$idx" }) { _, exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        addExerciseToSession(exercise.name)
                                        showExercisePicker = false
                                        exerciseSearchQuery = ""
                                    }
                                    .padding(vertical = 11.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(exercise.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.5.sp)
                                CategoryPill(category = exercise.category)
                            }
                            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                        }
                    }
                }
            }
        )
    }
}

/**
 * Custom centered, symmetrical numeric input box with consistent 38dp height,
 * clear placeholder and subtle focus borders.
 */
@Composable
fun SymmetricalNumericInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isHighlightedPr: Boolean = false,
    isFailedAttempt: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isFailedAttempt -> DecreaseRed
            isHighlightedPr -> GoldPR
            isFocused -> AccentOrange
            else -> BorderDark
        },
        label = "inputBorder"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgDark,
        border = BorderStroke(if (isFocused || isHighlightedPr || isFailedAttempt) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.height(38.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextMuted,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = when {
                        isFailedAttempt -> DecreaseRed
                        isHighlightedPr -> GoldPR
                        else -> TextPrimary
                    },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                cursorBrush = SolidColor(AccentOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
    }
}
