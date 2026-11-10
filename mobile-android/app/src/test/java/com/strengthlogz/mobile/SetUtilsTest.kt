package com.strengthlogz.mobile

import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.util.SetUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class SetUtilsTest {

    @Test
    fun testIdenticalSetsCondenseIntoSingleGroup() {
        val sets = listOf(
            WorkoutSet(id = "1", workoutId = "w1", date = "16-OCT-2026", exercise = "Dumbbell Curl", setNo = 1, weight = 10.0, reps = 10),
            WorkoutSet(id = "2", workoutId = "w1", date = "16-OCT-2026", exercise = "Dumbbell Curl", setNo = 2, weight = 10.0, reps = 10),
            WorkoutSet(id = "3", workoutId = "w1", date = "16-OCT-2026", exercise = "Dumbbell Curl", setNo = 3, weight = 10.0, reps = 10)
        )
        val result = SetUtils.formatSetsSummary(sets)
        assertEquals("3 x 10 @ 10kg", result)
    }

    @Test
    fun testVaryingSetsFormatCorrectly() {
        val sets = listOf(
            WorkoutSet(id = "1", workoutId = "w1", date = "16-OCT-2026", exercise = "Dumbbell Curl", setNo = 1, weight = 10.0, reps = 10),
            WorkoutSet(id = "2", workoutId = "w1", date = "16-OCT-2026", exercise = "Dumbbell Curl", setNo = 2, weight = 10.0, reps = 9),
            WorkoutSet(id = "3", workoutId = "w1", date = "16-OCT-2026", exercise = "Dumbbell Curl", setNo = 3, weight = 10.0, reps = 9)
        )
        val result = SetUtils.formatSetsSummary(sets)
        assertEquals("1 x 10 @ 10kg, 2 x 9 @ 10kg", result)
    }

    @Test
    fun testBodyweightAndAddedWeightFormat() {
        val bwSets = listOf(
            WorkoutSet(id = "1", workoutId = "w1", date = "16-OCT-2026", exercise = "Pull Ups", setNo = 1, weight = 0.0, reps = 10),
            WorkoutSet(id = "2", workoutId = "w1", date = "16-OCT-2026", exercise = "Pull Ups", setNo = 2, weight = 0.0, reps = 10),
            WorkoutSet(id = "3", workoutId = "w1", date = "16-OCT-2026", exercise = "Pull Ups", setNo = 3, weight = 0.0, reps = 10)
        )
        assertEquals("3 x 10 @ BW", SetUtils.formatSetsSummary(bwSets, isBodyweight = true))

        val addedSets = listOf(
            WorkoutSet(id = "4", workoutId = "w1", date = "16-OCT-2026", exercise = "Dips", setNo = 1, weight = 10.0, reps = 8),
            WorkoutSet(id = "5", workoutId = "w1", date = "16-OCT-2026", exercise = "Dips", setNo = 2, weight = 10.0, reps = 8)
        )
        assertEquals("2 x 8 @ BW+10kg", SetUtils.formatSetsSummary(addedSets, isBodyweight = true))
    }

    @Test
    fun testFailedAttemptZeroRepFormat() {
        val sets = listOf(
            WorkoutSet(id = "1", workoutId = "w1", date = "16-OCT-2026", exercise = "Bench Press", setNo = 1, weight = 115.0, reps = 0)
        )
        assertEquals("1 x 0 @ 115kg", SetUtils.formatSetsSummary(sets))
    }
}

