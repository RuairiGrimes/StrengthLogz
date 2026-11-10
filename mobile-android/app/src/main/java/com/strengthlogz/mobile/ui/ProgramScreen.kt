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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.strengthlogz.mobile.data.WorkoutRepository
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
    program: WorkoutProgram,
    availableExercises: List<Exercise>,
    workouts: List<Workout>,
    allSets: List<WorkoutSet>,
    getPreviousSets: (String) -> List<WorkoutSet>,
    onSaveProgram: (WorkoutProgram) -> Unit,
    onAddCustomExercise: (Exercise) -> Unit,
    onLoadProgramDayToLogger: (SuggestedWorkoutPlan) -> Unit
) {
    val daysCount = program.daysPerWeek.coerceIn(1, 7)
    val safeDays = remember(program) {
        val current = program.safeDays
        if (current.size == daysCount) {
            current
        } else {
            val template = WorkoutRepository.buildDefaultProgram(daysCount).safeDays
            (0 until daysCount).map { idx ->
                current.getOrNull(idx)?.copy(dayIndex = idx)
                    ?: template.getOrNull(idx)?.copy(dayIndex = idx)
                    ?: ProgramDay(dayIndex = idx, dayName = "Day ${idx + 1}", focus = "Custom Training Day")
            }
        }
    }

    var selectedDayIdx by remember(daysCount) {
        val nextSuggested = if (program.lastLoadedDayIndex in 0 until daysCount) {
            (program.lastLoadedDayIndex + 1) % daysCount
        } else 0
        mutableIntStateOf(nextSuggested.coerceIn(0, (daysCount - 1).coerceAtLeast(0)))
    }

    val activeDay = safeDays.getOrNull(selectedDayIdx) ?: safeDays.firstOrNull()
        ?: ProgramDay(0, "Day 1: Training", "Full Body")

    var showEditDayMetaDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var showResetTemplateConfirm by remember { mutableStateOf(false) }
    var coachBannerMessage by remember { mutableStateOf("") }

    // Compute coaching insights for the program & active day
    val activeExercises = activeDay.safeExercises
    val totalDaySets = activeExercises.sumOf { it.targetSets.coerceAtLeast(1) }

    val splitRecommendation = remember(daysCount) {
        when (daysCount) {
            1 -> "1-Day Split: Focus on heavy compound movements (Squat, Press, Pull, Hinge) to stimulate all major muscle groups in a single high-intensity session."
            2 -> "2-Day Split: Upper / Lower or Full Body A/B gives each muscle group optimal recovery while hitting key lifts every week."
            3 -> "3-Day Split: Classic Push / Pull / Legs or 3x Full Body balances volume and central nervous system recovery."
            4 -> "4-Day Split: Upper / Lower (Strength & Hypertrophy) hits every muscle group 2x/week — the gold standard for natural strength & size."
            5 -> "5-Day Split: Upper / Lower + Push / Pull / Legs hybrid maximizes both heavy barbell progression and hypertrophy volume."
            6 -> "6-Day Split: Push / Pull / Legs × 2 provides high weekly frequency. Keep session volume around 12–18 sets to recover well."
            else -> "7-Day Split: Daily specialization. Keep intensity regulated and include lighter mobility/arm/core days to prevent overtraining."
        }
    }

    val dayCoachAdvice = remember(activeExercises, totalDaySets, allSets) {
        when {
            activeExercises.isEmpty() ->
                "👋 This day has no exercises yet! Tap '+ Add Exercise to Day' below to build your routine, or tap 'Auto-Fill Template' for a proven split."
            totalDaySets < 8 ->
                "💡 Light Session ($totalDaySets total sets): Great for a quick strength focus or deload. Add 1–2 accessory lifts if you want more hypertrophy volume."
            totalDaySets in 8..18 ->
                "🔥 Optimal Session Volume ($totalDaySets working sets across ${activeExercises.size} lifts): Ready to load! Weights auto-fill from your last session so you only need to adjust reps or sets."
            else ->
                "⚡ High-Volume Session ($totalDaySets sets across ${activeExercises.size} lifts): Prioritize your first 2 compound movements when fresh and keep rest times crisp."
        }
    }

    fun updateDay(updatedDay: ProgramDay) {
        val newDays = safeDays.mapIndexed { idx, d ->
            if (idx == selectedDayIdx) updatedDay.copy(dayIndex = idx) else d
        }
        onSaveProgram(
            program.copy(
                daysPerWeek = daysCount,
                days = newDays
            )
        )
    }

    fun changeDaysPerWeek(newCount: Int) {
        val coerced = newCount.coerceIn(1, 7)
        if (coerced == daysCount) return
        val defaultForCount = WorkoutRepository.buildDefaultProgram(coerced).safeDays
        val mergedDays = (0 until coerced).map { idx ->
            val existing = safeDays.getOrNull(idx)
            if (existing != null && existing.safeExercises.isNotEmpty()) {
                existing.copy(dayIndex = idx)
            } else {
                defaultForCount.getOrNull(idx)?.copy(dayIndex = idx)
                    ?: ProgramDay(idx, "Day ${idx + 1}", "Custom Day", emptyList())
            }
        }
        if (selectedDayIdx >= coerced) {
            selectedDayIdx = coerced - 1
        }
        onSaveProgram(
            program.copy(
                daysPerWeek = coerced,
                programName = "$coerced-Day Training Split",
                days = mergedDays
            )
        )
        coachBannerMessage = "Switched to $coerced days/week! Tap 'Reset Split Template' anytime if you want fresh default routines for all $coerced days."
    }

    fun loadDayIntoLogger(day: ProgramDay) {
        if (day.safeExercises.isEmpty()) return
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
                    reps = item.targetReps.coerceAtLeast(1).toString(),
                    notes = item.safeNotes
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
                reasonTag = "Program Target: ${item.targetSets} × ${item.targetReps}",
                targetSummary = "${item.targetSets} sets × ${item.targetReps} reps",
                prescribedSets = prescribedSets
            )
        }

        val updatedProg = program.copy(
            daysPerWeek = daysCount,
            days = safeDays,
            lastLoadedDayIndex = day.dayIndex
        )
        onSaveProgram(updatedProg)

        val plan = SuggestedWorkoutPlan(
            split = WorkoutSplitType.FULL_BODY,
            title = day.dayName,
            notes = if (day.safeFocus.isNotBlank()) "Program Focus: ${day.safeFocus}" else "Loaded from ${program.safeProgramName}",
            exercises = suggestedItems
        )
        onLoadProgramDayToLogger(plan)
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
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add Exercise",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { loadDayIntoLogger(activeDay) },
                        enabled = activeExercises.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(50.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = Color(0xFF140D07),
                            disabledContainerColor = SurfaceDark,
                            disabledContentColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Load Day ${selectedDayIdx + 1} to Logger",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (activeExercises.isNotEmpty()) {
                                Text(
                                    text = "${activeExercises.size} lifts • $totalDaySets sets ready",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF140D07).copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(10.dp))
                ModernScreenHeader(
                    title = "Weekly Program",
                    subtitle = "Design your weekly split & load any day in 1 tap",
                    badgeText = "${daysCount}D / WK",
                    action = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.clickable { showResetTemplateConfirm = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reset Template",
                                    tint = AccentPeach,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Template",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentPeach,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                )
            }

            // 1. Days Per Week Selector Card
            item {
                StrengthCard(shape = RoundedCornerShape(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "How many days a week do you train?",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..7).forEach { count ->
                                val isSelected = count == daysCount
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable { changeDaysPerWeek(count) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentOrange else BgDark,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) AccentOrange else BorderDark
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${count}d",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSelected) Color(0xFF140D07) else TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = splitRecommendation,
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 2. Day Selector Pills
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(safeDays) { idx, day ->
                        val isSelected = idx == selectedDayIdx
                        val isNextUp = program.lastLoadedDayIndex >= 0 &&
                            ((program.lastLoadedDayIndex + 1) % daysCount) == idx
                        val exCount = day.safeExercises.size

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AccentOrange.copy(alpha = 0.18f) else SurfaceCard,
                            border = BorderStroke(
                                1.2.dp,
                                if (isSelected) AccentOrange else if (isNextUp) GoldPR.copy(alpha = 0.6f) else BorderDark
                            ),
                            modifier = Modifier.clickable { selectedDayIdx = idx }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = day.dayName.ifBlank { "Day ${idx + 1}" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AccentOrange else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isNextUp) {
                                        Surface(
                                            color = GoldPR.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(5.dp)
                                        ) {
                                            Text(
                                                text = "NEXT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = GoldPR,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "$exCount lift${if (exCount == 1) "" else "s"} • ${day.safeExercises.sumOf { it.targetSets }} sets",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 3. Active Day Header & Helpful Coach Banner
            item {
                StrengthCard(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.45f))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeDay.dayName.ifBlank { "Day ${selectedDayIdx + 1}" },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeDay.safeFocus.isNotBlank()) {
                                    Text(
                                        text = activeDay.safeFocus,
                                        fontSize = 12.sp,
                                        color = AccentPeach,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = SurfaceDark,
                                border = BorderStroke(0.8.dp, BorderDark),
                                modifier = Modifier.clickable { showEditDayMetaDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename Day",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Rename",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Helpful Coaching Message Box
                        Surface(
                            color = BgDark,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, BorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GoldPR,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(16.dp)
                                )
                                Text(
                                    text = if (coachBannerMessage.isNotBlank()) coachBannerMessage else dayCoachAdvice,
                                    fontSize = 11.5.sp,
                                    color = TextPrimary.copy(alpha = 0.9f),
                                    lineHeight = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Exercise Cards for Selected Day
            if (activeExercises.isEmpty()) {
                item {
                    StrengthCard(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
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
                                        Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No Exercises on ${activeDay.dayName}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Add exercises from the built-in catalog or load the recommended split template.",
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Button(
                                    onClick = { showExercisePicker = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentOrange,
                                        contentColor = Color(0xFF140D07)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Exercise", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val templateDay = WorkoutRepository.buildDefaultProgram(daysCount)
                                            .safeDays.getOrNull(selectedDayIdx)
                                        if (templateDay != null) {
                                            updateDay(templateDay)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                ) {
                                    Text("Auto-Fill Day", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    activeExercises,
                    key = { index, item -> "${selectedDayIdx}_${item.exerciseName}_$index" }
                ) { idx, exItem ->
                    val prevSets = remember(exItem.exerciseName, allSets) {
                        getPreviousSets(exItem.exerciseName).filter { (it.reps ?: 0) > 0 }
                    }
                    val prevSummary = remember(prevSets) {
                        if (prevSets.isNotEmpty()) SetUtils.formatSetsSummary(prevSets) else "No prior session logged"
                    }
                    val isBw = remember(exItem.exerciseName, availableExercises) {
                        BodyWeightUtils.isBodyweightExercise(exItem.exerciseName)
                    }

                    ProgramExerciseCard(
                        index = idx,
                        totalCount = activeExercises.size,
                        item = exItem,
                        previousSummary = prevSummary,
                        isBodyweight = isBw,
                        onUpdateSetsReps = { newSets, newReps ->
                            val updatedList = activeExercises.toMutableList()
                            updatedList[idx] = exItem.copy(
                                targetSets = newSets.coerceIn(1, 10),
                                targetReps = newReps.coerceIn(1, 50)
                            )
                            updateDay(activeDay.copy(exercises = updatedList))
                        },
                        onMoveUp = {
                            if (idx > 0) {
                                val updatedList = activeExercises.toMutableList()
                                val tmp = updatedList[idx - 1]
                                updatedList[idx - 1] = updatedList[idx]
                                updatedList[idx] = tmp
                                updateDay(activeDay.copy(exercises = updatedList))
                            }
                        },
                        onMoveDown = {
                            if (idx < activeExercises.lastIndex) {
                                val updatedList = activeExercises.toMutableList()
                                val tmp = updatedList[idx + 1]
                                updatedList[idx + 1] = updatedList[idx]
                                updatedList[idx] = tmp
                                updateDay(activeDay.copy(exercises = updatedList))
                            }
                        },
                        onRemove = {
                            val updatedList = activeExercises.toMutableList().apply { removeAt(idx) }
                            updateDay(activeDay.copy(exercises = updatedList))
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    // Edit Day Name & Focus Dialog
    if (showEditDayMetaDialog) {
        var nameInput by remember(activeDay) { mutableStateOf(activeDay.dayName) }
        var focusInput by remember(activeDay) { mutableStateOf(activeDay.safeFocus) }

        AlertDialog(
            onDismissRequest = { showEditDayMetaDialog = false },
            containerColor = SurfaceDark,
            title = {
                Text(
                    text = "Customize Day ${selectedDayIdx + 1}",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Day Title (e.g. Day 1: Push)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = focusInput,
                        onValueChange = { focusInput = it },
                        label = { Text("Muscle Group Focus (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        updateDay(
                            activeDay.copy(
                                dayName = nameInput.trim().ifBlank { "Day ${selectedDayIdx + 1}" },
                                focus = focusInput.trim()
                            )
                        )
                        showEditDayMetaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color(0xFF140D07)
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDayMetaDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Reset Template Confirmation Dialog
    if (showResetTemplateConfirm) {
        AlertDialog(
            onDismissRequest = { showResetTemplateConfirm = false },
            containerColor = SurfaceDark,
            title = {
                Text(
                    text = "Reset $daysCount-Day Split Template?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "This will load the recommended $daysCount-day training split across all days. Your workout history and exercise catalog will not be affected.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fresh = WorkoutRepository.buildDefaultProgram(daysCount)
                        onSaveProgram(fresh)
                        coachBannerMessage = "Loaded recommended $daysCount-Day Split template!"
                        showResetTemplateConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color(0xFF140D07)
                    )
                ) {
                    Text("Load Template", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTemplateConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Exercise Picker Modal for Adding Exercises to Active Day
    if (showExercisePicker) {
        ProgramExercisePickerDialog(
            availableExercises = availableExercises,
            alreadyAddedNames = activeExercises.map { it.exerciseName.lowercase() }.toSet(),
            onDismiss = { showExercisePicker = false },
            onSelectExercise = { ex, sets, reps ->
                val updatedList = activeExercises.toMutableList().apply {
                    add(
                        ProgramExerciseItem(
                            exerciseName = ex.name,
                            category = ex.category.ifBlank { "Other" },
                            targetSets = sets,
                            targetReps = reps
                        )
                    )
                }
                updateDay(activeDay.copy(exercises = updatedList))
                coachBannerMessage = "Added ${ex.name} (${sets} × ${reps}) to ${activeDay.dayName}."
                showExercisePicker = false
            },
            onCreateAndSelectExercise = { name, category, isBw, sets, reps ->
                val newEx = Exercise(name = name.trim(), category = category, bodyweight = isBw)
                onAddCustomExercise(newEx)
                val updatedList = activeExercises.toMutableList().apply {
                    add(
                        ProgramExerciseItem(
                            exerciseName = newEx.name,
                            category = newEx.category,
                            targetSets = sets,
                            targetReps = reps
                        )
                    )
                }
                updateDay(activeDay.copy(exercises = updatedList))
                coachBannerMessage = "Created & added ${newEx.name} (${sets} × ${reps})!"
                showExercisePicker = false
            }
        )
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

    val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Other")

    val filtered = remember(availableExercises, searchQuery, selectedCategory) {
        val q = searchQuery.trim()
        availableExercises.filter { ex ->
            val cat = ex.category.ifBlank { "Other" }
            val matchesCat = selectedCategory == "All" || cat.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                ex.name.contains(q, ignoreCase = true) ||
                cat.contains(q, ignoreCase = true)
            matchesCat && matchesQuery
        }.sortedBy { it.name.lowercase() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Exercise to Day",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Set default sets × reps and tap any movement",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Default Sets & Reps Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgDark,
                    border = BorderStroke(0.8.dp, BorderDark),
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
                            text = "Target Prescription:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // Search Bar
                ModernSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search ${availableExercises.size} exercises or type a new one..."
                )

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val active = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) AccentOrange else SurfaceCard,
                            border = BorderStroke(1.dp, if (active) AccentOrange else BorderDark),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.5.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) Color(0xFF140D07) else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Exercise List or Quick-Create
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchQuery.trim().isNotEmpty() &&
                        availableExercises.none { it.name.equals(searchQuery.trim(), ignoreCase = true) }
                    ) {
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
