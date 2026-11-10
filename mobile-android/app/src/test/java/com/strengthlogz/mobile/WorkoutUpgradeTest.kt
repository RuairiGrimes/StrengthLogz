package com.strengthlogz.mobile

import com.google.gson.Gson
import com.strengthlogz.mobile.model.ActiveWorkoutDraft
import com.strengthlogz.mobile.model.DraftExerciseLog
import com.strengthlogz.mobile.model.DraftSetInput
import com.strengthlogz.mobile.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutUpgradeTest {

    @Test
    fun testDateUtilsDayOfWeek() {
        // 20-OCT-2026 is a Tuesday
        assertEquals("Tuesday", DateUtils.getDayOfWeek("20-OCT-2026"))
        assertEquals("Tuesday", DateUtils.getDayOfWeek("2026-10-20"))

        // 19-OCT-2026 is a Monday
        assertEquals("Monday", DateUtils.getDayOfWeek("19-OCT-2026"))

        // Format with day
        assertEquals("Tuesday • 20-OCT-2026", DateUtils.formatDisplayWithDay("20-OCT-2026"))
        assertEquals("Monday • 19-OCT-2026", DateUtils.formatDisplayWithDay("19-OCT-2026"))
        assertEquals("Mon • 19-OCT-2026", DateUtils.formatDisplayWithDay("19-OCT-2026", shortDay = true))

        // Blank or null handling
        assertEquals("", DateUtils.getDayOfWeek(""))
        assertEquals("", DateUtils.formatDisplayWithDay(""))
    }

    @Test
    fun testActiveWorkoutDraftSerialization() {
        val draft = ActiveWorkoutDraft(
            date = "20-OCT-2026",
            title = "Push Day",
            notes = "Focus on pause reps",
            exercises = listOf(
                DraftExerciseLog(
                    exerciseName = "Bench Press",
                    category = "Chest",
                    sets = listOf(
                        DraftSetInput(weight = "100", reps = "8", notes = "RPE 8", isCompleted = true),
                        DraftSetInput(weight = "100", reps = "8", notes = "", isCompleted = false)
                    )
                ),
                DraftExerciseLog(
                    exerciseName = "Incline Dumbbell Press",
                    category = "Chest",
                    sets = listOf(
                        DraftSetInput(weight = "32.5", reps = "10", isCompleted = true)
                    )
                )
            )
        )

        val gson = Gson()
        val json = gson.toJson(draft)
        assertNotNull(json)
        assertTrue(json.contains("Bench Press"))
        assertTrue(json.contains("Incline Dumbbell Press"))
        assertTrue(json.contains("RPE 8"))

        val restored = gson.fromJson(json, ActiveWorkoutDraft::class.java)
        assertEquals(draft.date, restored.date)
        assertEquals(draft.title, restored.title)
        assertEquals(draft.notes, restored.notes)
        assertEquals(2, restored.exercises.size)
        assertEquals("Bench Press", restored.exercises[0].exerciseName)
        assertEquals("100", restored.exercises[0].sets[0].weight)
        assertEquals("8", restored.exercises[0].sets[0].reps)
        assertEquals(true, restored.exercises[0].sets[0].isCompleted)
        assertEquals("RPE 8", restored.exercises[0].sets[0].notes)
    }

    @Test
    fun testDateAndBodyWeightLookupPerformanceAndConsistency() {
        val seedEntries = (1..150).map { day ->
            val d = (day % 28 + 1).toString().padStart(2, '0')
            com.strengthlogz.mobile.model.BodyWeightEntry("$d-JAN-2026", 70.0 + (day * 0.1), "seed")
        }
        com.strengthlogz.mobile.util.BodyWeightUtils.publish(seedEntries)

        val startNs = System.nanoTime()
        repeat(5000) {
            val w = com.strengthlogz.mobile.util.BodyWeightUtils.weightOn("15-JAN-2026")
            assertTrue(w > 70.0)
            val iso = DateUtils.toSortableIso("15-JAN-2026")
            assertEquals("2026-01-15", iso)
            val dayStr = DateUtils.formatDisplayWithDay("20-OCT-2026")
            assertEquals("Tuesday • 20-OCT-2026", dayStr)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        assertTrue("5,000 cached date & bodyweight lookups should complete in < 250ms (took ${elapsedMs}ms)", elapsedMs < 250)
    }

    @Test
    fun testGoalEngineCalculationsProgrammingLiveSessionAndReports() {
        val workouts = listOf(
            com.strengthlogz.mobile.model.Workout("w1", "01-OCT-2026", "Push 1"),
            com.strengthlogz.mobile.model.Workout("w2", "08-OCT-2026", "Push 2"),
            com.strengthlogz.mobile.model.Workout("w3", "15-OCT-2026", "Push 3")
        )
        val benchSets = listOf(
            com.strengthlogz.mobile.model.WorkoutSet("s1", "w1", "01-OCT-2026", "Bench Press", 1, 95.0, 8),
            com.strengthlogz.mobile.model.WorkoutSet("s2", "w1", "01-OCT-2026", "Bench Press", 2, 95.0, 8),
            com.strengthlogz.mobile.model.WorkoutSet("s3", "w2", "08-OCT-2026", "Bench Press", 1, 97.5, 8),
            com.strengthlogz.mobile.model.WorkoutSet("s4", "w2", "08-OCT-2026", "Bench Press", 2, 97.5, 7),
            com.strengthlogz.mobile.model.WorkoutSet("s5", "w3", "15-OCT-2026", "Bench Press", 1, 100.0, 8),
            com.strengthlogz.mobile.model.WorkoutSet("s6", "w3", "15-OCT-2026", "Bench Press", 2, 100.0, 8)
        )
        val bodyWeights = listOf(
            com.strengthlogz.mobile.model.BodyWeightEntry("01-OCT-2026", 75.0, "manual"),
            com.strengthlogz.mobile.model.BodyWeightEntry("15-OCT-2026", 75.0, "manual")
        )
        com.strengthlogz.mobile.util.BodyWeightUtils.publish(bodyWeights)

        // 1. Test 1RM Goal Evaluation & Programming Recommendations
        val e1rmGoal = com.strengthlogz.mobile.model.ExerciseGoal(
            id = "goal-bench",
            exerciseName = "Bench Press",
            goalType = "E1RM",
            targetE1rmKg = 140.0,
            baselineE1rmKg = 120.5,
            baselineBodyWeightKg = 75.0,
            createdDate = "01-OCT-2026"
        )
        val eval1rm = com.strengthlogz.mobile.util.GoalEngine.evaluateGoal(
            goal = e1rmGoal,
            workouts = workouts,
            exerciseSets = benchSets,
            allSets = benchSets,
            bodyWeights = bodyWeights
        )
        // 100kg x 8 => 126.5kg E1RM
        assertEquals(126.5, eval1rm.currentBestE1rm, 0.01)
        assertTrue(eval1rm.progressPct > 85.0 && eval1rm.progressPct < 95.0)
        assertTrue(eval1rm.currentRelBw > 1.6)
        assertEquals("Elite", eval1rm.strengthTier)
        assertTrue(eval1rm.nextSessionSets.isNotEmpty())
        assertEquals(4, eval1rm.roadmapPhases.size)
        assertEquals(6, eval1rm.repEquivalenceTable.size)
        assertTrue(eval1rm.accessories.isNotEmpty())

        // 2. Test Sets/Reps Goal Evaluation
        val setsRepsGoal = com.strengthlogz.mobile.model.ExerciseGoal(
            id = "goal-bench-sr",
            exerciseName = "Bench Press",
            goalType = "SETS_REPS",
            targetWeightKg = 102.5,
            targetSets = 3,
            targetReps = 8,
            baselineE1rmKg = 120.5,
            createdDate = "01-OCT-2026"
        )
        val evalSr = com.strengthlogz.mobile.util.GoalEngine.evaluateGoal(
            goal = setsRepsGoal,
            workouts = workouts,
            exerciseSets = benchSets,
            allSets = benchSets,
            bodyWeights = bodyWeights
        )
        assertTrue(evalSr.effectiveTargetE1rm >= 129.5)
        assertTrue(evalSr.nextSessionSets.isNotEmpty())

        // 3. Test Live Session Coach (in-logger 'Goal Progress' popup)
        val liveAnalysis = com.strengthlogz.mobile.util.GoalEngine.evaluateLiveSession(
            evaluation = evalSr,
            liveSets = listOf(
                Triple(102.5, 8, true),
                Triple(102.5, 8, true),
                Triple(102.5, 8, true)
            ),
            sessionDate = "22-OCT-2026"
        )
        assertTrue(liveAnalysis.achievedGoalToday)
        assertEquals(100.0, liveAnalysis.updatedProgressPct, 0.01)
        assertEquals(3, liveAnalysis.qualifyingGoalSetsCount)

        // 4. Test Goal Update on Workout Save & Reports (Exercise + All Goals)
        val newWorkout = com.strengthlogz.mobile.model.Workout("w4", "22-OCT-2026", "Push 4")
        val newSets = listOf(
            com.strengthlogz.mobile.model.WorkoutSet("s7", "w4", "22-OCT-2026", "Bench Press", 1, 102.5, 8),
            com.strengthlogz.mobile.model.WorkoutSet("s8", "w4", "22-OCT-2026", "Bench Press", 2, 102.5, 8),
            com.strengthlogz.mobile.model.WorkoutSet("s9", "w4", "22-OCT-2026", "Bench Press", 3, 102.5, 8)
        )
        val updatedGoal = com.strengthlogz.mobile.util.GoalEngine.updateGoalWithLoggedSession(
            goal = setsRepsGoal,
            workout = newWorkout,
            workoutExerciseSets = newSets,
            allExerciseSetsAfterSave = benchSets + newSets,
            allWorkoutsAfterSave = workouts + newWorkout,
            bodyWeights = bodyWeights
        )
        assertEquals("22-OCT-2026", updatedGoal.lastUpdatedDate)
        assertEquals("22-OCT-2026", updatedGoal.achievedDate)
        assertTrue(updatedGoal.safeReports.isNotEmpty())
        assertTrue(updatedGoal.safeReports.first().achievedGoal)

        val exReport = com.strengthlogz.mobile.util.GoalEngine.generateExerciseReport(
            goal = updatedGoal,
            workouts = workouts + newWorkout,
            exerciseSets = benchSets + newSets,
            allSets = benchSets + newSets,
            bodyWeights = bodyWeights
        )
        assertEquals(4, exReport.sessionEntries.size)
        assertTrue(exReport.executiveStatus.contains("ACHIEVED"))

        val masterReport = com.strengthlogz.mobile.util.GoalEngine.generateAllGoalsReport(
            goals = listOf(e1rmGoal, updatedGoal),
            workouts = workouts + newWorkout,
            allSets = benchSets + newSets,
            bodyWeights = bodyWeights
        )
        assertEquals(2, masterReport.totalGoalsCount)
        assertEquals(1, masterReport.achievedGoalsCount)
        assertTrue(masterReport.executiveTakeaways.isNotEmpty())
    }
}

