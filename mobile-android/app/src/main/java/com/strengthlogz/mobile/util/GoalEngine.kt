package com.strengthlogz.mobile.util

import androidx.compose.runtime.Immutable
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.GoalSessionReport
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.SessionProgressPoint
import com.strengthlogz.mobile.ui.buildSessionPoints
import com.strengthlogz.mobile.ui.computeEpley
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class GoalStatus(val label: String) {
    ACHIEVED("ACHIEVED 🏆"),
    ON_TRACK("ON TRACK"),
    PLATEAU_ALERT("PLATEAU ALERT"),
    NEEDS_FREQUENCY("NEEDS FREQUENCY"),
    BUILDING("BUILDING BASE")
}

@Immutable
data class PrescribedGoalSet(
    val setNo: Int,
    val role: String,
    val weightKg: Double,
    val reps: Int,
    val rpe: String,
    val notes: String = ""
)

@Immutable
data class GoalPhaseStep(
    val phaseNumber: Int,
    val title: String,
    val weeksLabel: String,
    val prescriptionSummary: String,
    val milestoneE1rmKg: Double,
    val focusCue: String
)

@Immutable
data class AccessoryRecommendation(
    val exerciseName: String,
    val category: String,
    val transferBenefit: String,
    val recentSessions: Int,
    val statusBadge: String
)

@Immutable
data class GoalEvaluation(
    val goal: ExerciseGoal,
    val category: String,
    val isBodyweight: Boolean,
    val currentBodyWeightKg: Double,
    val bodyWeightDeltaKg: Double,
    val currentBestE1rm: Double,
    val recentBestE1rm: Double,
    val currentTopWeight: Double,
    val currentTopWeightReps: Int,
    val bestSetSummary: String,
    val bestMatchingRepsAtTargetWeight: Int,
    val bestMatchingSetsAtTargetWeightReps: Int,
    val effectiveTargetE1rm: Double,
    val progressPct: Double,
    val gapClosedPct: Double,
    val remainingKg: Double,
    val isAchieved: Boolean,
    val status: GoalStatus,
    val currentRelBw: Double,
    val targetRelBw: Double,
    val strengthTier: String,
    val totalSessions: Int,
    val sessionsPerWeek: Double,
    val recommendedSessionsPerWeek: Int,
    val avgSetsPerSession: Double,
    val recommendedSetsPerWeek: Int,
    val daysSinceLastSession: Int,
    val monthlyE1rmGainKg: Double,
    val estimatedWeeksToGoal: Int,
    val estimatedTargetDate: String,
    val isPlateaued: Boolean,
    val dominantRepZone: String,
    val methodologyTitle: String,
    val methodologyRationale: String,
    val bodyWeightAdvice: String,
    val nextSessionSets: List<PrescribedGoalSet>,
    val warmupSets: List<PrescribedGoalSet>,
    val nextSessionSummary: String,
    val nextSessionFocusCue: String,
    val roadmapPhases: List<GoalPhaseStep>,
    val repEquivalenceTable: List<Pair<Int, Double>>,
    val accessories: List<AccessoryRecommendation>,
    val sessionHistory: List<SessionProgressPoint>
)

@Immutable
data class LiveSessionGoalAnalysis(
    val evaluation: GoalEvaluation,
    val loggedWorkingSetsCount: Int,
    val completedSetsCount: Int,
    val sessionBestE1rm: Double,
    val sessionBestWeight: Double,
    val sessionBestReps: Int,
    val sessionVolumeKg: Double,
    val qualifyingGoalSetsCount: Int,
    val updatedProgressPct: Double,
    val deltaVsPreviousBestKg: Double,
    val hitSessionTargetToday: Boolean,
    val achievedGoalToday: Boolean,
    val sessionGrade: String,
    val liveCoachingHeadline: String,
    val liveCoachingBullets: List<String>,
    val nextSetTargetText: String
)

@Immutable
data class ExerciseGoalReportSessionEntry(
    val date: String,
    val workoutTitle: String,
    val setsSummary: String,
    val sessionBestE1rm: Double,
    val topWeight: Double,
    val topReps: Int,
    val totalVolumeKg: Double,
    val workingSets: Int,
    val progressPctAtSession: Double,
    val deltaVsPrevE1rm: Double,
    val hitMilestone: Boolean,
    val coachNote: String
)

@Immutable
data class ExerciseGoalReport(
    val evaluation: GoalEvaluation,
    val generatedDate: String,
    val headlineSummary: String,
    val executiveStatus: String,
    val adherenceScore: Int,
    val strengthGainSinceGoalStartKg: Double,
    val strengthGainPct: Double,
    val volumeTrendSummary: String,
    val bodyWeightImpactSummary: String,
    val keyStrengths: List<String>,
    val bottlenecksAndFixes: List<String>,
    val sessionEntries: List<ExerciseGoalReportSessionEntry>
)

@Immutable
data class AllGoalsMasterReport(
    val generatedDate: String,
    val totalGoalsCount: Int,
    val activeGoalsCount: Int,
    val achievedGoalsCount: Int,
    val onTrackCount: Int,
    val plateauedCount: Int,
    val overallPortfolioCompletionPct: Double,
    val averageAdherenceScore: Int,
    val currentBodyWeightKg: Double,
    val strongestRelativeLift: String,
    val closestToCompletionGoal: String,
    val priorityAttentionGoal: String,
    val executiveTakeaways: List<String>,
    val exerciseReports: List<ExerciseGoalReport>
)

object GoalEngine {

    private fun roundStep(v: Double, step: Double = 2.5): Double {
        if (v <= 0.0) return 0.0
        return (v / step).roundToInt() * step
    }

    private fun roundHalf(v: Double): Double = (v * 2.0).roundToInt() / 2.0
    private fun roundTenth(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    private fun roundHundredth(v: Double): Double = (v * 100.0).roundToInt() / 100.0

    /**
     * Rounds a target load to realistic gym increments (1.25 kg for small movements/dumbbells, 2.5 kg for barbell compounds).
     */
    fun roundToGymLoad(weightKg: Double, exerciseName: String): Double {
        if (weightKg <= 0.0) return 0.0
        val lower = exerciseName.lowercase()
        val isSmallIncrement = lower.contains("curl") ||
            lower.contains("raise") ||
            lower.contains("extension") ||
            lower.contains("fly") ||
            lower.contains("dumbbell") ||
            lower.contains("db") ||
            lower.contains("overhead") ||
            lower.contains("ohp") ||
            weightKg < 45.0
        val step = if (isSmallIncrement) 1.25 else 2.5
        return ((weightKg / step).roundToInt() * step).coerceAtLeast(step)
    }

    /**
     * Computes the equivalent total-load E1RM for a goal (whether E1RM or SETS_REPS).
     */
    fun computeEffectiveTargetE1rm(
        goal: ExerciseGoal,
        isBodyweight: Boolean,
        bodyWeightKg: Double
    ): Double {
        return if (goal.isSetsRepsGoal) {
            val totalLoad = BodyWeightUtils.totalLoad(isBodyweight, goal.targetWeightKg, bodyWeightKg)
            val baseE1rm = computeEpley(totalLoad, goal.targetReps.coerceAtLeast(1))
            // Multi-set fatigue factor: completing e.g. 4 sets x 8 reps requires ~2-4% higher 1RM capacity than a single set of 8
            val setFatigueMultiplier = 1.0 + ((goal.targetSets.coerceIn(1, 10) - 1) * 0.012)
            roundHalf(baseE1rm * setFatigueMultiplier)
        } else {
            if (isBodyweight && goal.targetE1rmKg < bodyWeightKg) {
                // If user entered added weight instead of total load for a bodyweight 1RM goal
                roundHalf(bodyWeightKg + goal.targetE1rmKg)
            } else {
                roundHalf(goal.targetE1rmKg)
            }
        }
    }

    /**
     * Classifies relative strength tier (E1RM / BodyWeight) tailored by movement archetype.
     */
    fun classifyStrengthTier(
        exerciseName: String,
        category: String,
        relBw: Double,
        e1rm: Double
    ): String {
        if (e1rm <= 0.0) return "Unranked"
        val lower = exerciseName.lowercase()
        val thresholds = when {
            lower.contains("deadlift") || lower.contains("rdl") || lower.contains("rack pull") ->
                listOf(1.20, 1.60, 2.05, 2.45)
            lower.contains("squat") || lower.contains("leg press") || lower.contains("hack") ->
                listOf(1.00, 1.40, 1.80, 2.20)
            lower.contains("bench") || lower.contains("chest press") ->
                listOf(0.75, 1.05, 1.35, 1.65)
            lower.contains("pull up") || lower.contains("chin up") || lower.contains("dip") ->
                listOf(1.00, 1.20, 1.45, 1.75)
            lower.contains("overhead") || lower.contains("shoulder press") || lower.contains("military") || lower.contains("ohp") ->
                listOf(0.50, 0.70, 0.90, 1.15)
            lower.contains("row") || lower.contains("pulldown") ->
                listOf(0.65, 0.90, 1.20, 1.50)
            category.equals("Legs", ignoreCase = true) ->
                listOf(0.85, 1.20, 1.60, 2.00)
            else ->
                listOf(0.35, 0.55, 0.75, 1.00)
        }
        return when {
            relBw >= thresholds[3] -> "Elite"
            relBw >= thresholds[2] -> "Advanced"
            relBw >= thresholds[1] -> "Intermediate"
            relBw >= thresholds[0] -> "Novice"
            else -> "Foundation"
        }
    }

    /**
     * Suggests 3 high-transfer accessory movements for a target exercise and checks recent training history.
     */
    private fun buildAccessories(
        exerciseName: String,
        category: String,
        allSets: List<WorkoutSet>,
        latestGlobalEpochMs: Long
    ): List<AccessoryRecommendation> {
        val lower = exerciseName.lowercase()
        val candidates: List<Triple<String, String, String>> = when {
            lower.contains("bench") || (category.equals("Chest", true) && !lower.contains("dip")) -> listOf(
                Triple("Incline Dumbbell Press", "Chest", "Builds upper clavicular pec shelf & shoulder stability off the chest"),
                Triple("Dips", "Chest", "Heavy compound lockout & lower pec/tricep force production"),
                Triple("Tricep Pushdowns", "Arms", "Eliminates mid-range to lockout sticking points on heavy presses")
            )
            lower.contains("overhead") || lower.contains("shoulder press") || lower.contains("ohp") -> listOf(
                Triple("Incline Bench Press", "Chest", "Bridges upper-chest & anterior delt pressing strength"),
                Triple("Lateral Raises", "Shoulders", "Hypertrophies medial delts to widen pressing base"),
                Triple("Dips", "Chest", "Overloads tricep extension strength for overhead lockout")
            )
            lower.contains("squat") || lower.contains("leg press") || lower.contains("hack") -> listOf(
                Triple("Bulgarian Split Squat", "Legs", "Fixes unilateral quad/glute asymmetries & hip stability"),
                Triple("Romanian Deadlift", "Legs", "Strengthens posterior chain & torso bracing under heavy knee flexion"),
                Triple("Leg Extension", "Legs", "Direct rectus femoris & vastus medialis hypertrophy for hole drive")
            )
            lower.contains("deadlift") || lower.contains("rdl") || lower.contains("good morning") -> listOf(
                Triple("Romanian Deadlift", "Legs", "Reinforces hamstring eccentric loading & hip hinge leverage"),
                Triple("Barbell Row", "Back", "Builds lats & upper-back erectors to keep the bar tight to shins"),
                Triple("Hyperextension", "Back", "Fortifies lumbar resilience & glute lockout endurance")
            )
            lower.contains("pull up") || lower.contains("chin up") || lower.contains("pulldown") -> listOf(
                Triple("Lat Pulldown", "Back", "Allows controlled high-rep lat volume & scapular depression practice"),
                Triple("Barbell Row", "Back", "Builds mid-back rhomboid/trap thickness for top-of-bar chest touch"),
                Triple("Hammer Curls", "Arms", "Strengthens brachialis & forearm grip endurance for vertical pulling")
            )
            lower.contains("dip") -> listOf(
                Triple("Bench Press", "Chest", "Develops raw horizontal pressing force & sternal pec mass"),
                Triple("Overhead Press", "Shoulders", "Strengthens anterior deltoids & scapular upward rotation"),
                Triple("Skullcrushers", "Arms", "Targets long-head triceps stretch under load")
            )
            lower.contains("row") -> listOf(
                Triple("Pull Ups", "Back", "Expands vertical lat width & grip resilience"),
                Triple("Face Pulls", "Shoulders", "Strengthens rear delts & external rotators for cleaner row retraction"),
                Triple("Romanian Deadlift", "Legs", "Builds isometric spinal erector endurance for heavy bent-over rows")
            )
            else -> listOf(
                Triple("Bench Press", "Chest", "Primary upper-body compound force builder"),
                Triple("Pull Ups", "Back", "Upper back & torso stability foundation"),
                Triple("Bulgarian Split Squat", "Legs", "Unilateral lower-body drive & core balance")
            )
        }.filter { !it.first.equals(exerciseName, ignoreCase = true) }

        val windowMs = 28L * 86_400_000L
        return candidates.take(3).map { (accName, accCat, benefit) ->
            val accSets = allSets.filter { it.exercise.equals(accName, ignoreCase = true) && (it.reps ?: 0) > 0 }
            val recentWorkouts = accSets.filter {
                val ms = DateUtils.toEpochMs(it.date)
                latestGlobalEpochMs <= 0L || (latestGlobalEpochMs - ms) <= windowMs
            }.map { it.workoutId.ifBlank { it.date } }.distinct().size

            val badge = when {
                recentWorkouts >= 3 -> "Active ($recentWorkouts sess / 4wk)"
                recentWorkouts in 1..2 -> "Light ($recentWorkouts sess / 4wk)"
                else -> "Untapped Catalyst — Add 2x/wk"
            }
            AccessoryRecommendation(
                exerciseName = accName,
                category = accCat,
                transferBenefit = benefit,
                recentSessions = recentWorkouts,
                statusBadge = badge
            )
        }
    }

    /**
     * Full in-app evaluation of an [ExerciseGoal] based on the user's current strength,
     * body weight, and previous workout sessions.
     */
    fun evaluateGoal(
        goal: ExerciseGoal,
        workouts: List<Workout>,
        exerciseSets: List<WorkoutSet>,
        allSets: List<WorkoutSet> = exerciseSets,
        exercisesCatalog: List<Exercise> = emptyList(),
        bodyWeights: List<BodyWeightEntry> = emptyList()
    ): GoalEvaluation {
        val exName = goal.exerciseName.trim()
        val catalogEntry = exercisesCatalog.find { it.name.equals(exName, ignoreCase = true) }
        val category = catalogEntry?.category ?: "Other"
        val isBw = catalogEntry?.bodyweight == true || BodyWeightUtils.isBodyweightExercise(exName)

        val currentBwKg = if (bodyWeights.isNotEmpty()) {
            BodyWeightUtils.weightOn(DateUtils.todayDdMonYyyy(), bodyWeights)
        } else {
            BodyWeightUtils.latestKg()
        }
        val baselineBwKg = if (goal.baselineBodyWeightKg > 0.0) goal.baselineBodyWeightKg else currentBwKg
        val bwDeltaKg = roundTenth(currentBwKg - baselineBwKg)

        val workoutMap = workouts.associateBy { it.id }
        val sessions = buildSessionPoints(exerciseSets, workoutMap, isBw)

        val totalSessions = sessions.size
        val peakE1rmSession = sessions.maxByOrNull { it.e1rm }
        val currentBestE1rm = peakE1rmSession?.e1rm ?: 0.0

        val recentSessions = sessions.takeLast(4)
        val recentBestE1rm = recentSessions.maxOfOrNull { it.e1rm } ?: currentBestE1rm

        val peakWeightSession = sessions.maxWithOrNull(
            compareBy<SessionProgressPoint> { it.topWeight }.thenBy { it.topWeightReps }
        )
        val currentTopWeight = peakWeightSession?.topWeight ?: 0.0
        val currentTopWeightReps = peakWeightSession?.topWeightReps ?: 0

        val bestSetSummary = peakE1rmSession?.bestE1rmSet?.let { s ->
            val wStr = if (isBw) {
                val w = s.weight ?: 0.0
                if (w == 0.0) "BW" else "BW+${formatWeightValue(w)}kg"
            } else "${formatWeightValue(s.weight ?: 0.0)}kg"
            "$wStr × ${s.reps ?: 0} (${formatWeightValue(currentBestE1rm)}kg E1RM)"
        } ?: "No sessions logged yet"

        val effectiveTargetE1rm = computeEffectiveTargetE1rm(goal, isBw, currentBwKg).coerceAtLeast(1.0)

        // Check sets/reps specific progress
        var bestMatchingRepsAtTargetWeight = 0
        var bestMatchingSetsAtTargetWeightReps = 0
        for (sess in sessions) {
            val validSets = sess.sets.filter { (it.reps ?: 0) > 0 }
            val setsAtWeight = validSets.filter { (it.weight ?: 0.0) >= goal.targetWeightKg - 0.01 }
            val maxRepsAtWeight = setsAtWeight.maxOfOrNull { it.reps ?: 0 } ?: 0
            if (maxRepsAtWeight > bestMatchingRepsAtTargetWeight) {
                bestMatchingRepsAtTargetWeight = maxRepsAtWeight
            }
            val qualifyingSetsInSess = setsAtWeight.count { (it.reps ?: 0) >= goal.targetReps }
            if (qualifyingSetsInSess > bestMatchingSetsAtTargetWeightReps) {
                bestMatchingSetsAtTargetWeightReps = qualifyingSetsInSess
            }
        }

        val isAchieved = if (!goal.achievedDate.isNullOrBlank()) {
            true
        } else if (goal.isSetsRepsGoal) {
            bestMatchingSetsAtTargetWeightReps >= goal.targetSets || currentBestE1rm >= effectiveTargetE1rm + 2.5
        } else {
            currentBestE1rm >= effectiveTargetE1rm - 0.05
        }

        // Progress percentage:
        // For 1RM goal: ratio of currentBestE1rm / effectiveTargetE1rm (capped at 100%)
        // For SETS_REPS goal: blend of E1RM capacity ratio (70%) and qualifying sets/reps completion (30%)
        val rawE1rmRatio = if (effectiveTargetE1rm > 0.0) (currentBestE1rm / effectiveTargetE1rm) * 100.0 else 0.0
        val progressPct = if (isAchieved) {
            100.0
        } else if (goal.isSetsRepsGoal) {
            val setsRatio = if (goal.targetSets > 0) {
                (bestMatchingSetsAtTargetWeightReps.toDouble() / goal.targetSets.toDouble()) * 100.0
            } else 0.0
            val repsRatio = if (goal.targetReps > 0 && bestMatchingRepsAtTargetWeight > 0) {
                (bestMatchingRepsAtTargetWeight.toDouble() / goal.targetReps.toDouble()).coerceAtMost(1.0) * 100.0
            } else 0.0
            roundTenth((rawE1rmRatio * 0.65 + max(setsRatio, repsRatio * 0.8) * 0.35).coerceIn(0.0, 99.5))
        } else {
            roundTenth(rawE1rmRatio.coerceIn(0.0, 99.5))
        }

        val baseline = if (goal.baselineE1rmKg > 0.0 && goal.baselineE1rmKg < effectiveTargetE1rm) {
            goal.baselineE1rmKg
        } else {
            (sessions.firstOrNull()?.e1rm ?: (currentBestE1rm * 0.9)).coerceAtMost(effectiveTargetE1rm - 2.5)
        }
        val totalGap = (effectiveTargetE1rm - baseline).coerceAtLeast(1.0)
        val gapClosedPct = if (isAchieved) 100.0 else roundTenth(((currentBestE1rm - baseline) / totalGap * 100.0).coerceIn(0.0, 100.0))
        val remainingKg = if (isAchieved) 0.0 else roundHalf((effectiveTargetE1rm - currentBestE1rm).coerceAtLeast(0.0))

        // Frequency & Velocity analysis from previous sessions
        val nowMs = workouts.maxOfOrNull { DateUtils.toEpochMs(it.date) }?.takeIf { it > 0L }
            ?: DateUtils.toEpochMs(DateUtils.todayDdMonYyyy())
        val lastSessionMs = sessions.lastOrNull()?.let { DateUtils.toEpochMs(it.displayDate) } ?: 0L
        val daysSinceLast = if (lastSessionMs > 0L && nowMs >= lastSessionMs) {
            ((nowMs - lastSessionMs) / 86_400_000L).toInt()
        } else 14

        val fourWeeksMs = 28L * 86_400_000L
        val recent28dSessions = sessions.count { (nowMs - DateUtils.toEpochMs(it.displayDate)) in 0..fourWeeksMs }
        val sessionsPerWeek = roundTenth(
            if (recent28dSessions > 0) recent28dSessions / 4.0
            else if (sessions.size >= 2) {
                val spanDays = ((DateUtils.toEpochMs(sessions.last().displayDate) - DateUtils.toEpochMs(sessions.first().displayDate)) / 86_400_000L).coerceAtLeast(7L)
                (sessions.size.toDouble() / spanDays.toDouble()) * 7.0
            } else 0.5
        )

        val avgSetsPerSession = if (sessions.isNotEmpty()) {
            roundTenth(sessions.takeLast(6).map { s -> s.sets.count { (it.reps ?: 0) > 0 } }.average())
        } else 3.0

        // Plateau detection: check if >= 3 recent sessions have failed to improve over prior sessions
        val isPlateaued = if (sessions.size >= 4 && !isAchieved) {
            val last3Max = sessions.takeLast(3).maxOf { it.e1rm }
            val priorMax = sessions.dropLast(3).maxOf { it.e1rm }
            last3Max <= priorMax + 0.5
        } else false

        // Monthly E1RM velocity
        val monthlyGainKg = if (sessions.size >= 3) {
            val windowSessions = sessions.takeLast(min(8, sessions.size))
            val firstPt = windowSessions.first()
            val lastPt = windowSessions.last()
            val days = ((DateUtils.toEpochMs(lastPt.displayDate) - DateUtils.toEpochMs(firstPt.displayDate)) / 86_400_000L).coerceAtLeast(14L)
            val rawMonthly = ((lastPt.e1rm - firstPt.e1rm) / days.toDouble()) * 30.0
            roundTenth(rawMonthly.coerceIn(0.8, 8.0))
        } else {
            2.5
        }

        // Relative Strength & Tier
        val currentRelBw = roundHundredth(BodyWeightUtils.relativeStrength(currentBestE1rm, currentBwKg))
        val targetRelBw = roundHundredth(BodyWeightUtils.relativeStrength(effectiveTargetE1rm, currentBwKg))
        val strengthTier = classifyStrengthTier(exName, category, currentRelBw, currentBestE1rm)

        // Realistic weekly progression rate based on tier & historical velocity
        val expectedWeeklyGainKg = when (strengthTier) {
            "Elite" -> 0.35
            "Advanced" -> 0.5
            "Intermediate" -> 0.75
            else -> 1.1
        }.let { baseRate ->
            val histWeekly = (monthlyGainKg / 4.3).coerceIn(0.25, 1.75)
            (baseRate * 0.6 + histWeekly * 0.4)
        }

        val estimatedWeeksToGoal = if (isAchieved) 0 else {
            ceil(remainingKg / expectedWeeklyGainKg).toInt().coerceIn(1, 52)
        }

        val estimatedTargetDate = if (isAchieved) {
            goal.achievedDate ?: sessions.lastOrNull()?.displayDate ?: DateUtils.todayDdMonYyyy()
        } else if (goal.safeTargetDate.isNotBlank()) {
            goal.safeTargetDate
        } else {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, estimatedWeeksToGoal * 7)
            val d = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            val m = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")[cal.get(Calendar.MONTH)]
            val y = cal.get(Calendar.YEAR)
            "$d-$m-$y"
        }

        // Dominant rep zone from previous sessions
        val allValidReps = sessions.takeLast(6).flatMap { it.sets }.mapNotNull { it.reps }.filter { it > 0 }
        val avgRep = if (allValidReps.isNotEmpty()) allValidReps.average() else 8.0
        val dominantRepZone = when {
            avgRep <= 4.5 -> "Heavy Strength (1–4 reps)"
            avgRep <= 8.5 -> "Strength-Hypertrophy (5–8 reps)"
            else -> "High-Rep Hypertrophy (9+ reps)"
        }

        val status = when {
            isAchieved -> GoalStatus.ACHIEVED
            isPlateaued -> GoalStatus.PLATEAU_ALERT
            sessionsPerWeek < 0.8 || daysSinceLast > 14 -> GoalStatus.NEEDS_FREQUENCY
            sessions.size < 2 -> GoalStatus.BUILDING
            else -> GoalStatus.ON_TRACK
        }

        // Recommended Frequency & Weekly Sets
        val recommendedSessionsPerWeek = when {
            exName.lowercase().contains("deadlift") -> 2
            remainingKg > 15.0 || sessionsPerWeek < 1.2 -> 2
            else -> 2
        }
        val recommendedSetsPerWeek = recommendedSessionsPerWeek * 4

        // Select Best Programming Methodology & Build Next Session Prescription
        val workingBaseE1rm = (if (recentBestE1rm > 0.0) recentBestE1rm * 0.7 + currentBestE1rm * 0.3 else effectiveTargetE1rm * 0.85)
            .coerceAtLeast(20.0)

        fun loadForPctOfE1rm(pct: Double): Double {
            val targetTotalLoad = workingBaseE1rm * pct
            val rawExternal = if (isBw) (targetTotalLoad - currentBwKg).coerceAtLeast(0.0) else targetTotalLoad
            return roundToGymLoad(rawExternal, exName)
        }

        val (methodologyTitle, methodologyRationale, nextSessionSets, nextFocusCue) = when {
            isAchieved -> {
                val maintW = if (goal.isSetsRepsGoal) goal.targetWeightKg else loadForPctOfE1rm(0.83)
                val maintReps = if (goal.isSetsRepsGoal) goal.targetReps else 5
                val sets = (1..3).map { idx ->
                    PrescribedGoalSet(
                        setNo = idx,
                        role = if (idx == 1) "Anchor Set" else "Consolidation",
                        weightKg = maintW,
                        reps = maintReps,
                        rpe = "RPE 8",
                        notes = "Lock in mastery & set a new +2.5kg goal!"
                    )
                }
                Quadruple(
                    "Goal Consolidation & New Peak Block",
                    "You have already achieved this target! Consolidate the neural adaptation with 2–3 clean sessions at your target load, or raise your goal by +2.5–5kg.",
                    sets,
                    "Own every rep with controlled eccentric tempo before setting your next milestone."
                )
            }
            goal.isSetsRepsGoal -> {
                // Sets x Reps Specific Programming (Double Progression / Top-Set Bridge)
                val targetW = goal.targetWeightKg
                val targetR = goal.targetReps.coerceAtLeast(1)
                val targetS = goal.targetSets.coerceAtLeast(1)
                val lastSess = sessions.lastOrNull()
                val lastTopW = lastSess?.topWeight ?: currentTopWeight

                val sets = if (lastTopW >= targetW - 0.5 && bestMatchingRepsAtTargetWeight >= targetR - 2) {
                    // Ready to work directly at targetWeightKg and build reps/sets across sets
                    val aimRepsSet1 = min(targetR, max(bestMatchingRepsAtTargetWeight + 1, targetR - 1))
                    (1..targetS.coerceAtLeast(3)).map { idx ->
                        val r = if (idx == 1) aimRepsSet1 else max(targetR - 1, aimRepsSet1 - 1)
                        PrescribedGoalSet(
                            setNo = idx,
                            role = if (idx == 1) "Goal Pace Set" else "Volume Builder",
                            weightKg = targetW,
                            reps = r,
                            rpe = if (idx == 1) "RPE 8.5" else "RPE 8-9",
                            notes = if (idx == 1) "Aim for $aimRepsSet1 clean reps at goal weight" else "Match reps to build multi-set endurance"
                        )
                    }
                } else {
                    // Bridge up to targetWeightKg using 1 heavier acclimation set + volume back-offs
                    val stepW = roundToGymLoad(
                        if (lastTopW > 0.0) (lastTopW + targetW) / 2.0 else targetW * 0.9,
                        exName
                    )
                    listOf(
                        PrescribedGoalSet(1, "Overload Top Set", targetW, max(3, targetR - 2), "RPE 8.5", "Feel goal weight ($targetW kg) for ${max(3, targetR - 2)} reps"),
                        PrescribedGoalSet(2, "Bridge Volume", stepW, targetR, "RPE 8", "Hit full target rep count ($targetR reps)"),
                        PrescribedGoalSet(3, "Bridge Volume", stepW, targetR, "RPE 8.5", "Maintain bar speed across all $targetR reps"),
                        PrescribedGoalSet(4, "AMRAP Finisher", stepW, targetR + 1, "RPE 9", "Push +1 rep past target to drive E1RM")
                    ).take(max(3, targetS))
                }
                Quadruple(
                    "Double-Progression & Acclimation Wave",
                    "To lock in ${goal.targetSets} × ${goal.targetReps} @ ${formatWeightValue(targetW)}kg, expose your nervous system to ${formatWeightValue(targetW)}kg on Set 1 while accumulating full ${goal.targetReps}-rep volume on back-off sets.",
                    sets,
                    "Prioritize hitting ${sets.first().reps} reps on Set 1 with 1–2 reps in reserve before chasing back-off volume."
                )
            }
            isPlateaued -> {
                // Plateau Breaker: Daily Undulating Periodization (Heavy Top Set + Hypertrophy Back-Offs)
                val topW = loadForPctOfE1rm(0.86) // ~4-5 RM
                val backoffW = loadForPctOfE1rm(0.76) // ~8-9 RM
                val sets = listOf(
                    PrescribedGoalSet(1, "Heavy Neural Top Set", topW, 4, "RPE 8.5", "Break accommodation with heavier load for 4 explosive reps"),
                    PrescribedGoalSet(2, "Force Back-Off", backoffW, 8, "RPE 8", "Drop load ~10% and drive 8 crisp reps"),
                    PrescribedGoalSet(3, "Force Back-Off", backoffW, 8, "RPE 8.5", "Maintain full ROM and tight bracing"),
                    PrescribedGoalSet(4, "Plus-Set Overload", backoffW, 9, "RPE 9", "Aim for 9+ reps to trigger new E1RM PR")
                )
                Quadruple(
                    "DUP Top-Set + Back-Off Plateau Breaker",
                    "Your recent sessions plateaued in the $dominantRepZone zone. Switching to a heavy 4-rep neural top set followed by 8–9 rep back-offs recruits high-threshold motor units while expanding work capacity.",
                    sets,
                    "Do not grind Set 1 — move ${formatWeightValue(topW)}kg with maximum bar speed, then attack the back-off sets."
                )
            }
            remainingKg <= 5.0 -> {
                // Close to 1RM Goal: Peaking & Realization Wave
                val heavyDoubleW = loadForPctOfE1rm(0.90)
                val prTestRepW = loadForPctOfE1rm(0.84)
                val backoffW = loadForPctOfE1rm(0.78)
                val sets = listOf(
                    PrescribedGoalSet(1, "Peak Primer", heavyDoubleW, 3, "RPE 8.5", "Potentiate CNS at ~90% current E1RM"),
                    PrescribedGoalSet(2, "E1RM PR Breaker", prTestRepW, 6, "RPE 9", "6 reps @ ${formatWeightValue(prTestRepW)}kg pushes your E1RM toward ${formatWeightValue(effectiveTargetE1rm)}kg"),
                    PrescribedGoalSet(3, "Strength Volume", backoffW, 8, "RPE 8", "Clean mechanical tension set")
                )
                Quadruple(
                    "Strength Peaking & Realization Block",
                    "You are within ${formatWeightValue(remainingKg)}kg (${formatWeightValue(100.0 - progressPct)}%) of your ${formatWeightValue(effectiveTargetE1rm)}kg 1RM goal! A 3-rep primer followed by an all-out 6-rep PR set is the safest, highest-yield way to cross the finish line.",
                    sets,
                    "Take a full 3–4 minutes rest between Set 1 and Set 2 to maximize peak force output."
                )
            }
            else -> {
                // Progressive Linear / Wave Periodization for 1RM Goal
                val topSetW = loadForPctOfE1rm(0.83) // ~5-6 reps
                val volSetW = loadForPctOfE1rm(0.77) // ~8 reps
                val sets = listOf(
                    PrescribedGoalSet(1, "Primary Strength Set", topSetW, 5, "RPE 8.5", "Target 5 strong reps @ ${formatWeightValue(topSetW)}kg"),
                    PrescribedGoalSet(2, "Primary Strength Set", topSetW, 5, "RPE 8.5-9", "Match 5 reps with identical technique"),
                    PrescribedGoalSet(3, "Hypertrophy Back-Off", volSetW, 8, "RPE 8", "Build myofibrillar cross-sectional area"),
                    PrescribedGoalSet(4, "Hypertrophy Back-Off", volSetW, 8, "RPE 8.5", "Drive 8+ reps on final set")
                )
                Quadruple(
                    "Top-Set + Back-Off Strength-Hypertrophy Wave",
                    "Closing a ${formatWeightValue(remainingKg)}kg gap (from $strengthTier ${"%.2f".format(currentRelBw)}× BW to ${"%.2f".format(targetRelBw)}× BW) requires pairing heavy 5-rep strength sets with 8-rep hypertrophy back-offs $recommendedSessionsPerWeek× per week.",
                    sets,
                    "When you hit 6 reps on Set 1, add +2.5kg to all working sets the following session."
                )
            }
        }

        // Warmup sets based on first working set weight
        val firstWorkW = nextSessionSets.firstOrNull()?.weightKg ?: 40.0
        val warmupSets = if (!isBw && firstWorkW >= 30.0) {
            listOf(
                PrescribedGoalSet(1, "Warmup 50%", roundToGymLoad(firstWorkW * 0.50, exName), 8, "Warmup", "Groove movement pattern & blood flow"),
                PrescribedGoalSet(2, "Warmup 70%", roundToGymLoad(firstWorkW * 0.70, exName), 5, "Warmup", "Accelerate concentric bar speed"),
                PrescribedGoalSet(3, "Potentiation 85%", roundToGymLoad(firstWorkW * 0.85, exName), 2, "Warmup", "Prime nervous system without fatigue")
            )
        } else emptyList()

        val nextSessionSummary = nextSessionSets.map { s ->
            val wTxt = if (isBw) {
                if (s.weightKg <= 0.0) "BW" else "BW+${formatWeightValue(s.weightKg)}kg"
            } else "${formatWeightValue(s.weightKg)}kg"
            "1×${s.reps} @ $wTxt"
        }.let { list ->
            // Condense identical consecutive prescriptions
            val grouped = mutableListOf<Pair<String, Int>>()
            for (item in list) {
                val spec = item.substringAfter("1×")
                if (grouped.isNotEmpty() && grouped.last().first == spec) {
                    val last = grouped.removeLast()
                    grouped.add(last.first to (last.second + 1))
                } else {
                    grouped.add(spec to 1)
                }
            }
            grouped.joinToString(", ") { (spec, count) -> "${count}×$spec" }
        }

        // Body Weight Specific Advice
        val bodyWeightAdvice = if (isBw) {
            val reqAddedAtCurrBw = roundHalf((effectiveTargetE1rm / (1.0 + (if (goal.isSetsRepsGoal) goal.targetReps else 1) / 30.0)) - currentBwKg)
            "Bodyweight lift (Current BW: ${formatWeightValue(currentBwKg)}kg). Every 1kg of body weight change shifts your relative load directly. At ${formatWeightValue(currentBwKg)}kg BW, your target equates to ${"%.2f".format(targetRelBw)}× BW (approx +${formatWeightValue(reqAddedAtCurrBw.coerceAtLeast(0.0))}kg added weight)."
        } else {
            val bwTrendNote = when {
                bwDeltaKg <= -1.0 -> "You are down ${formatWeightValue(abs(bwDeltaKg))}kg BW — while cutting, prioritize heavy low-fatigue top sets (3–5 reps) and extra rest so leverage loss doesn't stall your E1RM."
                bwDeltaKg >= 1.0 -> "You are up +${formatWeightValue(bwDeltaKg)}kg BW — leverage this energy surplus to push an extra back-off volume set each session."
                else -> "At ${formatWeightValue(currentBwKg)}kg body weight, your current strength is ${"%.2f".format(currentRelBw)}× BW ($strengthTier) and your goal reaches ${"%.2f".format(targetRelBw)}× BW."
            }
            bwTrendNote
        }

        // 4-Phase Periodization Roadmap from current to target
        val startE1rm = workingBaseE1rm
        val totalSpan = (effectiveTargetE1rm - startE1rm).coerceAtLeast(2.5)
        val totalWks = estimatedWeeksToGoal.coerceAtLeast(4)
        val wkStep = max(1, totalWks / 4)

        val roadmapPhases = (1..4).map { phaseIdx ->
            val frac = phaseIdx / 4.0
            val phaseE1rm = roundHalf(startE1rm + totalSpan * frac)
            val (pTitle, reps, setsCnt, cue) = when (phaseIdx) {
                1 -> Quadruple("Phase 1: Hypertrophy & Work Capacity", 8, 4, "Accumulate clean 8-rep volume to expand muscle cross-section")
                2 -> Quadruple("Phase 2: Strength Transition", 6, 4, "Increase bar weight and drive 6-rep sets at RPE 8–8.5")
                3 -> Quadruple("Phase 3: Heavy Neural Overload", 4, 3, "Peak force production with heavy 4-rep sets & back-offs")
                else -> if (goal.isSetsRepsGoal) {
                    Quadruple("Phase 4: Goal Realization", goal.targetReps, goal.targetSets, "Execute ${goal.targetSets} × ${goal.targetReps} @ ${formatWeightValue(goal.targetWeightKg)}kg")
                } else {
                    Quadruple("Phase 4: Peak & Goal Realization", 3, 3, "Hit target E1RM (${formatWeightValue(effectiveTargetE1rm)}kg) via heavy triple or rep PR")
                }
            }
            val totalLoadForReps = phaseE1rm / (1.0 + reps / 30.0)
            val extWeight = if (goal.isSetsRepsGoal && phaseIdx == 4) {
                goal.targetWeightKg
            } else {
                val rawExt = if (isBw) (totalLoadForReps - currentBwKg).coerceAtLeast(0.0) else totalLoadForReps
                roundToGymLoad(rawExt, exName)
            }
            val wLabel = if (isBw) {
                if (extWeight <= 0.0) "BW" else "BW+${formatWeightValue(extWeight)}kg"
            } else "${formatWeightValue(extWeight)}kg"

            val startWk = (phaseIdx - 1) * wkStep + 1
            val endWk = if (phaseIdx == 4) max(startWk, totalWks) else phaseIdx * wkStep
            GoalPhaseStep(
                phaseNumber = phaseIdx,
                title = pTitle,
                weeksLabel = if (startWk == endWk) "Week $startWk" else "Weeks $startWk–$endWk",
                prescriptionSummary = "$setsCnt × $reps @ $wLabel",
                milestoneE1rmKg = phaseE1rm,
                focusCue = cue
            )
        }

        // Rep-Equivalence Table: what weight for 1, 3, 5, 8, 10, 12 reps equals effectiveTargetE1rm
        val repEquivalenceTable = listOf(1, 3, 5, 8, 10, 12).map { r ->
            val reqTotalLoad = if (r == 1) effectiveTargetE1rm else effectiveTargetE1rm / (1.0 + r / 30.0)
            val extLoad = if (isBw) (reqTotalLoad - currentBwKg).coerceAtLeast(0.0) else reqTotalLoad
            r to roundHalf(extLoad)
        }

        val accessories = buildAccessories(exName, category, allSets, nowMs)

        return GoalEvaluation(
            goal = goal,
            category = category,
            isBodyweight = isBw,
            currentBodyWeightKg = currentBwKg,
            bodyWeightDeltaKg = bwDeltaKg,
            currentBestE1rm = currentBestE1rm,
            recentBestE1rm = recentBestE1rm,
            currentTopWeight = currentTopWeight,
            currentTopWeightReps = currentTopWeightReps,
            bestSetSummary = bestSetSummary,
            bestMatchingRepsAtTargetWeight = bestMatchingRepsAtTargetWeight,
            bestMatchingSetsAtTargetWeightReps = bestMatchingSetsAtTargetWeightReps,
            effectiveTargetE1rm = effectiveTargetE1rm,
            progressPct = progressPct,
            gapClosedPct = gapClosedPct,
            remainingKg = remainingKg,
            isAchieved = isAchieved,
            status = status,
            currentRelBw = currentRelBw,
            targetRelBw = targetRelBw,
            strengthTier = strengthTier,
            totalSessions = totalSessions,
            sessionsPerWeek = sessionsPerWeek,
            recommendedSessionsPerWeek = recommendedSessionsPerWeek,
            avgSetsPerSession = avgSetsPerSession,
            recommendedSetsPerWeek = recommendedSetsPerWeek,
            daysSinceLastSession = daysSinceLast,
            monthlyE1rmGainKg = monthlyGainKg,
            estimatedWeeksToGoal = estimatedWeeksToGoal,
            estimatedTargetDate = estimatedTargetDate,
            isPlateaued = isPlateaued,
            dominantRepZone = dominantRepZone,
            methodologyTitle = methodologyTitle,
            methodologyRationale = methodologyRationale,
            bodyWeightAdvice = bodyWeightAdvice,
            nextSessionSets = nextSessionSets,
            warmupSets = warmupSets,
            nextSessionSummary = nextSessionSummary,
            nextSessionFocusCue = nextFocusCue,
            roadmapPhases = roadmapPhases,
            repEquivalenceTable = repEquivalenceTable,
            accessories = accessories,
            sessionHistory = sessions
        )
    }

    /**
     * Evaluates live sets currently entered in the active Workout Logger for an exercise with a goal.
     * Powers the 'Goal Progress' popup in LogWorkoutScreen and post-workout updates.
     */
    fun evaluateLiveSession(
        evaluation: GoalEvaluation,
        liveSets: List<Triple<Double?, Int?, Boolean>>, // (weight, reps, isCompleted)
        sessionDate: String = DateUtils.todayDdMonYyyy()
    ): LiveSessionGoalAnalysis {
        val goal = evaluation.goal
        val isBw = evaluation.isBodyweight
        val bwKg = BodyWeightUtils.weightOn(sessionDate).takeIf { it > 0.0 } ?: evaluation.currentBodyWeightKg

        var workingSetsCount = 0
        var completedCount = 0
        var bestE1rm = 0.0
        var bestWeight = 0.0
        var bestReps = 0
        var volumeKg = 0.0
        var qualifyingSets = 0

        for ((wRaw, rRaw, isComp) in liveSets) {
            val w = wRaw ?: 0.0
            val r = rRaw ?: 0
            if (r > 0) {
                workingSetsCount++
                if (isComp) completedCount++
                volumeKg += w * r
                if (w > bestWeight || (w == bestWeight && r > bestReps)) {
                    bestWeight = w
                    bestReps = r
                }
                val totalLoad = BodyWeightUtils.totalLoad(isBw, w, bwKg)
                if (totalLoad > 0.0) {
                    val e = computeEpley(totalLoad, r)
                    if (e > bestE1rm) bestE1rm = e
                }
                if (goal.isSetsRepsGoal && w >= goal.targetWeightKg - 0.01 && r >= goal.targetReps) {
                    qualifyingSets++
                }
            }
        }

        val combinedBestE1rm = max(evaluation.currentBestE1rm, bestE1rm)
        val achievedToday = if (goal.isSetsRepsGoal) {
            qualifyingSets >= goal.targetSets || bestE1rm >= evaluation.effectiveTargetE1rm + 2.5
        } else {
            bestE1rm >= evaluation.effectiveTargetE1rm - 0.05
        }

        val updatedProgressPct = if (achievedToday || evaluation.isAchieved) {
            100.0
        } else if (goal.isSetsRepsGoal) {
            val eRatio = (combinedBestE1rm / evaluation.effectiveTargetE1rm) * 100.0
            val bestQualSets = max(evaluation.bestMatchingSetsAtTargetWeightReps, qualifyingSets)
            val sRatio = if (goal.targetSets > 0) (bestQualSets.toDouble() / goal.targetSets.toDouble()) * 100.0 else 0.0
            roundTenth((eRatio * 0.65 + sRatio * 0.35).coerceIn(0.0, 99.5))
        } else {
            roundTenth(((combinedBestE1rm / evaluation.effectiveTargetE1rm) * 100.0).coerceIn(0.0, 99.5))
        }

        val prevRefE1rm = if (evaluation.recentBestE1rm > 0.0) evaluation.recentBestE1rm else evaluation.currentBestE1rm
        val deltaVsPrev = if (bestE1rm > 0.0 && prevRefE1rm > 0.0) roundHalf(bestE1rm - prevRefE1rm) else 0.0

        val prescribedTopSet = evaluation.nextSessionSets.firstOrNull()
        val prescribedTopE1rm = if (prescribedTopSet != null) {
            val pLoad = BodyWeightUtils.totalLoad(isBw, prescribedTopSet.weightKg, bwKg)
            computeEpley(pLoad, prescribedTopSet.reps)
        } else evaluation.recentBestE1rm

        val hitSessionTarget = achievedToday || (bestE1rm >= prescribedTopE1rm - 0.5 && workingSetsCount >= min(3, evaluation.nextSessionSets.size))

        val sessionGrade = when {
            achievedToday -> "S+ • GOAL ACHIEVED! 🏆"
            bestE1rm > evaluation.currentBestE1rm + 0.1 -> "S • NEW ALL-TIME PR!"
            hitSessionTarget -> "A+ • SESSION TARGET HIT"
            workingSetsCount >= 2 && bestE1rm >= prescribedTopE1rm * 0.96 -> "A • ON TRACK"
            workingSetsCount > 0 -> "B • IN PROGRESS"
            else -> "READY TO START"
        }

        val targetFirstSet = evaluation.nextSessionSets.firstOrNull()
        val nextSetIdx = (completedCount + 1).coerceAtMost(evaluation.nextSessionSets.size.coerceAtLeast(1))
        val targetCurrentSet = evaluation.nextSessionSets.getOrNull(nextSetIdx - 1) ?: targetFirstSet

        val nextSetTargetText = if (achievedToday) {
            "Goal crushed! Optional: finish with 1–2 controlled back-off sets at RPE 7–8."
        } else if (targetCurrentSet != null) {
            val wStr = if (isBw) {
                if (targetCurrentSet.weightKg <= 0.0) "BW" else "BW+${formatWeightValue(targetCurrentSet.weightKg)}kg"
            } else "${formatWeightValue(targetCurrentSet.weightKg)}kg"
            // Calculate how many reps at current weight would beat today's target or the goal
            val repsForGoalAtThisWeight = ((evaluation.effectiveTargetE1rm / BodyWeightUtils.totalLoad(isBw, targetCurrentSet.weightKg, bwKg).coerceAtLeast(1.0) - 1.0) * 30.0).let { ceil(it).toInt().coerceIn(1, 30) }
            "Aim for Set $nextSetIdx (${targetCurrentSet.role}): $wStr × ${targetCurrentSet.reps} reps (${targetCurrentSet.rpe}). Note: $wStr × $repsForGoalAtThisWeight reps = 100% Goal E1RM!"
        } else {
            "Complete 3–4 working sets pushing 1–2 reps shy of failure (RPE 8.5)."
        }

        val liveHeadline = when {
            achievedToday -> "Incredible session — your logged sets today officially achieved your ${goal.exerciseName} goal!"
            hitSessionTarget -> "Session target achieved! Today's best E1RM (${formatWeightValue(bestE1rm)}kg) keeps you right on pace for your goal."
            workingSetsCount > 0 -> {
                val gapToday = roundHalf((prescribedTopE1rm - bestE1rm).coerceAtLeast(0.0))
                if (gapToday > 0.0) {
                    "Current session best: ${formatWeightValue(bestE1rm)}kg Est. 1RM ($workingSetsCount sets). Add +1 rep or +2.5kg on your next set to hit today's ${formatWeightValue(prescribedTopE1rm)}kg target!"
                } else {
                    "Top set pace met (${formatWeightValue(bestE1rm)}kg Est. 1RM)! Complete ${max(1, evaluation.nextSessionSets.size - workingSetsCount)} more working set(s) to lock in today's volume."
                }
            }
            else -> "Today's Goal Prescription: ${evaluation.nextSessionSummary}. Hit this to drive your E1RM toward ${formatWeightValue(evaluation.effectiveTargetE1rm)}kg!"
        }

        val bullets = buildList {
            if (goal.isSetsRepsGoal) {
                add("Goal Target: ${goal.targetSets} sets × ${goal.targetReps} reps @ ${formatWeightValue(goal.targetWeightKg)}kg (Today: $qualifyingSets/${goal.targetSets} qualifying sets).")
            } else {
                add("Goal Target: ${formatWeightValue(evaluation.effectiveTargetE1rm)}kg Est. 1RM (Current Progress: ${formatWeightValue(updatedProgressPct)}%, ${formatWeightValue(max(0.0, evaluation.effectiveTargetE1rm - combinedBestE1rm))}kg remaining).")
            }
            add("Today's Prescribed Sets: ${evaluation.nextSessionSummary} (${evaluation.methodologyTitle}).")
            if (bestWeight > 0.0 && bestReps > 0) {
                val repNeededAtBestW = ceil(((evaluation.effectiveTargetE1rm / BodyWeightUtils.totalLoad(isBw, bestWeight, bwKg).coerceAtLeast(1.0)) - 1.0) * 30.0).toInt().coerceAtLeast(1)
                add("At today's top weight (${formatWeightValue(bestWeight)}kg), hitting $repNeededAtBestW reps equates to your ${formatWeightValue(evaluation.effectiveTargetE1rm)}kg goal.")
            }
            add("Coach Cue: ${evaluation.nextSessionFocusCue}")
        }

        return LiveSessionGoalAnalysis(
            evaluation = evaluation,
            loggedWorkingSetsCount = workingSetsCount,
            completedSetsCount = completedCount,
            sessionBestE1rm = bestE1rm,
            sessionBestWeight = bestWeight,
            sessionBestReps = bestReps,
            sessionVolumeKg = roundTenth(volumeKg),
            qualifyingGoalSetsCount = qualifyingSets,
            updatedProgressPct = updatedProgressPct,
            deltaVsPreviousBestKg = deltaVsPrev,
            hitSessionTargetToday = hitSessionTarget,
            achievedGoalToday = achievedToday,
            sessionGrade = sessionGrade,
            liveCoachingHeadline = liveHeadline,
            liveCoachingBullets = bullets,
            nextSetTargetText = nextSetTargetText
        )
    }

    /**
     * Generates a comprehensive [ExerciseGoalReport] for a specific exercise goal.
     */
    fun generateExerciseReport(
        goal: ExerciseGoal,
        workouts: List<Workout>,
        exerciseSets: List<WorkoutSet>,
        allSets: List<WorkoutSet> = exerciseSets,
        exercisesCatalog: List<Exercise> = emptyList(),
        bodyWeights: List<BodyWeightEntry> = emptyList()
    ): ExerciseGoalReport {
        val eval = evaluateGoal(goal, workouts, exerciseSets, allSets, exercisesCatalog, bodyWeights)
        val isBw = eval.isBodyweight

        val freqRatio = (eval.sessionsPerWeek / eval.recommendedSessionsPerWeek.toDouble()).coerceIn(0.2, 1.15)
        val progRatio = (eval.progressPct / 100.0).coerceIn(0.2, 1.0)
        val plateauPenalty = if (eval.isPlateaued) 15 else 0
        val adherenceScore = ((freqRatio * 50.0 + progRatio * 50.0).roundToInt() - plateauPenalty).coerceIn(35, 100)

        val baselineE1rm = if (goal.baselineE1rmKg > 0.0) goal.baselineE1rmKg else (eval.sessionHistory.firstOrNull()?.e1rm ?: eval.currentBestE1rm)
        val gainKg = roundHalf(eval.currentBestE1rm - baselineE1rm)
        val gainPct = if (baselineE1rm > 0.0) roundTenth((gainKg / baselineE1rm) * 100.0) else 0.0

        val recentVolAvg = eval.sessionHistory.takeLast(3).map { it.totalVolume }.average().takeIf { !it.isNaN() } ?: 0.0
        val priorVolAvg = eval.sessionHistory.dropLast(3).takeLast(3).map { it.totalVolume }.average().takeIf { !it.isNaN() } ?: recentVolAvg
        val volDeltaPct = if (priorVolAvg > 0.0) roundTenth(((recentVolAvg - priorVolAvg) / priorVolAvg) * 100.0) else 0.0
        val volumeTrendSummary = when {
            volDeltaPct >= 5.0 -> "Upward (+${formatWeightValue(volDeltaPct)}% session tonnage over last 3 sessions • Avg ${formatWeightValue(roundHalf(recentVolAvg))}kg)"
            volDeltaPct <= -5.0 -> "Tapering (${formatWeightValue(volDeltaPct)}% session tonnage over last 3 sessions • Avg ${formatWeightValue(roundHalf(recentVolAvg))}kg)"
            else -> "Steady (Avg ${formatWeightValue(roundHalf(recentVolAvg))}kg session volume across recent workouts)"
        }

        val executiveStatus = when {
            eval.isAchieved -> "GOAL ACHIEVED 🏆 — Target of ${if (goal.isSetsRepsGoal) "${goal.targetSets}×${goal.targetReps} @ ${formatWeightValue(goal.targetWeightKg)}kg" else "${formatWeightValue(eval.effectiveTargetE1rm)}kg E1RM"} reached!"
            eval.isPlateaued -> "PLATEAU DETECTED — Switch to ${eval.methodologyTitle} (${eval.nextSessionSummary}) to break stagnation."
            eval.status == GoalStatus.NEEDS_FREQUENCY -> "FREQUENCY DEFICIT — Last trained ${eval.daysSinceLastSession}d ago (${eval.sessionsPerWeek}/wk vs ${eval.recommendedSessionsPerWeek}/wk target)."
            else -> "ON TRACK — ${formatWeightValue(eval.progressPct)}% complete (${formatWeightValue(eval.remainingKg)}kg remaining • Est. ${eval.estimatedWeeksToGoal} wks to goal)."
        }

        val headlineSummary = if (goal.isSetsRepsGoal) {
            "${goal.exerciseName}: Target ${goal.targetSets} × ${goal.targetReps} @ ${formatWeightValue(goal.targetWeightKg)}kg (Equiv. ${formatWeightValue(eval.effectiveTargetE1rm)}kg E1RM) • Best E1RM: ${formatWeightValue(eval.currentBestE1rm)}kg (${"%.2f".format(eval.currentRelBw)}× BW)"
        } else {
            "${goal.exerciseName}: Target ${formatWeightValue(eval.effectiveTargetE1rm)}kg 1RM (${"%.2f".format(eval.targetRelBw)}× BW) • Current Best: ${formatWeightValue(eval.currentBestE1rm)}kg (${"%.2f".format(eval.currentRelBw)}× BW)"
        }

        val strengths = buildList {
            if (eval.currentBestE1rm > 0.0) {
                add("Peak E1RM of ${formatWeightValue(eval.currentBestE1rm)}kg places you in the ${eval.strengthTier} strength tier (${"%.2f".format(eval.currentRelBw)}× body weight at ${formatWeightValue(eval.currentBodyWeightKg)}kg BW).")
            }
            if (gainKg > 0.0) {
                add("Gained +${formatWeightValue(gainKg)}kg (+${formatWeightValue(gainPct)}%) on Estimated 1RM from baseline (${formatWeightValue(baselineE1rm)}kg → ${formatWeightValue(eval.currentBestE1rm)}kg).")
            }
            if (eval.sessionsPerWeek >= 1.2) {
                add("Strong consistency: averaging ${eval.sessionsPerWeek} sessions/week and ${eval.avgSetsPerSession} working sets/session.")
            }
            if (volDeltaPct > 0.0) {
                add("Positive work capacity trend: recent session volume is up +${formatWeightValue(volDeltaPct)}%.")
            }
            if (isEmpty()) {
                add("Goal initialized and calibrated to your ${formatWeightValue(eval.currentBodyWeightKg)}kg body weight and historical profile.")
            }
        }

        val bottlenecks = buildList {
            if (eval.isPlateaued) {
                add("Stagnation across last 3+ sessions in the ${eval.dominantRepZone} range — introduce ${eval.methodologyTitle} next session (${eval.nextSessionSummary}).")
            }
            if (eval.sessionsPerWeek < eval.recommendedSessionsPerWeek - 0.3) {
                add("Training frequency is ${eval.sessionsPerWeek}×/week — increasing to ${eval.recommendedSessionsPerWeek}×/week (~${eval.recommendedSetsPerWeek} sets/week) will accelerate neural adaptations by ~35%.")
            }
            val missingAcc = eval.accessories.filter { it.recentSessions == 0 }
            if (missingAcc.isNotEmpty()) {
                add("Accessory gap: add ${missingAcc.joinToString(" or ") { it.exerciseName }} (2–3 sets/week) to reinforce weak-point leverage.")
            }
            add("Next Session Action: Aim for ${eval.nextSessionSummary}. ${eval.nextSessionFocusCue}")
        }

        // Build chronological session entries (most recent first)
        val sessionEntries = eval.sessionHistory.mapIndexed { idx, pt ->
            val prevPt = if (idx > 0) eval.sessionHistory[idx - 1] else null
            val deltaE1rm = if (prevPt != null) roundHalf(pt.e1rm - prevPt.e1rm) else 0.0
            val pctAtSess = roundTenth(((pt.e1rm / eval.effectiveTargetE1rm) * 100.0).coerceIn(0.0, 100.0))
            val validSets = pt.sets.filter { (it.reps ?: 0) > 0 }
            val hitGoalInSess = if (goal.isSetsRepsGoal) {
                validSets.count { (it.weight ?: 0.0) >= goal.targetWeightKg - 0.01 && (it.reps ?: 0) >= goal.targetReps } >= goal.targetSets
            } else {
                pt.e1rm >= eval.effectiveTargetE1rm - 0.05
            }
            val note = when {
                hitGoalInSess -> "🏆 Goal Target Achieved!"
                pt.isE1rmPr -> "★ New All-Time E1RM PR (${formatWeightValue(pt.e1rm)}kg)"
                deltaE1rm > 0.0 -> "+${formatWeightValue(deltaE1rm)}kg E1RM vs prior session"
                deltaE1rm < 0.0 -> "Volume / recovery session (${formatWeightValue(deltaE1rm)}kg vs prior)"
                else -> "Maintained ${formatWeightValue(pt.e1rm)}kg E1RM"
            }
            ExerciseGoalReportSessionEntry(
                date = pt.displayDate,
                workoutTitle = pt.workoutTitle.ifBlank { "Workout Session" },
                setsSummary = SetUtils.formatSetsSummary(validSets, isBw),
                sessionBestE1rm = pt.e1rm,
                topWeight = pt.topWeight,
                topReps = pt.topWeightReps,
                totalVolumeKg = pt.totalVolume,
                workingSets = validSets.size,
                progressPctAtSession = pctAtSess,
                deltaVsPrevE1rm = deltaE1rm,
                hitMilestone = hitGoalInSess || pt.isE1rmPr,
                coachNote = note
            )
        }.reversed()

        return ExerciseGoalReport(
            evaluation = eval,
            generatedDate = DateUtils.todayDdMonYyyy(),
            headlineSummary = headlineSummary,
            executiveStatus = executiveStatus,
            adherenceScore = adherenceScore,
            strengthGainSinceGoalStartKg = gainKg,
            strengthGainPct = gainPct,
            volumeTrendSummary = volumeTrendSummary,
            bodyWeightImpactSummary = eval.bodyWeightAdvice,
            keyStrengths = strengths,
            bottlenecksAndFixes = bottlenecks,
            sessionEntries = sessionEntries
        )
    }

    /**
     * Generates an executive [AllGoalsMasterReport] across all exercises that have goals configured.
     */
    fun generateAllGoalsReport(
        goals: List<ExerciseGoal>,
        workouts: List<Workout>,
        allSets: List<WorkoutSet>,
        exercisesCatalog: List<Exercise> = emptyList(),
        bodyWeights: List<BodyWeightEntry> = emptyList()
    ): AllGoalsMasterReport {
        val setsByExLower = allSets.groupBy { it.exercise.trim().lowercase() }
        val reports = goals.map { g ->
            val exSets = setsByExLower[g.exerciseName.trim().lowercase()] ?: emptyList()
            generateExerciseReport(g, workouts, exSets, allSets, exercisesCatalog, bodyWeights)
        }.sortedWith(
            compareByDescending<ExerciseGoalReport> { it.evaluation.goal.enabled }
                .thenByDescending { it.evaluation.progressPct }
        )

        val totalGoals = reports.size
        val activeGoals = reports.count { it.evaluation.goal.enabled }
        val achievedCount = reports.count { it.evaluation.isAchieved }
        val onTrackCount = reports.count { !it.evaluation.isAchieved && it.evaluation.status == GoalStatus.ON_TRACK }
        val plateauedCount = reports.count { !it.evaluation.isAchieved && (it.evaluation.isPlateaued || it.evaluation.status == GoalStatus.NEEDS_FREQUENCY) }

        val overallCompletionPct = if (reports.isNotEmpty()) {
            roundTenth(reports.map { it.evaluation.progressPct }.average())
        } else 0.0

        val avgAdherence = if (reports.isNotEmpty()) {
            reports.map { it.adherenceScore }.average().roundToInt()
        } else 0

        val currentBw = if (bodyWeights.isNotEmpty()) {
            BodyWeightUtils.weightOn(DateUtils.todayDdMonYyyy(), bodyWeights)
        } else BodyWeightUtils.latestKg()

        val strongestRel = reports.maxByOrNull { it.evaluation.currentRelBw }?.let {
            "${it.evaluation.goal.exerciseName} (${"%.2f".format(it.evaluation.currentRelBw)}× BW • ${it.evaluation.strengthTier})"
        } ?: "–"

        val closestGoal = reports.filter { !it.evaluation.isAchieved }
            .maxByOrNull { it.evaluation.progressPct }?.let {
                "${it.evaluation.goal.exerciseName} (${formatWeightValue(it.evaluation.progressPct)}% • ${formatWeightValue(it.evaluation.remainingKg)}kg left)"
            } ?: if (achievedCount > 0) "All active goals achieved! 🏆" else "–"

        val priorityGoal = reports.filter { !it.evaluation.isAchieved }
            .minByOrNull { it.adherenceScore - (if (it.evaluation.isPlateaued) 25 else 0) }?.let {
                "${it.evaluation.goal.exerciseName} (${it.evaluation.status.label})"
            } ?: "None — all lifts progressing smoothly"

        val takeaways = buildList {
            if (reports.isEmpty()) {
                add("No exercise goals set yet. Create your first 1RM or Sets × Reps goal on the Goals screen to unlock personalized periodization and session reports.")
            } else {
                add("Portfolio Completion: ${formatWeightValue(overallCompletionPct)}% average progress across $totalGoals goal lift${if (totalGoals == 1) "" else "s"} ($achievedCount achieved, $onTrackCount on track, $plateauedCount needing programming adjustments).")
                add("Relative Strength Profile (@ ${formatWeightValue(currentBw)}kg BW): Strongest relative movement is $strongestRel.")
                if (closestGoal != "–") {
                    add("Closest Breakthrough: $closestGoal.")
                }
                val stalledList = reports.filter { it.evaluation.isPlateaued }
                if (stalledList.isNotEmpty()) {
                    add("Plateau Action Required: ${stalledList.joinToString(", ") { it.evaluation.goal.exerciseName }} should transition to Top-Set + Back-Off waves on the next session.")
                }
                val neglectedList = reports.filter { !it.evaluation.isAchieved && it.evaluation.daysSinceLastSession > 10 }
                if (neglectedList.isNotEmpty()) {
                    add("Frequency Alert: ${neglectedList.joinToString(", ") { "${it.evaluation.goal.exerciseName} (${it.evaluation.daysSinceLastSession}d ago)" }} — schedule within the next 48 hours.")
                }
            }
        }

        return AllGoalsMasterReport(
            generatedDate = DateUtils.todayDdMonYyyy(),
            totalGoalsCount = totalGoals,
            activeGoalsCount = activeGoals,
            achievedGoalsCount = achievedCount,
            onTrackCount = onTrackCount,
            plateauedCount = plateauedCount,
            overallPortfolioCompletionPct = overallCompletionPct,
            averageAdherenceScore = avgAdherence,
            currentBodyWeightKg = currentBw,
            strongestRelativeLift = strongestRel,
            closestToCompletionGoal = closestGoal,
            priorityAttentionGoal = priorityGoal,
            executiveTakeaways = takeaways,
            exerciseReports = reports
        )
    }

    /**
     * Builds a [GoalSessionReport] snapshot and updates the [ExerciseGoal] when a workout session is logged.
     */
    fun updateGoalWithLoggedSession(
        goal: ExerciseGoal,
        workout: Workout,
        workoutExerciseSets: List<WorkoutSet>,
        allExerciseSetsAfterSave: List<WorkoutSet>,
        allWorkoutsAfterSave: List<Workout>,
        exercisesCatalog: List<Exercise> = emptyList(),
        bodyWeights: List<BodyWeightEntry> = emptyList()
    ): ExerciseGoal {
        val validSessionSets = workoutExerciseSets.filter { (it.reps ?: 0) > 0 }
        if (validSessionSets.isEmpty()) return goal

        val eval = evaluateGoal(
            goal = goal,
            workouts = allWorkoutsAfterSave,
            exerciseSets = allExerciseSetsAfterSave,
            allSets = allExerciseSetsAfterSave,
            exercisesCatalog = exercisesCatalog,
            bodyWeights = bodyWeights
        )
        val isBw = eval.isBodyweight
        val sessBw = BodyWeightUtils.weightOn(workout.date, bodyWeights.ifEmpty { emptyList() }).takeIf { it > 0.0 }
            ?: BodyWeightUtils.weightOn(workout.date)

        var sessBestE1rm = 0.0
        var sessTopW = 0.0
        var sessTopR = 0
        var sessVol = 0.0
        var qualifyingSets = 0

        for (s in validSessionSets) {
            val w = s.weight ?: 0.0
            val r = s.reps ?: 0
            sessVol += w * r
            if (w > sessTopW || (w == sessTopW && r > sessTopR)) {
                sessTopW = w
                sessTopR = r
            }
            val load = BodyWeightUtils.totalLoad(isBw, w, sessBw)
            val e = computeEpley(load, r)
            if (e > sessBestE1rm) sessBestE1rm = e
            if (goal.isSetsRepsGoal && w >= goal.targetWeightKg - 0.01 && r >= goal.targetReps) {
                qualifyingSets++
            }
        }

        val priorSessions = eval.sessionHistory.filter { it.workoutId != workout.id }
        val prevBestE1rm = priorSessions.lastOrNull()?.e1rm ?: goal.baselineE1rmKg
        val deltaKg = if (prevBestE1rm > 0.0) roundHalf(sessBestE1rm - prevBestE1rm) else 0.0

        val achievedInThisSession = if (goal.isSetsRepsGoal) {
            qualifyingSets >= goal.targetSets || sessBestE1rm >= eval.effectiveTargetE1rm + 2.5
        } else {
            sessBestE1rm >= eval.effectiveTargetE1rm - 0.05
        }

        val summaryNote = when {
            achievedInThisSession -> "🏆 Goal Achieved! Logged ${SetUtils.formatSetsSummary(validSessionSets, isBw)} (${formatWeightValue(sessBestE1rm)}kg E1RM)."
            deltaKg > 0.0 -> "Progress +${formatWeightValue(deltaKg)}kg E1RM vs last session (${SetUtils.formatSetsSummary(validSessionSets, isBw)}) • Now at ${formatWeightValue(eval.progressPct)}% of goal."
            else -> "Logged ${validSessionSets.size} working sets (${SetUtils.formatSetsSummary(validSessionSets, isBw)}) • Best E1RM ${formatWeightValue(sessBestE1rm)}kg (${formatWeightValue(eval.progressPct)}% of goal)."
        }

        val newReport = GoalSessionReport(
            workoutId = workout.id,
            date = DateUtils.toDdMonYyyy(workout.date),
            sessionBestE1rm = sessBestE1rm,
            sessionTopWeight = sessTopW,
            sessionTopReps = sessTopR,
            workingSetsCount = validSessionSets.size,
            totalVolumeKg = roundTenth(sessVol),
            progressPct = if (achievedInThisSession) 100.0 else eval.progressPct,
            deltaE1rmKg = deltaKg,
            achievedGoal = achievedInThisSession,
            summaryNote = summaryNote
        )

        val updatedReports = (goal.safeReports.filter { it.workoutId != workout.id } + newReport)
            .sortedByDescending { DateUtils.toSortableIso(it.date) }
            .take(30)

        return goal.copy(
            lastUpdatedDate = DateUtils.toDdMonYyyy(workout.date),
            achievedDate = if (achievedInThisSession && goal.achievedDate.isNullOrBlank()) {
                DateUtils.toDdMonYyyy(workout.date)
            } else goal.achievedDate,
            sessionReports = updatedReports
        )
    }

    /**
     * Converts today's prescribed goal sets into DraftExerciseLog / SetInputState strings so the user
     * can load a goal session directly into the workout logger with 1 tap.
     */
    fun prescribedSetsForLogger(evaluation: GoalEvaluation): List<Triple<String, String, String>> {
        return evaluation.nextSessionSets.map { ps ->
            Triple(
                formatWeightValue(ps.weightKg),
                ps.reps.toString(),
                "${ps.role} (${ps.rpe})"
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
