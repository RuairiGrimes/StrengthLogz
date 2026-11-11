package com.strengthlogz.mobile.util

import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet

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
}
