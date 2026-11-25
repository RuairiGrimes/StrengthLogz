package com.strengthlogz.mobile.util

import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import kotlin.math.abs
import kotlin.math.roundToInt

/** The three powerlifting competition lifts used for SBD total / DOTS. */
enum class BigLift(val label: String) { SQUAT("Squat"), BENCH("Bench"), DEADLIFT("Deadlift") }

@androidx.compose.runtime.Immutable
data class BodyWeightPoint(
    val displayDate: String,
    val isoDate: String,
    val epochDay: Long,
    val weightKg: Double,
    val avg7Kg: Double,
    val source: String
)

@androidx.compose.runtime.Immutable
data class DotsPoint(
    val displayDate: String,
    val isoDate: String,
    val squat: Double,
    val bench: Double,
    val deadlift: Double,
    val totalKg: Double,
    val bodyWeightKg: Double,
    val dots: Double
)

@androidx.compose.runtime.Immutable
data class RelativeStrengthPoint(
    val workoutId: String,
    val displayDate: String,
    val isoDate: String,
    val epochDay: Long,
    val workoutTitle: String,
    val e1rmKg: Double,
    val topSetSummary: String,
    val bodyWeightKg: Double,
    val bodyWeightDate: String,
    val hasExactOrPriorBw: Boolean,
    val relativeStrength: Double,
    val deltaVsPrev: Double?,
    val bwDeltaVsPrev: Double?,
    val pctVsFirst: Double?,
    val isRelativePr: Boolean,
    val isBodyweightShift: Boolean
)

object StrengthAnalytics {
    // ---- DOTS ----
    private val MALE = doubleArrayOf(-307.75076, 24.0900756, -0.1918759221, 0.0007391293, -0.000001093)
    private val FEMALE = doubleArrayOf(-57.96288, 13.6175032, -0.1126655495, 0.0005158568, -0.0000010706)

    fun dotsCoefficient(bodyWeightKg: Double, isMale: Boolean): Double {
        val bw = bodyWeightKg.coerceIn(40.0, 210.0)
        val c = if (isMale) MALE else FEMALE
        val denom = c[0] + c[1] * bw + c[2] * bw * bw + c[3] * bw * bw * bw + c[4] * bw * bw * bw * bw
        return if (denom > 0.0) 500.0 / denom else 0.0
    }

    fun computeDots(totalKg: Double, bodyWeightKg: Double, isMale: Boolean): Double =
        if (totalKg <= 0.0 || bodyWeightKg <= 0.0) 0.0 else totalKg * dotsCoefficient(bodyWeightKg, isMale)

    // ---- Body weight series ----
    fun epochDay(date: String?): Long {
        val iso = DateUtils.toSortableIso(date)
        return try {
            java.time.LocalDate.parse(iso).toEpochDay()
        } catch (_: Exception) {
            0L
        }
    }

    /** Chronological readings (one per date) with a trailing 7-calendar-day moving average. */
    fun buildBodyWeightSeries(entries: List<BodyWeightEntry>): List<BodyWeightPoint> {
        val sorted = entries
            .filter { it.weightKg > 0.0 }
            .associateBy { DateUtils.toSortableIso(it.date) }
            .values
            .sortedBy { DateUtils.toSortableIso(it.date) }
        val days = sorted.map { epochDay(it.date) }
        return sorted.mapIndexed { i, e ->
            var sum = 0.0
            var n = 0
            var j = i
            while (j >= 0 && days[i] - days[j] <= 6) {
                sum += sorted[j].weightKg
                n++
                j--
            }
            BodyWeightPoint(
                displayDate = DateUtils.toDdMonYyyy(e.date),
                isoDate = DateUtils.toSortableIso(e.date),
                epochDay = days[i],
                weightKg = e.weightKg,
                avg7Kg = if (n > 0) sum / n else e.weightKg,
                source = e.source
            )
        }
    }

    // ---- Lift classification ----
    private val SQUATS = setOf("squat", "back squat", "low bar squat", "high bar squat", "barbell squat", "barbell back squat")
    private val BENCHES = setOf("bench press", "flat bench press", "barbell bench press", "flat barbell bench press")
    private val DEADLIFTS = setOf("deadlift", "conventional deadlift", "sumo deadlift", "barbell deadlift")

    fun classifyLift(exerciseName: String?): BigLift? {
        val n = exerciseName?.trim()?.lowercase() ?: return null
        return when (n) {
            in SQUATS -> BigLift.SQUAT
            in BENCHES -> BigLift.BENCH
            in DEADLIFTS -> BigLift.DEADLIFT
            else -> null
        }
    }

    fun epley(weight: Double, reps: Int): Double = when {
        reps <= 0 || weight <= 0.0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1.0 + reps / 30.0)
    }

    /**
     * DOTS / SBD-total timeline. At each workout date that contains one of the big three, the lifter's
     * best E1RM per lift over the trailing [windowDays] days is paired with the body weight on that date.
     * Points are emitted only once all three lifts have a value inside the window.
     */
    fun buildDotsTimeline(
        workouts: List<Workout>,
        sets: List<WorkoutSet>,
        bodyWeights: List<BodyWeightEntry>,
        isMale: Boolean,
        windowDays: Int = 90
    ): List<DotsPoint> {
        if (bodyWeights.isEmpty()) return emptyList()
        val wMap = workouts.associateBy { it.id }
        // (epochDay, lift) -> best e1rm that day
        class Obs(val day: Long, val iso: String, val lift: BigLift, val e1rm: Double)
        val obs = ArrayList<Obs>()
        for (s in sets) {
            val lift = classifyLift(s.exercise) ?: continue
            val reps = s.reps ?: 0
            val w = s.weight ?: 0.0
            val e = epley(w, reps)
            if (e <= 0.0) continue
            val date = wMap[s.workoutId]?.date?.ifBlank { s.date } ?: s.date
            obs.add(Obs(epochDay(date), DateUtils.toSortableIso(date), lift, e))
        }
        if (obs.isEmpty()) return emptyList()
        val dates = obs.map { it.iso to it.day }.distinctBy { it.first }.sortedBy { it.first }
        val out = ArrayList<DotsPoint>()
        for ((iso, day) in dates) {
            val inWindow = obs.filter { it.day <= day && day - it.day <= windowDays }
            val best = BigLift.entries.associateWith { l -> inWindow.filter { it.lift == l }.maxOfOrNull { it.e1rm } ?: 0.0 }
            if (best.values.any { it <= 0.0 }) continue
            val bw = BodyWeightUtils.weightOn(iso, bodyWeights)
            val total = best.values.sum()
            out.add(
                DotsPoint(
                    displayDate = DateUtils.toDdMonYyyy(iso),
                    isoDate = iso,
                    squat = best.getValue(BigLift.SQUAT),
                    bench = best.getValue(BigLift.BENCH),
                    deadlift = best.getValue(BigLift.DEADLIFT),
                    totalKg = total,
                    bodyWeightKg = bw,
                    dots = computeDots(total, bw, isMale)
                )
            )
        }
        return out
    }

    /** Relative strength (E1RM / body weight on session date) timeline for any exercise. */
    fun buildRelativeStrength(
        sets: List<WorkoutSet>,
        workoutMap: Map<String, Workout>,
        bodyWeights: List<BodyWeightEntry>,
        isBodyweightExercise: Boolean
    ): List<Triple<String, Double, Double>> { // (displayDate, relative, e1rmUsed)
        if (bodyWeights.isEmpty()) return emptyList()
        return sets.groupBy { it.workoutId.ifBlank { it.date } }
            .mapNotNull { (wId, ws) ->
                val raw = workoutMap[wId]?.date?.ifBlank { ws.first().date } ?: ws.first().date
                val bw = BodyWeightUtils.weightOn(raw, bodyWeights)
                val best = ws.maxOfOrNull { s ->
                    val load = BodyWeightUtils.totalLoad(isBodyweightExercise, s.weight, bw)
                    epley(load, s.reps ?: 0)
                } ?: 0.0
                if (best <= 0.0 || bw <= 0.0) null
                else Triple(DateUtils.toSortableIso(raw), best / bw, best)
            }
            .sortedBy { it.first }
            .map { Triple(DateUtils.toDdMonYyyy(it.first), it.second, it.third) }
    }

    /**
     * Rich, dedicated relative-strength datapoint series for an exercise.
     * Excludes failed sets (reps <= 0), tracks carry-forward body weight source dates,
     * and computes independent relative-strength PR flags and session-to-session deltas.
     */
    fun buildExerciseRelativeStrengthSeries(
        sets: List<WorkoutSet>,
        workoutMap: Map<String, Workout>,
        bodyWeights: List<BodyWeightEntry>,
        isBodyweightExercise: Boolean
    ): List<RelativeStrengthPoint> {
        if (sets.isEmpty()) return emptyList()
        if (bodyWeights.isEmpty() && !isBodyweightExercise) return emptyList()

        fun roundHalf(v: Double): Double = (v * 2.0).roundToInt() / 2.0
        fun roundTenth(v: Double): Double = (v * 10.0).roundToInt() / 10.0
        fun roundHundredth(v: Double): Double = (v * 100.0).roundToInt() / 100.0

        val rawPoints = sets.groupBy { it.workoutId.ifBlank { it.date } }
            .mapNotNull { (wId, rawSets) ->
                val sortedSets = rawSets.sortedBy { it.setNo }
                val workout = workoutMap[wId]
                val rawDate = workout?.date?.ifBlank { sortedSets.first().date } ?: sortedSets.first().date
                val isoDate = DateUtils.toSortableIso(rawDate)
                if (isoDate.isBlank()) return@mapNotNull null
                val displayDate = DateUtils.toDdMonYyyy(rawDate)
                val title = workout?.title ?: ""

                val bwEntry = BodyWeightUtils.entryOn(rawDate, bodyWeights)
                val bwKg = bwEntry?.weightKg ?: if (isBodyweightExercise) BodyWeightUtils.FALLBACK_KG else 0.0
                if (bwKg <= 0.0) return@mapNotNull null
                val bwDisplayDate = bwEntry?.let { DateUtils.toDdMonYyyy(it.date) } ?: "Default"
                val hasExactOrPriorBw = bwEntry != null && DateUtils.toSortableIso(bwEntry.date) <= isoDate

                var bestE1rm = 0.0
                var bestSet: WorkoutSet? = null
                for (s in sortedSets) {
                    val r = s.reps ?: 0
                    if (r <= 0) continue
                    val w = s.weight ?: 0.0
                    val load = BodyWeightUtils.totalLoad(isBodyweightExercise, w, bwKg)
                    if (load <= 0.0) continue
                    val rawE = if (r == 1) load else roundHalf(epley(load, r))
                    if (rawE > bestE1rm) {
                        bestE1rm = rawE
                        bestSet = s
                    }
                }

                if (bestE1rm <= 0.0 || bestSet == null) return@mapNotNull null

                val w = bestSet.weight ?: 0.0
                val r = bestSet.reps ?: 0
                val topSetSummary = if (isBodyweightExercise) {
                    if (w <= 0.0) "BW × $r" else "BW+${SetUtils.formatWeightValue(w)}kg × $r"
                } else {
                    "${SetUtils.formatWeightValue(w)}kg × $r"
                }

                RelativeStrengthPoint(
                    workoutId = wId,
                    displayDate = displayDate,
                    isoDate = isoDate,
                    epochDay = epochDay(isoDate),
                    workoutTitle = title,
                    e1rmKg = bestE1rm,
                    topSetSummary = topSetSummary,
                    bodyWeightKg = roundTenth(bwKg),
                    bodyWeightDate = bwDisplayDate,
                    hasExactOrPriorBw = hasExactOrPriorBw,
                    relativeStrength = roundHundredth(bestE1rm / bwKg),
                    deltaVsPrev = null,
                    bwDeltaVsPrev = null,
                    pctVsFirst = null,
                    isRelativePr = false,
                    isBodyweightShift = false
                )
            }
            .sortedWith(compareBy<RelativeStrengthPoint> { it.isoDate }.thenBy { it.workoutId })

        if (rawPoints.isEmpty()) return emptyList()

        val firstRel = rawPoints.first().relativeStrength
        var runningBestRel = 0.0
        var prev: RelativeStrengthPoint? = null

        return rawPoints.map { pt ->
            val isPr = pt.relativeStrength > runningBestRel + 0.004 && pt.relativeStrength > 0.0
            if (isPr) runningBestRel = pt.relativeStrength

            val delta = prev?.let { roundHundredth(pt.relativeStrength - it.relativeStrength) }
            val bwDelta = prev?.let { roundTenth(pt.bodyWeightKg - it.bodyWeightKg) }
            val pctVsFirst = if (prev != null && firstRel > 0.0) {
                roundTenth(((pt.relativeStrength - firstRel) / firstRel) * 100.0)
            } else null
            val bwShift = abs(bwDelta ?: 0.0) >= 0.2

            val enriched = pt.copy(
                deltaVsPrev = delta,
                bwDeltaVsPrev = bwDelta,
                pctVsFirst = pctVsFirst,
                isRelativePr = isPr,
                isBodyweightShift = bwShift
            )
            prev = enriched
            enriched
        }
    }
}
