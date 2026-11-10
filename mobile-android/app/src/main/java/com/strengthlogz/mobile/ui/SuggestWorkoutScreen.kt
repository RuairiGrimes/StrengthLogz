package com.strengthlogz.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

enum class WorkoutSplitType(val label: String, val subtitle: String) {
    PUSH("Push", "Chest, Shoulders & Triceps"),
    PULL("Pull", "Back, Rear Delts & Biceps"),
    FULL_BODY("Full Body", "Legs, Push, Pull & Arms")
}

@Immutable
data class PrescribedSet(
    val weight: String,
    val reps: String,
    val notes: String = ""
)

@Immutable
data class SuggestedExerciseItem(
    val slotTitle: String,
    val exerciseName: String,
    val category: String,
    val lastSessionDate: String,
    val lastSummary: String,
    val peakE1rm: Double,
    val currentE1rm: Double,
    val reasonTag: String,
    val targetSummary: String,
    val prescribedSets: List<PrescribedSet>
)

@Immutable
data class SuggestedWorkoutPlan(
    val split: WorkoutSplitType,
    val title: String,
    val notes: String,
    val exercises: List<SuggestedExerciseItem>
)

@Immutable
data class MuscleVolumeStat(
    val category: String,
    val setsLast7Days: Int,
    val setsLast14Days: Int,
    val targetWeeklySets: Int,
    val deficitRatio: Float
)

private data class SlotDefinition(
    val slotTitle: String,
    val categoryHint: String,
    val candidates: List<String>,
    val defaultSets: Int,
    val defaultReps: Int
)

private val EXERCISE_BENEFITS = mapOf(
    "Bulgarian Split Squat" to "Unilateral quad/glute strength, eliminates leg imbalances, and spares lower spine",
    "Leg Press" to "Maximum quad overload and knee flexion with zero spinal shear",
    "Hack Squat" to "Deep knee flexion and quad hypertrophy with locked-in back support",
    "Leg Extension" to "Direct isolated rectus femoris quad peak-contraction stimulus",
    "Good Morning" to "Posterior chain hip-hinge endurance, hamstring stretch, and bulletproof lumbar erectors",
    "Hyperextension" to "Decompresses lower back, strengthens spinal erectors, glutes, and hamstrings under full range",
    "Romanian Deadlift" to "Lengthened-position hamstring stretch and powerful glute hypertrophy",
    "Leg Curl" to "Direct hamstring knee flexion isolation to balance knee joint integrity",
    "Incline Chest Press (Machine)" to "Locked-in clavicular upper pec tension without shoulder stability fatigue",
    "Dips" to "High-threshold motor unit recruitment for lower pecs, anterior delts, and triceps",
    "Cable Rear Delt Fly" to "Continuous peak resistance through full horizontal shoulder abduction",
    "Pendlay Row" to "Dead-stop explosive concentric back power and strict thoracic strength",
    "Snatch Grip High Pull" to "Explosive upper back, trapezius, and posterior chain power transfer",
    "Band Face Pull" to "Rotator cuff longevity, external rotation, and scapular upward rotation",
    "Fat Gripz Hammer Curl" to "Radical forearm flexor recruitment, brachialis mass, and grip strength",
    "Reverse Grip Curl" to "Brachioradialis hypertrophy and forearm extensor tendon balance",
    "Dumbbell Pullover" to "Expanded ribcage lat stretch in the fully lengthened position",
    "Paused Close Grip Bench Press" to "Eliminates stretch reflex for brute triceps and lockout power"
)

private val PUSH_SLOTS = listOf(
    SlotDefinition(
        slotTitle = "Primary Compound Press",
        categoryHint = "Chest",
        candidates = listOf("Bench Press", "Incline Bench Press", "Close Grip Bench Press", "Paused Close Grip Bench Press"),
        defaultSets = 3,
        defaultReps = 5
    ),
    SlotDefinition(
        slotTitle = "Push Leg: Quad Overload",
        categoryHint = "Legs",
        candidates = listOf("Leg Press", "Bulgarian Split Squat", "Hack Squat", "Squat", "Front Squat", "Leg Extension"),
        defaultSets = 3,
        defaultReps = 8
    ),
    SlotDefinition(
        slotTitle = "Vertical / Shoulder Press",
        categoryHint = "Shoulders",
        candidates = listOf("Overhead Press", "Seated Dumbbell Press", "Shoulder Press", "Incline Dumbbell Press"),
        defaultSets = 3,
        defaultReps = 6
    ),
    SlotDefinition(
        slotTitle = "Secondary Chest & Push Accessory",
        categoryHint = "Chest",
        candidates = listOf("Dips", "Incline Chest Press (Machine)", "Incline Dumbbell Press", "Dumbbell Fly", "Weighted Push Up"),
        defaultSets = 3,
        defaultReps = 8
    ),
    SlotDefinition(
        slotTitle = "Delts & Triceps Finisher",
        categoryHint = "Shoulders",
        candidates = listOf("Lateral Raise", "Overhead Tricep Extension", "Cable Tricep Pushdown", "Skull Crusher"),
        defaultSets = 3,
        defaultReps = 10
    )
)

private val PULL_SLOTS = listOf(
    SlotDefinition(
        slotTitle = "Primary Vertical Pull",
        categoryHint = "Back",
        candidates = listOf("Chin Ups", "Pull Ups", "Wide Grip Pull Ups", "Lat Pulldown"),
        defaultSets = 3,
        defaultReps = 6
    ),
    SlotDefinition(
        slotTitle = "Heavy Horizontal Row",
        categoryHint = "Back",
        candidates = listOf("Pendlay Row", "Chest Supported Dumbbell Row", "Barbell Row", "Dumbbell Row", "Cable Row"),
        defaultSets = 3,
        defaultReps = 6
    ),
    SlotDefinition(
        slotTitle = "Pull Leg: Hip Hinge & Posterior Chain",
        categoryHint = "Legs",
        candidates = listOf("Good Morning", "Hyperextension", "Romanian Deadlift", "Deadlift", "Trap Bar Deadlift", "Leg Curl"),
        defaultSets = 3,
        defaultReps = 8
    ),
    SlotDefinition(
        slotTitle = "Rear Delts & Upper Back Stretch",
        categoryHint = "Back",
        candidates = listOf("Band Face Pull", "Rear Delt Raise", "Cable Rear Delt Fly", "Dumbbell Pullover", "Snatch Grip High Pull"),
        defaultSets = 3,
        defaultReps = 12
    ),
    SlotDefinition(
        slotTitle = "Biceps & Grip Finisher",
        categoryHint = "Arms",
        candidates = listOf("Fat Gripz Hammer Curl", "Barbell Curl", "EZ Bar Preacher Curl", "Reverse Grip Curl", "Incline Fat Gripz Hammer Curl"),
        defaultSets = 3,
        defaultReps = 10
    )
)

private val FULL_BODY_SLOTS = listOf(
    SlotDefinition(
        slotTitle = "Primary Lower Compound",
        categoryHint = "Legs",
        candidates = listOf("Squat", "Leg Press", "Deadlift", "Romanian Deadlift", "Bulgarian Split Squat"),
        defaultSets = 3,
        defaultReps = 5
    ),
    SlotDefinition(
        slotTitle = "Primary Upper Push",
        categoryHint = "Chest",
        candidates = listOf("Bench Press", "Overhead Press", "Incline Bench Press", "Dips"),
        defaultSets = 3,
        defaultReps = 5
    ),
    SlotDefinition(
        slotTitle = "Primary Upper Pull",
        categoryHint = "Back",
        candidates = listOf("Pull Ups", "Chin Ups", "Pendlay Row", "Lat Pulldown", "Barbell Row"),
        defaultSets = 3,
        defaultReps = 6
    ),
    SlotDefinition(
        slotTitle = "Secondary Lower: Quad or Posterior Chain",
        categoryHint = "Legs",
        candidates = listOf("Bulgarian Split Squat", "Leg Press", "Hyperextension", "Good Morning", "Leg Extension", "Leg Curl", "Romanian Deadlift"),
        defaultSets = 3,
        defaultReps = 8
    ),
    SlotDefinition(
        slotTitle = "Shoulders & Arms Overload",
        categoryHint = "Shoulders",
        candidates = listOf("Lateral Raise", "Band Face Pull", "Rear Delt Raise", "Fat Gripz Hammer Curl", "Overhead Tricep Extension", "Cable Tricep Pushdown", "Barbell Curl"),
        defaultSets = 3,
        defaultReps = 10
    )
)

private fun parseIsoToEpochMs(iso: String): Long = DateUtils.toEpochMs(iso)

private fun analyzeMuscleVolume(
    sets: List<WorkoutSet>,
    exercises: List<Exercise>
): List<MuscleVolumeStat> {
    val catMap = exercises.associate { it.name.lowercase() to it.category }
    val validSets = sets.filter { (it.reps ?: 0) > 0 }

    // Reference date: latest workout date or today
    val latestIso = validSets.maxOfOrNull { DateUtils.toSortableIso(it.date) } ?: DateUtils.toSortableIso(DateUtils.todayDdMonYyyy())
    val refMs = parseIsoToEpochMs(latestIso).coerceAtLeast(System.currentTimeMillis())
    val dayMs = 86_400_000L

    val targets = linkedMapOf(
        "Chest" to 10,
        "Back" to 12,
        "Shoulders" to 9,
        "Legs" to 10,
        "Arms" to 9,
        "Core" to 4
    )

    val counts7 = mutableMapOf<String, Int>()
    val counts14 = mutableMapOf<String, Int>()

    for (s in validSets) {
        val cat = catMap[s.exercise.lowercase()] ?: "Other"
        val sMs = parseIsoToEpochMs(DateUtils.toSortableIso(s.date))
        if (sMs > 0L) {
            val diffDays = ((refMs - sMs) / dayMs).coerceAtLeast(0L)
            if (diffDays <= 7L) {
                counts7[cat] = (counts7[cat] ?: 0) + 1
            }
            if (diffDays <= 14L) {
                counts14[cat] = (counts14[cat] ?: 0) + 1
            }
        }
    }

    return targets.map { (cat, target) ->
        val c7 = counts7[cat] ?: 0
        val c14 = counts14[cat] ?: 0
        val ratio = (c7.toFloat() / target.toFloat()).coerceIn(0f, 1.5f)
        MuscleVolumeStat(
            category = cat,
            setsLast7Days = c7,
            setsLast14Days = c14,
            targetWeeklySets = target,
            deficitRatio = 1f - ratio.coerceAtMost(1f)
        )
    }
}

private fun buildSuggestedExerciseItem(
    slot: SlotDefinition,
    candidateIndexOffset: Int,
    alreadyPicked: Set<String>,
    setsByExerciseLower: Map<String, List<WorkoutSet>>,
    latestGlobalIso: String,
    workoutMap: Map<String, Workout>,
    catMap: Map<String, String>,
    volumeStats: Map<String, MuscleVolumeStat>
): SuggestedExerciseItem {
    val availableCandidates = slot.candidates.filter { it.lowercase() !in alreadyPicked }.ifEmpty { slot.candidates }

    val refMs = parseIsoToEpochMs(latestGlobalIso)

    val rankedCandidates = availableCandidates.sortedByDescending { exName ->
        val rawExSets = setsByExerciseLower[exName.lowercase()] ?: emptyList()
        val exSets = rawExSets.filter { (it.reps ?: 0) > 0 }
        val cat = catMap[exName.lowercase()] ?: slot.categoryHint
        val volDeficitBonus = (volumeStats[cat]?.deficitRatio ?: 0.5f) * 10.0
        val benefitBonus = if (EXERCISE_BENEFITS.containsKey(exName)) 4.0 else 0.0

        if (exSets.isEmpty()) {
            return@sortedByDescending 8.0 + volDeficitBonus + benefitBonus
        }

        val pts = buildSessionPoints(exSets, workoutMap, BodyWeightUtils.isBodyweightExercise(exName))
        val sessionCount = pts.size
        val lastPt = pts.lastOrNull()
        val peakE1rm = pts.maxOfOrNull { it.e1rm } ?: 0.0
        val currE1rm = lastPt?.e1rm ?: 0.0

        val daysSince = if (lastPt != null) {
            ((refMs - parseIsoToEpochMs(lastPt.isoDate)) / 86_400_000L).coerceIn(0L, 60L)
        } else 30L

        val plateauGapBonus = if (peakE1rm > 0 && currE1rm < peakE1rm) {
            ((peakE1rm - currE1rm) / peakE1rm * 25.0).coerceAtMost(15.0)
        } else 5.0

        val recencyBonus = when {
            daysSince < 2 -> -20.0
            daysSince in 3..18 -> 12.0
            else -> 6.0
        }

        (sessionCount.coerceAtMost(25) * 0.8) + plateauGapBonus + recencyBonus + volDeficitBonus + benefitBonus
    }

    val chosenName = rankedCandidates[candidateIndexOffset % rankedCandidates.size]
    val cat = catMap[chosenName.lowercase()] ?: slot.categoryHint
    val exSets = setsByExerciseLower[chosenName.lowercase()] ?: emptyList()
    val sessions = buildSessionPoints(exSets, workoutMap, BodyWeightUtils.isBodyweightExercise(chosenName))

    val lastSession = sessions.lastOrNull()
    val prevSession = if (sessions.size >= 2) sessions[sessions.size - 2] else null
    val peakSession = sessions.maxByOrNull { it.e1rm }
    val peakE1rm = peakSession?.e1rm ?: 0.0
    val currE1rm = lastSession?.e1rm ?: 0.0
    val benefitText = EXERCISE_BENEFITS[chosenName]

    if (lastSession == null) {
        val defaultPrescribed = List(slot.defaultSets) {
            PrescribedSet(weight = "", reps = slot.defaultReps.toString())
        }
        val newReason = if (!benefitText.isNullOrBlank()) {
            "✨ New Exercise Suggestion: $benefitText"
        } else {
            "🎯 New stimulus for $cat (${slot.defaultSets} × ${slot.defaultReps})"
        }
        return SuggestedExerciseItem(
            slotTitle = slot.slotTitle,
            exerciseName = chosenName,
            category = cat,
            lastSessionDate = "First Time",
            lastSummary = "No prior sessions logged",
            peakE1rm = 0.0,
            currentE1rm = 0.0,
            reasonTag = newReason,
            targetSummary = "${slot.defaultSets} sets × ${slot.defaultReps} reps",
            prescribedSets = defaultPrescribed
        )
    }

    val completedLastSets = lastSession.sets.filter { (it.reps ?: 0) > 0 }
    val baseWeight = lastSession.topWeight
    val baseReps = lastSession.topWeightReps
    val lastSummary = com.strengthlogz.mobile.util.SetUtils.formatSetsSummary(completedLastSets, isBodyweight = baseWeight <= 0.0)

    val numSets = completedLastSets.size.coerceIn(3, 4)

    val isReadyForWeightJump = baseWeight > 0 && (
        (baseReps >= 8 && slot.defaultReps <= 6) ||
            (baseReps >= 12) ||
            (prevSession != null && lastSession.e1rm > prevSession.e1rm && baseReps >= slot.defaultReps + 1)
        )

    val targetWeight: Double
    val targetReps: Int
    val overloadNote: String

    if (baseWeight <= 0.0) {
        targetWeight = 0.0
        targetReps = (baseReps + 1).coerceAtLeast(slot.defaultReps)
        overloadNote = "Aim for +1 rep per set over last session ($baseReps → $targetReps reps)"
    } else if (isReadyForWeightJump) {
        val increment = if (cat.equals("Arms", ignoreCase = true) || cat.equals("Shoulders", ignoreCase = true) && chosenName.contains("Raise", ignoreCase = true)) 1.25 else 2.5
        targetWeight = ((baseWeight + increment) * 2.0).roundToInt() / 2.0
        targetReps = slot.defaultReps.coerceAtLeast(baseReps - 2)
        overloadNote = "Weight progression: +${formatWeightValue(targetWeight - baseWeight)}kg ($numSets x $targetReps @ ${formatWeightValue(targetWeight)}kg)"
    } else {
        targetWeight = baseWeight
        targetReps = (baseReps + 1).coerceAtMost(15)
        overloadNote = "Rep progression: +1 rep at ${formatWeightValue(targetWeight)}kg ($numSets x $targetReps @ ${formatWeightValue(targetWeight)}kg)"
    }

    val isBwChosen = BodyWeightUtils.isBodyweightExercise(chosenName)
    val targetLoad = BodyWeightUtils.totalLoad(isBwChosen, targetWeight, BodyWeightUtils.latestKg())
    val targetE1rm = if (targetLoad > 0) computeEpley(targetLoad, targetReps) else 0.0
    val volStat = volumeStats[cat]
    val benefitSuffix = if (!benefitText.isNullOrBlank()) " • Benefit: $benefitText" else ""
    val reasonTag = when {
        peakE1rm > 0 && currE1rm < peakE1rm * 0.94 ->
            "🔧 Needs Improvement: Current E1RM (${formatWeightValue(currE1rm)}kg) is below your ${formatWeightValue(peakE1rm)}kg PR (${peakSession?.displayDate ?: ""})$benefitSuffix"
        targetE1rm > peakE1rm && peakE1rm > 0 ->
            "🏆 PR Opportunity: Hitting ${formatWeightValue(targetWeight)}kg × $targetReps sets a new E1RM PR (${formatWeightValue(targetE1rm)}kg)!$benefitSuffix"
        volStat != null && volStat.setsLast7Days < volStat.targetWeeklySets / 2 ->
            "📊 Weekly Volume Gap: $cat has only ${volStat.setsLast7Days}/${volStat.targetWeeklySets} sets in the last 7 days$benefitSuffix"
        else ->
            "📈 Progressive Overload: Build on ${lastSession.displayDate} ($overloadNote)$benefitSuffix"
    }

    val targetSummary = if (targetWeight > 0) {
        "$numSets x $targetReps @ ${formatWeightValue(targetWeight)}kg"
    } else {
        "$numSets x $targetReps @ BW"
    }

    val prescribedSets = List(numSets) {
        PrescribedSet(
            weight = if (targetWeight > 0) formatWeightValue(targetWeight) else "",
            reps = targetReps.toString(),
            notes = ""
        )
    }

    return SuggestedExerciseItem(
        slotTitle = slot.slotTitle,
        exerciseName = chosenName,
        category = cat,
        lastSessionDate = lastSession.displayDate,
        lastSummary = lastSummary,
        peakE1rm = peakE1rm,
        currentE1rm = currE1rm,
        reasonTag = reasonTag,
        targetSummary = targetSummary,
        prescribedSets = prescribedSets
    )
}

@Composable
fun SuggestWorkoutScreen(
    workouts: List<Workout>,
    sets: List<WorkoutSet>,
    exercises: List<Exercise>,
    onStartSuggestedWorkout: (SuggestedWorkoutPlan) -> Unit
) {
    var selectedSplit by remember { mutableStateOf(WorkoutSplitType.PUSH) }
    var targetExerciseCount by remember { mutableIntStateOf(5) }
    var shuffleSeed by remember { mutableIntStateOf(0) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    val slotOffsets = remember { mutableStateMapOf<Int, Int>() }
    val expandedSlots = remember { mutableStateMapOf<Int, Boolean>() }

    val workoutMap = remember(workouts) { workouts.associateBy { it.id } }
    val catMap = remember(exercises) { exercises.associate { it.name.lowercase() to it.category } }
    val volumeStatsList = remember(sets, exercises) { analyzeMuscleVolume(sets, exercises) }
    val volumeStatsMap = remember(volumeStatsList) { volumeStatsList.associateBy { it.category } }

    val recommendedSplit = remember(volumeStatsMap) {
        val pushDeficit = ((volumeStatsMap["Chest"]?.deficitRatio ?: 0f) + (volumeStatsMap["Shoulders"]?.deficitRatio ?: 0f)) / 2f
        val pullDeficit = (volumeStatsMap["Back"]?.deficitRatio ?: 0f)
        val legsDeficit = (volumeStatsMap["Legs"]?.deficitRatio ?: 0f)
        when {
            legsDeficit >= pushDeficit && legsDeficit >= pullDeficit -> WorkoutSplitType.FULL_BODY
            pullDeficit >= pushDeficit -> WorkoutSplitType.PULL
            else -> WorkoutSplitType.PUSH
        }
    }

    val allSlots = when (selectedSplit) {
        WorkoutSplitType.PUSH -> PUSH_SLOTS
        WorkoutSplitType.PULL -> PULL_SLOTS
        WorkoutSplitType.FULL_BODY -> FULL_BODY_SLOTS
    }

    val activeSlots = remember(allSlots, targetExerciseCount) {
        if (targetExerciseCount == 4) {
            when (selectedSplit) {
                WorkoutSplitType.PUSH -> listOf(allSlots[0], allSlots[1], allSlots[2], allSlots[4])
                WorkoutSplitType.PULL -> listOf(allSlots[0], allSlots[1], allSlots[2], allSlots[4])
                WorkoutSplitType.FULL_BODY -> listOf(allSlots[0], allSlots[1], allSlots[2], allSlots[3])
            }
        } else {
            allSlots
        }
    }

    val setsByExerciseLower = remember(sets) {
        sets.groupBy { it.exercise.trim().lowercase() }
    }
    val latestGlobalIso = remember(sets) {
        sets.maxOfOrNull { DateUtils.toSortableIso(it.date) } ?: "2026-10-16"
    }

    val generatedExercises = remember(selectedSplit, targetExerciseCount, shuffleSeed, slotOffsets.toMap(), setsByExerciseLower, workouts) {
        val picked = mutableSetOf<String>()
        activeSlots.mapIndexed { idx, slot ->
            val offset = (slotOffsets[idx] ?: 0) + shuffleSeed
            val item = buildSuggestedExerciseItem(
                slot = slot,
                candidateIndexOffset = offset,
                alreadyPicked = picked,
                setsByExerciseLower = setsByExerciseLower,
                latestGlobalIso = latestGlobalIso,
                workoutMap = workoutMap,
                catMap = catMap,
                volumeStats = volumeStatsMap
            )
            picked.add(item.exerciseName.lowercase())
            item
        }
    }

    val currentPlan = remember(selectedSplit, generatedExercises) {
        SuggestedWorkoutPlan(
            split = selectedSplit,
            title = selectedSplit.label,
            notes = "Auto-designed ${selectedSplit.label} session (progressive overload)",
            exercises = generatedExercises
        )
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            Surface(
                color = SurfaceDark,
                border = BorderStroke(0.8.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onStartSuggestedWorkout(currentPlan) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color(0xFF140D07)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Start ${selectedSplit.label} Session (${generatedExercises.size} Lifts)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgDark),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Clean Header with 7-Day Volume Sheet trigger & Shuffle
            item {
                ModernScreenHeader(
                    title = "Suggest",
                    subtitle = "Recommended today: ${recommendedSplit.label}",
                    action = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showVolumeSheet = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderDark),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("7-Day Volume", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            IconButton(
                                onClick = { shuffleSeed++ },
                                modifier = Modifier
                                    .background(SurfaceCard, RoundedCornerShape(10.dp))
                                    .border(0.8.dp, BorderDark, RoundedCornerShape(10.dp))
                                    .size(38.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Shuffle", tint = AccentOrange, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                )
            }

            // 2. Compact Split & Session Length Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(SurfaceCard, RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        WorkoutSplitType.entries.forEach { split ->
                            val selected = selectedSplit == split
                            val isRec = split == recommendedSplit
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selected) AccentOrange else Color.Transparent)
                                    .clickable {
                                        selectedSplit = split
                                        slotOffsets.clear()
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isRec && !selected) "${split.label} •" else split.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (selected) Color(0xFF140D07) else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        border = BorderStroke(0.8.dp, BorderDark),
                        modifier = Modifier.clickable {
                            targetExerciseCount = if (targetExerciseCount == 5) 4 else 5
                        }
                    ) {
                        Text(
                            text = "$targetExerciseCount lifts",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPeach,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // 3. Streamlined Suggested Exercise Cards (Tap to expand details)
            itemsIndexed(generatedExercises, key = { idx, item -> "$idx-${item.exerciseName}" }) { idx, item ->
                val isExpanded = expandedSlots[idx] == true

                StrengthCard(
                    shape = RoundedCornerShape(16.dp),
                    onClick = { expandedSlots[idx] = !isExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        text = "${idx + 1}. ${item.exerciseName}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    CategoryPill(category = item.category)
                                }
                                Text(
                                    text = item.slotTitle,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    slotOffsets[idx] = (slotOffsets[idx] ?: 0) + 1
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(0.8.dp, BorderDark),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Swap", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = AccentOrange.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = item.targetSummary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentOrange,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            Text(
                                text = "Last: ${item.lastSummary}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        if (isExpanded) {
                            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                            Text(
                                text = item.reasonTag,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 17.sp
                            )
                            if (item.peakE1rm > 0) {
                                Text(
                                    text = "All-time E1RM PR: ${formatWeightValue(item.peakE1rm)} kg • Last logged ${item.lastSessionDate}",
                                    fontSize = 11.5.sp,
                                    color = GoldPR
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 7-Day Muscle Volume & Deficit Breakdown Modal
    if (showVolumeSheet) {
        AlertDialog(
            onDismissRequest = { showVolumeSheet = false },
            containerColor = SurfaceDark,
            title = {
                Text("7-Day Muscle Volume", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Working sets completed in the last 7 days vs weekly target:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    volumeStatsList.forEach { stat ->
                        val progress = (stat.setsLast7Days.toFloat() / stat.targetWeeklySets.toFloat()).coerceIn(0f, 1f)
                        val barColor = when {
                            progress < 0.4f -> DecreaseRed
                            progress < 0.8f -> AccentOrange
                            else -> IncreaseGreen
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgDark)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stat.category, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    text = "${stat.setsLast7Days} / ${stat.targetWeeklySets} sets (14d: ${stat.setsLast14Days})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = barColor
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SurfaceHighlight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress.coerceAtLeast(0.06f))
                                        .fillMaxHeight()
                                        .background(barColor)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVolumeSheet = false }) {
                    Text("Close", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
