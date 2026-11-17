package com.strengthlogz.mobile.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class Workout(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: String,
    @SerializedName("title") val title: String = "",
    @SerializedName("notes") val notes: String = ""
)

@Immutable
data class WorkoutSet(
    @SerializedName("id") val id: String,
    @SerializedName("workoutId") val workoutId: String,
    @SerializedName("date") val date: String,
    @SerializedName("exercise") val exercise: String,
    @SerializedName("setNo") val setNo: Int,
    @SerializedName("weight") val weight: Double?,
    @SerializedName("reps") val reps: Int?,
    @SerializedName("notes") val notes: String = ""
)

@Immutable
data class Exercise(
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String = "Other",
    @SerializedName("bodyweight") val bodyweight: Boolean = false,
    @SerializedName("description") val description: String? = ""
) {
    val safeDescription: String get() = description ?: ""
}

/** Body weight reading in kg. [date] is DD-MON-YYYY; [source] is "seed", "manual" or "healthconnect". */
@Immutable
data class BodyWeightEntry(
    @SerializedName("date") val date: String = "",
    @SerializedName("weightKg") val weightKg: Double = 0.0,
    @SerializedName("source") val source: String = "manual",
    /** Epoch ms of the last edit; 0 for legacy entries. Used for cross-device sync conflict resolution. */
    @SerializedName("updatedAt") val updatedAt: Long = 0L
)

/**
 * Snapshot report recorded whenever a workout session is logged for an exercise with an active goal.
 */
@Immutable
data class GoalSessionReport(
    @SerializedName("workoutId") val workoutId: String = "",
    @SerializedName("date") val date: String = "",
    @SerializedName("sessionBestE1rm") val sessionBestE1rm: Double = 0.0,
    @SerializedName("sessionTopWeight") val sessionTopWeight: Double = 0.0,
    @SerializedName("sessionTopReps") val sessionTopReps: Int = 0,
    @SerializedName("workingSetsCount") val workingSetsCount: Int = 0,
    @SerializedName("totalVolumeKg") val totalVolumeKg: Double = 0.0,
    @SerializedName("progressPct") val progressPct: Double = 0.0,
    @SerializedName("deltaE1rmKg") val deltaE1rmKg: Double = 0.0,
    @SerializedName("achievedGoal") val achievedGoal: Boolean = false,
    @SerializedName("summaryNote") val summaryNote: String? = ""
) {
    val safeSummaryNote: String get() = summaryNote ?: ""
}

/**
 * User-defined target goal for a specific exercise.
 * [goalType] is either "E1RM" (1-Rep Max target) or "SETS_REPS" (Target sets x reps @ weight).
 */
@Immutable
data class ExerciseGoal(
    @SerializedName("id") val id: String = "",
    @SerializedName("exerciseName") val exerciseName: String = "",
    @SerializedName("goalType") val goalType: String? = "E1RM",
    @SerializedName("targetE1rmKg") val targetE1rmKg: Double = 0.0,
    @SerializedName("targetWeightKg") val targetWeightKg: Double = 0.0,
    @SerializedName("targetSets") val targetSets: Int = 3,
    @SerializedName("targetReps") val targetReps: Int = 8,
    @SerializedName("baselineE1rmKg") val baselineE1rmKg: Double = 0.0,
    @SerializedName("baselineWeightKg") val baselineWeightKg: Double = 0.0,
    @SerializedName("baselineReps") val baselineReps: Int = 0,
    @SerializedName("baselineBodyWeightKg") val baselineBodyWeightKg: Double = 0.0,
    @SerializedName("createdDate") val createdDate: String? = "",
    @SerializedName("targetDate") val targetDate: String? = "",
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("achievedDate") val achievedDate: String? = null,
    @SerializedName("lastUpdatedDate") val lastUpdatedDate: String? = null,
    @SerializedName("notes") val notes: String? = "",
    @SerializedName("sessionReports") val sessionReports: List<GoalSessionReport>? = emptyList(),
    @SerializedName("updatedAt") val updatedAt: Long = 0L
) {
    val safeGoalType: String get() = if (goalType == "SETS_REPS") "SETS_REPS" else "E1RM"
    val isSetsRepsGoal: Boolean get() = safeGoalType == "SETS_REPS"
    val safeCreatedDate: String get() = createdDate ?: ""
    val safeTargetDate: String get() = targetDate ?: ""
    val safeNotes: String get() = notes ?: ""
    val safeReports: List<GoalSessionReport> get() = sessionReports ?: emptyList()
}

@Immutable
data class ProgramExerciseItem(
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("category") val category: String = "Other",
    @SerializedName("targetSets") val targetSets: Int = 3,
    @SerializedName("targetReps") val targetReps: Int = 8,
    @SerializedName("notes") val notes: String? = ""
) {
    val safeNotes: String get() = notes ?: ""
}

@Immutable
data class ProgramDay(
    @SerializedName("dayIndex") val dayIndex: Int,
    @SerializedName("dayName") val dayName: String,
    @SerializedName("focus") val focus: String? = "",
    @SerializedName("exercises") val exercises: List<ProgramExerciseItem>? = emptyList()
) {
    val safeFocus: String get() = focus ?: ""
    val safeExercises: List<ProgramExerciseItem> get() = exercises ?: emptyList()
}

@Immutable
data class WorkoutProgram(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("daysPerWeek") val daysPerWeek: Int = 0,
    @SerializedName("programName") val programName: String? = "",
    @SerializedName("days") val days: List<ProgramDay>? = emptyList(),
    @SerializedName("lastLoadedDayIndex") val lastLoadedDayIndex: Int = -1,
    @SerializedName("createdAt") val createdAt: String? = "",
    @SerializedName("updatedAt") val updatedAt: Long = 0L
) {
    val safeId: String get() = if (id.isNotBlank()) id else java.util.UUID.randomUUID().toString()
    val safeProgramName: String get() = programName?.ifBlank { "Custom Program" } ?: "Custom Program"
    val safeDays: List<ProgramDay> get() = days ?: emptyList()
    val daysCount: Int get() = safeDays.size
}

@Immutable
data class SavedSummaryLiftItem(
    @SerializedName("exerciseName") val exerciseName: String = "",
    @SerializedName("category") val category: String = "Other",
    @SerializedName("isBodyweight") val isBodyweight: Boolean = false,
    @SerializedName("sessionsIn14d") val sessionsIn14d: Int = 0,
    @SerializedName("workingSets14d") val workingSets14d: Int = 0,
    @SerializedName("volumeKg14d") val volumeKg14d: Double = 0.0,
    @SerializedName("bestSet14d") val bestSet14d: String = "",
    @SerializedName("bestE1rm14dKg") val bestE1rm14dKg: Double = 0.0,
    @SerializedName("priorBestE1rmKg") val priorBestE1rmKg: Double? = null,
    @SerializedName("e1rmDeltaKg") val e1rmDeltaKg: Double? = null,
    @SerializedName("e1rmDeltaPct") val e1rmDeltaPct: Double? = null,
    @SerializedName("withinPeriodE1rmDeltaKg") val withinPeriodE1rmDeltaKg: Double? = null,
    @SerializedName("relativeStrengthBw") val relativeStrengthBw: Double? = null,
    @SerializedName("isAllTimePr") val isAllTimePr: Boolean = false
)

@Immutable
data class SavedSummaryMuscleItem(
    @SerializedName("category") val category: String = "",
    @SerializedName("workingSets14d") val workingSets14d: Int = 0,
    @SerializedName("prior14dWorkingSets") val prior14dWorkingSets: Int = 0,
    @SerializedName("volumeKg14d") val volumeKg14d: Double = 0.0,
    @SerializedName("target14dSets") val target14dSets: Int = 0
)

@Immutable
data class SavedSummaryEntry(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("title") val title: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("savedAt") val savedAt: String = "",
    @SerializedName("savedEpochMs") val savedEpochMs: Long = System.currentTimeMillis(),
    @SerializedName("startDate") val startDate: String = "",
    @SerializedName("endDate") val endDate: String = "",
    @SerializedName("overallScore") val overallScore: Int = 0,
    @SerializedName("tierLabel") val tierLabel: String = "",
    @SerializedName("volumeScore") val volumeScore: Int = 0,
    @SerializedName("strengthScore") val strengthScore: Int = 0,
    @SerializedName("consistencyScore") val consistencyScore: Int = 0,
    @SerializedName("balanceScore") val balanceScore: Int = 0,
    @SerializedName("workoutsCount") val workoutsCount: Int = 0,
    @SerializedName("priorWorkoutsCount") val priorWorkoutsCount: Int = 0,
    @SerializedName("workingSetsCount") val workingSetsCount: Int = 0,
    @SerializedName("priorWorkingSetsCount") val priorWorkingSetsCount: Int = 0,
    @SerializedName("totalReps") val totalReps: Int = 0,
    @SerializedName("failedSetsCount") val failedSetsCount: Int = 0,
    @SerializedName("totalVolumeKg") val totalVolumeKg: Double = 0.0,
    @SerializedName("priorVolumeKg") val priorVolumeKg: Double = 0.0,
    @SerializedName("volumeDeltaPct") val volumeDeltaPct: Double? = null,
    @SerializedName("startBodyWeightKg") val startBodyWeightKg: Double? = null,
    @SerializedName("endBodyWeightKg") val endBodyWeightKg: Double? = null,
    @SerializedName("bodyWeightDeltaKg") val bodyWeightDeltaKg: Double? = null,
    @SerializedName("avg7BodyWeightKg") val avg7BodyWeightKg: Double? = null,
    @SerializedName("dotsScore") val dotsScore: Double? = null,
    @SerializedName("prsCount") val prsCount: Int = 0,
    @SerializedName("progressingCount") val progressingCount: Int = 0,
    @SerializedName("holdingCount") val holdingCount: Int = 0,
    @SerializedName("regressingCount") val regressingCount: Int = 0,
    @SerializedName("pushToPullRatio") val pushToPullRatio: Double? = null,
    @SerializedName("upperSharePct") val upperSharePct: Int = 0,
    @SerializedName("heavySets") val heavySets: Int = 0,
    @SerializedName("strengthHypSets") val strengthHypSets: Int = 0,
    @SerializedName("hypertrophySets") val hypertrophySets: Int = 0,
    @SerializedName("enduranceSets") val enduranceSets: Int = 0,
    @SerializedName("muscleItems") val muscleItems: List<SavedSummaryMuscleItem>? = emptyList(),
    @SerializedName("topLifts") val topLifts: List<SavedSummaryLiftItem>? = emptyList(),
    @SerializedName("highlights") val highlights: List<String>? = emptyList(),
    @SerializedName("recommendations") val recommendations: List<String>? = emptyList()
) {
    val safeMuscleItems: List<SavedSummaryMuscleItem> get() = muscleItems ?: emptyList()
    val safeTopLifts: List<SavedSummaryLiftItem> get() = topLifts ?: emptyList()
    val safeHighlights: List<String> get() = highlights ?: emptyList()
    val safeRecommendations: List<String> get() = recommendations ?: emptyList()
}

data class AppData(
    @SerializedName("workouts") val workouts: MutableList<Workout> = mutableListOf(),
    @SerializedName("sets") val sets: MutableList<WorkoutSet> = mutableListOf(),
    @SerializedName("exercises") val exercises: MutableList<Exercise> = mutableListOf(),
    @SerializedName("bodyWeights") var bodyWeights: MutableList<BodyWeightEntry>? = mutableListOf(),
    @SerializedName("goals") var goals: MutableList<ExerciseGoal>? = mutableListOf(),
    @SerializedName("program") var program: WorkoutProgram? = null,
    @SerializedName("programs") var programs: MutableList<WorkoutProgram>? = mutableListOf(),
    @SerializedName("activeProgramId") var activeProgramId: String? = null,
    @SerializedName("savedSummaries") var savedSummaries: MutableList<SavedSummaryEntry>? = mutableListOf()
)

@Immutable
data class DraftSetInput(
    @SerializedName("weight") val weight: String = "",
    @SerializedName("reps") val reps: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("isCompleted") val isCompleted: Boolean = false
)

@Immutable
data class DraftExerciseLog(
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("category") val category: String = "Other",
    @SerializedName("sets") val sets: List<DraftSetInput> = emptyList(),
    @SerializedName("notes") val notes: String = ""
)

@Immutable
data class ActiveWorkoutDraft(
    @SerializedName("date") val date: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("exercises") val exercises: List<DraftExerciseLog> = emptyList(),
    @SerializedName("lastUpdatedEpochMs") val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)
