package com.strengthlogz.mobile.util

import com.strengthlogz.mobile.model.WorkoutSet

object SetUtils {

    fun formatWeightValue(w: Double?): String {
        if (w == null) return ""
        return if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()
    }

    /** Adds [inc] kg to a weight text field value (blank/invalid counts as 0). */
    fun addToWeight(current: String, inc: Double): String {
        val curr = current.trim().toDoubleOrNull() ?: 0.0
        return formatWeightValue(Math.round((curr + inc) * 100.0) / 100.0)
    }

    /** Adds [inc] reps to a reps text field value (blank/invalid counts as 0). */
    fun addToReps(current: String, inc: Int): String {
        val curr = current.trim().toIntOrNull() ?: 0
        return (curr + inc).toString()
    }

    /**
     * Formats a list of workout sets into standardized "sets x reps @ weight" notation:
     * e.g. "3 x 10 @ 10kg"
     * e.g. "1 x 10 @ 10kg, 2 x 9 @ 10kg"
     * e.g. "3 x 10 @ BW" or "2 x 8 @ BW+10kg"
     */
    fun formatSetsSummary(sets: List<WorkoutSet>, isBodyweight: Boolean = false): String {
        if (sets.isEmpty()) return ""

        data class SetGroup(val weight: Double?, val reps: Int?, var count: Int)
        val groups = mutableListOf<SetGroup>()

        for (s in sets) {
            val last = groups.lastOrNull()
            if (last != null && last.weight == s.weight && last.reps == s.reps) {
                last.count++
            } else {
                groups.add(SetGroup(weight = s.weight, reps = s.reps, count = 1))
            }
        }

        return groups.joinToString(", ") { g ->
            val count = g.count
            val repsStr = g.reps?.toString() ?: "?"
            val w = g.weight

            if (w == null) {
                if (isBodyweight) {
                    "$count x $repsStr @ BW"
                } else {
                    "$count x $repsStr"
                }
            } else if (isBodyweight) {
                if (w == 0.0) {
                    "$count x $repsStr @ BW"
                } else {
                    "$count x $repsStr @ BW+${formatWeightValue(w)}kg"
                }
            } else {
                "$count x $repsStr @ ${formatWeightValue(w)}kg"
            }
        }
    }
}

