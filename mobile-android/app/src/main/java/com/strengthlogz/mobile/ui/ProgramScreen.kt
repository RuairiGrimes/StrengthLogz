package com.strengthlogz.mobile.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ProgramDay
import com.strengthlogz.mobile.model.ProgramExerciseItem
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutProgram
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.SetUtils
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue

@Composable
fun ProgramScreen(
    programs: List<WorkoutProgram>,
    activeProgramId: String?,
    availableExercises: List<Exercise>,
    workouts: List<Workout>,
    allSets: List<WorkoutSet>,
    getPreviousSets: (String) -> List<WorkoutSet>,
    onSaveProgram: (WorkoutProgram) -> Unit,
    onCreateProgram: (String) -> Unit,
    onDeleteProgram: (String) -> Unit,
    onSelectProgram: (String) -> Unit,
    onDuplicateProgram: (String) -> Unit,
    onAddCustomExercise: (Exercise) -> Unit,
    onLoadProgramDayToLogger: (SuggestedWorkoutPlan) -> Unit
) {
    var showProgramSwitcher by remember { mutableStateOf(false) }
    var showCreateProgramDialog by remember { mutableStateOf(false) }
    var programToRename by remember { mutableStateOf<WorkoutProgram?>(null) }
    var programToDelete by remember { mutableStateOf<WorkoutProgram?>(null) }

    var showAddDayDialog by remember { mutableStateOf(false) }
    var dayToEdit by remember { mutableStateOf<ProgramDay?>(null) }
    var showExercisePicker by remember { mutableStateOf(false) }

    val activeProgram = remember(programs, activeProgramId) {
        programs.firstOrNull { it.id == activeProgramId } ?: programs.firstOrNull()
    }

    val activeDays = activeProgram?.safeDays ?: emptyList()
    var selectedDayIdx by remember(activeProgram?.id, activeDays.size) {
        val lastIdx = activeProgram?.lastLoadedDayIndex ?: -1
        val initial = if (lastIdx in 0 until activeDays.size) (lastIdx + 1) % activeDays.size else 0
        mutableIntStateOf(initial.coerceIn(0, (activeDays.size - 1).coerceAtLeast(0)))
    }

    // Ensure selectedDayIdx is valid
    val safeDayIdx = if (activeDays.isEmpty()) 0 else selectedDayIdx.coerceIn(0, activeDays.size - 1)
    val activeDay = activeDays.getOrNull(safeDayIdx)
    val activeExercises = activeDay?.safeExercises ?: emptyList()

    fun updateActiveDay(updatedDay: ProgramDay) {
        if (activeProgram == null) return
        val newDays = activeDays.mapIndexed { idx, d ->
            if (idx == safeDayIdx) updatedDay.copy(dayIndex = idx) else d
        }
        onSaveProgram(activeProgram.copy(days = newDays))
    }

    fun loadDayIntoLogger(day: ProgramDay) {
        if (activeProgram == null || day.safeExercises.isEmpty()) return
        val suggestedItems = day.safeExercises.map { item ->
            val prevSets = getPreviousSets(item.exerciseName).filter { (it.reps ?: 0) > 0 }
            val lastWeight = prevSets.maxOfOrNull { it.weight ?: 0.0 } ?: 0.0
            val lastSummary = if (prevSets.isNotEmpty()) {
                SetUtils.formatSetsSummary(prevSets)
            } else "First time logging"
            val lastDate = prevSets.firstOrNull()?.date?.let { DateUtils.toDdMonYyyy(it) } ?: "New"

            val prescribedSets = (0 until item.targetSets.coerceIn(1, 10)).map { setIdx ->
                val prevWeightForSet = prevSets.getOrNull(setIdx)?.weight ?: lastWeight
                val weightStr = if (prevWeightForSet > 0.0) formatWeightValue(prevWeightForSet) else ""
                PrescribedSet(
                    weight = weightStr,
                    reps = item.targetReps.toString(),
                    notes = ""
                )
            }

            SuggestedExerciseItem(
                slotTitle = day.dayName,
                exerciseName = item.exerciseName,
                category = item.category.ifBlank { "Other" },
                lastSessionDate = lastDate,
                lastSummary = lastSummary,
                peakE1rm = 0.0,
                currentE1rm = 0.0,
                reasonTag = "Target: ${item.targetSets} × ${item.targetReps}",
                targetSummary = "${item.targetSets} sets × ${item.targetReps} reps",
                prescribedSets = prescribedSets
            )
        }

        val updatedProg = activeProgram.copy(
            days = activeDays,
            lastLoadedDayIndex = safeDayIdx
        )
        onSaveProgram(updatedProg)

        val plan = SuggestedWorkoutPlan(
            split = WorkoutSplitType.FULL_BODY,
            title = day.dayName,
            notes = if (day.safeFocus.isNotBlank()) "Program Focus: ${day.safeFocus}" else "Loaded from ${activeProgram.safeProgramName}",
            exercises = suggestedItems
        )
        onLoadProgramDayToLogger(plan)
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            if (activeDay != null && activeExercises.isNotEmpty()) {
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeDay.dayName,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeExercises.size} exercise${if (activeExercises.size == 1) "" else "s"} • ${activeExercises.sumOf { it.targetSets }} working sets",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = { loadDayIntoLogger(activeDay) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentOrange,
                                contentColor = Color(0xFF140D07)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Workout", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgDark),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Program Switcher & Actions
            item(key = "program_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeProgram != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { showProgramSwitcher = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = activeProgram.safeProgramName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Program",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Workout Program",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeProgram != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceCard,
                                border = BorderStroke(0.8.dp, BorderDark),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { showProgramSwitcher = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Manage Programs",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentOrange,
                            modifier = Modifier
                                .height(34.dp)
                                .clickable { showCreateProgramDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Program",
                                    tint = Color(0xFF140D07),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "New Program",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF140D07)
                                )
                            }
                        }
                    }
                }
            }

            // Case 1: No Programs Exist
            if (activeProgram == null) {
                item(key = "no_programs_empty_state") {
                    StrengthCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AccentOrange.copy(alpha = 0.16f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Build Your Custom Program",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )

                            Text(
                                text = "Create your training split from scratch. Add your workout days, customize exercises, and set target sets & reps.",
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(Modifier.height(4.dp))

                            Button(
                                onClick = { showCreateProgramDialog = true },
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
                                Text("Create Your First Program", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                // Days Row / Selector Tabs
                item(key = "program_days_selector") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WORKOUT DAYS (${activeDays.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.6.sp
                            )

                            Text(
                                text = "+ Add Day",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentOrange,
                                modifier = Modifier
                                    .clickable { showAddDayDialog = true }
                                    .padding(4.dp)
                            )
                        }

                        if (activeDays.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(activeDays) { idx, day ->
                                    val isSelected = idx == safeDayIdx
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) AccentOrange else SurfaceCard,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) AccentOrange else BorderDark
                                        ),
                                        modifier = Modifier.clickable { selectedDayIdx = idx }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = day.dayName,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                color = if (isSelected) Color(0xFF140D07) else TextPrimary
                                            )
                                            if (day.safeExercises.isNotEmpty()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) Color(0xFF140D07).copy(alpha = 0.2f) else BgDark
                                                ) {
                                                    Text(
                                                        text = "${day.safeExercises.size}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) Color(0xFF140D07) else TextSecondary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = SurfaceDark,
                                        border = BorderStroke(1.dp, BorderDark),
                                        modifier = Modifier.clickable { showAddDayDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add Day",
                                                tint = AccentPeach,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Add Day",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentPeach
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Day Content
                if (activeDay == null || activeDays.isEmpty()) {
                    item(key = "no_days_empty_state") {
                        StrengthCard(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No Workout Days Yet",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Add your first training day (e.g. Day 1: Push, Upper Body, or Full Body) to start programming.",
                                    fontSize = 12.5.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showAddDayDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentOrange,
                                        contentColor = Color(0xFF140D07)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("+ Add Workout Day", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Day Header & Edit Day Action
                    item(key = "active_day_banner_${activeDay.dayIndex}") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = BorderStroke(0.8.dp, BorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = activeDay.dayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (activeDay.safeFocus.isNotBlank()) {
                                        Text(
                                            text = activeDay.safeFocus,
                                            fontSize = 11.5.sp,
                                            color = AccentPeach,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgDark,
                                    border = BorderStroke(0.6.dp, BorderDark),
                                    modifier = Modifier.clickable { dayToEdit = activeDay }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Day",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Edit Day",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Exercises in Active Day
                    if (activeExercises.isEmpty()) {
                        item(key = "day_exercises_empty") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BgDark,
                                border = BorderStroke(0.8.dp, BorderDark.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "No exercises in this day yet",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Add exercises from the catalog and set target sets & reps.",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Button(
                                        onClick = { showExercisePicker = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentOrange,
                                            contentColor = Color(0xFF140D07)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("+ Add Exercise to Day", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(activeExercises, key = { idx, item -> "${item.exerciseName}_$idx" }) { exIdx, item ->
                            val prevSets = getPreviousSets(item.exerciseName).filter { (it.reps ?: 0) > 0 }
                            val prevSummary = if (prevSets.isNotEmpty()) SetUtils.formatSetsSummary(prevSets) else "First time"
                            val isBw = BodyWeightUtils.isBodyweightExercise(item.exerciseName)

                            ProgramExerciseCard(
                                index = exIdx,
                                totalCount = activeExercises.size,
                                item = item,
                                previousSummary = prevSummary,
                                isBodyweight = isBw,
                                onUpdateSetsReps = { s, r ->
                                    val list = activeExercises.toMutableList()
                                    list[exIdx] = item.copy(targetSets = s, targetReps = r)
                                    updateActiveDay(activeDay.copy(exercises = list))
                                },
                                onMoveUp = {
                                    if (exIdx > 0) {
                                        val list = activeExercises.toMutableList()
                                        val itm = list.removeAt(exIdx)
                                        list.add(exIdx - 1, itm)
                                        updateActiveDay(activeDay.copy(exercises = list))
                                    }
                                },
                                onMoveDown = {
                                    if (exIdx < activeExercises.size - 1) {
                                        val list = activeExercises.toMutableList()
                                        val itm = list.removeAt(exIdx)
                                        list.add(exIdx + 1, itm)
                                        updateActiveDay(activeDay.copy(exercises = list))
                                    }
                                },
                                onRemove = {
                                    val list = activeExercises.toMutableList().apply { removeAt(exIdx) }
                                    updateActiveDay(activeDay.copy(exercises = list))
                                }
                            )
                        }

                        // Add Exercise Button below list
                        item(key = "add_exercise_button") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceDark,
                                border = BorderStroke(1.dp, BorderDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showExercisePicker = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "+ Add Exercise to ${activeDay.dayName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AccentOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Program Switcher & Manager Dialog
    if (showProgramSwitcher) {
        ProgramSwitcherDialog(
            programs = programs,
            activeProgramId = activeProgram?.id,
            onSelectProgram = { progId ->
                onSelectProgram(progId)
                showProgramSwitcher = false
            },
            onCreateNew = {
                showProgramSwitcher = false
                showCreateProgramDialog = true
            },
            onRenameProgram = { prog ->
                showProgramSwitcher = false
                programToRename = prog
            },
            onDuplicateProgram = { prog ->
                onDuplicateProgram(prog.id)
                showProgramSwitcher = false
            },
            onDeleteProgram = { prog ->
                showProgramSwitcher = false
                programToDelete = prog
            },
            onDismiss = { showProgramSwitcher = false }
        )
    }

    // Create New Program Dialog
    if (showCreateProgramDialog) {
        CreateOrRenameProgramDialog(
            title = "Create New Program",
            initialName = "",
            confirmButtonText = "Create Program",
            onConfirm = { name ->
                onCreateProgram(name)
                showCreateProgramDialog = false
            },
            onDismiss = { showCreateProgramDialog = false }
        )
    }

    // Rename Program Dialog
    programToRename?.let { prog ->
        CreateOrRenameProgramDialog(
            title = "Rename Program",
            initialName = prog.safeProgramName,
            confirmButtonText = "Save Name",
            onConfirm = { newName ->
                onSaveProgram(prog.copy(programName = newName.trim()))
                programToRename = null
            },
            onDismiss = { programToRename = null }
        )
    }

    // Delete Program Confirmation Dialog
    programToDelete?.let { prog ->
        AlertDialog(
            onDismissRequest = { programToDelete = null },
            containerColor = SurfaceCard,
            title = {
                Text("Delete Program?", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${prog.safeProgramName}\"? This cannot be undone.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProgram(prog.id)
                        programToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DecreaseRed)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { programToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Add Workout Day Dialog
    if (showAddDayDialog && activeProgram != null) {
        AddOrEditDayDialog(
            title = "Add Workout Day",
            initialName = "Day ${activeDays.size + 1}: ",
            initialFocus = "",
            canDelete = false,
            onSave = { name, focus ->
                val newDay = ProgramDay(
                    dayIndex = activeDays.size,
                    dayName = name.trim().ifBlank { "Day ${activeDays.size + 1}" },
                    focus = focus.trim(),
                    exercises = emptyList()
                )
                val updatedDays = activeDays + newDay
                onSaveProgram(activeProgram.copy(days = updatedDays))
                selectedDayIdx = updatedDays.size - 1
                showAddDayDialog = false
            },
            onDelete = {},
            onDismiss = { showAddDayDialog = false }
        )
    }

    // Edit Workout Day Dialog
    dayToEdit?.let { day ->
        AddOrEditDayDialog(
            title = "Edit Workout Day",
            initialName = day.dayName,
            initialFocus = day.safeFocus,
            canDelete = true,
            onSave = { newName, newFocus ->
                val updatedDays = activeDays.mapIndexed { idx, d ->
                    if (d.dayIndex == day.dayIndex) d.copy(dayName = newName.trim(), focus = newFocus.trim()) else d
                }
                onSaveProgram(activeProgram!!.copy(days = updatedDays))
                dayToEdit = null
            },
            onDelete = {
                val updatedDays = activeDays.filter { it.dayIndex != day.dayIndex }
                    .mapIndexed { idx, d -> d.copy(dayIndex = idx) }
                onSaveProgram(activeProgram!!.copy(days = updatedDays))
                selectedDayIdx = (safeDayIdx - 1).coerceAtLeast(0)
                dayToEdit = null
            },
            onDismiss = { dayToEdit = null }
        )
    }

    // Exercise Picker Modal
    if (showExercisePicker && activeDay != null) {
        ProgramExercisePickerDialog(
            availableExercises = availableExercises,
            alreadyAddedNames = activeExercises.map { it.exerciseName.lowercase() }.toSet(),
            onDismiss = { showExercisePicker = false },
            onSelectExercise = { ex, sets, reps ->
                val updatedList = activeExercises + ProgramExerciseItem(
                    exerciseName = ex.name,
                    category = ex.category.ifBlank { "Other" },
                    targetSets = sets,
                    targetReps = reps
                )
                updateActiveDay(activeDay.copy(exercises = updatedList))
                showExercisePicker = false
            },
            onCreateAndSelectExercise = { name, category, isBw, sets, reps ->
                val newEx = Exercise(name = name.trim(), category = category, bodyweight = isBw)
                onAddCustomExercise(newEx)
                val updatedList = activeExercises + ProgramExerciseItem(
                    exerciseName = newEx.name,
                    category = newEx.category,
                    targetSets = sets,
                    targetReps = reps
                )
                updateActiveDay(activeDay.copy(exercises = updatedList))
                showExercisePicker = false
            }
        )
    }
}

@Composable
private fun ProgramSwitcherDialog(
    programs: List<WorkoutProgram>,
    activeProgramId: String?,
    onSelectProgram: (String) -> Unit,
    onCreateNew: () -> Unit,
    onRenameProgram: (WorkoutProgram) -> Unit,
    onDuplicateProgram: (WorkoutProgram) -> Unit,
    onDeleteProgram: (WorkoutProgram) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Programs (${programs.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                if (programs.isEmpty()) {
                    Text(
                        text = "No programs stored yet. Create one below to get started.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(programs, key = { it.id }) { prog ->
                            val isActive = prog.id == activeProgramId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) AccentOrange.copy(alpha = 0.12f) else BgDark,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) AccentOrange else BorderDark
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectProgram(prog.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = prog.safeProgramName,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) AccentOrange else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isActive) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = AccentOrange,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${prog.safeDays.size} workout day${if (prog.safeDays.size == 1) "" else "s"}",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onDuplicateProgram(prog) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Duplicate",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRenameProgram(prog) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteProgram(prog) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
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

                Button(
                    onClick = onCreateNew,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color(0xFF140D07)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ Create New Program", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                }
            }
        }
    }
}

@Composable
private fun CreateOrRenameProgramDialog(
    title: String,
    initialName: String,
    confirmButtonText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameText by remember { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Program Name") },
                    placeholder = { Text("e.g. Push Pull Legs, Upper Lower, 5-Day Split") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nameText.isNotBlank()) onConfirm(nameText.trim())
                        },
                        enabled = nameText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = Color(0xFF140D07)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(confirmButtonText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOrEditDayDialog(
    title: String,
    initialName: String,
    initialFocus: String,
    canDelete: Boolean,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var dayName by remember { mutableStateOf(initialName) }
    var dayFocus by remember { mutableStateOf(initialFocus) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = dayName,
                    onValueChange = { dayName = it },
                    label = { Text("Day Name") },
                    placeholder = { Text("e.g. Day 1: Push, Upper Body, Chest & Triceps") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dayFocus,
                    onValueChange = { dayFocus = it },
                    label = { Text("Target Focus (Optional)") },
                    placeholder = { Text("e.g. Quad overload, Heavy bench progression") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canDelete) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = DecreaseRed)
                        ) {
                            Text("Delete Day", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                if (dayName.isNotBlank()) onSave(dayName.trim(), dayFocus.trim())
                            },
                            enabled = dayName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentOrange,
                                contentColor = Color(0xFF140D07)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramExerciseCard(
    index: Int,
    totalCount: Int,
    item: ProgramExerciseItem,
    previousSummary: String,
    isBodyweight: Boolean,
    onUpdateSetsReps: (Int, Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    StrengthCard(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Row: Index badge, Exercise Name, Category Pill, Remove Button
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
                    Surface(
                        shape = CircleShape,
                        color = AccentOrange.copy(alpha = 0.18f),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentOrange
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.exerciseName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Last: $previousSummary",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CategoryPill(category = item.category.ifBlank { "Other" })

                    if (isBodyweight) {
                        Surface(
                            color = GoldPR.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, GoldPR.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "BW",
                                color = GoldPR,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Row: Sets Stepper, Reps Stepper, Reorder & Delete Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sets & Reps Steppers
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactStepperPill(
                        label = "SETS",
                        value = item.targetSets,
                        onDecrement = { if (item.targetSets > 1) onUpdateSetsReps(item.targetSets - 1, item.targetReps) },
                        onIncrement = { if (item.targetSets < 10) onUpdateSetsReps(item.targetSets + 1, item.targetReps) }
                    )

                    CompactStepperPill(
                        label = "REPS",
                        value = item.targetReps,
                        onDecrement = { if (item.targetReps > 1) onUpdateSetsReps(item.targetSets, item.targetReps - 1) },
                        onIncrement = { if (item.targetReps < 50) onUpdateSetsReps(item.targetSets, item.targetReps + 1) }
                    )
                }

                // Reorder Up/Down & Delete
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalCount > 1) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Move Up",
                                tint = if (index > 0) TextSecondary else TextMuted.copy(alpha = 0.35f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < totalCount - 1,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Move Down",
                                tint = if (index < totalCount - 1) TextSecondary else TextMuted.copy(alpha = 0.35f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove Exercise",
                            tint = DecreaseRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStepperPill(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgDark,
        border = BorderStroke(0.8.dp, BorderDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SurfaceCard,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDecrement() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "−",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = 38.dp)
            ) {
                Text(
                    text = "$value",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentOrange,
                    maxLines = 1
                )
                Text(
                    text = label,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SurfaceCard,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onIncrement() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramExercisePickerDialog(
    availableExercises: List<Exercise>,
    alreadyAddedNames: Set<String>,
    onDismiss: () -> Unit,
    onSelectExercise: (Exercise, Int, Int) -> Unit,
    onCreateAndSelectExercise: (String, String, Boolean, Int, Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var targetSets by remember { mutableIntStateOf(3) }
    var targetReps by remember { mutableIntStateOf(8) }

    var newCustomCategory by remember { mutableStateOf("Chest") }
    var newCustomBodyweight by remember { mutableStateOf(false) }

    val categories = remember(availableExercises) {
        listOf("All") + availableExercises.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
    }

    val filtered = remember(availableExercises, searchQuery, selectedCategory) {
        availableExercises.filter { ex ->
            val matchesCategory = selectedCategory == "All" || ex.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() || ex.name.contains(searchQuery.trim(), ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    val exactMatchExists = remember(availableExercises, searchQuery) {
        val q = searchQuery.trim()
        q.isNotBlank() && availableExercises.any { it.name.equals(q, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header & Target Sets/Reps presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Exercise to Day",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Steppers for Target Sets & Reps
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = BorderStroke(0.7.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PRESCRIBED TARGETS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactStepperPill(
                                label = "SETS",
                                value = targetSets,
                                onDecrement = { if (targetSets > 1) targetSets-- },
                                onIncrement = { if (targetSets < 10) targetSets++ }
                            )
                            CompactStepperPill(
                                label = "REPS",
                                value = targetReps,
                                onDecrement = { if (targetReps > 1) targetReps-- },
                                onIncrement = { if (targetReps < 50) targetReps++ }
                            )
                        }
                    }
                }

                // Search box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentOrange),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search exercises catalog...", color = TextMuted, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Category chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AccentOrange else SurfaceCard,
                            border = BorderStroke(0.7.dp, if (isSelected) AccentOrange else BorderDark),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF140D07) else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Exercise list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Create new custom exercise banner if searched exercise doesn't exist
                    if (searchQuery.trim().isNotBlank() && !exactMatchExists) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceCard,
                                border = BorderStroke(1.dp, AccentOrange),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Create & Add \"${searchQuery.trim()}\"",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentOrange
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(categories.filter { it != "All" }) { cat ->
                                            val sel = newCustomCategory == cat
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (sel) AccentOrange else BgDark,
                                                modifier = Modifier.clickable { newCustomCategory = cat }
                                            ) {
                                                Text(
                                                    text = cat,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sel) Color(0xFF140D07) else TextSecondary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Checkbox(
                                                checked = newCustomBodyweight,
                                                onCheckedChange = { newCustomBodyweight = it }
                                            )
                                            Text("Bodyweight Lift", fontSize = 12.sp, color = TextPrimary)
                                        }
                                        Button(
                                            onClick = {
                                                onCreateAndSelectExercise(
                                                    searchQuery.trim(),
                                                    newCustomCategory,
                                                    newCustomBodyweight,
                                                    targetSets,
                                                    targetReps
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentOrange,
                                                contentColor = Color(0xFF140D07)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("+ Create & Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(filtered, key = { it.name }) { ex ->
                        val isAlreadyInDay = ex.name.lowercase() in alreadyAddedNames
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAlreadyInDay) SurfaceCard.copy(alpha = 0.6f) else SurfaceCard,
                            border = BorderStroke(
                                0.8.dp,
                                if (isAlreadyInDay) IncreaseGreen.copy(alpha = 0.5f) else BorderDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectExercise(ex, targetSets, targetReps) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ex.name,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${targetSets} sets × ${targetReps} reps",
                                        fontSize = 11.5.sp,
                                        color = AccentPeach
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CategoryPill(category = ex.category.ifBlank { "Other" })
                                    if (isAlreadyInDay) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Added",
                                            tint = IncreaseGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = AccentOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
