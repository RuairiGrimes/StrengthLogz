package com.strengthlogz.mobile

import com.strengthlogz.mobile.model.*
import com.strengthlogz.mobile.ui.buildSessionPoints
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.GoalEngine
import com.strengthlogz.mobile.util.GoalStatus
import com.strengthlogz.mobile.util.SummaryEngine
import com.strengthlogz.mobile.util.SyncMerge
import org.junit.Assert.*
import org.junit.Test

class TrainingModeTest {

    @Test
    fun verifyTrainingModeParsingAndPolicy() {
        assertEquals(TrainingMode.MAINGAIN, TrainingMode.fromString(null))
        assertEquals(TrainingMode.MAINGAIN, TrainingMode.fromString("unknown"))
        assertEquals(TrainingMode.BULK, TrainingMode.fromString("bulk"))
        assertEquals(TrainingMode.CUT, TrainingMode.fromString(" CUT "))

        val cutTargets = TrainingModePolicy.weeklyMuscleTargets(TrainingMode.CUT)
        val bulkTargets = TrainingModePolicy.weeklyMuscleTargets(TrainingMode.BULK)
        assertTrue(cutTargets["Chest"]!! < bulkTargets["Chest"]!!)
        assertEquals(7, cutTargets["Chest"])
        assertEquals(12, bulkTargets["Chest"])

        // Body weight pace classification adapts cleanly to Cut vs Bulk
        val cutPace = TrainingModePolicy.classifyBodyWeightPace(TrainingMode.CUT, -0.5)
        assertTrue(cutPace.isOnTarget)
        assertEquals("On-pace sustainable cut", cutPace.status)

        val bulkPace = TrainingModePolicy.classifyBodyWeightPace(TrainingMode.BULK, 0.3)
        assertTrue(bulkPace.isOnTarget)
        assertEquals("On-pace lean bulk", bulkPace.status)

        // <5% E1RM dip in Cut is treated as expected cut dip, never harsh failure
        assertTrue(TrainingModePolicy.isExpectedCutDip(TrainingMode.CUT, 96.0, 100.0))
        assertFalse(TrainingModePolicy.isExpectedCutDip(TrainingMode.CUT, 90.0, 100.0))
        assertFalse(TrainingModePolicy.isExpectedCutDip(TrainingMode.BULK, 97.0, 100.0))
    }

    @Test
    fun verifySyncMergeResolveTrainingMode() {
        assertEquals("CUT" to 200L, SyncMerge.resolveTrainingMode("MAINGAIN", 100L, "CUT", 200L))
        assertEquals("BULK" to 300L, SyncMerge.resolveTrainingMode("BULK", 300L, "CUT", 200L))
        assertEquals("CUT" to 100L, SyncMerge.resolveTrainingMode("CUT", 100L, "BULK", 100L))
        assertEquals("BULK" to 50L, SyncMerge.resolveTrainingMode("BULK", 50L, "INVALID", 999L))
        assertEquals("MAINGAIN" to 0L, SyncMerge.resolveTrainingMode(null, 0L, null, 0L))
    }

    @Test
    fun verifyCutGoalEvaluationRetainsStrengthAndScalesRelativeBwGoals() {
        val workouts = listOf(
            Workout("w1", "01-OCT-2026", "Push 1"),
            Workout("w2", "08-OCT-2026", "Push 2"),
            Workout("w3", "15-OCT-2026", "Push 3"),
            Workout("w4", "22-OCT-2026", "Push 4")
        )
        val bodyWeights = listOf(
            BodyWeightEntry("01-OCT-2026", 90.0, "manual", 10L),
            BodyWeightEntry("22-OCT-2026", 82.0, "manual", 20L)
        )
        val benchSets = listOf(
            WorkoutSet("s1", "w1", "01-OCT-2026", "Bench Press", 1, 100.0, 5), // E1RM 116.5 (1.29x BW)
            WorkoutSet("s2", "w2", "08-OCT-2026", "Bench Press", 1, 97.5, 5),  // E1RM 113.5 (~97% retention)
            WorkoutSet("s3", "w3", "15-OCT-2026", "Bench Press", 1, 97.5, 5),
            WorkoutSet("s4", "w4", "22-OCT-2026", "Bench Press", 1, 97.5, 5)   // 113.5 / 82 = 1.38x BW
        )
        val catalog = listOf(Exercise("Bench Press", "Chest", false))

        val e1rmGoal = ExerciseGoal(
            id = "g-bench",
            exerciseName = "Bench Press",
            goalType = "E1RM",
            targetE1rmKg = 125.0,
            baselineE1rmKg = 116.5,
            createdDate = "01-OCT-2026"
        )

        val cutEval = GoalEngine.evaluateGoal(
            goal = e1rmGoal,
            workouts = workouts,
            exerciseSets = benchSets,
            allSets = benchSets,
            exercisesCatalog = catalog,
            bodyWeights = bodyWeights,
            trainingMode = TrainingMode.CUT
        )
        assertEquals(GoalStatus.RETAINING, cutEval.status)
        assertTrue(cutEval.retentionPct >= 95.0)

        // RELATIVE_BW goal scales target down from 121.5kg (@90kg) to 110.7kg (@82kg) and becomes ACHIEVED!
        val relGoal = ExerciseGoal(
            id = "g-bench-rel",
            exerciseName = "Bench Press",
            goalType = "RELATIVE_BW",
            targetRelativeBw = 1.35,
            baselineE1rmKg = 116.5,
            createdDate = "01-OCT-2026"
        )
        val relEval = GoalEngine.evaluateGoal(
            goal = relGoal,
            workouts = workouts,
            exerciseSets = benchSets,
            allSets = benchSets,
            exercisesCatalog = catalog,
            bodyWeights = bodyWeights,
            trainingMode = TrainingMode.CUT
        )
        assertEquals(GoalStatus.ACHIEVED, relEval.status)
        assertTrue(relEval.isAchieved)
        assertEquals(110.5, relEval.effectiveTargetE1rm, 0.5)
    }

    @Test
    fun verifySummaryEngineAndSessionPointsRelativePrsExcludeFailedSets() {
        val bws = listOf(
            BodyWeightEntry("2026-11-01", 88.0, "manual", 10L),
            BodyWeightEntry("2026-11-08", 84.0, "manual", 15L),
            BodyWeightEntry("2026-11-15", 83.5, "manual", 20L)
        )
        BodyWeightUtils.publish(bws)

        val workouts = listOf(
            Workout("w1", "2026-11-01", "Prior Push"),
            Workout("w2", "2026-11-08", "Deload Push"),
            Workout("w3", "2026-11-15", "Cut Push")
        )
        val sets = listOf(
            WorkoutSet("s1", "w1", "2026-11-01", "Bench Press", 1, 100.0, 5), // 116.5kg / 88.0 = 1.32x (initial PR)
            WorkoutSet("s2", "w2", "2026-11-08", "Bench Press", 1, 92.5, 5),  // 108.0kg / 84.0 = 1.29x (not Rel PR)
            WorkoutSet("s3", "w3", "2026-11-15", "Bench Press", 1, 120.0, 0), // FAILED 0-rep set!
            WorkoutSet("s4", "w3", "2026-11-15", "Bench Press", 2, 97.5, 5)   // 114.0kg / 83.5 = 1.37x -> REL PR!
        )
        val catalog = listOf(Exercise("Bench Press", "Chest", false))

        val points = buildSessionPoints(sets, workouts.associateBy { it.id }, false)
        assertEquals(3, points.size)
        assertTrue(points[0].isRelPr)
        assertFalse(points[1].isRelPr)
        assertTrue(points[2].isRelPr)
        assertEquals(113.5, points[2].e1rm, 0.5)

        val cutSummary = SummaryEngine.build14DaySummary(
            workouts = workouts,
            sets = sets,
            exercises = catalog,
            bodyWeights = bws,
            goals = emptyList(),
            activeProgram = null,
            isMale = true,
            referenceDateDdMonYyyy = "17-NOV-2026",
            trainingMode = TrainingMode.CUT
        )
        assertEquals(TrainingMode.CUT, cutSummary.trainingMode)
        assertEquals(0, cutSummary.regressingCount)
        assertEquals(1, cutSummary.progressingCount) // Relative PR promotes to progressing!
        assertEquals(1, cutSummary.relPrsCount)
        assertTrue(cutSummary.lifts.first().isAllTimeRelPr)
        assertTrue((cutSummary.lifts.first().retentionPct ?: 0.0) >= 95.0)
        assertEquals("On-pace sustainable cut", cutSummary.bwPaceAssessment)

        // When body weight holds steady and E1RM dips <5%, Cut mode classifies as retained (not regressing)
        val steadyBw = listOf(BodyWeightEntry("2026-11-01", 84.0, "manual", 10L))
        val steadyRetention = SummaryEngine.build14DaySummary(
            workouts = listOf(Workout("sw1", "2026-11-01", "P1"), Workout("sw2", "2026-11-15", "P2")),
            sets = listOf(
                WorkoutSet("ss1", "sw1", "2026-11-01", "Bench Press", 1, 100.0, 5),
                WorkoutSet("ss2", "sw2", "2026-11-15", "Bench Press", 1, 97.5, 5)
            ),
            exercises = catalog,
            bodyWeights = steadyBw,
            goals = emptyList(),
            activeProgram = null,
            isMale = true,
            referenceDateDdMonYyyy = "17-NOV-2026",
            trainingMode = TrainingMode.CUT
        )
        assertEquals(0, steadyRetention.regressingCount)
        assertEquals(1, steadyRetention.retainedCount)

        val bulkSummary = SummaryEngine.build14DaySummary(
            workouts = workouts,
            sets = sets,
            exercises = catalog,
            bodyWeights = bws,
            goals = emptyList(),
            activeProgram = null,
            isMale = true,
            referenceDateDdMonYyyy = "17-NOV-2026",
            trainingMode = TrainingMode.BULK
        )
        assertEquals(TrainingMode.BULK, bulkSummary.trainingMode)
        assertEquals(1, bulkSummary.regressingCount)
    }
}
