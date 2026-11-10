package com.strengthlogz.mobile

import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.buildSessionPoints
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.SetUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyWeightUtilsTest {
    private val entries = listOf(
        BodyWeightEntry("01-JAN-2026", 80.0),
        BodyWeightEntry("10-JAN-2026", 78.0)
    )

    @Test
    fun lbsToKgConverts() {
        assertEquals(93.7, BodyWeightUtils.lbsToKg(206.6), 0.05)
    }

    @Test
    fun weightOnCarriesForward() {
        assertEquals(80.0, BodyWeightUtils.weightOn("05-JAN-2026", entries), 0.0)
        assertEquals(78.0, BodyWeightUtils.weightOn("2026-01-10", entries), 0.0)
        assertEquals(78.0, BodyWeightUtils.weightOn("01-MAR-2026", entries), 0.0)
    }

    @Test
    fun weightOnBeforeFirstUsesEarliest() {
        assertEquals(80.0, BodyWeightUtils.weightOn("01-DEC-2025", entries), 0.0)
    }

    @Test
    fun totalLoadAndRelativeStrength() {
        assertEquals(90.0, BodyWeightUtils.totalLoad(true, 10.0, 80.0), 0.0)
        assertEquals(10.0, BodyWeightUtils.totalLoad(false, 10.0, 80.0), 0.0)
        assertEquals(1.5, BodyWeightUtils.relativeStrength(120.0, 80.0), 0.0)
    }

    @Test
    fun bodyweightSessionUsesTotalLoadAndIgnoresFailedSets() {
        BodyWeightUtils.publish(entries)
        val sets = listOf(
            WorkoutSet("s1", "w1", "05-JAN-2026", "Pull Ups", 1, 10.0, 5),
            WorkoutSet("s2", "w1", "05-JAN-2026", "Pull Ups", 2, 40.0, 0)
        )
        val pts = buildSessionPoints(sets, emptyMap(), isBodyweight = true)
        assertEquals(1, pts.size)
        // total = 80 + 10 = 90, 5 reps => 90 * (1 + 5/30) = 105
        assertEquals(105.0, pts[0].e1rm, 0.01)
        assertTrue(pts[0].relativeStrength > 1.0)
    }

    @Test
    fun pureBodyweightSetNowHasE1rm() {
        BodyWeightUtils.publish(entries)
        val sets = listOf(WorkoutSet("s1", "w1", "05-JAN-2026", "Pull Ups", 1, 0.0, 6))
        val pts = buildSessionPoints(sets, emptyMap(), isBodyweight = true)
        assertTrue(pts[0].e1rm > 80.0)
    }

    @Test
    fun incrementAppliesToValues() {
        assertEquals("22.5", SetUtils.addToWeight("20", 2.5))
        assertEquals("1.25", SetUtils.addToWeight("", 1.25))
        assertEquals("9", SetUtils.addToReps("8", 1))
    }
}

