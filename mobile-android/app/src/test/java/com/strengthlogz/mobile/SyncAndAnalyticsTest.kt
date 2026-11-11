package com.strengthlogz.mobile

import com.google.gson.Gson
import com.strengthlogz.mobile.model.AppData
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutProgram
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.util.BigLift
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.StrengthAnalytics
import com.strengthlogz.mobile.util.SyncMerge
import com.strengthlogz.mobile.ui.buildSessionPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAndAnalyticsTest {

    // ---------- SyncMerge ----------

    @Test
    fun manualBodyWeightBeatsAutomaticOnSameDate() {
        val local = listOf(BodyWeightEntry("05-JAN-2026", 80.0, "manual", 1L))
        val remote = listOf(BodyWeightEntry("05-JAN-2026", 81.0, "healthconnect", 999L))
        val merged = SyncMerge.mergeBodyWeights(local, remote, emptySet(), emptySet())
        assertEquals(1, merged.size)
        assertEquals(80.0, merged[0].weightKg, 0.0)
    }

    @Test
    fun newerBodyWeightEditWinsWhenSameSource() {
        val local = listOf(BodyWeightEntry("05-JAN-2026", 80.0, "manual", 10L))
        val remote = listOf(BodyWeightEntry("2026-01-05", 79.0, "manual", 20L))
        val merged = SyncMerge.mergeBodyWeights(local, remote, emptySet(), emptySet())
        assertEquals(79.0, merged[0].weightKg, 0.0)
    }

    @Test
    fun locallyDeletedKeyIsDroppedAndRemotelyDeletedKeyIsNotResurrected() {
        val e1 = BodyWeightEntry("01-JAN-2026", 80.0)
        val e2 = BodyWeightEntry("02-JAN-2026", 81.0)
        val e3 = BodyWeightEntry("03-JAN-2026", 82.0)
        // e1 deleted locally (pending); e2 synced before but gone remotely (deleted elsewhere); e3 is new local
        val merged = SyncMerge.mergeBodyWeights(
            local = listOf(e2, e3),
            remote = listOf(e1),
            deletedLocally = setOf("2026-01-01"),
            lastSyncedKeys = setOf("2026-01-01", "2026-01-02")
        )
        assertEquals(listOf("2026-01-03"), merged.map { SyncMerge.bodyWeightKey(it) })
    }

    @Test
    fun remoteOnlyItemsFromOtherDevicesAreKept() {
        val remote = listOf(ExerciseGoal(id = "g1", exerciseName = "Bench Press", updatedAt = 5L))
        val merged = SyncMerge.mergeGoals(emptyList(), remote, emptySet(), emptySet())
        assertEquals(1, merged.size)
    }

    @Test
    fun newerGoalWinsAndProgramsMergeById() {
        val lg = ExerciseGoal(id = "g1", exerciseName = "Squat", targetE1rmKg = 150.0, updatedAt = 5L)
        val rg = ExerciseGoal(id = "g1", exerciseName = "Squat", targetE1rmKg = 160.0, updatedAt = 9L)
        assertEquals(160.0, SyncMerge.mergeGoals(listOf(lg), listOf(rg), emptySet(), emptySet())[0].targetE1rmKg, 0.0)

        val lp = WorkoutProgram(id = "p1", programName = "A", updatedAt = 50L)
        val rp = WorkoutProgram(id = "p1", programName = "B", updatedAt = 10L)
        val rp2 = WorkoutProgram(id = "p2", programName = "C", updatedAt = 1L)
        val merged = SyncMerge.mergePrograms(listOf(lp), listOf(rp, rp2), emptySet(), emptySet())
        assertEquals(setOf("p1", "p2"), merged.map { it.id }.toSet())
        assertEquals("A", merged.first { it.id == "p1" }.programName)
    }

    @Test
    fun legacyJsonWithoutUpdatedAtAndProgramsDeserializes() {
        val legacy = """{"workouts":[],"sets":[],"exercises":[],"bodyWeights":[{"date":"01-JAN-2026","weightKg":80.0,"source":"manual"}],"goals":[{"id":"g","exerciseName":"Bench Press"}]}"""
        val data = Gson().fromJson(legacy, AppData::class.java)
        assertEquals(0L, data.bodyWeights!![0].updatedAt)
        assertEquals(0L, data.goals!![0].updatedAt)
        assertEquals(true, data.goals!![0].enabled)
        assertEquals(3, data.goals!![0].targetSets)
        assertEquals(8, data.goals!![0].targetReps)
        assertNull(data.activeProgramId)
    }

    @Test
    fun desktopGoalWithoutEnabledAndBodyWeightWithoutSourceDeserializeWithDefaults() {
        val goalJson = """{"id":"goal-1","exerciseName":"Bench Press","goalType":"E1RM","targetE1rmKg":120}"""
        val g = Gson().fromJson(goalJson, ExerciseGoal::class.java)
        assertEquals(true, g.enabled)
        assertEquals(3, g.targetSets)
        assertEquals(8, g.targetReps)

        val bwJson = """{"date":"01-JAN-2026","weightKg":82.5}"""
        val bw = Gson().fromJson(bwJson, BodyWeightEntry::class.java)
        assertEquals("manual", bw.source)
        assertEquals(0L, bw.updatedAt)
    }

    // ---------- Analytics ----------

    @Test
    fun rollingAverageUsesTrailingSevenCalendarDays() {
        val entries = listOf(
            BodyWeightEntry("01-JAN-2026", 80.0),
            BodyWeightEntry("03-JAN-2026", 82.0),
            BodyWeightEntry("20-JAN-2026", 78.0)
        )
        val s = StrengthAnalytics.buildBodyWeightSeries(entries)
        assertEquals(3, s.size)
        assertEquals(80.0, s[0].avg7Kg, 0.001)
        assertEquals(81.0, s[1].avg7Kg, 0.001)
        assertEquals(78.0, s[2].avg7Kg, 0.001) // earlier readings are outside the 7-day window
    }

    @Test
    fun dotsMatchesKnownReferenceValues() {
        // 80 kg male, 500 kg total ~ 344.8 DOTS
        assertEquals(344.8, StrengthAnalytics.computeDots(500.0, 80.0, true), 0.5)
        // Females score higher for the same total/body weight
        assertTrue(StrengthAnalytics.computeDots(300.0, 60.0, false) > StrengthAnalytics.computeDots(300.0, 60.0, true))
        assertEquals(0.0, StrengthAnalytics.computeDots(0.0, 80.0, true), 0.0)
    }

    @Test
    fun liftClassificationIsStrict() {
        assertEquals(BigLift.SQUAT, StrengthAnalytics.classifyLift("Squat"))
        assertEquals(BigLift.BENCH, StrengthAnalytics.classifyLift("bench press"))
        assertEquals(BigLift.DEADLIFT, StrengthAnalytics.classifyLift("Sumo Deadlift"))
        assertNull(StrengthAnalytics.classifyLift("Romanian Deadlift"))
        assertNull(StrengthAnalytics.classifyLift("Front Squat"))
        assertNull(StrengthAnalytics.classifyLift("Incline Bench Press"))
    }

    @Test
    fun dotsTimelineNeedsAllThreeLiftsAndIgnoresFailedSets() {
        val bw = listOf(BodyWeightEntry("01-JAN-2026", 80.0))
        val workouts = listOf(Workout("w1", "05-JAN-2026"), Workout("w2", "10-JAN-2026"))
        val sets = listOf(
            WorkoutSet("1", "w1", "05-JAN-2026", "Squat", 1, 140.0, 1),
            WorkoutSet("2", "w1", "05-JAN-2026", "Bench Press", 1, 100.0, 1),
            WorkoutSet("3", "w2", "10-JAN-2026", "Deadlift", 1, 180.0, 1),
            WorkoutSet("4", "w2", "10-JAN-2026", "Deadlift", 2, 250.0, 0) // failed, ignored
        )
        val tl = StrengthAnalytics.buildDotsTimeline(workouts, sets, bw, true)
        assertEquals(1, tl.size) // only 10-JAN has all three
        assertEquals("2026-01-10", tl[0].isoDate)
        assertEquals(420.0, tl[0].totalKg, 0.001)
        assertEquals(StrengthAnalytics.computeDots(420.0, 80.0, true), tl[0].dots, 0.001)
    }

    @Test
    fun relativeStrengthAppliesToNonBodyweightLifts() {
        val entries = listOf(BodyWeightEntry("01-JAN-2026", 80.0))
        BodyWeightUtils.publish(entries)
        val sets = listOf(WorkoutSet("s1", "w1", "05-JAN-2026", "Bench Press", 1, 100.0, 1))
        val pts = buildSessionPoints(sets, emptyMap(), isBodyweight = false)
        assertEquals(1.25, pts[0].relativeStrength, 0.001)

        val rel = StrengthAnalytics.buildRelativeStrength(sets, emptyMap(), entries, false)
        assertEquals(1.25, rel[0].second, 0.001)
    }
}
