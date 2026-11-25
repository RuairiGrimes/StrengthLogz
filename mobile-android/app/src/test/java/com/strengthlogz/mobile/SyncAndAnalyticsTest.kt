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
import com.strengthlogz.mobile.util.SummaryEngine
import com.strengthlogz.mobile.util.SyncMerge
import com.strengthlogz.mobile.ui.buildSessionPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun bodyWeightEntryOnCarriesForwardAndReportsSourceDate() {
        assertEquals(null, BodyWeightUtils.entryOn("05-JAN-2026", emptyList()))

        val entries = listOf(
            BodyWeightEntry("10-JAN-2026", 81.0),
            BodyWeightEntry("02-JAN-2026", 80.0),
            BodyWeightEntry("20-JAN-2026", 79.5)
        )
        val exact = BodyWeightUtils.entryOn("10-JAN-2026", entries)!!
        assertEquals("10-JAN-2026", exact.date)
        assertEquals(81.0, exact.weightKg, 0.001)

        val carried = BodyWeightUtils.entryOn("15-JAN-2026", entries)!!
        assertEquals("10-JAN-2026", carried.date)
        assertEquals(81.0, carried.weightKg, 0.001)

        val earliestFallback = BodyWeightUtils.entryOn("01-JAN-2026", entries)!!
        assertEquals("02-JAN-2026", earliestFallback.date)
        assertEquals(80.0, earliestFallback.weightKg, 0.001)
    }

    @Test
    fun exerciseRelativeStrengthSeriesComputesIndependentRelPrsAndExcludesFailedSets() {
        val entries = listOf(
            BodyWeightEntry("01-JAN-2026", 80.0),
            BodyWeightEntry("01-FEB-2026", 75.0),
            BodyWeightEntry("15-FEB-2026", 75.0)
        )
        BodyWeightUtils.publish(entries)

        val sets = listOf(
            WorkoutSet("s1", "w1", "05-JAN-2026", "Bench Press", 1, 100.0, 1),
            WorkoutSet("s1_fail", "w1", "05-JAN-2026", "Bench Press", 2, 120.0, 0),
            WorkoutSet("s2", "w2", "05-FEB-2026", "Bench Press", 1, 100.0, 1),
            WorkoutSet("s3", "w3", "20-FEB-2026", "Bench Press", 1, 90.0, 1)
        )

        assertEquals(
            0,
            StrengthAnalytics.buildExerciseRelativeStrengthSeries(sets, emptyMap(), emptyList(), isBodyweightExercise = false).size
        )

        val series = StrengthAnalytics.buildExerciseRelativeStrengthSeries(
            sets = sets,
            workoutMap = emptyMap(),
            bodyWeights = entries,
            isBodyweightExercise = false
        )
        assertEquals(3, series.size)

        // Session 1: failed 120x0 excluded -> E1RM 100kg @ 80kg BW = 1.25x
        assertEquals(100.0, series[0].e1rmKg, 0.01)
        assertEquals(1.25, series[0].relativeStrength, 0.001)
        assertEquals(true, series[0].isRelativePr)
        assertEquals("01-JAN-2026", series[0].bodyWeightDate)
        assertEquals(true, series[0].hasExactOrPriorBw)

        // Session 2: same 100kg E1RM at lower 75kg BW -> independent Rel PR driven by BW change
        assertEquals(1.33, series[1].relativeStrength, 0.001)
        assertEquals(true, series[1].isRelativePr)
        assertEquals(true, series[1].isBodyweightShift)
        assertEquals(-5.0, series[1].bwDeltaVsPrev ?: 0.0, 0.01)

        // Session 3: 90kg @ 75kg = 1.20x, not a PR
        assertEquals(1.20, series[2].relativeStrength, 0.002)
        assertEquals(false, series[2].isRelativePr)

        // Bodyweight exercise uses total load (BW + added weight)
        val bwSets = listOf(WorkoutSet("b1", "wb1", "05-FEB-2026", "Pull Ups", 1, 15.0, 1))
        val bwSeries = StrengthAnalytics.buildExerciseRelativeStrengthSeries(
            sets = bwSets,
            workoutMap = emptyMap(),
            bodyWeights = entries,
            isBodyweightExercise = true
        )
        assertEquals(1, bwSeries.size)
        assertEquals(90.0, bwSeries[0].e1rmKg, 0.01)
        assertEquals(1.20, bwSeries[0].relativeStrength, 0.002)
        assertEquals("01-FEB-2026", bwSeries[0].bodyWeightDate)
    }

    // ---------- In-Engine 14-Day Summary ----------

    @Test
    fun fourteenDaySummaryComputesMetricsRepZonesPrsAndSavedEntry() {
        val exercises = listOf(
            com.strengthlogz.mobile.model.Exercise("Bench Press", "Chest", false, ""),
            com.strengthlogz.mobile.model.Exercise("Pull Ups", "Back", true, "")
        )
        val bw = listOf(
            BodyWeightEntry("01-NOV-2026", 80.0, "manual"),
            BodyWeightEntry("16-NOV-2026", 81.0, "manual")
        )
        val workouts = listOf(
            Workout("w-old", "15-OCT-2026", "Old Push"),
            Workout("w-p1", "01-NOV-2026", "Prior Push"),
            Workout("w-c1", "06-NOV-2026", "Current Push"),
            Workout("w-c2", "15-NOV-2026", "Current Upper")
        )
        val sets = listOf(
            WorkoutSet("s0", "w-p1", "01-NOV-2026", "Bench Press", 1, 90.0, 5), // E1RM 105.0, vol 450
            WorkoutSet("s1", "w-c1", "06-NOV-2026", "Bench Press", 1, 95.0, 5), // E1RM 111.0, vol 475
            WorkoutSet("s2", "w-c2", "15-NOV-2026", "Bench Press", 1, 100.0, 5), // E1RM 116.5, vol 500
            WorkoutSet("s3", "w-c2", "15-NOV-2026", "Bench Press", 2, 120.0, 0), // Failed -> excluded!
            WorkoutSet("s4", "w-c2", "15-NOV-2026", "Pull Ups", 1, 10.0, 8) // BW on 15-NOV is 80 + 10 = 90 -> E1RM 114.0
        )
        val summary = SummaryEngine.build14DaySummary(
            workouts = workouts,
            sets = sets,
            exercises = exercises,
            bodyWeights = bw,
            goals = emptyList(),
            referenceDateDdMonYyyy = "17-NOV-2026"
        )

        assertEquals("04-NOV-2026", summary.startDate)
        assertEquals("17-NOV-2026", summary.endDate)
        assertEquals(2, summary.workoutsCount)
        assertEquals(1, summary.priorWorkoutsCount)
        assertEquals(3, summary.workingSetsCount)
        assertEquals(1, summary.failedSetsCount)
        assertEquals(1055.0, summary.totalVolumeKg, 0.1) // 475 + 500 + 80
        assertEquals(450.0, summary.priorVolumeKg, 0.1)
        assertEquals(2, summary.heavySets) // two 5-rep Bench sets
        assertEquals(1, summary.strengthHypSets) // one 8-rep Pull Up set
        assertEquals(80.0, summary.startBodyWeightKg!!, 0.01)
        assertEquals(81.0, summary.endBodyWeightKg!!, 0.01)
        assertEquals(1.0, summary.bodyWeightDeltaKg!!, 0.01)
        assertEquals(1, summary.prsCount)
        assertTrue(summary.overallScore in 1..100)

        val bench = summary.lifts.first { it.exerciseName == "Bench Press" }
        assertTrue(bench.isAllTimePr)
        assertEquals(116.5, bench.bestE1rm14dKg, 0.5)
        assertEquals(105.0, bench.priorBestE1rmKg!!, 0.5)
        assertEquals(11.5, bench.e1rmDeltaKg!!, 0.5)
        assertEquals(5.5, bench.withinPeriodE1rmDeltaKg!!, 0.5)

        val pullUps = summary.lifts.first { it.exerciseName == "Pull Ups" }
        assertTrue(pullUps.isBodyweight)
        assertEquals(114.0, pullUps.bestE1rm14dKg, 0.5)

        val saved = summary.toSavedEntry(customTitle = "Block 1 Review", customNotes = "Bench PR")
        assertEquals("Block 1 Review", saved.title)
        assertEquals("Bench PR", saved.notes)
        assertEquals(summary.overallScore, saved.overallScore)
        assertEquals(2, saved.safeTopLifts.size)
        assertFalse(saved.safeHighlights.isEmpty())
    }
}
