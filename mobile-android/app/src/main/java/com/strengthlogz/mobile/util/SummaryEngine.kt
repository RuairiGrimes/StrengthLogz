package com.strengthlogz.mobile.util

import androidx.compose.runtime.Immutable
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.SavedSummaryEntry
import com.strengthlogz.mobile.model.SavedSummaryLiftItem
import com.strengthlogz.mobile.model.SavedSummaryMuscleItem
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutProgram
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.computeEpley
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class SummarySessionItem(
    val workoutId: String,
    val date: String,
    val dayOfWeek: String,
    val title: String,
    val workingSets: Int,
    val volumeKg: Double,
    val exercisesCount: Int
)

@Immutable
data class SummaryGoalStatusItem(
    val exerciseName: String,
    val targetSummary: String,
    val progressPct: Int,
    val trainedIn14d: Boolean,
    val achieved: Boolean
)

@Immutable
data class FourteenDaySummary(
    val startDate: String,
    val endDate: String,
    val priorStartDate: String,
    val priorEndDate: String,
    val latestLoggedWorkoutDate: String?,
    val daysSinceLastWorkout: Int?,
    val overallScore: Int,
    val tierLabel: String,
    val volumeScore: Int,
    val strengthScore: Int,
    val consistencyScore: Int,
    val balanceScore: Int,
    val workoutsCount: Int,
    val priorWorkoutsCount: Int,
    val sessionsPerWeek: Double,
    val activeDaysCount: Int,
    val longestRestGapDays: Int,
    val programName: String?,
    val programTargetSessions14d: Int?,
    val programAdherencePct: Int?,
    val workingSetsCount: Int,
    val priorWorkingSetsCount: Int,
    val totalReps: Int,
    val failedSetsCount: Int,
    val totalVolumeKg: Double,
    val priorVolumeKg: Double,
    val volumeDeltaKg: Double,
    val volumeDeltaPct: Double?,
    val effectiveBwAdjustedVolumeKg: Double,
    val avgVolumePerWorkoutKg: Double,
    val avgSetsPerWorkout: Double,
    val avgRepsPerSet: Double,
    val heavySets: Int,
    val strengthHypSets: Int,
    val hypertrophySets: Int,
    val enduranceSets: Int,
    val pushSets: Int,
    val pullSets: Int,
    val pushToPullRatio: Double?,
    val upperSets: Int,
    val lowerSets: Int,
    val upperSharePct: Int,
    val hasBodyWeightData: Boolean,
    val startBodyWeightKg: Double?,
    val endBodyWeightKg: Double?,
    val bodyWeightDeltaKg: Double?,
    val avg7BodyWeightKg: Double?,
    val bwWeeklyRateKg: Double?,
    val dotsScore: Double?,
    val dotsDelta: Double?,
    val prsCount: Int,
    val progressingCount: Int,
    val holdingCount: Int,
    val regressingCount: Int,
    val muscleItems: List<SavedSummaryMuscleItem>,
    val lifts: List<SavedSummaryLiftItem>,
    val sessions: List<SummarySessionItem>,
    val goalStatuses: List<SummaryGoalStatusItem>,
    val highlights: List<String>,
    val recommendations: List<String>
) {
    fun toSavedEntry(customTitle: String = "", customNotes: String = ""): SavedSummaryEntry {
        val defaultTitle = if (workoutsCount == 0) {
            "14-Day Block ($startDate - $endDate)"
        } else {
            "$startDate - $endDate ($workoutsCount sessions)"
        }
        return SavedSummaryEntry(
            id = java.util.UUID.randomUUID().toString(),
            title = customTitle.trim().ifBlank { defaultTitle },
            notes = customNotes.trim(),
            savedAt = DateUtils.nowTimestampDdMonYyyy(),
            savedEpochMs = System.currentTimeMillis(),
            startDate = startDate,
            endDate = endDate,
            overallScore = overallScore,
            tierLabel = tierLabel,
            volumeScore = volumeScore,
            strengthScore = strengthScore,
            consistencyScore = consistencyScore,
            balanceScore = balanceScore,
            workoutsCount = workoutsCount,
            priorWorkoutsCount = priorWorkoutsCount,
            workingSetsCount = workingSetsCount,
            priorWorkingSetsCount = priorWorkingSetsCount,
            totalReps = totalReps,
            failedSetsCount = failedSetsCount,
            totalVolumeKg = totalVolumeKg,
            priorVolumeKg = priorVolumeKg,
            volumeDeltaPct = volumeDeltaPct,
            startBodyWeightKg = startBodyWeightKg,
            endBodyWeightKg = endBodyWeightKg,
            bodyWeightDeltaKg = bodyWeightDeltaKg,
            avg7BodyWeightKg = avg7BodyWeightKg,
            dotsScore = dotsScore,
            prsCount = prsCount,
            progressingCount = progressingCount,
            holdingCount = holdingCount,
            regressingCount = regressingCount,
            pushToPullRatio = pushToPullRatio,
            upperSharePct = upperSharePct,
            heavySets = heavySets,
            strengthHypSets = strengthHypSets,
            hypertrophySets = hypertrophySets,
            enduranceSets = enduranceSets,
            muscleItems = muscleItems,
            topLifts = lifts.take(10),
            highlights = highlights,
            recommendations = recommendations
        )
    }
}

object SummaryEngine {
    private val WEEKLY_MUSCLE_TARGETS = linkedMapOf(
        "Chest" to 10,
        "Back" to 12,
        "Legs" to 10,
        "Shoulders" to 9,
        "Arms" to 9,
        "Core" to 4
    )

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0

    private fun formatEpochDayDdMonYyyy(epochDay: Long): String {
        val ld = LocalDate.ofEpochDay(epochDay)
        val mon = ld.format(DateTimeFormatter.ofPattern("MMM", Locale.US)).uppercase(Locale.US)
        val day = ld.dayOfMonth.toString().padStart(2, '0')
        return "$day-$mon-${ld.year}"
    }

    fun build14DaySummary(
        workouts: List<Workout>,
        sets: List<WorkoutSet>,
        exercises: List<Exercise>,
        bodyWeights: List<BodyWeightEntry>,
        goals: List<ExerciseGoal>,
        activeProgram: WorkoutProgram? = null,
        isMale: Boolean = true,
        referenceDateDdMonYyyy: String = DateUtils.todayDdMonYyyy()
    ): FourteenDaySummary {
        val endIso = DateUtils.toSortableIso(referenceDateDdMonYyyy).ifBlank {
            DateUtils.toSortableIso(DateUtils.todayDdMonYyyy())
        }
        val endDay = StrengthAnalytics.epochDay(endIso)
        val startDay = endDay - 13L
        val priorEndDay = startDay - 1L
        val priorStartDay = priorEndDay - 13L

        val startDateStr = formatEpochDayDdMonYyyy(startDay)
        val endDateStr = formatEpochDayDdMonYyyy(endDay)
        val priorStartStr = formatEpochDayDdMonYyyy(priorStartDay)
        val priorEndStr = formatEpochDayDdMonYyyy(priorEndDay)

        val workoutById = workouts.associateBy { it.id }
        val exerciseMap = exercises.associateBy { it.name.trim().lowercase() }
        val hasBwData = bodyWeights.any { it.weightKg > 0.0 }

        fun resolveCategory(exName: String): String =
            exerciseMap[exName.trim().lowercase()]?.category?.ifBlank { "Other" } ?: "Other"

        fun resolveIsBw(exName: String): Boolean =
            exerciseMap[exName.trim().lowercase()]?.bodyweight ?: BodyWeightUtils.isBodyweightExercise(exName)

        fun setDateStr(s: WorkoutSet): String =
            workoutById[s.workoutId]?.date?.ifBlank { s.date } ?: s.date

        fun setEpochDay(s: WorkoutSet): Long =
            StrengthAnalytics.epochDay(setDateStr(s))

        val workoutDays = workouts.mapNotNull { w ->
            val d = StrengthAnalytics.epochDay(w.date)
            if (d > 0L) w to d else null
        }
        val latestWorkoutPair = workoutDays.maxByOrNull { it.second }
        val latestLoggedWorkoutDate = latestWorkoutPair?.first?.let { DateUtils.toDdMonYyyy(it.date) }
        val daysSinceLastWorkout = latestWorkoutPair?.let { (endDay - it.second).coerceAtLeast(0L).toInt() }

        val workouts14d = workoutDays
            .filter { it.second in startDay..endDay }
            .sortedWith(compareByDescending<Pair<Workout, Long>> { it.second }.thenByDescending { it.first.id })
        val priorWorkouts14d = workoutDays.filter { it.second in priorStartDay..priorEndDay }

        val setsIn14dAll = mutableListOf<WorkoutSet>()
        val validSets14d = mutableListOf<WorkoutSet>()
        val validSetsPrior14d = mutableListOf<WorkoutSet>()
        val validSetsBeforeWindow = mutableListOf<WorkoutSet>()

        for (s in sets) {
            val d = setEpochDay(s)
            if (d <= 0L) continue
            if (d in startDay..endDay) {
                setsIn14dAll.add(s)
                if ((s.reps ?: 0) > 0) validSets14d.add(s)
            } else if (d in priorStartDay..priorEndDay) {
                if ((s.reps ?: 0) > 0) {
                    validSetsPrior14d.add(s)
                    validSetsBeforeWindow.add(s)
                }
            } else if (d < startDay) {
                if ((s.reps ?: 0) > 0) validSetsBeforeWindow.add(s)
            }
        }

        val failedSetsCount = setsIn14dAll.count { (it.reps ?: 0) <= 0 }
        val totalReps = validSets14d.sumOf { it.reps ?: 0 }
        val totalVolumeKg = round1(validSets14d.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) })
        val priorVolumeKg = round1(validSetsPrior14d.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) })
        val volumeDeltaKg = round1(totalVolumeKg - priorVolumeKg)
        val volumeDeltaPct = if (priorVolumeKg > 0.0) {
            round1(((totalVolumeKg - priorVolumeKg) / priorVolumeKg) * 100.0)
        } else null

        fun e1rmForSet(s: WorkoutSet): Double {
            val reps = s.reps ?: 0
            if (reps <= 0) return 0.0
            val isBw = resolveIsBw(s.exercise)
            val date = setDateStr(s)
            val bwKg = if (isBw) {
                if (hasBwData) BodyWeightUtils.weightOn(date, bodyWeights) else BodyWeightUtils.FALLBACK_KG
            } else 0.0
            val load = BodyWeightUtils.totalLoad(isBw, s.weight, bwKg)
            return if (load > 0.0) computeEpley(load, reps) else 0.0
        }

        val effectiveBwVolume = round1(
            validSets14d.sumOf { s ->
                val reps = s.reps ?: 0
                val isBw = resolveIsBw(s.exercise)
                val bwKg = if (isBw) {
                    if (hasBwData) BodyWeightUtils.weightOn(setDateStr(s), bodyWeights) else BodyWeightUtils.FALLBACK_KG
                } else 0.0
                val load = BodyWeightUtils.totalLoad(isBw, s.weight, bwKg)
                load * reps
            }
        )

        val workoutsCount = if (workouts14d.isNotEmpty()) {
            workouts14d.size
        } else {
            validSets14d.map { it.workoutId.ifBlank { setDateStr(it) } }.distinct().size
        }
        val priorWorkoutsCount = if (priorWorkouts14d.isNotEmpty()) {
            priorWorkouts14d.size
        } else {
            validSetsPrior14d.map { it.workoutId.ifBlank { setDateStr(it) } }.distinct().size
        }

        // Active days & longest rest gap within 14-day window
        val activeEpochDays = (workouts14d.map { it.second } + validSets14d.map { setEpochDay(it) })
            .filter { it in startDay..endDay }
            .distinct()
            .sorted()
        val activeDaysCount = activeEpochDays.size
        val longestRestGapDays = if (activeEpochDays.isEmpty()) {
            14
        } else {
            var maxGap = (activeEpochDays.first() - startDay).toInt()
            for (i in 1 until activeEpochDays.size) {
                val gap = (activeEpochDays[i] - activeEpochDays[i - 1] - 1L).toInt().coerceAtLeast(0)
                if (gap > maxGap) maxGap = gap
            }
            val trailingGap = (endDay - activeEpochDays.last()).toInt().coerceAtLeast(0)
            maxOf(maxGap, trailingGap)
        }

        val progDaysCount = activeProgram?.safeDays?.size ?: 0
        val programTarget14d = if (progDaysCount > 0) progDaysCount * 2 else null
        val programAdherencePct = if (programTarget14d != null && programTarget14d > 0) {
            ((workoutsCount.toDouble() / programTarget14d) * 100.0).roundToInt().coerceIn(0, 100)
        } else null

        val sessionsPerWeek = round1(workoutsCount / 2.0)
        val avgVolumePerWorkoutKg = if (workoutsCount > 0) round1(totalVolumeKg / workoutsCount) else 0.0
        val avgSetsPerWorkout = if (workoutsCount > 0) round1(validSets14d.size.toDouble() / workoutsCount) else 0.0
        val avgRepsPerSet = if (validSets14d.isNotEmpty()) round1(totalReps.toDouble() / validSets14d.size) else 0.0

        // Rep-zone distribution
        var heavySets = 0
        var strengthHypSets = 0
        var hypertrophySets = 0
        var enduranceSets = 0
        for (s in validSets14d) {
            val r = s.reps ?: 0
            when {
                r <= 5 -> heavySets++
                r <= 8 -> strengthHypSets++
                r <= 12 -> hypertrophySets++
                else -> enduranceSets++
            }
        }

        // Muscle group breakdown & structural balance
        val sets14ByCat = mutableMapOf<String, Int>()
        val vol14ByCat = mutableMapOf<String, Double>()
        for (s in validSets14d) {
            val cat = resolveCategory(s.exercise)
            sets14ByCat[cat] = (sets14ByCat[cat] ?: 0) + 1
            vol14ByCat[cat] = (vol14ByCat[cat] ?: 0.0) + (s.weight ?: 0.0) * (s.reps ?: 0)
        }
        val priorSetsByCat = mutableMapOf<String, Int>()
        for (s in validSetsPrior14d) {
            val cat = resolveCategory(s.exercise)
            priorSetsByCat[cat] = (priorSetsByCat[cat] ?: 0) + 1
        }
        val muscleItems = WEEKLY_MUSCLE_TARGETS.map { (cat, wkTarget) ->
            SavedSummaryMuscleItem(
                category = cat,
                workingSets14d = sets14ByCat[cat] ?: 0,
                prior14dWorkingSets = priorSetsByCat[cat] ?: 0,
                volumeKg14d = round1(vol14ByCat[cat] ?: 0.0),
                target14dSets = wkTarget * 2
            )
        }

        val pushSets = (sets14ByCat["Chest"] ?: 0) + (sets14ByCat["Shoulders"] ?: 0)
        val pullSets = sets14ByCat["Back"] ?: 0
        val pushToPullRatio = if (pullSets > 0) round2(pushSets.toDouble() / pullSets) else null
        val upperSets = pushSets + pullSets + (sets14ByCat["Arms"] ?: 0)
        val lowerSets = sets14ByCat["Legs"] ?: 0
        val upperSharePct = if (upperSets + lowerSets > 0) {
            ((upperSets.toDouble() / (upperSets + lowerSets)) * 100.0).roundToInt()
        } else 0

        // Body weight & DOTS
        val startBw = if (hasBwData) round1(BodyWeightUtils.weightOn(startDateStr, bodyWeights)) else null
        val endBw = if (hasBwData) round1(BodyWeightUtils.weightOn(endDateStr, bodyWeights)) else null
        val bwDelta = if (startBw != null && endBw != null) round2(endBw - startBw) else null
        val bwSeries = if (hasBwData) StrengthAnalytics.buildBodyWeightSeries(bodyWeights) else emptyList()
        val bwInPeriod = bwSeries.filter { it.epochDay in startDay..endDay }
        val avg7Bw = bwSeries.lastOrNull { it.epochDay <= endDay }?.let { round1(it.avg7Kg) }
        val bwWeeklyRate = if (bwInPeriod.size >= 2) {
            val spanDays = (bwInPeriod.last().epochDay - bwInPeriod.first().epochDay).coerceAtLeast(1L)
            if (spanDays >= 4L) round2((bwInPeriod.last().weightKg - bwInPeriod.first().weightKg) / (spanDays / 7.0)) else null
        } else null

        val dotsTimeline = if (hasBwData) StrengthAnalytics.buildDotsTimeline(workouts, sets, bodyWeights, isMale) else emptyList()
        val latestDots = dotsTimeline.lastOrNull { it.isoDate <= endIso }?.let { round1(it.dots) }
        val priorDots = dotsTimeline.lastOrNull { it.isoDate < DateUtils.toSortableIso(startDateStr) }?.let { round1(it.dots) }
        val dotsDelta = if (latestDots != null && priorDots != null) round1(latestDots - priorDots) else null

        // Movement progression & PR detection
        val sets14ByEx = validSets14d.groupBy { it.exercise.trim().lowercase() }
        val priorByEx = validSetsBeforeWindow.groupBy { it.exercise.trim().lowercase() }

        var prsCount = 0
        var progressingCount = 0
        var holdingCount = 0
        var regressingCount = 0

        val lifts = sets14ByEx.values.map { exSets14 ->
            val exName = exSets14.first().exercise.trim()
            val cat = resolveCategory(exName)
            val isBw = resolveIsBw(exName)

            val bySession = exSets14.groupBy { it.workoutId.ifBlank { setDateStr(it) } }
                .map { (_, sList) ->
                    val d = setEpochDay(sList.first())
                    val bestE = sList.maxOfOrNull { e1rmForSet(it) } ?: 0.0
                    d to bestE
                }
                .sortedBy { it.first }

            val firstSessE = bySession.firstOrNull()?.second ?: 0.0
            val lastSessE = bySession.lastOrNull()?.second ?: 0.0
            val withinDelta = if (bySession.size >= 2 && firstSessE > 0.0 && lastSessE > 0.0) {
                round1(lastSessE - firstSessE)
            } else null

            var bestSet: WorkoutSet? = null
            var bestE1rm14 = 0.0
            var exVol14 = 0.0
            for (s in exSets14) {
                val w = s.weight ?: 0.0
                val r = s.reps ?: 0
                exVol14 += w * r
                val e = e1rmForSet(s)
                if (e > bestE1rm14) {
                    bestE1rm14 = e
                    bestSet = s
                }
            }

            val priorSets = priorByEx[exName.lowercase()].orEmpty()
            val priorBestE1rm = priorSets.maxOfOrNull { e1rmForSet(it) }?.takeIf { it > 0.0 }
            val recentPriorSets = priorSets.filter { setEpochDay(it) >= priorStartDay - 28L }.ifEmpty { priorSets }
            val recentBaselineE1rm = recentPriorSets.maxOfOrNull { e1rmForSet(it) }?.takeIf { it > 0.0 }

            val e1rmDelta = if (recentBaselineE1rm != null && bestE1rm14 > 0.0) {
                round1(bestE1rm14 - recentBaselineE1rm)
            } else null
            val e1rmDeltaPct = if (recentBaselineE1rm != null && recentBaselineE1rm > 0.0 && e1rmDelta != null) {
                round1((e1rmDelta / recentBaselineE1rm) * 100.0)
            } else null

            val isAllTimePr = bestE1rm14 > 0.0 && priorBestE1rm != null && bestE1rm14 > priorBestE1rm

            val trendDelta = e1rmDelta ?: withinDelta
            if (isAllTimePr) prsCount++
            when {
                trendDelta != null && trendDelta > 0.5 -> progressingCount++
                trendDelta != null && trendDelta < -0.5 -> regressingCount++
                else -> holdingCount++
            }

            val bestSetStr = if (bestSet != null) {
                val w = bestSet.weight ?: 0.0
                val wPart = if (isBw) {
                    if (w == 0.0) "BW" else "BW+${formatWeightValue(w)}kg"
                } else {
                    "${formatWeightValue(w)}kg"
                }
                "$wPart x ${bestSet.reps ?: 0}"
            } else "-"

            val relBw = if (endBw != null && endBw > 0.0 && bestE1rm14 > 0.0) {
                round2(bestE1rm14 / endBw)
            } else null

            SavedSummaryLiftItem(
                exerciseName = exName,
                category = cat,
                isBodyweight = isBw,
                sessionsIn14d = bySession.size,
                workingSets14d = exSets14.size,
                volumeKg14d = round1(exVol14),
                bestSet14d = bestSetStr,
                bestE1rm14dKg = round1(bestE1rm14),
                priorBestE1rmKg = recentBaselineE1rm?.let { round1(it) },
                e1rmDeltaKg = e1rmDelta,
                e1rmDeltaPct = e1rmDeltaPct,
                withinPeriodE1rmDeltaKg = withinDelta,
                relativeStrengthBw = relBw,
                isAllTimePr = isAllTimePr
            )
        }.sortedWith(
            compareByDescending<SavedSummaryLiftItem> { it.isAllTimePr }
                .thenByDescending { it.e1rmDeltaKg ?: it.withinPeriodE1rmDeltaKg ?: 0.0 }
                .thenByDescending { it.workingSets14d }
        )

        // Sessions list
        val sets14ByWorkoutId = validSets14d.groupBy { it.workoutId }
        val sessions = workouts14d.map { (w, _) ->
            val wSets = sets14ByWorkoutId[w.id] ?: emptyList()
            val wVol = round1(wSets.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) })
            SummarySessionItem(
                workoutId = w.id,
                date = DateUtils.toDdMonYyyy(w.date),
                dayOfWeek = DateUtils.getDayOfWeek(w.date),
                title = w.title.ifBlank { "Workout Session" },
                workingSets = wSets.size,
                volumeKg = wVol,
                exercisesCount = wSets.map { it.exercise.trim().lowercase() }.distinct().size
            )
        }

        // Goal status check
        val goalStatuses = goals.filter { it.enabled }.map { g ->
            val exSets = sets.filter { it.exercise.equals(g.exerciseName, ignoreCase = true) }
            val eval = GoalEngine.evaluateGoal(
                goal = g,
                workouts = workouts,
                exerciseSets = exSets,
                allSets = sets,
                exercisesCatalog = exercises,
                bodyWeights = bodyWeights
            )
            val trainedRecently = sets14ByEx.containsKey(g.exerciseName.trim().lowercase())
            val targetStr = if (g.isSetsRepsGoal) {
                "${g.targetSets}x${g.targetReps} @ ${formatWeightValue(g.targetWeightKg)}kg"
            } else {
                "${formatWeightValue(g.targetE1rmKg)}kg 1RM"
            }
            SummaryGoalStatusItem(
                exerciseName = g.exerciseName,
                targetSummary = targetStr,
                progressPct = eval.progressPct.roundToInt().coerceIn(0, 100),
                trainedIn14d = trainedRecently,
                achieved = eval.isAchieved
            )
        }

        // Deterministic 0-100 Scoring
        val (overallScore, volScore, strScore, conScore, balScore, tierLabel) = computeDeterministicScores(
            workoutsCount = workoutsCount,
            programTarget14d = programTarget14d,
            longestRestGapDays = longestRestGapDays,
            validSetsCount = validSets14d.size,
            volumeDeltaPct = volumeDeltaPct,
            muscleItems = muscleItems,
            lifts = lifts,
            prsCount = prsCount,
            progressingCount = progressingCount,
            regressingCount = regressingCount,
            pushToPullRatio = pushToPullRatio,
            upperSets = upperSets,
            lowerSets = lowerSets
        )

        val (highlights, recommendations) = buildInsights(
            workoutsCount = workoutsCount,
            priorWorkoutsCount = priorWorkoutsCount,
            totalVolumeKg = totalVolumeKg,
            volumeDeltaPct = volumeDeltaPct,
            failedSetsCount = failedSetsCount,
            longestRestGapDays = longestRestGapDays,
            programName = activeProgram?.safeProgramName,
            programAdherencePct = programAdherencePct,
            muscleItems = muscleItems,
            lifts = lifts,
            prsCount = prsCount,
            hasBodyWeightData = hasBwData,
            bodyWeightDeltaKg = bwDelta,
            bwWeeklyRateKg = bwWeeklyRate,
            dotsScore = latestDots,
            dotsDelta = dotsDelta,
            pushToPullRatio = pushToPullRatio,
            upperSets = upperSets,
            lowerSets = lowerSets,
            goalStatuses = goalStatuses,
            daysSinceLastWorkout = daysSinceLastWorkout
        )

        return FourteenDaySummary(
            startDate = startDateStr,
            endDate = endDateStr,
            priorStartDate = priorStartStr,
            priorEndDate = priorEndStr,
            latestLoggedWorkoutDate = latestLoggedWorkoutDate,
            daysSinceLastWorkout = daysSinceLastWorkout,
            overallScore = overallScore,
            tierLabel = tierLabel,
            volumeScore = volScore,
            strengthScore = strScore,
            consistencyScore = conScore,
            balanceScore = balScore,
            workoutsCount = workoutsCount,
            priorWorkoutsCount = priorWorkoutsCount,
            sessionsPerWeek = sessionsPerWeek,
            activeDaysCount = activeDaysCount,
            longestRestGapDays = longestRestGapDays,
            programName = activeProgram?.safeProgramName,
            programTargetSessions14d = programTarget14d,
            programAdherencePct = programAdherencePct,
            workingSetsCount = validSets14d.size,
            priorWorkingSetsCount = validSetsPrior14d.size,
            totalReps = totalReps,
            failedSetsCount = failedSetsCount,
            totalVolumeKg = totalVolumeKg,
            priorVolumeKg = priorVolumeKg,
            volumeDeltaKg = volumeDeltaKg,
            volumeDeltaPct = volumeDeltaPct,
            effectiveBwAdjustedVolumeKg = effectiveBwVolume,
            avgVolumePerWorkoutKg = avgVolumePerWorkoutKg,
            avgSetsPerWorkout = avgSetsPerWorkout,
            avgRepsPerSet = avgRepsPerSet,
            heavySets = heavySets,
            strengthHypSets = strengthHypSets,
            hypertrophySets = hypertrophySets,
            enduranceSets = enduranceSets,
            pushSets = pushSets,
            pullSets = pullSets,
            pushToPullRatio = pushToPullRatio,
            upperSets = upperSets,
            lowerSets = lowerSets,
            upperSharePct = upperSharePct,
            hasBodyWeightData = hasBwData,
            startBodyWeightKg = startBw,
            endBodyWeightKg = endBw,
            bodyWeightDeltaKg = bwDelta,
            avg7BodyWeightKg = avg7Bw,
            bwWeeklyRateKg = bwWeeklyRate,
            dotsScore = latestDots,
            dotsDelta = dotsDelta,
            prsCount = prsCount,
            progressingCount = progressingCount,
            holdingCount = holdingCount,
            regressingCount = regressingCount,
            muscleItems = muscleItems,
            lifts = lifts,
            sessions = sessions,
            goalStatuses = goalStatuses,
            highlights = highlights,
            recommendations = recommendations
        )
    }

    private data class ScoreBundle(
        val overall: Int,
        val volume: Int,
        val strength: Int,
        val consistency: Int,
        val balance: Int,
        val tier: String
    )

    private fun computeDeterministicScores(
        workoutsCount: Int,
        programTarget14d: Int?,
        longestRestGapDays: Int,
        validSetsCount: Int,
        volumeDeltaPct: Double?,
        muscleItems: List<SavedSummaryMuscleItem>,
        lifts: List<SavedSummaryLiftItem>,
        prsCount: Int,
        progressingCount: Int,
        regressingCount: Int,
        pushToPullRatio: Double?,
        upperSets: Int,
        lowerSets: Int
    ): ScoreBundle {
        if (workoutsCount == 0 || validSetsCount == 0) {
            return ScoreBundle(0, 0, 0, 0, 0, "No Activity")
        }

        // 1. Consistency (0-100): frequency against target (program or 6 sessions/14d) + gap penalty
        val targetSessions = (programTarget14d ?: 6).coerceAtLeast(2)
        val freqRatio = (workoutsCount.toDouble() / targetSessions).coerceIn(0.0, 1.1)
        val gapPenalty = ((longestRestGapDays - 4).coerceAtLeast(0)) * 6
        val consistencyScore = ((freqRatio * 90.0) + if (longestRestGapDays <= 3) 10.0 else 0.0 - gapPenalty)
            .roundToInt().coerceIn(10, 100)

        // 2. Volume (0-100): avg target attainment across main muscle groups + continuity
        val primaryMuscles = muscleItems.filter { it.category != "Core" }
        val avgMuscleCoverage = primaryMuscles.map {
            (it.workingSets14d.toDouble() / it.target14dSets.coerceAtLeast(1)).coerceAtMost(1.1)
        }.average().let { if (it.isNaN()) 0.0 else it }
        val continuityAdj = when {
            volumeDeltaPct == null -> 5.0
            volumeDeltaPct in -15.0..25.0 -> 8.0
            volumeDeltaPct > 25.0 -> 4.0
            else -> -6.0
        }
        val volumeScore = ((avgMuscleCoverage * 88.0) + continuityAdj).roundToInt().coerceIn(10, 100)

        // 3. Strength (0-100): ratio of progressing/holding lifts, average E1RM delta, and PR bonus
        val trackedWithBaseline = lifts.filter { (it.e1rmDeltaPct ?: it.withinPeriodE1rmDeltaKg) != null }
        val strengthScore = if (lifts.isEmpty()) {
            0
        } else {
            val netPositiveRatio = (progressingCount + 0.5 * (lifts.size - progressingCount - regressingCount)) / lifts.size.toDouble()
            val avgDeltaPct = trackedWithBaseline.mapNotNull { it.e1rmDeltaPct }.average().let { if (it.isNaN()) 0.0 else it }
            val prBonus = (prsCount * 6).coerceAtMost(18)
            val raw = 45.0 + (netPositiveRatio * 35.0) + (avgDeltaPct.coerceIn(-10.0, 10.0) * 1.5) + prBonus
            raw.roundToInt().coerceIn(15, 100)
        }

        // 4. Balance (0-100): muscle coverage count + push:pull symmetry + lower body presence
        val coveredCount = primaryMuscles.count { it.workingSets14d >= (it.target14dSets * 0.45) }
        val coveragePts = (coveredCount / 5.0) * 60.0
        val pushPullPts = when {
            pushToPullRatio == null -> 10.0
            pushToPullRatio in 0.75..1.35 -> 25.0
            pushToPullRatio in 0.55..1.65 -> 16.0
            else -> 6.0
        }
        val lowerPts = when {
            upperSets + lowerSets == 0 -> 0.0
            lowerSets == 0 -> 0.0
            (lowerSets.toDouble() / (upperSets + lowerSets)) >= 0.22 -> 15.0
            else -> 8.0
        }
        val balanceScore = (coveragePts + pushPullPts + lowerPts).roundToInt().coerceIn(10, 100)

        val overall = (consistencyScore * 0.25 + volumeScore * 0.25 + strengthScore * 0.30 + balanceScore * 0.20)
            .roundToInt().coerceIn(0, 100)

        val tier = when {
            overall >= 88 -> "Peak Block"
            overall >= 74 -> "Strong Momentum"
            overall >= 58 -> "Steady Progress"
            overall >= 40 -> "Building Base"
            else -> "Low Stimulus"
        }

        return ScoreBundle(overall, volumeScore, strengthScore, consistencyScore, balanceScore, tier)
    }

    private fun buildInsights(
        workoutsCount: Int,
        priorWorkoutsCount: Int,
        totalVolumeKg: Double,
        volumeDeltaPct: Double?,
        failedSetsCount: Int,
        longestRestGapDays: Int,
        programName: String?,
        programAdherencePct: Int?,
        muscleItems: List<SavedSummaryMuscleItem>,
        lifts: List<SavedSummaryLiftItem>,
        prsCount: Int,
        hasBodyWeightData: Boolean,
        bodyWeightDeltaKg: Double?,
        bwWeeklyRateKg: Double?,
        dotsScore: Double?,
        dotsDelta: Double?,
        pushToPullRatio: Double?,
        upperSets: Int,
        lowerSets: Int,
        goalStatuses: List<SummaryGoalStatusItem>,
        daysSinceLastWorkout: Int?
    ): Pair<List<String>, List<String>> {
        val highlights = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        if (workoutsCount == 0) {
            val lastInfo = if (daysSinceLastWorkout != null) "Last recorded session was $daysSinceLastWorkout days ago." else "No sessions logged yet."
            highlights.add("Zero working sessions recorded in the trailing 14-day window. $lastInfo")
            recommendations.add("Log a baseline Push, Pull, or Full Body session to re-establish training momentum.")
            return highlights to recommendations
        }

        // Volume highlight
        if (volumeDeltaPct != null) {
            val dir = if (volumeDeltaPct >= 0) "+${formatWeightValue(volumeDeltaPct)}%" else "${formatWeightValue(volumeDeltaPct)}%"
            highlights.add("Volume totaled ${formatWeightValue(totalVolumeKg)} kg across $workoutsCount sessions ($dir vs prior 14 days, $priorWorkoutsCount sessions).")
        } else {
            highlights.add("Logged $workoutsCount sessions totaling ${formatWeightValue(totalVolumeKg)} kg of external workload.")
        }

        // Top strength progression / PRs
        if (prsCount > 0) {
            val topPr = lifts.firstOrNull { it.isAllTimePr }
            if (topPr != null) {
                highlights.add("$prsCount all-time E1RM PR${if (prsCount > 1) "s" else ""} set, led by ${topPr.exerciseName} at ${formatWeightValue(topPr.bestE1rm14dKg)} kg (${topPr.bestSet14d}).")
            }
        } else {
            val bestGain = lifts.filter { (it.e1rmDeltaKg ?: 0.0) > 0.0 }.maxByOrNull { it.e1rmDeltaKg ?: 0.0 }
            if (bestGain != null) {
                highlights.add("Top strength gain on ${bestGain.exerciseName}: +${formatWeightValue(bestGain.e1rmDeltaKg)} kg est. 1RM (${bestGain.bestSet14d}).")
            }
        }

        // Body weight & DOTS
        if (hasBodyWeightData && bodyWeightDeltaKg != null) {
            val sign = if (bodyWeightDeltaKg > 0) "+" else ""
            val rateStr = bwWeeklyRateKg?.let { " (${if (it > 0) "+" else ""}${formatWeightValue(it)} kg/wk)" } ?: ""
            val dotsStr = dotsScore?.let {
                val dDelta = dotsDelta?.let { dd -> " (${if (dd > 0) "+" else ""}${formatWeightValue(dd)})" } ?: ""
                " • DOTS ${formatWeightValue(it)}$dDelta"
            } ?: ""
            highlights.add("Body weight moved $sign${formatWeightValue(bodyWeightDeltaKg)} kg over 14 days$rateStr$dotsStr.")
        }

        // Program adherence
        if (programName != null && programAdherencePct != null) {
            highlights.add("$programName adherence: $programAdherencePct% of scheduled 14-day sessions completed.")
        }

        // Recommendations
        val underTrained = muscleItems
            .filter { it.category != "Core" && it.workingSets14d < it.target14dSets * 0.5 }
            .sortedBy { it.workingSets14d.toDouble() / it.target14dSets.coerceAtLeast(1) }
        if (underTrained.isNotEmpty()) {
            val names = underTrained.take(2).joinToString(" and ") { "${it.category} (${it.workingSets14d}/${it.target14dSets} sets)" }
            recommendations.add("Prioritize $names in the upcoming block to close 14-day volume deficits.")
        }

        if (pushToPullRatio != null && pushToPullRatio > 1.4) {
            recommendations.add("Push-to-pull ratio is ${formatWeightValue(pushToPullRatio)}:1; add horizontal/vertical pulling sets for shoulder balance.")
        } else if (pushToPullRatio != null && pushToPullRatio < 0.65) {
            recommendations.add("Pulling exceeds pressing (${formatWeightValue(pushToPullRatio)}:1); add pressing volume to match upper-back work.")
        }

        if (upperSets > 0 && lowerSets == 0) {
            recommendations.add("Zero lower-body working sets logged in 14 days; schedule a squat or hinge session.")
        }

        if (failedSetsCount >= 2) {
            recommendations.add("$failedSetsCount zero-rep failed sets logged in 14 days; trim top-set load by 2.5-5% to restore bar speed.")
        }

        val untouchedGoals = goalStatuses.filter { !it.achieved && !it.trainedIn14d }
        if (untouchedGoals.isNotEmpty()) {
            recommendations.add("Active goal lift${if (untouchedGoals.size > 1) "s" else ""} not trained in 14 days: ${untouchedGoals.joinToString(", ") { it.exerciseName }}.")
        }

        if (longestRestGapDays >= 5) {
            recommendations.add("Longest training gap was $longestRestGapDays days; tighter session spacing will improve weekly stimulus.")
        }

        if (volumeDeltaPct != null && volumeDeltaPct > 30.0) {
            recommendations.add("Volume jumped +${formatWeightValue(volumeDeltaPct)}% vs the prior 14 days; watch recovery before adding more sets.")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Volume, cadence, and strength trends are balanced; hold current split and progress loads by 1.25-2.5 kg on top-rep hits.")
        }

        return highlights.take(4) to recommendations.take(4)
    }
}
